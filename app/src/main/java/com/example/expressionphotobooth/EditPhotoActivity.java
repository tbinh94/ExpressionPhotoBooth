package com.example.expressionphotobooth;

import android.app.Activity;
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
    private Uri currentPhotoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_photo);
        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();
        sessionState = sessionRepository.getSession();
        renderEditedBitmapUseCase = new RenderEditedBitmapUseCase(new BitmapEditRenderer());
        setupToolbar();

        String selectedUriString = getIntent().getStringExtra(IntentKeys.EXTRA_SELECTED_IMAGE);
        if (selectedUriString == null || selectedUriString.isEmpty()) {
            Toast.makeText(this, "No photo to edit", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentPhotoUri = Uri.parse(selectedUriString);

        ivEditingPhoto = findViewById(R.id.ivEditingPhoto);
        
        // Buttons mapping
        findViewById(R.id.btnFilterNone).setOnClickListener(v -> updateFilter(EditState.FilterStyle.NONE));
        findViewById(R.id.btnFilterSoft).setOnClickListener(v -> updateFilter(EditState.FilterStyle.SOFT));
        findViewById(R.id.btnFilterBW).setOnClickListener(v -> updateFilter(EditState.FilterStyle.BW));

        findViewById(R.id.btnFrameNone).setOnClickListener(v -> updateFrame(EditState.FrameStyle.NONE));
        findViewById(R.id.btnFrameCortis).setOnClickListener(v -> updateFrame(EditState.FrameStyle.CORTIS));
        findViewById(R.id.btnFrameT1).setOnClickListener(v -> updateFrame(EditState.FrameStyle.T1));
        findViewById(R.id.btnFrameAespa).setOnClickListener(v -> updateFrame(EditState.FrameStyle.AESPA));

        originalBitmap = decodeBitmapFromUri(currentPhotoUri);
        if (originalBitmap == null) {
            Toast.makeText(this, "Failed to load photo", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        applyCurrentEditState();

        MaterialButton btnFinish = findViewById(R.id.btnFinishEdit);
        btnFinish.setOnClickListener(v -> {
            Uri resultUri = saveEditedBitmapToCache();
            if (resultUri == null) {
                Toast.makeText(this, "Failed to save edit", Toast.LENGTH_SHORT).show();
                return;
            }

            // Gửi kết quả về PhotoSelectionActivity
            Intent resultIntent = new Intent();
            resultIntent.putExtra(IntentKeys.EXTRA_ORIGINAL_URI, currentPhotoUri.toString());
            resultIntent.putExtra(IntentKeys.EXTRA_EDITED_URI, resultUri.toString());
            setResult(Activity.RESULT_OK, resultIntent);
            finish(); // Quay lại trang PhotoSelection
        });
    }

    private void updateFilter(EditState.FilterStyle style) {
        sessionState.getEditState().setFilterStyle(style);
        applyCurrentEditState();
    }

    private void updateFrame(EditState.FrameStyle style) {
        sessionState.getEditState().setFrameStyle(style);
        applyCurrentEditState();
    }

    private void applyCurrentEditState() {
        Bitmap target = renderEditedBitmapUseCase.execute(this, originalBitmap, sessionState.getEditState());
        if (editedBitmap != null && editedBitmap != originalBitmap && !editedBitmap.isRecycled()) {
            editedBitmap.recycle();
        }
        editedBitmap = target;
        ivEditingPhoto.setImageBitmap(editedBitmap);
        sessionRepository.saveSession(sessionState);
    }

    private Bitmap decodeBitmapFromUri(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            return null;
        }
    }

    private Uri saveEditedBitmapToCache() {
        if (editedBitmap == null) return null;
        File file = new File(getCacheDir(), "edited_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            editedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            return Uri.fromFile(file);
        } catch (IOException e) {
            return null;
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());
    }
}