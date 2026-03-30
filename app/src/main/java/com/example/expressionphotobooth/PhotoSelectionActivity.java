package com.example.expressionphotobooth;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public class PhotoSelectionActivity extends AppCompatActivity {
    private static final int REQUIRED_SELECTION_COUNT = 4;
    private PhotoAdapter adapter;
    private SessionRepository sessionRepository;
    private SessionState sessionState;
    private MaterialButton btnContinueToEdit;
    private MaterialButton btnDirectToResult;
    private TextView tvSelectionStatus;
    private TextView tvClearSelection;
    private SelectedPhotoPreviewAdapter selectedPhotoPreviewAdapter;
    private final ArrayDeque<String> pendingEditOriginalUris = new ArrayDeque<>();
    private boolean isBatchEditing = false;

    private final ActivityResultLauncher<Intent> editActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                boolean editSaved = false;
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String originalUri = result.getData().getStringExtra(IntentKeys.EXTRA_ORIGINAL_URI);
                    String editedUri = result.getData().getStringExtra(IntentKeys.EXTRA_EDITED_URI);
                    if (originalUri != null && editedUri != null) {
                        sessionState.getEditedImageUris().put(originalUri, editedUri);
                        sessionRepository.saveSession(sessionState);
                        editSaved = true;
                    }
                }

                if (isBatchEditing && !editSaved) {
                    // User backed out/canceled; stop queue gracefully without false success toast.
                    isBatchEditing = false;
                    pendingEditOriginalUris.clear();
                    updateAdapterUris();
                    Toast.makeText(this, R.string.batch_edit_cancelled, Toast.LENGTH_SHORT).show();
                    return;
                }

                if (isBatchEditing && !pendingEditOriginalUris.isEmpty()) {
                    launchNextEditInQueue();
                } else {
                    // Cập nhật lại danh sách hiển thị
                    isBatchEditing = false;
                    pendingEditOriginalUris.clear();
                    updateAdapterUris();
                    
                    // KHÔNG gọi clearSelection và updateSelectionStatus(0) ở đây
                    // Để giữ lại 4 ảnh đã chọn (giờ đã được cập nhật bản edit mới)
                    // PhotoSelectionActivity.java:71-76 removed.

                    if (editSaved) {
                        Toast.makeText(this, R.string.batch_edit_completed, Toast.LENGTH_SHORT).show();
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
        btnContinueToEdit = findViewById(R.id.btnNextToEdit);
        btnDirectToResult = findViewById(R.id.btnDirectToResult);
        MaterialButton btnHelpSelection = findViewById(R.id.btnHelpSelection);
        tvSelectionStatus = findViewById(R.id.tvSelectionStatus);
        tvClearSelection = findViewById(R.id.tvClearSelection);
        RecyclerView rvSelectedPhotos = findViewById(R.id.rvSelectedPhotos);
        btnHelpSelection.setOnClickListener(v -> showSelectionHelpDialog());

        selectedPhotoPreviewAdapter = new SelectedPhotoPreviewAdapter();
        rvSelectedPhotos.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        rvSelectedPhotos.setAdapter(selectedPhotoPreviewAdapter);
        
        btnContinueToEdit.setEnabled(false);
        btnDirectToResult.setEnabled(false);

        ArrayList<String> imageUriStrings = getIntent().getStringArrayListExtra(IntentKeys.EXTRA_CAPTURED_IMAGES);
        if (imageUriStrings != null && !imageUriStrings.isEmpty()) {
            sessionState.setCapturedImageUris(imageUriStrings);
            sessionRepository.saveSession(sessionState);
        }

        rvPhotos.setLayoutManager(new GridLayoutManager(this, 2));
        rvPhotos.setHasFixedSize(true);

        adapter = new PhotoAdapter(new ArrayList<>(), REQUIRED_SELECTION_COUNT, (selectedUris, selectedCount) -> {
            btnContinueToEdit.setEnabled(selectedCount > 0);
            updateSelectionStatus(selectedCount);
            updateResultButtonState(selectedCount);
            updateSelectedPreviewStrip(selectedUris);
            tvClearSelection.setEnabled(selectedCount > 0);
            tvClearSelection.setAlpha(selectedCount > 0 ? 1f : 0.45f);
        });
        rvPhotos.setAdapter(adapter);
        updateAdapterUris();

        tvClearSelection.setOnClickListener(v -> {
            if (adapter == null) {
                return;
            }
            adapter.clearSelection();
            updateSelectionStatus(0);
            updateSelectedPreviewStrip(new ArrayList<>());
            btnContinueToEdit.setEnabled(false);
            btnContinueToEdit.setText(R.string.btn_edit);
            updateResultButtonState(0);
            tvClearSelection.setEnabled(false);
            tvClearSelection.setAlpha(0.45f);
        });
        tvClearSelection.setEnabled(false);
        tvClearSelection.setAlpha(0.45f);

        btnContinueToEdit.setOnClickListener(v -> {
            List<Uri> selectedDisplayUris = adapter.getSelectedUris();
            if (selectedDisplayUris.isEmpty()) {
                return;
            }

            pendingEditOriginalUris.clear();
            for (Uri selectedDisplayUri : selectedDisplayUris) {
                Uri originalUri = getSelectedOriginalUri(selectedDisplayUri);
                if (originalUri != null) {
                    pendingEditOriginalUris.add(originalUri.toString());
                }
            }

            if (pendingEditOriginalUris.isEmpty()) {
                return;
            }

            isBatchEditing = true;
            if (pendingEditOriginalUris.size() > 1) {
                Toast.makeText(this, getString(R.string.batch_edit_start, pendingEditOriginalUris.size()), Toast.LENGTH_SHORT).show();
            }
            launchNextEditInQueue();
        });

        btnDirectToResult.setOnClickListener(v -> {
            List<Uri> selectedDisplayUris = adapter.getSelectedUris();
            int required = getRequiredSelectionCount();
            if (selectedDisplayUris.size() != required) {
                Toast.makeText(this, getString(R.string.select_enough_for_result, required), Toast.LENGTH_SHORT).show();
                return;
            }

            LinkedHashSet<String> selectedOriginalUris = new LinkedHashSet<>();
            for (Uri selectedDisplayUri : selectedDisplayUris) {
                Uri originalUri = getSelectedOriginalUri(selectedDisplayUri);
                if (originalUri != null) {
                    selectedOriginalUris.add(originalUri.toString());
                }
            }

            if (selectedOriginalUris.size() != required) {
                Toast.makeText(this, getString(R.string.select_enough_for_result, required), Toast.LENGTH_SHORT).show();
                return;
            }

            // Chi lay nhom da chon de tao ket qua cuoi.
            ArrayList<String> finalImagesToStitch = new ArrayList<>();

            for (String originalUriStr : selectedOriginalUris) {
                String editedUriStr = sessionState.getEditedImageUris().get(originalUriStr);
                finalImagesToStitch.add(editedUriStr != null ? editedUriStr : originalUriStr);
            }

            if (finalImagesToStitch.isEmpty()) return;

            Intent intent = new Intent(PhotoSelectionActivity.this, ResultActivity.class);
            intent.putStringArrayListExtra(IntentKeys.EXTRA_CAPTURED_IMAGES, finalImagesToStitch);

            startActivity(intent);
        });
    }

    private void updateAdapterUris() {
        List<Uri> displayUris = new ArrayList<>();
        for (String originalUriStr : sessionState.getCapturedImageUris()) {
            String editedUriStr = sessionState.getEditedImageUris().get(originalUriStr);
            displayUris.add(Uri.parse(editedUriStr != null ? editedUriStr : originalUriStr));
        }

        if (adapter != null) {
            adapter.setUris(displayUris);
        }
        btnContinueToEdit.setEnabled(false);
        updateSelectionStatus(0);
        updateResultButtonState(0);
        updateSelectedPreviewStrip(new ArrayList<>());
    }

    private void updateSelectionStatus(int selectedCount) {
        int required = getRequiredSelectionCount();
        if (selectedCount <= 0) {
            tvSelectionStatus.setText(getString(R.string.selection_status_none, required));
            btnContinueToEdit.setText(R.string.btn_edit);
        } else {
            tvSelectionStatus.setText(getString(R.string.selection_status_selected, selectedCount, required));
            btnContinueToEdit.setText(getString(R.string.btn_edit_with_count, selectedCount));
        }
    }

    private void updateResultButtonState(int selectedCount) {
        int required = getRequiredSelectionCount();
        boolean canExport = required > 0 && selectedCount == required;
        btnDirectToResult.setEnabled(canExport);
        btnDirectToResult.setText(getString(R.string.btn_result_with_count, selectedCount, required));
    }

    private int getRequiredSelectionCount() {
        int capturedCount = sessionState.getCapturedImageUris().size();
        if (capturedCount <= 0) {
            return 0;
        }
        return Math.min(REQUIRED_SELECTION_COUNT, capturedCount);
    }

    private Uri getSelectedOriginalUri(Uri currentDisplayUri) {
        if (currentDisplayUri == null) {
            return null;
        }

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

    private void launchNextEditInQueue() {
        String nextOriginalUri = pendingEditOriginalUris.pollFirst();
        if (nextOriginalUri == null) {
            isBatchEditing = false;
            updateAdapterUris();
            return;
        }

        sessionState.setSelectedImageUri(nextOriginalUri);
        sessionRepository.saveSession(sessionState);

        Intent intent = new Intent(PhotoSelectionActivity.this, EditPhotoActivity.class);
        intent.putExtra(IntentKeys.EXTRA_SELECTED_IMAGE, nextOriginalUri);
        editActivityResultLauncher.launch(intent);
    }

    private void updateSelectedPreviewStrip(List<Uri> selectedUris) {
        selectedPhotoPreviewAdapter.setSelectedUris(selectedUris);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    private void showSelectionHelpDialog() {
        int requiredCount = getRequiredSelectionCount();
        HelpDialogUtils.showPhotoboothHelp(
                this,
                getString(R.string.help_selection_title),
                getString(R.string.help_selection_subtitle),
                Arrays.asList(
                        getString(R.string.help_selection_bullet_1, requiredCount),
                        getString(R.string.help_selection_bullet_2),
                        getString(R.string.help_selection_bullet_3, requiredCount)
                )
        );
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
