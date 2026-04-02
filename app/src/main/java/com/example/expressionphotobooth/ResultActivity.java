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
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;

import com.example.expressionphotobooth.data.video.TimelapseVideoEncoder;
import com.example.expressionphotobooth.domain.model.DownloadType;
import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.AdminStatsRepository;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.example.expressionphotobooth.domain.usecase.CreateTimelapseVideoUseCase;
import com.example.expressionphotobooth.domain.usecase.CreateVerticalCollageUseCase;
import com.example.expressionphotobooth.utils.FrameConfig;
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

public class ResultActivity extends AppCompatActivity {
    private Uri resultUri;
    private SessionRepository sessionRepository;
    private SessionState sessionState;
    private CreateTimelapseVideoUseCase createTimelapseVideoUseCase;
    private CreateVerticalCollageUseCase createVerticalCollageUseCase;
    private MaterialButton btnSaveVideo;
    private List<String> sourceOriginalUris;
    private boolean hasShownFeedback = false;
    private ToneGenerator toneGenerator;
    private AdminStatsRepository adminStatsRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();
        adminStatsRepository = ((AppContainer) getApplication()).getAdminStatsRepository();
        sessionState = sessionRepository.getSession();
        
        if (sessionState == null) {
            finish();
            return;
        }

        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        createTimelapseVideoUseCase = new CreateTimelapseVideoUseCase(new TimelapseVideoEncoder());
        createVerticalCollageUseCase = new CreateVerticalCollageUseCase();
        sourceOriginalUris = resolveSourceOriginalUris();

