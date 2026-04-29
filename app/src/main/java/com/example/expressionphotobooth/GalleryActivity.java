package com.example.expressionphotobooth;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ContentValues;
import android.app.ActivityManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.example.expressionphotobooth.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.FrameLayout;

import com.bumptech.glide.Glide;
import com.example.expressionphotobooth.data.security.RBACService;
import com.example.expressionphotobooth.data.security.SecureImageStorageService;
import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.example.expressionphotobooth.domain.model.UserRole;
import com.example.expressionphotobooth.utils.LocaleManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.net.Uri;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import com.example.expressionphotobooth.domain.model.HistorySession;
import com.example.expressionphotobooth.domain.repository.HistoryRepository;

public class GalleryActivity extends AppCompatActivity {

    private AuthRepository authRepository;
    private com.example.expressionphotobooth.domain.repository.HistoryRepository historyRepository;
    private List<File> galleryFiles = new ArrayList<>();
    private boolean isStamp = true;
    
    // Security components
    private RBACService rbacService;
    private SecureImageStorageService secureImageStorageService;
    
    // Multi-Book Scrapbook State
    public static class BookItem {
        public String imagePath;
        public float x, y, scale = 1.0f, rotation = 0f;
    }
    public static class MemoryBook {
        public String id;
        public String name;
        public int backgroundColor = 0xFFFDFCF2; // Default aged paper
        public int borderColor = 0xFFD2B48C;     // Default tan
        public int textColor = 0xFF4A3728;      // Default brown
        public int padding = 24;                 // Default padding
        public int strokeWidth = 6;              // Default stroke width
        public float cornerRadius = 8f;          // Default corner radius
        public String coverStyle = "vintage";
        public List<BookItem> items = new ArrayList<>();
    }

    public static class BookStylePreset {
        public String name;
        public int bgColor;
        public int strokeColor;
        public int textColor;
        public int padding;
        public int strokeWidth;
        public float cornerRadius;

        public BookStylePreset(String name, int bgColor, int strokeColor, int textColor, int padding, int strokeWidth, float cornerRadius) {
            this.name = name;
            this.bgColor = bgColor;
            this.strokeColor = strokeColor;
            this.textColor = textColor;
            this.padding = padding;
            this.strokeWidth = strokeWidth;
            this.cornerRadius = cornerRadius;
        }
    }

    
    private List<MemoryBook> memoryBooks = new ArrayList<>();
    private MemoryBook currentActiveBook = null;
    private SharedPreferences prefs;
    private com.google.gson.Gson gson = new com.google.gson.Gson();

    // Stamp view components
    private View stampView;
    private RecyclerView rvGallery;
    private View emptyState;
    private GalleryAdapter adapter;

    // Book view components
    private View bookView;
    private View tvEmptyBook;

    // Tab buttons
    private View tabStamp, tabBook;
    private ImageView ivTabStamp, ivTabBook;
    private TextView tvTabStamp, tvTabBook;
    private ImageButton btnViewModeToggle;
    private ImageButton btnHeaderAction;
    private View bottomModeSwitcher;
    private TextView tvGalleryDate;

    private boolean isGridView = true;
    private ViewPager2 vpCarousel;
    private CarouselAdapter carouselAdapter;
    private LinearLayout carouselIndicator;
    private View cardCarouselInfo;
    private TextView tvCarouselInfoDate;
    private TextView tvCarouselInfoFrame;
    private static final long CAROUSEL_INFO_AUTO_HIDE_MS = 2500L;
    private final Handler carouselInfoHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideCarouselInfoRunnable = this::hideCarouselInfoPanelImmediate;

    private static final String PREF_BOOK_PHOTOS = "book_pinned_photos";
    private static final String PREF_CAROUSEL_INFO_AUTO_SHOWN = "carousel_info_auto_shown";
    private boolean hasCarouselInfoAutoShown;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        // 1. Khởi tạo Repo an toàn
        AppContainer appContainer = (AppContainer) getApplication();
        authRepository = appContainer.getAuthRepository();
        historyRepository = appContainer.getHistoryRepository();

        if (authRepository != null && authRepository.isGuest()) {
            finish();
            return;
        }
        
