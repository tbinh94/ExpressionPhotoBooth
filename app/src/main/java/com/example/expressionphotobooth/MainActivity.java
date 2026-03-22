package com.example.expressionphotobooth;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.imageview.ShapeableImageView;
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
    private ImageButton btnSwitchCamera;
    private ShapeableImageView btnRecentPreview;
    private ImageView ivRecentThumbnail;
    private TextView tvCountdown;
    private ImageCapture imageCapture;
    private int maxPhotos;
    private int capturedCount = 0;
    private final List<Uri> savedImageUris = new ArrayList<>();
    private ProcessCameraProvider cameraProvider;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

    // 0: 1:1, 1: 4:3, 2: 16:9
    private int currentRatio = 1;
    private boolean isCapturingSequence = false;
    private SessionRepository sessionRepository;
    private SessionState sessionState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        AppContainer appContainer = (AppContainer) getApplication();
        sessionRepository = appContainer.getSessionRepository();
        sessionState = sessionRepository.getSession();

        int photoCountFromIntent = getIntent().getIntExtra("EXTRA_PHOTO_COUNT", 4);

        if (sessionState == null) {
            sessionState = new SessionState();
        }

        sessionState.setPhotoCount(photoCountFromIntent);
        maxPhotos = photoCountFromIntent;

        sessionRepository.saveSession(sessionState);

        setupToolbar();

        viewFinder = findViewById(R.id.viewFinder);
        previewCard = findViewById(R.id.previewCard);
        captureButton = findViewById(R.id.btnCapture);
        tvCountdown = findViewById(R.id.tvCountdown);
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera);
        btnRecentPreview = findViewById(R.id.btnRecentPreview);
        ivRecentThumbnail = findViewById(R.id.ivRecentThumbnail);

        Button squareButton = findViewById(R.id.btnRatioSquare);
        Button standardButton = findViewById(R.id.btnRatioStandard);
        Button wideButton = findViewById(R.id.btnRatioWide);

        maxPhotos = getIntent().getIntExtra("EXTRA_PHOTO_COUNT", sessionState.getPhotoCount());
        sessionState.setPhotoCount(maxPhotos);
        sessionRepository.saveSession(sessionState);

        captureButton.setEnabled(false);
        captureButton.setOnClickListener(v -> startPhotoSequence());

        if (squareButton != null) squareButton.setOnClickListener(v -> updateRatio(0));
        if (standardButton != null) standardButton.setOnClickListener(v -> updateRatio(1));
        if (wideButton != null) wideButton.setOnClickListener(v -> updateRatio(2));

        if (btnSwitchCamera != null) {
            btnSwitchCamera.setOnClickListener(v -> switchCamera());
        }

        if (btnRecentPreview != null) {
            btnRecentPreview.setOnClickListener(v -> {
                if (!savedImageUris.isEmpty()) {
                    // Logic to open preview of recent photos if needed
                }
            });
        }

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

    private void switchCamera() {
        if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        } else {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
        }
        if (cameraProvider != null) {
            bindCameraUseCases();
        }
    }

    private void updateRatio(int ratio) {
        if (currentRatio != ratio) {
            currentRatio = ratio;
            applyPreviewRatio();
            if (cameraProvider != null) {
                bindCameraUseCases();
            }
        }
    }

    private void applyPreviewRatio() {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) previewCard.getLayoutParams();
        switch (currentRatio) {
            case 0: // 1:1
                params.dimensionRatio = "1:1";
                break;
            case 1: // 4:3
                params.dimensionRatio = "3:4";
                break;
            case 2: // 16:9
                params.dimensionRatio = "9:16";
                break;
        }
        previewCard.setLayoutParams(params);
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        Preview.Builder previewBuilder = new Preview.Builder();
        if (currentRatio == 2) {
            previewBuilder.setTargetAspectRatio(AspectRatio.RATIO_16_9);
        } else {
            previewBuilder.setTargetAspectRatio(AspectRatio.RATIO_4_3);
        }

        Preview preview = previewBuilder.build();
        imageCapture = buildImageCaptureUseCase();
        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
        captureButton.setEnabled(true);
    }

    private ImageCapture buildImageCaptureUseCase() {
        ImageCapture.Builder builder = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY);

        if (currentRatio == 0) {
            builder.setTargetResolution(new Size(1080, 1080));
        } else if (currentRatio == 1) {
            builder.setTargetAspectRatio(AspectRatio.RATIO_4_3);
        } else {
            builder.setTargetAspectRatio(AspectRatio.RATIO_16_9);
        }
        return builder.build();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Lỗi khởi động camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void startPhotoSequence() {
        if (isCapturingSequence) return;
        capturedCount = 0;
        savedImageUris.clear();
        isCapturingSequence = true;
        captureButton.setEnabled(false);
        if (btnSwitchCamera != null) btnSwitchCamera.setEnabled(false);
        startCountdownAndCapture();
    }

    private void startCountdownAndCapture() {
        if (capturedCount >= maxPhotos) {
            isCapturingSequence = false;
            if (btnSwitchCamera != null) btnSwitchCamera.setEnabled(true);
            goToSelectionPage();
            return;
        }
        tvCountdown.setVisibility(View.VISIBLE);
        runCountdown(3);
    }

    private void runCountdown(int seconds) {
        if (seconds > 0) {
            tvCountdown.setText(String.valueOf(seconds));
            new Handler(Looper.getMainLooper()).postDelayed(() -> runCountdown(seconds - 1), 1000);
        } else {
            tvCountdown.setVisibility(View.GONE);
            takePhoto();
        }
    }

    private void takePhoto() {
        if (imageCapture == null) return;
        File photoFile = new File(getExternalFilesDir(null), "photo_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                capturedCount++;
                Uri savedUri = Uri.fromFile(photoFile);
                savedImageUris.add(savedUri);

                // Cập nhật thumbnail preview
                runOnUiThread(() -> {
                    ivRecentThumbnail.setPadding(0, 0, 0, 0);
                    ivRecentThumbnail.setColorFilter(null);
                    ivRecentThumbnail.setImageURI(savedUri);
                });

                new Handler(Looper.getMainLooper()).postDelayed(() -> startCountdownAndCapture(), 1000);
            }
            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                isCapturingSequence = false;
                captureButton.setEnabled(true);
                if (btnSwitchCamera != null) btnSwitchCamera.setEnabled(true);
            }
        });
    }

    private void goToSelectionPage() {
        ArrayList<String> uriStrings = new ArrayList<>();
        for (Uri uri : savedImageUris) uriStrings.add(uri.toString());
        sessionState.setCapturedImageUris(uriStrings);
        sessionRepository.saveSession(sessionState);
        Intent intent = new Intent(this, PhotoSelectionActivity.class);
        intent.putStringArrayListExtra("EXTRA_CAPTURED_IMAGES", uriStrings);
        startActivity(intent);
        finish();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }
    }

    private void requestCameraPermission() {
        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }
}
