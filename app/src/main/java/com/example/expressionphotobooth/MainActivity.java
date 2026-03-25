package com.example.expressionphotobooth;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final String TAG = "CameraX";
    private PreviewView viewFinder;
    private CardView previewCard;
    private View captureButton;
    private ImageButton flashButton;
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
    private int maxPhotos;
    private int capturedCount = 0;
    private final List<Uri> savedImageUris = new ArrayList<>();
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
    private boolean isSquareRatio = true;
    private boolean isCapturingSequence = false;
    private boolean isFlashPreferredOn = false;
    private boolean isScreenFlashStrong = false;
    private SessionRepository sessionRepository;
    private SessionState sessionState;
    private MediaActionSound shutterSound;

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
        setupToolbar();

        shutterSound = new MediaActionSound();
        shutterSound.load(MediaActionSound.SHUTTER_CLICK);

        // Ánh xạ View
        viewFinder = findViewById(R.id.viewFinder);
        previewCard = findViewById(R.id.previewCard);
        captureButton = findViewById(R.id.btnCapture);
        flashButton = findViewById(R.id.btnFlash);
        tvCountdown = findViewById(R.id.tvCountdown);
        squareRatioButton = findViewById(R.id.btnRatioSquare);
        wideRatioButton = findViewById(R.id.btnRatioWide);
        tvCaptureStatus = findViewById(R.id.tvCaptureStatus);
        dotContainer = findViewById(R.id.dotContainer);
        captureProgress = findViewById(R.id.captureProgress);
        ivLastCapturePreview = findViewById(R.id.ivLastCapturePreview);
        cardLastCapture = findViewById(R.id.cardLastCapture);
        captureFlashOverlay = findViewById(R.id.captureFlashOverlay);

        // Nhận số lượng ảnh từ trang trước
        maxPhotos = getIntent().getIntExtra(IntentKeys.EXTRA_PHOTO_COUNT, sessionState.getPhotoCount());
        sessionState.setPhotoCount(maxPhotos);
        sessionRepository.saveSession(sessionState);
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
        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

        try {
            cameraProvider.unbindAll();
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
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
        if (flashButton != null) flashButton.setEnabled(false);
        updateCaptureProgressUi();
        updateCaptureStatus(getString(R.string.capture_status_starting, maxPhotos));
        
        // Bắt đầu chuỗi chụp với đếm ngược
        startCountdownAndCapture();
    }

    private void startCountdownAndCapture() {
        if (capturedCount >= maxPhotos) {
            isCapturingSequence = false;
            updateCaptureStatus(getString(R.string.capture_status_done, maxPhotos));
            goToSelectionPage();
            return;
        }

        tvCountdown.setVisibility(View.VISIBLE);
        int nextShot = capturedCount + 1;
        updateCaptureStatus(getString(R.string.capture_status_next, nextShot, maxPhotos));
        runCountdown(3); // Bắt đầu đếm từ 3
    }

    private void runCountdown(int seconds) {
        if (seconds > 0) {
            tvCountdown.setText(String.valueOf(seconds));
            int nextShot = Math.min(capturedCount + 1, maxPhotos);
            updateCaptureStatus(getString(R.string.capture_status_countdown, nextShot, maxPhotos, seconds));
            new Handler(Looper.getMainLooper()).postDelayed(() -> runCountdown(seconds - 1), 1000);
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
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

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
                        if (shutterSound != null) {
                            shutterSound.play(MediaActionSound.SHUTTER_CLICK);
                        }

                        updateCaptureProgressUi();
                        updateCaptureStatus(getString(R.string.capture_status_captured, capturedCount, maxPhotos));
                        
                        // Sau khi chụp xong 1 tấm, đợi 1 chút rồi đếm ngược cho tấm tiếp theo
                        new Handler(Looper.getMainLooper()).postDelayed(() -> startCountdownAndCapture(), 1000);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        isCapturingSequence = false;
                        captureButton.setEnabled(true);
                        squareRatioButton.setEnabled(true);
                        wideRatioButton.setEnabled(true);
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
        float flashPeakAlpha = isScreenFlashStrong ? 1.0f : 0.86f;
        long fadeInDuration = isScreenFlashStrong ? 110L : 90L;
        long fadeOutDuration = isScreenFlashStrong ? 220L : 170L;

        captureFlashOverlay.setVisibility(View.VISIBLE);
        captureFlashOverlay.setAlpha(0f);
        captureFlashOverlay.animate()
                .alpha(flashPeakAlpha)
                .setDuration(fadeInDuration)
                .withEndAction(() -> {
                    onFlashPeak.run();
                    captureFlashOverlay.animate()
                            .alpha(0f)
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
        if (shutterSound != null) {
            shutterSound.release();
        }
    }
}
