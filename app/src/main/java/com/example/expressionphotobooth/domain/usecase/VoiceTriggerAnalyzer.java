package com.example.expressionphotobooth.domain.usecase;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Super-Expert Voice Trigger Analyzer (V3 - Final Precision)
 * Improvements:
 * 1. Sliding Window with 50% Overlap: Prevents signal loss at buffer boundaries.
 * 2. Spectral Brightness Ratio: High-frequency energy analysis for robust 'S' detection.
 * 3. Temporal Stability Filter: Requires multiple consecutive frames to confirm phonemes.
 * 4. Advanced Pitch Tracking: Uses normalized cross-correlation for human vowel verification.
 */
public class VoiceTriggerAnalyzer {
    private static final String TAG = "VoiceAI_SuperExpert";
    
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    // Window config (30ms window @ 16kHz)
    private static final int WINDOW_SIZE = 480;
    private static final int OVERLAP = 240;

    private enum State { IDLE, VOWEL_STABLE, SIBILANT_WAITING }
    private State currentState = State.IDLE;
    
    private int vowelFrameCounter = 0;
    private int sibilantFrameCounter = 0;
    private long stateTimestamp = 0;

    // Expert Thresholds
    private static final int REQUIRED_VOWEL_FRAMES = 3;    // ~90ms of stable 'EE'
    private static final int REQUIRED_SIBILANT_FRAMES = 3; // ~90ms of stable 'S'
    private static final long MAX_PHONEME_GAP_MS = 600;

    private double noiseFloor = 400.0;
    private static final double LEARNING_RATE = 0.01;
    private static final double TRIGGER_SNR = 2.2; 

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

    public void start(OnVoiceTriggerDetected listener) {
        this.listener = listener;
        stop();

        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);
        int bufferSize = Math.max(minBufferSize, 2048);

        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize);
        } catch (SecurityException e) {
            notifyError("Mic permission denied");
            return;
        }

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            notifyError("Mic init failed");
            return;
        }

        isRunning = true;
        audioRecord.startRecording();
        
        workerThread = new Thread(this::audioProcessingLoop, "SuperVoiceAnalyzer");
        workerThread.start();
    }

    private void audioProcessingLoop() {
        short[] slidingBuffer = new short[WINDOW_SIZE];
        int writePos = 0;

        while (isRunning) {
            // Read 240 samples (half window) to maintain 50% overlap
            short[] tempBuffer = new short[OVERLAP];
            int read = audioRecord.read(tempBuffer, 0, OVERLAP);
            
            if (read > 0) {
                // Shift existing data and add new data
                System.arraycopy(slidingBuffer, OVERLAP, slidingBuffer, 0, OVERLAP);
                System.arraycopy(tempBuffer, 0, slidingBuffer, OVERLAP, read);
                
                analyzeExpertLogic(slidingBuffer, WINDOW_SIZE);
            }
        }
    }

    private void analyzeExpertLogic(short[] window, int length) {
        double rms = 0;
        int crossings = 0;
        double highFreqEnergy = 0;
        double lowFreqEnergy = 0;

        for (int i = 0; i < length; i++) {
            double val = window[i];
            rms += val * val;
            if (i > 0) {
                if ((window[i] ^ window[i-1]) < 0) crossings++;
                // Simple High-Pass Filter: diff between samples
                highFreqEnergy += Math.abs(window[i] - window[i-1]);
                lowFreqEnergy += Math.abs(window[i]);
            }
        }
        rms = Math.sqrt(rms / length);
        double zcr = (double) crossings / length;
        double brightness = highFreqEnergy / (lowFreqEnergy + 1.0);

        // 1. Adaptive Noise Floor
        if (rms < noiseFloor * 1.4) {
            noiseFloor = (noiseFloor * (1 - LEARNING_RATE)) + (rms * LEARNING_RATE);
            if (currentState != State.IDLE && (System.currentTimeMillis() - stateTimestamp > MAX_PHONEME_GAP_MS)) {
                resetState("Timeout");
            }
            return;
        }

        // 2. SNR Gate
        if (rms < noiseFloor * TRIGGER_SNR) return;

        // 3. Autocorrelation for periodicity
        double correlation = calculateCorrelation(window, length);

        // 4. Phoneme Classifier
        boolean isVowel = (zcr < 0.15 && correlation > 0.68 && brightness < 1.0);
        boolean isSibilant = (zcr > 0.22 && zcr < 0.5 && brightness > 1.3 && correlation < 0.45);

        updateStateMachine(isVowel, isSibilant);
    }

    private void updateStateMachine(boolean isVowel, boolean isSibilant) {
        long now = System.currentTimeMillis();

        switch (currentState) {
            case IDLE:
                if (isVowel) {
                    vowelFrameCounter++;
                    if (vowelFrameCounter >= REQUIRED_VOWEL_FRAMES) {
                        currentState = State.VOWEL_STABLE;
                        stateTimestamp = now;
                        Log.d(TAG, "Step 1: Stable 'EE' verified");
                    }
                } else {
                    vowelFrameCounter = 0;
                }
                break;

            case VOWEL_STABLE:
                if (now - stateTimestamp > MAX_PHONEME_GAP_MS) {
                    resetState("EE->S timeout");
                    return;
                }
                if (isSibilant) {
                    sibilantFrameCounter++;
                    if (sibilantFrameCounter >= REQUIRED_SIBILANT_FRAMES) {
                        triggerCapture();
                    }
                } else if (!isVowel) {
                    // Allow some background/gap between EE and S
                }
                break;
        }
    }

    private double calculateCorrelation(short[] buffer, int length) {
        int minLag = 40;  // 400Hz
        int maxLag = 200; // 80Hz
        double maxCorr = 0;

        for (int lag = minLag; lag < maxLag; lag++) {
            double corr = 0;
            double energyL = 0;
            double energyR = 0;
            for (int i = 0; i < length - lag; i++) {
                corr += (double) buffer[i] * buffer[i + lag];
                energyL += (double) buffer[i] * buffer[i];
                energyR += (double) buffer[i + lag] * buffer[i + lag];
            }
            if (energyL > 0 && energyR > 0) {
                corr = corr / Math.sqrt(energyL * energyR);
            }
            if (corr > maxCorr) maxCorr = corr;
        }
        return maxCorr;
    }

    private void triggerCapture() {
        Log.i(TAG, "🎯 ✅ [CHEESE CONFIRMED] - Sequence Matched");
        resetState("Triggered");
        mainHandler.post(() -> { if (listener != null) listener.onTrigger(); });
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
    }

    private void resetState(String reason) {
        if (currentState != State.IDLE) Log.v(TAG, "State Reset: " + reason);
        currentState = State.IDLE;
        vowelFrameCounter = 0;
        sibilantFrameCounter = 0;
    }

    public void stop() {
        isRunning = false;
        if (workerThread != null) { workerThread.interrupt(); workerThread = null; }
        if (audioRecord != null) { 
            try { audioRecord.stop(); audioRecord.release(); } catch (Exception ignored) {}
            audioRecord = null; 
        }
    }

    private void notifyError(String msg) {
        mainHandler.post(() -> { if (listener != null) listener.onError(msg); });
    }
}
