package com.example.expressionphotobooth;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;

public class EditPhotoActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_photo);

        // Nhận danh sách ảnh từ trang Selection
        ArrayList<String> imagesToEdit = getIntent().getStringArrayListExtra("captured_images");

        MaterialButton btnFinish = findViewById(R.id.btnFinishEdit);
        btnFinish.setOnClickListener(v -> {
            Intent intent = new Intent(EditPhotoActivity.this, ResultActivity.class);

            // TRUYỀN TIẾP danh sách ảnh sang trang Result để hiển thị/lưu
            intent.putStringArrayListExtra("captured_images", imagesToEdit);

            startActivity(intent);
        });
    }
}