package com.example.expressionphotobooth;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class PhotoSelectionActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_photo_selection);

        MaterialButton btnNext = findViewById(R.id.btnNextToEdit);
        btnNext.setOnClickListener(v -> {
            Intent intent = new Intent(PhotoSelectionActivity.this, EditPhotoActivity.class);
            startActivity(intent);
        });
    }
}