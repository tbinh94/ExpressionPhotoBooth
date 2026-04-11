package com.example.expressionphotobooth.domain.usecase;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.expressionphotobooth.utils.LocaleManager;

/**
 * Universal Voice Trigger - Works on any Android device without Google Services.
 * Uses real-time Digital Signal Processing (DSP):
 * 1. Root Mean Square (RMS) for volume detection.
 * 2. Zero-Crossing Rate (ZCR) to distinguish between:
 *    - "Hi" (Vietnamese): Voiced sound, low frequency, LOW ZCR.
 *    - "Cheese" (English): Sibilant 'S' sound, high frequency, HIGH ZCR.
 */
public class VoiceTriggerAnalyzer {
    private static final String TAG = "VoiceTrigger";
    
    // Audio Configuration
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    // Detection Thresholds (Tweakable)
    private static final double MIN_RMS_THRESHOLD = 600; // Minimum volume to start analysis
    private static final double ZCR_HI_THRESHOLD = 0.16;  // Below this is likely "Hi/Hì" (low freq)
    private static final double ZCR_CHEESE_THRESHOLD = 0.22; // Above this is likely "S" sound (high freq)

    public interface OnVoiceTriggerDetected {
        void onTrigger();
        void onError(String error);
    }

    private final Context context;
    private AudioRecord audioRecord;
    private Thread workerThread;
    private volatile boolean isRunning = false;
    private OnVoiceTriggerDetected listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public VoiceTriggerAnalyzer(Context context) {
        this.context = context;
    }

    /** Start listening using raw PCM processing */
    public void start(OnVoiceTriggerDetected listener) {
        this.listener = listener;
        stop(); // Ensure clean state

        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SampleRateConverter_8000_To_16000();
        }

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL,
                    ENCODING,
                    bufferSize * 2
            );
        } catch (SecurityException e) {
            notifyError("Microphone permission denied");
            return;
        }

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            notifyError("Microphone initialization failed");
            return;
        }

        isRunning = true;
        audioRecord.startRecording();
        
        final int finalBufferSize = bufferSize;
        workerThread = new Thread(() -> {
            short[] buffer = new short[finalBufferSize];
            String lang = LocaleManager.getSavedLanguage(context);
            boolean isVi = LocaleManager.LANG_VI.equals(lang);

            Log.d(TAG, "DSP Voice Analyzer started for " + (isVi ? "Vietnamese" : "English"));

            while (isRunning) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    analyzeFrame(buffer, read, isVi);
                }
            }
            Log.d(TAG, "DSP Voice Analyzer stopped");
        }, "VoiceAnalyzer-Worker");
        
        workerThread.start();
    }

    private void analyzeFrame(short[] buffer, int read, boolean isVi) {
        double sum = 0;
        int crossings = 0;

        for (int i = 0; i < read; i++) {
            sum += (double) buffer[i] * buffer[i];
            
            // Calculate Zero-Crossing Rate
            if (i > 0 && ((buffer[i] >= 0 && buffer[i-1] < 0) || (buffer[i] < 0 && buffer[i-1] >= 0))) {
                crossings++;
            }
        }

        double rms = Math.sqrt(sum / read);
        double zcr = (double) crossings / read;

        // Skip silence
        if (rms < MIN_RMS_THRESHOLD) return;

        boolean hit = false;
        if (isVi) {
            // "Hi" / "Hì" / "Hả" are voiced sounds -> Low ZCR
            if (zcr < ZCR_HI_THRESHOLD) hit = true;
        } else {
            // "Cheese" has a sharp "S" at the end -> High ZCR
            if (zcr > ZCR_CHEESE_THRESHOLD) hit = true;
        }

        if (hit) {
            Log.d(TAG, "✅ [DETECTED] RMS: " + (int)rms + " | ZCR: " + String.format("%.2f", zcr));
            isRunning = false;
            mainHandler.post(() -> {
                if (listener != null) listener.onTrigger();
            });
        }
    }

    /** Release recording resources */
    public void stop() {
        isRunning = false;
        if (workerThread != null) {
            try { workerThread.interrupt(); } catch (Exception ignored) {}
            workerThread = null;
        }
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.w(TAG, "Stop warning: " + e.getMessage());
            }
            audioRecord = null;
        }
    }

    private void notifyError(String msg) {
        mainHandler.post(() -> {
            if (listener != null) listener.onError(msg);
        });
    }

    private int SampleRateConverter_8000_To_16000() {
        return 1024; // Fallback buffer size
    }
}
