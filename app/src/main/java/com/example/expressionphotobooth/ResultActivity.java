package com.example.expressionphotobooth;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.expressionphotobooth.data.video.TimelapseVideoEncoder;
import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.example.expressionphotobooth.domain.usecase.CreateTimelapseVideoUseCase;
import com.example.expressionphotobooth.domain.usecase.CreateVerticalCollageUseCase;
import com.example.expressionphotobooth.utils.FrameConfig;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_result);

        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();
        sessionState = sessionRepository.getSession();
        
        if (sessionState == null) {
            Toast.makeText(this, "Session data not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

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
                runOnUiThread(() -> Toast.makeText(this, "Không tìm thấy Frame, đang tạo ảnh dọc mặc định", Toast.LENGTH_SHORT).show());
            }

            runOnUiThread(() -> {
                if (finalBitmap != null) {
                    resultUri = saveBitmapToCache(finalBitmap);
                    ivFinalResult.setImageBitmap(finalBitmap);
                    sessionState.setResultImageUri(resultUri != null ? resultUri.toString() : null);
                    sessionRepository.saveSession(sessionState);
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
                sessionRepository.clearSession();
                Intent intent = new Intent(ResultActivity.this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
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

        // Lấy danh sách tọa độ các lỗ hổng
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
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(inputStream);
        } catch (Exception e) {
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

        btnSaveVideo.setEnabled(false);
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

                // Repeat the loop 2 times to reach ~4 seconds at 3 FPS
                List<String> finalFrames = new ArrayList<>();
                finalFrames.addAll(loopSequence);
                finalFrames.addAll(loopSequence);

                // Encode at 3 Frames Per Second for standard photobooth bounce feel
                Uri videoUri = createTimelapseVideoUseCase.execute(ResultActivity.this, finalFrames, 3);

                runOnUiThread(() -> {
                    btnSaveVideo.setEnabled(true);
                    if (videoUri != null) {
                        Toast.makeText(ResultActivity.this, R.string.video_saved_success, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(ResultActivity.this, R.string.failed_save_video, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    btnSaveVideo.setEnabled(true);
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
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                Toast.makeText(this, "Đã lưu vào bộ sưu tập!", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private List<String> resolveSourceOriginalUris() {
        ArrayList<String> fromIntent = getIntent().getStringArrayListExtra(IntentKeys.EXTRA_CAPTURED_IMAGES);
        if (fromIntent != null) return fromIntent;
        if (sessionState != null && sessionState.getCapturedImageUris() != null) {
            return new ArrayList<>(sessionState.getCapturedImageUris());
        }
        return new ArrayList<>();
    }
}
