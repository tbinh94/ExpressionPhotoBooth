package com.example.expressionphotobooth;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.expressionphotobooth.data.video.TimelapseVideoEncoder;
import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.example.expressionphotobooth.domain.usecase.CreateTimelapseVideoUseCase;
import com.example.expressionphotobooth.domain.usecase.CreateVerticalCollageUseCase;
import com.google.android.material.appbar.MaterialToolbar;
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
    private MaterialButton btnSaveVideo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_result);
        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();
        sessionState = sessionRepository.getSession();
        createTimelapseVideoUseCase = new CreateTimelapseVideoUseCase(new TimelapseVideoEncoder());
        createVerticalCollageUseCase = new CreateVerticalCollageUseCase();
        setupToolbar();

        ImageView ivFinalResult = findViewById(R.id.ivFinalResult);

        Toast.makeText(this, "Đang xử lý ảnh ghép...", Toast.LENGTH_SHORT).show();

        // Chạy ngầm để không bị lag máy
        new Thread(() -> {
            List<String> imageUrisToCollage = new ArrayList<>();
            for (String originalUri : sessionState.getCapturedImageUris()) {
                String editedUri = sessionState.getEditedImageUris().get(originalUri);
                imageUrisToCollage.add(editedUri != null ? editedUri : originalUri);
            }

            // Gọi hàm ghép
            Bitmap collageBitmap = createVerticalCollageUseCase.execute(this, imageUrisToCollage);

            // Sau khi ghép xong, quay lại UI Thread để hiển thị
            runOnUiThread(() -> {
                if (collageBitmap != null) {
                    resultUri = saveBitmapToCache(collageBitmap);
                    ivFinalResult.setImageBitmap(collageBitmap);
                    sessionState.setResultImageUri(resultUri.toString());
                    sessionRepository.saveSession(sessionState);
                } else {
                    Toast.makeText(this, R.string.no_result_to_show, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();

        MaterialButton btnSavePng = findViewById(R.id.btnSavePng);
        btnSaveVideo = findViewById(R.id.btnSaveVideo);
        btnSavePng.setOnClickListener(v -> saveCurrentResultAsPng());
        btnSaveVideo.setOnClickListener(v -> exportTimelapseVideo());

        MaterialButton btnHome = findViewById(R.id.btnHome);
        btnHome.setOnClickListener(v -> {
            sessionRepository.clearSession();
            Intent intent = new Intent(ResultActivity.this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
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

    private void exportTimelapseVideo() {
        // Sửa lại chỗ này: Lấy danh sách ảnh đã xử lý (giống như lúc ghép ảnh)
        List<String> sourceUris = new ArrayList<>();
        for (String originalUri : sessionState.getCapturedImageUris()) {
            String editedUri = sessionState.getEditedImageUris().get(originalUri);
            sourceUris.add(editedUri != null ? editedUri : originalUri);
        }

        if (sourceUris.isEmpty()) {
            Toast.makeText(this, R.string.no_images_for_video, Toast.LENGTH_SHORT).show();
            return;
        }

        btnSaveVideo.setEnabled(false);
        Toast.makeText(this, R.string.exporting_video, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                // Sử dụng sourceUris đã bao gồm ảnh edit
                Uri videoUri = createTimelapseVideoUseCase.execute(this, sourceUris, 2);
                runOnUiThread(() -> {
                    btnSaveVideo.setEnabled(true);
                    Toast.makeText(this, "Video đã lưu vào thư viện!", Toast.LENGTH_LONG).show();
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    btnSaveVideo.setEnabled(true);
                    Toast.makeText(this, R.string.failed_save_video, Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void saveCurrentResultAsPng() {
        if (resultUri == null) {
            Toast.makeText(this, R.string.no_result_to_save, Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap bitmap = decodeBitmap(resultUri);
        if (bitmap == null) {
            Toast.makeText(this, R.string.failed_open_photo, Toast.LENGTH_SHORT).show();
            return;
        }

        String name = "photobooth_" + System.currentTimeMillis() + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Photobooth");

        Uri outputUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (outputUri == null) {
            Toast.makeText(this, R.string.failed_save_result, Toast.LENGTH_SHORT).show();
            return;
        }

        try (OutputStream out = getContentResolver().openOutputStream(outputUri)) {
            if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                Toast.makeText(this, R.string.failed_save_result, Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, getString(R.string.saved_to_gallery, name), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, R.string.failed_save_result, Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap decodeBitmap(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                return null;
            }
            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            return null;
        }
    }

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
