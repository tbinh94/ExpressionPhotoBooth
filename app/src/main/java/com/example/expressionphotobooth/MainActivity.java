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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
    private Button captureButton;
    private TextView tvCountdown;
    private ImageCapture imageCapture;
    private int maxPhotos;
    private int capturedCount = 0;
    private final List<Uri> savedImageUris = new ArrayList<>();
    private ProcessCameraProvider cameraProvider;
    private final CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
    private boolean isSquareRatio = true;
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
        setupToolbar();

        // Ánh xạ View
        viewFinder = findViewById(R.id.viewFinder);
        previewCard = findViewById(R.id.previewCard);
        captureButton = findViewById(R.id.btnCapture);
        tvCountdown = findViewById(R.id.tvCountdown);
        Button squareButton = findViewById(R.id.btnRatioSquare);
        Button wideButton = findViewById(R.id.btnRatioWide);

        // Nhận số lượng ảnh từ trang trước
        maxPhotos = getIntent().getIntExtra(IntentKeys.EXTRA_PHOTO_COUNT, sessionState.getPhotoCount());
        sessionState.setPhotoCount(maxPhotos);
        sessionRepository.saveSession(sessionState);

        captureButton.setEnabled(false);
        captureButton.setOnClickListener(v -> startPhotoSequence());
        
        squareButton.setOnClickListener(v -> {
            if (!isSquareRatio) {
                isSquareRatio = true;
                applyPreviewRatio();
                if (cameraProvider != null) bindCameraUseCases();
            }
        });

        wideButton.setOnClickListener(v -> {
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

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
        captureButton.setEnabled(true);
    }

    private ImageCapture buildImageCaptureUseCase() {
        ImageCapture.Builder builder = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY);

        if (isSquareRatio) {
            builder.setTargetResolution(new Size(1080, 1080));
        } else {
            builder.setTargetResolution(new Size(1920, 1080));
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
        isCapturingSequence = true;
        captureButton.setEnabled(false);
        
        // Bắt đầu chuỗi chụp với đếm ngược
        startCountdownAndCapture();
    }

    private void startCountdownAndCapture() {
        if (capturedCount >= maxPhotos) {
            isCapturingSequence = false;
            goToSelectionPage();
            return;
        }

        tvCountdown.setVisibility(View.VISIBLE);
        runCountdown(3); // Bắt đầu đếm từ 3
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
        if (imageCapture == null) {
            isCapturingSequence = false;
            captureButton.setEnabled(true);
            return;
        }

        File photoFile = new File(getExternalFilesDir(null), "photo_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        capturedCount++;
                        savedImageUris.add(Uri.fromFile(photoFile));
                        
                        // Sau khi chụp xong 1 tấm, đợi 1 chút rồi đếm ngược cho tấm tiếp theo
                        new Handler(Looper.getMainLooper()).postDelayed(() -> startCountdownAndCapture(), 1000);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        isCapturingSequence = false;
                        captureButton.setEnabled(true);
                        Toast.makeText(MainActivity.this, "Capture failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void goToSelectionPage() {
        if (cameraProvider != null) cameraProvider.unbindAll();

        ArrayList<String> uriStrings = new ArrayList<>();
        for (Uri uri : savedImageUris) uriStrings.add(uri.toString());
        
        sessionState.setCapturedImageUris(uriStrings);
        sessionRepository.saveSession(sessionState);

        Intent intent = new Intent(MainActivity.this, PhotoSelectionActivity.class);
        intent.putStringArrayListExtra(IntentKeys.EXTRA_CAPTURED_IMAGES, uriStrings);
        startActivity(intent);
        finish();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }
}