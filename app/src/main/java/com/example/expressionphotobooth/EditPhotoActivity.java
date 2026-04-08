package com.example.expressionphotobooth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.RectF;
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
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expressionphotobooth.data.graphics.BitmapEditRenderer;
import com.example.expressionphotobooth.domain.model.EditState;
import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.example.expressionphotobooth.domain.usecase.RenderEditedBitmapUseCase;
import com.example.expressionphotobooth.utils.FrameConfig;
import com.example.expressionphotobooth.utils.StickerPlacementMapper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.tabs.TabLayout;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

public class EditPhotoActivity extends AppCompatActivity {

    private static final int MAX_EDIT_BITMAP_SIZE = 1600;

    // Views
    private ImageView ivEditingPhoto;
    private ImageView ivFrameOverlay;
    private Chip chipActiveEdit;
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
    private int selectedFrameResId = -1;

    // ── Thumbnail item model ──────────────────────────────────────────────────

    static class ThumbItem {
        String label;
        int drawableRes;   // preview drawable (small cropped sample); 0 = use colorRes
        int colorRes;      // fallback solid color for preview swatch
        Object value;      // EditState.FilterStyle / FrameStyle / StickerStyle
        String base64;     // New field for custom stickers

        ThumbItem(String label, int drawableRes, int colorRes, Object value) {
            this.label = label;
            this.drawableRes = drawableRes;
            this.colorRes = colorRes;
            this.value = value;
            this.base64 = null;
        }

