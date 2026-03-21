package com.example.expressionphotobooth;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
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

        RecyclerView rvPhotos = findViewById(R.id.rvPhotos);
        MaterialButton btnBack = findViewById(R.id.btnBack);
        MaterialButton btnNextToEdit = findViewById(R.id.btnNextToEdit);
        MaterialButton btnDirectToResult = findViewById(R.id.btnDirectToResult);
        
        btnNextToEdit.setEnabled(false);
        btnDirectToResult.setEnabled(true);

        ArrayList<String> imageUriStrings = getIntent().getStringArrayListExtra(IntentKeys.EXTRA_CAPTURED_IMAGES);
        if (imageUriStrings != null && !imageUriStrings.isEmpty()) {
            sessionState.setCapturedImageUris(imageUriStrings);
            sessionRepository.saveSession(sessionState);
        }

        rvPhotos.setLayoutManager(new GridLayoutManager(this, 2));
        rvPhotos.setHasFixedSize(true);

        adapter = new PhotoAdapter(new ArrayList<>(), (uri, position) -> {
            btnNextToEdit.setEnabled(true);
            btnDirectToResult.setEnabled(true);
        });
        rvPhotos.setAdapter(adapter);
        updateAdapterUris();

        btnNextToEdit.setOnClickListener(v -> {
            Animation press = AnimationUtils.loadAnimation(this, R.anim.btn_press);
            btnNextToEdit.startAnimation(press);
            press.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation animation) {}
                @Override public void onAnimationRepeat(Animation animation) {}
                @Override
                public void onAnimationEnd(Animation animation) {
                    Uri selectedUri = getSelectedOriginalUri();
                    if (selectedUri == null) return;

                    sessionState.setSelectedImageUri(selectedUri.toString());
                    sessionRepository.saveSession(sessionState);

                    Intent intent = new Intent(PhotoSelectionActivity.this, EditPhotoActivity.class);
                    intent.putExtra(IntentKeys.EXTRA_SELECTED_IMAGE, selectedUri.toString());
                    editActivityResultLauncher.launch(intent);
                }
            });
        });

        btnDirectToResult.setOnClickListener(v -> {
            Animation press = AnimationUtils.loadAnimation(this, R.anim.btn_press);
            btnDirectToResult.startAnimation(press);
            press.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation animation) {}
                @Override public void onAnimationRepeat(Animation animation) {}
                @Override
                public void onAnimationEnd(Animation animation) {
                    Intent intent = new Intent(PhotoSelectionActivity.this, ResultActivity.class);
                    startActivity(intent);
                }
            });
        });

        btnBack.setOnClickListener(v -> {
            Animation press = AnimationUtils.loadAnimation(this, R.anim.btn_press);
            btnBack.startAnimation(press);
            press.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation animation) {}
                @Override public void onAnimationRepeat(Animation animation) {}
                @Override
                public void onAnimationEnd(Animation animation) {
                    finish();
                }
            });
        });
    }

    private void updateAdapterUris() {
        List<Uri> displayUris = new ArrayList<>();
        for (String originalUriStr : sessionState.getCapturedImageUris()) {
            String editedUriStr = sessionState.getEditedImageUris().get(originalUriStr);
            displayUris.add(Uri.parse(editedUriStr != null ? editedUriStr : originalUriStr));
        }
        
        adapter = new PhotoAdapter(displayUris, (uri, position) -> {
            findViewById(R.id.btnNextToEdit).setEnabled(true);
            findViewById(R.id.btnDirectToResult).setEnabled(true);
        });
        ((RecyclerView)findViewById(R.id.rvPhotos)).setAdapter(adapter);
    }

    private Uri getSelectedOriginalUri() {
        Uri currentDisplayUri = adapter.getSelectedUri();
        if (currentDisplayUri == null) return null;

        List<String> captured = sessionState.getCapturedImageUris();
        for (String orig : captured) {
            String edit = sessionState.getEditedImageUris().get(orig);
            if (currentDisplayUri.toString().equals(orig) || currentDisplayUri.toString().equals(edit)) {
                return Uri.parse(orig);
            }
        }
        return null;
    }
}
