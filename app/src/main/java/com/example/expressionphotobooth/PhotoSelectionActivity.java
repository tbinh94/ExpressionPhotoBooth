package com.example.expressionphotobooth;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class PhotoSelectionActivity extends AppCompatActivity {
    private PhotoAdapter adapter;
    private SessionRepository sessionRepository;
    private SessionState sessionState;

    private final ActivityResultLauncher<Intent> editActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String originalUri = result.getData().getStringExtra(IntentKeys.EXTRA_ORIGINAL_URI);
                    String editedUri = result.getData().getStringExtra(IntentKeys.EXTRA_EDITED_URI);
                    if (originalUri != null && editedUri != null) {
                        sessionState.getEditedImageUris().put(originalUri, editedUri);
                        sessionRepository.saveSession(sessionState);
                        updateAdapterUris();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_selection);
        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();
        sessionState = sessionRepository.getSession();
        setupToolbar();

        RecyclerView rvPhotos = findViewById(R.id.rvPhotos);
        MaterialButton btnContinueToEdit = findViewById(R.id.btnNextToEdit);
        MaterialButton btnDirectToResult = findViewById(R.id.btnDirectToResult);
        
        btnContinueToEdit.setEnabled(false);
        btnDirectToResult.setEnabled(true);

        ArrayList<String> imageUriStrings = getIntent().getStringArrayListExtra(IntentKeys.EXTRA_CAPTURED_IMAGES);
        if (imageUriStrings != null && !imageUriStrings.isEmpty()) {
            sessionState.setCapturedImageUris(imageUriStrings);
            sessionRepository.saveSession(sessionState);
        }

        rvPhotos.setLayoutManager(new GridLayoutManager(this, 2));
        rvPhotos.setHasFixedSize(true);

        adapter = new PhotoAdapter(new ArrayList<>(), (uri, position) -> {
            btnContinueToEdit.setEnabled(true);
            btnDirectToResult.setEnabled(true);
        });
        rvPhotos.setAdapter(adapter);
        updateAdapterUris();

        btnContinueToEdit.setOnClickListener(v -> {
            Uri selectedUri = getSelectedOriginalUri();
            if (selectedUri == null) return;

            sessionState.setSelectedImageUri(selectedUri.toString());
            sessionRepository.saveSession(sessionState);

            Intent intent = new Intent(PhotoSelectionActivity.this, EditPhotoActivity.class);
            intent.putExtra(IntentKeys.EXTRA_SELECTED_IMAGE, selectedUri.toString());
            editActivityResultLauncher.launch(intent);
        });

        btnDirectToResult.setOnClickListener(v -> {
            // 1. Tạo danh sách tất cả ảnh cuối cùng (ưu tiên ảnh đã edit)
            ArrayList<String> finalImagesToStitch = new ArrayList<>();

            for (String originalUriStr : sessionState.getCapturedImageUris()) {
                String editedUriStr = sessionState.getEditedImageUris().get(originalUriStr);
                // Nếu đã edit thì lấy bản edit, chưa edit thì lấy bản gốc
                finalImagesToStitch.add(editedUriStr != null ? editedUriStr : originalUriStr);
            }

            if (finalImagesToStitch.isEmpty()) return;

            // 2. Lưu trạng thái nếu cần (tùy thuộc vào logic ResultActivity của bạn)
            // Ở đây ta truyền toàn bộ list ảnh vào intent
            Intent intent = new Intent(PhotoSelectionActivity.this, ResultActivity.class);

            startActivity(intent);
        });
    }

    private void updateAdapterUris() {
        List<Uri> displayUris = new ArrayList<>();
        for (String originalUriStr : sessionState.getCapturedImageUris()) {
            String editedUriStr = sessionState.getEditedImageUris().get(originalUriStr);
            displayUris.add(Uri.parse(editedUriStr != null ? editedUriStr : originalUriStr));
        }
        
        // Cập nhật list trong adapter (Cần thêm method setUris vào PhotoAdapter hoặc tạo mới)
        // Ở đây tôi sẽ tạo adapter mới để đơn giản hóa việc refresh UI
        int selectedPos = -1; // Reset selection hoặc giữ lại nếu cần
        adapter = new PhotoAdapter(displayUris, (uri, position) -> {
            findViewById(R.id.btnNextToEdit).setEnabled(true);
            findViewById(R.id.btnDirectToResult).setEnabled(true);
        });
        ((RecyclerView)findViewById(R.id.rvPhotos)).setAdapter(adapter);
    }

    private Uri getSelectedOriginalUri() {
        int pos = -1;
        // Mocking logic to get position since PhotoAdapter doesn't expose it easily
        // In real app, better to have adapter.getSelectedPosition()
        Uri currentDisplayUri = adapter.getSelectedUri();
        if (currentDisplayUri == null) return null;

        List<String> captured = sessionState.getCapturedImageUris();
        for (int i = 0; i < captured.size(); i++) {
            String orig = captured.get(i);
            String edit = sessionState.getEditedImageUris().get(orig);
            if (currentDisplayUri.toString().equals(orig) || currentDisplayUri.toString().equals(edit)) {
                return Uri.parse(orig);
            }
        }
        return null;
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