        // 2. Initialize security services (RBAC + Secure Storage)
        try {
            secureImageStorageService = new SecureImageStorageService(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        String currentUid = authRepository.getCurrentUid();
        authRepository.fetchCurrentRole(new AuthRepository.RoleCallback() {
            @Override
            public void onSuccess(UserRole role) {
                rbacService = new RBACService(currentUid, role, false);
            }

            @Override
            public void onError(String message) {
                // Fallback to USER role
                rbacService = new RBACService(currentUid, UserRole.USER, false);
            }
        });
        
        // 3. Lấy dữ liệu an toàn
        prefs = getSharedPreferences("ExpressionGallery", MODE_PRIVATE);
        hasCarouselInfoAutoShown = prefs.getBoolean(PREF_CAROUSEL_INFO_AUTO_SHOWN, false);
        loadMemoryBooks();
        
        initViews();
        switchTab(true);
        if (authRepository != null) {
            loadGallery();
        }
    }

    private void returnHomeWhenGuestBlocked() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void openRegisterFromGuest() {
        if (authRepository != null) {
            authRepository.signOut();
        }
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(IntentKeys.EXTRA_OPEN_REGISTER, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }


    private void initViews() {
        ViewGroup container = findViewById(R.id.galleryContainer);
        
        // Inflate views
        stampView = LayoutInflater.from(this).inflate(R.layout.fragment_gallery_stamp, container, false);
        bookView = LayoutInflater.from(this).inflate(R.layout.layout_gallery_book, container, false);
        
        container.addView(stampView);
        container.addView(bookView);

        rvGallery = stampView.findViewById(R.id.rvGallery);
        emptyState = stampView.findViewById(R.id.emptyState);
        rvGallery.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new GalleryAdapter(galleryFiles);
        rvGallery.setAdapter(adapter);

        tvEmptyBook = bookView.findViewById(R.id.tvEmptyBook);

        bookView.findViewById(R.id.btnSaveBook).setOnClickListener(v -> {
            saveMemoryBooks();
            Toast.makeText(this, R.string.gallery_book_saved, Toast.LENGTH_SHORT).show();
        });
        bookView.findViewById(R.id.btnChangeStyle).setOnClickListener(v -> showChooseStyleDialog());


        tabStamp = findViewById(R.id.tabStamp);
        tabBook = findViewById(R.id.tabBook);
        ivTabStamp = findViewById(R.id.ivTabStamp);
        ivTabBook = findViewById(R.id.ivTabBook);
        tvTabStamp = findViewById(R.id.tvTabStamp);
        tvTabBook = findViewById(R.id.tvTabBook);

        btnViewModeToggle = findViewById(R.id.btnViewModeToggle);
        btnViewModeToggle.setOnClickListener(v -> toggleViewMode());

        btnHeaderAction = findViewById(R.id.btnHeaderAction);
        btnHeaderAction.setOnClickListener(v -> handleHeaderAction());
        
        // Setup Book List RecyclerView
        RecyclerView rvBookList = bookView.findViewById(R.id.rvBookList);
        rvBookList.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 2));
        
        vpCarousel = stampView.findViewById(R.id.vpCarousel);
        carouselIndicator = stampView.findViewById(R.id.carouselIndicator);

        vpCarousel.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateCarouselIndicator(position);
                updateCarouselInfo(position);
            }
        });

        cardCarouselInfo = stampView.findViewById(R.id.cardCarouselInfo);
        tvCarouselInfoDate = stampView.findViewById(R.id.tvCarouselInfoDate);
        tvCarouselInfoFrame = stampView.findViewById(R.id.tvCarouselInfoFrame);

