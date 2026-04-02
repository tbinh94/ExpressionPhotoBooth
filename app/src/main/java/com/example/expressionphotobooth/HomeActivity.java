package com.example.expressionphotobooth;

import android.content.Intent;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.example.expressionphotobooth.domain.model.UserRole;
import com.example.expressionphotobooth.utils.LocaleManager;
import com.google.android.material.button.MaterialButton;

public class HomeActivity extends AppCompatActivity {
    private MediaPlayer mediaPlayer;
    private static boolean isMuted = false;
    private AuthRepository authRepository;
    private MaterialButton btnLanguageToggle;
    private MaterialButton btnAdmin;
    private MaterialButton btnStart;
    private MaterialButton btnLogout;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.launcher_splash_bg)));
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        overridePendingTransition(0, 0);

        authRepository = ((AppContainer) getApplication()).getAuthRepository();
        if (!authRepository.isLoggedIn()) {
            startActivity(new Intent(HomeActivity.this, LoginActivity.class));
            finish();
            return;
        }

        // Phát nhạc nền
        startBackgroundMusic();

        btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> handleSignOut());

        btnAdmin = findViewById(R.id.btnAdmin);
        checkAdminAccess();

        btnStart = findViewById(R.id.btnStart);
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
            String lang = LocaleManager.toggleLanguageWithoutRecreate(this);
            updateLocalizedUi(lang);
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
        int textRes = LocaleManager.isVietnamese(this)
                ? R.string.home_switch_to_english
                : R.string.home_switch_to_vietnamese;
        btnLanguageToggle.setText(textRes);
    }

    private void updateLocalizedUi(String languageTag) {
        btnLanguageToggle.setText(LocaleManager.getString(this,
                LocaleManager.LANG_VI.equals(languageTag)
                        ? R.string.home_switch_to_english
                        : R.string.home_switch_to_vietnamese,
                languageTag));
        if (btnStart != null) {
            btnStart.setText(LocaleManager.getString(this, R.string.btn_start_decorated, languageTag));
        }
        if (btnLogout != null) {
            btnLogout.setText(LocaleManager.getString(this, R.string.auth_sign_out, languageTag));
        }
        if (btnAdmin != null) {
            btnAdmin.setText(LocaleManager.getString(this, R.string.admin_go_to_dashboard, languageTag));
        }
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

    private void checkAdminAccess() {
        authRepository.fetchCurrentRole(new AuthRepository.RoleCallback() {
            @Override
            public void onSuccess(UserRole role) {
                if (role == UserRole.ADMIN) {
                    btnAdmin.setVisibility(android.view.View.VISIBLE);
                    btnAdmin.setOnClickListener(v -> {
                        startActivity(new Intent(HomeActivity.this, AdminDashboardActivity.class));
                        finish();
                    });
                }
            }
            @Override
            public void onError(String message) {}
        });
    }

    private void handleSignOut() {
        authRepository.signOut();
        Intent loginIntent = new Intent(HomeActivity.this, LoginActivity.class);
        loginIntent.putExtra(IntentKeys.EXTRA_NOTICE_TITLE, getString(R.string.auth_sign_out_title));
        loginIntent.putExtra(IntentKeys.EXTRA_NOTICE_MESSAGE, getString(R.string.auth_sign_out_message));
        startActivity(loginIntent);
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }
}
