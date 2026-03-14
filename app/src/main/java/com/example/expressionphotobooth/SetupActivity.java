package com.example.expressionphotobooth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.activity.EdgeToEdge;import androidx.appcompat.app.AppCompatActivity;
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

        // Kích hoạt hiển thị tràn viền (EdgeToEdge)
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_setup);

        // Ánh xạ các View từ XML
        toggleGroupQuantity = findViewById(R.id.toggleGroupQuantity);
        btnNext = findViewById(R.id.btnNext);

        // Xử lý khoảng cách thanh hệ thống (StatusBar/NavigationBar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Thiết lập sự kiện khi bấm nút CONTINUE TO CAMERA
        btnNext.setOnClickListener(v -> {
            // Lấy số lượng ảnh được chọn (mặc định là 4 nếu không lấy được)
            int photoCount = getSelectedPhotoCount();

            // Chuyển sang MainActivity (Trang Camera)
            Intent intent = new Intent(SetupActivity.this, MainActivity.class);

            // Truyền số lượng ảnh sang MainActivity qua Intent
            intent.putExtra("PHOTO_COUNT", photoCount);

            startActivity(intent);

            // (Tùy chọn) Nếu bạn không muốn người dùng bấm Back quay lại màn hình Setup
            // finish();
        });
    }

    /**
     * Phương thức phụ trợ để lấy số lượng ảnh dựa trên nút đang được chọn trong ToggleGroup
     */
    private int getSelectedPhotoCount() {
        int checkedId = toggleGroupQuantity.getCheckedButtonId();
        if (checkedId == R.id.btn1) {
            return 1;
        } else if (checkedId == R.id.btn2) {
            return 2;
        } else {
            return 4; // Mặc định là nút btn4
        }
    }
}