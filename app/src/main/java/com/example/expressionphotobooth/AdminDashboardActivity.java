package com.example.expressionphotobooth;

import android.content.Intent;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.example.expressionphotobooth.utils.LocaleManager;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Locale;

public class AdminDashboardActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private android.widget.ImageView btnLanguageToggle;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private TextView tvAdminGreeting;
    private TextView tvStatAccountsLabel;
    private TextView tvStatImageDownloadsLabel;
    private TextView tvStatVideoDownloadsLabel;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean languageSwitchInProgress;
    private String currentAdminName = "Administrator";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(com.example.expressionphotobooth.utils.LocaleManager.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.admin_dashboard_bg)));
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_admin_dashboard);
        overridePendingTransition(0, 0);

        AuthRepository authRepository = ((AppContainer) getApplication()).getAuthRepository();
        if (!authRepository.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        navigationView.setNavigationItemSelectedListener(this);

        View btnMenu = findViewById(R.id.btnMenu);
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        // Email display removed
        String email = authRepository.getCurrentEmail();

        tvAdminGreeting = findViewById(R.id.tvAdminGreeting);
        tvStatAccountsLabel = findViewById(R.id.tvStatAccountsLabel);
        tvStatImageDownloadsLabel = findViewById(R.id.tvStatImageDownloadsLabel);
        tvStatVideoDownloadsLabel = findViewById(R.id.tvStatVideoDownloadsLabel);

        btnLanguageToggle = findViewById(R.id.btnLanguageToggle);
        updateLanguageButtonA11y(LocaleManager.getCurrentLanguage(this));
        btnLanguageToggle.setOnClickListener(v -> {
            if (languageSwitchInProgress) {
                return;
            }
            languageSwitchInProgress = true;
            btnLanguageToggle.setEnabled(false);

            String language = LocaleManager.toggleLanguageWithoutRecreate(this);
            updateLocalizedUi(language);
            notifyRuntimeLanguageChanged(language);

            mainHandler.postDelayed(() -> {
                languageSwitchInProgress = false;
                if (btnLanguageToggle != null) {
                    btnLanguageToggle.setEnabled(true);
                }
            }, 250L);
        });

        ViewPager2 viewPager = findViewById(R.id.viewPager);

        AdminPagerAdapter adapter = new AdminPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // Handle drawer close from header
        View headerView = navigationView.getHeaderView(0);
        View btnCloseDrawer = headerView.findViewById(R.id.btnCloseDrawer);
        if (btnCloseDrawer != null) {
            btnCloseDrawer.setOnClickListener(v -> drawerLayout.closeDrawer(GravityCompat.START));
        }

        TextView tvNavAvatarInitials = headerView.findViewById(R.id.tvNavAvatarInitials);
        if (tvNavAvatarInitials != null) {
            tvNavAvatarInitials.setText(resolveAvatarInitial(email));
        }

        String uid = authRepository.getCurrentUid();
        if (!TextUtils.isEmpty(uid) && tvNavAvatarInitials != null) {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .get()
                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot snapshot) {
                            String displayName = snapshot.getString("displayName");
                            if (!TextUtils.isEmpty(displayName)) {
                                currentAdminName = displayName;
                                tvNavAvatarInitials.setText(resolveAvatarInitial(displayName));
                            }
                        }
                    });
        }

        View cardNavUserProfile = headerView.findViewById(R.id.cardNavUserProfile);
        if (cardNavUserProfile != null) {
            cardNavUserProfile.setOnClickListener(v -> showAdminProfilePopup(email));
        }

        findViewById(R.id.btnThemeToggle).setOnClickListener(v -> {
            String current = com.example.expressionphotobooth.utils.ThemeManager.getSavedThemeMode(this);
            String target = com.example.expressionphotobooth.utils.ThemeManager.MODE_DARK.equals(current) 
                    ? com.example.expressionphotobooth.utils.ThemeManager.MODE_LIGHT 
                    : com.example.expressionphotobooth.utils.ThemeManager.MODE_DARK;
            
            com.example.expressionphotobooth.utils.ThemeManager.setThemeMode(this, target);
            updateThemeToggleButton(target);
            
            // Recreate activity to apply theme globally
            recreate();
        });
        updateThemeToggleButton(com.example.expressionphotobooth.utils.ThemeManager.getSavedThemeMode(this));

        updateNavigationMenuTitles(LocaleManager.getCurrentLanguage(this));
        setupOnBackPressed();

        // Delay binding until after first layout pass so CollapsingToolbarLayout
        // has fully attached the stat cards and touch dispatch is stable
        getWindow().getDecorView().post(this::bindStatCardClicks);
    }

    // ── Stat card click handlers ────────────────────────────────────────────
    private com.example.expressionphotobooth.domain.model.AdminDashboardStats cachedStats;

    /** Called by AdminOverviewFragment after it loads stats so cards here can use fresh data. */
    public void onStatsLoaded(com.example.expressionphotobooth.domain.model.AdminDashboardStats stats) {
        cachedStats = stats;
    }

    private void bindStatCardClicks() {
        com.google.android.material.card.MaterialCardView cardAccounts =
                findViewById(R.id.cardStatAccounts);
        com.google.android.material.card.MaterialCardView cardImages =
                findViewById(R.id.cardStatImageDownloads);
        com.google.android.material.card.MaterialCardView cardVideos =
                findViewById(R.id.cardStatVideoDownloads);

        if (cardAccounts != null) cardAccounts.setOnClickListener(v -> showStatDetail("accounts"));
        if (cardImages != null)   cardImages.setOnClickListener(v -> showStatDetail("images"));
        if (cardVideos != null)   cardVideos.setOnClickListener(v -> showStatDetail("videos"));
    }

    private void showStatDetail(String type) {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_stat_detail, null);
        sheet.setContentView(view);

        // Đảm bảo background trong suốt để hiện góc bo tròn của layout custom
        sheet.setOnShowListener(dialog -> {
            com.google.android.material.bottomsheet.BottomSheetDialog d = (com.google.android.material.bottomsheet.BottomSheetDialog) dialog;
            View bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });

        com.google.android.material.card.MaterialCardView iconCard = view.findViewById(R.id.bsStatIconCard);
        android.widget.ImageView icon      = view.findViewById(R.id.bsStatIcon);
        TextView title      = view.findViewById(R.id.bsStatTitle);
        TextView subtitle   = view.findViewById(R.id.bsStatSubtitle);
        TextView bigNumber  = view.findViewById(R.id.bsStatBigNumber);
        android.widget.LinearLayout detailContainer  = view.findViewById(R.id.bsStatDetailContainer);
        android.widget.LinearLayout monthlyContainer = view.findViewById(R.id.bsStatMonthlyContainer);
        TextView monthlyTitle = view.findViewById(R.id.bsStatMonthlyTitle);

        com.example.expressionphotobooth.domain.model.AdminDashboardStats s = cachedStats;

        switch (type) {
            case "accounts":
                iconCard.setCardBackgroundColor(android.graphics.Color.parseColor("#4A6CF7"));
                icon.setImageResource(R.drawable.ic_person_24);
                title.setText("Tài khoản");
                title.setTextColor(android.graphics.Color.parseColor("#4A6CF7"));
                bigNumber.setTextColor(android.graphics.Color.parseColor("#4A6CF7"));
                subtitle.setText("Tổng người dùng đã đăng ký");
                bigNumber.setText(s != null ? String.valueOf(s.getTotalAccounts()) : "—");
                addDetailRow(detailContainer, "👤 Tổng tài khoản",
                        s != null ? String.valueOf(s.getTotalAccounts()) : "—", "#4A6CF7");
                addDetailRow(detailContainer, "🤖 Dùng tính năng AI",
                        s != null ? String.valueOf(s.getAiRegisteredUsers()) : "—", "#8B5CF6");
                addDetailRow(detailContainer, "📊 Tỷ lệ AI",
                        s != null ? String.format(Locale.getDefault(), "%.0f%%", s.getAiRegisteredPercent()) : "—", "#06B6D4");
                if (s != null && s.getUsersByMonth() != null && !s.getUsersByMonth().isEmpty()) {
                    monthlyTitle.setVisibility(View.VISIBLE);
                    monthlyContainer.setVisibility(View.VISIBLE);
                    populateMonthlyRows(monthlyContainer, s.getUsersByMonth(), "#4A6CF7");
                }
                break;

            case "images":
                iconCard.setCardBackgroundColor(android.graphics.Color.parseColor("#F59E0B"));
                icon.setImageResource(R.drawable.ic_download_24);
                title.setText("Tải ảnh");
                title.setTextColor(android.graphics.Color.parseColor("#D97706"));
                bigNumber.setTextColor(android.graphics.Color.parseColor("#D97706"));
                subtitle.setText("Tổng lượt lưu ảnh kết quả");
                bigNumber.setText(s != null ? String.valueOf(s.getImageDownloads()) : "—");
                addDetailRow(detailContainer, "📥 Tổng lượt tải",
                        s != null ? String.valueOf(s.getImageDownloads()) : "—", "#F59E0B");
                addDetailRow(detailContainer, "📈 Tháng gần nhất",
                        s != null ? getLatestMonthValue(s.getImageDownloadsByMonth()) : "—", "#10B981");
                if (s != null && s.getImageDownloadsByMonth() != null && !s.getImageDownloadsByMonth().isEmpty()) {
                    monthlyTitle.setVisibility(View.VISIBLE);
                    monthlyContainer.setVisibility(View.VISIBLE);
                    populateMonthlyRows(monthlyContainer, s.getImageDownloadsByMonth(), "#F59E0B");
                }
                break;

            case "videos":
                iconCard.setCardBackgroundColor(android.graphics.Color.parseColor("#EF4444"));
                icon.setImageResource(R.drawable.ic_videocam_24);
                title.setText("Tải video");
                title.setTextColor(android.graphics.Color.parseColor("#DC2626"));
                bigNumber.setTextColor(android.graphics.Color.parseColor("#DC2626"));
                subtitle.setText("Tổng lượt lưu video kết quả");
                bigNumber.setText(s != null ? String.valueOf(s.getVideoDownloads()) : "—");
                addDetailRow(detailContainer, "🎬 Tổng lượt tải",
                        s != null ? String.valueOf(s.getVideoDownloads()) : "—", "#EF4444");
                addDetailRow(detailContainer, "📈 Xu hướng",
                        s != null && s.getVideoDownloads() > 0 ? "Đang tăng 🔥" : "Chưa có dữ liệu", "#10B981");
                break;
        }

        view.findViewById(R.id.bsStatClose).setOnClickListener(v -> sheet.dismiss());
        sheet.show();
    }

    private void addDetailRow(android.widget.LinearLayout container, String label, String value, String colorHex) {
        View row = getLayoutInflater().inflate(R.layout.item_stat_detail_row, null);
        TextView tvLabel = row.findViewById(R.id.tvDetailRowLabel);
        TextView tvValue = row.findViewById(R.id.tvDetailRowValue);
        View indicator  = row.findViewById(R.id.viewDetailRowIndicator);

        tvLabel.setText(label);
        tvValue.setText(value);
        indicator.setBackgroundColor(android.graphics.Color.parseColor(colorHex));

        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        row.setLayoutParams(lp);
        container.addView(row);
    }

    private void populateMonthlyRows(android.widget.LinearLayout container,
                                      java.util.LinkedHashMap<String, Integer> data,
                                      String colorHex) {
        java.util.List<java.util.Map.Entry<String, Integer>> entries = new java.util.ArrayList<>(data.entrySet());
        int max = entries.stream().mapToInt(e -> e.getValue() == null ? 0 : e.getValue()).max().orElse(1);
        if (max == 0) max = 1;

        for (int i = Math.max(0, entries.size() - 6); i < entries.size(); i++) {
            java.util.Map.Entry<String, Integer> entry = entries.get(i);
            String month = entry.getKey();
            int val = entry.getValue() == null ? 0 : entry.getValue();

            // format month key e.g. "2026-04" → "04/26"
            if (month != null && month.length() == 7 && month.charAt(4) == '-') {
                month = month.substring(5, 7) + "/" + month.substring(2, 4);
            }
            addMonthRow(container, month, val, max, colorHex);
        }
    }

    private void addMonthRow(android.widget.LinearLayout container, String month, int value, int maxValue, String colorHex) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.widget.LinearLayout.LayoutParams rowLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (int)(36 * getResources().getDisplayMetrics().density));
        rowLp.bottomMargin = (int)(6 * getResources().getDisplayMetrics().density);
        row.setLayoutParams(rowLp);

        // Month label
        TextView tvMonth = new TextView(this);
        tvMonth.setText(month);
        tvMonth.setTextSize(12f);
        tvMonth.setTextColor(android.graphics.Color.parseColor("#6B7280"));
        tvMonth.setMinWidth((int)(44 * getResources().getDisplayMetrics().density));
        row.addView(tvMonth);

        // Bar background
        android.widget.FrameLayout barBg = new android.widget.FrameLayout(this);
        android.widget.LinearLayout.LayoutParams barBgLp = new android.widget.LinearLayout.LayoutParams(
                0, (int)(14 * getResources().getDisplayMetrics().density), 1f);
        int barMargin = (int)(8 * getResources().getDisplayMetrics().density);
        barBgLp.setMarginStart(barMargin);
        barBgLp.setMarginEnd(barMargin);
        barBg.setLayoutParams(barBgLp);
        barBg.setBackgroundColor(android.graphics.Color.parseColor("#F3F4F6"));

        // Filled portion
        View barFill = new View(this);
        int fillPercent = maxValue > 0 ? (int)((value / (float)maxValue) * 100) : 0;
        android.widget.FrameLayout.LayoutParams fillLp = new android.widget.FrameLayout.LayoutParams(0,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        barFill.setLayoutParams(fillLp);
        barFill.setBackgroundColor(android.graphics.Color.parseColor(colorHex));
        barBg.addView(barFill);
        barBg.post(() -> {
            int parentWidth = barBg.getWidth();
            android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) barFill.getLayoutParams();
            lp.width = (int)(parentWidth * fillPercent / 100f);
            barFill.setLayoutParams(lp);
        });
        row.addView(barBg);

        // Value label
        TextView tvVal = new TextView(this);
        tvVal.setText(String.valueOf(value));
        tvVal.setTextSize(12f);
        tvVal.setTextColor(android.graphics.Color.parseColor("#374151"));
        tvVal.setTypeface(null, android.graphics.Typeface.BOLD);
        tvVal.setMinWidth((int)(28 * getResources().getDisplayMetrics().density));
        tvVal.setGravity(android.view.Gravity.END);
        row.addView(tvVal);

        container.addView(row);
    }

    private String getLatestMonthValue(java.util.LinkedHashMap<String, Integer> map) {
        if (map == null || map.isEmpty()) return "—";
        java.util.List<Integer> values = new java.util.ArrayList<>(map.values());
        Integer last = values.get(values.size() - 1);
        return last == null ? "0" : String.valueOf(last);
    }


    private void updateThemeToggleButton(String mode) {
        android.widget.ImageView btnThemeToggle = findViewById(R.id.btnThemeToggle);
        if (btnThemeToggle != null) {
            boolean isDark = com.example.expressionphotobooth.utils.ThemeManager.MODE_DARK.equals(mode);
            btnThemeToggle.setImageResource(isDark ? R.drawable.ic_theme_sun_20 : R.drawable.ic_theme_moon_20);
        }
    }

    private void showAdminProfilePopup(String email) {
        String displayEmail = TextUtils.isEmpty(email) ? "admin@expressionphotobooth.com" : email;
        
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_admin_profile_bottom_sheet, null);
        
        TextView tvProfileAvatar = view.findViewById(R.id.tvProfileAvatar);
        TextView tvProfileName = view.findViewById(R.id.tvProfileName);
        TextView tvProfileEmail = view.findViewById(R.id.tvProfileEmail);
        View btnCloseProfile = view.findViewById(R.id.btnCloseProfile);
        
        tvProfileName.setText(currentAdminName);
        tvProfileEmail.setText(displayEmail);
        tvProfileAvatar.setText(resolveAvatarInitial(currentAdminName));
        
        btnCloseProfile.setOnClickListener(v -> dialog.dismiss());
        
        dialog.setContentView(view);
        // Make background transparent so custom shape shows
        if (dialog.getWindow() != null) {
            dialog.getWindow().findViewById(com.google.android.material.R.id.design_bottom_sheet)
                  .setBackgroundResource(android.R.color.transparent);
        }
        dialog.show();
    }

    private void updateNavigationMenuTitles(String languageTag) {
        Context localized = LocaleManager.createLocalizedContext(this, languageTag);
        Menu menu = navigationView.getMenu();

        MenuItem navHome = menu.findItem(R.id.nav_home);
        if (navHome != null) navHome.setTitle(localized.getString(R.string.admin_menu_home));

        MenuItem navUserFlow = menu.findItem(R.id.nav_user_flow);
        if (navUserFlow != null) navUserFlow.setTitle(localized.getString(R.string.admin_menu_user_flow));

        MenuItem navReviews = menu.findItem(R.id.nav_reviews);
        if (navReviews != null) navReviews.setTitle(localized.getString(R.string.admin_menu_reviews));

        MenuItem navStickers = menu.findItem(R.id.nav_stickers);
        if (navStickers != null) navStickers.setTitle(localized.getString(R.string.admin_menu_stickers));

        MenuItem navUsers = menu.findItem(R.id.nav_users);
        if (navUsers != null) navUsers.setTitle(localized.getString(R.string.admin_menu_users));

        MenuItem navSignOut = menu.findItem(R.id.nav_sign_out);
        if (navSignOut != null) navSignOut.setTitle(localized.getString(R.string.admin_menu_sign_out));
    }

    private void setupOnBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            // Already on home/overview
        } else if (id == R.id.nav_user_flow) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        } else if (id == R.id.nav_reviews) {
            startActivity(new Intent(this, AdminReviewsActivity.class));
        } else if (id == R.id.nav_stickers) {
            startActivity(new Intent(this, AdminStickersActivity.class));
        } else if (id == R.id.nav_frames) {
            startActivity(new Intent(this, AdminFramesActivity.class));
        } else if (id == R.id.nav_users) {
            startActivity(new Intent(this, AdminUsersActivity.class));
        } else if (id == R.id.nav_sign_out) {
            AuthRepository authRepository = ((AppContainer) getApplication()).getAuthRepository();
            authRepository.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }


    private void updateLanguageButtonA11y(String currentLanguageTag) {
        if (btnLanguageToggle == null) {
            return;
        }
        boolean isVietnamese = LocaleManager.LANG_VI.equalsIgnoreCase(currentLanguageTag);
        int contentDescRes = isVietnamese
                ? R.string.language_toggle_to_english
                : R.string.language_toggle_to_vietnamese;
        String contentDesc = getString(contentDescRes);
        btnLanguageToggle.setContentDescription(contentDesc);
        ViewCompat.setTooltipText(btnLanguageToggle, contentDesc);

        int flagRes = isVietnamese ? R.drawable.ic_flag_vn : R.drawable.ic_flag_uk;
        btnLanguageToggle.setImageResource(flagRes);
    }

    private void updateLocalizedUi(String languageTag) {
        Context localized = LocaleManager.createLocalizedContext(this, languageTag);

        if (btnLanguageToggle != null) {
            boolean isVietnamese = LocaleManager.LANG_VI.equals(languageTag);
            int contentDescRes = isVietnamese
                    ? R.string.language_toggle_to_english
                    : R.string.language_toggle_to_vietnamese;
            String contentDesc = localized.getString(contentDescRes);
            btnLanguageToggle.setContentDescription(contentDesc);
            ViewCompat.setTooltipText(btnLanguageToggle, contentDesc);

            int flagRes = isVietnamese ? R.drawable.ic_flag_vn : R.drawable.ic_flag_uk;
            btnLanguageToggle.setImageResource(flagRes);
        }

        if (tvAdminGreeting != null) {
            tvAdminGreeting.setText(localized.getString(R.string.admin_dashboard_greeting));
        }
        if (tvStatAccountsLabel != null) {
            tvStatAccountsLabel.setText(localized.getString(R.string.admin_stat_accounts));
        }
        if (tvStatImageDownloadsLabel != null) {
            tvStatImageDownloadsLabel.setText(localized.getString(R.string.admin_stat_image_downloads));
        }
        if (tvStatVideoDownloadsLabel != null) {
            tvStatVideoDownloadsLabel.setText(localized.getString(R.string.admin_stat_video_downloads));
        }

        updateNavigationMenuTitles(languageTag);
    }

    private void notifyRuntimeLanguageChanged(String languageTag) {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            dispatchLanguageToFragment(fragment, languageTag);
        }
    }

    private void dispatchLanguageToFragment(Fragment fragment, String languageTag) {
        if (fragment == null) {
            return;
        }
        if (fragment instanceof RuntimeLanguageUpdatable) {
            ((RuntimeLanguageUpdatable) fragment).onRuntimeLanguageChanged(languageTag);
        }
        for (Fragment child : fragment.getChildFragmentManager().getFragments()) {
            dispatchLanguageToFragment(child, languageTag);
        }
    }

    private String resolveAvatarInitial(String identity) {
        if (TextUtils.isEmpty(identity)) {
            return "A";
        }

        String source = identity.trim();
        int atIndex = source.indexOf('@');
        if (atIndex > 0) {
            source = source.substring(0, atIndex);
        }

        source = source.replace('.', ' ').replace('_', ' ').trim();
        if (source.isEmpty()) {
            return "A";
        }

        return source.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }
}
