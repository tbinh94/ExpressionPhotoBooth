package com.example.expressionphotobooth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

public class SetupActivity extends AppCompatActivity {

    private MaterialButtonToggleGroup toggleGroupQuantity;
    private MaterialButton btnNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_setup);

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

            Intent intent = new Intent(SetupActivity.this, MainActivity.class);
            intent.putExtra("PHOTO_COUNT", photoCount);
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
}