package com.example.expressionphotobooth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expressionphotobooth.data.graphics.BitmapEditRenderer;
import com.example.expressionphotobooth.domain.model.EditState;
import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.example.expressionphotobooth.domain.usecase.RenderEditedBitmapUseCase;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.tabs.TabLayout;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

public class EditPhotoActivity extends AppCompatActivity {

    private static final int MAX_EDIT_BITMAP_SIZE = 1600;

    // Views
    private ImageView ivEditingPhoto;
    private Chip chipActiveEdit;
    private TextView tvEditSummary;
    private TabLayout editTabLayout;
    private LinearLayout panelPresets, panelFilters, panelFrames, panelStickers;
    private LinearLayout filterIntensityRow;
    private SeekBar seekFilterIntensity;
    private TextView tvIntensityValue;

    // RecyclerViews
    private RecyclerView rvPresets, rvFilters, rvFrames, rvStickers;

    // State
    private Bitmap originalBitmap;
    private Bitmap editedBitmap;
    private SessionRepository sessionRepository;
    private SessionState sessionState;
    private EditState currentEditState;
    private Stack<EditState> undoStack = new Stack<>();
    private RenderEditedBitmapUseCase renderEditedBitmapUseCase;
    private Uri currentPhotoUri;

    // ── Thumbnail item model ──────────────────────────────────────────────────

    static class ThumbItem {
        final String label;
        final int drawableRes;   // preview drawable (small cropped sample); 0 = use colorRes
        final int colorRes;      // fallback solid color for preview swatch
        final Object value;      // EditState.FilterStyle / FrameStyle / StickerStyle

        ThumbItem(String label, int drawableRes, int colorRes, Object value) {
            this.label = label;
            this.drawableRes = drawableRes;
            this.colorRes = colorRes;
            this.value = value;
        }
    }

    // ── Generic thumbnail RecyclerView adapter ────────────────────────────────

    interface OnThumbSelectedListener {
        void onSelected(ThumbItem item);
    }

    class ThumbAdapter extends RecyclerView.Adapter<ThumbAdapter.VH> {

        private final List<ThumbItem> items;
        private int selectedPos = 0;
        private final OnThumbSelectedListener listener;

        ThumbAdapter(List<ThumbItem> items, OnThumbSelectedListener listener) {
            this.items = items;
            this.listener = listener;
        }

