package com.example.expressionphotobooth;

import android.content.Intent;
import android.content.ContentValues;
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
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ResultActivity extends AppCompatActivity {
    private Uri resultUri;
    private SessionRepository sessionRepository;
    private SessionState sessionState;
    private CreateTimelapseVideoUseCase createTimelapseVideoUseCase;
    private MaterialButton btnSaveVideo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_result);
        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();
        sessionState = sessionRepository.getSession();
        createTimelapseVideoUseCase = new CreateTimelapseVideoUseCase(new TimelapseVideoEncoder());
        setupToolbar();

        ImageView ivFinalResult = findViewById(R.id.ivFinalResult);

        String resultUriString = getIntent().getStringExtra(IntentKeys.EXTRA_RESULT_IMAGE);
        String selectedUriString = getIntent().getStringExtra(IntentKeys.EXTRA_SELECTED_IMAGE);
        ArrayList<String> capturedImages = getIntent().getStringArrayListExtra(IntentKeys.EXTRA_CAPTURED_IMAGES);
        resultUri = resolveResultUri(resultUriString, selectedUriString, capturedImages, sessionState);
        sessionState.setResultImageUri(resultUri == null ? null : resultUri.toString());
        sessionRepository.saveSession(sessionState);

        if (resultUri != null) {
            ivFinalResult.setImageURI(resultUri);
        } else {
            Toast.makeText(this, R.string.no_result_to_show, Toast.LENGTH_SHORT).show();
        }

        MaterialButton btnSavePng = findViewById(R.id.btnSavePng);
        btnSaveVideo = findViewById(R.id.btnSaveVideo);
        btnSavePng.setOnClickListener(v -> saveCurrentResultAsPng());
        btnSaveVideo.setOnClickListener(v -> exportTimelapseVideo());

        MaterialButton btnHome = findViewById(R.id.btnHome);
        btnHome.setOnClickListener(v -> {
            // Ket thuc session khi quay ve Home de luot chup moi bat dau sach.
            sessionRepository.clearSession();
            Intent intent = new Intent(ResultActivity.this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
    }

    private Uri resolveResultUri(String resultUriString, String selectedUriString, ArrayList<String> capturedImages, SessionState state) {
        if (resultUriString != null && !resultUriString.isEmpty()) {
            return Uri.parse(resultUriString);
        }
        if (state.getResultImageUri() != null && !state.getResultImageUri().isEmpty()) {
            return Uri.parse(state.getResultImageUri());
        }
        if (selectedUriString != null && !selectedUriString.isEmpty()) {
            return Uri.parse(selectedUriString);
        }
        if (state.getSelectedImageUri() != null && !state.getSelectedImageUri().isEmpty()) {
            return Uri.parse(state.getSelectedImageUri());
        }
        if (capturedImages != null && !capturedImages.isEmpty()) {
            return Uri.parse(capturedImages.get(0));
        }
        if (!state.getCapturedImageUris().isEmpty()) {
            return Uri.parse(state.getCapturedImageUris().get(0));
        }
        return null;
    }

    private void exportTimelapseVideo() {
        List<String> sourceUris = sessionState.getCapturedImageUris();
        if (sourceUris == null || sourceUris.isEmpty()) {
            Toast.makeText(this, R.string.no_images_for_video, Toast.LENGTH_SHORT).show();
            return;
        }

        btnSaveVideo.setEnabled(false);
        Toast.makeText(this, R.string.exporting_video, Toast.LENGTH_SHORT).show();

        // Chay encode o background thread de khong block UI.
        new Thread(() -> {
            try {
                Uri videoUri = createTimelapseVideoUseCase.execute(this, sourceUris, 2);
                runOnUiThread(() -> {
                    btnSaveVideo.setEnabled(true);
                    Toast.makeText(this, getString(R.string.video_saved_to_gallery, String.valueOf(videoUri)), Toast.LENGTH_LONG).show();
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

    // Toolbar giup dong bo kieu dieu huong va de nguoi dung quay lai man edit.
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