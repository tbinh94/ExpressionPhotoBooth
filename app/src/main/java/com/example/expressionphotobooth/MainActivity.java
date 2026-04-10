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
import com.example.expressionphotobooth.domain.usecase.VoiceTriggerAnalyzer;
import com.example.expressionphotobooth.domain.usecase.PortraitProcessor;


public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int AUDIO_PERMISSION_CODE = 101;
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
    private VoiceTriggerAnalyzer voiceTriggerAnalyzer;
    private boolean isWaitingForExpression = false;
    private boolean isWaitingForVoice = false;
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
    private boolean isVoiceTriggerMode = false; // Sub AI Mode: Voice
    private boolean isFlashPreferredOn = false;
    private boolean isScreenFlashStrong = false;
    private boolean isSoundEnabled = true;
    private SessionRepository sessionRepository;
    private SessionState sessionState;
    private MediaActionSound shutterSound;
    private final Handler captureHandler = new Handler(Looper.getMainLooper());
    private android.os.CountDownTimer fallbackCountDownTimer;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private volatile long lastAiAnalysisAtMs = 0L;
    private boolean isHandAnalyzerAvailable = false;
    private UserRole currentUserRole = UserRole.USER; // Default to normal User
    private long premiumUntil = 0L;
    private boolean isPortraitMode = false;
    private PortraitProcessor portraitProcessor;
    private boolean isNavigatingToSelection = false;

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
        voiceTriggerAnalyzer = new VoiceTriggerAnalyzer(this);
        portraitProcessor = new PortraitProcessor();
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

        // Portrait Mode toggle button (icon-only)
        ImageButton btnPortrait = findViewById(R.id.btnPortraitMode);
        if (btnPortrait != null) {
            btnPortrait.setOnClickListener(v -> {
                isPortraitMode = !isPortraitMode;
                updatePortraitButtonState(btnPortrait);
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            });
        }

        if (squareRatioButton != null) {
            squareRatioButton.setOnClickListener(v -> {
                if (!isSquareRatio) {
                    isSquareRatio = true;
                    applyPreviewRatio();
                    if (cameraProvider != null) bindCameraUseCases();
                }
            });
        }

        if (wideRatioButton != null) {
            wideRatioButton.setOnClickListener(v -> {
                if (isSquareRatio) {
                    isSquareRatio = false;
                    applyPreviewRatio();
                    if (cameraProvider != null) bindCameraUseCases();
                }
            });
        }

        com.google.android.material.button.MaterialButtonToggleGroup modeGroup = findViewById(R.id.modeContainer);
        View aiSubGroup = findViewById(R.id.aiScrollContainer);
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
                android.transition.TransitionManager.beginDelayedTransition((android.view.ViewGroup) aiSubGroup.getParent());
                aiSubGroup.setVisibility(isExpressionMode ? View.VISIBLE : View.GONE);
            }
        });

        com.google.android.material.button.MaterialButtonToggleGroup aiSubSelector = findViewById(R.id.aiSelectionContainer);
        aiSubSelector.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnAiVoice && !hasAudioPermission()) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_CODE);
                    group.check(R.id.btnAiFace);
                    Toast.makeText(this, "Microphone permission is required for Voice mode.", Toast.LENGTH_SHORT).show();
                    return;
                }

                isHandGestureMode = (checkedId == R.id.btnAiHand);
                isVoiceTriggerMode = (checkedId == R.id.btnAiVoice);
                isFaceExpressionMode = (checkedId == R.id.btnAiFace);
                
                if (isHandGestureMode && !isHandAnalyzerAvailable) {
                    Toast.makeText(this, "Hand tracking models downloading...", Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (hasCameraPermission()) {
            startCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void initOptionalGestureAnalyzer() {
        try {
            gestureAnalyzer = new GestureAnalyzer(this);
            isHandAnalyzerAvailable = true;
        } catch (UnsatisfiedLinkError e) {
            gestureAnalyzer = null;
            isHandAnalyzerAvailable = false;
            Log.w(TAG, "Gesture analyzer native lib missing: " + e.getMessage());
        } catch (Exception e) {
            gestureAnalyzer = null;
            isHandAnalyzerAvailable = false;
            Log.w(TAG, "GestureAnalyzer not yet ready: " + e.getMessage());
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.inflateMenu(R.menu.camera_menu);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_help) {
                HelpDialogUtils.showHelpDialog(this);
                return true;
            }
            return false;
        });
    }

    private void toggleFlash() {
        isFlashPreferredOn = !isFlashPreferredOn;
        sessionState.setFlashEnabled(isFlashPreferredOn);
        sessionRepository.saveSession(sessionState);
        updateFlashIcon();
        if (camera != null) {
            camera.getCameraControl().enableTorch(isFlashPreferredOn && cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA);
        }
    }

    private void toggleScreenFlashStrength() {
        isScreenFlashStrong = !isScreenFlashStrong;
        sessionState.setScreenFlashStrong(isScreenFlashStrong);
        sessionRepository.saveSession(sessionState);
        updateFlashIcon();
    }

    private void updateFlashIcon() {
        if (flashButton == null) return;
        if (isFlashPreferredOn) {
            flashButton.setImageResource(isScreenFlashStrong ? R.drawable.ic_flash_on_strong : R.drawable.ic_flash_on);
            flashButton.setColorFilter(ContextCompat.getColor(this, R.color.flash_active));
        } else {
            flashButton.setImageResource(R.drawable.ic_flash_off);
            flashButton.setColorFilter(ContextCompat.getColor(this, R.color.white));
        }
    }

    private void toggleSound() {
        isSoundEnabled = !isSoundEnabled;
        sessionState.setSoundEnabled(isSoundEnabled);
        sessionRepository.saveSession(sessionState);
        updateSoundIcon();
    }

    private void updateSoundIcon() {
        if (soundButton == null) return;
        soundButton.setImageResource(isSoundEnabled ? R.drawable.ic_volume_up : R.drawable.ic_volume_off);
    }

    private void applyPreviewRatio() {
        if (previewCard == null) {
            return;
        }
        ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) previewCard.getLayoutParams();
        if (isSquareRatio) {
            lp.dimensionRatio = "1:1";
            if (squareRatioButton != null) {
                squareRatioButton.setAlpha(1.0f);
            }
            if (wideRatioButton != null) {
                wideRatioButton.setAlpha(0.5f);
            }
        } else {
            lp.dimensionRatio = "3:4";
            if (squareRatioButton != null) {
                squareRatioButton.setAlpha(0.5f);
            }
            if (wideRatioButton != null) {
                wideRatioButton.setAlpha(1.0f);
            }
        }
        previewCard.setLayoutParams(lp);
    }

    private void initCaptureUi() {
        capturedCount = 0;
        savedImageUris.clear();
        isCapturingSequence = false;
        isWaitingForExpression = false;
        isWaitingForVoice = false;
        
        dotContainer.removeAllViews();
        for (int i = 0; i < maxPhotos; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(24, 24);
            params.setMargins(8, 0, 8, 0);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(R.drawable.dot_inactive);
            dotContainer.addView(dot);
        }
        
        captureProgress.setProgress(0);
        captureProgress.setMax(maxPhotos);
        tvCaptureStatus.setText(R.string.capture_status_ready);
        cardLastCapture.setVisibility(View.GONE);
        updateFlashIcon();
    }

    private void updateCaptureStatus(String text) {
        tvCaptureStatus.setText(text);
        captureProgress.setProgress(capturedCount);
        for (int i = 0; i < dotContainer.getChildCount(); i++) {
            dotContainer.getChildAt(i).setBackgroundResource(i < capturedCount ? R.drawable.dot_active : R.drawable.dot_inactive);
        }
    }

    private void updatePortraitButtonState(ImageButton btnPortrait) {
        if (isPortraitMode) {
            // Active: glow effect — blue background, white icon
            btnPortrait.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF3D68E8));
            btnPortrait.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            btnPortrait.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start();
        } else {
            // Inactive: default pill
            btnPortrait.setBackgroundTintList(null);
            btnPortrait.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF3D68E8));
            btnPortrait.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
        }
    }

    private void startPhotoSequence() {
        if (isCapturingSequence) return;
        
        // Check for premium if needed (extra safety)
        if (isExpressionMode) {
            AuthRepository authRepo = ((AppContainer) getApplication()).getAuthRepository();
            boolean isPremium = (currentUserRole == UserRole.PREMIUM && premiumUntil > System.currentTimeMillis());
            if (authRepo.isGuest() || (currentUserRole != UserRole.ADMIN && !isPremium)) {
                 Toast.makeText(this, "Subscription required", Toast.LENGTH_SHORT).show();
                 return;
            }
        }

        isCapturingSequence = true;
        capturedCount = 0;
        savedImageUris.clear();
        captureButton.setEnabled(false);
        findViewById(R.id.modeContainer).setEnabled(false);
        findViewById(R.id.aiSelectionContainer).setEnabled(false);
        
        updateCaptureStatus(getString(R.string.capture_status_starting));
        startCountdownAndCapture();
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (hasCameraPermission()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
            return;
        }

        if (requestCode == AUDIO_PERMISSION_CODE) {
            if (!hasAudioPermission()) {
                Toast.makeText(this, "Voice mode remains disabled without microphone permission.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
                captureButton.setEnabled(true);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                .build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(480, 640))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build();

        imageAnalysis.setAnalyzer(analysisExecutor, image -> {
            if (isNavigatingToSelection) {
                image.close();
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastAiAnalysisAtMs < AI_ANALYSIS_MIN_INTERVAL_MS) {
                image.close();
                return;
            }
            lastAiAnalysisAtMs = now;

            if (isCapturingSequence && isExpressionMode) {
                if (isHandGestureMode && gestureAnalyzer != null) {
                    gestureAnalyzer.analyze(image, targetExpression, result -> {
                        if (isWaitingForExpression && result.isMatched()) {
                            isWaitingForExpression = false;
                            runOnUiThread(this::triggerImmediateCapture);
                        }
                        if (aiOverlayView != null) {
                            runOnUiThread(() -> aiOverlayView.setDetection(result.getBoundingBox(), result.getLabel()));
                        }
                    });
                } else if (!isVoiceTriggerMode) {
                    // Face Expression Analysis
                    expressionAnalyzer.analyze(image, targetExpression, result -> {
                        if (isWaitingForExpression && result.isMatched()) {
                            isWaitingForExpression = false;
                            runOnUiThread(this::triggerImmediateCapture);
                        }
                        if (aiOverlayView != null) {
                            runOnUiThread(() -> aiOverlayView.setDetection(result.getBoundingBox(), result.getLabel()));
                        }
                    });
                } else {
                    image.close();
                }
            } else {
                image.close();
            }
        });

        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "Binding failed", e);
        }
    }

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
                switch (nextShot) {
                    case 1:
                        targetExpression = "HI";
                        tvCountdown.setText("✌️");
                        updateCaptureStatus(getString(R.string.main_capture_prompt_hand_hi, nextShot));
                        break;
                    case 2:
                        targetExpression = "HEART";
                        tvCountdown.setText("❤️");
                        updateCaptureStatus(getString(R.string.main_capture_prompt_hand_heart, nextShot));
                        break;
                    case 3:
                        targetExpression = "THUMBS_UP";
                        tvCountdown.setText("👍");
                        updateCaptureStatus(getString(R.string.main_capture_prompt_hand_thumbs_up, nextShot));
                        break;
                    case 4:
                        targetExpression = "OPEN_PALM";
                        tvCountdown.setText("🖐️");
                        updateCaptureStatus(getString(R.string.main_capture_prompt_hand_open_palm, nextShot));
                        break;
                    case 5:
                        targetExpression = "FIST";
                        tvCountdown.setText("✊");
                        updateCaptureStatus(getString(R.string.main_capture_prompt_hand_fist, nextShot));
                        break;
                    case 6:
                        targetExpression = "OK_SIGN";
                        tvCountdown.setText("👌");
                        updateCaptureStatus(getString(R.string.main_capture_prompt_hand_ok, nextShot));
                        break;
                    default:
                        targetExpression = "HI";
                        tvCountdown.setText("✌️");
                        updateCaptureStatus(getString(R.string.main_capture_prompt_hand_hi, nextShot));
                }
            } else if (isVoiceTriggerMode) {
                tvCountdown.setText("🎤");
                updateCaptureStatus(getString(R.string.main_capture_prompt_voice, nextShot));
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
                    
                    if (isVoiceTriggerMode) {
                        isWaitingForVoice = true;
                        // Hiển thị trạng thái rõ ràng hơn
                        tvCountdown.setText("🎤");
                        tvCountdown.setVisibility(View.VISIBLE);
                        // Nhấp nháy countdown icon để báo hiệu đang lắng nghe
                        tvCountdown.animate().alpha(0.3f).setDuration(600)
                                .withEndAction(() -> tvCountdown.animate().alpha(1f).setDuration(600).start())
                                .start();
                        updateCaptureStatus(getString(R.string.main_capture_prompt_voice_v2));
                        
                        voiceTriggerAnalyzer.start(new VoiceTriggerAnalyzer.OnVoiceTriggerDetected() {
                            @Override
                            public void onTrigger() {
                                if (isWaitingForVoice) {
                                    isWaitingForVoice = false;
                                    runOnUiThread(() -> {
                                        vibrate(50); // Mượt mà báo hiệu đã nhận lệnh voice
                                        tvCountdown.setAlpha(1f);
                                        tvCountdown.animate().cancel();
                                        triggerImmediateCapture();
                                    });
                                }
                            }

                            @Override
                            public void onError(String error) {
                                Log.e(TAG, "Voice error: " + error);
                                runOnUiThread(() ->
                                    Toast.makeText(MainActivity.this, getString(R.string.main_mic_error_format, error), Toast.LENGTH_SHORT).show()
                                );
                            }
                        });
                    } else {
                        isWaitingForExpression = true;
                    }

                    // Fallback 10s (hiển thị countdown trên màn hình để người dùng biết là tính năng)
                    if (fallbackCountDownTimer != null) fallbackCountDownTimer.cancel();
                    
                    fallbackCountDownTimer = new android.os.CountDownTimer(10000, 1000) {
                        public void onTick(long millisUntilFinished) {
                            if (isCapturingSequence && (isWaitingForExpression || isWaitingForVoice)) {
                                String msg = String.format(getString(R.string.fallback_countdown_msg), millisUntilFinished / 1000);
                                tvCaptureStatus.setText(msg);
                            }
                        }
                        public void onFinish() {
                            if (isCapturingSequence && (isWaitingForExpression || isWaitingForVoice)) {
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

        Runnable performCapture = () -> imageCapture.takePicture(outputOptions, cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        if (isPortraitMode) {
                            // ── Portrait post-processing ──
                            applyPortraitEffect(photoFile);
                        } else {
                            onPhotoReady(photoFile);
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                        isCapturingSequence = false;
                        runOnUiThread(() -> captureButton.setEnabled(true));
                    }
                });

        if (isFlashPreferredOn) {
            performScreenFlash(() -> {
                vibrate(80);
                performCapture.run();
            });
        } else {
            vibrate(80);
            if (isSoundEnabled) shutterSound.play(MediaActionSound.SHUTTER_CLICK);
            performCapture.run();
        }
    }

    private void performScreenFlash(Runnable onFlashMidpoint) {
        captureFlashOverlay.setVisibility(View.VISIBLE);
        captureFlashOverlay.setAlpha(0f);
        
        long fadeIn = isScreenFlashStrong ? SCREEN_FLASH_FADE_IN_STRONG_MS : SCREEN_FLASH_FADE_IN_NORMAL_MS;
        long hold = isScreenFlashStrong ? SCREEN_FLASH_HOLD_STRONG_MS : SCREEN_FLASH_HOLD_NORMAL_MS;
        long fadeOut = isScreenFlashStrong ? SCREEN_FLASH_FADE_OUT_STRONG_MS : SCREEN_FLASH_FADE_OUT_NORMAL_MS;

        captureFlashOverlay.animate()
                .alpha(1f)
                .setDuration(fadeIn)
                .withEndAction(() -> {
                    if (isSoundEnabled) shutterSound.play(MediaActionSound.SHUTTER_CLICK);
                    onFlashMidpoint.run();
                    captureFlashOverlay.postDelayed(() -> {
                        captureFlashOverlay.animate()
                                .alpha(0f)
                                .setDuration(fadeOut)
                                .withEndAction(() -> captureFlashOverlay.setVisibility(View.GONE))
                                .start();
                    }, hold);
                })
                .start();
    }

    /**
     * Gọi khi ảnh đã sẵn sàng (gốc hoặc đã qua portrait processing).
     * Cập nhật UI, count, và tiếp tục chuỗi chụp.
     */
    private void onPhotoReady(File photoFile) {
        capturedCount++;
        Uri savedUri = Uri.fromFile(photoFile);
        savedImageUris.add(savedUri);

        runOnUiThread(() -> {
            showLastCapture(savedUri);
            updateCaptureStatus(getString(R.string.capture_status_done_shot, capturedCount, maxPhotos));
            if (isCapturingSequence) {
                startCountdownAndCapture();
            }
        });
    }

    /**
     * Áp dụng hiệu ứng Portrait (blur background) lên ảnh đã lưu.
     * Chạy trên background thread, ghi đè file JPEG gốc khi hoàn thành.
     */
    private void applyPortraitEffect(File photoFile) {
        runOnUiThread(() -> updateCaptureStatus(getString(R.string.main_portrait_processing)));

        try {
            android.graphics.Bitmap decoded = android.graphics.BitmapFactory.decodeFile(photoFile.getAbsolutePath());
            if (decoded == null) {
                Log.e(TAG, "Portrait: cannot decode " + photoFile);
                onPhotoReady(photoFile);
                return;
            }

            // Đọc EXIF và xoay bitmap trước khi xử lý segmentation
            android.graphics.Bitmap original = applyExifRotation(decoded, photoFile.getAbsolutePath());

            portraitProcessor.process(original,
                    portraitBitmap -> {
                        // Ghi đè file gốc với ảnh portrait (đã xoay đúng chiều)
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(photoFile)) {
                            portraitBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, fos);
                            // Xóa EXIF rotation vì pixel đã được xoay đúng rồi
                            try {
                                androidx.exifinterface.media.ExifInterface exif =
                                        new androidx.exifinterface.media.ExifInterface(photoFile.getAbsolutePath());
                                exif.setAttribute(
                                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                                        String.valueOf(androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL));
                                exif.saveAttributes();
                            } catch (Exception ignored) {}
                            Log.d(TAG, "✅ Portrait saved: " + photoFile.getName());
                        } catch (Exception e) {
                            Log.e(TAG, "Portrait save failed", e);
                        }
                        portraitBitmap.recycle();
                        if (original != decoded) original.recycle();
                        onPhotoReady(photoFile);
                    },
                    error -> {
                        Log.e(TAG, "Portrait processing failed: " + error);
                        if (original != decoded) original.recycle();
                        runOnUiThread(() ->
                                Toast.makeText(this, R.string.main_portrait_failed, Toast.LENGTH_SHORT).show()
                        );
                        // Fallback: dùng ảnh gốc không blur
                        onPhotoReady(photoFile);
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "Portrait exception", e);
            onPhotoReady(photoFile);
        }
    }

    /**
     * Đọc EXIF orientation từ file và xoay bitmap cho đúng chiều.
     */
    private android.graphics.Bitmap applyExifRotation(android.graphics.Bitmap bitmap, String filePath) {
        try {
            androidx.exifinterface.media.ExifInterface exif =
                    new androidx.exifinterface.media.ExifInterface(filePath);
            int orientation = exif.getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL);

            int degrees = 0;
            switch (orientation) {
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90: degrees = 90; break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180: degrees = 180; break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270: degrees = 270; break;
            }
            if (degrees == 0) return bitmap;

            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(degrees);
            android.graphics.Bitmap rotated = android.graphics.Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            return rotated;
        } catch (Exception e) {
            Log.w(TAG, "Could not read EXIF: " + e.getMessage());
            return bitmap;
        }
    }

    private void triggerImmediateCapture() {
        if (!isCapturingSequence) return;
        if (fallbackCountDownTimer != null) fallbackCountDownTimer.cancel();
        isWaitingForExpression = false;
        isWaitingForVoice = false;
        voiceTriggerAnalyzer.stop();
        tvCountdown.setVisibility(View.GONE);
        takePhoto();
    }

    private void showLastCapture(Uri uri) {
        cardLastCapture.setVisibility(View.VISIBLE);
        Glide.with(this).load(uri).into(ivLastCapturePreview);
        cardLastCapture.setAlpha(0f);
        cardLastCapture.animate().alpha(1f).setDuration(300).start();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            cardLastCapture.animate().alpha(0f).setDuration(300).withEndAction(() -> cardLastCapture.setVisibility(View.GONE)).start();
        }, 2000);
    }

    private void goToSelectionPage() {
        isNavigatingToSelection = true;
        stopRealtimeProcessingAndReleaseCamera();

        Intent intent = new Intent(this, PhotoSelectionActivity.class);
        ArrayList<String> uriStrings = new ArrayList<>();
        for (Uri u : savedImageUris) uriStrings.add(u.toString());
        intent.putStringArrayListExtra(IntentKeys.EXTRA_CAPTURED_IMAGES, uriStrings);
        startActivity(intent);
    }

    private void stopRealtimeProcessingAndReleaseCamera() {
        if (fallbackCountDownTimer != null) {
            fallbackCountDownTimer.cancel();
            fallbackCountDownTimer = null;
        }
        captureHandler.removeCallbacksAndMessages(null);
        isWaitingForExpression = false;
        isWaitingForVoice = false;
        if (voiceTriggerAnalyzer != null) {
            voiceTriggerAnalyzer.stop();
        }

        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
        }

        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll();
            } catch (Exception e) {
                Log.w(TAG, "Failed to unbind camera use cases", e);
            }
        }

        imageAnalysis = null;
        imageCapture = null;
        camera = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isNavigatingToSelection && hasCameraPermission()) {
            if (cameraProvider != null && imageCapture == null) {
                bindCameraUseCases();
                captureButton.setEnabled(true);
            } else if (cameraProvider == null) {
                startCamera();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRealtimeProcessingAndReleaseCamera();
        isNavigatingToSelection = false;
        if (analysisExecutor != null) analysisExecutor.shutdown();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (shutterSound != null) shutterSound.release();
        if (voiceTriggerAnalyzer != null) voiceTriggerAnalyzer.stop();
        if (portraitProcessor != null) portraitProcessor.close();
    }

    private void vibrate(long durationMs) {
        android.os.Vibrator v = (android.os.Vibrator) getSystemService(android.content.Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(durationMs, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(durationMs);
                }
            } catch (SecurityException ex) {
                Log.w(TAG, "Vibrate permission missing at runtime: " + ex.getMessage());
            }
        }
    }
}
