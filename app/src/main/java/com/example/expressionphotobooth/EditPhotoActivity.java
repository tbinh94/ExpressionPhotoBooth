package com.example.expressionphotobooth;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.expressionphotobooth.data.graphics.BitmapEditRenderer;
import com.example.expressionphotobooth.domain.model.EditState;
import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.example.expressionphotobooth.domain.usecase.RenderEditedBitmapUseCase;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class EditPhotoActivity extends AppCompatActivity {
    private ImageView ivEditingPhoto;
    private Bitmap originalBitmap;
    private Bitmap editedBitmap;
    private SessionRepository sessionRepository;
    private SessionState sessionState;
    private RenderEditedBitmapUseCase renderEditedBitmapUseCase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_photo);
        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();
        sessionState = sessionRepository.getSession();
        renderEditedBitmapUseCase = new RenderEditedBitmapUseCase(new BitmapEditRenderer());
        setupToolbar();

        ArrayList<String> imagesToEdit = getIntent().getStringArrayListExtra(IntentKeys.EXTRA_CAPTURED_IMAGES);
        String selectedUriString = getIntent().getStringExtra(IntentKeys.EXTRA_SELECTED_IMAGE);

        ivEditingPhoto = findViewById(R.id.ivEditingPhoto);
        Button btnFilterNone = findViewById(R.id.btnFilterNone);
        Button btnFilterSoft = findViewById(R.id.btnFilterSoft);
        Button btnFilterBW = findViewById(R.id.btnFilterBW);
        Button btnFrameNone = findViewById(R.id.btnFrameNone);
        Button btnFrameCortis = findViewById(R.id.btnFrameCortis);
        Button btnFrameT1 = findViewById(R.id.btnFrameT1);
        Button btnFrameAespa = findViewById(R.id.btnFrameAespa);
        Button btnStickerNone = findViewById(R.id.btnStickerNone);
        Button btnStickerStar = findViewById(R.id.btnStickerStar);
        Button btnStickerFlash = findViewById(R.id.btnStickerFlash);
        Button btnStickerCamera = findViewById(R.id.btnStickerCamera);

        Uri selectedUri = resolveSelectedUri(selectedUriString, imagesToEdit, sessionState);
        if (selectedUri == null) {
            Toast.makeText(this, R.string.no_photo_to_edit, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        originalBitmap = decodeBitmapFromUri(selectedUri);
        if (originalBitmap == null) {
            Toast.makeText(this, R.string.failed_open_photo, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Neu nguoi dung quay lai man edit, van giu nguyen state truoc do.
        applyCurrentEditState();

        btnFilterNone.setOnClickListener(v -> {
            sessionState.getEditState().setFilterStyle(EditState.FilterStyle.NONE);
            applyCurrentEditState();
        });

        btnFilterSoft.setOnClickListener(v -> {
            sessionState.getEditState().setFilterStyle(EditState.FilterStyle.SOFT);
            applyCurrentEditState();
        });

        btnFilterBW.setOnClickListener(v -> {
            sessionState.getEditState().setFilterStyle(EditState.FilterStyle.BW);
            applyCurrentEditState();
        });

        btnFrameNone.setOnClickListener(v -> {
            sessionState.getEditState().setFrameStyle(EditState.FrameStyle.NONE);
            applyCurrentEditState();
        });
        btnFrameCortis.setOnClickListener(v -> {
            sessionState.getEditState().setFrameStyle(EditState.FrameStyle.CORTIS);
            applyCurrentEditState();
        });
        btnFrameT1.setOnClickListener(v -> {
            sessionState.getEditState().setFrameStyle(EditState.FrameStyle.T1);
            applyCurrentEditState();
        });
        btnFrameAespa.setOnClickListener(v -> {
            sessionState.getEditState().setFrameStyle(EditState.FrameStyle.AESPA);
            applyCurrentEditState();
        });

        btnStickerNone.setOnClickListener(v -> {
            sessionState.getEditState().setStickerStyle(EditState.StickerStyle.NONE);
            applyCurrentEditState();
        });
        btnStickerStar.setOnClickListener(v -> {
            sessionState.getEditState().setStickerStyle(EditState.StickerStyle.STAR);
            applyCurrentEditState();
        });
        btnStickerFlash.setOnClickListener(v -> {
            sessionState.getEditState().setStickerStyle(EditState.StickerStyle.FLASH);
            applyCurrentEditState();
        });
        btnStickerCamera.setOnClickListener(v -> {
            sessionState.getEditState().setStickerStyle(EditState.StickerStyle.CAMERA);
            applyCurrentEditState();
        });

        MaterialButton btnFinish = findViewById(R.id.btnFinishEdit);
        btnFinish.setOnClickListener(v -> {
            Uri resultUri = saveEditedBitmapToCache();
            if (resultUri == null) {
                Toast.makeText(this, R.string.failed_save_temp_result, Toast.LENGTH_SHORT).show();
                return;
            }

            // Luu ket qua vao session de ResultActivity co the khoi phuc sau process recreation.
            sessionState.setResultImageUri(resultUri.toString());
            sessionRepository.saveSession(sessionState);

            Intent intent = new Intent(EditPhotoActivity.this, ResultActivity.class);
            intent.putStringArrayListExtra(IntentKeys.EXTRA_CAPTURED_IMAGES, imagesToEdit);
            intent.putExtra(IntentKeys.EXTRA_SELECTED_IMAGE, selectedUri.toString());
            intent.putExtra(IntentKeys.EXTRA_RESULT_IMAGE, resultUri.toString());

            startActivity(intent);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (originalBitmap != null && !originalBitmap.isRecycled()) {
            originalBitmap.recycle();
        }
        if (editedBitmap != null && editedBitmap != originalBitmap && !editedBitmap.isRecycled()) {
            editedBitmap.recycle();
        }
    }

    private Uri resolveSelectedUri(String selectedUriString, ArrayList<String> capturedImages, SessionState state) {
        if (selectedUriString != null && !selectedUriString.isEmpty()) {
            state.setSelectedImageUri(selectedUriString);
            sessionRepository.saveSession(state);
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

    private Bitmap decodeBitmapFromUri(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                return null;
            }
            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            return null;
        }
    }

    private void applyCurrentEditState() {
        Bitmap target = renderEditedBitmapUseCase.execute(this, originalBitmap, sessionState.getEditState());
        if (editedBitmap != null && editedBitmap != originalBitmap && !editedBitmap.isRecycled()) {
            editedBitmap.recycle();
        }
        editedBitmap = target;
        ivEditingPhoto.setImageBitmap(editedBitmap);

        // Luu state moi sau moi lan user doi filter/frame/sticker.
        sessionRepository.saveSession(sessionState);
    }

    private Uri saveEditedBitmapToCache() {
        if (editedBitmap == null) {
            return null;
        }

        File file = new File(getCacheDir(), "edited_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            if (!editedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                return null;
            }
            return Uri.fromFile(file);
        } catch (IOException e) {
            return null;
        }
    }

    // Dat toolbar co nut Up de tro lai man hinh chon anh.
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