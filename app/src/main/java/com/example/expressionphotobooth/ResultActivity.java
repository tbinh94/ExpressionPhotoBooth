package com.example.expressionphotobooth;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import com.example.expressionphotobooth.data.security.RBACService;
import com.example.expressionphotobooth.data.security.SecureImageStorageService;
import com.example.expressionphotobooth.data.video.TimelapseVideoEncoder;
import com.example.expressionphotobooth.data.graphics.BitmapEditRenderer;
import com.example.expressionphotobooth.domain.model.DownloadType;
import com.example.expressionphotobooth.domain.model.EditState;
import com.example.expressionphotobooth.domain.model.HistorySession;
import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.model.UserRole;
import com.example.expressionphotobooth.domain.repository.AdminStatsRepository;
import com.example.expressionphotobooth.domain.repository.HistoryRepository;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.example.expressionphotobooth.domain.usecase.CreateTimelapseVideoUseCase;
import com.example.expressionphotobooth.domain.usecase.CreateVerticalCollageUseCase;
import com.example.expressionphotobooth.domain.usecase.PortraitProcessor;
import com.example.expressionphotobooth.domain.usecase.RenderEditedBitmapUseCase;
import androidx.annotation.Nullable;
import com.example.expressionphotobooth.utils.FrameConfig;
import com.example.expressionphotobooth.utils.StickerPlacementMapper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import com.example.expressionphotobooth.utils.ViralRewardManager;

