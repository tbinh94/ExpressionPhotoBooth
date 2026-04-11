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
                                tvNavAvatarInitials.setText(resolveAvatarInitial(displayName));
                            }
                        }
                    });
        }

        updateNavigationMenuTitles(LocaleManager.getCurrentLanguage(this));
        setupOnBackPressed();
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
