package com.example.expressionphotobooth;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
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
    private TextView tvEditSummary;

    private MaterialButton btnFilterNone;
    private MaterialButton btnFilterSoft;
    private MaterialButton btnFilterBW;
    private MaterialButton btnFrameNone;
    private MaterialButton btnFrameCortis;
    private MaterialButton btnFrameT1;
    private MaterialButton btnFrameAespa;
    private MaterialButton btnStickerNone;
    private MaterialButton btnStickerStar;
    private MaterialButton btnStickerFlash;
    private MaterialButton btnStickerCamera;
    private MaterialButton btnPresetCute;
    private MaterialButton btnPresetKpop;
    private MaterialButton btnPresetClassic;

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
        tvEditSummary = findViewById(R.id.tvEditSummary);

        btnFilterNone = findViewById(R.id.btnFilterNone);
        btnFilterSoft = findViewById(R.id.btnFilterSoft);
        btnFilterBW = findViewById(R.id.btnFilterBW);
        btnFrameNone = findViewById(R.id.btnFrameNone);
        btnFrameCortis = findViewById(R.id.btnFrameCortis);
        btnFrameT1 = findViewById(R.id.btnFrameT1);
        btnFrameAespa = findViewById(R.id.btnFrameAespa);
        btnStickerNone = findViewById(R.id.btnStickerNone);
        btnStickerStar = findViewById(R.id.btnStickerStar);
        btnStickerFlash = findViewById(R.id.btnStickerFlash);
        btnStickerCamera = findViewById(R.id.btnStickerCamera);
        btnPresetCute = findViewById(R.id.btnPresetCute);
        btnPresetKpop = findViewById(R.id.btnPresetKpop);
        btnPresetClassic = findViewById(R.id.btnPresetClassic);
        
        // Buttons mapping
        btnFilterNone.setOnClickListener(v -> {
            updateFilter(EditState.FilterStyle.NONE);
            animateButtonTap(v);
        });
        btnFilterSoft.setOnClickListener(v -> {
            updateFilter(EditState.FilterStyle.SOFT);
            animateButtonTap(v);
        });
        btnFilterBW.setOnClickListener(v -> {
            updateFilter(EditState.FilterStyle.BW);
            animateButtonTap(v);
        });

        btnFrameNone.setOnClickListener(v -> {
            updateFrame(EditState.FrameStyle.NONE);
            animateButtonTap(v);
        });
        btnFrameCortis.setOnClickListener(v -> {
            updateFrame(EditState.FrameStyle.CORTIS);
            animateButtonTap(v);
        });
        btnFrameT1.setOnClickListener(v -> {
            updateFrame(EditState.FrameStyle.T1);
            animateButtonTap(v);
        });
        btnFrameAespa.setOnClickListener(v -> {
            updateFrame(EditState.FrameStyle.AESPA);
            animateButtonTap(v);
        });

        btnStickerNone.setOnClickListener(v -> {
            updateSticker(EditState.StickerStyle.NONE);
            animateButtonTap(v);
        });
        btnStickerStar.setOnClickListener(v -> {
            updateSticker(EditState.StickerStyle.STAR);
            animateButtonTap(v);
        });
        btnStickerFlash.setOnClickListener(v -> {
            updateSticker(EditState.StickerStyle.FLASH);
            animateButtonTap(v);
        });
        btnStickerCamera.setOnClickListener(v -> {
            updateSticker(EditState.StickerStyle.CAMERA);
            animateButtonTap(v);
        });

        btnPresetCute.setOnClickListener(v -> {
            applyPreset(EditState.FilterStyle.SOFT, EditState.FrameStyle.AESPA, EditState.StickerStyle.STAR);
            animateButtonTap(v);
        });
        btnPresetKpop.setOnClickListener(v -> {
            applyPreset(EditState.FilterStyle.NONE, EditState.FrameStyle.T1, EditState.StickerStyle.FLASH);
            animateButtonTap(v);
        });
        btnPresetClassic.setOnClickListener(v -> {
            applyPreset(EditState.FilterStyle.BW, EditState.FrameStyle.NONE, EditState.StickerStyle.NONE);
            animateButtonTap(v);
        });

        originalBitmap = decodeBitmapFromUri(currentPhotoUri);
        if (originalBitmap == null) {
            Toast.makeText(this, R.string.failed_open_photo, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        applyCurrentEditState();
        refreshSelectionUi();
        updateEditSummary();

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
        refreshSelectionUi();
        updateEditSummary();
        animatePreviewPulse();
    }

    private void updateFrame(EditState.FrameStyle style) {
        currentEditState.setFrameStyle(style);
        persistCurrentPhotoEditState();
        applyCurrentEditState();
        refreshSelectionUi();
        updateEditSummary();
        animatePreviewPulse();
    }

    private void updateSticker(EditState.StickerStyle style) {
        currentEditState.setStickerStyle(style);
        persistCurrentPhotoEditState();
        applyCurrentEditState();
        refreshSelectionUi();
        updateEditSummary();
        animatePreviewPulse();
    }

    // Preset cho phep ap dung nhanh mot bo style trong 1 lan cham.
    private void applyPreset(EditState.FilterStyle filterStyle,
                             EditState.FrameStyle frameStyle,
                             EditState.StickerStyle stickerStyle) {
        currentEditState.setFilterStyle(filterStyle);
        currentEditState.setFrameStyle(frameStyle);
        currentEditState.setStickerStyle(stickerStyle);
        persistCurrentPhotoEditState();
        applyCurrentEditState();
        refreshSelectionUi();
        updateEditSummary();
        animatePreviewPulse();
    }

    private void refreshSelectionUi() {
        setSelectedState(btnFilterNone, currentEditState.getFilterStyle() == EditState.FilterStyle.NONE);
        setSelectedState(btnFilterSoft, currentEditState.getFilterStyle() == EditState.FilterStyle.SOFT);
        setSelectedState(btnFilterBW, currentEditState.getFilterStyle() == EditState.FilterStyle.BW);

        setSelectedState(btnFrameNone, currentEditState.getFrameStyle() == EditState.FrameStyle.NONE);
        setSelectedState(btnFrameCortis, currentEditState.getFrameStyle() == EditState.FrameStyle.CORTIS);
        setSelectedState(btnFrameT1, currentEditState.getFrameStyle() == EditState.FrameStyle.T1);
        setSelectedState(btnFrameAespa, currentEditState.getFrameStyle() == EditState.FrameStyle.AESPA);

        setSelectedState(btnStickerNone, currentEditState.getStickerStyle() == EditState.StickerStyle.NONE);
        setSelectedState(btnStickerStar, currentEditState.getStickerStyle() == EditState.StickerStyle.STAR);
        setSelectedState(btnStickerFlash, currentEditState.getStickerStyle() == EditState.StickerStyle.FLASH);
        setSelectedState(btnStickerCamera, currentEditState.getStickerStyle() == EditState.StickerStyle.CAMERA);
    }

    private void setSelectedState(MaterialButton button, boolean selected) {
        if (button == null) {
            return;
        }
        button.setSelected(selected);
        button.setPressed(false);
    }

    private void updateEditSummary() {
        if (tvEditSummary == null) {
            return;
        }
        tvEditSummary.setText(getString(
                R.string.edit_summary_format,
                getFilterLabel(currentEditState.getFilterStyle()),
                getFrameLabel(currentEditState.getFrameStyle()),
                getStickerLabel(currentEditState.getStickerStyle())
        ));
    }

    private void animatePreviewPulse() {
        if (ivEditingPhoto == null) {
            return;
        }
        ivEditingPhoto.animate().cancel();
        ivEditingPhoto.animate()
                .scaleX(1.02f)
                .scaleY(1.02f)
                .setDuration(90)
                .withEndAction(() -> ivEditingPhoto.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .start())
                .start();
    }

    private void animateButtonTap(View target) {
        if (target == null) {
            return;
        }
        target.animate().cancel();
        target.animate()
                .alpha(0.82f)
                .setDuration(70)
                .withEndAction(() -> target.animate()
                        .alpha(1f)
                        .setDuration(110)
                        .start())
                .start();
    }

    private String getFilterLabel(EditState.FilterStyle style) {
        switch (style) {
            case SOFT:
                return getString(R.string.edit_filter_soft);
            case BW:
                return getString(R.string.edit_filter_bw);
            case NONE:
            default:
                return getString(R.string.edit_option_none);
        }
    }

    private String getFrameLabel(EditState.FrameStyle style) {
        switch (style) {
            case CORTIS:
                return getString(R.string.edit_frame_cortis);
            case T1:
                return getString(R.string.edit_frame_t1);
            case AESPA:
                return getString(R.string.edit_frame_aespa);
            case NONE:
            default:
                return getString(R.string.edit_option_none);
        }
    }

    private String getStickerLabel(EditState.StickerStyle style) {
        switch (style) {
            case STAR:
                return getString(R.string.edit_sticker_star);
            case FLASH:
                return getString(R.string.edit_sticker_flash);
            case CAMERA:
                return getString(R.string.edit_sticker_camera);
            case NONE:
            default:
                return getString(R.string.edit_option_none);
        }
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