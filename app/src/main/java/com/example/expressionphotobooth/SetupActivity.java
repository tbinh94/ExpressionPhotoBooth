package com.example.expressionphotobooth;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class SetupActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_setup);

        MaterialButton btnNext = findViewById(R.id.btnNext);
        btnNext.setOnClickListener(v -> {
            // Ở đây bạn có thể truyền thêm dữ liệu frame và số lượng ảnh qua Intent
            Intent intent = new Intent(SetupActivity.this, MainActivity.class);
            startActivity(intent);
        });
    }
}