package com.example.expressionphotobooth.domain.usecase;

import android.graphics.Rect;
import android.media.Image;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

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
        InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

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

                        String expression;
                        if (smileProb > 0.70f) {
                            expression = "SMILE";
                        } else if (leftOpenProb < 0.20f && rightOpenProb > 0.85f) {
                            expression = "WINK_LEFT";
                        } else if (rightOpenProb < 0.20f && leftOpenProb > 0.85f) {
                            expression = "WINK_RIGHT";
                        } else {
                            expression = "NEUTRAL";
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
