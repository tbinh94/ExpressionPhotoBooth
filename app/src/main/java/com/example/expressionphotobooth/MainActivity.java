package com.example.expressionphotobooth;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler; // THÊM DÒNG NÀY
import android.os.Looper;  // THÊM DÒNG NÀY
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List; // THÊM DÒNG NÀY
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final String TAG = "CameraX";
    private PreviewView viewFinder;
    private CardView previewCard;
    private ImageCapture imageCapture;
    private int maxPhotos;
    private int capturedCount = 0;
    private List<Uri> savedImageUris = new ArrayList<>(); // Đã hết đỏ nhờ import List
    private ProcessCameraProvider cameraProvider;
    private final CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
    private boolean isSquareRatio = true;
    private LinearLayout layoutResult;
    private RecyclerView rvPhotos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Ánh xạ View
        viewFinder = findViewById(R.id.viewFinder);
        previewCard = findViewById(R.id.previewCard);
        Button captureButton = findViewById(R.id.btnCapture);
        Button squareButton = findViewById(R.id.btnRatioSquare);
        Button wideButton = findViewById(R.id.btnRatioWide);

        // Nhận số lượng ảnh từ trang trước (PHOTO_COUNT)
        maxPhotos = getIntent().getIntExtra("PHOTO_COUNT", 4);
        captureButton.setOnClickListener(v -> takePhotoSequence());
        layoutResult = findViewById(R.id.layoutResult);
        rvPhotos = findViewById(R.id.rvPhotos);
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
            checkPermission(CAMERA_PERMISSION_CODE, Manifest.permission.CAMERA);
        }
    }

    private void checkPermission(int requestCode, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
        } else {
            startCamera();
        }
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

    private void takePhotoSequence() {
        if (capturedCount >= maxPhotos) {
            goToSelectionPage();
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

                        // SỬA LỖI TÊN LỚP Ở ĐÂY (MainActivity thay vì CaptureActivity)
                        Toast.makeText(MainActivity.this, "Captured " + capturedCount + "/" + maxPhotos, Toast.LENGTH_SHORT).show();

                        // Chụp tấm tiếp theo sau 1.5s
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            takePhotoSequence();
                        }, 1500);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Capture failed: " + exception.getMessage());
                    }
                });
    }

    private void goToSelectionPage() {
        // 1. Tắt camera để giải phóng bộ nhớ
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }

        // 2. CHÚ Ý: Chuyển đến PhotoSelectionActivity (không phải SetupActivity)
        Intent intent = new Intent(MainActivity.this, PhotoSelectionActivity.class);

        // 3. Đóng gói danh sách ảnh để gửi đi
        ArrayList<String> uriStrings = new ArrayList<>();
        for (Uri uri : savedImageUris) {
            uriStrings.add(uri.toString());
        }
        intent.putStringArrayListExtra("captured_images", uriStrings);

        // 4. Bắt đầu chuyển trang
        startActivity(intent);

        // 5. Đóng MainActivity để không bị quay lại màn hình camera khi bấm Back
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}