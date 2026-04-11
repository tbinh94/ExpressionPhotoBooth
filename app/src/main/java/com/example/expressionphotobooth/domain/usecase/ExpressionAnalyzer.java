package com.example.expressionphotobooth.domain.usecase;

import android.graphics.PointF;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

public class ExpressionAnalyzer {

    public interface OnExpressionDetected {
        void onResult(AnalysisResult result);
    }

    private final FaceDetector detector;
    private boolean isProcessing = false;
    
    // Exponential smoothing cho face box
    private float smoothLeft = 0, smoothTop = 0, smoothRight = 0, smoothBottom = 0;
    private static final float SMOOTH = 0.35f;

    public ExpressionAnalyzer() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build();
        detector = FaceDetection.getClient(options);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    public void analyze(ImageProxy imageProxy, String targetExpression, OnExpressionDetected listener) {
        if (isProcessing || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        isProcessing = true;
        Image mediaImage = imageProxy.getImage();
        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
        InputImage image = InputImage.fromMediaImage(mediaImage, rotationDegrees);

        // Kich thuoc anh sau khi xoay
        int width = (rotationDegrees == 90 || rotationDegrees == 270) ? imageProxy.getHeight() : imageProxy.getWidth();
        int height = (rotationDegrees == 90 || rotationDegrees == 270) ? imageProxy.getWidth() : imageProxy.getHeight();

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty()) {
                        listener.onResult(new AnalysisResult(false, null, "NONE"));
                    } else {
                        Face face = faces.get(0);
                        Rect rawBox = face.getBoundingBox(); 
                        Rect faceBox = smoothFaceBox(rawBox);

                        float smileProb = face.getSmilingProbability() != null ? face.getSmilingProbability() : -1f;
                        float leftOpenProb = face.getLeftEyeOpenProbability() != null ? face.getLeftEyeOpenProbability() : -1f;
                        float rightOpenProb = face.getRightEyeOpenProbability() != null ? face.getRightEyeOpenProbability() : -1f;
                        
                        float headEulerZ = face.getHeadEulerAngleZ(); // Tilt (nghiêng)
                        float headEulerY = face.getHeadEulerAngleY(); // Yaw (quay trái/phải)
                        
                        String expression = "NEUTRAL";
                        // 1. Detect Smile (Highest Priority)
                        if (smileProb > 0.75f) {
                            expression = "SMILE";
                        } 
                        // 2. Detect Wink (Higher sensitivity)
                        else if (leftOpenProb < 0.35f && rightOpenProb > 0.70f) {
                            expression = "WINK";
                        } else if (rightOpenProb < 0.35f && leftOpenProb > 0.70f) {
                            expression = "WINK";
                        }
                        // 3. Detect Mouth Open (Reduced accidental triggers)
                        else {
                            FaceLandmark mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM);
                            FaceLandmark noseBase = face.getLandmark(FaceLandmark.NOSE_BASE);
                            if (mouthBottom != null && noseBase != null) {
                                float dist = Math.abs(mouthBottom.getPosition().y - noseBase.getPosition().y);
                                float faceHeight = rawBox.height();
                                // Tang nguong len 0.25 de tranh nham voi cu dong mat/nhay mat
                                if (dist / faceHeight > 0.25f) {
                                    expression = "MOUTH_OPEN";
                                }
                            }
                        }
                        
                        // 4. Detect Tilt (Nghiêng đầu) - only if no other expression detected
                        if (expression.equals("NEUTRAL")) {
                            if (headEulerZ > 18f) expression = "TILT_RIGHT";
                            else if (headEulerZ < -18f) expression = "TILT_LEFT";
                        }

                        // 5. Detect Centered (Chính giữa) - High priority if targeted
                        if (targetExpression.equals("CENTERED")) {
                            float centerX = rawBox.centerX();
                            float centerY = rawBox.centerY();
                            float imgCenterX = width / 2f;
                            float imgCenterY = height / 2f;
                            
                            boolean isCentered = Math.abs(centerX - imgCenterX) < (width * 0.12f)
                                              && Math.abs(centerY - imgCenterY) < (height * 0.12f);
                            if (isCentered) expression = "CENTERED";
                        }

                        boolean matched = expression.equals(targetExpression);
                        listener.onResult(new AnalysisResult(matched, faceBox, expression));
                    }
                })
                .addOnFailureListener(e -> listener.onResult(new AnalysisResult(false, null, "ERROR")))
                .addOnCompleteListener(task -> {
                    isProcessing = false;
                    imageProxy.close();
                });
    }

    private Rect smoothFaceBox(Rect raw) {
        if (smoothRight == 0) {
            smoothLeft   = raw.left;
            smoothTop    = raw.top;
            smoothRight  = raw.right;
            smoothBottom = raw.bottom;
        } else {
            smoothLeft   += SMOOTH * (raw.left   - smoothLeft);
            smoothTop    += SMOOTH * (raw.top    - smoothTop);
            smoothRight  += SMOOTH * (raw.right  - smoothRight);
            smoothBottom += SMOOTH * (raw.bottom - smoothBottom);
        }
        return new Rect((int)smoothLeft, (int)smoothTop, (int)smoothRight, (int)smoothBottom);
    }
}
