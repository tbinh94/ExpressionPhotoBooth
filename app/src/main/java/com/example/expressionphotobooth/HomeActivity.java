package com.example.expressionphotobooth;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
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

import java.util.Arrays;
import java.util.List;

public class HomeActivity extends AppCompatActivity {
    private static final String PREF_HOME = "home_preferences";
    private static final String KEY_MUSIC_ENABLED = "music_enabled";
    private static final String KEY_BANNER_URI = "banner_uri";
    private static final String TAG = "HomeActivity";

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
    private TextView tvDrawerUsageGuide;
    private TextView tvDrawerMusicLabel;
    private TextView tvDrawerLanguageLabel;
    private TextView tvDrawerThemeLabel;
    private MaterialButtonToggleGroup groupMusicState;
    private MaterialButtonToggleGroup groupTheme;
    private MaterialButton btnMusicOn;
    private MaterialButton btnMusicOff;
    private MaterialButton btnLanguageToggle;
    private MaterialButton btnThemeLight;
    private MaterialButton btnThemeDark;
    private MaterialButton btnThemeSystem;
    private TextView tvHomeOur;
    private TextView tvHomeMemories;
    private TextView tvHomePhotobooth;
    private View titleContainer;
    private View viewHomeBannerDimmer;

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
        updateTitleVisibility();

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
        tvDrawerUsageGuide = findViewById(R.id.tvDrawerUsageGuide);
        tvDrawerMusicLabel = findViewById(R.id.tvDrawerMusicLabel);
        tvDrawerLanguageLabel = findViewById(R.id.tvDrawerLanguageLabel);
        tvDrawerThemeLabel = findViewById(R.id.tvDrawerThemeLabel);
        groupMusicState = findViewById(R.id.groupMusicState);
        groupTheme = findViewById(R.id.groupTheme);
        btnMusicOn = findViewById(R.id.btnMusicOn);
        btnMusicOff = findViewById(R.id.btnMusicOff);
        btnLanguageToggle = findViewById(R.id.btnLanguageToggle);
        btnThemeLight = findViewById(R.id.btnThemeLight);
        btnThemeDark = findViewById(R.id.btnThemeDark);
        btnThemeSystem = findViewById(R.id.btnThemeSystem);
        ivHomeBanner = findViewById(R.id.ivHomeBanner);
        viewHomeBannerDimmer = findViewById(R.id.viewHomeBannerDimmer);

        tvHomeOur = findViewById(R.id.tvHomeOur);
        tvHomeMemories = findViewById(R.id.tvHomeMemories);
        tvHomePhotobooth = findViewById(R.id.tvHomePhotobooth);
        titleContainer = findViewById(R.id.titleContainer);
        tvDrawerChangeBanner = findViewById(R.id.tvDrawerChangeBanner);

        resizeCompoundStartIcon(tvDrawerShowHistory, R.dimen.home_drawer_item_icon_size);
        resizeCompoundStartIcon(tvDrawerChangeBanner, R.dimen.home_drawer_item_icon_size);
        resizeCompoundStartIcon(tvDrawerUsageGuide, R.dimen.home_drawer_item_icon_size);
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

        tvDrawerShowHistory.setOnClickListener(v -> openGalleryIfAllowed());

        tvDrawerUsageGuide.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            showUsageGuideDialog();
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
            showChangeBannerDialog();
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
                    openSetupActivity();
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
        if (btnLanguageToggle != null) {
            btnLanguageToggle.setOnClickListener(v -> {
                String updated = LocaleManager.toggleLanguageWithoutRecreate(HomeActivity.this);
                updateLocalizedUi(updated);
            });
        }
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
            String targetMode = ThemeManager.MODE_LIGHT;
            if (checkedId == R.id.btnThemeDark) targetMode = ThemeManager.MODE_DARK;
            else if (checkedId == R.id.btnThemeSystem) targetMode = ThemeManager.MODE_SYSTEM;

