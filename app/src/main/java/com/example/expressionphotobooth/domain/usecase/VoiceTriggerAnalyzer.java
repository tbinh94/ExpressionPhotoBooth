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
 * BỘ PHÂN TÍCH GIỌNG NÓI CHUYÊN GIA
 * Phương pháp:
 * 1. Adaptive Noise Floor: Tự động học và khử nhiễu môi trường (tiếng quạt, tiếng ồn trắng).
 * 2. Autocorrelation (DSP): Nhận diện tính chu kỳ (Pitch) để phân biệt giọng người với tiếng ồn.
 * 3. Phoneme State Machine: Máy trạng thái nhận diện trình tự âm học [EE -> S] của từ "Cheese".
 */
public class VoiceTriggerAnalyzer {
    private static final String TAG = "VoiceAI_SuperExpert";
    
    // Cấu hình âm thanh: 16kHz, Đơn kênh, 16-bit PCM (Tiêu chuẩn cho nhận diện giọng nói)
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    // Kích thước cửa sổ phân tích (30ms @ 16kHz)
    private static final int WINDOW_SIZE = 480;
    private static final int OVERLAP = 240; // Độ chồng lấp 50% để không bỏ sót tín hiệu

    // Máy trạng thái nhận diện từ "Cheese"
    private enum State { IDLE, VOWEL_STABLE, SIBILANT_WAITING }
    private State currentState = State.IDLE;
    
    private int vowelFrameCounter = 0;    // Đếm số frame âm "EE" ổn định
    private int sibilantFrameCounter = 0; // Đếm số frame âm "S" ổn định
    private long stateTimestamp = 0;

    // Các ngưỡng kỹ thuật (Thresholds) - Đã được tối ưu qua thực nghiệm
    private static final int REQUIRED_VOWEL_FRAMES = 3;    // Cần ~90ms âm "EE" liên tục
    private static final int REQUIRED_SIBILANT_FRAMES = 3; // Cần ~90ms âm "S" liên tục
    private static final long MAX_PHONEME_GAP_MS = 600;    // Thời gian tối đa giữa âm EE và S

    private double noiseFloor = 400.0;                     // Mức nhiễu nền cơ sở
    private static final double LEARNING_RATE = 0.01;      // Tốc độ học nhiễu môi trường
    private static final double TRIGGER_SNR = 2.2;         // Tỉ lệ Tín hiệu/Nhiễu tối thiểu để kích hoạt

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

