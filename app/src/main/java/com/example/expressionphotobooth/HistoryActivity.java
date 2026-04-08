package com.example.expressionphotobooth;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.expressionphotobooth.domain.model.HistorySession;
import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.example.expressionphotobooth.domain.repository.HistoryRepository;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.example.expressionphotobooth.utils.LocaleManager;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private View emptyState;
    private HistoryAdapter adapter;
    private final List<HistorySession> sessions = new ArrayList<>();
    private HistoryRepository historyRepository;
    private SessionRepository sessionRepository;
    private AuthRepository authRepository;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        historyRepository = ((AppContainer) getApplication()).getHistoryRepository();
        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();
        authRepository = ((AppContainer) getApplication()).getAuthRepository();

        if (authRepository.isGuest()) {
            HelpDialogUtils.showHistoryGuestRegisterCta(
                    this,
                    getString(R.string.home_history_user_only_title),
                    getString(R.string.home_history_user_only_message),
                    this::openRegisterFromGuest,
                    this::returnHomeForGuestCancel
            );
            return;
        }

        rvHistory = findViewById(R.id.rvHistory);
        emptyState = findViewById(R.id.emptyState);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter();
        rvHistory.setAdapter(adapter);

        loadData();
    }

    private void loadData() {
        String uid = authRepository.getCurrentUid();
        if (uid == null) {
            finish();
            return;
        }
        sessions.clear();
        sessions.addAll(historyRepository.getSessions(uid));

        boolean empty = sessions.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvHistory.setVisibility(empty ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            HistorySession item = sessions.get(position);

            holder.tvDate.setText(getString(R.string.history_date_value, dateFormat.format(new Date(item.getCapturedAt()))));
            holder.tvFrame.setText(getString(R.string.history_frame_value, resolveFrameDisplayName(item)));
            holder.tvVideoState.setText(item.hasVideo() ? R.string.history_has_video : R.string.history_no_video);

            Glide.with(holder.ivThumb)
                    .load(Uri.parse(item.getResultImageUri()))
                    .centerCrop()
                    .into(holder.ivThumb);

            holder.ivThumb.setOnClickListener(v -> openDetail(item));
            holder.btnSavePhoto.setOnClickListener(v -> saveImageToGallery(item.getResultImageUri()));
            holder.btnSaveVideo.setOnClickListener(v -> saveVideoToGallery(item.getVideoUri()));
            holder.btnReload.setOnClickListener(v -> reloadSession(item));
            holder.btnShare.setOnClickListener(v -> shareImage(item.getResultImageUri()));
            holder.btnViewFeedback.setOnClickListener(v -> showFeedback(item));
            holder.btnDelete.setOnClickListener(v -> deleteSession(item));
        }

        @Override
        public int getItemCount() {
            return sessions.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            ImageView ivThumb;
            TextView tvDate;
            TextView tvFrame;
            TextView tvVideoState;
            MaterialButton btnSavePhoto;
            MaterialButton btnSaveVideo;
            MaterialButton btnReload;
            MaterialButton btnShare;
            MaterialButton btnViewFeedback;
            MaterialButton btnDelete;

            Holder(View itemView) {
                super(itemView);
                ivThumb = itemView.findViewById(R.id.ivHistoryThumb);
                tvDate = itemView.findViewById(R.id.tvHistoryDate);
                tvFrame = itemView.findViewById(R.id.tvHistoryFrame);
                tvVideoState = itemView.findViewById(R.id.tvHistoryVideoState);
                btnSavePhoto = itemView.findViewById(R.id.btnHistorySavePhoto);
                btnSaveVideo = itemView.findViewById(R.id.btnHistorySaveVideo);
                btnReload = itemView.findViewById(R.id.btnHistoryReload);
                btnShare = itemView.findViewById(R.id.btnHistoryShare);
                btnViewFeedback = itemView.findViewById(R.id.btnHistoryFeedback);
                btnDelete = itemView.findViewById(R.id.btnHistoryDelete);
            }
        }
    }

    private void openDetail(HistorySession item) {
        Intent intent = new Intent(this, HistoryDetailActivity.class);
        intent.putExtra(IntentKeys.EXTRA_HISTORY_ID, item.getId());
        startActivity(intent);
    }

    private void showFeedback(HistorySession item) {
        String ratingText = item.getRating() >= 0 ? String.format(Locale.getDefault(), "%.1f", item.getRating()) : getString(R.string.history_not_available);
        String feedbackText = (item.getFeedback() == null || item.getFeedback().trim().isEmpty())
                ? getString(R.string.history_not_available)
                : item.getFeedback();

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

    private void deleteSession(HistorySession item) {
        String uid = authRepository.getCurrentUid();
        if (uid == null) {
            return;
        }

        HelpDialogUtils.showHistoryStyledConfirm(
                this,
                R.drawable.ic_help_24,
                getString(R.string.history_delete_title),
                getString(R.string.history_delete_message),
                getString(R.string.history_popup_ok),
                getString(R.string.history_popup_cancel),
                () -> {
                    deleteLocalFileIfExists(item.getResultImageUri());
                    historyRepository.deleteSession(uid, item.getId());
                    loadData();
                },
                null
        );
    }

    private void saveImageToGallery(String sourceUriString) {
        if (sourceUriString == null || sourceUriString.isEmpty()) {
            Toast.makeText(this, R.string.no_result_to_save, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri sourceUri = Uri.parse(sourceUriString);

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

    private void saveVideoToGallery(String sourceUriString) {
        if (sourceUriString == null || sourceUriString.isEmpty()) {
            Toast.makeText(this, R.string.history_no_video_to_save, Toast.LENGTH_SHORT).show();
            return;
        }

        Uri sourceUri = Uri.parse(sourceUriString);
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, "history_video_" + System.currentTimeMillis() + ".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Photobooth");
        Uri outputUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
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
            Toast.makeText(this, R.string.video_saved_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.failed_save_video, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareImage(String sourceUriString) {
        if (sourceUriString == null || sourceUriString.isEmpty()) {
            return;
        }
        Uri uri = Uri.parse(sourceUriString);
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

    private void deleteLocalFileIfExists(String uriString) {
        if (uriString == null) {
            return;
        }
        try {
            Uri uri = Uri.parse(uriString);
            if ("file".equals(uri.getScheme())) {
                File file = new File(uri.getPath());
                if (file.exists()) {
                    file.delete();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String resolveFrameDisplayName(HistorySession item) {
        if (item == null) {
            return getString(R.string.history_not_available);
        }
        if (item.getFrameName() != null && !item.getFrameName().trim().isEmpty() && !"Unknown".equalsIgnoreCase(item.getFrameName())) {
            return item.getFrameName();
        }

        int frameResId = item.getFrameResId();
        if (frameResId != -1) {
            try {
                String entry = getResources().getResourceEntryName(frameResId);
                return entry.replace("frm_", "")
                        .replace("frm3_", "")
                        .replace("frm4_", "")
                        .replace('_', ' ')
                        .trim();
            } catch (Exception ignored) {
            }
        }
        return getString(R.string.history_not_available);
    }

    private void reloadSession(HistorySession item) {
        if (item == null || item.getSelectedImageUris() == null || item.getSelectedImageUris().isEmpty()) {
            Toast.makeText(this, R.string.no_photo_to_continue, Toast.LENGTH_SHORT).show();
            return;
        }

        SessionState state = new SessionState();
        state.setCapturedImageUris(item.getSelectedImageUris());
        if (item.getFrameResId() != -1) {
            state.setSelectedFrameResId(item.getFrameResId());
            getSharedPreferences("PhotoboothPrefs", MODE_PRIVATE)
                    .edit()
                    .putInt("SELECTED_FRAME_ID", item.getFrameResId())
                    .apply();
        }
        sessionRepository.saveSession(state);

        Intent intent = new Intent(this, ResultActivity.class);
        intent.putStringArrayListExtra(IntentKeys.EXTRA_CAPTURED_IMAGES, new ArrayList<>(item.getSelectedImageUris()));
        startActivity(intent);
    }

    private void openRegisterFromGuest() {
        authRepository.signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(IntentKeys.EXTRA_OPEN_REGISTER, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void returnHomeForGuestCancel() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}

