package com.example.expressionphotobooth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.util.Log;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import com.example.expressionphotobooth.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expressionphotobooth.data.graphics.BitmapEditRenderer;
import com.example.expressionphotobooth.domain.model.EditState;
import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.model.UserRole;
import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.example.expressionphotobooth.domain.usecase.RenderEditedBitmapUseCase;
import com.example.expressionphotobooth.utils.FrameConfig;
import com.example.expressionphotobooth.utils.StickerPlacementMapper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import android.graphics.drawable.Drawable;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;

public class EditPhotoActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(com.example.expressionphotobooth.utils.LocaleManager.wrapContext(newBase));
    }


    private static final int MAX_EDIT_BITMAP_SIZE = 1600;
    private static final float MIN_STICKER_SCALE = 0.3f;
    private static final float MAX_STICKER_SCALE = 5.0f;
    private static final float STICKER_SCALE_PRESET_S = 0.8f;
    private static final float STICKER_SCALE_PRESET_M = 1.0f;
    private static final float STICKER_SCALE_PRESET_L = 1.3f;

    // Views
    private View btnCompare;
    private ImageView ivEditingPhoto;
    private ImageView ivFrameOverlay;
    private Chip chipActiveEdit;
    private TabLayout editTabLayout;
    private LinearLayout panelFilters, panelFrames, panelStickers, panelBackgrounds;
    private LinearLayout filterIntensityRow;
    private SeekBar seekFilterIntensity;
    private TextView tvIntensityValue;
    private LinearLayout stickerSizeRow;
    private SeekBar seekStickerSize;
    private TextView tvStickerSizeValue;
    private ChipGroup stickerSizePresetGroup;
    private Chip chipStickerSizeSmall;
    private Chip chipStickerSizeMedium;
    private Chip chipStickerSizeLarge;
    private View stickerQuickActionsRow;
    private View btnStickerCenter;
    private View btnStickerReset;

    // RecyclerViews
    private RecyclerView rvFilters, rvFrames, rvStickers, rvBackgrounds;

    // State
    private Bitmap originalBitmap;
    private Bitmap editedBitmap;
    private Bitmap backgroundCachedBitmap;
    private com.example.expressionphotobooth.domain.usecase.PortraitProcessor portraitProcessor;
    private SessionRepository sessionRepository;
    private SessionState sessionState;
    private EditState currentEditState;
    private Stack<EditState> undoStack = new Stack<>();
    private RenderEditedBitmapUseCase renderEditedBitmapUseCase;
    private Uri currentPhotoUri;
    private int selectedFrameResId = -1;
    private ScaleGestureDetector scaleGestureDetector;
    private UserRole currentUserRole = UserRole.USER;
    private long premiumUntil = 0L;

    // ── Thumbnail item model ──────────────────────────────────────────────────

    static class ThumbItem {
        String id;         // Firestore doc ID
        String label;
        int drawableRes;
        int colorRes;
        Object value;
        String base64;
        String imageUrl;   // URL from Storage
        boolean isRemovable;
        boolean isGlobal;

        ThumbItem(String label, int drawableRes, int colorRes, Object value) {
            this.label = label;
            this.drawableRes = drawableRes;
            this.colorRes = colorRes;
            this.value = value;
            this.base64 = null;
            this.imageUrl = null;
            this.isRemovable = false;
            this.isGlobal = false;
        }

        /** Constructor for items that show a URL-based preview image (e.g. asset backgrounds). */
        ThumbItem(String label, String imageUrl, Object value) {
            this.label = label;
            this.drawableRes = 0;
            this.colorRes = 0;
            this.value = value;
            this.base64 = null;
            this.imageUrl = imageUrl;
            this.isRemovable = false;
            this.isGlobal = false;
        }

        ThumbItem(String id, String label, String base64, String imageUrl, Object value) {
            this.id = id;
            this.label = label;
            this.drawableRes = 0;
            this.colorRes = 0;
            this.value = value;
            this.base64 = base64;
            this.imageUrl = imageUrl;
            this.isRemovable = false;
            this.isGlobal = false;
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
                ThumbItem item = items.get(i);
                boolean match = false;
                if (item.value != null && item.value.equals(value)) {
                    if (value == EditState.StickerStyle.CUSTOM) {
                        // For stickers, also check base64 to distinguish between custom ones
                        String currentB64 = currentEditState != null ? currentEditState.getCustomStickerBase64() : null;
                        if (currentB64 != null && currentB64.equals(item.base64)) {
                            match = true;
                        }
                    } else {
                        match = true;
                    }
                }
                if (match) {
                    int old = selectedPos;
                    selectedPos = i;
                    notifyItemChanged(old);
                    notifyItemChanged(i);
                    return;
                }
            }
        }

        void submitItems(List<ThumbItem> newItems) {
            items.clear();
            items.addAll(newItems);
            selectedPos = 0;
            notifyDataSetChanged();
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
            h.label.setVisibility(item.label.isEmpty() ? View.INVISIBLE : View.VISIBLE);

            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                Glide.with(h.itemView.getContext())
                        .load(item.imageUrl)
                        .placeholder(R.color.thumb_none)
                        .error(R.drawable.ic_filter_none)
                        .centerCrop()
                        .into(h.preview);
                h.preview.setBackground(null);
            } else if (item.base64 != null) {
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

            h.btnDelete.setVisibility(item.isRemovable ? View.VISIBLE : View.GONE);
            h.btnDelete.setOnClickListener(v -> {
                deleteCustomSticker(item);
            });

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
            View btnDelete;

            VH(@NonNull View itemView) {
                super(itemView);
                preview   = itemView.findViewById(R.id.ivThumbPreview);
                cardThumb = itemView.findViewById(R.id.cardThumb);
                label     = itemView.findViewById(R.id.tvThumbLabel);
                btnDelete = itemView.findViewById(R.id.btnDeleteSticker);
            }
        }
    }

    // ── Adapters ──────────────────────────────────────────────────────────────

    private ThumbAdapter filtersAdapter;
    private ThumbAdapter framesAdapter;
    private ThumbAdapter stickersAdapter;
    private ThumbAdapter backgroundsAdapter;
    private TabLayout stickerCategoryTabLayout;

    private enum StickerCategory {
        CUTE,
        Y2K,
        KPOP,
        CAMERA,
        VIP,
        STORE
    }

    private StickerCategory currentStickerCategory = StickerCategory.CUTE;
    private final Map<StickerCategory, int[]> stickerScrollState = new HashMap<>();
    private long stickerFadeOutDurationMs = 100L;
    private long stickerFadeInDurationMs = 130L;
    private final List<ThumbItem> globalStickers = new ArrayList<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_photo);

        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();
        sessionState = sessionRepository.getSession();
        
        ((AppContainer) getApplication()).getAuthRepository().fetchCurrentUserInfo(new AuthRepository.UserInfoCallback() {
            @Override
            public void onSuccess(UserRole role, long until) {
                currentUserRole = role;
                premiumUntil = until;
            }
            @Override
            public void onError(String message) {}
        });

        renderEditedBitmapUseCase = new RenderEditedBitmapUseCase(new BitmapEditRenderer());
        portraitProcessor = new com.example.expressionphotobooth.domain.usecase.PortraitProcessor();
        renderEditedBitmapUseCase.setPortraitProcessor(portraitProcessor);

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
        configureStickerTabAnimationDurations();
        setupToolbar();
        setupTabs();
        setupStickerCategoryTabs();
        setupAdapters();
        setupIntensitySlider();
        setupStickerSizeSlider();
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
        if (currentEditState.getBackgroundStyle() != EditState.BackgroundStyle.NONE) {
            refreshBackground();
        }
        syncSelectionsToAdapters();
        loadGlobalStickers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh stickers in case they were unlocked via sharing in ResultActivity
        loadGlobalStickers();
    }

    private void bindViews() {
        ivEditingPhoto    = findViewById(R.id.ivEditingPhoto);
        ivFrameOverlay    = findViewById(R.id.ivFrameOverlay);
        chipActiveEdit    = findViewById(R.id.chipActiveEdit);
        editTabLayout     = findViewById(R.id.editTabLayout);
        btnCompare        = findViewById(R.id.btnCompare);
        panelFilters      = findViewById(R.id.panelFilters);
        panelFrames       = findViewById(R.id.panelFrames);
        panelBackgrounds  = findViewById(R.id.panelBackgrounds);
        panelStickers     = findViewById(R.id.panelStickers);
        filterIntensityRow = findViewById(R.id.filterIntensityRow);
        seekFilterIntensity = findViewById(R.id.seekFilterIntensity);
        tvIntensityValue  = findViewById(R.id.tvIntensityValue);
        stickerSizeRow = findViewById(R.id.stickerSizeRow);
        seekStickerSize = findViewById(R.id.seekStickerSize);
        tvStickerSizeValue = findViewById(R.id.tvStickerSizeValue);
        stickerSizePresetGroup = findViewById(R.id.stickerSizePresetGroup);
        chipStickerSizeSmall = findViewById(R.id.chipStickerSizeSmall);
        chipStickerSizeMedium = findViewById(R.id.chipStickerSizeMedium);
        chipStickerSizeLarge = findViewById(R.id.chipStickerSizeLarge);
        stickerQuickActionsRow = findViewById(R.id.stickerQuickActionsRow);
        btnStickerCenter = findViewById(R.id.btnStickerCenter);
        btnStickerReset = findViewById(R.id.btnStickerReset);
        rvFilters         = findViewById(R.id.rvFilters);
        rvFrames          = findViewById(R.id.rvFrames);
        rvBackgrounds     = findViewById(R.id.rvBackgrounds);
        rvStickers        = findViewById(R.id.rvStickers);
        stickerCategoryTabLayout = findViewById(R.id.stickerCategoryTabLayout);
    }

    private void setupStickerCategoryTabs() {
        if (stickerCategoryTabLayout == null) {
            return;
        }
        stickerCategoryTabLayout.removeAllTabs();
        stickerCategoryTabLayout.addTab(stickerCategoryTabLayout.newTab().setText(getString(R.string.edit_sticker_tab_cute)));
        stickerCategoryTabLayout.addTab(stickerCategoryTabLayout.newTab().setText(getString(R.string.edit_sticker_tab_y2k)));
        stickerCategoryTabLayout.addTab(stickerCategoryTabLayout.newTab().setText(getString(R.string.edit_sticker_tab_kpop)));
        stickerCategoryTabLayout.addTab(stickerCategoryTabLayout.newTab().setText(getString(R.string.edit_sticker_tab_camera)));
        stickerCategoryTabLayout.addTab(stickerCategoryTabLayout.newTab().setText("VIP"));
        stickerCategoryTabLayout.addTab(stickerCategoryTabLayout.newTab().setText(getString(R.string.edit_sticker_tab_store)));

        stickerCategoryTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                saveCurrentStickerScrollState();
                StickerCategory nextCategory = StickerCategory.values()[Math.max(0, Math.min(tab.getPosition(), StickerCategory.values().length - 1))];
                if (nextCategory == currentStickerCategory) {
                    restoreStickerScrollState(nextCategory);
                    return;
                }
                currentStickerCategory = nextCategory;
                animateStickerCategorySwitch();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                onTabSelected(tab);
            }
        });
    }

    private int resolveSelectedFrameResId() {
        if (sessionState != null && sessionState.getSelectedFrameResId() > 0) {
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
        if (selectedFrameResId > 0) {
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

    @Override
    protected void onDestroy() {
        if (portraitProcessor != null) portraitProcessor.close();
        super.onDestroy();
    }

    // ── Tab setup ─────────────────────────────────────────────────────────────

    private void setupTabs() {
        editTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showPanel(tab.getPosition());
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        showPanel(0); // start on Filters
    }

    private void showPanel(int index) {
        if (index == 1) {
            AuthRepository authRepo = ((AppContainer) getApplication()).getAuthRepository();
            if (authRepo.isGuest()) {
                String languageTag = com.example.expressionphotobooth.utils.LocaleManager.getCurrentLanguage(this);
                com.example.expressionphotobooth.HelpDialogUtils.showHistoryGuestRegisterCta(
                        this,
                        com.example.expressionphotobooth.utils.LocaleManager.getString(this, R.string.home_background_user_only_title, languageTag),
                        com.example.expressionphotobooth.utils.LocaleManager.getString(this, R.string.home_background_user_only_message, languageTag),
                        this::openRegisterFromGuest
                );
                // Switch back to Filters
                TabLayout.Tab tab = editTabLayout.getTabAt(0);
                if (tab != null) tab.select();
                return;
            }
        }
        panelFilters.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        panelBackgrounds.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        panelFrames.setVisibility(View.GONE);
        panelStickers.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        if (index == 2) {
            updateStickerSizeUi();
        }
    }

    // ── Adapters setup ────────────────────────────────────────────────────────

    private void setupAdapters() {

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
                new ThumbItem(getString(R.string.edit_frame_cortis),   R.drawable.frm_3x4_movie, R.color.thumb_frame_cortis, EditState.FrameStyle.CORTIS),
                new ThumbItem(getString(R.string.edit_frame_aespa),    R.drawable.frm3_16x9_blue_canvas, R.color.thumb_frame_aespa,  EditState.FrameStyle.AESPA),
                new ThumbItem(getString(R.string.edit_frame_t1),       R.drawable.frm_3x4_pig_hero, R.color.thumb_frame_t1,     EditState.FrameStyle.T1)
        );
        framesAdapter = new ThumbAdapter(frameItems, item -> {
            updateFrame((EditState.FrameStyle) item.value);
        });
        rvFrames.setAdapter(framesAdapter);

        // BACKGROUNDS
        List<ThumbItem> backgroundItems = Arrays.asList(
                new ThumbItem(getString(R.string.edit_option_none), R.drawable.ic_filter_none, R.color.thumb_none, EditState.BackgroundStyle.NONE),
                new ThumbItem(getString(R.string.edit_bg_blur), R.drawable.ic_filter_soft, R.color.thumb_filter_soft, EditState.BackgroundStyle.BLUR),
                new ThumbItem(getString(R.string.edit_bg_studio), "file:///android_asset/backgrounds/bg_studio.png", EditState.BackgroundStyle.STUDIO),
                new ThumbItem(getString(R.string.edit_bg_beach), "file:///android_asset/backgrounds/bg_beach.png", EditState.BackgroundStyle.BEACH),
                new ThumbItem(getString(R.string.edit_bg_space), "file:///android_asset/backgrounds/bg_space.png", EditState.BackgroundStyle.SPACE),
                new ThumbItem(getString(R.string.edit_bg_vintage), "file:///android_asset/backgrounds/bg_vintage.png", EditState.BackgroundStyle.VINTAGE),
                new ThumbItem("", R.drawable.ic_add_24, R.color.edit_accent, "ADD_BG")
        );

        backgroundsAdapter = new ThumbAdapter(backgroundItems, item -> {
            if ("ADD_BG".equals(item.value)) {
                AuthRepository authRepo = ((AppContainer) getApplication()).getAuthRepository();
                if (authRepo.isGuest()) {
                    com.example.expressionphotobooth.HelpDialogUtils.showCenteredNotice(
                            this,
                            getString(R.string.main_ai_login_required_title),
                            getString(R.string.main_ai_login_required_message),
                            false
                    );
                    return;
                }

                boolean isPremium = (currentUserRole == UserRole.PREMIUM && premiumUntil > System.currentTimeMillis());
                if (currentUserRole != UserRole.ADMIN && !isPremium) {
                    String paymentUrl = "https://img.vietqr.io/image/MB-56111166662004-compact.png" +
                            "?amount=50000&addInfo=Premium%20Sub%20" + authRepo.getCurrentEmail() +
                            "&accountName=PHOTO%20BOOTH";
                    com.example.expressionphotobooth.HelpDialogUtils.showSubscriptionQR(this, paymentUrl);
                } else {
                    openBackgroundPicker();
                }
            } else {
                updateBackground((EditState.BackgroundStyle) item.value);
            }
        });
        rvBackgrounds.setAdapter(backgroundsAdapter);

        stickersAdapter = new ThumbAdapter(new ArrayList<>(), item -> {
            if ("ADD_NEW".equals(item.value)) {
                openStickerPicker();
            } else {
                updateSticker(item);
            }
        });
        rvStickers.setAdapter(stickersAdapter);
        updateStickerCategorySelectionFromCurrentState();
        refreshStickerAdapter();
    }

    private void refreshStickerAdapter() {
        if (rvStickers == null) {
            return;
        }
        List<ThumbItem> stickerItems = buildStickerItemsForCategory(currentStickerCategory);
        stickersAdapter.submitItems(stickerItems);
        stickersAdapter.setSelectedByValue(currentEditState.getStickerStyle());
        restoreStickerScrollState(currentStickerCategory);
        updateStickerSizeUi();
    }

    private void animateStickerCategorySwitch() {
        if (rvStickers == null) {
            refreshStickerAdapter();
            return;
        }
        rvStickers.animate().cancel();
        rvStickers.animate()
                .alpha(0f)
                .setDuration(stickerFadeOutDurationMs)
                .withEndAction(() -> {
                    refreshStickerAdapter();
                    rvStickers.animate().alpha(1f).setDuration(stickerFadeInDurationMs).start();
                })
                .start();
    }

    private void configureStickerTabAnimationDurations() {
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        boolean isLowRam = activityManager != null && activityManager.isLowRamDevice();
        // Keep transitions subtle and cheaper on low-end devices.
        stickerFadeOutDurationMs = isLowRam ? 70L : 100L;
        stickerFadeInDurationMs = isLowRam ? 90L : 130L;
    }

    private void saveCurrentStickerScrollState() {
        if (rvStickers == null) {
            return;
        }
        RecyclerView.LayoutManager lm = rvStickers.getLayoutManager();
        if (!(lm instanceof GridLayoutManager)) {
            return;
        }
        GridLayoutManager glm = (GridLayoutManager) lm;
        int first = glm.findFirstVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION) {
            return;
        }
        View firstView = glm.findViewByPosition(first);
        int offset = firstView != null ? firstView.getTop() - rvStickers.getPaddingTop() : 0;
        stickerScrollState.put(currentStickerCategory, new int[]{first, offset});
    }

    private void restoreStickerScrollState(StickerCategory category) {
        if (rvStickers == null) {
            return;
        }
        int[] state = stickerScrollState.get(category);
        if (state == null) {
            return;
        }
        RecyclerView.LayoutManager lm = rvStickers.getLayoutManager();
        if (!(lm instanceof GridLayoutManager)) {
            return;
        }
        GridLayoutManager glm = (GridLayoutManager) lm;
        rvStickers.post(() -> glm.scrollToPositionWithOffset(state[0], state[1]));
    }

    private List<ThumbItem> buildStickerItemsForCategory(StickerCategory category) {
        List<ThumbItem> stickerItems = new ArrayList<>();
        stickerItems.add(new ThumbItem("", R.drawable.ic_add_24, R.color.edit_accent, "ADD_NEW"));
        stickerItems.add(new ThumbItem(getString(R.string.edit_option_none), 0, R.color.thumb_none, EditState.StickerStyle.NONE));

        switch (category) {
            case CUTE:
                stickerItems.add(new ThumbItem(getString(R.string.edit_sticker_heart), R.drawable.ic_sticker_heart, R.color.thumb_pink, EditState.StickerStyle.HEART));
                stickerItems.add(new ThumbItem(getString(R.string.edit_sticker_smile), R.drawable.ic_sticker_smile, R.color.thumb_yellow, EditState.StickerStyle.SMILE));
                stickerItems.add(new ThumbItem(getString(R.string.edit_sticker_flower), R.drawable.ic_sticker_flower, R.color.thumb_pink, EditState.StickerStyle.FLOWER));
                stickerItems.add(new ThumbItem(getString(R.string.edit_sticker_bow), R.drawable.ic_sticker_bow, R.color.thumb_pink, EditState.StickerStyle.BOW));
                break;
            case Y2K:
                stickerItems.add(new ThumbItem(getString(R.string.edit_sticker_star), R.drawable.ic_star_24, R.color.thumb_sticker_star, EditState.StickerStyle.STAR));
                stickerItems.add(new ThumbItem(getString(R.string.edit_sticker_sparkle), R.drawable.ic_sticker_sparkle, R.color.thumb_sticker_star, EditState.StickerStyle.SPARKLE));
                stickerItems.add(new ThumbItem(getString(R.string.edit_sticker_butterfly), R.drawable.ic_sticker_butterfly, R.color.thumb_filter_cool, EditState.StickerStyle.BUTTERFLY));
                stickerItems.add(new ThumbItem(getString(R.string.edit_sticker_cherry), R.drawable.ic_sticker_cherry, R.color.thumb_filter_warm, EditState.StickerStyle.CHERRY));
                break;
            case KPOP:
                stickerItems.add(new ThumbItem(getString(R.string.edit_sticker_crown), R.drawable.ic_sticker_crown, R.color.thumb_gold, EditState.StickerStyle.CROWN));
                stickerItems.add(new ThumbItem(getString(R.string.edit_sticker_music), R.drawable.ic_sticker_music, R.color.thumb_blue_grey, EditState.StickerStyle.MUSIC));
                stickerItems.add(new ThumbItem(getString(R.string.edit_sticker_sparkle), R.drawable.ic_sticker_sparkle, R.color.thumb_sticker_star, EditState.StickerStyle.SPARKLE));
                break;
            case CAMERA:
            default:
                stickerItems.add(new ThumbItem(getString(R.string.edit_sticker_camera), R.drawable.ic_videocam_24, R.color.thumb_blue_grey, EditState.StickerStyle.CAMERA));
                stickerItems.add(new ThumbItem(getString(R.string.edit_sticker_flash), R.drawable.ic_flash_on_24, R.color.thumb_blue_grey, EditState.StickerStyle.FLASH));
                break;
        }

        AuthRepository authRepository = ((AppContainer) getApplication()).getAuthRepository();
        // Removed local sticker loading logic as everything is now in Firestore

        // Hiển thị Sticker VIP đã mở khóa
        if (category == StickerCategory.VIP) {
            List<ThumbItem> vipItems = new ArrayList<>();
            for (ThumbItem item : globalStickers) {
                if ("★ VIP".equals(item.label)) {
                    vipItems.add(item);
                }
            }
            if (vipItems.isEmpty()) {
                vipItems.add(new ThumbItem("No VIP yet", 0, R.color.thumb_none, null));
            }
            return vipItems;
        }

        // Add Global Store stickers if this is STORE category
        if (category == StickerCategory.STORE) {
            List<ThumbItem> storeItems = new ArrayList<>();
            storeItems.add(new ThumbItem("", R.drawable.ic_add_24, R.color.edit_accent, "ADD_NEW"));
            // Lọc ra các sticker thường (không phải VIP) để hiện trong Store
            for (ThumbItem item : globalStickers) {
                if (!"★ VIP".equals(item.label)) {
                    storeItems.add(item);
                }
            }
            return storeItems;
        }

        return stickerItems;
    }

    private StickerCategory resolveStickerCategoryForStyle(EditState.StickerStyle style) {
        if (style == null) {
            return StickerCategory.CUTE;
        }
        switch (style) {
            case STAR:
            case SPARKLE:
            case BUTTERFLY:
            case CHERRY:
                return StickerCategory.Y2K;
            case CROWN:
            case MUSIC:
                return StickerCategory.KPOP;
            case CAMERA:
            case FLASH:
                return StickerCategory.CAMERA;
            case HEART:
            case SMILE:
            case FLOWER:
            case BOW:
            case NONE:
                return StickerCategory.CUTE;
            case CUSTOM:
                if (currentEditState != null && currentEditState.isFromStore()) {
                    return StickerCategory.STORE;
                }
                return StickerCategory.CUTE;
            default:
                return StickerCategory.CUTE;
        }
    }

    private void updateStickerCategorySelectionFromCurrentState() {
        currentStickerCategory = resolveStickerCategoryForStyle(currentEditState.getStickerStyle());
        if (stickerCategoryTabLayout != null) {
            int tabIndex = currentStickerCategory.ordinal();
            TabLayout.Tab tab = stickerCategoryTabLayout.getTabAt(tabIndex);
            if (tab != null && !tab.isSelected()) {
                tab.select();
            }
        }
    }

    private final androidx.activity.result.ActivityResultLauncher<Intent> backgroundPickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    handleCustomBackgroundPicked(result.getData().getData());
                }
            });

    private void openBackgroundPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        backgroundPickerLauncher.launch(Intent.createChooser(intent, getString(R.string.edit_bg_picker_title)));
    }

    private void handleCustomBackgroundPicked(Uri uri) {
        if (uri == null) return;
        Toast.makeText(this, R.string.edit_bg_processing, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                if (bitmap != null) {
                    // Normalizing size for background to avoid OOM
                    int maxDim = 1200;
                    float ratio = (float) bitmap.getWidth() / bitmap.getHeight();
                    int w = (ratio > 1) ? maxDim : (int) (maxDim * ratio);
                    int h = (ratio > 1) ? (int) (maxDim / ratio) : maxDim;
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap, w, h, true);
                    
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                    byte[] bytes = baos.toByteArray();
                    String base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                    
                    runOnUiThread(() -> {
                        pushToUndoStack();
                        currentEditState.setBackgroundStyle(EditState.BackgroundStyle.CUSTOM);
                        currentEditState.setCustomBackgroundBase64(base64);
                        refreshBackground();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private final androidx.activity.result.ActivityResultLauncher<Intent> stickerPickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    handleCustomStickerPicked(result.getData().getData());
                }
            });

    private void openStickerPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        stickerPickerLauncher.launch(Intent.createChooser(intent, getString(R.string.edit_sticker_picker_title)));
    }

    private void handleCustomStickerPicked(Uri uri) {
        if (uri == null) return;
        Toast.makeText(this, R.string.edit_sticker_processing, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                if (bitmap != null) {
                    removeBackgroundAndSave(bitmap);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void removeBackgroundAndSave(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        SubjectSegmenterOptions options = new SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build();
        SubjectSegmenter segmenter = SubjectSegmentation.getClient(options);

        segmenter.process(image)
                .addOnSuccessListener(result -> {
                    Bitmap foreground = result.getForegroundBitmap();
                    if (foreground != null) {
                        finalizeStickerCreation(foreground);
                    } else {
                        // Fallback if no foreground subject detected
                        finalizeStickerCreation(bitmap);
                    }
                })
                .addOnFailureListener(e -> {
                    finalizeStickerCreation(bitmap);
                });
    }

    private void finalizeStickerCreation(Bitmap bitmap) {
        new Thread(() -> {
            // Normalize size
            int size = 400;
            float ratio = (float) bitmap.getWidth() / bitmap.getHeight();
            int w = (ratio > 1) ? size : (int) (size * ratio);
            int h = (ratio > 1) ? (int) (size / ratio) : size;

            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, w, h, true);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.PNG, 90, baos);
            byte[] bytes = baos.toByteArray();
            String base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);

            AuthRepository authRepository = ((AppContainer) getApplication()).getAuthRepository();
            String uid = authRepository.getCurrentUid();

            Map<String, Object> data = new HashMap<>();
            data.put("type", "user");
            data.put("userId", uid);
            data.put("base64", base64);
            data.put("timestamp", System.currentTimeMillis());

            FirebaseFirestore.getInstance().collection("stickers")
                    .add(data)
                    .addOnSuccessListener(docRef -> {
                        runOnUiThread(() -> {
                            loadGlobalStickers(); 
                            updateSticker(new ThumbItem(docRef.getId(), "Me", base64, null, EditState.StickerStyle.CUSTOM));
                        });
                    })
                    .addOnFailureListener(e -> {
                        runOnUiThread(() -> Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    });

            scaled.recycle();
        }).start();
    }

    private void saveLocalCustomSticker(String uid, String base64) {
        String key = (uid != null) ? uid : "guest";
        android.content.SharedPreferences prefs = getSharedPreferences("UserStickers_" + key, MODE_PRIVATE);
        String current = prefs.getString("stickers", "");
        if (current.isEmpty()) {
            current = base64;
        } else {
            current = base64 + "|||" + current;
        }
        prefs.edit().putString("stickers", current).apply();
    }

    private List<String> getLocalCustomStickers(String uid) {
        String key = (uid != null) ? uid : "guest";
        android.content.SharedPreferences prefs = getSharedPreferences("UserStickers_" + key, MODE_PRIVATE);
        String saved = prefs.getString("stickers", "");
        if (saved.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(saved.split("\\|\\|\\|")));
    }

    private void updateSticker(ThumbItem item) {
        pushToUndoStack();
        EditState.StickerStyle style = (EditState.StickerStyle) item.value;

        if (style == EditState.StickerStyle.NONE) {
            // Clear only the active sticker, or all if none active
            int active = currentEditState.getActiveStickerIndex();
            if (active >= 0 && active < currentEditState.getStickerItems().size()) {
                currentEditState.removeSticker(active);
            } else {
                currentEditState.getStickerItems().clear();
                currentEditState.setActiveStickerIndex(-1);
            }
            // Also clear legacy single-sticker fields
            currentEditState.setStickerStyle(EditState.StickerStyle.NONE);
            currentEditState.setCustomStickerBase64(null);
            currentEditState.setCustomStickerId(null);
            currentEditState.setFromStore(false);
        } else {
            // Add a new sticker to the list
            com.example.expressionphotobooth.domain.model.StickerItem si =
                    new com.example.expressionphotobooth.domain.model.StickerItem();
            si.setStyle(style);
            if (style == EditState.StickerStyle.CUSTOM) {
                si.setCustomBase64(item.base64);
                si.setCustomId(item.id);
                si.setFromStore(item.isGlobal);
            }
            // Inherit current frame crop bounds so placement is frame-aware
            if (originalBitmap != null) {
                RectF cropRect = resolveFrameCropRectForBitmap(
                        originalBitmap.getWidth(), originalBitmap.getHeight());
                RectF norm = com.example.expressionphotobooth.utils.StickerPlacementMapper
                        .toNormalizedRect(cropRect, originalBitmap.getWidth(), originalBitmap.getHeight());
                si.setCropLeftNorm(norm.left);
                si.setCropTopNorm(norm.top);
                si.setCropRightNorm(norm.right);
                si.setCropBottomNorm(norm.bottom);
                // Default position: top-right of crop rect
                si.setCropX(0.84f);
                si.setCropY(0.18f);
                float absX = cropRect.left + 0.84f * cropRect.width();
                float absY = cropRect.top  + 0.18f * cropRect.height();
                si.setAbsX(absX / originalBitmap.getWidth());
                si.setAbsY(absY / originalBitmap.getHeight());
            }
            currentEditState.addSticker(si);

            // Keep legacy single-sticker in sync (used by old rendering path / export)
            currentEditState.setStickerStyle(style);
            if (style == EditState.StickerStyle.CUSTOM) {
                currentEditState.setCustomStickerBase64(item.base64);
                currentEditState.setCustomStickerId(item.id);
                currentEditState.setFromStore(item.isGlobal);
            } else {
                currentEditState.setCustomStickerBase64(null);
                currentEditState.setCustomStickerId(null);
                currentEditState.setFromStore(false);
            }
        }

        applyCurrentEditState();
        updateStickerSizeUi();
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

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                pushToUndoStack();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void setupStickerSizeSlider() {
        if (seekStickerSize == null) {
            return;
        }
        seekStickerSize.setMax(Math.round((MAX_STICKER_SCALE - MIN_STICKER_SCALE) * 100f));
        seekStickerSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                float scale = MIN_STICKER_SCALE + (progress / 100f);
                // Update active sticker in the new list
                com.example.expressionphotobooth.domain.model.StickerItem active =
                        currentEditState.getActiveSticker();
                if (active != null) {
                    active.setScale(scale);
                }
                // Also update legacy single-sticker scale for backward compat
                currentEditState.setStickerScale(scale);
                applyCurrentEditState();
                tvStickerSizeValue.setText(getString(R.string.edit_sticker_size_format, Math.round(scale * 100f)));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (currentEditState.getStickerStyle() != EditState.StickerStyle.NONE) {
                    pushToUndoStack();
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        if (chipStickerSizeSmall != null) {
            chipStickerSizeSmall.setOnClickListener(v -> applyStickerSizePreset(STICKER_SCALE_PRESET_S));
        }
        if (chipStickerSizeMedium != null) {
            chipStickerSizeMedium.setOnClickListener(v -> applyStickerSizePreset(STICKER_SCALE_PRESET_M));
        }
        if (chipStickerSizeLarge != null) {
            chipStickerSizeLarge.setOnClickListener(v -> applyStickerSizePreset(STICKER_SCALE_PRESET_L));
        }
        if (btnStickerCenter != null) {
            btnStickerCenter.setOnClickListener(v -> centerStickerInFrame());
        }
        if (btnStickerReset != null) {
            btnStickerReset.setOnClickListener(v -> resetStickerTransform());
        }

        updateStickerSizeUi();
    }

    private void centerStickerInFrame() {
        if (currentEditState == null || currentEditState.getStickerStyle() == EditState.StickerStyle.NONE || originalBitmap == null) {
            return;
        }
        pushToUndoStack();
        centerStickerInFrameInternal();
        applyCurrentEditState();
        updateStickerSizeUi();
    }

    private void centerStickerInFrameInternal() {
        if (currentEditState == null || originalBitmap == null) {
            return;
        }
        RectF cropRect = resolveFrameCropRectForBitmap(originalBitmap.getWidth(), originalBitmap.getHeight());
        currentEditState.setStickerCropX(0.5f);
        currentEditState.setStickerCropY(0.5f);
        float centerX = cropRect.left + (cropRect.width() * 0.5f);
        float centerY = cropRect.top + (cropRect.height() * 0.5f);
        currentEditState.setStickerX(centerX / originalBitmap.getWidth());
        currentEditState.setStickerY(centerY / originalBitmap.getHeight());
    }

    private void resetStickerTransform() {
        if (currentEditState == null || currentEditState.getStickerStyle() == EditState.StickerStyle.NONE) {
            return;
        }
        pushToUndoStack();
        currentEditState.setStickerScale(STICKER_SCALE_PRESET_M);
        centerStickerInFrameInternal();
        applyCurrentEditState();
        updateStickerSizeUi();
    }

    private void applyStickerSizePreset(float presetScale) {
        boolean hasStickers = !currentEditState.getStickerItems().isEmpty();
        if (currentEditState == null || (!hasStickers && currentEditState.getStickerStyle() == EditState.StickerStyle.NONE)) {
            return;
        }
        pushToUndoStack();
        float clamped = Math.max(MIN_STICKER_SCALE, Math.min(presetScale, MAX_STICKER_SCALE));
        // Apply to active sticker in list
        com.example.expressionphotobooth.domain.model.StickerItem active = currentEditState.getActiveSticker();
        if (active != null) active.setScale(clamped);
        // Also update legacy field
        currentEditState.setStickerScale(clamped);
        applyCurrentEditState();
        updateStickerSizeUi();
    }

    private void updateStickerSizeUi() {
        if (stickerSizeRow == null || seekStickerSize == null || tvStickerSizeValue == null || currentEditState == null) {
            return;
        }
        boolean show = !currentEditState.getStickerItems().isEmpty()
                || currentEditState.getStickerStyle() != EditState.StickerStyle.NONE;
        stickerSizeRow.setVisibility(show ? View.VISIBLE : View.GONE);
        if (stickerSizePresetGroup != null) {
            stickerSizePresetGroup.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (stickerQuickActionsRow != null) {
            stickerQuickActionsRow.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        // Read scale from active sticker, or fall back to legacy field
        com.example.expressionphotobooth.domain.model.StickerItem active = currentEditState.getActiveSticker();
        float clamped = active != null
                ? Math.max(MIN_STICKER_SCALE, Math.min(active.getScale(), MAX_STICKER_SCALE))
                : Math.max(MIN_STICKER_SCALE, Math.min(currentEditState.getStickerScale(), MAX_STICKER_SCALE));
        int progress = Math.round((clamped - MIN_STICKER_SCALE) * 100f);
        seekStickerSize.setProgress(progress);
        tvStickerSizeValue.setText(getString(R.string.edit_sticker_size_format, Math.round(clamped * 100f)));
        if (chipStickerSizeSmall != null && chipStickerSizeMedium != null && chipStickerSizeLarge != null) {
            float deltaToSmall = Math.abs(clamped - STICKER_SCALE_PRESET_S);
            float deltaToMedium = Math.abs(clamped - STICKER_SCALE_PRESET_M);
            float deltaToLarge = Math.abs(clamped - STICKER_SCALE_PRESET_L);
            if (deltaToSmall <= deltaToMedium && deltaToSmall <= deltaToLarge) {
                stickerSizePresetGroup.check(chipStickerSizeSmall.getId());
            } else if (deltaToMedium <= deltaToLarge) {
                stickerSizePresetGroup.check(chipStickerSizeMedium.getId());
            } else {
                stickerSizePresetGroup.check(chipStickerSizeLarge.getId());
            }
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.topBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        View btnUndo = findViewById(R.id.btnUndo);
        if (btnUndo != null) {
            btnUndo.setOnClickListener(v -> undo());
        }

        if (btnCompare != null) {
            btnCompare.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        ivEditingPhoto.setImageBitmap(originalBitmap);
                        if (chipActiveEdit != null) chipActiveEdit.setVisibility(View.VISIBLE);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        ivEditingPhoto.setImageBitmap(editedBitmap != null ? editedBitmap : originalBitmap);
                        if (chipActiveEdit != null) chipActiveEdit.setVisibility(View.GONE);
                        break;
                }
                return true;
            });
        }
    }

    private void setupActionButtons() {
        MaterialButton btnFinish = findViewById(R.id.btnFinishEdit);
        btnFinish.setOnClickListener(v -> saveAndFinish());
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupStickerDrag() {
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private long lastScaleRenderMs = 0;

            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                boolean hasStickers = !currentEditState.getStickerItems().isEmpty();
                if (!hasStickers && currentEditState.getStickerStyle() == EditState.StickerStyle.NONE) return false;
                float factor = detector.getScaleFactor();
                // Scale active StickerItem
                com.example.expressionphotobooth.domain.model.StickerItem active =
                        currentEditState.getActiveSticker();
                float currentScale;
                if (active != null) {
                    currentScale = active.getScale();
                    float newScale = Math.max(MIN_STICKER_SCALE, Math.min(currentScale * factor, MAX_STICKER_SCALE));
                    active.setScale(newScale);
                    currentEditState.setStickerScale(newScale); // keep legacy in sync
                } else {
                    currentScale = currentEditState.getStickerScale();
                    float newScale = Math.max(MIN_STICKER_SCALE, Math.min(currentScale * factor, MAX_STICKER_SCALE));
                    currentEditState.setStickerScale(newScale);
                }
                // Throttle re-render to ~30fps during live pinch to reduce lag
                long now = System.currentTimeMillis();
                if (now - lastScaleRenderMs > 33) {
                    lastScaleRenderMs = now;
                    applyCurrentEditState();
                    updateStickerSizeUi();
                }
                return true;
            }

            @Override
            public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                // Always do a final render when the gesture ends so the result is accurate
                applyCurrentEditState();
                updateStickerSizeUi();
            }
        });

        ivEditingPhoto.setOnTouchListener(new View.OnTouchListener() {
            private boolean isDragging = false;
            private float offsetX = 0f;
            private float offsetY = 0f;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean hasStickers = !currentEditState.getStickerItems().isEmpty();
                if (!hasStickers && currentEditState.getStickerStyle() == EditState.StickerStyle.NONE) {
                    return false;
                }

                // Scale always handled
                scaleGestureDetector.onTouchEvent(event);

                if (event.getPointerCount() > 1) {
                    isDragging = false;
                    return true;
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

                com.example.expressionphotobooth.domain.model.StickerItem active = currentEditState.getActiveSticker();
                float sx, sy, cropX, cropY;

                if (active != null) {
                    sx = active.getAbsX();
                    sy = active.getAbsY();
                    cropX = active.getCropX();
                    cropY = active.getCropY();
                } else {
                    sx = currentEditState.getStickerX();
                    sy = currentEditState.getStickerY();
                    cropX = currentEditState.getStickerCropX();
                    cropY = currentEditState.getStickerCropY();
                }

                float bw = imgWidth;
                float bh = imgHeight;

                int minL = Math.min((int) bw, (int) bh);
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
                float distSq = dx * dx + dy * dy;

                float radius = size / 2f;
                float radiusSq = (radius * 1.5f) * (radius * 1.5f);

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (distSq < radiusSq * 3.0f) {
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

                            if (active != null) {
                                active.setAbsX(clampedCenterX / bw);
                                active.setAbsY(clampedCenterY / bh);
                                active.setCropX(normCropX);
                                active.setCropY(normCropY);
                            }
                            // Keep legacy fields in sync
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
            Toast.makeText(this, R.string.edit_nothing_to_undo, Toast.LENGTH_SHORT).show();
        }
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

    private void updateBackground(EditState.BackgroundStyle backgroundStyle) {
        pushToUndoStack();
        backgroundCachedBitmap = null; // Clear cache to show pending state
        currentEditState.setBackgroundStyle(backgroundStyle);
        currentEditState.setCustomBackgroundBase64(null);
        refreshBackground();
    }

    private void refreshBackground() {
        if (originalBitmap == null) return;

        EditState.BackgroundStyle style = currentEditState.getBackgroundStyle();
        Log.d("PortraitProcessor", "Refreshing background style: " + style);
        
        if (style == EditState.BackgroundStyle.NONE) {
            backgroundCachedBitmap = null;
            applyCurrentEditState();
            return;
        }

        // Show loading indicator
        Toast.makeText(this, getString(R.string.edit_applying_portrait), Toast.LENGTH_SHORT).show();

        Bitmap bgToUse = null;
        switch (style) {
            case BLUR:
                // Let PortraitProcessor handle blur by passing null as bg
                break;
            case STUDIO: bgToUse = loadAssetBitmap("bg_studio.png"); break;
            case BEACH:  bgToUse = loadAssetBitmap("bg_beach.png"); break;
            case SPACE:  bgToUse = loadAssetBitmap("bg_space.png"); break;
            case VINTAGE: bgToUse = loadAssetBitmap("bg_vintage.png"); break;
            case CUSTOM:
                if (currentEditState.getCustomBackgroundBase64() != null) {
                    byte[] bytes = android.util.Base64.decode(currentEditState.getCustomBackgroundBase64(), android.util.Base64.DEFAULT);
                    bgToUse = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                }
                break;
        }

        Log.d("PortraitProcessor", "bgToUse is " + (bgToUse == null ? "NULL" : "READY (" + bgToUse.getWidth() + "x" + bgToUse.getHeight() + ")"));

        portraitProcessor.processWithBackground(originalBitmap, bgToUse, result -> {
            runOnUiThread(() -> {
                backgroundCachedBitmap = result;
                applyCurrentEditState();
            });
        }, error -> {
            runOnUiThread(() -> Toast.makeText(this, error, Toast.LENGTH_SHORT).show());
        });
    }

    private Bitmap loadAssetBitmap(String name) {
        try {
            InputStream is = getAssets().open("backgrounds/" + name);
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            if (bmp == null) Log.e("PortraitProcessor", "Failed to decode asset: " + name);
            return bmp;
        } catch (IOException e) {
            Log.e("PortraitProcessor", "Failed to open asset: " + name, e);
            return null;
        }
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

        // If a background was chosen but the async cache isn't ready yet, show the original
        // and wait — refreshBackground() will call us again once the result is ready.
        boolean backgroundPending = (currentEditState.getBackgroundStyle() != EditState.BackgroundStyle.NONE)
                && (backgroundCachedBitmap == null);

        Bitmap sourceForRender;
        boolean skipBg;
        if (backgroundPending) {
            // Background processing is in progress: show original for now, skip re-processing
            sourceForRender = originalBitmap;
            skipBg = true;
        } else {
            sourceForRender = backgroundCachedBitmap != null ? backgroundCachedBitmap : originalBitmap;
            skipBg = backgroundCachedBitmap != null;
        }

        editedBitmap = renderEditedBitmapUseCase.execute(this, sourceForRender, currentEditState, skipBg);
        ivEditingPhoto.setImageBitmap(editedBitmap);
        applyFrameOverlay();
        
        // chipActiveEdit visibility is now handled exclusively by the comparison eye icon touch listener
    }

    private void syncSelectionsToAdapters() {
        filtersAdapter.setSelectedByValue(currentEditState.getFilterStyle());
        framesAdapter.setSelectedByValue(currentEditState.getFrameStyle());
        backgroundsAdapter.setSelectedByValue(currentEditState.getBackgroundStyle());
        updateStickerCategorySelectionFromCurrentState();
        refreshStickerAdapter();
        stickersAdapter.setSelectedByValue(currentEditState.getStickerStyle());
        updateStickerSizeUi();
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
                Bitmap currentEdited = renderEditedBitmapUseCase.execute(this, originalBitmap, currentEditState, false);
                Uri currentEditedUri = saveToInternalCache(currentEdited, "edited_" + System.currentTimeMillis() + ".jpg");

                // Always save the current photo's EditState (including sticker scale, background, etc.)
                sessionState.setPhotoEditState(currentPhotoUri.toString(), currentEditState);
                if (currentEditedUri != null) {
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
                        Bitmap otherEdited = renderEditedBitmapUseCase.execute(this, otherOriginal, otherState, false);
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
                    Toast.makeText(this, getString(R.string.edit_save_error_format, e.getMessage()), Toast.LENGTH_SHORT).show();
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

        Rect primaryHole = selectedFrameResId > 0 ? FrameConfig.getPrimaryHoleForFrame(selectedFrameResId) : null;
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

    private void loadGlobalStickers() {
        AuthRepository authRepository = ((AppContainer) getApplication()).getAuthRepository();
        String uid = authRepository.getCurrentUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(uid != null ? uid : "unknown")
                .get()
                .addOnSuccessListener(userDoc -> {
                    boolean isRewardUnlocked = userDoc.exists() && Boolean.TRUE.equals(userDoc.getBoolean("viralRewardUnlocked"));

                    db.collection("stickers")
                            .whereEqualTo("type", "admin")
                            .get()
                            .addOnSuccessListener(adminSnap -> {
                                List<ThumbItem> temp = new ArrayList<>();
                                temp.add(new ThumbItem(getString(R.string.edit_option_none), 0, R.color.thumb_none, EditState.StickerStyle.NONE));

                                for (QueryDocumentSnapshot doc : adminSnap) {
                                    ThumbItem item = new ThumbItem(doc.getId(), doc.getString("label"), doc.getString("base64"), null, EditState.StickerStyle.CUSTOM);
                                    item.isGlobal = true;
                                    temp.add(item);
                                }

                                if (isRewardUnlocked) {
                                    db.collection("stickers")
                                            .whereEqualTo("type", "reward")
                                            .get()
                                            .addOnSuccessListener(rewardSnap -> {
                                                for (QueryDocumentSnapshot doc : rewardSnap) {
                                                    ThumbItem item = new ThumbItem(doc.getId(), "★ VIP", doc.getString("base64"), null, EditState.StickerStyle.CUSTOM);
                                                    item.isGlobal = true;
                                                    temp.add(item);
                                                }
                                                fetchUserStickersAndRefresh(db, uid, temp);
                                            })
                                            .addOnFailureListener(e -> fetchUserStickersAndRefresh(db, uid, temp));
                                } else {
                                    fetchUserStickersAndRefresh(db, uid, temp);
                                }
                            });
                });
    }

    private void fetchUserStickersAndRefresh(FirebaseFirestore db, String uid, List<ThumbItem> temp) {
        if (uid != null) {
            db.collection("stickers")
                    .whereEqualTo("type", "user")
                    .whereEqualTo("userId", uid)
                    .get()
                    .addOnSuccessListener(userSnap -> {
                        for (QueryDocumentSnapshot doc : userSnap) {
                            ThumbItem item = new ThumbItem(doc.getId(), "Me", doc.getString("base64"), null, EditState.StickerStyle.CUSTOM);
                            item.isRemovable = true;
                            temp.add(item);
                        }
                        globalStickers.clear();
                        globalStickers.addAll(temp);
                        runOnUiThread(this::refreshStickerAdapter);
                    })
                    .addOnFailureListener(e -> {
                        globalStickers.clear();
                        globalStickers.addAll(temp);
                        runOnUiThread(this::refreshStickerAdapter);
                    });
        } else {
            globalStickers.clear();
            globalStickers.addAll(temp);
            runOnUiThread(this::refreshStickerAdapter);
        }
    }

    private void deleteCustomSticker(ThumbItem item) {
        if (item == null || item.id == null) return;
        
        if (item.isGlobal) {
            Toast.makeText(this, R.string.edit_sticker_cannot_delete_global, Toast.LENGTH_SHORT).show();
            return;
        }
        
        FirebaseFirestore.getInstance().collection("stickers")
                .document(item.id)
                .delete()
                .addOnSuccessListener(v -> {
                    loadGlobalStickers();
                    if (currentEditState.getStickerStyle() == EditState.StickerStyle.CUSTOM && 
                        item.id.equals(currentEditState.getCustomStickerId())) {
                        updateSticker(new ThumbItem(getString(R.string.edit_option_none), 0, R.color.thumb_none, EditState.StickerStyle.NONE));
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void openRegisterFromGuest() {
        AuthRepository authRepository = ((AppContainer) getApplication()).getAuthRepository();
        authRepository.signOut();
        Intent intent = new Intent(this, com.example.expressionphotobooth.LoginActivity.class);
        intent.putExtra(com.example.expressionphotobooth.IntentKeys.EXTRA_OPEN_REGISTER, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}











