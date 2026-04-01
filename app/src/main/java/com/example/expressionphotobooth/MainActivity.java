package com.example.expressionphotobooth;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Camera;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.example.expressionphotobooth.domain.usecase.ExpressionAnalyzer;
import com.example.expressionphotobooth.domain.usecase.GestureAnalyzer;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.example.expressionphotobooth.domain.model.UserRole;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int CAPTURE_COUNT = 6;
    private static final String TAG = "CameraX";
    private static final long AI_ANALYSIS_MIN_INTERVAL_MS = 120L;
    // Tăng thời gian và độ sáng để mô phỏng đèn flash thật
    private static final long SCREEN_FLASH_FADE_IN_NORMAL_MS = 60L;
    private static final long SCREEN_FLASH_FADE_IN_STRONG_MS = 80L;
    private static final long SCREEN_FLASH_HOLD_NORMAL_MS = 1000L;
    private static final long SCREEN_FLASH_HOLD_STRONG_MS = 1500L;
    private static final long SCREEN_FLASH_FADE_OUT_NORMAL_MS = 300L;
    private static final long SCREEN_FLASH_FADE_OUT_STRONG_MS = 500L;
    private PreviewView viewFinder;
    private CardView previewCard;
    private AiOverlayView aiOverlayView;
    private View captureButton;
    private ImageButton flashButton;
    private ImageButton soundButton;
    private Button squareRatioButton;
    private Button wideRatioButton;
    private TextView tvCountdown;
    private TextView tvCaptureStatus;
    private LinearLayout dotContainer;
    private LinearProgressIndicator captureProgress;
    private ImageView ivLastCapturePreview;
    private View cardLastCapture;
    private View captureFlashOverlay;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private ExpressionAnalyzer expressionAnalyzer;
    private GestureAnalyzer gestureAnalyzer;
    private boolean isWaitingForExpression = false;
    private String targetExpression = "HI"; // Default to Hi gesture
    private int maxPhotos;
    private int capturedCount = 0;
    private final List<Uri> savedImageUris = new ArrayList<>();
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
    private boolean isSquareRatio = true;
    private boolean isCapturingSequence = false;
    private boolean isExpressionMode = false; // Main AI Mode flag
    private boolean isHandGestureMode = false; // Sub AI Mode: Hand (default = false, Face is default)
    private boolean isFaceExpressionMode = true; // Sub AI Mode: Face (default = true)
    private boolean isFlashPreferredOn = false;
    private boolean isScreenFlashStrong = false;
    private boolean isSoundEnabled = true;
    private SessionRepository sessionRepository;
    private SessionState sessionState;
    private MediaActionSound shutterSound;
    private final Handler captureHandler = new Handler(Looper.getMainLooper());
    private android.os.CountDownTimer fallbackCountDownTimer;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private volatile long lastAiAnalysisAtMs = 0L;
    private boolean isHandAnalyzerAvailable = false;
    private UserRole currentUserRole = UserRole.USER; // Default to normal User
    private long premiumUntil = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        AppContainer appContainer = (AppContainer) getApplication();
        sessionRepository = appContainer.getSessionRepository();
        sessionState = sessionRepository.getSession();
        isFlashPreferredOn = sessionState.isFlashEnabled();
        isScreenFlashStrong = sessionState.isScreenFlashStrong();
        isSoundEnabled = sessionState.isSoundEnabled();
        setupToolbar();

        shutterSound = new MediaActionSound();
        shutterSound.load(MediaActionSound.SHUTTER_CLICK);
        
        expressionAnalyzer = new ExpressionAnalyzer();
        initOptionalGestureAnalyzer();

        // Fetch current user info to handle premium features and expiration logic
        ((AppContainer) getApplication()).getAuthRepository().fetchCurrentUserInfo(new AuthRepository.UserInfoCallback() {
            @Override
            public void onSuccess(UserRole role, long until) {
                currentUserRole = role;
                premiumUntil = until;
                Log.d(TAG, "User role: " + role + ", premiumUntil: " + until);
            }
            @Override
            public void onError(String message) {
                Log.e(TAG, "Failed to fetch user info: " + message);
            }
        });

        // Firebase test probe removed to keep camera screen independent from backend setup.
        // Ánh xạ View
        viewFinder = findViewById(R.id.viewFinder);
        previewCard = findViewById(R.id.previewCard);
        aiOverlayView = (AiOverlayView) findViewById(R.id.overlayView);
        captureButton = findViewById(R.id.btnCapture);
        flashButton = findViewById(R.id.btnFlash);
        soundButton = findViewById(R.id.btnSound);
        tvCountdown = findViewById(R.id.tvCountdown);
        squareRatioButton = findViewById(R.id.btnRatioSquare);
        wideRatioButton = findViewById(R.id.btnRatioWide);
        tvCaptureStatus = findViewById(R.id.tvCaptureStatus);
        dotContainer = findViewById(R.id.dotContainer);
        captureProgress = findViewById(R.id.captureProgress);
        ivLastCapturePreview = findViewById(R.id.ivLastCapturePreview);
        cardLastCapture = findViewById(R.id.cardLastCapture);
        captureFlashOverlay = findViewById(R.id.captureFlashOverlay);

        // Camera luôn chụp 6 ảnh; selection phía sau sẽ yêu cầu chọn đúng 4 ảnh.
        maxPhotos = CAPTURE_COUNT;
        initCaptureUi();

        captureButton.setEnabled(false);
        captureButton.setOnClickListener(v -> startPhotoSequence());
        
        View btnSwitchCamera = findViewById(R.id.btnSwitchCamera);
        if (btnSwitchCamera != null) {
            btnSwitchCamera.setOnClickListener(v -> {
                if (cameraProvider != null && !isCapturingSequence) {
                    if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                    } else {
                        cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                    }
                    bindCameraUseCases();
                }
            });
        }

        if (flashButton != null) {
            flashButton.setOnClickListener(v -> toggleFlash());
            flashButton.setOnLongClickListener(v -> {
                toggleScreenFlashStrength();
                return true;
            });
        }

        if (soundButton != null) {
            updateSoundIcon();
            soundButton.setOnClickListener(v -> toggleSound());
        }

        squareRatioButton.setOnClickListener(v -> {
            if (!isSquareRatio) {
                isSquareRatio = true;
                applyPreviewRatio();
                if (cameraProvider != null) bindCameraUseCases();
            }
        });

        wideRatioButton.setOnClickListener(v -> {
            if (isSquareRatio) {
                isSquareRatio = false;
                applyPreviewRatio();
                if (cameraProvider != null) bindCameraUseCases();
            }
        });

        com.google.android.material.button.MaterialButtonToggleGroup modeGroup = findViewById(R.id.modeContainer);
        View aiSubGroup = findViewById(R.id.aiSelectionContainer);
        modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                AuthRepository authRepo = ((AppContainer) getApplication()).getAuthRepository();
                if (checkedId == R.id.btnModeExpression) {
                    if (authRepo.isGuest()) {
                        group.check(R.id.btnModeCountdown); // Force back to auto
                        HelpDialogUtils.showCenteredNotice(
                                this,
                                getString(R.string.main_ai_login_required_title),
                                getString(R.string.main_ai_login_required_message),
                                false
                        );
                        return;
                    }

                    // Check for Premium Subscription if not Admin
                    boolean isPremium = (currentUserRole == UserRole.PREMIUM && premiumUntil > System.currentTimeMillis());
                    
                    if (currentUserRole != UserRole.ADMIN && !isPremium) {
                        group.check(R.id.btnModeCountdown); // Force back to auto
                        String paymentUrl = "https://img.vietqr.io/image/MB-56111166662004-compact.png" +
                                "?amount=50000&addInfo=Premium%20Sub%20" + authRepo.getCurrentEmail() +
                                "&accountName=PHOTO%20BOOTH";
                        HelpDialogUtils.showSubscriptionQR(this, paymentUrl);
                        return;
                    }
                }
                
                isExpressionMode = (checkedId == R.id.btnModeExpression);
                aiSubGroup.setVisibility(isExpressionMode ? View.VISIBLE : View.GONE);
                if (isExpressionMode) {
                    Toast.makeText(this, R.string.main_mode_ai_selected, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.main_mode_auto_selected, Toast.LENGTH_SHORT).show();
                }
            }
        });

        com.google.android.material.button.MaterialButtonToggleGroup aiSubSelector = 
            (com.google.android.material.button.MaterialButtonToggleGroup) findViewById(R.id.aiSelectionContainer);
        com.google.android.material.button.MaterialButton btnAiHand = findViewById(R.id.btnAiHand);
        if (btnAiHand != null && !isHandAnalyzerAvailable) {
            btnAiHand.setEnabled(false);
            btnAiHand.setAlpha(0.45f);
        }
        
        // Đồng bộ trạng thái ban đầu theo nút đang được check trong XML (btnAiFace)
        isHandGestureMode = false;
        isFaceExpressionMode = true;
        
        aiSubSelector.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return; // chỉ xử lý khi nút được chọn
            if (checkedId == R.id.btnAiHand && !isHandAnalyzerAvailable) {
                group.check(R.id.btnAiFace);
                Toast.makeText(this, R.string.main_ai_hand_unavailable, Toast.LENGTH_SHORT).show();
                return;
            }
            isHandGestureMode = (checkedId == R.id.btnAiHand);
            isFaceExpressionMode = (checkedId == R.id.btnAiFace);
            if (isFaceExpressionMode) {
                Toast.makeText(this, R.string.main_ai_face_selected, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.main_ai_action_selected, Toast.LENGTH_SHORT).show();
            }
        });

        applyPreviewRatio();

        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestCameraPermission();
        }
    }

    private void requestCameraPermission() {
        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
    }

    private void initOptionalGestureAnalyzer() {
        if (!isMediaPipeHandSupportedAbi()) {
            Log.w(TAG, "Hand analyzer disabled on this ABI: " + java.util.Arrays.toString(Build.SUPPORTED_ABIS));
            isHandAnalyzerAvailable = false;
            gestureAnalyzer = null;
            return;
        }

        try {
            gestureAnalyzer = new GestureAnalyzer(this);
            isHandAnalyzerAvailable = (gestureAnalyzer != null);
        } catch (Throwable throwable) {
            // Prevent app crash if MediaPipe native library is missing on current device.
            Log.e(TAG, "Failed to initialize hand analyzer", throwable);
            isHandAnalyzerAvailable = false;
            gestureAnalyzer = null;
        }
    }

    private boolean isMediaPipeHandSupportedAbi() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if (abi != null && abi.startsWith("arm64")) {
                return true;
            }
        }
        return false;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Failed to start camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        Preview preview = new Preview.Builder().build();
        imageCapture = buildImageCaptureUseCase();
        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                // Reduce ML load to keep preview smooth on mid-range devices.
                .setTargetResolution(new Size(640, 480))
                .build();
        
        imageAnalysis.setAnalyzer(analysisExecutor, imageProxy -> {
            boolean isWaiting = isWaitingForExpression;

            // Throttle AI analyzer frequency to reduce CPU spikes and UI jank.
            long now = SystemClock.elapsedRealtime();
            if ((now - lastAiAnalysisAtMs) < AI_ANALYSIS_MIN_INTERVAL_MS) {
                imageProxy.close();
                return;
            }
            lastAiAnalysisAtMs = now;
            
            if (isExpressionMode) {
                if (isHandGestureMode && gestureAnalyzer != null) {
                    gestureAnalyzer.analyzeImageProxy(imageProxy, (gesture, box) -> {
                        // Luôn hiện khung xanh để test kể cả khi không trong mode capture
                        updateAiOverlay(box, gesture, android.graphics.Color.parseColor("#00E676"),
                                imageProxy.getWidth(), imageProxy.getHeight(),
                                imageProxy.getImageInfo().getRotationDegrees());
                        // Chỉ trigger nếu gesture nằm trong phần HIỂN THỊ của camera
                        if (isWaiting && targetExpression.equals(gesture)
                                && isBoxVisibleInPreview(box, imageProxy.getWidth(), imageProxy.getHeight(),
                                                        imageProxy.getImageInfo().getRotationDegrees())) {
                            triggerImmediateCapture();
                        }
                    });
                } else if (isFaceExpressionMode && expressionAnalyzer != null) {
                    expressionAnalyzer.analyzeImageProxy(imageProxy, (expression, faceBox) -> {
                        updateAiOverlay(faceBox, expression, android.graphics.Color.parseColor("#00B0FF"),
                                imageProxy.getWidth(), imageProxy.getHeight(),
                                imageProxy.getImageInfo().getRotationDegrees());
                        if (isWaiting && targetExpression.equals(expression)
                                && isBoxVisibleInPreview(faceBox, imageProxy.getWidth(), imageProxy.getHeight(),
                                                        imageProxy.getImageInfo().getRotationDegrees())) {
                            triggerImmediateCapture();
                        }
                    });
                } else {
                    imageProxy.close();
                }
            } else {
                // Tắt hoàn toàn overlay và detector khi đang ở Tự động
                updateAiOverlay(null, "");
                imageProxy.close();
            }
        });
        
        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

        try {
            cameraProvider.unbindAll();
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);
            captureButton.setEnabled(true);
            applyFlashMode();
            updateFlashAvailability();
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind camera use cases", e);
            captureButton.setEnabled(false);
            if (flashButton != null) flashButton.setEnabled(false);
            Toast.makeText(this, R.string.camera_not_ready, Toast.LENGTH_SHORT).show();
        }
    }

    private ImageCapture buildImageCaptureUseCase() {
        ImageCapture.Builder builder = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY);

        if (isSquareRatio) {
            builder.setTargetResolution(new Size(1080, 1080));
        } else {
            builder.setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_16_9);
        }
        return builder.build();
    }

    private void applyPreviewRatio() {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) previewCard.getLayoutParams();
        params.dimensionRatio = isSquareRatio ? "1:1" : "16:9";
        previewCard.setLayoutParams(params);
    }

    private void startPhotoSequence() {
        if (isCapturingSequence) return;

        capturedCount = 0;
        savedImageUris.clear();
        cardLastCapture.setVisibility(View.GONE);
        isCapturingSequence = true;
        captureButton.setEnabled(false);
        squareRatioButton.setEnabled(false);
        wideRatioButton.setEnabled(false);
        findViewById(R.id.modeContainer).setEnabled(false);
        if (flashButton != null) flashButton.setEnabled(false);
        updateCaptureProgressUi();
        updateCaptureStatus(getString(R.string.capture_status_starting, maxPhotos));
        
        // Bắt đầu chuỗi chụp
        startCountdownAndCapture();
    }

    private void triggerImmediateCapture() {
        if (fallbackCountDownTimer != null) {
            fallbackCountDownTimer.cancel();
            fallbackCountDownTimer = null;
        }
        isWaitingForExpression = false;
        tvCountdown.post(() -> {
            if (aiOverlayView != null) aiOverlayView.updateOverlay(null, "");
            tvCountdown.setVisibility(View.GONE);
            updateCaptureStatus(getString(R.string.capture_status_captured, capturedCount + 1, maxPhotos));
            takePhoto();
        });
    }

    private void updateAiOverlay(Rect box, String label, int color, int imgW, int imgH, int rotation) {
        if (aiOverlayView == null) return;
        aiOverlayView.post(() -> {
            java.util.List<Rect> boxes = new java.util.ArrayList<>();
            int viewW = aiOverlayView.getWidth();
            int viewH = aiOverlayView.getHeight();

            if (box != null && viewW > 0 && viewH > 0) {
                // Sau khi rotation, portrait device: imgW/imgH có thể được đảo
                float srcW = (rotation == 90 || rotation == 270) ? (float) imgH : (float) imgW;
                float srcH = (rotation == 90 || rotation == 270) ? (float) imgW : (float) imgH;

                // Tính CENTER_CROP: tìm scale để cả 2 chiều đều >= view (giống scaleType=centerCrop)
                float scaleX = viewW / srcW;
                float scaleY = viewH / srcH;
                float scale = Math.max(scaleX, scaleY); // CENTER_CROP lấy scale lớn nhất

                // Phần thừa bị crop ở giữa
                float cropOffsetX = (srcW * scale - viewW) / 2f;
                float cropOffsetY = (srcH * scale - viewH) / 2f;

                // Chuyển đổi: pixel ảnh -> pixel view (sau crop)
                int left   = (int)(box.left   * scale - cropOffsetX);
                int top    = (int)(box.top    * scale - cropOffsetY);
                int right  = (int)(box.right  * scale - cropOffsetX);
                int bottom = (int)(box.bottom * scale - cropOffsetY);

                // Mirror X cho camera trước
                if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                    int newLeft = viewW - right;
                    right = viewW - left;
                    left = newLeft;
                }

                // Clip vào phần hiển thị thực tế của overlay
                left   = Math.max(0, left);
                top    = Math.max(0, top);
                right  = Math.min(viewW, right);
                bottom = Math.min(viewH, bottom);

                // Chỉ vẽ nếu hộp hợp lệ
                if (left < right && top < bottom) {
                    boxes.add(new Rect(left, top, right, bottom));
                }
            }
            aiOverlayView.updateOverlay(boxes, label, color);
        });
    }

    // Overload for calls without images (e.g. clearing)
    private void updateAiOverlay(Rect box, String label) {
        updateAiOverlay(box, label, android.graphics.Color.parseColor("#00E676"), 640, 480, 90);
    }

    /**
     * Kiểm tra xem trung tâm bounding box có nằm trong phần hiển thị thực tế
     * (sau CENTER_CROP) của camera preview không.
     */
    private boolean isBoxVisibleInPreview(Rect box, int imgW, int imgH, int rotation) {
        if (box == null || aiOverlayView == null) return false;
        int viewW = aiOverlayView.getWidth();
        int viewH = aiOverlayView.getHeight();
        if (viewW <= 0 || viewH <= 0) return true; // biến mết thì cứ cho qua

        float srcW = (rotation == 90 || rotation == 270) ? (float) imgH : (float) imgW;
        float srcH = (rotation == 90 || rotation == 270) ? (float) imgW : (float) imgH;

        float scale = Math.max(viewW / srcW, viewH / srcH);
        float cropOffsetX = (srcW * scale - viewW) / 2f;
        float cropOffsetY = (srcH * scale - viewH) / 2f;

        // Tính trung tâm hộp sau transform
        float cx = ((box.left + box.right) / 2f) * scale - cropOffsetX;
        float cy = ((box.top + box.bottom) / 2f) * scale - cropOffsetY;

        // Mirror cho camera trước
        if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            cx = viewW - cx;
        }

        return cx >= 0 && cx <= viewW && cy >= 0 && cy <= viewH;
    }

    private void startCountdownAndCapture() {
        if (capturedCount >= maxPhotos) {
            isCapturingSequence = false;
            updateCaptureStatus(getString(R.string.capture_status_done, maxPhotos));
            findViewById(R.id.modeContainer).setEnabled(true);
            findViewById(R.id.aiSelectionContainer).setEnabled(true);
            goToSelectionPage();
            return;
        }

        tvCountdown.setVisibility(View.VISIBLE);
        int nextShot = capturedCount + 1;
        
        if (isExpressionMode) {
            if (isHandGestureMode) {
                if (nextShot % 2 != 0) {
                    targetExpression = "HI";
                    tvCountdown.setText("✌️");
                    updateCaptureStatus(getString(R.string.main_capture_prompt_hand_hi, nextShot));
                } else {
                    targetExpression = "HEART";
                    tvCountdown.setText("❤️");
                    updateCaptureStatus(getString(R.string.main_capture_prompt_hand_heart, nextShot));
                }
            } else {
                // Face mode
                if (nextShot % 2 != 0) {
                    targetExpression = "SMILE";
                    tvCountdown.setText(":D");
                    updateCaptureStatus(getString(R.string.main_capture_prompt_face_smile, nextShot));
                } else {
                    targetExpression = "WINK_RIGHT"; // Simple specific wink
                    tvCountdown.setText(";)");
                    updateCaptureStatus(getString(R.string.main_capture_prompt_face_wink, nextShot));
                }
            }

            // Reset detector state and wait a bit longer (1.5s) to allow user to see the prompt
            captureHandler.postDelayed(() -> {
                if (isCapturingSequence) {
                    if (gestureAnalyzer != null) gestureAnalyzer.reset();
                    isWaitingForExpression = true;
                    // Fallback 10s (hiển thị countdown trên màn hình để người dùng biết là tính năng)
                    if (fallbackCountDownTimer != null) fallbackCountDownTimer.cancel();
                    
                    fallbackCountDownTimer = new android.os.CountDownTimer(10000, 1000) {
                        public void onTick(long millisUntilFinished) {
                            if (isCapturingSequence && isWaitingForExpression) {
                                String msg = String.format(getString(R.string.fallback_countdown_msg), millisUntilFinished / 1000);
                                tvCaptureStatus.setText(msg);
                            }
                        }
                        public void onFinish() {
                            if (isCapturingSequence && isWaitingForExpression) {
                                triggerImmediateCapture();
                            }
                        }
                    }.start();
                }
            }, 1500);
        } else {
            // Standard Countdown Mode
            runCountdown(3);
        }
    }

    private void runCountdown(int seconds) {
        if (seconds > 0) {
            tvCountdown.setText(String.valueOf(seconds));
            int nextShot = Math.min(capturedCount + 1, maxPhotos);
            updateCaptureStatus(getString(R.string.capture_status_countdown, nextShot, maxPhotos, seconds));
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isCapturingSequence && !isExpressionMode) {
                    runCountdown(seconds - 1);
                }
            }, 1000);
        } else {
            tvCountdown.setVisibility(View.GONE);
            takePhoto();
        }
    }

    private void takePhoto() {
        if (imageCapture == null) {
            isCapturingSequence = false;
            captureButton.setEnabled(true);
            return;
        }

        File photoFile = new File(getExternalFilesDir(null), "photo_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.Metadata captureMetadata = new ImageCapture.Metadata();
        // Save final photo with real orientation (non-mirrored) so collage/export is not flipped.
        captureMetadata.setReversedHorizontal(false);
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile)
                .setMetadata(captureMetadata)
                .build();

        Runnable performCapture = () -> imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        capturedCount++;
                        Uri savedUri = Uri.fromFile(photoFile);
                        savedImageUris.add(savedUri);
                        showLastCapturePreview(savedUri);
                        if (!shouldUseScreenFlash()) {
                            playCaptureFlash();
                        }
                        
                        // Phát âm thanh chụp hình
                        if (shutterSound != null && isSoundEnabled) {
                            shutterSound.play(MediaActionSound.SHUTTER_CLICK);
                        }

                        updateCaptureProgressUi();
                        updateCaptureStatus(getString(R.string.capture_status_captured, capturedCount, maxPhotos));
                        
                        // Sau khi chụp xong 1 tấm, đợi 1 chút rồi đếm ngược cho tấm tiếp theo
                        // Tăng delay lên 2.5s nếu dùng flash để flash kịp hồi và ổn định ánh sáng
                        long nextCaptureDelay = isFlashPreferredOn ? 2500L : 1000L;
                        new Handler(Looper.getMainLooper()).postDelayed(() -> startCountdownAndCapture(), nextCaptureDelay);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        isCapturingSequence = false;
                        isWaitingForExpression = false;
                        captureButton.setEnabled(true);
                        squareRatioButton.setEnabled(true);
                        wideRatioButton.setEnabled(true);
                        findViewById(R.id.modeContainer).setEnabled(true);
                        updateFlashAvailability();
                        updateCaptureStatus(getString(R.string.capture_status_error));
                        Toast.makeText(MainActivity.this, R.string.capture_failed, Toast.LENGTH_SHORT).show();
                    }
                });

        if (shouldUseScreenFlash()) {
            playScreenFlashThenCapture(performCapture);
        } else {
            performCapture.run();
        }
    }

    private void goToSelectionPage() {
        if (cameraProvider != null) cameraProvider.unbindAll();

        squareRatioButton.setEnabled(true);
        wideRatioButton.setEnabled(true);

        ArrayList<String> uriStrings = new ArrayList<>();
        for (Uri uri : savedImageUris) uriStrings.add(uri.toString());
        
        sessionState.setCapturedImageUris(uriStrings);
        sessionRepository.saveSession(sessionState);

        Intent intent = new Intent(MainActivity.this, PhotoSelectionActivity.class);
        intent.putStringArrayListExtra(IntentKeys.EXTRA_CAPTURED_IMAGES, uriStrings);
        startActivity(intent);
        finish();
    }

    private void initCaptureUi() {
        captureProgress.setMax(Math.max(maxPhotos, 1));
        captureProgress.setProgress(0);
        dotContainer.removeAllViews();
        for (int i = 0; i < maxPhotos; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpToPx(10), dpToPx(10));
            params.setMargins(dpToPx(4), 0, dpToPx(4), 0);
            params.gravity = Gravity.CENTER_VERTICAL;
            dot.setLayoutParams(params);
            dot.setBackgroundResource(R.drawable.bg_capture_dot_inactive);
            dotContainer.addView(dot);
        }
        updateCaptureStatus(getString(R.string.capture_status_ready));
    }

    private void updateCaptureProgressUi() {
        captureProgress.setProgressCompat(capturedCount, true);
        for (int i = 0; i < dotContainer.getChildCount(); i++) {
            View dot = dotContainer.getChildAt(i);
            dot.setBackgroundResource(i < capturedCount ? R.drawable.bg_capture_dot_active : R.drawable.bg_capture_dot_inactive);
        }
    }

    private void updateCaptureStatus(String message) {
        tvCaptureStatus.setText(message);
    }

    private void showLastCapturePreview(Uri uri) {
        cardLastCapture.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(ivLastCapturePreview);
    }

    private void playCaptureFlash() {
        captureFlashOverlay.setVisibility(View.VISIBLE);
        captureFlashOverlay.setAlpha(0.6f);
        captureFlashOverlay.animate()
                .alpha(0f)
                .setDuration(220)
                .withEndAction(() -> captureFlashOverlay.setVisibility(View.GONE))
                .start();
    }

    private void toggleFlash() {
        if (camera == null || imageCapture == null) {
            Toast.makeText(this, R.string.camera_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        isFlashPreferredOn = !isFlashPreferredOn;
        sessionState.setFlashEnabled(isFlashPreferredOn);
        sessionRepository.saveSession(sessionState);
        applyFlashMode();
        updateFlashAvailability();
        Toast.makeText(this, isFlashPreferredOn ? R.string.camera_flash_on : R.string.camera_flash_off, Toast.LENGTH_SHORT).show();
    }

    private void toggleScreenFlashStrength() {
        isScreenFlashStrong = !isScreenFlashStrong;
        sessionState.setScreenFlashStrong(isScreenFlashStrong);
        sessionRepository.saveSession(sessionState);
        int levelRes = isScreenFlashStrong ? R.string.camera_screen_flash_level_strong : R.string.camera_screen_flash_level_normal;
        Toast.makeText(this, getString(R.string.camera_screen_flash_level_changed, getString(levelRes)), Toast.LENGTH_SHORT).show();
    }

    private void applyFlashMode() {
        if (imageCapture == null) return;
        imageCapture.setFlashMode(shouldUseHardwareFlash() ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF);
    }

    private void updateFlashAvailability() {
        if (flashButton == null) return;

        boolean canToggleFlash = camera != null && !isCapturingSequence;
        flashButton.setEnabled(canToggleFlash);
        flashButton.setAlpha(canToggleFlash ? 1f : 0.35f);
        flashButton.setImageResource(isFlashPreferredOn ? R.drawable.ic_flash_on_24 : R.drawable.ic_flash_off_24);
    }

    private void toggleSound() {
        isSoundEnabled = !isSoundEnabled;
        sessionState.setSoundEnabled(isSoundEnabled);
        sessionRepository.saveSession(sessionState);
        updateSoundIcon();
        Toast.makeText(this, isSoundEnabled ? R.string.main_sound_on : R.string.main_sound_off, Toast.LENGTH_SHORT).show();
    }

    private void updateSoundIcon() {
        if (soundButton != null) {
            soundButton.setImageResource(isSoundEnabled ? R.drawable.ic_sound_on : R.drawable.ic_sound_off);
        }
    }

    private boolean shouldUseHardwareFlash() {
        return isFlashPreferredOn
                && camera != null
                && camera.getCameraInfo().hasFlashUnit()
                && cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA;
    }

    private boolean shouldUseScreenFlash() {
        return isFlashPreferredOn && cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA;
    }

    private void playScreenFlashThenCapture(Runnable onFlashPeak) {
        float flashPeakAlpha = 1.0f; // Luôn dùng độ sáng tối đa cho flash màn hình
        long fadeInDuration = isScreenFlashStrong ? SCREEN_FLASH_FADE_IN_STRONG_MS : SCREEN_FLASH_FADE_IN_NORMAL_MS;
        long flashHoldDuration = isScreenFlashStrong ? SCREEN_FLASH_HOLD_STRONG_MS : SCREEN_FLASH_HOLD_NORMAL_MS;
        long fadeOutDuration = isScreenFlashStrong ? SCREEN_FLASH_FADE_OUT_STRONG_MS : SCREEN_FLASH_FADE_OUT_NORMAL_MS;

        captureFlashOverlay.animate().cancel();
        captureFlashOverlay.setVisibility(View.VISIBLE);
        captureFlashOverlay.setAlpha(0f);
        captureFlashOverlay.animate()
                .alpha(flashPeakAlpha)
                .setDuration(fadeInDuration)
                .withEndAction(() -> {
                    onFlashPeak.run();
                    captureFlashOverlay.animate()
                            .alpha(0f)
                            .setStartDelay(flashHoldDuration)
                            .setDuration(fadeOutDuration)
                            .withEndAction(() -> captureFlashOverlay.setVisibility(View.GONE))
                            .start();
                })
                .start();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else if (requestCode == CAMERA_PERMISSION_CODE) {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        analysisExecutor.shutdown();
        if (shutterSound != null) {
            shutterSound.release();
        }
    }
}