/* Nav buttons removed per user request */

        stampView.findViewById(R.id.btnCarouselSavePhoto).setOnClickListener(v -> performCarouselAction("save_photo"));
        stampView.findViewById(R.id.btnCarouselShare).setOnClickListener(v -> performCarouselAction("share"));
        stampView.findViewById(R.id.btnCarouselSaveVideo).setOnClickListener(v -> performCarouselAction("save_video"));
        stampView.findViewById(R.id.btnCarouselViewFeedback).setOnClickListener(v -> performCarouselAction("view_feedback"));
        stampView.findViewById(R.id.btnCarouselDelete).setOnClickListener(v -> performCarouselAction("delete"));

        stampView.findViewById(R.id.btnStaticSavePhoto).setOnClickListener(v -> performCarouselAction("save_photo"));
        stampView.findViewById(R.id.btnStaticShare).setOnClickListener(v -> performCarouselAction("share"));
        stampView.findViewById(R.id.btnStaticSaveVideo).setOnClickListener(v -> performCarouselAction("save_video"));
        stampView.findViewById(R.id.btnStaticViewFeedback).setOnClickListener(v -> performCarouselAction("view_feedback"));

        View btnHideInfo = stampView.findViewById(R.id.btnHideInfo);
        if (btnHideInfo != null) {
            btnHideInfo.setOnClickListener(v -> hideCarouselInfoPanelImmediate());
        }

        // Removed btnPrev/btnNext handlers as requested


        findViewById(R.id.btnBack).setOnClickListener(v -> {
            if (!isStamp && currentActiveBook != null) {
                renderBookList();
            } else {
                finish();
            }
        });
        
        // Current Date for Title
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d MMM", Locale.getDefault());
        tvGalleryDate = findViewById(R.id.tvGalleryDate);
        bottomModeSwitcher = findViewById(R.id.bottomModeSwitcher);
        if (tvGalleryDate != null) {
            tvGalleryDate.setText(dateFormat.format(new Date()));
        }

        setupTabs();
    }

    private void setupTabs() {
        tabStamp.setOnClickListener(v -> switchTab(true));
        tabBook.setOnClickListener(v -> switchTab(false));
    }

    private void switchTab(boolean isStamp) {
        this.isStamp = isStamp;
        stampView.setVisibility(isStamp ? View.VISIBLE : View.GONE);
        bookView.setVisibility(isStamp ? View.GONE : View.VISIBLE);

        ivTabStamp.setColorFilter(isStamp ? 0xFF333333 : 0xFF999999);
        tvTabStamp.setTextColor(isStamp ? 0xFF333333 : 0xFF999999);
        tabStamp.setBackgroundResource(isStamp ? R.drawable.bg_tab_selected : 0);

        ivTabBook.setColorFilter(!isStamp ? 0xFF333333 : 0xFF999999);
        tvTabBook.setTextColor(!isStamp ? 0xFF333333 : 0xFF999999);
        tabBook.setBackgroundResource(!isStamp ? R.drawable.bg_tab_selected : 0);

        if (!isStamp) {
            renderBookList();
            btnViewModeToggle.setVisibility(View.GONE);
            if (btnHeaderAction != null) btnHeaderAction.setVisibility(View.VISIBLE);
        } else {
            btnViewModeToggle.setVisibility(View.VISIBLE);
            if (btnHeaderAction != null) btnHeaderAction.setVisibility(View.GONE);
            updateViewModeUi();
        }
    }

    private void handleHeaderAction() {
        if (!isStamp) {
            if (currentActiveBook == null) {
                showCreateBookDialog();
            } else {
                showAddPhotoToBookDialog();
            }
        }
    }

    private void showCreateBookDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.gallery_book_name_hint);
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.gallery_book_create_title)
            .setView(input)
            .setPositiveButton(R.string.gallery_book_create_action, (d, w) -> {
                String name = input.getText().toString();
                if (!name.isEmpty()) {
                    MemoryBook book = new MemoryBook();
                    book.id = String.valueOf(System.currentTimeMillis());
                    book.name = name;
                    memoryBooks.add(book);
                    saveMemoryBooks();
                    openBook(book);
                }
            })
            .setNegativeButton(R.string.history_popup_cancel, null)
            .show();
    }

    private void showAddPhotoToBookDialog() {
        if (galleryFiles.isEmpty()) return;
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.layout_dialog_add_photo, null);
        RecyclerView rv = dialogView.findViewById(R.id.rvDialogPhotos);
        rv.setLayoutManager(new GridLayoutManager(this, 3));
        
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.gallery_book_add_photo_title, currentActiveBook.name))
            .setView(dialogView)
            .setNegativeButton(R.string.history_popup_cancel, null)
            .create();

        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
                return new RecyclerView.ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_gallery_stamp, p, false)) {};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int p) {
                File file = galleryFiles.get(p);
                ImageView iv = h.itemView.findViewById(R.id.ivGalleryPhoto);
                Glide.with(iv).load(file).centerCrop().into(iv);
                TextView tv = h.itemView.findViewById(R.id.tvDate);
                if (tv != null) tv.setVisibility(View.GONE);
                
                h.itemView.setOnClickListener(v -> {
                    BookItem item = new BookItem();
                    item.imagePath = file.getAbsolutePath();
                    // Set default scale/pos to center
                    item.x = 200;
                    item.y = 200;
                    currentActiveBook.items.add(item);
                    saveMemoryBooks();
                    renderActiveBook();
                    dialog.dismiss();
                });
            }
            @Override public int getItemCount() { return galleryFiles.size(); }
        });
        
        dialog.show();
    }

    private void toggleViewMode() {
        if (galleryFiles.isEmpty()) {
            Toast.makeText(this, R.string.gallery_no_photo_to_show, Toast.LENGTH_SHORT).show();
            // Optional: ensure we stay in grid view if empty
            if (!isGridView) {
                isGridView = true;
                updateViewModeUi();
            }
            return;
        }
        isGridView = !isGridView;
        updateViewModeUi();
    }

    private void updateViewModeUi() {
        if (stampView == null || btnViewModeToggle == null) return;
        
        try {
            View gridContainer = stampView.findViewById(R.id.gridViewContainer);
            View carouselContainer = stampView.findViewById(R.id.carouselViewContainer);
            View carouselActions = cardCarouselInfo;

            if (gridContainer != null) gridContainer.setVisibility(isGridView ? View.VISIBLE : View.GONE);
            if (carouselContainer != null) carouselContainer.setVisibility(isGridView ? View.GONE : View.VISIBLE);
            
            // Toggle Logic: Switch Icons
            btnViewModeToggle.setImageResource(isGridView ? R.drawable.ic_view_carousel : R.drawable.ic_grid_view_24);
            
            if (!isGridView) {
                if (galleryFiles.isEmpty()) {
                    if (carouselActions != null) carouselActions.setVisibility(View.GONE);
                    if (carouselIndicator != null) carouselIndicator.setVisibility(View.GONE);
                    if (bottomModeSwitcher != null) bottomModeSwitcher.setVisibility(View.VISIBLE);
                    if (tvGalleryDate != null) tvGalleryDate.setVisibility(View.VISIBLE);
                } else {
                    if (carouselActions != null) carouselActions.setVisibility(View.VISIBLE);
                    if (carouselIndicator != null) carouselIndicator.setVisibility(View.VISIBLE);
                    if (bottomModeSwitcher != null) bottomModeSwitcher.setVisibility(View.GONE);
                    if (tvGalleryDate != null) tvGalleryDate.setVisibility(View.GONE);
                    hideCarouselInfoPanelImmediate();
                    ensureCarouselAdapter();
                    vpCarousel.post(() -> {
                        setupCarousel();
                        if (!hasCarouselInfoAutoShown && !galleryFiles.isEmpty()) {
                            // Delay a bit to show the slide up effect as a hint
                            vpCarousel.postDelayed(() -> {
                                if (!isGridView) showCarouselInfoPanelTemporarily();
                            }, 500L);
                            hasCarouselInfoAutoShown = true;
                            prefs.edit().putBoolean(PREF_CAROUSEL_INFO_AUTO_SHOWN, true).apply();
                        }
                    });
                }
            } else {
                if (bottomModeSwitcher != null) bottomModeSwitcher.setVisibility(View.VISIBLE);
                if (tvGalleryDate != null) tvGalleryDate.setVisibility(View.VISIBLE);
                carouselInfoHandler.removeCallbacks(hideCarouselInfoRunnable);
                if (adapter != null) adapter.notifyDataSetChanged();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveCurrentImageToGallery(File file) {
        if (file == null || !file.exists()) return;
        try {
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap == null) return;

            String name = "photobooth_" + System.currentTimeMillis() + ".png";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Photobooth");

            Uri outputUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (outputUri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(outputUri)) {
                    if (out != null) {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                        Toast.makeText(this, R.string.saved_to_gallery_short, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.failed_save_image, Toast.LENGTH_SHORT).show();
        }
    }

    private void performCarouselAction(String action) {
        if (galleryFiles.isEmpty()) return;

        if (authRepository != null && authRepository.isGuest() && !"save_photo".equals(action)) {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            return;
        }

        File currentFile = galleryFiles.get(vpCarousel.getCurrentItem());
        
        String uid = authRepository.getCurrentUid();
        com.example.expressionphotobooth.domain.model.HistorySession session = findSessionForFile(currentFile, uid);

        if (currentFile == null) return;
        switch (action) {
            case "save_photo":
                saveCurrentImageToGallery(currentFile);
                break;
            case "share":
                shareFile(currentFile);
                break;
            case "save_video":
                if (session != null) {
                    long limit24h = 24 * 60 * 60 * 1000L;
                    if (System.currentTimeMillis() - session.getCapturedAt() > limit24h) {
                        Toast.makeText(this, R.string.gallery_video_expired_24h, Toast.LENGTH_LONG).show();
                        break;
                    }
                    if (session.getVideoUri() != null && !session.getVideoUri().trim().isEmpty()) {
                        saveVideoToGallery(session.getVideoUri());
                    } else {
                        Toast.makeText(this, R.string.history_no_video_to_save, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, R.string.history_no_video_to_save, Toast.LENGTH_SHORT).show();
                }
                break;
            case "view_feedback":
                if (session != null) {
                    showFeedbackDialog(session);
                } else {
                    Toast.makeText(this, R.string.gallery_no_feedback_data, Toast.LENGTH_SHORT).show();
                }
                break;
            case "delete":
                int pos = vpCarousel.getCurrentItem();
                deleteFile(currentFile, pos, true);
                break;
        }
    }

    private com.example.expressionphotobooth.domain.model.HistorySession findSessionForFile(File currentFile, String uid) {
        if (historyRepository == null || uid == null || currentFile == null) {
            return null;
        }
        List<com.example.expressionphotobooth.domain.model.HistorySession> sessions = historyRepository.getSessions(uid);
        if (sessions == null || sessions.isEmpty()) {
            return null;
        }

        // 1st: Try to extract sessionId from filename (100% accurate matching).
        // Expected format: pose_<sessionId>_<timestamp>.png
        String sessionIdFromFilename = extractSessionIdFromFilename(currentFile.getName());
        if (sessionIdFromFilename != null && !sessionIdFromFilename.isEmpty()) {
            for (com.example.expressionphotobooth.domain.model.HistorySession s : sessions) {
                if (sessionIdFromFilename.equals(s.getId())) {
                    // RBAC Check: Verify user can access this session
                    if (rbacService != null && !rbacService.canAccessSession(s.getUserId())) {
                        return null; // Access denied
                    }
                    return s;
                }
            }
        }

        // 2nd: Try exact filename match with result image URI.
        for (com.example.expressionphotobooth.domain.model.HistorySession s : sessions) {
            String resultUri = s.getResultImageUri();
            if (resultUri == null || resultUri.trim().isEmpty()) {
                continue;
            }
            try {
                Uri uri = Uri.parse(resultUri);
                String lastPath = uri.getLastPathSegment();
                if (lastPath != null && lastPath.equals(currentFile.getName())) {
                    // RBAC Check: Verify user can access this session
                    if (rbacService != null && !rbacService.canAccessSession(s.getUserId())) {
                        return null; // Access denied
                    }
                    return s;
                }
                if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
                    File sessionFile = new File(uri.getPath());
                    if (sessionFile.getName().equals(currentFile.getName())) {
                        // RBAC Check: Verify user can access this session
                        if (rbacService != null && !rbacService.canAccessSession(s.getUserId())) {
                            return null; // Access denied
                        }
                        return s;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 3rd (Fallback): nearest capture timestamp (local gallery file is saved right after result).
        com.example.expressionphotobooth.domain.model.HistorySession closest = null;
        long bestDiff = Long.MAX_VALUE;
        long fileTime = currentFile.lastModified();
        for (com.example.expressionphotobooth.domain.model.HistorySession s : sessions) {
            long diff = Math.abs(fileTime - s.getCapturedAt());
            if (diff < bestDiff) {
                bestDiff = diff;
                closest = s;
            }
        }

        // Accept nearest only when reasonably close (2 minutes).
        return bestDiff <= 120_000L ? closest : null;
    }

    /**
     * Extract sessionId from filename.
     * Expected format: pose_<sessionId>_<timestamp>.png
     * Example: pose_abc123def456_1712234567890.png -> abc123def456
     */
    private String extractSessionIdFromFilename(String filename) {
        if (filename == null || !filename.startsWith("pose_")) {
            return null;
        }
        
        // Remove extension
        String nameWithoutExt = filename;
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExt = filename.substring(0, dotIndex);
        }
        
        // Remove prefix
        String withoutPrefix = nameWithoutExt.startsWith("pose_") ? nameWithoutExt.substring(5) : nameWithoutExt;
        
        // Split by underscore to find parts: <sessionId>_<timestamp>
        String[] parts = withoutPrefix.split("_");
        if (parts.length >= 2) {
            // The first part should be sessionId, last part should be timestamp
            // Check if last part is numeric (timestamp)
            String lastPart = parts[parts.length - 1];
            if (lastPart.matches("\\d+")) {
                // Reconstruct sessionId (handle IDs with underscores)
                return String.join("_", java.util.Arrays.copyOfRange(parts, 0, parts.length - 1));
            }
        }
        
        return null;
    }

    private void saveVideoToGallery(String sourceUriString) {
        try {
            Uri sourceUri = Uri.parse(sourceUriString);
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, "memory_video_" + System.currentTimeMillis() + ".mp4");
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Photobooth");
            Uri outputUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            if (outputUri == null) {
                Toast.makeText(this, R.string.failed_save_video, Toast.LENGTH_SHORT).show();
                return;
            }

            try (InputStream in = getContentResolver().openInputStream(sourceUri);
                 OutputStream out = getContentResolver().openOutputStream(outputUri)) {
                if (in == null || out == null) {
                    Toast.makeText(this, R.string.failed_save_video, Toast.LENGTH_SHORT).show();
                    return;
                }
                byte[] buffer = new byte[8 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            Toast.makeText(this, R.string.video_saved_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.failed_save_video, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFile(File file) {
        Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.gallery_share_memory_chooser)));
    }

    private void showFeedbackDialog(com.example.expressionphotobooth.domain.model.HistorySession session) {
        String ratingText = session.getRating() >= 0
                ? String.format(Locale.getDefault(), "%.1f", session.getRating())
                : getString(R.string.history_not_available);
        String feedbackText = (session.getFeedback() == null || session.getFeedback().trim().isEmpty())
                ? getString(R.string.history_not_available)
                : session.getFeedback();
        String message = getString(R.string.history_feedback_format, ratingText, feedbackText);

        HelpDialogUtils.showHistoryStyledNotice(
                this,
                R.drawable.ic_info_24,
                getString(R.string.history_feedback_title),
                message,
                getString(R.string.common_ok),
                null
        );
    }

    private void setupCarousel() {
        ensureCarouselAdapter();
        if (carouselAdapter != null) {
            // Keep one primary page fully visible to avoid side clipping/overlap.
            vpCarousel.setClipToPadding(true);
            vpCarousel.setClipChildren(true);
            boolean constrained = isCarouselPerformanceConstrainedDevice();
            vpCarousel.setOffscreenPageLimit(constrained ? 1 : 2);

            // No side offset so adjacent pages are not visible.
            vpCarousel.setPadding(0, 0, 0, 0);

            if (constrained) {
                // On weak devices, disable custom transform for best frame stability.
                vpCarousel.setPageTransformer(null);
            } else {
                // Very light transform keeps focus without causing visible clipping.
                vpCarousel.setPageTransformer((page, position) -> {
                    float abs = Math.abs(position);
                    page.setScaleY(1f - (0.04f * abs));
                    page.setAlpha(1f - (0.12f * abs));
                });
            }
        }
        if (carouselAdapter != null) {
            carouselAdapter.notifyDataSetChanged();
        }
        updateCarouselIndicator(vpCarousel.getCurrentItem());
        updateCarouselInfo(vpCarousel.getCurrentItem());
    }

    private boolean isCarouselPerformanceConstrainedDevice() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        return am != null && am.isLowRamDevice();
    }

    private void ensureCarouselAdapter() {
        if (vpCarousel == null) {
            return;
        }
        if (carouselAdapter == null) {
            carouselAdapter = new CarouselAdapter(galleryFiles);
            vpCarousel.setAdapter(carouselAdapter);
        } else if (vpCarousel.getAdapter() == null) {
            vpCarousel.setAdapter(carouselAdapter);
        }
    }

    private void moveCarouselBy(int delta) {
        if (vpCarousel == null || galleryFiles.isEmpty()) {
            return;
        }
        int current = vpCarousel.getCurrentItem();
        int target = Math.max(0, Math.min(galleryFiles.size() - 1, current + delta));
        // Removed automatic info show per user request
        if (target != current) {
            vpCarousel.setCurrentItem(target, true);
        }
    }

    private void showCarouselInfoPanelToggle() {
        if (cardCarouselInfo == null || isGridView) {
            return;
        }
        
        if (cardCarouselInfo.getVisibility() == View.VISIBLE && cardCarouselInfo.getAlpha() > 0.5f) {
            hideCarouselInfoPanelImmediate();
            return;
        }

        cardCarouselInfo.setVisibility(View.VISIBLE);
        cardCarouselInfo.animate().cancel();
        // Slide up from bottom
        if (cardCarouselInfo.getTranslationY() == 0f || cardCarouselInfo.getTranslationY() < 100f) {
            cardCarouselInfo.setTranslationY(cardCarouselInfo.getHeight() > 0 ? cardCarouselInfo.getHeight() : 1000f);
        }
        cardCarouselInfo.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(400L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }
    
    private void showCarouselInfoPanelTemporarily() {
        showCarouselInfoPanelToggle();
    }

    private void hideCarouselInfoPanelImmediate() {
        if (cardCarouselInfo == null) {
            return;
        }
        cardCarouselInfo.animate().cancel();
        cardCarouselInfo.animate()
                .translationY(cardCarouselInfo.getHeight() > 0 ? cardCarouselInfo.getHeight() : 1000f)
                .alpha(0f)
                .setDuration(350L)
                .withEndAction(() -> cardCarouselInfo.setVisibility(View.GONE))
                .start();
    }

    private void updateCarouselIndicator(int position) {
        if (carouselIndicator == null) return;
        carouselIndicator.removeAllViews();
        int count = galleryFiles.size();
        if (count == 0) return;
        
        try {
            int dotSize = getResources().getDimensionPixelSize(R.dimen.gallery_carousel_dot_size);
            int dotGap = getResources().getDimensionPixelSize(R.dimen.gallery_carousel_dot_gap);
            for (int i = 0; i < Math.min(count, 10); i++) {
                View dot = new View(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dotSize, dotSize);
                params.setMargins(dotGap, 0, dotGap, 0);
                dot.setLayoutParams(params);
                dot.setBackgroundResource(i == position ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
                carouselIndicator.addView(dot);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateCarouselInfo(int position) {
        if (tvCarouselInfoDate == null || tvCarouselInfoFrame == null) {
            return;
        }
        if (galleryFiles.isEmpty() || position < 0 || position >= galleryFiles.size()) {
            tvCarouselInfoDate.setText(getString(R.string.history_date_value, getString(R.string.history_not_available)));
            tvCarouselInfoFrame.setText(getString(R.string.history_frame_value, getString(R.string.history_not_available)));
            return;
        }

        File file = galleryFiles.get(position);
        String dateText = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(file.lastModified()));
        String frameText = getString(R.string.history_not_available);

        String uid = authRepository != null ? authRepository.getCurrentUid() : null;
        HistorySession session = findSessionForFile(file, uid);
        if (session != null && session.getFrameName() != null && !session.getFrameName().trim().isEmpty()) {
            frameText = session.getFrameName().trim();
        }

        tvCarouselInfoDate.setText(getString(R.string.history_date_value, dateText));
        tvCarouselInfoFrame.setText(getString(R.string.history_frame_value, frameText));
    }

    private void loadGallery() {
        String uid = authRepository.getCurrentUid();
        if (uid == null) {
            finish();
            return;
        }

        File galleryDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "user_gallery/" + uid);
        galleryFiles.clear();

        if (galleryDir.exists() && galleryDir.isDirectory()) {
            // Include both .png and .mp4
            File[] files = galleryDir.listFiles((dir, name) -> name.endsWith(".png") || name.endsWith(".mp4"));
            if (files != null) {
                long now = System.currentTimeMillis();
                long limit24h = 24 * 60 * 60 * 1000L;
                
                for (File f : files) {
                    // Cleanup Video: if it's a video and older than 24h, delete it.
                    if (f.getName().endsWith(".mp4")) {
                        if (now - f.lastModified() > limit24h) {
                            f.delete();
                        }
                        continue; // Don't add to list
                    }
                    galleryFiles.add(f);
                }
                Collections.sort(galleryFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            }
        }


        if (galleryFiles.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvGallery.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            rvGallery.setVisibility(View.VISIBLE);
            setupRecyclerView();
        }
    }

    private void setupRecyclerView() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void loadMemoryBooks() {
        String uid = authRepository != null ? authRepository.getCurrentUid() : null;
        String key = uid != null ? "memory_books_" + uid : "memory_books";
        String json = prefs.getString(key, "[]");
        java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<MemoryBook>>(){}.getType();
        memoryBooks = gson.fromJson(json, type);
        if (memoryBooks == null) memoryBooks = new ArrayList<>();
    }

    private void saveMemoryBooks() {
        String uid = authRepository != null ? authRepository.getCurrentUid() : null;
        String key = uid != null ? "memory_books_" + uid : "memory_books";
        String json = gson.toJson(memoryBooks);
        prefs.edit().putString(key, json).apply();
    }

    private void renderBookList() {
        currentActiveBook = null;
        bookView.findViewById(R.id.bookListContainer).setVisibility(View.VISIBLE);
        bookView.findViewById(R.id.bookEditorContainer).setVisibility(View.GONE);
        bookView.findViewById(R.id.tvEmptyBook).setVisibility(memoryBooks.isEmpty() ? View.VISIBLE : View.GONE);
        
        RecyclerView rv = bookView.findViewById(R.id.rvBookList);
        rv.setAdapter(new BookListAdapter(memoryBooks));
    }

    private void openBook(MemoryBook book) {
        currentActiveBook = book;
        renderActiveBook();
    }

    private void showChooseStyleDialog() {
        if (currentActiveBook == null) return;
        
        List<BookStylePreset> presets = new ArrayList<>();
        presets.add(new BookStylePreset("Vintage", 0xFFFDFCF2, 0xFFD2B48C, 0xFF4A3728, 24, 6, 8f));
        presets.add(new BookStylePreset("Modern", 0xFFFFFFFF, 0xFFE0E0E0, 0xFF121212, 32, 2, 0f));
        presets.add(new BookStylePreset("Night", 0xFF141E30, 0xFF34495E, 0xFFFFFFFF, 16, 4, 16f));
        presets.add(new BookStylePreset("Royal", 0xFF800000, 0xFFFFD700, 0xFFFFD700, 40, 12, 24f));
        presets.add(new BookStylePreset("Sakura", 0xFFFFF0F5, 0xFFFFB7C5, 0xFFB44D5E, 24, 6, 20f));
        presets.add(new BookStylePreset("Forest", 0xFFF0FFF0, 0xFF2E8B57, 0xFF006400, 24, 8, 12f));
        presets.add(new BookStylePreset("Ocean", 0xFFF0F8FF, 0xFF4682B4, 0xFF003366, 24, 6, 30f));
        presets.add(new BookStylePreset("Sunset", 0xFFFFF5E6, 0xFFFF8C00, 0xFFB22222, 24, 10, 10f));
        presets.add(new BookStylePreset("Clean", 0xFFF9F9F9, 0xFF333333, 0xFF333333, 0, 0, 0f));

        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_dialog_book_style, null);
        RecyclerView rv = view.findViewById(R.id.rvStyles);
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        
        view.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());
        
        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {


            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
                return new RecyclerView.ViewHolder(getLayoutInflater().inflate(R.layout.item_book_style_preset, p, false)) {};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int p) {
                BookStylePreset s = presets.get(p);
                h.itemView.findViewById(R.id.viewColor).setBackgroundColor(s.bgColor);
                ((TextView)h.itemView.findViewById(R.id.tvStyleName)).setText(s.name);
                ((com.google.android.material.card.MaterialCardView)h.itemView.findViewById(R.id.cardColor)).setStrokeColor(s.strokeColor);
                
                h.itemView.setOnClickListener(v -> {
                    currentActiveBook.backgroundColor = s.bgColor;
                    currentActiveBook.borderColor = s.strokeColor;
                    currentActiveBook.textColor = s.textColor;
                    currentActiveBook.padding = s.padding;
                    currentActiveBook.strokeWidth = s.strokeWidth;
                    currentActiveBook.cornerRadius = s.cornerRadius;
                    currentActiveBook.coverStyle = s.name.toLowerCase();
                    renderActiveBook();
                    // Removed dialog.dismiss() to allow live preview as requested
                });

            }
            @Override public int getItemCount() { return presets.size(); }
        });
        
        dialog.setContentView(view);
        dialog.show();
    }


    private void renderActiveBook() {
        bookView.findViewById(R.id.bookListContainer).setVisibility(View.GONE);
        bookView.findViewById(R.id.bookEditorContainer).setVisibility(View.VISIBLE);
        
        TextView tvName = bookView.findViewById(R.id.tvBookName);
        tvName.setText(currentActiveBook.name);
        
        FrameLayout surface = bookView.findViewById(R.id.bookSurface);
        surface.removeAllViews();
        
        // Apply Style (Colors & Frame/Stroke)
        tvName.setTextColor(currentActiveBook.textColor);
        surface.setPadding(
            (int)(currentActiveBook.padding * getResources().getDisplayMetrics().density),
            (int)(currentActiveBook.padding * getResources().getDisplayMetrics().density),
            (int)(currentActiveBook.padding * getResources().getDisplayMetrics().density),
            (int)(currentActiveBook.padding * getResources().getDisplayMetrics().density)
        );

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(currentActiveBook.backgroundColor);
        gd.setStroke((int)(currentActiveBook.strokeWidth * getResources().getDisplayMetrics().density), currentActiveBook.borderColor);
        gd.setCornerRadius(currentActiveBook.cornerRadius * getResources().getDisplayMetrics().density);
        surface.setBackground(gd);

        
        for (BookItem item : currentActiveBook.items) {

            ImageView iv = new ImageView(this);
            iv.setLayoutParams(new FrameLayout.LayoutParams(400, 500));
            Glide.with(this).load(item.imagePath).into(iv);
            
            iv.setTranslationX(item.x);
            iv.setTranslationY(item.y);
            iv.setScaleX(item.scale);
            iv.setScaleY(item.scale);
            iv.setRotation(item.rotation);
            
            setupItemTouch(iv, item);
            surface.addView(iv);
        }
    }

    private void setupItemTouch(View v, BookItem item) {
        v.setOnTouchListener(new View.OnTouchListener() {
            private float lastX, lastY;
            private float lastRotationAngle;
            private ScaleGestureDetector scaleDetector = new ScaleGestureDetector(v.getContext(), 
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        item.scale *= detector.getScaleFactor();
                        item.scale = Math.max(0.3f, Math.min(item.scale, 5.0f));
                        v.setScaleX(item.scale);
                        v.setScaleY(item.scale);
                        return true;
                    }
                });

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                scaleDetector.onTouchEvent(event);
                
                if (event.getPointerCount() == 2) {
                    float dx = event.getX(1) - event.getX(0);
                    float dy = event.getY(1) - event.getY(0);
                    float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                    
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_POINTER_DOWN:
                            lastRotationAngle = angle;
                            break;
                        case MotionEvent.ACTION_MOVE:
                            float dAngle = angle - lastRotationAngle;
                            item.rotation += dAngle;
                            v.setRotation(item.rotation);
                            lastRotationAngle = angle;
                            break;
                    }
                }

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = event.getRawX();
                        lastY = event.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (event.getPointerCount() == 1) {
                            float dx = event.getRawX() - lastX;
                            float dy = event.getRawY() - lastY;
                            item.x += dx;
                            item.y += dy;
                            v.setTranslationX(item.x);
                            v.setTranslationY(item.y);
                            lastX = event.getRawX();
                            lastY = event.getRawY();
                        }
                        break;
                }
                return true;
            }
        });
    }

    // Adapters
    class BookListAdapter extends RecyclerView.Adapter<BookListAdapter.ViewHolder> {
        private List<MemoryBook> books;
        BookListAdapter(List<MemoryBook> books) { this.books = books; }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_gallery_book, p, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder h, int p) {
            MemoryBook b = books.get(p);
            h.title.setText(b.name);
            h.count.setText(getString(R.string.gallery_book_photo_count, b.items.size()));
            
            // Set cover color and text color based on saved colors
            ((com.google.android.material.card.MaterialCardView)h.itemView).setCardBackgroundColor(b.backgroundColor);
            ((com.google.android.material.card.MaterialCardView)h.itemView).setStrokeColor(b.borderColor);
            h.title.setTextColor(b.textColor);
            h.count.setTextColor(b.textColor);
            h.count.setAlpha(0.6f);



            if (!b.items.isEmpty()) {
                Glide.with(h.cover).load(b.items.get(0).imagePath).centerCrop().into(h.cover);
            } else {
                h.cover.setImageDrawable(null);
            }

            h.itemView.setOnClickListener(v -> openBook(b));
            h.btnDownload.setOnClickListener(v -> exportBookAsPng(b));
            h.itemView.setOnLongClickListener(v -> {
                int currentPos = h.getBindingAdapterPosition();
                if (currentPos == RecyclerView.NO_POSITION) return true;
                HelpDialogUtils.showHistoryStyledConfirm(
                    GalleryActivity.this,
                    R.drawable.ic_help_24,
                    getString(R.string.gallery_book_delete_title),
                    getString(R.string.gallery_book_delete_message, b.name),
                    getString(R.string.history_delete),
                    getString(R.string.history_popup_cancel),
                    () -> {
                        books.remove(currentPos);
                        saveMemoryBooks();
                        renderBookList();
                    },
                    null
                );
                return true;
            });
        }
        @Override public int getItemCount() { return books.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, count;
            ImageView cover, btnDownload;
            ViewHolder(View v) {
                super(v);
                title = v.findViewById(R.id.tvBookTitle);
                count = v.findViewById(R.id.tvPhotoCount);
                cover = v.findViewById(R.id.ivBookCover);
                btnDownload = v.findViewById(R.id.btnDownloadBook);
            }
        }
    }

    private class CarouselAdapter extends RecyclerView.Adapter<CarouselAdapter.ViewHolder> {
        private final List<File> files;

        CarouselAdapter(List<File> files) {
            this.files = files;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_carousel_photo, parent, false);
            // ViewPager2 REQUIRES match_parent for its items.
            view.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            File file = files.get(position);
            Glide.with(holder.itemView.getContext())
                .load(file)
                .fitCenter()
                .placeholder(R.drawable.bg_stamp_border)
                .into(holder.ivPhoto);
            if (holder.tvDate != null) holder.tvDate.setVisibility(View.GONE);
            View.OnClickListener showInfoClick = v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    updateCarouselInfo(pos);
                }
                showCarouselInfoPanelTemporarily();
            };
            holder.ivPhoto.setOnClickListener(showInfoClick);
            holder.itemView.setOnClickListener(showInfoClick);
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivPhoto;
            TextView tvDate;
            ImageButton btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                ivPhoto = itemView.findViewById(R.id.ivGalleryPhoto);
                tvDate = itemView.findViewById(R.id.tvDate);
                btnDelete = itemView.findViewById(R.id.btnDeletePhoto);
            }
        }
    }

    private class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.ViewHolder> {
        private final List<File> files;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        GalleryAdapter(List<File> files) {
            this.files = files;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_gallery_stamp, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            File file = files.get(position);
            boolean isVideo = file.getName().endsWith(".mp4");
            
            Glide.with(holder.ivPhoto)
                .load(file)
                .centerCrop()
                .into(holder.ivPhoto);
                
            // If it's a video, show a play overlay or hint
            View playIcon = holder.itemView.findViewById(R.id.ivPlayIcon);
            if (playIcon != null) {
                playIcon.setVisibility(isVideo ? View.VISIBLE : View.GONE);
            }
            
            holder.tvDate.setText(dateFormat.format(new Date(file.lastModified())));

            // Show X button
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();

                if (pos != RecyclerView.NO_POSITION) {
                    deleteFile(galleryFiles.get(pos), pos, false);
                }
            });
            
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(GalleryActivity.this, FullImageActivity.class);
                intent.putExtra("IMAGE_PATH", file.getAbsolutePath());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivPhoto;
            TextView tvDate;
            ImageButton btnDelete;
            ViewHolder(View itemView) {
                super(itemView);
                ivPhoto = itemView.findViewById(R.id.ivGalleryPhoto);
                tvDate = itemView.findViewById(R.id.tvDate);
                btnDelete = itemView.findViewById(R.id.btnDeletePhoto);
            }
        }
    }

    private void deleteFile(File file, int position, boolean isCarousel) {
        if (authRepository != null && authRepository.isGuest()) {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            return;
        }

        // RBAC Check: Verify user can delete this file
        String uid = authRepository.getCurrentUid();
        if (rbacService != null && !rbacService.canDeleteImage(uid)) {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            return;
        }

        HelpDialogUtils.showHistoryStyledConfirm(
            this,
            R.drawable.ic_help_24,
            getString(R.string.gallery_delete_title),
            getString(R.string.gallery_delete_message),
            getString(R.string.history_delete),
            getString(R.string.history_popup_cancel),
            () -> {
                boolean deleted = file.delete();
                
                // Try to delete from secure storage if applicable
                if (secureImageStorageService != null && deleted) {
                    try {
                        secureImageStorageService.deleteSecureImage(file.getAbsolutePath());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                
                if (deleted) {
                    galleryFiles.remove(position);
                    if (isCarousel) {
                        if (carouselAdapter != null) carouselAdapter.notifyItemRemoved(position);
                        updateCarouselIndicator(Math.min(position, galleryFiles.size() - 1));
                    } else {
                        if (adapter != null) adapter.notifyItemRemoved(position);
                    }
                    if (galleryFiles.isEmpty()) {
                        emptyState.setVisibility(View.VISIBLE);
                        rvGallery.setVisibility(View.GONE);
                        
                        // Switch back to grid view state smoothly
                        isGridView = true;
                        updateViewModeUi();
                    }
                    Toast.makeText(this, R.string.gallery_delete_success, Toast.LENGTH_SHORT).show();
                }
            },
            null
        );
    }

    private void exportBookAsPng(MemoryBook book) {
        if (book == null) return;
        Toast.makeText(this, R.string.gallery_book_exporting, Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            try {
                // Resolution for export (1080x1920)
                int width = 1080;
                int height = 1920;
                android.graphics.Bitmap result = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(result);
                
                // 1. Draw Background
                android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                paint.setColor(book.backgroundColor);
                canvas.drawRect(0, 0, width, height, paint);
                
                // 2. Draw Book Spine Effect
                paint.setColor(0x2E000000);
                canvas.drawRect(0, 0, 60, height, paint);
                paint.setColor(0x4D000000);
                canvas.drawRect(50, 0, 56, height, paint);
                
                // 3. Draw Photos/Items
                for (BookItem item : book.items) {
                    android.graphics.Bitmap itemBmp = android.graphics.BitmapFactory.decodeFile(item.imagePath);
                    if (itemBmp != null) {
                        canvas.save();
                        // Coordinates in saved books might need mapping to 1080p width
                        // Simple proportional mapping
                        canvas.translate(item.x, item.y);
                        canvas.scale(item.scale, item.scale);
                        canvas.rotate(item.rotation);
                        
                        // Draw with fixed editor size (400x500 reference)
                        canvas.drawBitmap(itemBmp, null, new android.graphics.Rect(0, 0, 400, 500), null);
                        canvas.restore();
                        itemBmp.recycle();
                    }
                }
                
                // 4. Draw Title Header
                paint.setColor(book.textColor);
                paint.setTextSize(64f);
                paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD));
                paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                canvas.drawText(book.name, width/2f + 30, 200, paint);

                // 5. Save logic
                String fileName = "Album_" + book.name.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis() + ".png";
                saveBitmapToSystemGallery(result, fileName);
                
                result.recycle();
                runOnUiThread(() -> Toast.makeText(this, R.string.gallery_book_export_success, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void saveBitmapToSystemGallery(android.graphics.Bitmap bitmap, String fileName) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OurMemories_Albums");

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri != null) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