        ThumbItem(String label, String base64, Object value) {
            this.label = label;
            this.drawableRes = 0;
            this.colorRes = 0;
            this.value = value;
            this.base64 = base64;
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
                if (items.get(i).value != null && items.get(i).value.equals(value)) {
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

            if (item.base64 != null) {
                byte[] bytes = android.util.Base64.decode(item.base64, android.util.Base64.DEFAULT);
                h.preview.setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                h.preview.setBackground(null);
            } else if (item.drawableRes != 0) {
                h.preview.setImageResource(item.drawableRes);
                h.preview.setBackground(null);
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

        selectedFrameResId = resolveSelectedFrameResId();
        applyPreviewAspectForSelectedFrame();
        applyFrameOverlay();

        applyCurrentEditState();
        syncSelectionsToAdapters();
    }

    private void bindViews() {
        ivEditingPhoto    = findViewById(R.id.ivEditingPhoto);
        ivFrameOverlay    = findViewById(R.id.ivFrameOverlay);
        chipActiveEdit    = findViewById(R.id.chipActiveEdit);
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

    private int resolveSelectedFrameResId() {
        if (sessionState != null && sessionState.getSelectedFrameResId() != -1) {
            return sessionState.getSelectedFrameResId();
        }
        // Legacy bridge: old sessions may still rely on this pref value.
        int legacyFrameResId = getSharedPreferences("PhotoboothPrefs", MODE_PRIVATE)
                .getInt("SELECTED_FRAME_ID", -1);
        if (legacyFrameResId != -1 && sessionState != null) {
            sessionState.setSelectedFrameResId(legacyFrameResId);
            sessionRepository.saveSession(sessionState);
        }
        return legacyFrameResId;
    }

    private void applyFrameOverlay() {
        if (ivFrameOverlay == null) {
            return;
        }
        // Option 1: hide full frame overlay in Edit screen to keep single-photo editing clean.
        // selectedFrameResId is still preserved for crop/sticker mapping and final Result render.
        ivFrameOverlay.setVisibility(View.GONE);
        ivFrameOverlay.setImageDrawable(null);
    }

    private int resolveOverlayFrameResId() {
        if (selectedFrameResId != -1) {
            return selectedFrameResId;
        }
        EditState.FrameStyle frameStyle = currentEditState != null ? currentEditState.getFrameStyle() : null;
        if (frameStyle == null) {
            return -1;
        }
        switch (frameStyle) {
            case CORTIS:
                return R.drawable.frm_3x4_movie;
            case T1:
                return R.drawable.frm_3x4_pig_hero;
            case AESPA:
                return R.drawable.frm3_16x9_blue_canvas;
            case NONE:
            default:
                // Do not force setup frame on single-photo edit preview.
                return -1;
        }
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
                new ThumbItem(getString(R.string.edit_option_none),     R.drawable.ic_filter_none, R.color.thumb_none,             "none"),
                new ThumbItem(getString(R.string.edit_preset_cute),    R.drawable.ic_preset_cute, R.color.thumb_preset_cute,    "cute"),
                new ThumbItem(getString(R.string.edit_preset_kpop),    R.drawable.ic_preset_kpop, R.color.thumb_preset_kpop,    "kpop"),
                new ThumbItem(getString(R.string.edit_preset_classic), R.drawable.ic_preset_classic, R.color.thumb_preset_classic, "classic"),
                new ThumbItem(getString(R.string.edit_preset_retro),   R.drawable.ic_preset_retro, R.color.thumb_preset_retro,   "retro"),
                new ThumbItem(getString(R.string.edit_preset_cinematic), R.drawable.ic_preset_cinematic, R.color.thumb_preset_cinematic, "cinematic")
        );
        presetsAdapter = new ThumbAdapter(presetItems, item -> {
            switch ((String) item.value) {
                case "cute":    applyPreset(EditState.FilterStyle.SOFT, EditState.FrameStyle.AESPA, EditState.StickerStyle.STAR);    break;
                case "kpop":    applyPreset(EditState.FilterStyle.NONE, EditState.FrameStyle.T1,    EditState.StickerStyle.FLASH);   break;
                case "classic": applyPreset(EditState.FilterStyle.BW,   EditState.FrameStyle.NONE,  EditState.StickerStyle.NONE);   break;
                case "retro":   applyPreset(EditState.FilterStyle.VINTAGE, EditState.FrameStyle.CORTIS, EditState.StickerStyle.CAMERA); break;
                case "cinematic": applyPreset(EditState.FilterStyle.COOL, EditState.FrameStyle.NONE,  EditState.StickerStyle.NONE); break;
                default:        applyPreset(EditState.FilterStyle.NONE, EditState.FrameStyle.NONE,  EditState.StickerStyle.NONE);   break;
            }
            if (item.value != null && !item.value.equals("none")) {
                Toast.makeText(this, R.string.edit_filter_applied_all, Toast.LENGTH_SHORT).show();
            }
        });
        rvPresets.setAdapter(presetsAdapter);

        // FILTERS
        List<ThumbItem> filterItems = Arrays.asList(
                new ThumbItem(getString(R.string.edit_option_none),     R.drawable.ic_filter_none, R.color.thumb_none,             EditState.FilterStyle.NONE),
                new ThumbItem(getString(R.string.edit_filter_soft),    R.drawable.ic_filter_soft, R.color.thumb_filter_soft,    EditState.FilterStyle.SOFT),
                new ThumbItem(getString(R.string.edit_filter_bw),      R.drawable.ic_filter_bw, R.color.thumb_filter_bw,      EditState.FilterStyle.BW),
                new ThumbItem(getString(R.string.edit_filter_vintage), R.drawable.ic_filter_vintage, R.color.thumb_filter_vintage, EditState.FilterStyle.VINTAGE),
                new ThumbItem(getString(R.string.edit_filter_cool),    R.drawable.ic_filter_cool, R.color.thumb_filter_cool,    EditState.FilterStyle.COOL),
                new ThumbItem(getString(R.string.edit_filter_warm),    R.drawable.ic_filter_warm, R.color.thumb_filter_warm,    EditState.FilterStyle.WARM),
                new ThumbItem(getString(R.string.edit_filter_sepia),   R.drawable.ic_filter_sepia, R.color.thumb_filter_sepia,   EditState.FilterStyle.SEPIA)
        );
        filtersAdapter = new ThumbAdapter(filterItems, item -> {
            updateFilter((EditState.FilterStyle) item.value);
            findViewById(R.id.filterIntensityRow).setVisibility(item.value == EditState.FilterStyle.NONE ? View.GONE : View.VISIBLE);
            if (item.value != EditState.FilterStyle.NONE) {
                Toast.makeText(this, R.string.edit_filter_applied_all, Toast.LENGTH_SHORT).show();
            }
        });
        rvFilters.setAdapter(filtersAdapter);

        findViewById(R.id.btnQuickApplyFilter).setOnClickListener(v -> saveAndFinish());

        // FRAMES
        List<ThumbItem> frameItems = Arrays.asList(
                new ThumbItem(getString(R.string.edit_option_none),    R.drawable.ic_filter_none, R.color.thumb_none,         EditState.FrameStyle.NONE),
                new ThumbItem(getString(R.string.edit_frame_cortis),   0, R.color.thumb_frame_cortis, EditState.FrameStyle.CORTIS),
                new ThumbItem(getString(R.string.edit_frame_aespa),    0, R.color.thumb_frame_aespa,  EditState.FrameStyle.AESPA),
                new ThumbItem(getString(R.string.edit_frame_t1),       0, R.color.thumb_frame_t1,     EditState.FrameStyle.T1)
        );
        framesAdapter = new ThumbAdapter(frameItems, item -> {
            updateFrame((EditState.FrameStyle) item.value);
        });
        rvFrames.setAdapter(framesAdapter);

        // STICKERS
        List<ThumbItem> stickerItems = new ArrayList<>(Arrays.asList(
                new ThumbItem(getString(R.string.edit_option_none),     0, R.color.thumb_none,             EditState.StickerStyle.NONE),
                new ThumbItem(getString(R.string.edit_sticker_star),    R.drawable.ic_star_24, R.color.thumb_sticker_star, EditState.StickerStyle.STAR),
                new ThumbItem(getString(R.string.edit_sticker_heart),   R.drawable.ic_sticker_heart, R.color.thumb_pink, EditState.StickerStyle.HEART),
                new ThumbItem(getString(R.string.edit_sticker_crown),   R.drawable.ic_sticker_crown, R.color.thumb_gold, EditState.StickerStyle.CROWN),
                new ThumbItem(getString(R.string.edit_sticker_smile),   R.drawable.ic_sticker_smile, R.color.thumb_yellow, EditState.StickerStyle.SMILE),
                new ThumbItem(getString(R.string.edit_sticker_flower),  R.drawable.ic_sticker_flower, R.color.thumb_pink, EditState.StickerStyle.FLOWER),
                new ThumbItem(getString(R.string.edit_sticker_camera),  R.drawable.ic_videocam_24, R.color.thumb_blue_grey, EditState.StickerStyle.CAMERA),
                new ThumbItem(getString(R.string.edit_sticker_flash),   R.drawable.ic_flash_on_24, R.color.thumb_blue_grey, EditState.StickerStyle.FLASH)
        ));

        stickersAdapter = new ThumbAdapter(stickerItems, item -> updateSticker(item));
        rvStickers.setAdapter(stickersAdapter);

        // Fetch custom stickers from Firestore
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("custom_stickers")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) return;
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snap) {
                        String label = doc.getString("label");
                        String base64 = doc.getString("base64");
                        if (label != null && base64 != null) {
                            stickerItems.add(new ThumbItem(label, base64, EditState.StickerStyle.CUSTOM));
                        }
                    }
                    stickersAdapter.notifyDataSetChanged();
                });
    }

    private void updateSticker(ThumbItem item) {
        pushToUndoStack();
        EditState.StickerStyle style = (EditState.StickerStyle) item.value;
        currentEditState.setStickerStyle(style);
        if (style == EditState.StickerStyle.CUSTOM) {
            currentEditState.setCustomStickerBase64(item.base64);
        } else {
            currentEditState.setCustomStickerBase64(null);
        }
        applyCurrentEditState();
    }

    private void setupIntensitySlider() {
        seekFilterIntensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float intensity = progress / 100f;
                    currentEditState.setFilterIntensity(intensity);
                    applyFilterToAllPhotos(currentEditState.getFilterStyle(), intensity);
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

                RectF contentRect = StickerPlacementMapper.calculateCenterCropViewContentRect(viewWidth, viewHeight, imgWidth, imgHeight);
                float scale = Math.max(viewWidth / imgWidth, viewHeight / imgHeight);
                float drawLeft = contentRect.left;
                float drawTop = contentRect.top;

                float bX = (event.getX() - drawLeft) / scale;
                float bY = (event.getY() - drawTop) / scale;

                RectF cropRect = resolveFrameCropRectForBitmap(imgWidth, imgHeight);

                float sx = currentEditState.getStickerX();
                float sy = currentEditState.getStickerY();
                float cropX = currentEditState.getStickerCropX();
                float cropY = currentEditState.getStickerCropY();

                float bw = imgWidth;
                float bh = imgHeight;

                int minL = Math.min((int)bw, (int)bh);
                int size = Math.max(72, minL / 5);

                if (cropX < 0f || cropY < 0f) {
                    cropX = 0.84f;
                    cropY = 0.18f;
                    sx = (cropRect.left + cropRect.width() * cropX) / bw;
                    sy = (cropRect.top + cropRect.height() * cropY) / bh;
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

                            float clampedCenterX = Math.max(cropRect.left, Math.min(newCenterX, cropRect.right));
                            float clampedCenterY = Math.max(cropRect.top, Math.min(newCenterY, cropRect.bottom));

                            float normCropX = StickerPlacementMapper.clamp01((clampedCenterX - cropRect.left) / Math.max(1f, cropRect.width()));
                            float normCropY = StickerPlacementMapper.clamp01((clampedCenterY - cropRect.top) / Math.max(1f, cropRect.height()));

                            currentEditState.setStickerX(clampedCenterX / bw);
                            currentEditState.setStickerY(clampedCenterY / bh);
                            currentEditState.setStickerCropX(normCropX);
                            currentEditState.setStickerCropY(normCropY);
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
        } else {
            Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyPreset(EditState.FilterStyle filter, EditState.FrameStyle frame, EditState.StickerStyle sticker) {
        pushToUndoStack();
        currentEditState.setFilterStyle(filter);
        currentEditState.setFrameStyle(frame);
        currentEditState.setStickerStyle(sticker);
        
        // Filter áp dụng cho tất cả
        applyFilterToAllPhotos(filter, currentEditState.getFilterIntensity());
        
        applyCurrentEditState();
        syncSelectionsToAdapters();
    }

    private void updateFilter(EditState.FilterStyle filter) {
        pushToUndoStack();
        currentEditState.setFilterStyle(filter);
        applyFilterToAllPhotos(filter, currentEditState.getFilterIntensity());
        applyCurrentEditState();
        filterIntensityRow.setVisibility(filter == EditState.FilterStyle.NONE ? View.GONE : View.VISIBLE);
        seekFilterIntensity.setProgress((int)(currentEditState.getFilterIntensity() * 100));
        tvIntensityValue.setText(String.valueOf((int)(currentEditState.getFilterIntensity() * 100)));
    }

    private void applyFilterToAllPhotos(EditState.FilterStyle filter, float intensity) {
        // Cập nhật filter cho bộ nhớ session (global)
        sessionState.getEditState().setFilterStyle(filter);
        sessionState.getEditState().setFilterIntensity(intensity);
        
        // Cập nhật filter cho tất cả các tấm ảnh khác đã/chưa có trong map
        for (String uri : sessionState.getCapturedImageUris()) {
            EditState state = sessionState.getPhotoEditState(uri);
            state.setFilterStyle(filter);
            state.setFilterIntensity(intensity);
            sessionState.setPhotoEditState(uri, state);
        }
    }

    private void updateFrame(EditState.FrameStyle frame) {
        pushToUndoStack();
        currentEditState.setFrameStyle(frame);
        applyCurrentEditState();
    }

    private void updateSticker(EditState.StickerStyle sticker) {
        pushToUndoStack();
        currentEditState.setStickerStyle(sticker);
        currentEditState.setCustomStickerBase64(null); // Clear custom if any
        applyCurrentEditState();
    }

    private void applyCurrentEditState() {
        if (originalBitmap == null) return;
        ensureStickerPlacementAlignedToSelectedFrame();
        editedBitmap = renderEditedBitmapUseCase.execute(this, originalBitmap, currentEditState);
        ivEditingPhoto.setImageBitmap(editedBitmap);
        applyFrameOverlay();
        
        chipActiveEdit.setVisibility(
                (currentEditState.getFilterStyle() != EditState.FilterStyle.NONE ||
                        currentEditState.getStickerStyle() != EditState.StickerStyle.NONE)
                        ? View.VISIBLE : View.GONE
        );
    }

    private void syncSelectionsToAdapters() {
        presetsAdapter.setSelectedByValue(null);
        filtersAdapter.setSelectedByValue(currentEditState.getFilterStyle());
        framesAdapter.setSelectedByValue(currentEditState.getFrameStyle());
        stickersAdapter.setSelectedByValue(currentEditState.getStickerStyle());
    }

    private void saveAndFinish() {
        // Show progress since we might be rendering multiple photos (up to 6)
        View progressView = findViewById(R.id.progress);
        if (progressView != null) progressView.setVisibility(View.VISIBLE);
        findViewById(R.id.btnFinishEdit).setEnabled(false);

        new Thread(() -> {
            try {
                // 1. Render and save the CURRENT photo first (to return immediate result if needed)
                if (editedBitmap == null) {
                    runOnUiThread(this::applyCurrentEditState);
                    // Wait a bit for UI thread to finish applying if needed, 
                    // or just use renderEditedBitmapUseCase directly here.
                }
                
                // We use the use case directly to avoid UI thread dependencies.
                Bitmap currentEdited = renderEditedBitmapUseCase.execute(this, originalBitmap, currentEditState);
                Uri currentEditedUri = saveToInternalCache(currentEdited, "edited_" + System.currentTimeMillis() + ".jpg");

                if (currentEditedUri != null) {
                    sessionState.setPhotoEditState(currentPhotoUri.toString(), currentEditState);
                    sessionState.getEditedImageUris().put(currentPhotoUri.toString(), currentEditedUri.toString());
                }

                // 2. Render and save ALL OTHER PHOTOS with the same filter settings
                List<String> allUris = sessionState.getCapturedImageUris();
                for (String uriStr : allUris) {
                    if (uriStr.equals(currentPhotoUri.toString())) continue;

                    Uri uri = Uri.parse(uriStr);
                    Bitmap otherOriginal = decodeBitmapFromUri(uri);
                    if (otherOriginal != null) {
                        EditState otherState = sessionState.getPhotoEditState(uriStr);
                        // Filter is already updated in otherState by applyFilterToAllPhotos()
                        Bitmap otherEdited = renderEditedBitmapUseCase.execute(this, otherOriginal, otherState);
                        Uri otherEditedUri = saveToInternalCache(otherEdited, "edited_auto_" + System.currentTimeMillis() + ".jpg");
                        
                        if (otherEditedUri != null) {
                            sessionState.getEditedImageUris().put(uriStr, otherEditedUri.toString());
                        }
                        otherOriginal.recycle();
                        otherEdited.recycle();
                    }
                }

                // Finalize session
                sessionRepository.saveSession(sessionState);

                runOnUiThread(() -> {
                    if (progressView != null) progressView.setVisibility(View.GONE);
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra(IntentKeys.EXTRA_ORIGINAL_URI, currentPhotoUri.toString());
                    resultIntent.putExtra(IntentKeys.EXTRA_EDITED_URI, currentEditedUri != null ? currentEditedUri.toString() : "");
                    setResult(Activity.RESULT_OK, resultIntent);
                    finish();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    if (progressView != null) progressView.setVisibility(View.GONE);
                    findViewById(R.id.btnFinishEdit).setEnabled(true);
                    Toast.makeText(this, "Error saving edits: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private Uri saveToInternalCache(Bitmap bitmap, String filename) {
        if (bitmap == null) return null;
        java.io.File file = new java.io.File(getExternalFilesDir(null), filename);
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            return Uri.fromFile(file);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
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
                Bitmap bitmap = BitmapFactory.decodeStream(is2, null, options);
                return rotateIfRequired(bitmap, uri);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Bitmap rotateIfRequired(Bitmap img, Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            ExifInterface ei = new ExifInterface(input);
            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:  return rotateImage(img, 90);
                case ExifInterface.ORIENTATION_ROTATE_180: return rotateImage(img, 180);
                case ExifInterface.ORIENTATION_ROTATE_270: return rotateImage(img, 270);
                default: return img;
            }
        } catch (IOException e) {
            return img;
        }
    }

    private Bitmap rotateImage(Bitmap img, int degree) {
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotatedImg;
    }

    private RectF resolveFrameCropRectForBitmap(float bitmapWidth, float bitmapHeight) {
        if (selectedFrameResId == -1) {
            return new RectF(0f, 0f, bitmapWidth, bitmapHeight);
        }
        return StickerPlacementMapper.resolveFrameCropRect(selectedFrameResId, bitmapWidth, bitmapHeight);
    }

    private void ensureStickerPlacementAlignedToSelectedFrame() {
        if (currentEditState == null || originalBitmap == null) {
            return;
        }
        RectF cropRect = resolveFrameCropRectForBitmap(originalBitmap.getWidth(), originalBitmap.getHeight());
        RectF cropNorm = StickerPlacementMapper.toNormalizedRect(cropRect, originalBitmap.getWidth(), originalBitmap.getHeight());

        currentEditState.setStickerCropLeftNorm(cropNorm.left);
        currentEditState.setStickerCropTopNorm(cropNorm.top);
        currentEditState.setStickerCropRightNorm(cropNorm.right);
        currentEditState.setStickerCropBottomNorm(cropNorm.bottom);

        float cropX = currentEditState.getStickerCropX();
        float cropY = currentEditState.getStickerCropY();
        if (cropX < 0f || cropY < 0f) {
            float legacyX = currentEditState.getStickerX();
            float legacyY = currentEditState.getStickerY();
            if (legacyX >= 0f && legacyY >= 0f) {
                float absX = legacyX * originalBitmap.getWidth();
                float absY = legacyY * originalBitmap.getHeight();
                cropX = StickerPlacementMapper.clamp01((absX - cropRect.left) / Math.max(1f, cropRect.width()));
                cropY = StickerPlacementMapper.clamp01((absY - cropRect.top) / Math.max(1f, cropRect.height()));
            } else {
                cropX = 0.84f;
                cropY = 0.18f;
            }
            currentEditState.setStickerCropX(cropX);
            currentEditState.setStickerCropY(cropY);
        }

        float mappedAbsX = cropRect.left + StickerPlacementMapper.clamp01(cropX) * cropRect.width();
        float mappedAbsY = cropRect.top + StickerPlacementMapper.clamp01(cropY) * cropRect.height();
        currentEditState.setStickerX(mappedAbsX / originalBitmap.getWidth());
        currentEditState.setStickerY(mappedAbsY / originalBitmap.getHeight());
    }

    private void applyPreviewAspectForSelectedFrame() {
        View previewCard = findViewById(R.id.photoPreviewCard);
        if (!(previewCard.getLayoutParams() instanceof androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)) {
            return;
        }

        Rect primaryHole = selectedFrameResId != -1 ? FrameConfig.getPrimaryHoleForFrame(selectedFrameResId) : null;
        int ratioW = 3;
        int ratioH = 4;
        if (primaryHole != null && primaryHole.width() > 0 && primaryHole.height() > 0) {
            ratioW = primaryHole.width();
            ratioH = primaryHole.height();
        }

        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) previewCard.getLayoutParams();
        params.dimensionRatio = String.format(Locale.US, "%d:%d", ratioW, ratioH);
        previewCard.setLayoutParams(params);
    }
}