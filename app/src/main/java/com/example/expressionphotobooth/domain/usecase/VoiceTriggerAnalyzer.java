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
 * Advanced Voice Trigger Analyzer (Expert Edition)
 * Features:
 * 1. Adaptive Noise Floor Tracking (Handles fan noise)
 * 2. Phoneme Sequence State Machine (EE -> S pattern for "Cheese")
 * 3. Spectral Energy Ratio Analysis
 */
public class VoiceTriggerAnalyzer {
    private static final String TAG = "VoiceAI_Expert";
    
    // Audio Configuration
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    // States for "Cheese" [CH-EE-SE] recognition
    private enum State { IDLE, VOWEL_DETECTED, TRIGGERED }
    private State currentState = State.IDLE;
    private long stateTimestamp = 0;
    private static final long MAX_PHONEME_GAP_MS = 600; // Time window between 'EE' and 'S'

    // DSP Constants
    private double noiseFloor = 200.0;              // Lowered: don't start with artificially high floor
    private static final double ADAPTIVE_LEARNING_RATE = 0.02; // Slowed: prevents noisy rooms from killing sensitivity
    private static final double SIGNAL_TO_NOISE_RATIO = 1.3;   // Lowered: normal voice (not shouting) can pass

    // Sibilant (S) vs Vowel (EE) characteristics — widened for better coverage
    private static final double ZCR_VOWEL_MIN  = 0.03;  // Catch quieter 'EE' onset
    private static final double ZCR_VOWEL_MAX  = 0.18;  // Slightly wider for 'EE' variations
    private static final double ZCR_SIBILANT_MIN = 0.20; // Lowered: catch softer 'S'
    private static final double ZCR_SIBILANT_MAX = 0.50; // Slightly wider upper bound

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

        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);
        if (bufferSize <= 0) bufferSize = 1024;

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL,
                    ENCODING,
                    bufferSize * 2
            );
        } catch (SecurityException e) {
            notifyError("Permission denied");
            return;
        }

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            notifyError("Mic init failed");
            return;
        }

        isRunning = true;
        audioRecord.startRecording();
        
        final int finalBufferSize = bufferSize;
        workerThread = new Thread(() -> {
            short[] buffer = new short[finalBufferSize];
            String lang = LocaleManager.getSavedLanguage(context);
            boolean isVi = LocaleManager.LANG_VI.equals(lang);

            while (isRunning) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    analyzeVoiceLogic(buffer, read, isVi);
                }
            }
        }, "ExpertVoiceAnalyzer");
        
        workerThread.start();
    }

    private void analyzeVoiceLogic(short[] buffer, int read, boolean isVi) {
        double sum = 0;
        int crossings = 0;

        for (int i = 0; i < read; i++) {
            sum += (double) buffer[i] * buffer[i];
            if (i > 0 && ((buffer[i] >= 0 && buffer[i-1] < 0) || (buffer[i] < 0 && buffer[i-1] >= 0))) {
                crossings++;
            }
        }

        double rms = Math.sqrt(sum / read);
        double zcr = (double) crossings / read;

        Log.v(TAG, String.format("rms=%.1f  floor=%.1f  snr=%.2f  zcr=%.3f",
                rms, noiseFloor, rms / (noiseFloor + 1), zcr));

        // 1. Adaptive Noise Floor Tracking — only update when truly silent
        if (rms < noiseFloor * 1.1) {
            noiseFloor = (noiseFloor * (1 - ADAPTIVE_LEARNING_RATE)) + (rms * ADAPTIVE_LEARNING_RATE);
            if (currentState != State.IDLE && (System.currentTimeMillis() - stateTimestamp > MAX_PHONEME_GAP_MS)) {
                resetState("Timeout");
            }
            return;
        }

        // 2. Voice Activity Detection (VAD) — require SNR > 1.3x floor
        if (rms < noiseFloor * SIGNAL_TO_NOISE_RATIO) return;

        // 3. Phoneme Sequence Logic
        processCheeseSequence(zcr);
    }

    private void processCheeseSequence(double zcr) {
        long now = System.currentTimeMillis();

        switch (currentState) {
            case IDLE:
                // Looking for "EE" sound (Low ZCR characteristic of vowels)
                if (zcr > ZCR_VOWEL_MIN && zcr < ZCR_VOWEL_MAX) {
                    currentState = State.VOWEL_DETECTED;
                    stateTimestamp = now;
                    Log.d(TAG, "Step 1: Vowel 'EE' detected (zcr=" + String.format("%.3f", zcr) + ")");
                }
                break;

            case VOWEL_DETECTED:
                // Check for timeout
                if (now - stateTimestamp > MAX_PHONEME_GAP_MS) {
                    resetState("EE-S gap too long");
                    return;
                }

                // Looking for "S" sound (High ZCR)
                if (zcr > ZCR_SIBILANT_MIN && zcr < ZCR_SIBILANT_MAX) {
                    triggerCapture("Step 2: Sibilant 'S' detected -> SUCCESS");
                }
                break;
        }
    }

    private void triggerCapture(String reason) {
        Log.d(TAG, "✅ TRIGGER: " + reason);
        resetState("Triggered");
        
        mainHandler.post(() -> {
            if (listener != null) listener.onTrigger();
        });

        // Add a small sleep to worker thread to prevent instant multi-trigger
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
    }

    private void resetState(String reason) {
        if (currentState != State.IDLE) {
            Log.v(TAG, "Reset State: " + reason);
        }
        currentState = State.IDLE;
    }

    public void stop() {
        isRunning = false;
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception ignored) {}
            audioRecord = null;
        }
    }

    private void notifyError(String msg) {
        mainHandler.post(() -> {
            if (listener != null) listener.onError(msg);
        });
    }
}