            String current = ThemeManager.getSavedThemeMode(HomeActivity.this);
            if (!targetMode.equals(current)) {
                // Update UI immediately before animation to prevent toggle bounce-back
                updateSegmentButtonState(btnThemeLight, ThemeManager.MODE_LIGHT.equals(targetMode));
                updateSegmentButtonState(btnThemeDark, ThemeManager.MODE_DARK.equals(targetMode));
                updateSegmentButtonState(btnThemeSystem, ThemeManager.MODE_SYSTEM.equals(targetMode));
                applyThemeWithCrossFade(targetMode, groupTheme);
            }
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
        updateSegmentButtonState(btnMusicOn, onChecked);
        updateSegmentButtonState(btnMusicOff, !onChecked);
    }

    private void updateLanguageControls(String languageTag) {
        if (btnLanguageToggle == null) {
            return;
        }
        boolean isVietnamese = LocaleManager.LANG_VI.equalsIgnoreCase(languageTag);
        int contentDescRes = isVietnamese
                ? R.string.language_toggle_to_english
                : R.string.language_toggle_to_vietnamese;
        String contentDesc = LocaleManager.getString(this, contentDescRes, languageTag);
        btnLanguageToggle.setContentDescription(contentDesc);
        ViewCompat.setTooltipText(btnLanguageToggle, contentDesc);
        
        // Show flag of what the NEXT language will be, or the current one? 
        // Usually, show the flag of the language you will SWITCH TO.
        int flagRes = isVietnamese ? R.drawable.ic_flag_uk : R.drawable.ic_flag_vn;
        btnLanguageToggle.setIconResource(flagRes);
    }


    private void updateThemeControls() {
        boolean isNightMode = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        if (viewHomeBannerDimmer != null) {
            viewHomeBannerDimmer.setVisibility(isNightMode ? View.VISIBLE : View.GONE);
            viewHomeBannerDimmer.setAlpha(isNightMode ? 0.88f : 0f);
        }

        String savedMode = ThemeManager.getSavedThemeMode(this);

        // Temporarily clear listener to prevent infinite loop when setting checked state
        groupTheme.clearOnButtonCheckedListeners();

        int checkedId = R.id.btnThemeLight;
        if (ThemeManager.MODE_DARK.equals(savedMode)) checkedId = R.id.btnThemeDark;
        else if (ThemeManager.MODE_SYSTEM.equals(savedMode)) checkedId = R.id.btnThemeSystem;

        if (groupTheme.getCheckedButtonId() != checkedId) {
            groupTheme.check(checkedId);
        }

        updateSegmentButtonState(btnThemeLight, ThemeManager.MODE_LIGHT.equals(savedMode));
        updateSegmentButtonState(btnThemeDark, ThemeManager.MODE_DARK.equals(savedMode));
        updateSegmentButtonState(btnThemeSystem, ThemeManager.MODE_SYSTEM.equals(savedMode));

        // Re-attach listener
        setupThemeControlsListenerOnly();
    }

    private void setupThemeControlsListenerOnly() {
        groupTheme.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            String targetMode = ThemeManager.MODE_LIGHT;
            if (checkedId == R.id.btnThemeDark) targetMode = ThemeManager.MODE_DARK;
            else if (checkedId == R.id.btnThemeSystem) targetMode = ThemeManager.MODE_SYSTEM;

            String current = ThemeManager.getSavedThemeMode(HomeActivity.this);
            if (!targetMode.equals(current)) {
                updateSegmentButtonState(btnThemeLight, ThemeManager.MODE_LIGHT.equals(targetMode));
                updateSegmentButtonState(btnThemeDark, ThemeManager.MODE_DARK.equals(targetMode));
                updateSegmentButtonState(btnThemeSystem, ThemeManager.MODE_SYSTEM.equals(targetMode));
                applyThemeWithCrossFade(targetMode, groupTheme);
            }
        });
    }

    private void updateSegmentButtonState(MaterialButton button, boolean selected) {
        if (button == null) {
            return;
        }
        int bg = selected ? R.color.home_toggle_selected_bg : android.R.color.transparent;
        int text = selected ? R.color.home_toggle_selected_text : R.color.home_toggle_text;
        button.setBackgroundTintList(ContextCompat.getColorStateList(this, bg));
        button.setTextColor(ContextCompat.getColor(this, text));
    }

    private void applyThemeWithCrossFade(String targetMode, View anchor) {
        View root = findViewById(android.R.id.content);
        if (root == null || anchor == null) {
            ThemeManager.setThemeMode(this, targetMode);
            return;
        }

        root.setDrawingCacheEnabled(true);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(root.getDrawingCache());
        root.setDrawingCacheEnabled(false);

        ImageView overlay = new ImageView(this);
        overlay.setImageBitmap(bitmap);
        overlay.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        ((android.view.ViewGroup) root).addView(overlay);

        ThemeManager.setThemeMode(this, targetMode);

        int cx = (anchor.getLeft() + anchor.getRight()) / 2;
        int cy = (anchor.getTop() + anchor.getBottom()) / 2;
        int finalRadius = Math.max(root.getWidth(), root.getHeight());

        android.animation.Animator anim = android.view.ViewAnimationUtils.createCircularReveal(root, cx, cy, 0, finalRadius);
        anim.setDuration(400);
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                ((android.view.ViewGroup) root).removeView(overlay);
                bitmap.recycle();
            }
        });
        anim.start();
    }

    private void applyThemeWithoutCrossFade(String targetMode) {
        ThemeManager.setThemeMode(this, targetMode);
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


    private void updateLocalizedUi(String languageTag) {
        if (tvDrawerOur != null) tvDrawerOur.setText(LocaleManager.getString(this, R.string.home_drawer_our, languageTag));
        if (tvDrawerMemories != null) tvDrawerMemories.setText(LocaleManager.getString(this, R.string.home_drawer_memories, languageTag));
        if (tvDrawerShowHistory != null) tvDrawerShowHistory.setText(LocaleManager.getString(this, R.string.home_drawer_show_history, languageTag));
        if (tvDrawerChangeBanner != null) tvDrawerChangeBanner.setText(LocaleManager.getString(this, R.string.home_drawer_change_banner, languageTag));
        if (tvDrawerUsageGuide != null) tvDrawerUsageGuide.setText(LocaleManager.getString(this, R.string.home_drawer_usage_guide, languageTag));
        if (tvDrawerAdminDashboard != null) tvDrawerAdminDashboard.setText(LocaleManager.getString(this, R.string.admin_go_to_dashboard, languageTag));
        if (tvDrawerMusicLabel != null) tvDrawerMusicLabel.setText(LocaleManager.getString(this, R.string.home_drawer_music, languageTag));
        if (tvDrawerLanguageLabel != null) tvDrawerLanguageLabel.setText(LocaleManager.getString(this, R.string.home_drawer_language, languageTag));
        if (tvDrawerThemeLabel != null) tvDrawerThemeLabel.setText(LocaleManager.getString(this, R.string.home_drawer_theme, languageTag));
        if (btnMusicOn != null) btnMusicOn.setText(LocaleManager.getString(this, R.string.home_music_on, languageTag));
        if (btnMusicOff != null) btnMusicOff.setText(LocaleManager.getString(this, R.string.home_music_off, languageTag));
        if (btnThemeLight != null) btnThemeLight.setText(LocaleManager.getString(this, R.string.home_theme_light, languageTag));
        if (btnThemeDark != null) btnThemeDark.setText(LocaleManager.getString(this, R.string.home_theme_dark, languageTag));
        if (btnThemeSystem != null) btnThemeSystem.setText(LocaleManager.getString(this, R.string.home_theme_system, languageTag));
        if (btnDrawerSignOut != null) btnDrawerSignOut.setText(LocaleManager.getString(this, R.string.auth_sign_out, languageTag));
        if (btnStart != null) btnStart.setText(LocaleManager.getString(this, R.string.btn_start_decorated, languageTag));
        if (btnGallery != null) btnGallery.setText(LocaleManager.getString(this, R.string.btn_gallery, languageTag));
        if (tvHomeOur != null) tvHomeOur.setText(LocaleManager.getString(this, R.string.home_title_our, languageTag));
        if (tvHomeMemories != null) tvHomeMemories.setText(LocaleManager.getString(this, R.string.home_title_memories, languageTag));
        if (tvHomePhotobooth != null) tvHomePhotobooth.setText(LocaleManager.getString(this, R.string.home_title_photobooth, languageTag));
        updateLanguageControls(languageTag);
        updateThemeControls();
    }

    private void showUsageGuideDialog() {
        String languageTag = LocaleManager.getCurrentLanguage(this);
        String title = LocaleManager.getString(this, R.string.home_usage_guide_title, languageTag);
        String subtitle = LocaleManager.getString(this, R.string.home_usage_guide_subtitle, languageTag);
        List<String> steps = Arrays.asList(
                LocaleManager.getString(this, R.string.home_usage_step_1, languageTag),
                LocaleManager.getString(this, R.string.home_usage_step_2, languageTag),
                LocaleManager.getString(this, R.string.home_usage_step_3, languageTag),
                LocaleManager.getString(this, R.string.home_usage_step_4, languageTag),
                LocaleManager.getString(this, R.string.home_usage_step_5, languageTag),
                LocaleManager.getString(this, R.string.home_usage_step_6, languageTag),
                LocaleManager.getString(this, R.string.home_usage_step_7, languageTag),
                LocaleManager.getString(this, R.string.home_usage_step_8, languageTag)
        );
        int[] stepIcons = new int[] {
                R.drawable.ic_grid_pattern,
                R.drawable.ic_videocam_24,
                R.drawable.ic_videocam_24,
                R.drawable.ic_edit_24,
                R.drawable.ic_check_24,
                R.drawable.ic_check_24,
                R.drawable.ic_history_24,
                R.drawable.ic_settings_24
        };
        String ctaText = LocaleManager.getString(this, R.string.home_usage_cta_start_now, languageTag);
        HelpDialogUtils.showUsageGuideBranded(this, title, subtitle, steps, stepIcons, ctaText, this::openSetupActivity);
    }

    private void openSetupActivity() {
        pauseBackgroundMusicForCaptureFlow();
        startActivity(new Intent(HomeActivity.this, SetupActivity.class));
    }

    private void showChangeBannerDialog() {
        String languageTag = LocaleManager.getCurrentLanguage(this);
        String[] options = {
            LocaleManager.getString(this, R.string.home_banner_option_picker, languageTag),
            LocaleManager.getString(this, R.string.home_banner_option_default, languageTag)
        };

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(LocaleManager.getString(this, R.string.home_banner_dialog_title, languageTag))
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    pickMedia.launch(new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build());
                } else {
                    resetBannerToDefault();
                }
            })
            .show();
    }

    private void resetBannerToDefault() {
        getSharedPreferences(PREF_HOME, MODE_PRIVATE)
                .edit()
                .remove(KEY_BANNER_URI)
                .apply();
        if (ivHomeBanner != null) {
            ivHomeBanner.setImageResource(R.drawable.photo_banner);
        }
        updateTitleVisibility();
    }

    private void updateTitleVisibility() {
        if (titleContainer == null) return;
        String savedUri = getSharedPreferences(PREF_HOME, MODE_PRIVATE).getString(KEY_BANNER_URI, null);
        boolean isCustom = savedUri != null;
        boolean isNightMode = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        titleContainer.setVisibility(isCustom ? View.VISIBLE : View.GONE);
        
        // Apply adaptive neon glow in dark mode and soft depth in light mode.
        int shadowColor = isNightMode
                ? ContextCompat.getColor(this, R.color.home_neon_glow)
                : (isCustom ? 0xAA000000 : 0x66FFFFFF);
        float radius = isNightMode ? 20f : (isCustom ? 12f : 8f);
        float dy = isNightMode ? 0f : (isCustom ? 3f : 0f);

        if (tvHomeOur != null) tvHomeOur.setShadowLayer(radius, 0f, dy, shadowColor);
        if (tvHomeMemories != null) tvHomeMemories.setShadowLayer(radius, 0f, dy, shadowColor);
        if (tvHomePhotobooth != null) tvHomePhotobooth.setShadowLayer(radius, 0f, dy/2, shadowColor);

        // Enhance buttons for custom background
        if (btnStart != null) {
            btnStart.setElevation(isCustom ? dpToPx(16f) : dpToPx(8f));
            if (isCustom) {
                btnStart.setStrokeWidth(Math.round(dpToPx(2f)));
                btnStart.setStrokeColor(ContextCompat.getColorStateList(this, R.color.black_overlay_80));
            } else {
                btnStart.setStrokeWidth(0);
            }
        }
        if (btnGallery != null) {
            btnGallery.setElevation(isCustom ? dpToPx(12f) : dpToPx(4f));
            if (isCustom) {
                btnGallery.setStrokeWidth(Math.round(dpToPx(1.5f)));
                btnGallery.setStrokeColor(ContextCompat.getColorStateList(this, R.color.black_overlay_60));
            } else {
                btnGallery.setStrokeWidth(0);
            }
        }
    }

    private void setupBannerPicker() {
        pickMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri == null) {
                return;
            }
            // Persist permission for internal storage URIs if possible
            try {
                final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (SecurityException e) {
                // Not a persistable URI, fine for many pickers
            }

            if (ivHomeBanner != null) {
                Glide.with(this).load(uri).centerCrop().into(ivHomeBanner);
            }
            getSharedPreferences(PREF_HOME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_BANNER_URI, uri.toString())
                    .apply();
            updateTitleVisibility();
        });
    }

    private void loadSavedBanner() {
        String savedUri = getSharedPreferences(PREF_HOME, MODE_PRIVATE).getString(KEY_BANNER_URI, null);
        if (savedUri == null || ivHomeBanner == null) {
            return;
        }
        Glide.with(this).load(Uri.parse(savedUri)).centerCrop().into(ivHomeBanner);
    }
}
