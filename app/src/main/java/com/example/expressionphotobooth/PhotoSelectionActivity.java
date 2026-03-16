package com.example.expressionphotobooth;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;

public class PhotoSelectionActivity extends AppCompatActivity {
    private PhotoAdapter adapter;
    private SessionRepository sessionRepository;
    private SessionState sessionState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_selection);
        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();
        sessionState = sessionRepository.getSession();
        setupToolbar();

        RecyclerView rvPhotos = findViewById(R.id.rvPhotos);
        MaterialButton btnContinue = findViewById(R.id.btnNextToEdit);
        btnContinue.setEnabled(false);

        // Uu tien danh sach tu Intent; fallback ve repository de phuc hoi khi recreate.
        ArrayList<String> imageUriStrings = getIntent().getStringArrayListExtra(IntentKeys.EXTRA_CAPTURED_IMAGES);
        if (imageUriStrings == null || imageUriStrings.isEmpty()) {
            imageUriStrings = new ArrayList<>(sessionState.getCapturedImageUris());
        }
        // Giu mot bien final de dung an toan trong lambda.
        final ArrayList<String> finalImageUriStrings = imageUriStrings == null ? new ArrayList<>() : imageUriStrings;
        sessionState.setCapturedImageUris(finalImageUriStrings);
        sessionRepository.saveSession(sessionState);
        ArrayList<Uri> uriList = new ArrayList<>();

        if (!finalImageUriStrings.isEmpty()) {
            for (String s : finalImageUriStrings) {
                uriList.add(Uri.parse(s));
            }
        }

        rvPhotos.setLayoutManager(new GridLayoutManager(this, 2));
        rvPhotos.setHasFixedSize(true);

        adapter = new PhotoAdapter(uriList, (uri, position) -> btnContinue.setEnabled(true));
        rvPhotos.setAdapter(adapter);

        if (uriList.isEmpty()) {
            btnContinue.setEnabled(false);
            btnContinue.setText(R.string.no_photo_to_continue);
        }

        btnContinue.setOnClickListener(v -> {
            Uri selectedUri = adapter.getSelectedUri();
            if (selectedUri == null) {
                return;
            }

            // Luu lua chon "best shot" vao session.
            sessionState.setSelectedImageUri(selectedUri.toString());
            sessionRepository.saveSession(sessionState);

            Intent intent = new Intent(PhotoSelectionActivity.this, EditPhotoActivity.class);
            intent.putStringArrayListExtra(IntentKeys.EXTRA_CAPTURED_IMAGES, finalImageUriStrings);
            intent.putExtra(IntentKeys.EXTRA_SELECTED_IMAGE, selectedUri.toString());

            startActivity(intent);
        });
    }

    // Su dung toolbar Up thay cho nut custom de dong bo UI.
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