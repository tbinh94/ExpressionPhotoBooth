package com.example.expressionphotobooth.domain.usecase;

import android.content.Context;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class GestureAnalyzer {

    public interface OnGestureDetected {
        void onResult(AnalysisResult result);
    }

    private static final String TAG = "GestureAnalyzer";
    private static final int HISTORY_SIZE = 5;
    private static final int CONFIRM_THRESHOLD = 3;

    private HandLandmarker handLandmarker;
    private final Deque<String> gestureHistory = new ArrayDeque<>();
    private boolean isProcessing = false;

    // Smoothed bounding box
    private float smoothLeft = 0, smoothTop = 0, smoothRight = 0, smoothBottom = 0;
    private static final float SMOOTH_FACTOR = 0.5f;

    public GestureAnalyzer(Context context) {
        try {
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .build();

            HandLandmarker.HandLandmarkerOptions options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumHands(1)
                    .setMinHandDetectionConfidence(0.3f)
                    .setMinHandPresenceConfidence(0.3f)
                    .setMinTrackingConfidence(0.3f)
                    .build();

            handLandmarker = HandLandmarker.createFromOptions(context, options);
            Log.d(TAG, "MediaPipe HandLandmarker initialized");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing MediaPipe: " + e.getMessage());
        }
    }

    public void reset() {
        gestureHistory.clear();
        smoothLeft = smoothTop = smoothRight = smoothBottom = 0;
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    public void analyze(ImageProxy imageProxy, String targetExpression, OnGestureDetected listener) {
        if (handLandmarker == null || isProcessing || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        isProcessing = true;
        try {
            MPImage mpImage = new BitmapImageBuilder(imageProxy.toBitmap()).build();

            HandLandmarkerResult result = handLandmarker.detect(mpImage);
            
            String rawGesture = "NONE";
            Rect handBox = null;

            if (result != null && !result.landmarks().isEmpty()) {
                List<NormalizedLandmark> landmarks = result.landmarks().get(0);
                
                boolean[] isFingerRaised = checkFingersRaised(landmarks);
                
                if (isFingerRaised[1] && isFingerRaised[2] && !isFingerRaised[3] && !isFingerRaised[4]) {
                    rawGesture = "HI";
                }
                else if (checkFingerHeart(landmarks)) {
                    rawGesture = "HEART";
                }

                handBox = calculateHandBox(landmarks, mpImage.getWidth(), mpImage.getHeight());
            }

            gestureHistory.addLast(rawGesture);
            if (gestureHistory.size() > HISTORY_SIZE) gestureHistory.pollFirst();

            String confirmed = "NONE";
            int hiVotes = 0, heartVotes = 0;
            for (String g : gestureHistory) {
                if ("HI".equals(g)) hiVotes++;
                else if ("HEART".equals(g)) heartVotes++;
            }
            if (hiVotes >= CONFIRM_THRESHOLD) confirmed = "HI";
            else if (heartVotes >= CONFIRM_THRESHOLD) confirmed = "HEART";

            boolean matched = confirmed.equals(targetExpression);
            listener.onResult(new AnalysisResult(matched, handBox, confirmed));

        } catch (Exception e) {
            Log.e(TAG, "Analysis error: " + e.getMessage());
            listener.onResult(new AnalysisResult(false, null, "ERROR"));
        } finally {
            isProcessing = false;
            imageProxy.close();
        }
    }

    /**
     * Logic đếm ngón tay (dựa trên vị trí Tip vs PIP)
     * Index: 0:wrist, 1-4:thumb, 5-8:index, 9-12:middle, 13-16:ring, 17-20:pinky
     */
    private boolean[] checkFingersRaised(List<NormalizedLandmark> landmarks) {
        boolean[] raised = new boolean[5];
        NormalizedLandmark wrist = landmarks.get(0);
        
        // Ngón cái
        float dThumbTip = dist(landmarks.get(4), wrist);
        float dThumbIP = dist(landmarks.get(3), wrist);
        raised[0] = dThumbTip > dThumbIP * 1.1f;

        // Trỏ (8,6), Giữa (12,10), Áp út (16,14), Út (20,18)
        int[][] fingerIndices = {{8, 6}, {12, 10}, {16, 14}, {20, 18}};
        for (int i = 0; i < 4; i++) {
            float dTip = dist(landmarks.get(fingerIndices[i][0]), wrist);
            float dPIP = dist(landmarks.get(fingerIndices[i][1]), wrist);
            // Nếu đầu ngón tay xa cổ tay hơn khớp giữa 15%, coi như đang mở
            raised[i + 1] = dTip > dPIP * 1.15f;
        }
        
        return raised;
    }

    private boolean checkFingerHeart(List<NormalizedLandmark> landmarks) {
        // Finger heart: Đầu ngón cái (4) và đầu ngón trỏ (8) gần nhau
        float dThumbIndex = dist(landmarks.get(4), landmarks.get(8));
        return dThumbIndex < 0.12f; // Nới lỏng để dễ nhận diện hơn
    }

    private float dist(NormalizedLandmark a, NormalizedLandmark b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private Rect calculateHandBox(List<NormalizedLandmark> landmarks, int width, int height) {
        float minX = 1f, minY = 1f, maxX = 0f, maxY = 0f;
        for (NormalizedLandmark lm : landmarks) {
            if (lm.x() < minX) minX = lm.x();
            if (lm.y() < minY) minY = lm.y();
            if (lm.x() > maxX) maxX = lm.x();
            if (lm.y() > maxY) maxY = lm.y();
        }

        int pad = 40;
        int left = (int)(minX * width) - pad;
        int top = (int)(minY * height) - pad;
        int right = (int)(maxX * width) + pad;
        int bottom = (int)(maxY * height) + pad;

        // Exponential smoothing
        if (smoothRight == 0) {
            smoothLeft = left; smoothTop = top; smoothRight = right; smoothBottom = bottom;
        } else {
            smoothLeft += SMOOTH_FACTOR * (left - smoothLeft);
            smoothTop += SMOOTH_FACTOR * (top - smoothTop);
            smoothRight += SMOOTH_FACTOR * (right - smoothRight);
            smoothBottom += SMOOTH_FACTOR * (bottom - smoothBottom);
        }

        return new Rect((int)smoothLeft, (int)smoothTop, (int)smoothRight, (int)smoothBottom);
    }
}
