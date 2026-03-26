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
                    PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
                    PoseLandmark rightShoulder= pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
                    PoseLandmark leftThumb    = pose.getPoseLandmark(PoseLandmark.LEFT_THUMB);
                    PoseLandmark rightThumb   = pose.getPoseLandmark(PoseLandmark.RIGHT_THUMB);
                    PoseLandmark leftIndex    = pose.getPoseLandmark(PoseLandmark.LEFT_INDEX);
                    PoseLandmark rightIndex   = pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX);
                    PoseLandmark leftPinky    = pose.getPoseLandmark(PoseLandmark.LEFT_PINKY);
                    PoseLandmark rightPinky   = pose.getPoseLandmark(PoseLandmark.RIGHT_PINKY);

                    // === PHÁT HIỆN CỬ CHỈ ===

                    // ❤️ FINGER HEART: ngón CÁI và ngón TRỎ CẠM nhau (1 bàn tay)
                    // ML Kit trả về thumb tip & index tip - khoảng cách < 15% chiều rộng vai
                    boolean isHeart = false;
                    float bodyRef = (leftShoulder != null && rightShoulder != null)
                            ? dist(leftShoulder, rightShoulder) : 150f;

                    boolean leftHeart = leftThumb != null && leftIndex != null
                            && dist(leftThumb, leftIndex) < bodyRef * 0.15f
                            && isWristRaisedAboveShoulder(leftWrist, leftShoulder);

                    boolean rightHeart = rightThumb != null && rightIndex != null
                            && dist(rightThumb, rightIndex) < bodyRef * 0.15f
                            && isWristRaisedAboveShoulder(rightWrist, rightShoulder);

                    isHeart = leftHeart || rightHeart;

                    // ✌️ HI: cổ tay giơ CAO hơn vai (bất kỳ bên)
                    boolean leftHi  = isWristRaisedAboveShoulder(leftWrist, leftShoulder);
                    boolean rightHi = isWristRaisedAboveShoulder(rightWrist, rightShoulder);

                    String rawGesture;
                    if (isHeart) {
                        rawGesture = "HEART";
                    } else if (leftHi || rightHi) {
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
                    Rect handBox = calculateSmoothedHandBox(
                            leftWrist, rightWrist,
                            leftThumb, rightThumb,
                            leftIndex, rightIndex,
                            leftPinky, rightPinky
                    );

                    listener.onResult(confirmedGesture, handBox);
                })
                .addOnFailureListener(e -> listener.onResult("NONE", null))
                .addOnCompleteListener(task -> {
                    isProcessing = false;
                    imageProxy.close();
                });
    }

    private boolean isWristRaisedAboveShoulder(PoseLandmark wrist, PoseLandmark shoulder) {
        if (wrist == null || shoulder == null) return false;
        // y nhỏ hơn = cao hơn trên màn hình Android
        return wrist.getPosition().y < shoulder.getPosition().y - 20;
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