public class ResultActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(com.example.expressionphotobooth.utils.LocaleManager.wrapContext(newBase));
    }

    private Uri resultUri;
    private SessionRepository sessionRepository;
    private SessionState sessionState;
    private CreateTimelapseVideoUseCase createTimelapseVideoUseCase;
    private CreateVerticalCollageUseCase createVerticalCollageUseCase;
    private RenderEditedBitmapUseCase renderEditedBitmapUseCase;
    private PortraitProcessor portraitProcessor;
    private MaterialButton btnSaveVideo;
    private List<String> sourceOriginalUris;
    private boolean hasShownFeedback = false;
    private ToneGenerator toneGenerator;
    private AdminStatsRepository adminStatsRepository;
    private AuthRepository authRepository;
    private HistoryRepository historyRepository;
    private String currentUid;
    private String historySessionId;
    private boolean isGuestSession;
    private boolean hasShownGuestHistoryNotice;
    
    // Security components
    private SecureImageStorageService secureImageStorageService;
    private RBACService rbacService;
    private UserRole currentUserRole;
    private ViralRewardManager viralRewardManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();
        adminStatsRepository = ((AppContainer) getApplication()).getAdminStatsRepository();
        authRepository = ((AppContainer) getApplication()).getAuthRepository();
        historyRepository = ((AppContainer) getApplication()).getHistoryRepository();
        currentUid = authRepository.getCurrentUid();
        isGuestSession = authRepository.isGuest();
        sessionState = sessionRepository.getSession();
        
        if (sessionState == null) {
            finish();
            return;
        }

        // Initialize security services
        try {
            secureImageStorageService = new SecureImageStorageService(this);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to initialize secure storage", Toast.LENGTH_SHORT).show();
        }
        
        // Initialize RBAC service with user info
        initializeRBAC();
        viralRewardManager = new ViralRewardManager(this);

        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        createTimelapseVideoUseCase = new CreateTimelapseVideoUseCase(new TimelapseVideoEncoder());
        createVerticalCollageUseCase = new CreateVerticalCollageUseCase();
        renderEditedBitmapUseCase = new RenderEditedBitmapUseCase(new BitmapEditRenderer());
        portraitProcessor = new PortraitProcessor();
        renderEditedBitmapUseCase.setPortraitProcessor(portraitProcessor);
        sourceOriginalUris = resolveSourceOriginalUris();

        if (sourceOriginalUris.isEmpty()) {
            Toast.makeText(this, R.string.no_photo_to_continue, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ImageView ivFinalResult = findViewById(R.id.ivFinalResult);
        View loadingOverlay = findViewById(R.id.loadingOverlay);

        // Chạy ngầm để xử lý ảnh nặng + Hiện loading tránh người dùng sốt ruột
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);

        int selectedFrameResId  = sessionState != null ? sessionState.getSelectedFrameResId() : -1;
        String remoteLayout     = sessionState != null ? sessionState.getSelectedFrameLayout() : null;
        String firestoreFrameId = sessionState != null ? sessionState.getSelectedFirestoreFrameId() : null;
        String remoteBase64Mem  = sessionState != null ? sessionState.getSelectedFrameBase64() : null;

        if (selectedFrameResId > 0) {
            // Local drawable frame
            startRenderThread(loadingOverlay, ivFinalResult, null, null);
        } else if (remoteBase64Mem != null && remoteLayout != null) {
            // Remote frame already in memory
            startRenderThread(loadingOverlay, ivFinalResult, remoteBase64Mem, remoteLayout);
        } else if (firestoreFrameId != null && remoteLayout != null) {
            // Session restored from SharedPrefs — re-fetch base64 from Firestore
            final String layout = remoteLayout;
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("frames").document(firestoreFrameId)
                    .get()
                    .addOnCompleteListener(task -> {
                        String fetched = null;
                        if (task.isSuccessful() && task.getResult() != null) {
                            fetched = task.getResult().getString("base64");
                        }
                        if (fetched != null && sessionState != null) {
                            sessionState.setSelectedFrameBase64(fetched);
                        }
                        startRenderThread(loadingOverlay, ivFinalResult, fetched, layout);
                    });
        } else {
            // No frame selected
            startRenderThread(loadingOverlay, ivFinalResult, null, null);
        }

        findViewById(R.id.btnSavePng).setOnClickListener(v -> saveCurrentResultAsPng());
        btnSaveVideo = findViewById(R.id.btnSaveVideo);
        btnSaveVideo.setOnClickListener(v -> exportTimelapseVideo());

        findViewById(R.id.btnShare).setOnClickListener(v -> shareResult());

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                // Return to edit screen
                getOnBackPressedDispatcher().onBackPressed();
            });
        }

        View btnHome = findViewById(R.id.btnHome);
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                // Clear session and return home
                sessionRepository.clearSession();
                Intent intent = new Intent(ResultActivity.this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }

        View cardRate = findViewById(R.id.cardRate);
        if (cardRate != null) {
            cardRate.setOnClickListener(v -> {
                if (isGuestSession) {
                    HelpDialogUtils.showHistoryGuestRegisterCta(
                            this,
                            getString(R.string.home_history_user_only_title),
                            getString(R.string.home_history_user_only_message),
                            this::openRegisterFromGuest,
                            null
                    );
                } else {
                    showFeedbackBottomSheet(false);
                }
            });
            // Ensure child views don't block clicks from reachng the card
            cardRate.setFocusable(true);
            cardRate.setClickable(true);
        }

    }

    private void startRenderThread(View loadingOverlay, ImageView ivFinalResult,
                                    String remoteBase64, String remoteLayout) {
        int localFrameResId = sessionState != null ? sessionState.getSelectedFrameResId() : -1;
        new Thread(() -> {
            List<String> imageUrisToCollage = new ArrayList<>();
            Map<String, String> editedImageUris = sessionState != null ? sessionState.getEditedImageUris() : null;
            for (String originalUri : sourceOriginalUris) {
                String editedUri = (editedImageUris != null) ? editedImageUris.get(originalUri) : null;
                imageUrisToCollage.add(editedUri != null ? editedUri : originalUri);
            }

            Bitmap finalBitmap;
            if (localFrameResId > 0) {
                finalBitmap = createFramedCollage(new ArrayList<>(imageUrisToCollage), localFrameResId);
            } else if (remoteBase64 != null && remoteLayout != null) {
                finalBitmap = createRemoteFramedCollage(new ArrayList<>(imageUrisToCollage), remoteBase64, remoteLayout);
            } else {
                finalBitmap = createVerticalCollageUseCase.execute(ResultActivity.this, imageUrisToCollage);
            }

            runOnUiThread(() -> {
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                if (finalBitmap != null) {
                    resultUri = saveBitmapToCache(finalBitmap);
                    ivFinalResult.setImageBitmap(finalBitmap);
                    if (sessionState != null) {
                        sessionState.setResultImageUri(resultUri != null ? resultUri.toString() : null);
                        sessionRepository.saveSession(sessionState);
                    }
                    persistHistoryBaseRecord();
                    if (!isGuestSession) {
                        saveToUserLocalGallery(finalBitmap);
                    }
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!isFinishing() && !isGuestSession) showFeedbackBottomSheet(true);
                    }, 1500);
                } else {
                    Toast.makeText(ResultActivity.this, R.string.no_result_to_show, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private Bitmap createRemoteFramedCollage(List<String> photoUris, String frameBase64, String layoutType) {
        try {
            byte[] frameBytes = android.util.Base64.decode(frameBase64, android.util.Base64.DEFAULT);
            Bitmap frameBitmap = BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.length);
            if (frameBitmap == null) return null;

            // 1. Flood-fill: xóa nền trắng VÀ tự phát hiện vị trí các ô trống thực tế
            java.util.List<Rect> detectedHoles = new java.util.ArrayList<>();
            frameBitmap = applyFloodFillWhiteRemoval(frameBitmap, layoutType, detectedHoles);

            final float EXPORT_SCALE_FACTOR = 4.0f;
            int targetWidth  = Math.round(frameBitmap.getWidth()  * EXPORT_SCALE_FACTOR);
            int targetHeight = Math.round(frameBitmap.getHeight() * EXPORT_SCALE_FACTOR);

            Bitmap resultBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(resultBitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

            // Sử dụng vị trí lỗ tự phát hiện; fallback về hardcoded nếu không tìm được
            java.util.List<Rect> holes = detectedHoles.isEmpty()
                    ? FrameConfig.getHolesForLayoutScaled(layoutType, frameBitmap.getWidth(), frameBitmap.getHeight())
                    : detectedHoles;

            android.util.Log.d("RemoteFrame", "Frame size: " + frameBitmap.getWidth() + "x" + frameBitmap.getHeight()
                    + " | Detected holes: " + detectedHoles.size() + " | Used holes: " + holes.size()
                    + " | Layout: " + layoutType);
            for (int hi = 0; hi < holes.size(); hi++) {
                Rect h = holes.get(hi);
                android.util.Log.d("RemoteFrame", "  Hole[" + hi + "]: " + h.left + "," + h.top + " -> " + h.right + "," + h.bottom
                        + " (w=" + h.width() + " h=" + h.height() + ")");
            }

            // 2. Vẽ ảnh chụp vào đúng vị trí lỗ trống (SCALED)
            for (int i = 0; i < Math.min(photoUris.size(), holes.size()); i++) {
                String originalUri = photoUris.get(i);
                Bitmap originalPhoto = decodeBitmap(Uri.parse(originalUri));
                if (originalPhoto == null) continue;

                EditState photoState = sessionState != null
                        ? sessionState.getPhotoEditState(originalUri).copy()
                        : new EditState();

                Bitmap photoNoSticker = renderPhotoWithoutSticker(originalPhoto, photoState);
                originalPhoto.recycle();
                if (photoNoSticker == null) continue;

                Rect hole = holes.get(i);
                // Scale hole to export resolution
                RectF scaledHoleF = new RectF(
                        hole.left   * EXPORT_SCALE_FACTOR,
                        hole.top    * EXPORT_SCALE_FACTOR,
                        hole.right  * EXPORT_SCALE_FACTOR,
                        hole.bottom * EXPORT_SCALE_FACTOR
                );

                int photoW = photoNoSticker.getWidth();
                int photoH = photoNoSticker.getHeight();
                float holeW = scaledHoleF.width();
                float holeH = scaledHoleF.height();

                // CENTER CROP using Matrix (equivalent to ImageView.ScaleType.CENTER_CROP)
                // Scale uniformly so the image covers the hole entirely, then center
                float scaleX = holeW / photoW;
                float scaleY = holeH / photoH;
                float scale  = Math.max(scaleX, scaleY);  // take the LARGER scale → fills hole

                float scaledW = photoW * scale;
                float scaledH = photoH * scale;
                // Translate so the center of the scaled photo aligns with the center of the hole
                float dx = scaledHoleF.left + (holeW - scaledW) / 2f;
                float dy = scaledHoleF.top  + (holeH - scaledH) / 2f;

                android.util.Log.d("RemoteFrame", "  Photo[" + i + "]: " + photoW + "x" + photoH
                        + " | hole: " + (int)holeW + "x" + (int)holeH
                        + " | scale=" + scale + " dx=" + dx + " dy=" + dy);

                Matrix matrix = new Matrix();
                matrix.setScale(scale, scale);
                matrix.postTranslate(dx, dy);

                // Clip to hole bounds to prevent photo from bleeding into adjacent slots
                canvas.save();
                canvas.clipRect(scaledHoleF);
                canvas.drawBitmap(photoNoSticker, matrix, paint);
                canvas.restore();

                // Sticker: reuse srcRectF for placement mapping
                RectF srcRectF = new RectF(0, 0, photoW, photoH);
                Rect scaledHoleRect = new Rect(Math.round(scaledHoleF.left), Math.round(scaledHoleF.top),
                        Math.round(scaledHoleF.right), Math.round(scaledHoleF.bottom));
                drawStickerForHole(canvas, scaledHoleRect, srcRectF, photoW, photoH, photoState);
                photoNoSticker.recycle();
            }

            // Debug overlay removed as positions are now verified


            // 3. Vẽ frame (đã trong suốt ở vị trí lỗ) chồng lên ảnh
            Bitmap scaledFrame = Bitmap.createScaledBitmap(frameBitmap, targetWidth, targetHeight, true);
            canvas.drawBitmap(scaledFrame, 0, 0, paint);
            scaledFrame.recycle();
            frameBitmap.recycle();
            return resultBitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Tách biệt logic xử lý cho hai loại layout:
     * 16x9_3 (Frame giấy xé) -> Dùng Full Scan để nhận diện hình dạng phức tạp.
     * Các loại khác -> Dùng Seed BFS để ổn định vị trí.
     */
    private Bitmap applyFloodFillWhiteRemoval(Bitmap src, String layoutType,
                                               java.util.List<Rect> outHoles) {
        if ("16x9_3".equals(layoutType)) {
            return applyFullScanHoleDetection(src, layoutType, outHoles);
        } else {
            return applySeedBasedHoleDetection(src, layoutType, outHoles);
        }
    }

    private Bitmap applyFullScanHoleDetection(Bitmap src, String layoutType, java.util.List<Rect> outHoles) {
        int w = src.getWidth();
        int h = src.getHeight();

        final int MAX_DIM = 600;
        float dsScale = 1f;
        Bitmap workSrc = src;
        if (w > MAX_DIM || h > MAX_DIM) {
            dsScale = Math.min((float) MAX_DIM / w, (float) MAX_DIM / h);
            int dsW = Math.max(1, Math.round(w * dsScale));
            int dsH = Math.max(1, Math.round(h * dsScale));
            workSrc = Bitmap.createScaledBitmap(src, dsW, dsH, true);
        }
        int dw = workSrc.getWidth();
        int dh = workSrc.getHeight();
        int[] dsPixels = new int[dw * dh];
        workSrc.getPixels(dsPixels, 0, dw, 0, 0, dw, dh);
        boolean[] dsVisited = new boolean[dw * dh];
        if (workSrc != src) workSrc.recycle();

        int minHoleArea = Math.max(30, (dw * dh) / 200);
        java.util.List<int[]> dsRegions = new java.util.ArrayList<>();

        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                int idx = y * dw + x;
                if (dsVisited[idx]) continue;
                int p = dsPixels[idx];
                int r = (p >> 16) & 0xff;
                int g = (p >>  8) & 0xff;
                int b = p & 0xff;
                int a = (p >> 24) & 0xff;
                dsVisited[idx] = true;
                if (a < 128 || r < 180 || g < 180 || b < 180) continue;

                java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
                q.add(idx);
                int minX = x, maxX = x, minY = y, maxY = y, area = 0;
                while (!q.isEmpty()) {
                    int curr = q.poll();
                    area++;
                    int cx = curr % dw, cy = curr / dw;
                    if (cx < minX) minX = cx; if (cx > maxX) maxX = cx;
                    if (cy < minY) minY = cy; if (cy > maxY) maxY = cy;
                    int[] ns = {curr - 1, curr + 1, curr - dw, curr + dw};
                    for (int n : ns) {
                        if (n < 0 || n >= dw * dh || dsVisited[n]) continue;
                        if (Math.abs(n % dw - cx) > 1) continue;
                        int np = dsPixels[n];
                        int na = (np >> 24) & 0xff;
                        int nr = (np >> 16) & 0xff;
                        int ng = (np >>  8) & 0xff;
                        int nb = np & 0xff;
                        if (na >= 128 && nr > 180 && ng > 180 && nb > 180) {
                            dsVisited[n] = true;
                            q.add(n);
                        }
                    }
                }
                if (area >= minHoleArea) {
                    dsRegions.add(new int[]{minX, minY, maxX, maxY, area});
                }
            }
        }

        int expectedCount = FrameConfig.getSlotCountForLayout(layoutType);
        dsRegions.sort((a, b) -> Integer.compare(b[4], a[4]));
        while (dsRegions.size() > expectedCount) dsRegions.remove(dsRegions.size() - 1);
        dsRegions.sort((a, b) -> a[1] != b[1] ? a[1] - b[1] : a[0] - b[0]);

        if (dsRegions.isEmpty()) return src;

        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        int[] origPixels = new int[w * h];
        out.getPixels(origPixels, 0, w, 0, 0, w, h);
        boolean[] origVisited = new boolean[w * h];

        for (int[] reg : dsRegions) {
            int ox1 = Math.max(0, Math.round(reg[0] / dsScale));
            int oy1 = Math.max(0, Math.round(reg[1] / dsScale));
            int ox2 = Math.min(w - 1, Math.round(reg[2] / dsScale));
            int oy2 = Math.min(h - 1, Math.round(reg[3] / dsScale));
            
            int seedX = (ox1 + ox2) / 2, seedY = (oy1 + oy2) / 2;
            int seedIdx = -1;
            int maxRadius = Math.max(ox2 - ox1, oy2 - oy1) / 2;
            outer: for (int r = 0; r <= maxRadius; r++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (Math.abs(dx) != r && Math.abs(dy) != r) continue;
                        int sx = seedX + dx, sy = seedY + dy;
                        if (sx < ox1 || sx > ox2 || sy < oy1 || sy > oy2) continue;
                        int si = sy * w + sx;
                        if (origVisited[si]) continue;
                        int sp = origPixels[si];
                        if (((sp >> 24) & 0xff) >= 128 && ((sp >> 16) & 0xff) > 180 && ((sp >> 8) & 0xff) > 180 && (sp & 0xff) > 180) {
                            seedIdx = si; break outer;
                        }
                    }
                }
            }

            if (seedIdx < 0) {
                outHoles.add(new Rect(ox1, oy1, ox2, oy2));
                continue;
            }

            java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
            q.add(seedIdx);
            origVisited[seedIdx] = true;
            int minX = seedX, maxX = seedX, minY = seedY, maxY = seedY;
            int margin = Math.max(5, Math.min(ox2 - ox1, oy2 - oy1) / 10);
            while (!q.isEmpty()) {
                int curr = q.poll();
                origPixels[curr] = 0x00000000;
                int cx = curr % w, cy = curr / w;
                if (cx < minX) minX = cx; if (cx > maxX) maxX = cx;
                if (cy < minY) minY = cy; if (cy > maxY) maxY = cy;
                int[] ns = {curr - 1, curr + 1, curr - w, curr + w};
                for (int n : ns) {
                    if (n < 0 || n >= w * h || origVisited[n]) continue;
                    int nx = n % w, ny = n / w;
                    if (Math.abs(nx - cx) > 1) continue;
                    if (nx < ox1 - margin || nx > ox2 + margin) continue;
                    if (ny < oy1 - margin || ny > oy2 + margin) continue;
                    int np = origPixels[n];
                    if (((np >> 24) & 0xff) >= 128 && ((np >> 16) & 0xff) > 180 && ((np >> 8) & 0xff) > 180 && (np & 0xff) > 180) {
                        origVisited[n] = true; q.add(n);
                    }
                }
            }
            outHoles.add(new Rect(minX, minY, maxX, maxY));
        }
        out.setPixels(origPixels, 0, w, 0, 0, w, h);
        return out;
    }

    private Bitmap applySeedBasedHoleDetection(Bitmap src, String layoutType, java.util.List<Rect> outHoles) {
        int w = src.getWidth();
        int h = src.getHeight();
        java.util.List<Rect> seedHoles = FrameConfig.getHolesForLayoutScaled(layoutType, w, h);
        if (seedHoles.isEmpty()) return src;

        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        int[] pixels = new int[w * h];
        out.getPixels(pixels, 0, w, 0, 0, w, h);
        boolean[] visited = new boolean[w * h];

        for (Rect rect : seedHoles) {
            int cx = rect.centerX(), cy = rect.centerY();
            int sx1 = Math.max(0, cx - rect.width()/4), sx2 = Math.min(w-1, cx + rect.width()/4);
            int sy1 = Math.max(0, cy - rect.height()/4), sy2 = Math.min(h-1, cy + rect.height()/4);

            int minX = w, maxX = 0, minY = h, maxY = 0;
            boolean found = false;

            for (int sy = sy1; sy <= sy2; sy++) {
                for (int sx = sx1; sx <= sx2; sx++) {
                    int idx = sy * w + sx;
                    if (visited[idx]) continue;
                    int p = pixels[idx];
                    if (((p >> 24) & 0xff) < 128 || ((p >> 16) & 0xff) < 180 || ((p >> 8) & 0xff) < 180 || (p & 0xff) < 180) continue;

                    java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
                    q.add(idx); visited[idx] = true; found = true;
                    while (!q.isEmpty()) {
                        int curr = q.poll();
                        pixels[curr] = 0x00000000;
                        int curX = curr % w, curY = curr / w;
                        if (curX < minX) minX = curX; if (curX > maxX) maxX = curX;
                        if (curY < minY) minY = curY; if (curY > maxY) maxY = curY;
                        int[] ns = {curr - 1, curr + 1, curr - w, curr + w};
                        for (int n : ns) {
                            if (n < 0 || n >= w*h || visited[n]) continue;
                            if (Math.abs(n % w - curX) > 1) continue;
                            int np = pixels[n];
                            if (((np >> 24) & 0xff) >= 128 && ((np >> 16) & 0xff) > 180 && ((np >> 8) & 0xff) > 180 && (np & 0xff) > 180) {
                                visited[n] = true; q.add(n);
                            }
                        }
                    }
                }
            }
            if (found) outHoles.add(new Rect(minX, minY, maxX, maxY));
            else outHoles.add(new Rect(rect.left, rect.top, rect.right, rect.bottom)); // fallback to seed rect
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h);
        return out;
    }

    private Bitmap createFramedCollage(List<String> photoUris, int frameResId) {
        // 1. Tăng độ phân giải: Sử dụng Scale Factor để ảnh xuất ra cực nét
        // Thay vì dùng kích thước gốc của file Frame (thường nhỏ), ta nhân lên 4 lần.
        final float EXPORT_SCALE_FACTOR = 4.0f;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false; // Lấy pixel gốc của Frame
        Bitmap frameBitmap = BitmapFactory.decodeResource(getResources(), frameResId, options);
        if (frameBitmap == null) return null;

        int targetWidth = Math.round(frameBitmap.getWidth() * EXPORT_SCALE_FACTOR);
        int targetHeight = Math.round(frameBitmap.getHeight() * EXPORT_SCALE_FACTOR);

        // 2. Tạo Bitmap kết quả với độ phân giải cao
        Bitmap resultBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(resultBitmap);
        
        // Bật các cờ chống răng cưa và lọc bitmap để ảnh mượt nhất
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

        List<Rect> holes = FrameConfig.getHolesForFrame(frameResId);

        // 3. VẼ CÁC ẢNH CHỤP (SCALED)
        for (int i = 0; i < Math.min(photoUris.size(), holes.size()); i++) {
            String originalUri = photoUris.get(i);
            Bitmap originalPhoto = decodeBitmap(Uri.parse(originalUri));
            if (originalPhoto == null) continue;

            EditState photoState = sessionState != null
                    ? sessionState.getPhotoEditState(originalUri).copy()
                    : new EditState();

            Bitmap photoNoSticker = renderPhotoWithoutSticker(originalPhoto, photoState);
            originalPhoto.recycle();
            if (photoNoSticker == null) continue;

            // Tính toán khung lỗ (hole) và nhân với hệ số scale
            Rect hole = holes.get(i);
            Rect scaledHole = new Rect(
                    Math.round(hole.left * EXPORT_SCALE_FACTOR),
                    Math.round(hole.top * EXPORT_SCALE_FACTOR),
                    Math.round(hole.right * EXPORT_SCALE_FACTOR),
                    Math.round(hole.bottom * EXPORT_SCALE_FACTOR)
            );

            RectF srcRectF = StickerPlacementMapper.calculateCenterCropRect(
                    photoNoSticker.getWidth(),
                    photoNoSticker.getHeight(),
                    scaledHole.width(),
                    scaledHole.height()
            );
            
            Rect srcRect = new Rect(
                    Math.round(srcRectF.left),
                    Math.round(srcRectF.top),
                    Math.round(srcRectF.right),
                    Math.round(srcRectF.bottom)
            );

            // Vẽ ảnh đã crop vào lỗ đã phóng to
            canvas.drawBitmap(photoNoSticker, srcRect, scaledHole, paint);
            
            // Vẽ sticker (cũng sẽ được phóng to bên trong hàm này)
            drawStickerForHole(canvas, scaledHole, srcRectF, photoNoSticker.getWidth(), photoNoSticker.getHeight(), photoState);
            
            photoNoSticker.recycle();
        }

        // 4. VẼ FRAME CHỒNG LÊN TRÊN (SCALED)
        Rect destFrameRect = new Rect(0, 0, targetWidth, targetHeight);
        canvas.drawBitmap(frameBitmap, null, destFrameRect, paint);
        
        frameBitmap.recycle();
        return resultBitmap;
    }

    private Bitmap renderPhotoWithoutSticker(Bitmap source, EditState state) {
        if (source == null) {
            return null;
        }
        if (state == null) {
            return source;
        }
        EditState noSticker = state.copy();
        noSticker.setStickerStyle(EditState.StickerStyle.NONE);
        noSticker.setCustomStickerBase64(null);
        return renderEditedBitmapUseCase.execute(this, source, noSticker, false);
    }

    private void drawStickerForHole(
            Canvas canvas,
            Rect targetHole,
            RectF srcRect,
            int sourceWidth,
            int sourceHeight,
            EditState state
    ) {
        if (state == null || state.getStickerStyle() == EditState.StickerStyle.NONE) {
            return;
        }

        RectF stickerCropRect = resolveStickerCropRect(state, sourceWidth, sourceHeight, srcRect);
        float sourceCenterX;
        float sourceCenterY;

        if (state.getStickerCropX() >= 0f && state.getStickerCropY() >= 0f) {
            sourceCenterX = stickerCropRect.left + StickerPlacementMapper.clamp01(state.getStickerCropX()) * stickerCropRect.width();
            sourceCenterY = stickerCropRect.top + StickerPlacementMapper.clamp01(state.getStickerCropY()) * stickerCropRect.height();
        } else if (state.getStickerX() >= 0f && state.getStickerY() >= 0f) {
            sourceCenterX = state.getStickerX() * sourceWidth;
            sourceCenterY = state.getStickerY() * sourceHeight;
        } else {
            sourceCenterX = stickerCropRect.left + 0.84f * stickerCropRect.width();
            sourceCenterY = stickerCropRect.top + 0.18f * stickerCropRect.height();
        }

        float relX = StickerPlacementMapper.clamp01((sourceCenterX - srcRect.left) / Math.max(1f, srcRect.width()));
        float relY = StickerPlacementMapper.clamp01((sourceCenterY - srcRect.top) / Math.max(1f, srcRect.height()));

        float targetCenterX = targetHole.left + relX * targetHole.width();
        float targetCenterY = targetHole.top + relY * targetHole.height();

        int sourceSize = Math.max(72, Math.min(sourceWidth, sourceHeight) / 5);
        float scale = Math.min(
                targetHole.width() / Math.max(1f, srcRect.width()),
                targetHole.height() / Math.max(1f, srcRect.height())
        );
        int targetSize = Math.max(22, Math.round(sourceSize * scale));

        int left = Math.round(targetCenterX - (targetSize / 2f));
        int top = Math.round(targetCenterY - (targetSize / 2f));
        Rect dest = new Rect(left, top, left + targetSize, top + targetSize);

        Bitmap customStickerBitmap = null;
        try {
            if (state.getStickerStyle() == EditState.StickerStyle.CUSTOM && state.getCustomStickerBase64() != null) {
                byte[] bytes = Base64.decode(state.getCustomStickerBase64(), Base64.DEFAULT);
                customStickerBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }
        } catch (Exception ignored) {
            customStickerBitmap = null;
        }

        if (customStickerBitmap != null) {
            canvas.drawBitmap(customStickerBitmap, null, dest, new Paint(Paint.FILTER_BITMAP_FLAG));
            customStickerBitmap.recycle();
            return;
        }

        int drawableId = resolveStickerDrawable(state.getStickerStyle());
        if (drawableId == 0) {
            return;
        }
        Drawable drawable = ContextCompat.getDrawable(this, drawableId);
        if (drawable == null) {
            return;
        }
        drawable.setBounds(dest);
        drawable.draw(canvas);
    }

    private RectF resolveStickerCropRect(EditState state, int sourceWidth, int sourceHeight, RectF fallbackCropRect) {
        if (state.getStickerCropLeftNorm() >= 0f
                && state.getStickerCropTopNorm() >= 0f
                && state.getStickerCropRightNorm() > state.getStickerCropLeftNorm()
                && state.getStickerCropBottomNorm() > state.getStickerCropTopNorm()) {
            return StickerPlacementMapper.fromNormalizedRect(
                    new RectF(
                            state.getStickerCropLeftNorm(),
                            state.getStickerCropTopNorm(),
                            state.getStickerCropRightNorm(),
                            state.getStickerCropBottomNorm()
                    ),
                    sourceWidth,
                    sourceHeight
            );
        }
        return new RectF(fallbackCropRect);
    }

    private int resolveStickerDrawable(EditState.StickerStyle stickerStyle) {
        switch (stickerStyle) {
            case STAR:
                return R.drawable.ic_star_24;
            case FLASH:
                return R.drawable.ic_flash_on_24;
            case CAMERA:
                return R.drawable.ic_videocam_24;
            case HEART:
                return R.drawable.ic_sticker_heart;
            case CROWN:
                return R.drawable.ic_sticker_crown;
            case SMILE:
                return R.drawable.ic_sticker_smile;
            case FLOWER:
                return R.drawable.ic_sticker_flower;
            case BOW:
                return R.drawable.ic_sticker_bow;
            case SPARKLE:
                return R.drawable.ic_sticker_sparkle;
            case BUTTERFLY:
                return R.drawable.ic_sticker_butterfly;
            case CHERRY:
                return R.drawable.ic_sticker_cherry;
            case MUSIC:
                return R.drawable.ic_sticker_music;
            case NONE:
            case CUSTOM:
            default:
                return 0;
        }
    }

    private Bitmap decodeBitmap(Uri uri) {
        try {
            // 1. Decode bitmap gốc
            Bitmap bitmap;
            try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(inputStream);
            }
            if (bitmap == null) return null;

            // 2. Kiểm tra hướng xoay EXIF
            int rotation = 0;
            try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                if (inputStream != null) {
                    ExifInterface exif = new ExifInterface(inputStream);
                    int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    switch (orientation) {
                        case ExifInterface.ORIENTATION_ROTATE_90: rotation = 90; break;
                        case ExifInterface.ORIENTATION_ROTATE_180: rotation = 180; break;
                        case ExifInterface.ORIENTATION_ROTATE_270: rotation = 270; break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // 3. Xoay bitmap nếu cần
            if (rotation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (rotated != bitmap) {
                    bitmap.recycle();
                }
                return rotated;
            }

            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Uri saveBitmapToCache(Bitmap bitmap) {
        try {
            File cacheFile = new File(getCacheDir(), "temp_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream out = new FileOutputStream(cacheFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            return Uri.fromFile(cacheFile);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Initialize Role-Based Access Control service
     * Fetches current user role and sets up permission checks
     */
    private void initializeRBAC() {
        authRepository.fetchCurrentRole(new AuthRepository.RoleCallback() {
            @Override
            public void onSuccess(UserRole role) {
                currentUserRole = role;
                rbacService = new RBACService(currentUid, role, isGuestSession);
            }

            @Override
            public void onError(String message) {
                // Fallback to USER role
                currentUserRole = UserRole.USER;
                rbacService = new RBACService(currentUid, UserRole.USER, isGuestSession);
            }
        });
    }

    /**
     * Secure storage process:
     * 1. Compress image to PNG bytes
     * 2. Encrypt compressed data using AES-256
     * 3. Save encrypted data to internal storage with restrictive permissions
     * 4. Store encrypted path reference in database
     */
    /**
     * Automatically save the final bitmap to the device's public gallery
     */
    private void autoSaveToPublicGallery(Bitmap bitmap) {
        if (bitmap == null) return;
        
        try {
            String name = "photobooth_" + System.currentTimeMillis() + ".png";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Photobooth");

            Uri outputUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (outputUri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(outputUri)) {
                    if (out != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        // Record stats (not strictly necessary for auto-save but good to track)
                        if (adminStatsRepository != null) adminStatsRepository.recordDownload(DownloadType.IMAGE);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveToUserLocalGallery(Bitmap bitmap) {

        AuthRepository authRepository = ((AppContainer) getApplication()).getAuthRepository();
        String uid = authRepository.getCurrentUid();
        if (uid == null || !rbacService.canAccessSession(uid)) {
            // RBAC Check: User must own the session
            return;
        }

        // Always save to standard Gallery format (.png) so it can be loaded by GalleryActivity
        fallbackSaveToGallery(bitmap, uid);

        // Also use secure storage service for encrypted storage if available
        if (secureImageStorageService != null) {
            try {
                // Step 1 & 2 & 3: Compress -> Encrypt -> Save to internal storage
                String encryptedImagePath = secureImageStorageService.saveSecureImage(
                        bitmap,
                        uid,
                        historySessionId != null ? historySessionId : String.valueOf(System.currentTimeMillis())
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Fallback method: Save image to external gallery (non-encrypted) if secure storage fails
     * This ensures user can still access photos even if encryption fails
     */
    private void fallbackSaveToGallery(Bitmap bitmap, String uid) {
        File galleryDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "user_gallery/" + uid);
        if (!galleryDir.exists()) {
            galleryDir.mkdirs();
        }

        String sessionIdPart = (historySessionId != null && !historySessionId.isEmpty())
                ? "_" + historySessionId
                : "";
        String fileName = "pose" + sessionIdPart + "_" + System.currentTimeMillis() + ".png";
        File file = new File(galleryDir, fileName);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveVideoToUserLocalGallery(Uri videoUri, String uid) {
        if (videoUri == null || uid == null) return;
        try {
            File galleryDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "user_gallery/" + uid);
            if (!galleryDir.exists()) galleryDir.mkdirs();

            String sessionIdPart = (historySessionId != null && !historySessionId.isEmpty()) ? "_" + historySessionId : "";
            String fileName = "pose" + sessionIdPart + "_" + System.currentTimeMillis() + ".mp4";
            File file = new File(galleryDir, fileName);

            try (java.io.InputStream in = getContentResolver().openInputStream(videoUri);
                 FileOutputStream out = new FileOutputStream(file)) {
                if (in == null) return;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void exportTimelapseVideo() {
        // Guest Check: Guests cannot export video, prompt to login
        if (isGuestSession) {
            HelpDialogUtils.showHistoryGuestRegisterCta(
                    this,
                    getString(R.string.home_history_user_only_title),
                    getString(R.string.home_history_user_only_message),
                    () -> {
                        // Redirect to Login
                        Intent intent = new Intent(ResultActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    },
                    null // If they cancel, do nothing
            );
            return;
        }

        // RBAC Check: Verify user can perform this action on their own session
        if (rbacService != null && !rbacService.canAccessSession(currentUid)) {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            return;
        }

        if (sourceOriginalUris == null || sourceOriginalUris.isEmpty()) {
            Toast.makeText(this, R.string.no_images_for_video, Toast.LENGTH_SHORT).show();
            return;
        }

        btnSaveVideo.setEnabled(false);
        String originalText = btnSaveVideo.getText().toString();
        runOnUiThread(() -> btnSaveVideo.setText(R.string.video_exporting_btn));

        new Thread(() -> {
            try {
                // Prepare edited URIs
                List<String> editedUris = new ArrayList<>();
                Map<String, String> editedImageUris = sessionState.getEditedImageUris();
                
                for (String originalUri : sourceOriginalUris) {
                    String editedUri = (editedImageUris != null) ? editedImageUris.get(originalUri) : null;
                    editedUris.add(editedUri != null ? editedUri : originalUri);
                }

                // Photobooth Ping-Pong loop: e.g. [0, 1, 2, 3, 2, 1]
                List<String> loopSequence = new ArrayList<>(editedUris);
                for (int i = editedUris.size() - 2; i >= 1; i--) {
                    loopSequence.add(editedUris.get(i));
                }

                // Optimization: Reducing loops from 4 to 3 to speed up export while keeping decent length
                List<String> finalFrames = new ArrayList<>();
                finalFrames.addAll(loopSequence);
                finalFrames.addAll(loopSequence);
                finalFrames.addAll(loopSequence);

                runOnUiThread(() -> btnSaveVideo.setText(R.string.video_saving_btn));

                // Encode at 6 Frames Per Second
                Uri videoUri = createTimelapseVideoUseCase.execute(ResultActivity.this, finalFrames, 6);
                
                if (videoUri != null && currentUid != null) {
                    saveVideoToUserLocalGallery(videoUri, currentUid);
                }

                runOnUiThread(() -> {
                    btnSaveVideo.setEnabled(true);
                    btnSaveVideo.setText(originalText);
                    if (videoUri != null) {
                        if (!isGuestSession && historyRepository != null && currentUid != null && historySessionId != null) {
                            historyRepository.updateVideoUri(currentUid, historySessionId, videoUri.toString());
                        }
                        adminStatsRepository.recordDownload(DownloadType.VIDEO);
                        Toast.makeText(ResultActivity.this, R.string.video_saved_success, Toast.LENGTH_LONG).show();
                        showFeedbackBottomSheet(true);
                    } else {
                        Toast.makeText(ResultActivity.this, R.string.failed_save_video, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    btnSaveVideo.setEnabled(true);
                    btnSaveVideo.setText(originalText);
                    Toast.makeText(ResultActivity.this, R.string.failed_save_video, Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void saveCurrentResultAsPng() {
        // RBAC Check: Verify user can save own session result (Guest can download PNG)
        if (!isGuestSession && rbacService != null && !rbacService.canAccessSession(currentUid)) {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            return;
        }

        if (resultUri == null) {
            Toast.makeText(this, R.string.no_result_to_save, Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap bitmap = decodeBitmap(resultUri);
        if (bitmap == null) return;

        String name = "photobooth_" + System.currentTimeMillis() + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Photobooth");

        Uri outputUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (outputUri != null) {
            try (OutputStream out = getContentResolver().openOutputStream(outputUri)) {
                if (out == null) {
                    Toast.makeText(this, R.string.failed_open_photo, Toast.LENGTH_SHORT).show();
                    return;
                }
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                adminStatsRepository.recordDownload(DownloadType.IMAGE);
                Toast.makeText(this, R.string.saved_to_gallery_short, Toast.LENGTH_SHORT).show();
                showFeedbackBottomSheet(true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void shareResult() {
        // Guest Check: Guests cannot share, prompt to login
        if (isGuestSession) {
            HelpDialogUtils.showHistoryGuestRegisterCta(
                    this,
                    getString(R.string.home_history_user_only_title),
                    getString(R.string.home_history_user_only_message),
                    () -> {
                        // Redirect to Login
                        Intent intent = new Intent(ResultActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    },
                    null
            );
            return;
        }

        // RBAC Check: Verify user can share own session result
        if (rbacService != null && !rbacService.canAccessSession(currentUid)) {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            return;
        }

        if (resultUri == null) {
            Toast.makeText(this, R.string.no_result_to_save, Toast.LENGTH_SHORT).show();
            return;
        }

        File file = new File(getCacheDir(), resultUri.getLastPathSegment());
        if (!file.exists()) {
            Toast.makeText(this, R.string.no_result_to_save, Toast.LENGTH_SHORT).show();
            return;
        }

        Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);

        // Sử dụng ViralRewardManager để hiển thị Popup Chia sẻ & Nhận quà
        if (viralRewardManager != null) {
            viralRewardManager.checkAndShowRewardPopup(contentUri);
        } else {
            // Fallback về share mặc định nếu manager lỗi
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.setDataAndType(contentUri, getContentResolver().getType(contentUri));
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.setType("image/png");
            startActivity(Intent.createChooser(shareIntent, getString(R.string.result_share)));
        }
    }

    private void showFeedbackBottomSheet(boolean autoTrigger) {
        if (isGuestSession) return;
        if (autoTrigger && hasShownFeedback) {
            return;
        }
        if (autoTrigger) hasShownFeedback = true;

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View bottomSheetView = getLayoutInflater().inflate(R.layout.layout_feedback_bottom_sheet, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        RatingBar ratingBar = bottomSheetView.findViewById(R.id.ratingBar);
        TextInputEditText etFeedback = bottomSheetView.findViewById(R.id.etFeedback);
        MaterialButton btnSubmit = bottomSheetView.findViewById(R.id.btnSubmitFeedback);

        ratingBar.setOnRatingBarChangeListener((rb, rating, fromUser) -> {
            if (fromUser) {
                int toneType = ToneGenerator.TONE_PROP_BEEP;
                if (rating >= 5) toneType = ToneGenerator.TONE_DTMF_0;
                else if (rating >= 4) toneType = ToneGenerator.TONE_DTMF_1;
                else if (rating >= 3) toneType = ToneGenerator.TONE_DTMF_2;
                else if (rating >= 2) toneType = ToneGenerator.TONE_DTMF_3;
                else toneType = ToneGenerator.TONE_DTMF_4;
                
                toneGenerator.startTone(toneType, 150);
            }
        });

        btnSubmit.setOnClickListener(v -> {
            float rating = ratingBar.getRating();

            // Ensure Telex composing text is committed before reading the final feedback value.
            etFeedback.clearComposingText();
            etFeedback.clearFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(etFeedback.getWindowToken(), 0);
            }

            btnSubmit.setEnabled(false);
            etFeedback.postDelayed(() -> {
                String feedback = etFeedback.getText() != null ? etFeedback.getText().toString().trim() : "";

                AuthRepository authRepository = ((AppContainer) getApplication()).getAuthRepository();
                String uid = authRepository.getCurrentUid();
                String email = authRepository.getCurrentEmail();

                Map<String, Object> review = new HashMap<>();
                review.put("userId", uid);
                review.put("userEmail", email);
                review.put("rating", rating);
                review.put("feedback", feedback);
                review.put("createdAt", FieldValue.serverTimestamp());
                review.put("timestamp", System.currentTimeMillis());
                review.put("date", new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date()));

                FirebaseFirestore.getInstance().collection("reviews")
                    .add(review)
                    .addOnSuccessListener(doc -> {
                        if (!isGuestSession && historyRepository != null && uid != null && historySessionId != null) {
                            historyRepository.updateFeedback(uid, historySessionId, rating, feedback);
                        }
                        adminStatsRepository.recordReviewSubmitted();
                        Toast.makeText(this, getString(R.string.feedback_thanks_with_rating, rating), Toast.LENGTH_SHORT).show();
                        hasShownFeedback = true;
                        bottomSheetDialog.dismiss();
                    })
                    .addOnFailureListener(e -> {
                        btnSubmit.setEnabled(true);
                        Toast.makeText(this, R.string.feedback_submit_failed, Toast.LENGTH_SHORT).show();
                    });
            }, 90L);
        });

        bottomSheetDialog.show();

    }

    private List<String> resolveSourceOriginalUris() {
        ArrayList<String> fromIntent = getIntent().getStringArrayListExtra(IntentKeys.EXTRA_CAPTURED_IMAGES);
        if (fromIntent != null) return fromIntent;
        if (sessionState != null && sessionState.getCapturedImageUris() != null) {
            return new ArrayList<>(sessionState.getCapturedImageUris());
        }
        return new ArrayList<>();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }

    private void persistHistoryBaseRecord() {
        if (isGuestSession) {
            if (!hasShownGuestHistoryNotice && !isFinishing()) {
                hasShownGuestHistoryNotice = true;
                HelpDialogUtils.showHistoryGuestRegisterCta(
                        this,
                        getString(R.string.home_history_user_only_title),
                        getString(R.string.home_history_user_only_message),
                        this::openRegisterFromGuest
                );
            }
            return;
        }
        if (historyRepository == null || currentUid == null || resultUri == null) {
            return;
        }
        if (historySessionId != null) {
            return;
        }

        int selectedFrameResId = sessionState != null ? sessionState.getSelectedFrameResId() : -1;

        HistorySession historySession = new HistorySession();
        historySession.setUserId(currentUid);
        historySession.setCapturedAt(System.currentTimeMillis());
        historySession.setSelectedImageUris(sourceOriginalUris);
        historySession.setResultImageUri(resultUri.toString());
        historySession.setFrameResId(selectedFrameResId);
        historySession.setAspectRatio(resolveAspectRatio(selectedFrameResId));

        if (selectedFrameResId != -1) {
            historySession.setFrameName(resolveFrameName(selectedFrameResId));
        } else {
            historySession.setFrameName("Unknown");
        }

        historySessionId = historyRepository.createSession(historySession);
    }

    private String resolveFrameName(int frameResId) {
        if (frameResId == R.drawable.frm_3x4_cushin) return "Cushin";
        if (frameResId == R.drawable.frm_3x4_movie) return "Movie";
        if (frameResId == R.drawable.frm_3x4_pig_hero) return "Pig Hero";
        if (frameResId == R.drawable.frm3_16x9_blue_canvas) return "Blue Canvas";
        if (frameResId == R.drawable.frm3_16x9_green_doodle) return "Green Doodle";
        if (frameResId == R.drawable.frm3_16x9_red_star) return "Red Star";
        if (frameResId == R.drawable.frm4_16x9_bow) return "Bow";
        if (frameResId == R.drawable.frm4_16x9_food) return "Food";
        if (frameResId == R.drawable.frm4_16x9_heart) return "Heart";
        return "Unknown";
    }

    private String resolveAspectRatio(int frameResId) {
        if (frameResId == R.drawable.frm_3x4_cushin
                || frameResId == R.drawable.frm_3x4_movie
                || frameResId == R.drawable.frm_3x4_pig_hero) {
            return "3:4";
        }
        if (frameResId == R.drawable.frm3_16x9_blue_canvas
                || frameResId == R.drawable.frm3_16x9_green_doodle
                || frameResId == R.drawable.frm3_16x9_red_star
                || frameResId == R.drawable.frm4_16x9_bow
                || frameResId == R.drawable.frm4_16x9_food
                || frameResId == R.drawable.frm4_16x9_heart) {
            return "16:9";
        }
        return "N/A";
    }

    private void openRegisterFromGuest() {
        authRepository.signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(IntentKeys.EXTRA_OPEN_REGISTER, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
