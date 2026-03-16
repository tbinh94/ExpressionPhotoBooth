package com.example.expressionphotobooth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.expressionphotobooth.domain.model.EditState;
import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

public class SetupActivity extends AppCompatActivity {

    private MaterialButtonToggleGroup toggleGroupQuantity;
    private MaterialButton btnNext;
    private SessionRepository sessionRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_setup);
        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();
        setupToolbar();

        // Ánh xạ
        toggleGroupQuantity = findViewById(R.id.toggleGroupQuantity);
        btnNext = findViewById(R.id.btnNext);

        // Xử lý System Bars
        View mainLayout = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Click Event
        btnNext.setOnClickListener(v -> {
            int photoCount = getSelectedPhotoCount();

            // Tao moi session cho luot chup hien tai de tranh du lieu cu bi tron.
            SessionState session = new SessionState();
            session.setPhotoCount(photoCount);
            session.setEditState(new EditState());
            sessionRepository.saveSession(session);

            Intent intent = new Intent(SetupActivity.this, MainActivity.class);
            intent.putExtra(IntentKeys.EXTRA_PHOTO_COUNT, photoCount);
            startActivity(intent);
        });
    }

    private int getSelectedPhotoCount() {
        int checkedId = toggleGroupQuantity.getCheckedButtonId();
        if (checkedId == R.id.btn1) {
            return 1;
        } else if (checkedId == R.id.btn2) {
            return 2;
        } else {
            return 4; // Mặc định là btn4
        }
    }

    // Dung toolbar co nut Up theo chuan Android de quay lai man truoc.
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