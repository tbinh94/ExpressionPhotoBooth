package com.example.expressionphotobooth;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final String TAG = "CameraX";
    private PreviewView viewFinder;
    private CardView previewCard;
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private final CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
    private boolean isSquareRatio = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        CardView btnStartCamera = findViewById(R.id.btnStartCamera);
        btnStartCamera.setOnClickListener(v -> {
                    Toast.makeText(this, "Starting Camera...", Toast.LENGTH_SHORT).show();
                    startCamera();
                }
        );
        viewFinder = findViewById(R.id.viewFinder);
        previewCard = findViewById(R.id.previewCard);
        Button captureButton = findViewById(R.id.btnCapture);
        Button squareButton = findViewById(R.id.btnRatioSquare);
        Button wideButton = findViewById(R.id.btnRatioWide);

        captureButton.setOnClickListener(v -> takePhoto());
        squareButton.setOnClickListener(v -> {
            if (!isSquareRatio) {
                isSquareRatio = true;
                applyPreviewRatio();
                if (cameraProvider != null) {
                    bindCameraUseCases();
                }
            }
        });
        wideButton.setOnClickListener(v -> {
            if (isSquareRatio) {
                isSquareRatio = false;
                applyPreviewRatio();
                if (cameraProvider != null) {
                    bindCameraUseCases();
                }
            }
        });
        applyPreviewRatio();

        if (findViewById(R.id.main) != null) {
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        {
            startCamera();
        }
        else {
            checkPermission(CAMERA_PERMISSION_CODE, Manifest.permission.CAMERA);
        }
    }
    private void checkPermission(int requestCode, String permission) {
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{permission}, requestCode);
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

    private void takePhoto() {
        if (imageCapture == null) {
            Toast.makeText(this, R.string.camera_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }

        File outputDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "photobooth");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            Toast.makeText(this, R.string.failed_create_folder, Toast.LENGTH_SHORT).show();
            return;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File jpegFile = new File(outputDir, "PB_" + timestamp + ".jpg");
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(jpegFile).build();

        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                File pngFile = new File(outputDir, "PB_" + timestamp + ".png");
                boolean converted = convertJpegToPng(jpegFile, pngFile);
                if (converted) {
                    if (!jpegFile.delete()) {
                        Log.w(TAG, "Temporary JPEG could not be deleted: " + jpegFile.getAbsolutePath());
                    }
                    Toast.makeText(MainActivity.this, getString(R.string.photo_saved_path, pngFile.getAbsolutePath()), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, getString(R.string.photo_saved_path, jpegFile.getAbsolutePath()), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed", exception);
                Toast.makeText(MainActivity.this, R.string.capture_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean convertJpegToPng(File jpegFile, File pngFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(jpegFile.getAbsolutePath());
        if (bitmap == null) {
            return false;
        }

        try (FileOutputStream outputStream = new FileOutputStream(pngFile)) {
            return bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        } catch (IOException e) {
            Log.e(TAG, "PNG conversion failed", e);
            return false;
        } finally {
            bitmap.recycle();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.camera_permission_granted, Toast.LENGTH_SHORT).show();
                startCamera();
            } else {
                Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }
}