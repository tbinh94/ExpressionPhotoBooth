package com.example.expressionphotobooth;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
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

public class EditPhotoActivity extends AppCompatActivity {
    private static final int MAX_EDIT_BITMAP_SIZE = 1600;
    private ImageView ivEditingPhoto;
    private Bitmap originalBitmap;
    private Bitmap editedBitmap;
    private SessionRepository sessionRepository;
    private SessionState sessionState;
    private EditState currentEditState;
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
            Toast.makeText(this, R.string.no_photo_to_edit, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentPhotoUri = Uri.parse(selectedUriString);
        currentEditState = sessionState.getPhotoEditState(currentPhotoUri.toString()).copy();

        ivEditingPhoto = findViewById(R.id.ivEditingPhoto);
        
        // Buttons mapping
        findViewById(R.id.btnFilterNone).setOnClickListener(v -> updateFilter(EditState.FilterStyle.NONE));
        findViewById(R.id.btnFilterSoft).setOnClickListener(v -> updateFilter(EditState.FilterStyle.SOFT));
        findViewById(R.id.btnFilterBW).setOnClickListener(v -> updateFilter(EditState.FilterStyle.BW));

        findViewById(R.id.btnFrameNone).setOnClickListener(v -> updateFrame(EditState.FrameStyle.NONE));
        findViewById(R.id.btnFrameCortis).setOnClickListener(v -> updateFrame(EditState.FrameStyle.CORTIS));
        findViewById(R.id.btnFrameT1).setOnClickListener(v -> updateFrame(EditState.FrameStyle.T1));
        findViewById(R.id.btnFrameAespa).setOnClickListener(v -> updateFrame(EditState.FrameStyle.AESPA));

        findViewById(R.id.btnStickerNone).setOnClickListener(v -> updateSticker(EditState.StickerStyle.NONE));
        findViewById(R.id.btnStickerStar).setOnClickListener(v -> updateSticker(EditState.StickerStyle.STAR));
        findViewById(R.id.btnStickerFlash).setOnClickListener(v -> updateSticker(EditState.StickerStyle.FLASH));
        findViewById(R.id.btnStickerCamera).setOnClickListener(v -> updateSticker(EditState.StickerStyle.CAMERA));

        originalBitmap = decodeBitmapFromUri(currentPhotoUri);
        if (originalBitmap == null) {
            Toast.makeText(this, R.string.failed_open_photo, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        applyCurrentEditState();

        MaterialButton btnFinish = findViewById(R.id.btnFinishEdit);
        btnFinish.setOnClickListener(v -> {
            Uri resultUri = saveEditedBitmapToCache();
            if (resultUri == null) {
                Toast.makeText(this, R.string.failed_save_temp_result, Toast.LENGTH_SHORT).show();
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
        currentEditState.setFilterStyle(style);
        persistCurrentPhotoEditState();
        applyCurrentEditState();
    }

    private void updateFrame(EditState.FrameStyle style) {
        currentEditState.setFrameStyle(style);
        persistCurrentPhotoEditState();
        applyCurrentEditState();
    }

    private void updateSticker(EditState.StickerStyle style) {
        currentEditState.setStickerStyle(style);
        persistCurrentPhotoEditState();
        applyCurrentEditState();
    }

    private void applyCurrentEditState() {
        Bitmap target = renderEditedBitmapUseCase.execute(this, originalBitmap, currentEditState);
        if (editedBitmap != null && editedBitmap != originalBitmap && !editedBitmap.isRecycled()) {
            editedBitmap.recycle();
        }
        editedBitmap = target;
        ivEditingPhoto.setImageBitmap(editedBitmap);
    }

    private void persistCurrentPhotoEditState() {
        sessionState.setPhotoEditState(currentPhotoUri.toString(), currentEditState);
        sessionRepository.saveSession(sessionState);
    }

    private Bitmap decodeBitmapFromUri(Uri uri) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;

        try (InputStream boundsStream = getContentResolver().openInputStream(uri)) {
            if (boundsStream == null) {
                return null;
            }
            BitmapFactory.decodeStream(boundsStream, null, bounds);
        } catch (IOException e) {
            return null;
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = calculateInSampleSize(bounds, MAX_EDIT_BITMAP_SIZE, MAX_EDIT_BITMAP_SIZE);

        try (InputStream decodeStream = getContentResolver().openInputStream(uri)) {
            if (decodeStream == null) {
                return null;
            }
            return BitmapFactory.decodeStream(decodeStream, null, decodeOptions);
        } catch (IOException e) {
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        while ((height / inSampleSize) > reqHeight || (width / inSampleSize) > reqWidth) {
            inSampleSize *= 2;
        }
        return Math.max(inSampleSize, 1);
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
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (editedBitmap != null && editedBitmap != originalBitmap && !editedBitmap.isRecycled()) {
            editedBitmap.recycle();
        }
        if (originalBitmap != null && !originalBitmap.isRecycled()) {
            originalBitmap.recycle();
        }
    }
}