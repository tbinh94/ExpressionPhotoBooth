package com.example.expressionphotobooth;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.expressionphotobooth.data.video.TimelapseVideoEncoder;
import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.example.expressionphotobooth.domain.usecase.CreateTimelapseVideoUseCase;
import com.example.expressionphotobooth.domain.usecase.CreateVerticalCollageUseCase;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ResultActivity extends AppCompatActivity {
    private Uri resultUri;
    private SessionRepository sessionRepository;
    private SessionState sessionState;
    private CreateTimelapseVideoUseCase createTimelapseVideoUseCase;
    private CreateVerticalCollageUseCase createVerticalCollageUseCase;
    private MaterialButton btnNext; // Chính là btnSaveVideo

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_result);
        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();
        sessionState = sessionRepository.getSession();
        createTimelapseVideoUseCase = new CreateTimelapseVideoUseCase(new TimelapseVideoEncoder());
        createVerticalCollageUseCase = new CreateVerticalCollageUseCase();

        ImageView ivFinalResult = findViewById(R.id.ivFinalResult);
        MaterialButton btnBack = findViewById(R.id.btnBack);
        MaterialButton btnSavePng = findViewById(R.id.btnSavePng);
        btnNext = findViewById(R.id.btnNext);

        Toast.makeText(this, "Đang xử lý ảnh và ghép frame...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            List<String> imageUrisToCollage = new ArrayList<>();
            for (String originalUri : sessionState.getCapturedImageUris()) {
                String editedUri = sessionState.getEditedImageUris().get(originalUri);
                imageUrisToCollage.add(editedUri != null ? editedUri : originalUri);
            }

            int selectedFrameId = sessionState.getSelectedFrameResId();
            Bitmap collageWithFrame = createVerticalCollageUseCase.execute(this, imageUrisToCollage, selectedFrameId);

            runOnUiThread(() -> {
                if (collageWithFrame != null) {
                    resultUri = saveBitmapToCache(collageWithFrame);
                    ivFinalResult.setAdjustViewBounds(true);
                    ivFinalResult.setImageBitmap(collageWithFrame);
                    sessionState.setResultImageUri(resultUri.toString());
                    sessionRepository.saveSession(sessionState);
                }
            });
        }).start();

        btnSavePng.setOnClickListener(v -> {
            Animation press = AnimationUtils.loadAnimation(this, R.anim.btn_press);
            btnSavePng.startAnimation(press);
            saveCurrentResultAsPng();
        });

        btnNext.setOnClickListener(v -> {
            Animation press = AnimationUtils.loadAnimation(this, R.anim.btn_press);
            btnNext.startAnimation(press);
            press.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation animation) {}
                @Override public void onAnimationRepeat(Animation animation) {}
                @Override
                public void onAnimationEnd(Animation animation) {
                    exportTimelapseVideoAndFinish();
                }
            });
        });

        btnBack.setOnClickListener(v -> {
            Animation press = AnimationUtils.loadAnimation(this, R.anim.btn_press);
            btnBack.startAnimation(press);
            press.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation animation) {}
                @Override public void onAnimationRepeat(Animation animation) {}
                @Override
                public void onAnimationEnd(Animation animation) {
                    sessionRepository.clearSession();
                    Intent intent = new Intent(ResultActivity.this, HomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                }
            });
        });
    }

    private Uri saveBitmapToCache(Bitmap bitmap) {
        File file = new File(getCacheDir(), "collage_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            return Uri.fromFile(file);
        } catch (IOException e) {
            return null;
        }
    }

    private void exportTimelapseVideoAndFinish() {
        List<String> sourceUris = sessionState.getTimelapseImageUris();
        
        // Nếu không có timelapseImageUris, thử dùng capturedImageUris làm fallback để tránh lỗi quay về home mà không làm gì
        if (sourceUris == null || sourceUris.isEmpty()) {
            sourceUris = sessionState.getCapturedImageUris();
        }

        if (sourceUris == null || sourceUris.isEmpty()) {
            Toast.makeText(this, "Không có dữ liệu ảnh để tạo video!", Toast.LENGTH_SHORT).show();
            navigateToHome();
            return;
        }

        btnNext.setEnabled(false);
        Toast.makeText(this, "Đang tạo video timelapse...", Toast.LENGTH_SHORT).show();

        List<String> finalSourceUris = sourceUris;
        new Thread(() -> {
            try {
                Uri videoUri = createTimelapseVideoUseCase.execute(this, finalSourceUris, 10);
                runOnUiThread(() -> {
                    btnNext.setEnabled(true);
                    Toast.makeText(this, "Video đã lưu vào thư viện!", Toast.LENGTH_LONG).show();
                    navigateToHome();
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    btnNext.setEnabled(true);
                    Toast.makeText(this, "Lỗi khi lưu video", Toast.LENGTH_SHORT).show();
                    navigateToHome(); // Vẫn quay về home dù lỗi lưu video
                });
            }
        }).start();
    }

    private void navigateToHome() {
        sessionRepository.clearSession();
        Intent intent = new Intent(ResultActivity.this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void saveCurrentResultAsPng() {
        if (resultUri == null) return;
        Bitmap bitmap = decodeBitmap(resultUri);
        if (bitmap == null) return;

        String name = "photobooth_" + System.currentTimeMillis() + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Photobooth");

        Uri outputUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (outputUri == null) return;

        try (OutputStream out = getContentResolver().openOutputStream(outputUri)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            Toast.makeText(this, "Đã lưu ảnh!", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Bitmap decodeBitmap(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            return null;
        }
    }
}