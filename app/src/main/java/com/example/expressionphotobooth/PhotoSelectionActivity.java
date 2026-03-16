package com.example.expressionphotobooth;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;

public class PhotoSelectionActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_selection);

        RecyclerView rvPhotos = findViewById(R.id.rvPhotos);
        MaterialButton btnContinue = findViewById(R.id.btnNextToEdit); // Kiểm tra ID nút của bạn

        // 1. Nhận danh sách ảnh từ MainActivity
        ArrayList<String> imageUriStrings = getIntent().getStringArrayListExtra("captured_images");

        if (imageUriStrings != null) {
            // 2. Hiển thị danh sách ảnh (Không còn dòng code cập nhật tvSelectionCount)
            rvPhotos.setLayoutManager(new GridLayoutManager(this, 2));

            ArrayList<Uri> uriList = new ArrayList<>();
            for (String s : imageUriStrings) uriList.add(Uri.parse(s));

            PhotoAdapter adapter = new PhotoAdapter(this, uriList);
            rvPhotos.setAdapter(adapter);
        }

        // 3. Logic chuyển sang EditPhotoActivity
        btnContinue.setOnClickListener(v -> {
            Intent intent = new Intent(PhotoSelectionActivity.this, EditPhotoActivity.class);

            // Truyền tiếp danh sách ảnh sang trang Edit để xử lý
            intent.putStringArrayListExtra("captured_images", imageUriStrings);

            startActivity(intent);
        });
    }
}