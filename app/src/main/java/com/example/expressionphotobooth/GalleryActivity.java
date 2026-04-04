package com.example.expressionphotobooth;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ContentValues;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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
        public List<BookItem> items = new ArrayList<>();
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

    private boolean isGridView = true;
    private ViewPager2 vpCarousel;
    private CarouselAdapter carouselAdapter;
    private LinearLayout carouselIndicator;

    private static final String PREF_BOOK_PHOTOS = "book_pinned_photos";

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
            String languageTag = LocaleManager.getCurrentLanguage(this);
            HelpDialogUtils.showHistoryGuestRegisterCta(
                    this,
                    LocaleManager.getString(this, R.string.home_gallery_user_only_title, languageTag),
                    LocaleManager.getString(this, R.string.home_gallery_user_only_message, languageTag),
                    this::openRegisterFromGuest,
                    this::returnHomeWhenGuestBlocked
            );
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
        
        tvEmptyBook = bookView.findViewById(R.id.tvEmptyBook);

        bookView.findViewById(R.id.btnSaveBook).setOnClickListener(v -> {
            saveMemoryBooks();
            Toast.makeText(this, "Book saved successfully!", Toast.LENGTH_SHORT).show();
        });

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
            }
        });

        stampView.findViewById(R.id.btnCarouselSavePhoto).setOnClickListener(v -> performCarouselAction("save_photo"));
        stampView.findViewById(R.id.btnCarouselShare).setOnClickListener(v -> performCarouselAction("share"));
        stampView.findViewById(R.id.btnCarouselSaveVideo).setOnClickListener(v -> performCarouselAction("save_video"));
        stampView.findViewById(R.id.btnCarouselViewFeedback).setOnClickListener(v -> performCarouselAction("view_feedback"));
        stampView.findViewById(R.id.btnCarouselDelete).setOnClickListener(v -> performCarouselAction("delete"));

        stampView.findViewById(R.id.btnPrev).setOnClickListener(v -> {
            if (vpCarousel.getCurrentItem() > 0) vpCarousel.setCurrentItem(vpCarousel.getCurrentItem() - 1);
        });
        stampView.findViewById(R.id.btnNext).setOnClickListener(v -> {
            if (carouselAdapter != null && vpCarousel.getCurrentItem() < carouselAdapter.getItemCount() - 1) {
                vpCarousel.setCurrentItem(vpCarousel.getCurrentItem() + 1);
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            if (!isStamp && currentActiveBook != null) {
                renderBookList();
            } else {
                finish();
            }
        });
        
        // Current Date for Title
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d MMM", Locale.getDefault());
        ((TextView) findViewById(R.id.tvGalleryDate)).setText(dateFormat.format(new Date()));
        
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
        input.setHint("Book Name");
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("New Memory Book")
            .setView(input)
            .setPositiveButton("Create", (d, w) -> {
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
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showAddPhotoToBookDialog() {
        if (galleryFiles.isEmpty()) return;
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.layout_dialog_add_photo, null);
        RecyclerView rv = dialogView.findViewById(R.id.rvDialogPhotos);
        rv.setLayoutManager(new GridLayoutManager(this, 3));
        
        AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add Photo to " + currentActiveBook.name)
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
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
        isGridView = !isGridView;
        updateViewModeUi();
    }

    private void updateViewModeUi() {
        if (stampView == null || btnViewModeToggle == null) return;
        
        try {
            View gridContainer = stampView.findViewById(R.id.gridViewContainer);
            View carouselContainer = stampView.findViewById(R.id.carouselViewContainer);
            
            if (gridContainer != null) gridContainer.setVisibility(isGridView ? View.VISIBLE : View.GONE);
            if (carouselContainer != null) carouselContainer.setVisibility(isGridView ? View.GONE : View.VISIBLE);
            
            // Toggle Logic: Switch Icons
            btnViewModeToggle.setImageResource(isGridView ? R.drawable.ic_view_carousel : R.drawable.ic_grid_view_24);
            
            if (!isGridView) {
                vpCarousel.post(this::setupCarousel);
            } else {
                if (adapter != null) adapter.notifyDataSetChanged();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void performCarouselAction(String action) {
        if (galleryFiles.isEmpty()) return;
        File currentFile = galleryFiles.get(vpCarousel.getCurrentItem());
        
        String uid = authRepository.getCurrentUid();
        com.example.expressionphotobooth.domain.model.HistorySession session = findSessionForFile(currentFile, uid);

        if (currentFile == null) return;
        switch (action) {
            case "save_photo":
                Intent full = new Intent(this, FullImageActivity.class);
                full.putExtra("IMAGE_PATH", currentFile.getAbsolutePath());
                startActivity(full);
                break;
            case "share":
                shareFile(currentFile);
                break;
            case "save_video":
                if (session != null && session.getVideoUri() != null && !session.getVideoUri().trim().isEmpty()) {
                    saveVideoToGallery(session.getVideoUri());
                } else {
                    Toast.makeText(this, R.string.history_no_video_to_save, Toast.LENGTH_SHORT).show();
                }
                break;
            case "view_feedback":
                if (session != null) {
                    showFeedbackDialog(session);
                } else {
                    Toast.makeText(this, "No feedback data", Toast.LENGTH_SHORT).show();
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
        
        // Remove extension and prefix
        String nameWithoutExt = filename.endsWith(".png") ? filename.substring(0, filename.length() - 4) : filename;
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
        startActivity(Intent.createChooser(intent, "Share Memory"));
    }

    private void showFeedbackDialog(com.example.expressionphotobooth.domain.model.HistorySession session) {
        new AlertDialog.Builder(this)
            .setTitle("History Feedback")
            .setMessage("Rating: " + session.getRating() + " ⭐\nFeedback: " + session.getFeedback())
            .setPositiveButton("OK", null)
            .show();
    }

    private void setupCarousel() {
        if (carouselAdapter == null) {
            carouselAdapter = new CarouselAdapter(galleryFiles);
            vpCarousel.setAdapter(carouselAdapter);
            
            vpCarousel.setClipToPadding(false);
            vpCarousel.setClipChildren(false);
            vpCarousel.setOffscreenPageLimit(3);
            
            int pageMarginPx = (int) (12 * getResources().getDisplayMetrics().density);
            int offsetPx = (int) (30 * getResources().getDisplayMetrics().density);
            vpCarousel.setPadding(offsetPx, 0, offsetPx, 0);
            
            androidx.viewpager2.widget.CompositePageTransformer transformer = new androidx.viewpager2.widget.CompositePageTransformer();
            transformer.addTransformer(new androidx.viewpager2.widget.MarginPageTransformer(pageMarginPx));
            transformer.addTransformer((page, position) -> {
                float r = 1 - Math.abs(position);
                page.setScaleY(0.85f + r * 0.15f);
                page.setAlpha(0.6f + r * 0.4f);
            });
            vpCarousel.setPageTransformer(transformer);
        } else {
            carouselAdapter.notifyDataSetChanged();
        }
        updateCarouselIndicator(vpCarousel.getCurrentItem());
    }

    private void updateCarouselIndicator(int position) {
        if (carouselIndicator == null) return;
        carouselIndicator.removeAllViews();
        int count = galleryFiles.size();
        if (count == 0) return;
        
        try {
            for (int i = 0; i < Math.min(count, 10); i++) {
                View dot = new View(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(16, 16);
                params.setMargins(8, 0, 8, 0);
                dot.setLayoutParams(params);
                dot.setBackgroundResource(i == position ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
                carouselIndicator.addView(dot);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
            File[] files = galleryDir.listFiles((dir, name) -> name.endsWith(".png"));
            if (files != null) {
                Collections.addAll(galleryFiles, files);
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
        if (adapter == null) {
            adapter = new GalleryAdapter(galleryFiles);
            rvGallery.setLayoutManager(new GridLayoutManager(this, 2));
            rvGallery.setAdapter(adapter);
        } else {
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

    private void renderActiveBook() {
        bookView.findViewById(R.id.bookListContainer).setVisibility(View.GONE);
        bookView.findViewById(R.id.bookEditorContainer).setVisibility(View.VISIBLE);
        
        TextView tvName = bookView.findViewById(R.id.tvBookName);
        tvName.setText(currentActiveBook.name);
        
        FrameLayout surface = bookView.findViewById(R.id.bookSurface);
        surface.removeAllViews();
        
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
            h.count.setText(b.items.size() + " photos");
            
            String[] colors = {"#5E81AC", "#BF616A", "#D08770", "#EBCB8B", "#A3BE8C", "#B48EAD"};
            int color = android.graphics.Color.parseColor(colors[Math.abs(b.name.hashCode()) % colors.length]);
            ((com.google.android.material.card.MaterialCardView)h.itemView).setCardBackgroundColor(color);

            if (!b.items.isEmpty()) {
                Glide.with(h.cover).load(b.items.get(0).imagePath).centerCrop().into(h.cover);
            } else {
                h.cover.setImageDrawable(null);
            }

            h.itemView.setOnClickListener(v -> openBook(b));
            h.itemView.setOnLongClickListener(v -> {
                int currentPos = h.getBindingAdapterPosition();
                if (currentPos == RecyclerView.NO_POSITION) return true;
                new androidx.appcompat.app.AlertDialog.Builder(v.getContext())
                    .setMessage("Delete '" + b.name + "'?")
                    .setPositiveButton("Delete", (d, w) -> {
                        books.remove(currentPos);
                        saveMemoryBooks();
                        renderBookList();
                    }).setNegativeButton("Cancel", null).show();
                return true;
            });
        }
        @Override public int getItemCount() { return books.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, count;
            ImageView cover;
            ViewHolder(View v) { 
                super(v); 
                title = v.findViewById(R.id.tvBookTitle); 
                count = v.findViewById(R.id.tvPhotoCount); 
                cover = v.findViewById(R.id.ivBookCover);
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
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_gallery_stamp, parent, false);
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
            holder.tvDate.setVisibility(View.GONE);
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
            Glide.with(holder.ivPhoto).load(file).centerCrop().into(holder.ivPhoto);
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
        // RBAC Check: Verify user can delete this file
        String uid = authRepository.getCurrentUid();
        if (rbacService != null && !rbacService.canDeleteImage(uid)) {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Delete Photo")
            .setMessage("Delete this photo permanently?")
            .setPositiveButton("Delete", (dialog, which) -> {
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
                        if (vpCarousel != null) vpCarousel.setVisibility(View.GONE);
                    }
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
