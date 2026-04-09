package com.example.expressionphotobooth;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.example.expressionphotobooth.domain.model.UserRole;
import com.example.expressionphotobooth.utils.LocaleManager;
import com.example.expressionphotobooth.utils.ThemeManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.bumptech.glide.Glide;
import android.net.Uri;
import android.widget.ImageView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import android.widget.Toast;

public class HomeActivity extends AppCompatActivity {
    private static final String PREF_HOME = "home_preferences";
    private static final String KEY_MUSIC_ENABLED = "music_enabled";
    private static final String KEY_BANNER_URI = "banner_uri";

    private MediaPlayer mediaPlayer;
    private static boolean isMuted = false;
    private AuthRepository authRepository;
    private DrawerLayout drawerLayout;
    private MaterialButton btnMenu;
    private MaterialButton btnDrawerSignOut;
    private MaterialButton btnAdmin;
    private MaterialButton btnStart;
    private MaterialButton btnGallery;
    private View homeContentRoot;
    private View topBar;
    private View drawerPanel;
    private TextView tvDrawerOur;
    private TextView tvDrawerMemories;
    private TextView tvDrawerShowHistory;
    private TextView tvDrawerAdminDashboard;
    private TextView tvDrawerMusicLabel;
    private TextView tvDrawerLanguageLabel;
    private TextView tvDrawerThemeLabel;
    private MaterialButtonToggleGroup groupMusicState;
    private MaterialButtonToggleGroup groupLanguage;
    private MaterialButtonToggleGroup groupTheme;
    private MaterialButton btnMusicOn;
    private MaterialButton btnMusicOff;
    private MaterialButton btnLangVi;
    private MaterialButton btnLangEn;
    private MaterialButton btnThemeLight;
    private MaterialButton btnThemeDark;

    private ImageView ivHomeBanner;
    private TextView tvDrawerChangeBanner;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;

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

        bindViews();
        applySystemInsets();
        setupDrawerActions();
        setupMainActions();

