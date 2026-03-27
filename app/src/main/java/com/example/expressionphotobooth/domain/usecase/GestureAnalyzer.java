package com.example.expressionphotobooth.domain.usecase;

import android.graphics.Rect;
import android.media.Image;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import java.util.ArrayDeque;
import java.util.Deque;

public class GestureAnalyzer {

    public interface OnGestureDetected {
        void onResult(String gesture, Rect handBox);
    }

    private static final int HISTORY_SIZE = 6; // Số frame cần nhất quán để xác nhận gesture
    private static final int CONFIRM_THRESHOLD = 4; // Cần ít nhất 4/6 frame khớp

    private final PoseDetector poseDetector;
    private final Deque<String> gestureHistory = new ArrayDeque<>();
    
    // Smoothed bounding box để giảm flicker
    private float smoothLeft = 0, smoothTop = 0, smoothRight = 0, smoothBottom = 0;
    private static final float SMOOTH_FACTOR = 0.4f; // 0 = không đổi, 1 = lấy ngay giá trị mới

    private boolean isProcessing = false;

    public GestureAnalyzer() {
        PoseDetectorOptions options = new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build();
        poseDetector = PoseDetection.getClient(options);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    public void analyzeImageProxy(ImageProxy imageProxy, OnGestureDetected listener) {
        if (isProcessing || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        isProcessing = true;
        Image mediaImage = imageProxy.getImage();
        InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        poseDetector.process(image)
                .addOnSuccessListener(pose -> {
                    PoseLandmark leftWrist    = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);
                    PoseLandmark rightWrist   = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);
                    PoseLandmark leftThumb    = pose.getPoseLandmark(PoseLandmark.LEFT_THUMB);
                    PoseLandmark rightThumb   = pose.getPoseLandmark(PoseLandmark.RIGHT_THUMB);
                    PoseLandmark leftIndex    = pose.getPoseLandmark(PoseLandmark.LEFT_INDEX);
                    PoseLandmark rightIndex   = pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX);
                    PoseLandmark leftPinky    = pose.getPoseLandmark(PoseLandmark.LEFT_PINKY);
                    PoseLandmark rightPinky   = pose.getPoseLandmark(PoseLandmark.RIGHT_PINKY);
                    
                    PoseLandmark leftEye      = pose.getPoseLandmark(PoseLandmark.LEFT_EYE_OUTER);
                    PoseLandmark rightEye     = pose.getPoseLandmark(PoseLandmark.RIGHT_EYE_OUTER);

                    // === PHÁT HIỆN CỬ CHỈ (Chỉ lấy landmarks có độ tin cậy cao) ===

                    float faceWidth = 150f;
                    if (isValid(leftEye, rightEye)) {
                        faceWidth = dist(leftEye, rightEye) * 1.5f; // uớc tính rộng khuôn mặt
                    }

                    boolean leftHeart = checkHeart(leftWrist, leftThumb, leftIndex, faceWidth);
                    boolean rightHeart = checkHeart(rightWrist, rightThumb, rightIndex, faceWidth);

                    boolean leftHi = checkHi(leftWrist, leftThumb, leftIndex, faceWidth);
                    boolean rightHi = checkHi(rightWrist, rightThumb, rightIndex, faceWidth);

                    boolean isHeart = leftHeart || rightHeart;
                    boolean isHi = leftHi || rightHi;

                    String rawGesture;
                    if (isHeart) {
                        rawGesture = "HEART";
                    } else if (isHi) {
                        rawGesture = "HI";
                    } else {
                        rawGesture = "NONE";
                    }

                    // === TEMPORAL SMOOTHING: bỏ phiếu qua N frame ===
                    gestureHistory.addLast(rawGesture);
                    if (gestureHistory.size() > HISTORY_SIZE) {
                        gestureHistory.pollFirst();
                    }

                    String confirmedGesture = "NONE";
                    int heartVotes = 0, hiVotes = 0;
                    for (String g : gestureHistory) {
                        if ("HEART".equals(g)) heartVotes++;
                        else if ("HI".equals(g)) hiVotes++;
                    }
                    if (heartVotes >= CONFIRM_THRESHOLD) confirmedGesture = "HEART";
                    else if (hiVotes >= CONFIRM_THRESHOLD) confirmedGesture = "HI";

                    // === BOUNDING BOX với smoothing ===
                    // Chỉ vẽ bbox trên tay có độ tin cậy cao để tránh nhảy bậy vào mặt
                    Rect handBox = null;
                    if (leftHeart || leftHi || (isValid(leftWrist, leftIndex) && !isValid(rightWrist, rightIndex))) {
                        handBox = calculateSmoothedHandBox(leftWrist, leftThumb, leftIndex, leftPinky);
                    } else if (rightHeart || rightHi || isValid(rightWrist, rightIndex)) {
                        handBox = calculateSmoothedHandBox(rightWrist, rightThumb, rightIndex, rightPinky);
                    }

                    listener.onResult(confirmedGesture, handBox);
                })
                .addOnFailureListener(e -> listener.onResult("NONE", null))
                .addOnCompleteListener(task -> {
                    isProcessing = false;
                    imageProxy.close();
                });
    }

    private boolean isValid(PoseLandmark... landmarks) {
        for (PoseLandmark lm : landmarks) {
            // Ngưỡng 0.25f: ngón tay trỏ/cái thường có độ tin cậy thấp hơn vai/mặt trong model Pose Detection
            if (lm == null || lm.getInFrameLikelihood() < 0.25f) return false;
        }
        return true;
    }

    private boolean checkHeart(PoseLandmark wrist, PoseLandmark thumb, PoseLandmark index, float faceWidth) {
        if (!isValid(wrist, thumb, index)) return false;
        float dThumbIndex = dist(thumb, index);
        float dIndexWrist = dist(index, wrist);
        // Ngón cái và trỏ chụm khít sát nhau (HEART) - nới lỏng xuống 0.35 để dễ bắt hơn
        return dThumbIndex < dIndexWrist * 0.35f && dThumbIndex < faceWidth * 0.6f;
    }

    private boolean checkHi(PoseLandmark wrist, PoseLandmark thumb, PoseLandmark index, float faceWidth) {
        if (!isValid(wrist, index)) return false; 
        float dIndexWrist = dist(index, wrist);
        
        // Khi ngón trỏ duỗi thẳng, khoảng cách từ cổ tay tới ngón trỏ dài hơn
        if (dIndexWrist < faceWidth * 0.35f) return false;

        // Cho phép tay nghiêng chữ V (tilted) bằng cách giảm điều kiện y
        boolean indexUp = index.getPosition().y < wrist.getPosition().y - (dIndexWrist * 0.10f);
        
        // V-sign: ngón trỏ thẳng, ngón cái co lại hoặc xòe
        // Khoảng cách cái - trỏ không cần quá lớn, 25% là đủ để tránh trùng với Heart
        float dThumbIndex = isValid(thumb) ? dist(thumb, index) : Float.MAX_VALUE;
        boolean fingersSpread = dThumbIndex > dIndexWrist * 0.25f;
        
        return indexUp && fingersSpread;
    }
    private float dist(PoseLandmark a, PoseLandmark b) {
        float dx = a.getPosition().x - b.getPosition().x;
        float dy = a.getPosition().y - b.getPosition().y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Tính bounding box có exponential smoothing để tránh flicker.
     */
    private Rect calculateSmoothedHandBox(PoseLandmark... landmarks) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean hasAny = false;

        for (PoseLandmark lm : landmarks) {
            if (lm == null) continue;
            float x = lm.getPosition().x;
            float y = lm.getPosition().y;
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            hasAny = true;
        }

        if (!hasAny) return null;

        int pad = 50;
        float targetLeft   = minX - pad;
        float targetTop    = minY - pad;
        float targetRight  = maxX + pad;
        float targetBottom = maxY + pad;

        // Exponential smoothing: trộn giá trị cũ với giá trị mới
        if (smoothRight == 0) {
            // Lần đầu tiên: lấy luôn
            smoothLeft = targetLeft;
            smoothTop = targetTop;
            smoothRight = targetRight;
            smoothBottom = targetBottom;
        } else {
            smoothLeft   = smoothLeft   + SMOOTH_FACTOR * (targetLeft   - smoothLeft);
            smoothTop    = smoothTop    + SMOOTH_FACTOR * (targetTop    - smoothTop);
            smoothRight  = smoothRight  + SMOOTH_FACTOR * (targetRight  - smoothRight);
            smoothBottom = smoothBottom + SMOOTH_FACTOR * (targetBottom - smoothBottom);
        }

        return new Rect((int) smoothLeft, (int) smoothTop, (int) smoothRight, (int) smoothBottom);
    }
}