    /**
     * Khởi tạo và bắt đầu luồng ghi âm xử lý tín hiệu
     */
    public void start(OnVoiceTriggerDetected listener) {
        this.listener = listener;
        stop();

        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);
        int bufferSize = Math.max(minBufferSize, 2048);

        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize);
        } catch (SecurityException e) {
            notifyError("Quyền truy cập Micro bị từ chối");
            return;
        }

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            notifyError("Không thể khởi tạo Micro");
            return;
        }

        isRunning = true;
        audioRecord.startRecording();
        
        // Chạy luồng xử lý riêng để không gây lag giao diện (UI Thread)
        workerThread = new Thread(this::audioProcessingLoop, "SuperVoiceAnalyzer");
        workerThread.start();
    }

    /**
     * Vòng lặp xử lý âm thanh sử dụng kỹ thuật Sliding Window (Cửa sổ trượt)
     */
    private void audioProcessingLoop() {
        short[] slidingBuffer = new short[WINDOW_SIZE];
        while (isRunning) {
            short[] tempBuffer = new short[OVERLAP];
            int read = audioRecord.read(tempBuffer, 0, OVERLAP);
            
            if (read > 0) {
                // Dịch chuyển dữ liệu cũ và ghi đè dữ liệu mới vào 50% cuối cửa sổ
                System.arraycopy(slidingBuffer, OVERLAP, slidingBuffer, 0, OVERLAP);
                System.arraycopy(tempBuffer, 0, slidingBuffer, OVERLAP, read);
                
                // Bắt đầu phân tích cửa sổ âm thanh hiện tại
                analyzeExpertLogic(slidingBuffer, WINDOW_SIZE);
            }
        }
    }

    /**
     * Logic phân tích DSP chuyên sâu
     */
    private void analyzeExpertLogic(short[] window, int length) {
        double rms = 0;
        int crossings = 0;
        double highFreqEnergy = 0;
        double lowFreqEnergy = 0;

        // Trích xuất các đặc trưng âm học (Feature Extraction)
        for (int i = 0; i < length; i++) {
            double val = window[i];
            rms += val * val;
            if (i > 0) {
                // Tính Zero Crossing Rate (ZCR) - Tần số cắt không
                if ((window[i] ^ window[i-1]) < 0) crossings++;
                
                // Tính Spectral Brightness (Độ sáng phổ) bằng bộ lọc High-pass đơn giản
                highFreqEnergy += Math.abs(window[i] - window[i-1]);
                lowFreqEnergy += Math.abs(window[i]);
            }
        }
        rms = Math.sqrt(rms / length); // Cường độ âm thanh (Volume)
        double zcr = (double) crossings / length;
        double brightness = highFreqEnergy / (lowFreqEnergy + 1.0);

        // BƯỚC 1: Cập nhật Noise Floor thích nghi (Chỉ học khi yên tĩnh)
        if (rms < noiseFloor * 1.4) {
            noiseFloor = (noiseFloor * (1 - LEARNING_RATE)) + (rms * LEARNING_RATE);
            if (currentState != State.IDLE && (System.currentTimeMillis() - stateTimestamp > MAX_PHONEME_GAP_MS)) {
                resetState("Hết thời gian chờ âm S");
            }
            return;
        }

        // BƯỚC 2: Kiểm tra ngưỡng SNR (Tín hiệu phải lớn hơn nhiễu nền)
        if (rms < noiseFloor * TRIGGER_SNR) return;

        // BƯỚC 3: Autocorrelation - Tính toán sự tự tương quan để tìm chu kỳ (Pitch)
        // Đây là bước quan trọng nhất để phân biệt tiếng người với tiếng quạt
        double correlation = calculateCorrelation(window, length);

        // BƯỚC 4: Phân loại âm tiết (Phoneme Classification)
        // Nguyên âm "EE": ZCR thấp, Tính chu kỳ cao (correlation > 0.68), Độ sáng thấp.
        boolean isVowel = (zcr < 0.15 && correlation > 0.68 && brightness < 1.0);
        // Phụ âm xì "S": ZCR cao, Độ sáng phổ cao, Không có tính chu kỳ (correlation thấp).
        boolean isSibilant = (zcr > 0.22 && zcr < 0.5 && brightness > 1.3 && correlation < 0.45);

        // Cập nhật máy trạng thái (State Machine)
        updateStateMachine(isVowel, isSibilant);
    }

    /**
     * Cập nhật trạng thái nhận diện theo trình tự thời gian
     */
    private void updateStateMachine(boolean isVowel, boolean isSibilant) {
        long now = System.currentTimeMillis();

        switch (currentState) {
            case IDLE:
                if (isVowel) {
                    vowelFrameCounter++;
                    if (vowelFrameCounter >= REQUIRED_VOWEL_FRAMES) {
                        currentState = State.VOWEL_STABLE; // Xác nhận đã nói xong âm "EE" ổn định
                        stateTimestamp = now;
                        Log.d(TAG, "Bước 1: Đã nhận diện được nguyên âm 'EE' ổn định");
                    }
                } else {
                    vowelFrameCounter = 0;
                }
                break;

            case VOWEL_STABLE:
                if (now - stateTimestamp > MAX_PHONEME_GAP_MS) {
                    resetState("Quá lâu không có âm S xì đuôi");
                    return;
                }
                if (isSibilant) {
                    sibilantFrameCounter++;
                    if (sibilantFrameCounter >= REQUIRED_SIBILANT_FRAMES) {
                        triggerCapture(); // Khớp hoàn toàn chuỗi [EE -> S] của từ "Cheese"
                    }
                }
                break;
        }
    }

    /**
     * Thuật toán tính toán sự tự tương quan (Cross-Correlation)
     * Giúp xác định xem âm thanh có phải là "giọng người" hay không
     */
    private double calculateCorrelation(short[] buffer, int length) {
        int minLag = 40;  // 400Hz (Giọng nữ/trẻ em)
        int maxLag = 200; // 80Hz (Giọng nam trầm)
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

    /**
     * Kích hoạt chụp ảnh và thông báo cho UI
     */
    private void triggerCapture() {
        Log.i(TAG, "🎯 ✅ [CHEESE CONFIRMED] - Nhận diện thành công!");
        resetState("Kích hoạt chụp");
        mainHandler.post(() -> { if (listener != null) listener.onTrigger(); });
        
        // Cooldown 2s để tránh chụp liên tiếp
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
    }

    private void resetState(String reason) {
        if (currentState != State.IDLE) Log.v(TAG, "Reset: " + reason);
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
