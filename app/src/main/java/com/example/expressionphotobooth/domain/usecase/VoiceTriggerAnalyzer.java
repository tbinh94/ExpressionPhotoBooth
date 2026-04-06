package com.example.expressionphotobooth.domain.usecase;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Voice trigger dựa trên AudioRecord + biên độ âm thanh.
 *
 * Không cần API key, hoạt động offline hoàn toàn.
 * Logic: liên tục đọc PCM từ mic → nếu biên độ RMS vượt ngưỡng
 *        trong đủ số frame liên tiếp → kích hoạt trigger.
 *
 * Điều chỉnh AMPLITUDE_THRESHOLD nếu cần (thấp hơn = nhạy hơn).
 */
public class VoiceTriggerAnalyzer {

    private static final String TAG = "VoiceTrigger";

    // ── Cấu hình AudioRecord ─────────────────────────────────────
    private static final int SAMPLE_RATE   = 16000;   // Hz
    private static final int CHANNEL       = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING      = AudioFormat.ENCODING_PCM_16BIT;

    // ── Cấu hình trigger ─────────────────────────────────────────
    /**
     * Ngưỡng biên độ peak (0–32767).
     * ~1200 = nói bình thường cách mic ~30-50cm.
     * Giảm nếu không nhận; tăng nếu bị rung do tiếng ồn xung quanh.
     */
    private static final short AMPLITUDE_THRESHOLD = 1200;

    /**
     * Số chunk âm thanh liên tiếp vượt ngưỡng để confirm trigger.
     * Tránh trigger do tiếng click ngắn.  (~5 × 20ms = 100ms)
     */
    private static final int SUSTAINED_CHUNKS = 5;

    // ─────────────────────────────────────────────────────────────

    public interface OnVoiceTriggerDetected {
        void onTrigger();
        void onError(String error);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AudioRecord audioRecord;
    private Thread recordThread;
    private volatile boolean isRunning = false;
    private OnVoiceTriggerDetected listener;

    public VoiceTriggerAnalyzer(android.content.Context context) {
        // Context không dùng nhưng giữ signature nhất quán với code cũ
    }

    // ── Public API ───────────────────────────────────────────────

    /** Bắt đầu lắng nghe. Có thể gọi từ bất kỳ thread nào. */
    public void start(OnVoiceTriggerDetected listener) {
        this.listener = listener;
        stop();           // dọn phiên cũ nếu còn

        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);
        if (minBuf == AudioRecord.ERROR_BAD_VALUE || minBuf == AudioRecord.ERROR) {
            notifyError("AudioRecord: bad config");
            return;
        }

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, CHANNEL, ENCODING,
                    minBuf * 2   // buffer dư để tránh overrun
            );
        } catch (SecurityException e) {
            notifyError("Mic permission denied");
            return;
        }

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            notifyError("AudioRecord init failed");
            audioRecord.release();
            audioRecord = null;
            return;
        }

        isRunning = true;
        final int chunkSamples = minBuf / 2; // số short mỗi lần đọc

        recordThread = new Thread(() -> {
            Log.d(TAG, "Recording thread started, chunkSamples=" + chunkSamples);
            audioRecord.startRecording();
            short[] buffer = new short[chunkSamples];
            int sustainedCount = 0;

            while (isRunning) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read <= 0) continue;

                // Tính peak amplitude của chunk này
                short peak = 0;
                for (int i = 0; i < read; i++) {
                    short abs = (short) Math.abs(buffer[i]);
                    if (abs > peak) peak = abs;
                }

                Log.v(TAG, "peak=" + peak + " sus=" + sustainedCount);

                if (peak >= AMPLITUDE_THRESHOLD) {
                    sustainedCount++;
                    if (sustainedCount >= SUSTAINED_CHUNKS) {
                        Log.d(TAG, "✅ Voice trigger fired! peak=" + peak);
                        isRunning = false;   // tự dừng sau khi trigger
                        mainHandler.post(() -> {
                            if (this.listener != null) this.listener.onTrigger();
                        });
                    }
                } else {
                    // Giảm dần để tránh bị vỡ bởi tiếng nhỏ lẻ
                    if (sustainedCount > 0) sustainedCount--;
                }
            }

            safeStopRecord();
            Log.d(TAG, "Recording thread stopped");
        }, "VoiceTrigger-Thread");

        recordThread.setDaemon(true);
        recordThread.start();
        Log.d(TAG, "VoiceTriggerAnalyzer started");
    }

    /** Dừng ngay lập tức. An toàn để gọi nhiều lần. */
    public void stop() {
        isRunning = false;
        safeStopRecord();
        if (recordThread != null) {
            recordThread.interrupt();
            recordThread = null;
        }
        Log.d(TAG, "VoiceTriggerAnalyzer stopped");
    }

    // ── Internal helpers ─────────────────────────────────────────

    private void safeStopRecord() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.w(TAG, "safeStopRecord: " + e.getMessage());
            }
            audioRecord = null;
        }
    }

    private void notifyError(String msg) {
        Log.e(TAG, "Error: " + msg);
        mainHandler.post(() -> {
            if (listener != null) listener.onError(msg);
        });
    }
}
