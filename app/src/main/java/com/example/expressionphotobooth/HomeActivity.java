package com.example.expressionphotobooth;

import android.content.Intent;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class HomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        MaterialButton btnStart = findViewById(R.id.btnStart);
        btnStart.setOnClickListener(v -> {
            // Chạy hiệu ứng nhấn nút
            Animation press = AnimationUtils.loadAnimation(this, R.anim.btn_press);
            btnStart.startAnimation(press);

            // Đợi hiệu ứng chạy xong rồi mới chuyển màn hình (tùy chọn)
            press.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    // Chuyển sang SetupActivity (hoặc MainActivity tùy bạn)
                    Intent intent = new Intent(HomeActivity.this, SetupActivity.class);
                    startActivity(intent);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
        });
    }
}
