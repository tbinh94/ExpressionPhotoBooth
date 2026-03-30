package com.example.expressionphotobooth;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.example.expressionphotobooth.utils.LocaleManager;
import com.google.android.material.button.MaterialButton;

public class HomeActivity extends AppCompatActivity {
    private MediaPlayer mediaPlayer;
    private static boolean isMuted = false;
    private AuthRepository authRepository;
    private MaterialButton btnLanguageToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        authRepository = ((AppContainer) getApplication()).getAuthRepository();
        if (!authRepository.isLoggedIn()) {
            startActivity(new Intent(HomeActivity.this, LoginActivity.class));
            finish();
            return;
        }

        // Phát nhạc nền
        startBackgroundMusic();

        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            authRepository.signOut();
            Intent loginIntent = new Intent(HomeActivity.this, LoginActivity.class);
            loginIntent.putExtra(IntentKeys.EXTRA_NOTICE_TITLE, getString(R.string.auth_sign_out_title));
            loginIntent.putExtra(IntentKeys.EXTRA_NOTICE_MESSAGE, getString(R.string.auth_sign_out_message));
            startActivity(loginIntent);
            finish();
        });

        MaterialButton btnStart = findViewById(R.id.btnStart);
        btnStart.setOnClickListener(v -> {
            Animation press = AnimationUtils.loadAnimation(this, R.anim.btn_press);
            btnStart.startAnimation(press);

            press.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    Intent intent = new Intent(HomeActivity.this, SetupActivity.class);
                    startActivity(intent);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
        });

        MaterialButton btnVolumeToggle = findViewById(R.id.btnVolumeToggle);
        updateVolumeIcon(btnVolumeToggle);

        btnLanguageToggle = findViewById(R.id.btnLanguageToggle);
        updateLanguageButtonText();
        btnLanguageToggle.setOnClickListener(v -> {
            LocaleManager.toggleLanguage(this);
            recreate();
        });

        btnVolumeToggle.setOnClickListener(v -> {
            isMuted = !isMuted;
            if (isMuted) {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }
            } else {
                if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                }
            }
            updateVolumeIcon(btnVolumeToggle);
        });
    }

    private void updateVolumeIcon(MaterialButton button) {
        button.setIconResource(isMuted ? R.drawable.ic_volume_off : R.drawable.ic_volume_up);
    }

    private void updateLanguageButtonText() {
        if (btnLanguageToggle == null) {
            return;
        }
        int textRes = LocaleManager.isVietnamese()
                ? R.string.home_switch_to_english
                : R.string.home_switch_to_vietnamese;
        btnLanguageToggle.setText(textRes);
    }

    private void startBackgroundMusic() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.gentle_bgm);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
                mediaPlayer.setVolume(0.5f, 0.5f);
            }
        }
        
        if (mediaPlayer != null && !isMuted && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }
//DucNT183-26.03.2026
    @Override
    protected void onResume() {
        super.onResume();
        if (mediaPlayer != null && !mediaPlayer.isPlaying() && !isMuted) {
            mediaPlayer.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