        startBackgroundMusic();
        applyMusicPreference();
        setupMusicControls();
        setupLanguageControls();
        setupThemeControls();
        updateLocalizedUi(LocaleManager.getCurrentLanguage(this));
        checkAdminAccess();
        setupBannerPicker();
        loadSavedBanner();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }

    private void bindViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        btnMenu = findViewById(R.id.btnMenu);
        homeContentRoot = findViewById(R.id.homeContentRoot);
        topBar = findViewById(R.id.topBar);
        drawerPanel = findViewById(R.id.drawerPanel);
        btnDrawerSignOut = findViewById(R.id.btnDrawerSignOut);
        btnAdmin = findViewById(R.id.btnAdmin);
        btnStart = findViewById(R.id.btnStart);
        btnGallery = findViewById(R.id.btnGallery);

        tvDrawerOur = findViewById(R.id.tvDrawerOur);
        tvDrawerMemories = findViewById(R.id.tvDrawerMemories);
        tvDrawerShowHistory = findViewById(R.id.tvDrawerShowHistory);
        tvDrawerAdminDashboard = findViewById(R.id.tvDrawerAdminDashboard);
        tvDrawerMusicLabel = findViewById(R.id.tvDrawerMusicLabel);
        tvDrawerLanguageLabel = findViewById(R.id.tvDrawerLanguageLabel);
        tvDrawerThemeLabel = findViewById(R.id.tvDrawerThemeLabel);
        groupMusicState = findViewById(R.id.groupMusicState);
        groupLanguage = findViewById(R.id.groupLanguage);
        groupTheme = findViewById(R.id.groupTheme);
        btnMusicOn = findViewById(R.id.btnMusicOn);
        btnMusicOff = findViewById(R.id.btnMusicOff);
        btnLangVi = findViewById(R.id.btnLangVi);
        btnLangEn = findViewById(R.id.btnLangEn);
        btnThemeLight = findViewById(R.id.btnThemeLight);
        btnThemeDark = findViewById(R.id.btnThemeDark);
        ivHomeBanner = findViewById(R.id.ivHomeBanner);
        tvDrawerChangeBanner = findViewById(R.id.tvDrawerChangeBanner);

        resizeCompoundStartIcon(tvDrawerShowHistory, R.dimen.home_drawer_item_icon_size);
        resizeCompoundStartIcon(tvDrawerChangeBanner, R.dimen.home_drawer_item_icon_size);
        resizeCompoundStartIcon(tvDrawerAdminDashboard, R.dimen.home_drawer_item_icon_size);
    }

    private void applySystemInsets() {
        if (drawerLayout == null) {
            return;
        }

        final int baseTopBarMargin = getResources().getDimensionPixelSize(R.dimen.home_top_bar_margin_top);
        final int baseDrawerTop = getResources().getDimensionPixelSize(R.dimen.home_drawer_padding_top);

        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, (v, insets) -> {
            int statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

            if (topBar != null) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                        (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) topBar.getLayoutParams();
                lp.topMargin = baseTopBarMargin + statusTop;
                topBar.setLayoutParams(lp);
            }

            if (drawerPanel != null) {
                drawerPanel.setPadding(
                        drawerPanel.getPaddingLeft(),
                        baseDrawerTop + statusTop,
                        drawerPanel.getPaddingRight(),
                        drawerPanel.getPaddingBottom()
                );
            }

            return insets;
        });
    }

    private void setupDrawerActions() {
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                if (drawerView != drawerPanel || homeContentRoot == null) {
                    return;
                }
                float translationX = dpToPx(14f) * slideOffset;
                homeContentRoot.setTranslationX(translationX);
                homeContentRoot.setAlpha(1f - (0.12f * slideOffset));
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                if (homeContentRoot != null) {
                    homeContentRoot.animate().translationX(0f).alpha(1f).setDuration(140L).start();
                }
            }
        });

        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        tvDrawerShowHistory.setOnClickListener(v -> {
            openGalleryIfAllowed();
        });

        tvDrawerAdminDashboard.setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, AdminDashboardActivity.class));
            drawerLayout.closeDrawer(GravityCompat.START);
            finish();
        });

        btnDrawerSignOut.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            handleSignOut();
        });

        tvDrawerChangeBanner.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });
    }

    private void setupMainActions() {
        btnStart.setOnClickListener(v -> {
            Animation press = AnimationUtils.loadAnimation(this, R.anim.btn_press);
            btnStart.startAnimation(press);

            press.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    pauseBackgroundMusicForCaptureFlow();
                    startActivity(new Intent(HomeActivity.this, SetupActivity.class));
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
        });

        btnGallery.setOnClickListener(v -> openGalleryIfAllowed());
    }

    private void setupMusicControls() {
        groupMusicState.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.btnMusicOn) {
                setMusicEnabled(true);
            } else if (checkedId == R.id.btnMusicOff) {
                setMusicEnabled(false);
            }
            updateMusicControls();
        });
        updateMusicControls();
    }

    private void setupLanguageControls() {
        groupLanguage.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            String targetLang = checkedId == R.id.btnLangVi ? LocaleManager.LANG_VI : LocaleManager.LANG_EN;
            String current = LocaleManager.getCurrentLanguage(HomeActivity.this);
            if (!targetLang.equals(current)) {
                String updated = LocaleManager.switchLanguageWithoutRecreate(HomeActivity.this, targetLang);
                updateLocalizedUi(updated);
            }
        });
        updateLanguageControls(LocaleManager.getCurrentLanguage(this));
    }

    private void setupThemeControls() {
        if (groupTheme == null) {
            return;
        }
        groupTheme.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            String targetMode = checkedId == R.id.btnThemeDark ? ThemeManager.MODE_DARK : ThemeManager.MODE_LIGHT;
            String current = ThemeManager.getSavedThemeMode(HomeActivity.this);
            if (!targetMode.equals(current)) {
                ThemeManager.setThemeMode(HomeActivity.this, targetMode);
            }
            updateThemeControls();
        });
        updateThemeControls();
    }

    private void applyMusicPreference() {
        SharedPreferences prefs = getSharedPreferences(PREF_HOME, MODE_PRIVATE);
        boolean musicEnabled = prefs.getBoolean(KEY_MUSIC_ENABLED, true);
        isMuted = !musicEnabled;
        if (musicEnabled) {
            if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
            }
        } else if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    private void setMusicEnabled(boolean enabled) {
        isMuted = !enabled;
        getSharedPreferences(PREF_HOME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MUSIC_ENABLED, enabled)
                .apply();

        if (enabled) {
            if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
            }
        } else if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    private void updateMusicControls() {
        int checkedId = isMuted ? R.id.btnMusicOff : R.id.btnMusicOn;
        if (groupMusicState.getCheckedButtonId() != checkedId) {
            groupMusicState.check(checkedId);
        }
        boolean onChecked = !isMuted;
        btnMusicOn.setBackgroundTintList(ContextCompat.getColorStateList(this, onChecked ? R.color.white : android.R.color.transparent));
        btnMusicOff.setBackgroundTintList(ContextCompat.getColorStateList(this, onChecked ? android.R.color.transparent : R.color.white));
    }

    private void updateLanguageControls(String languageTag) {
        int checkedId = LocaleManager.LANG_VI.equals(languageTag) ? R.id.btnLangVi : R.id.btnLangEn;
        if (groupLanguage.getCheckedButtonId() != checkedId) {
            groupLanguage.check(checkedId);
        }
        boolean viChecked = LocaleManager.LANG_VI.equals(languageTag);
        btnLangVi.setBackgroundTintList(ContextCompat.getColorStateList(this, viChecked ? R.color.white : android.R.color.transparent));
        btnLangEn.setBackgroundTintList(ContextCompat.getColorStateList(this, viChecked ? android.R.color.transparent : R.color.white));
    }

    private void updateThemeControls() {
        if (groupTheme == null || btnThemeLight == null || btnThemeDark == null) {
            return;
        }
        boolean darkMode = ThemeManager.MODE_DARK.equals(ThemeManager.getSavedThemeMode(this));
        int checkedId = darkMode ? R.id.btnThemeDark : R.id.btnThemeLight;
        if (groupTheme.getCheckedButtonId() != checkedId) {
            groupTheme.check(checkedId);
        }
        btnThemeLight.setBackgroundTintList(ContextCompat.getColorStateList(this, darkMode ? android.R.color.transparent : R.color.white));
        btnThemeDark.setBackgroundTintList(ContextCompat.getColorStateList(this, darkMode ? R.color.white : android.R.color.transparent));
    }


    private void updateLocalizedUi(String languageTag) {
        tvDrawerOur.setText(LocaleManager.getString(this, R.string.home_drawer_our, languageTag));
        tvDrawerMemories.setText(LocaleManager.getString(this, R.string.home_drawer_memories, languageTag));
        tvDrawerShowHistory.setText(LocaleManager.getString(this, R.string.home_drawer_show_history, languageTag));
        tvDrawerAdminDashboard.setText(LocaleManager.getString(this, R.string.admin_go_to_dashboard, languageTag));
        tvDrawerMusicLabel.setText(LocaleManager.getString(this, R.string.home_drawer_music, languageTag));
        tvDrawerLanguageLabel.setText(LocaleManager.getString(this, R.string.home_drawer_language, languageTag));
        tvDrawerThemeLabel.setText(LocaleManager.getString(this, R.string.home_drawer_theme, languageTag));
        btnMusicOn.setText(LocaleManager.getString(this, R.string.home_music_on, languageTag));
        btnMusicOff.setText(LocaleManager.getString(this, R.string.home_music_off, languageTag));
        btnLangVi.setText(LocaleManager.getString(this, R.string.home_switch_to_vietnamese, languageTag));
        btnLangEn.setText(LocaleManager.getString(this, R.string.home_switch_to_english, languageTag));
        btnThemeLight.setText(LocaleManager.getString(this, R.string.home_theme_light, languageTag));
        btnThemeDark.setText(LocaleManager.getString(this, R.string.home_theme_dark, languageTag));
        tvDrawerChangeBanner.setText(LocaleManager.getString(this, R.string.home_drawer_change_banner, languageTag));
        btnDrawerSignOut.setText(LocaleManager.getString(this, R.string.auth_sign_out, languageTag));

        updateMusicControls();
        updateLanguageControls(languageTag);
        updateThemeControls();

        if (btnStart != null) {
            btnStart.setText(LocaleManager.getString(this, R.string.btn_start_decorated, languageTag));
        }
        if (btnAdmin != null) {
            btnAdmin.setText(LocaleManager.getString(this, R.string.admin_go_to_dashboard, languageTag));
        }
        if (btnGallery != null) {
            btnGallery.setText(LocaleManager.getString(this, R.string.btn_gallery, languageTag));
        }
    }

    private void setupBannerPicker() {
        pickMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                // Grant persistable permission so it survives reboots
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                saveBannerUri(uri);
                displayBanner(uri);
            }
        });
    }

    private void saveBannerUri(Uri uri) {
        getSharedPreferences(PREF_HOME, MODE_PRIVATE)
                .edit()
                .putString(KEY_BANNER_URI, uri.toString())
                .apply();
    }

    private void loadSavedBanner() {
        String uriStr = getSharedPreferences(PREF_HOME, MODE_PRIVATE).getString(KEY_BANNER_URI, null);
        if (uriStr != null) {
            displayBanner(Uri.parse(uriStr));
        }
    }

    private void displayBanner(Uri uri) {
        if (ivHomeBanner == null) return;
        Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(ivHomeBanner);
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

    private void pauseBackgroundMusicForCaptureFlow() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseBackgroundMusicForCaptureFlow();
    }
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
                    tvDrawerAdminDashboard.setVisibility(View.VISIBLE);
                    return;
                }
                tvDrawerAdminDashboard.setVisibility(View.GONE);
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

    private void openGalleryIfAllowed() {
        if (authRepository.isGuest()) {
            String languageTag = LocaleManager.getCurrentLanguage(this);
            HelpDialogUtils.showHistoryGuestRegisterCta(
                    this,
                    LocaleManager.getString(this, R.string.home_gallery_user_only_title, languageTag),
                    LocaleManager.getString(this, R.string.home_gallery_user_only_message, languageTag),
                    this::openRegisterFromGuest,
                    () -> drawerLayout.closeDrawer(GravityCompat.START)
            );
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        startActivity(new Intent(HomeActivity.this, GalleryActivity.class));
        drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void openRegisterFromGuest() {
        authRepository.signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(IntentKeys.EXTRA_OPEN_REGISTER, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private void resizeCompoundStartIcon(TextView view, int sizeDimenRes) {
        if (view == null) {
            return;
        }
        Drawable[] drawables = view.getCompoundDrawablesRelative();
        Drawable start = drawables[0];
        if (start == null) {
            return;
        }
        int size = getResources().getDimensionPixelSize(sizeDimenRes);
        start = start.mutate();
        start.setBounds(0, 0, size, size);
        view.setCompoundDrawablesRelative(start, drawables[1], drawables[2], drawables[3]);
    }
}
