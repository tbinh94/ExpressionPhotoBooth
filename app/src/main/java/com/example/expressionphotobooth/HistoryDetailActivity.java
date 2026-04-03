package com.example.expressionphotobooth;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.example.expressionphotobooth.domain.model.HistorySession;
import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.example.expressionphotobooth.domain.repository.HistoryRepository;
import com.example.expressionphotobooth.utils.LocaleManager;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HistoryDetailActivity extends AppCompatActivity {

    private HistoryRepository historyRepository;
    private AuthRepository authRepository;
    private HistorySession historySession;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_detail);

        historyRepository = ((AppContainer) getApplication()).getHistoryRepository();
        authRepository = ((AppContainer) getApplication()).getAuthRepository();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        String sessionId = getIntent().getStringExtra(IntentKeys.EXTRA_HISTORY_ID);
        String uid = authRepository.getCurrentUid();
        if (sessionId == null || uid == null) {
            finish();
            return;
        }

        historySession = historyRepository.getById(uid, sessionId);
        if (historySession == null) {
            finish();
            return;
        }

        bindData();
        findViewById(R.id.btnSavePhoto).setOnClickListener(v -> saveImageToGallery());
        findViewById(R.id.btnShare).setOnClickListener(v -> shareImage());
    }

    private void bindData() {
        ImageView ivResult = findViewById(R.id.ivHistoryResult);
        TextView tvDate = findViewById(R.id.tvHistoryDate);
        TextView tvFrame = findViewById(R.id.tvHistoryFrame);
        TextView tvRatio = findViewById(R.id.tvHistoryRatio);
        TextView tvFeedback = findViewById(R.id.tvHistoryFeedback);

        Glide.with(this)
                .load(Uri.parse(historySession.getResultImageUri()))
                .into(ivResult);

        String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(historySession.getCapturedAt()));
        tvDate.setText(getString(R.string.history_date_value, date));
        tvFrame.setText(getString(R.string.history_frame_value, historySession.getFrameName()));
        tvRatio.setText(getString(R.string.history_ratio_value, historySession.getAspectRatio()));

        String feedback = historySession.getFeedback();
        if (feedback == null || feedback.trim().isEmpty()) {
            tvFeedback.setText(R.string.history_not_available);
        } else {
            tvFeedback.setText(feedback);
        }
    }

    private void saveImageToGallery() {
        Uri sourceUri = Uri.parse(historySession.getResultImageUri());

        String name = "history_" + System.currentTimeMillis() + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Photobooth");

        Uri outputUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (outputUri == null) {
            return;
        }

        try (InputStream in = getContentResolver().openInputStream(sourceUri);
             OutputStream out = getContentResolver().openOutputStream(outputUri)) {
            if (in == null || out == null) {
                return;
            }
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            Toast.makeText(this, R.string.saved_to_gallery_short, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.failed_open_photo, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareImage() {
        Uri uri = Uri.parse(historySession.getResultImageUri());
        Uri shareUri = uri;

        if ("file".equals(uri.getScheme())) {
            File file = new File(uri.getPath());
            shareUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.setType("image/png");
        shareIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.result_share)));
    }
}

