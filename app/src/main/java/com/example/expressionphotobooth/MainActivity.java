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
        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();
        sessionState = sessionRepository.getSession();
        setupToolbar();

        // Ánh xạ View
        viewFinder = findViewById(R.id.viewFinder);
        previewCard = findViewById(R.id.previewCard);
        captureButton = findViewById(R.id.btnCapture);
        Button squareButton = findViewById(R.id.btnRatioSquare);
        Button wideButton = findViewById(R.id.btnRatioWide);

        // Nhận số lượng ảnh từ trang trước (PHOTO_COUNT)
        // Uu tien du lieu moi tu Intent, neu khong co thi phuc hoi tu repository.
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

        // Xử lý hệ thống Insets (Tràn viền)
        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        // Kiểm tra quyền
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
        if (isCapturingSequence) {
            Toast.makeText(this, "Photo sequence is running", Toast.LENGTH_SHORT).show();
            return;
        }

        if (imageCapture == null) {
            Toast.makeText(this, R.string.camera_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }

        capturedCount = 0;
        savedImageUris.clear();
        // Xoa du lieu ket qua cu khi bat dau chup moi.
        sessionState.setSelectedImageUri(null);
        sessionState.setResultImageUri(null);
        sessionState.setCapturedImageUris(new ArrayList<>());
        sessionRepository.saveSession(sessionState);
        isCapturingSequence = true;
        captureButton.setEnabled(false);
        takePhotoSequence();
    }

    private void takePhotoSequence() {
        if (capturedCount >= maxPhotos) {
            isCapturingSequence = false;
            goToSelectionPage();
            return;
        }

        if (imageCapture == null) {
            isCapturingSequence = false;
            captureButton.setEnabled(true);
            Toast.makeText(this, R.string.camera_not_ready, Toast.LENGTH_SHORT).show();
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

                        Toast.makeText(MainActivity.this, "Captured " + capturedCount + "/" + maxPhotos, Toast.LENGTH_SHORT).show();

                        new Handler(Looper.getMainLooper()).postDelayed(MainActivity.this::takePhotoSequence, 1500);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        isCapturingSequence = false;
                        captureButton.setEnabled(true);
                        Log.e(TAG, "Capture failed: " + exception.getMessage());
                        Toast.makeText(MainActivity.this, R.string.capture_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void goToSelectionPage() {
        // 1) Tat camera de giai phong bo nho.
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }

        // 2) Luu danh sach uri vao session de phong truong hop app bi recreate.
        ArrayList<String> uriStrings = new ArrayList<>();
        for (Uri uri : savedImageUris) {
            uriStrings.add(uri.toString());
        }
        sessionState.setCapturedImageUris(uriStrings);
        sessionRepository.saveSession(sessionState);

        // 3) Van gui qua Intent de giu tuong thich nguoc flow cu.
        Intent intent = new Intent(MainActivity.this, PhotoSelectionActivity.class);
        intent.putStringArrayListExtra(IntentKeys.EXTRA_CAPTURED_IMAGES, uriStrings);

        // 4) Bat dau chuyen trang.
        startActivity(intent);

        // 5) Dong MainActivity de khong quay lai camera khi bam Back.
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                captureButton.setEnabled(false);
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Cau hinh toolbar chuan de nguoi dung co the quay lai Setup nhanh.
    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}