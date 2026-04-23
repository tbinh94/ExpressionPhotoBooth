package com.example.expressionphotobooth;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

import androidx.appcompat.app.AppCompatActivity;

import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.example.expressionphotobooth.utils.LocaleManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiTriggerSettingsActivity extends AppCompatActivity {

    private SessionRepository sessionRepository;
    private SessionState sessionState;

    private final Map<Integer, String> handChipMap = new HashMap<>();
    private final Map<Integer, String> faceChipMap = new HashMap<>();

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_trigger_settings);

        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();
        sessionState = sessionRepository.getSession();

        initMaps();
        setupToolbar();
        loadSettings();

        findViewById(R.id.btnSave).setOnClickListener(v -> saveSettings());

        View btnDevSkip = findViewById(R.id.btnDevSkip);
        if (btnDevSkip != null) {
            btnDevSkip.setOnClickListener(v -> skipToPhotoSelection());
        }
    }

    private void initMaps() {
        handChipMap.put(R.id.chipHandHi, "HI");
        handChipMap.put(R.id.chipHandHeart, "HEART");
        handChipMap.put(R.id.chipHandThumbsUp, "THUMBS_UP");
        handChipMap.put(R.id.chipHandOpenPalm, "OPEN_PALM");
        handChipMap.put(R.id.chipHandFist, "FIST");
        handChipMap.put(R.id.chipHandOk, "OK_SIGN");

        faceChipMap.put(R.id.chipFaceCentered, "CENTERED");
        faceChipMap.put(R.id.chipFaceSmile, "SMILE");
        faceChipMap.put(R.id.chipFaceMouthOpen, "MOUTH_OPEN");
        faceChipMap.put(R.id.chipFaceWink, "WINK");
        faceChipMap.put(R.id.chipFaceTilt, "TILT_RIGHT"); // TILT_LEFT will be implied
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadSettings() {
        List<String> enabledHand = sessionState.getEnabledHandGestures();
        for (Map.Entry<Integer, String> entry : handChipMap.entrySet()) {
            Chip chip = findViewById(entry.getKey());
            if (chip != null) {
                chip.setChecked(enabledHand.contains(entry.getValue()));
            }
        }

        List<String> enabledFace = sessionState.getEnabledFaceExpressions();
        for (Map.Entry<Integer, String> entry : faceChipMap.entrySet()) {
            Chip chip = findViewById(entry.getKey());
            if (chip != null) {
                // For tilt, we check TILT_RIGHT or TILT_LEFT
                if (entry.getValue().equals("TILT_RIGHT")) {
                    chip.setChecked(enabledFace.contains("TILT_RIGHT") || enabledFace.contains("TILT_LEFT"));
                } else {
                    chip.setChecked(enabledFace.contains(entry.getValue()));
                }
            }
        }
    }

    private void saveSettings() {
        List<String> newHand = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : handChipMap.entrySet()) {
            Chip chip = findViewById(entry.getKey());
            if (chip != null && chip.isChecked()) {
                newHand.add(entry.getValue());
            }
        }

        List<String> newFace = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : faceChipMap.entrySet()) {
            Chip chip = findViewById(entry.getKey());
            if (chip != null && chip.isChecked()) {
                if (entry.getValue().equals("TILT_RIGHT")) {
                    newFace.add("TILT_RIGHT");
                    newFace.add("TILT_LEFT");
                } else {
                    newFace.add(entry.getValue());
                }
            }
        }

        if (newHand.isEmpty()) {
            Toast.makeText(this, R.string.ai_settings_select_hand_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (newFace.isEmpty()) {
            Toast.makeText(this, R.string.ai_settings_select_face_required, Toast.LENGTH_SHORT).show();
            return;
        }

        sessionState.setEnabledHandGestures(newHand);
        sessionState.setEnabledFaceExpressions(newFace);
        sessionRepository.saveSession(sessionState);

        Toast.makeText(this, R.string.ai_settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void skipToPhotoSelection() {
        ArrayList<String> uriStrings = new ArrayList<>();
        // Generate 6 blank white images to simulate a full capture sequence
        for (int i = 0; i < 6; i++) {
            File file = new File(getExternalFilesDir(null), "dev_blank_" + i + "_" + System.currentTimeMillis() + ".jpg");
            try {
                Bitmap bitmap = Bitmap.createBitmap(1200, 1200, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.BLACK);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                }
                bitmap.recycle();
                uriStrings.add(Uri.fromFile(file).toString());
            } catch (Exception e) {
                Log.e("AiTriggerSettings", "Failed to create dev blank image", e);
            }
        }

        Intent intent = new Intent(this, PhotoSelectionActivity.class);
        intent.putStringArrayListExtra(IntentKeys.EXTRA_CAPTURED_IMAGES, uriStrings);
        startActivity(intent);
        finish();
    }
}