        if (sourceOriginalUris.isEmpty()) {
            Toast.makeText(this, R.string.no_photo_to_continue, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ImageView ivFinalResult = findViewById(R.id.ivFinalResult);

        // Chạy ngầm để xử lý ảnh nặng
        new Thread(() -> {
            List<String> imageUrisToCollage = new ArrayList<>();
            Map<String, String> editedImageUris = sessionState.getEditedImageUris();
            
            for (String originalUri : sourceOriginalUris) {
                String editedUri = (editedImageUris != null) ? editedImageUris.get(originalUri) : null;
                imageUrisToCollage.add(editedUri != null ? editedUri : originalUri);
            }

            // Lấy Frame ID từ SharedPreferences
            int selectedFrameResId = getSharedPreferences("PhotoboothPrefs", MODE_PRIVATE)
                    .getInt("SELECTED_FRAME_ID", -1);

            Bitmap finalBitmap;
            if (selectedFrameResId != -1) {
                // TRƯỜNG HỢP CÓ CHỌN FRAME
                finalBitmap = createFramedCollage(imageUrisToCollage, selectedFrameResId);
            } else {
                // TRƯỜNG HỢP LỖI HOẶC KHÔNG CHỌN FRAME -> Fallback về Collage dọc
                finalBitmap = createVerticalCollageUseCase.execute(ResultActivity.this, imageUrisToCollage);
                runOnUiThread(() -> Toast.makeText(this, R.string.frame_not_found_using_default_collage, Toast.LENGTH_SHORT).show());
            }

            runOnUiThread(() -> {
                if (finalBitmap != null) {
                    resultUri = saveBitmapToCache(finalBitmap);
                    ivFinalResult.setImageBitmap(finalBitmap);
                    sessionState.setResultImageUri(resultUri != null ? resultUri.toString() : null);
                    sessionRepository.saveSession(sessionState);

                    // Tự động bật popup đánh giá sau khi người dùng xem kết quả 1 lúc
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!isFinishing()) showFeedbackBottomSheet(true);
                    }, 1500);
                } else {
                    Toast.makeText(ResultActivity.this, R.string.no_result_to_show, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();

        findViewById(R.id.btnSavePng).setOnClickListener(v -> saveCurrentResultAsPng());
        btnSaveVideo = findViewById(R.id.btnNext);
        btnSaveVideo.setOnClickListener(v -> exportTimelapseVideo());

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

        View btnRate = findViewById(R.id.btnRate);
        if (btnRate != null) {
            btnRate.setOnClickListener(v -> showFeedbackBottomSheet(false));
        }
    }

    private Bitmap createFramedCollage(List<String> photoUris, int frameResId) {
        // 1. Cấm Android tự ý scale mật độ điểm ảnh (Density Scaling)
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap frameBitmap = BitmapFactory.decodeResource(getResources(), frameResId, options);

        if (frameBitmap == null) return null;

        // 2. Tạo Bitmap kết quả với kích thước chuẩn xác của file Frame (ví dụ 180x559)
        Bitmap resultBitmap = Bitmap.createBitmap(frameBitmap.getWidth(), frameBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(resultBitmap);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG); // Giúp ảnh sau khi scale mượt hơn

        // Lấy danh sách tọa độ các lỗ hổng từ FrameConfig (cấu hình tĩnh)
        List<Rect> holes = FrameConfig.getHolesForFrame(frameResId);

        // 3. VẼ CÁC ẢNH CHỤP NẰM DƯỚI FRAME
        for (int i = 0; i < Math.min(photoUris.size(), holes.size()); i++) {
            Bitmap photo = decodeBitmap(Uri.parse(photoUris.get(i)));
            if (photo != null) {
                Rect targetHole = holes.get(i);

                // Thuật toán CENTER CROP để ảnh vừa khít lỗ hổng không bị méo
                Rect srcRect = calculateCenterCrop(photo.getWidth(), photo.getHeight(), targetHole.width(), targetHole.height());

                // Vẽ ảnh chụp vào đúng vị trí lỗ hổng
                canvas.drawBitmap(photo, srcRect, targetHole, paint);
                photo.recycle(); // Giải phóng ngay để tránh tràn RAM
            }
        }

        // 4. VẼ FRAME LÊN TRÊN CÙNG (ĐÈ LÊN ẢNH CHỤP)
        // Ép Canvas vẽ chính xác 1:1, không cho phép tự động zoom
        Rect frameRect = new Rect(0, 0, frameBitmap.getWidth(), frameBitmap.getHeight());
        canvas.drawBitmap(frameBitmap, null, frameRect, null);
        
        frameBitmap.recycle(); // Giải phóng frame bitmap sau khi vẽ xong

        return resultBitmap;
    }

    /**
     * Thuật toán tính toán vùng cắt Center Crop
     */
    private Rect calculateCenterCrop(int srcW, int srcH, int dstW, int dstH) {
        float srcAspect = (float) srcW / srcH;
        float dstAspect = (float) dstW / dstH;

        int cropW, cropH, left, top;

        if (srcAspect > dstAspect) {
            // Ảnh gốc rộng hơn mục tiêu -> Cắt hai bên
            cropH = srcH;
            cropW = (int) (srcH * dstAspect);
            left = (srcW - cropW) / 2;
            top = 0;
        } else {
            // Ảnh gốc cao hơn mục tiêu -> Cắt trên dưới
            cropW = srcW;
            cropH = (int) (srcW / dstAspect);
            left = 0;
            top = (srcH - cropH) / 2;
        }
        return new Rect(left, top, left + cropW, top + cropH);
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

    private void exportTimelapseVideo() {
        if (sourceOriginalUris == null || sourceOriginalUris.isEmpty()) {
            Toast.makeText(this, R.string.no_images_for_video, Toast.LENGTH_SHORT).show();
            return;
        }

        // UI/UX Improvement: Disable button and show loading text
        btnSaveVideo.setEnabled(false);
        String originalText = btnSaveVideo.getText().toString();
        btnSaveVideo.setText(R.string.video_exporting_btn);
        
        Toast.makeText(this, R.string.exporting_video, Toast.LENGTH_SHORT).show();

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

                // UI update before saving
                runOnUiThread(() -> btnSaveVideo.setText(R.string.video_saving_btn));

                // Encode at 6 Frames Per Second
                Uri videoUri = createTimelapseVideoUseCase.execute(ResultActivity.this, finalFrames, 6);

                runOnUiThread(() -> {
                    btnSaveVideo.setEnabled(true);
                    btnSaveVideo.setText(originalText);
                    if (videoUri != null) {
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

    private void showFeedbackBottomSheet(boolean autoTrigger) {
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
}