        void setSelectedByValue(Object value) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).value == value) {
                    int old = selectedPos;
                    selectedPos = i;
                    notifyItemChanged(old);
                    notifyItemChanged(i);
                    return;
                }
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_edit_thumb, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ThumbItem item = items.get(position);
            boolean selected = (position == selectedPos);

            h.label.setText(item.label);

            if (item.drawableRes != 0) {
                h.preview.setImageResource(item.drawableRes);
            } else {
                h.preview.setImageDrawable(null);
                h.preview.setBackgroundResource(item.colorRes);
            }

            // Selected ring
            h.cardThumb.setStrokeWidth(selected ? 6 : 0); // 6 px outline
            h.cardThumb.setStrokeColor(getColor(R.color.edit_accent));
            h.label.setTextColor(selected
                    ? getColor(R.color.edit_accent)
                    : getColor(R.color.edit_label));

            h.itemView.setOnClickListener(v -> {
                int old = selectedPos;
                selectedPos = h.getAdapterPosition();
                notifyItemChanged(old);
                notifyItemChanged(selectedPos);
                listener.onSelected(items.get(selectedPos));
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView preview;
            com.google.android.material.card.MaterialCardView cardThumb;
            TextView label;

            VH(@NonNull View itemView) {
                super(itemView);
                preview   = itemView.findViewById(R.id.ivThumbPreview);
                cardThumb = itemView.findViewById(R.id.cardThumb);
                label     = itemView.findViewById(R.id.tvThumbLabel);
            }
        }
    }

    // ── Adapters ──────────────────────────────────────────────────────────────

    private ThumbAdapter presetsAdapter;
    private ThumbAdapter filtersAdapter;
    private ThumbAdapter framesAdapter;
    private ThumbAdapter stickersAdapter;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_photo);

        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();
        sessionState = sessionRepository.getSession();
        renderEditedBitmapUseCase = new RenderEditedBitmapUseCase(new BitmapEditRenderer());

        // URI
        String selectedUriString = getIntent().getStringExtra(IntentKeys.EXTRA_SELECTED_IMAGE);
        if (selectedUriString == null || selectedUriString.isEmpty()) {
            Toast.makeText(this, R.string.no_photo_to_edit, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentPhotoUri = Uri.parse(selectedUriString);
        currentEditState = sessionState.getPhotoEditState(currentPhotoUri.toString()).copy();

        bindViews();
        setupToolbar();
        setupTabs();
        setupAdapters();
        setupIntensitySlider();
        setupActionButtons();
        setupStickerDrag();

        originalBitmap = decodeBitmapFromUri(currentPhotoUri);
        if (originalBitmap == null) {
            Toast.makeText(this, R.string.failed_open_photo, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        applyCurrentEditState();
        syncSelectionsToAdapters();
        updateEditSummary();
    }

    private void bindViews() {
        ivEditingPhoto    = findViewById(R.id.ivEditingPhoto);
        chipActiveEdit    = findViewById(R.id.chipActiveEdit);
        tvEditSummary     = findViewById(R.id.tvEditSummary);
        editTabLayout     = findViewById(R.id.editTabLayout);
        panelPresets      = findViewById(R.id.panelPresets);
        panelFilters      = findViewById(R.id.panelFilters);
        panelFrames       = findViewById(R.id.panelFrames);
        panelStickers     = findViewById(R.id.panelStickers);
        filterIntensityRow = findViewById(R.id.filterIntensityRow);
        seekFilterIntensity = findViewById(R.id.seekFilterIntensity);
        tvIntensityValue  = findViewById(R.id.tvIntensityValue);
        rvPresets         = findViewById(R.id.rvPresets);
        rvFilters         = findViewById(R.id.rvFilters);
        rvFrames          = findViewById(R.id.rvFrames);
        rvStickers        = findViewById(R.id.rvStickers);
    }

    // ── Tab setup ─────────────────────────────────────────────────────────────

    private void setupTabs() {
        editTabLayout.addTab(editTabLayout.newTab().setText(getString(R.string.edit_section_presets)));
        editTabLayout.addTab(editTabLayout.newTab().setText(getString(R.string.edit_section_filters)));
        editTabLayout.addTab(editTabLayout.newTab().setText(getString(R.string.edit_section_stickers)));

        editTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showPanel(tab.getPosition());
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        showPanel(0); // start on Presets
    }

    private void showPanel(int index) {
        panelPresets.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        panelFilters.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        panelFrames.setVisibility(View.GONE);
        panelStickers.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
    }

    // ── Adapters setup ────────────────────────────────────────────────────────

    private void setupAdapters() {

        // PRESETS
        List<ThumbItem> presetItems = Arrays.asList(
                new ThumbItem(getString(R.string.edit_preset_cute),    0, R.color.thumb_preset_cute,    "cute"),
                new ThumbItem(getString(R.string.edit_preset_kpop),    0, R.color.thumb_preset_kpop,    "kpop"),
                new ThumbItem(getString(R.string.edit_preset_classic), 0, R.color.thumb_preset_classic, "classic"),
                new ThumbItem(getString(R.string.edit_preset_retro),   0, R.color.thumb_preset_retro,   "retro"),
                new ThumbItem(getString(R.string.edit_preset_cinematic), 0, R.color.thumb_preset_cinematic, "cinematic")
        );
        presetsAdapter = new ThumbAdapter(presetItems, item -> {
            switch ((String) item.value) {
                case "cute":    applyPreset(EditState.FilterStyle.SOFT, EditState.FrameStyle.AESPA, EditState.StickerStyle.STAR);    break;
                case "kpop":    applyPreset(EditState.FilterStyle.NONE, EditState.FrameStyle.T1,    EditState.StickerStyle.FLASH);   break;
                case "classic": applyPreset(EditState.FilterStyle.BW,   EditState.FrameStyle.NONE,  EditState.StickerStyle.NONE);   break;
                case "retro":   applyPreset(EditState.FilterStyle.VINTAGE, EditState.FrameStyle.CORTIS, EditState.StickerStyle.CAMERA); break;
                case "cinematic": applyPreset(EditState.FilterStyle.COOL, EditState.FrameStyle.NONE,  EditState.StickerStyle.NONE); break;
            }
        });
        rvPresets.setAdapter(presetsAdapter);

        // FILTERS
        List<ThumbItem> filterItems = Arrays.asList(
                new ThumbItem(getString(R.string.edit_option_none), 0, R.color.thumb_none,        EditState.FilterStyle.NONE),
                new ThumbItem(getString(R.string.edit_filter_soft), 0, R.color.thumb_filter_soft, EditState.FilterStyle.SOFT),
                new ThumbItem(getString(R.string.edit_filter_bw),   0, R.color.thumb_filter_bw,   EditState.FilterStyle.BW),
                new ThumbItem(getString(R.string.edit_filter_vintage), 0, R.color.thumb_filter_vintage, EditState.FilterStyle.VINTAGE),
                new ThumbItem(getString(R.string.edit_filter_cool), 0, R.color.thumb_filter_cool, EditState.FilterStyle.COOL),
                new ThumbItem(getString(R.string.edit_filter_warm), 0, R.color.thumb_filter_warm, EditState.FilterStyle.WARM),
                new ThumbItem(getString(R.string.edit_filter_sepia), 0, R.color.thumb_filter_sepia, EditState.FilterStyle.SEPIA)
        );
        filtersAdapter = new ThumbAdapter(filterItems, item -> {
            updateFilter((EditState.FilterStyle) item.value);
        });
        rvFilters.setAdapter(filtersAdapter);

        // FRAMES
        List<ThumbItem> frameItems = Arrays.asList(
                new ThumbItem(getString(R.string.edit_option_none),    0, R.color.thumb_none,         EditState.FrameStyle.NONE),
                new ThumbItem(getString(R.string.edit_frame_cortis),   0, R.color.thumb_frame_cortis, EditState.FrameStyle.CORTIS),
                new ThumbItem(getString(R.string.edit_frame_aespa),    0, R.color.thumb_frame_aespa,  EditState.FrameStyle.AESPA),
                new ThumbItem(getString(R.string.edit_frame_t1),       0, R.color.thumb_frame_t1,     EditState.FrameStyle.T1)
        );
        framesAdapter = new ThumbAdapter(frameItems, item -> {
            updateFrame((EditState.FrameStyle) item.value);
        });
        rvFrames.setAdapter(framesAdapter);

        // STICKERS
        List<ThumbItem> stickerItems = Arrays.asList(
                new ThumbItem(getString(R.string.edit_option_none),     0, R.color.thumb_none,             EditState.StickerStyle.NONE),
                new ThumbItem(getString(R.string.edit_sticker_star),    0, R.color.thumb_sticker_star,     EditState.StickerStyle.STAR),
                new ThumbItem(getString(R.string.edit_sticker_camera),  0, R.color.thumb_sticker_camera,   EditState.StickerStyle.CAMERA),
                new ThumbItem(getString(R.string.edit_sticker_flash),   0, R.color.thumb_sticker_flash,    EditState.StickerStyle.FLASH)
        );
        stickersAdapter = new ThumbAdapter(stickerItems, item -> {
            updateSticker((EditState.StickerStyle) item.value);
        });
        rvStickers.setAdapter(stickersAdapter);
    }

    private void setupIntensitySlider() {
        seekFilterIntensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentEditState.setFilterIntensity(progress / 100f);
                    tvIntensityValue.setText(String.valueOf(progress));
                    applyCurrentEditState();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                pushToUndoStack();
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.topBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        View btnUndo = findViewById(R.id.btnUndo);
        if (btnUndo != null) {
            btnUndo.setOnClickListener(v -> undo());
        }
    }

    private void setupActionButtons() {
        MaterialButton btnFinish = findViewById(R.id.btnFinishEdit);
        btnFinish.setOnClickListener(v -> saveAndFinish());
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupStickerDrag() {
        ivEditingPhoto.setOnTouchListener(new View.OnTouchListener() {
            private boolean isDragging = false;
            private float offsetX = 0f;
            private float offsetY = 0f;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (currentEditState.getStickerStyle() == EditState.StickerStyle.NONE) {
                    return false;
                }
                if (originalBitmap == null) return false;

                float viewWidth = ivEditingPhoto.getWidth();
                float viewHeight = ivEditingPhoto.getHeight();
                float imgWidth = originalBitmap.getWidth();
                float imgHeight = originalBitmap.getHeight();

                if (viewWidth == 0 || viewHeight == 0 || imgWidth == 0 || imgHeight == 0) {
                    return false;
                }

                float scaleX = viewWidth / imgWidth;
                float scaleY = viewHeight / imgHeight;
                float scale = Math.min(scaleX, scaleY);

                float drawLeft = (viewWidth - (imgWidth * scale)) / 2f;
                float drawTop = (viewHeight - (imgHeight * scale)) / 2f;

                float bX = (event.getX() - drawLeft) / scale;
                float bY = (event.getY() - drawTop) / scale;

                float sx = currentEditState.getStickerX();
                float sy = currentEditState.getStickerY();

                float bw = imgWidth;
                float bh = imgHeight;

                int minL = Math.min((int)bw, (int)bh);
                int size = Math.max(72, minL / 5);

                if (sx < 0 || sy < 0) {
                    int safeW = (int) (minL * 0.75f);
                    int safeH = (int) (minL * 0.75f);
                    int centerX = (int)(bw / 2f);
                    int centerY = (int)(bh / 2f);
                    int padding = 24;

                    float left = centerX + (safeW / 2f) - size - padding;
                    float top = centerY - (safeH / 2f) + padding;
                    sx = (left + size / 2f) / bw;
                    sy = (top + size / 2f) / bh;
                }

                float scX = sx * bw;
                float scY = sy * bh;

                float dx = bX - scX;
                float dy = bY - scY;
                float distSq = dx*dx + dy*dy;

                float radius = size / 2f;
                float radiusSq = (radius * 1.5f) * (radius * 1.5f);

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (distSq < radiusSq * 3.0f) { // Cung cấp vùng chạm rộng
                            pushToUndoStack();
                            isDragging = true;
                            offsetX = bX - scX;
                            offsetY = bY - scY;
                            return true;
                        }
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        if (isDragging) {
                            float newCenterX = bX - offsetX;
                            float newCenterY = bY - offsetY;

                            newCenterX = Math.max(0, Math.min(newCenterX, bw));
                            newCenterY = Math.max(0, Math.min(newCenterY, bh));

                            currentEditState.setStickerX(newCenterX / bw);
                            currentEditState.setStickerY(newCenterY / bh);
                            applyCurrentEditState();
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isDragging) {
                            isDragging = false;
                            return true;
                        }
                        break;
                }
                return false;
            }
        });
    }

    // ── Logic ─────────────────────────────────────────────────────────────────

    private void pushToUndoStack() {
        if (undoStack.size() >= 20) {
            undoStack.remove(0); // Cap history
        }
        undoStack.push(currentEditState.copy());
    }

    private void undo() {
        if (!undoStack.isEmpty()) {
            currentEditState = undoStack.pop();
            applyCurrentEditState();
            syncSelectionsToAdapters();
            updateEditSummary();
        } else {
            Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyPreset(EditState.FilterStyle filter, EditState.FrameStyle frame, EditState.StickerStyle sticker) {
        pushToUndoStack();
        currentEditState.setFilterStyle(filter);
        currentEditState.setFrameStyle(frame);
        currentEditState.setStickerStyle(sticker);
        applyCurrentEditState();
        syncSelectionsToAdapters();
        updateEditSummary();
    }

    private void updateFilter(EditState.FilterStyle filter) {
        pushToUndoStack();
        currentEditState.setFilterStyle(filter);
        applyCurrentEditState();
        updateEditSummary();
        filterIntensityRow.setVisibility(filter == EditState.FilterStyle.NONE ? View.GONE : View.VISIBLE);
        seekFilterIntensity.setProgress((int)(currentEditState.getFilterIntensity() * 100));
        tvIntensityValue.setText(String.valueOf((int)(currentEditState.getFilterIntensity() * 100)));
    }

    private void updateFrame(EditState.FrameStyle frame) {
        pushToUndoStack();
        currentEditState.setFrameStyle(frame);
        applyCurrentEditState();
        updateEditSummary();
    }

    private void updateSticker(EditState.StickerStyle sticker) {
        pushToUndoStack();
        currentEditState.setStickerStyle(sticker);
        applyCurrentEditState();
        updateEditSummary();
    }

    private void applyCurrentEditState() {
        if (originalBitmap == null) return;
        editedBitmap = renderEditedBitmapUseCase.execute(this, originalBitmap, currentEditState);
        ivEditingPhoto.setImageBitmap(editedBitmap);
    }

    private void syncSelectionsToAdapters() {
        presetsAdapter.setSelectedByValue(null);
        filtersAdapter.setSelectedByValue(currentEditState.getFilterStyle());
        framesAdapter.setSelectedByValue(currentEditState.getFrameStyle());
        stickersAdapter.setSelectedByValue(currentEditState.getStickerStyle());
    }

    private void updateEditSummary() {
        String filter = currentEditState.getFilterStyle().name();
        String sticker = currentEditState.getStickerStyle().name();
        tvEditSummary.setText(String.format("Filter: %s | Sticker: %s", filter, sticker));
        chipActiveEdit.setVisibility(
                (currentEditState.getFilterStyle() != EditState.FilterStyle.NONE ||
                        currentEditState.getStickerStyle() != EditState.StickerStyle.NONE)
                        ? View.VISIBLE : View.GONE
        );
    }

    private void saveAndFinish() {
        if (editedBitmap == null) {
            applyCurrentEditState();
        }

        Uri editedUri = null;
        if (editedBitmap != null) {
            java.io.File file = new java.io.File(getExternalFilesDir(null), "edited_" + System.currentTimeMillis() + ".jpg");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                editedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                editedUri = Uri.fromFile(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (editedUri != null) {
            sessionState.setPhotoEditState(currentPhotoUri.toString(), currentEditState);
            sessionRepository.saveSession(sessionState);

            Intent resultIntent = new Intent();
            resultIntent.putExtra(IntentKeys.EXTRA_ORIGINAL_URI, currentPhotoUri.toString());
            resultIntent.putExtra(IntentKeys.EXTRA_EDITED_URI, editedUri.toString());
            setResult(Activity.RESULT_OK, resultIntent);
        } else {
            setResult(Activity.RESULT_CANCELED);
        }
        finish();
    }

    private Bitmap decodeBitmapFromUri(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);

            int width = options.outWidth;
            int height = options.outHeight;
            int scale = 1;
            while (width / scale / 2 >= MAX_EDIT_BITMAP_SIZE && height / scale / 2 >= MAX_EDIT_BITMAP_SIZE) {
                scale *= 2;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = scale;
            try (InputStream is2 = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(is2, null, options);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}