package com.example.expressionphotobooth;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

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
        createTimelapseVideoUseCase = new CreateTimelapseVideoUseCase(new TimelapseVideoEncoder());
        createVerticalCollageUseCase = new CreateVerticalCollageUseCase();
        sourceOriginalUris = resolveSourceOriginalUris();
        setupToolbar();

        ImageView ivFinalResult = findViewById(R.id.ivFinalResult);

        Toast.makeText(this, "Frame ID nhận được: " + sessionState.getSelectedFrameResId(), Toast.LENGTH_LONG).show();

        // Chạy ngầm để không bị lag máy
        new Thread(() -> {
            List<String> imageUrisToCollage = new ArrayList<>();
            for (String originalUri : sourceOriginalUris) {
                String editedUri = sessionState.getEditedImageUris().get(originalUri);
                imageUrisToCollage.add(editedUri != null ? editedUri : originalUri);
            }

            Bitmap tempBitmap = null;
            int selectedFrameResId = getSharedPreferences("PhotoboothPrefs", MODE_PRIVATE)
                    .getInt("SELECTED_FRAME_ID", -1);

            if (selectedFrameResId != -1) {
                // NẾU CÓ CHỌN FRAME
                tempBitmap = createFramedCollage(imageUrisToCollage, selectedFrameResId);
            } else {
                // NẾU KHÔNG CHỌN FRAME
                tempBitmap = createVerticalCollageUseCase.execute(ResultActivity.this, imageUrisToCollage);
            }

            // FIX LỖI: Chốt cứng (final) biến bitmap trước khi mang vào runOnUiThread
            final Bitmap bitmapToSave = tempBitmap;

            runOnUiThread(() -> {
                if (bitmapToSave != null) {
                    resultUri = saveBitmapToCache(bitmapToSave);
                    ivFinalResult.setImageBitmap(bitmapToSave);
                    sessionState.setResultImageUri(resultUri.toString());
                    sessionRepository.saveSession(sessionState);
                } else {
                    Toast.makeText(ResultActivity.this, R.string.no_result_to_show, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();

        MaterialButton btnSavePng = findViewById(R.id.btnSavePng);
        btnSaveVideo = findViewById(R.id.btnNext);
        btnSavePng.setOnClickListener(v -> saveCurrentResultAsPng());
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

    // Hàm dùng để lưu ảnh tạm thời vào bộ nhớ Cache
    private Uri saveBitmapToCache(Bitmap bitmap) {
        try {
            // Tạo một file tạm trong thư mục cache của ứng dụng
            File cacheFile = new File(getCacheDir(), "temp_result_" + System.currentTimeMillis() + ".png");
            FileOutputStream out = new FileOutputStream(cacheFile);

            // Nén ảnh và lưu vào file
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();

            // Trả về đường dẫn (Uri) của file vừa lưu
            return Uri.fromFile(cacheFile);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Bitmap createFramedCollage(List<String> photoUris, int frameResId) {
        // 1. Cấm Android tự phóng to ảnh gốc (Trường hợp 2 của bạn)
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap frameBitmap = BitmapFactory.decodeResource(getResources(), frameResId, options);
        if (frameBitmap == null) return null;

        Bitmap resultBitmap = Bitmap.createBitmap(frameBitmap.getWidth(), frameBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(resultBitmap);

        List<android.graphics.Rect> holes = com.example.expressionphotobooth.utils.FrameConfig.getHolesForFrame(frameResId);

        // 2. VẼ ẢNH CHỤP XUỐNG DƯỚI VỚI THUẬT TOÁN CENTER CROP
        for (int i = 0; i < Math.min(photoUris.size(), holes.size()); i++) {
            Uri photoUri = Uri.parse(photoUris.get(i));
            Bitmap photoBitmap = decodeBitmap(photoUri);

            if (photoBitmap != null) {
                android.graphics.Rect targetHole = holes.get(i); // Vị trí lỗ hổng trên frame

                // --- BẮT ĐẦU THUẬT TOÁN CENTER CROP ---
                int photoW = photoBitmap.getWidth();
                int photoH = photoBitmap.getHeight();
                float holeRatio = (float) targetHole.width() / targetHole.height();
                float photoRatio = (float) photoW / photoH;

                int cropW = photoW;
                int cropH = photoH;
                int cropX = 0;
                int cropY = 0;

                if (photoRatio > holeRatio) {
                    // Ảnh rộng hơn lỗ hổng -> Cắt bớt 2 bên hông
                    cropW = (int) (photoH * holeRatio);
                    cropX = (photoW - cropW) / 2;
                } else {
                    // Ảnh cao hơn lỗ hổng -> Cắt bớt đỉnh đầu và đáy
                    cropH = (int) (photoW / holeRatio);
                    cropY = (photoH - cropH) / 2;
                }

                // Chọn vùng ảnh chụp để lấy (đã cắt đúng tỷ lệ)
                android.graphics.Rect srcRect = new android.graphics.Rect(cropX, cropY, cropX + cropW, cropY + cropH);
                // --- KẾT THÚC CENTER CROP ---

                canvas.drawBitmap(photoBitmap, srcRect, targetHole, (android.graphics.Paint) null);
            }
        }

        return resultBitmap;
    }

    private void exportTimelapseVideo() {
        List<String> sourceUris = new ArrayList<>();
        for (String originalUri : sourceOriginalUris) {
            String editedUri = sessionState.getEditedImageUris().get(originalUri);
            sourceUris.add(editedUri != null ? editedUri : originalUri);
        }

        if (sourceUris.isEmpty()) {
            Toast.makeText(this, R.string.no_images_for_video, Toast.LENGTH_SHORT).show();
            return;
        }

        btnSaveVideo.setEnabled(false);
        Toast.makeText(this, R.string.exporting_video, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                // Sử dụng sourceUris đã bao gồm ảnh edit
                Uri videoUri = createTimelapseVideoUseCase.execute(this, sourceUris, 2);
                runOnUiThread(() -> {
                    btnSaveVideo.setEnabled(true);
                    Toast.makeText(this, R.string.video_saved_success, Toast.LENGTH_LONG).show();
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    btnSaveVideo.setEnabled(true);
                    Toast.makeText(this, R.string.failed_save_video, Toast.LENGTH_SHORT).show();
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
        if (bitmap == null) {
            Toast.makeText(this, R.string.failed_open_photo, Toast.LENGTH_SHORT).show();
            return;
        }

        String name = "photobooth_" + System.currentTimeMillis() + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Photobooth");

        Uri outputUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (outputUri == null) {
            Toast.makeText(this, R.string.failed_save_result, Toast.LENGTH_SHORT).show();
            return;
        }

        try (OutputStream out = getContentResolver().openOutputStream(outputUri)) {
            if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                Toast.makeText(this, R.string.failed_save_result, Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, getString(R.string.saved_to_gallery, name), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, R.string.failed_save_result, Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap decodeBitmap(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                return null;
            }
            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            return null;
        }
    }

    private void setupToolbar() {
        // No MaterialToolbar in layout (it has a custom btnBack)
        // If you want to use setSupportActionBar, you need a Toolbar with id topAppBar
        /*
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24);
            toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }
        */
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    private List<String> resolveSourceOriginalUris() {
        ArrayList<String> fromIntent = getIntent().getStringArrayListExtra(IntentKeys.EXTRA_CAPTURED_IMAGES);
        if (fromIntent != null && !fromIntent.isEmpty()) {
            return fromIntent;
        }
        return new ArrayList<>(sessionState.getCapturedImageUris());
    }
}
