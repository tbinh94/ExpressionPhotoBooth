package com.example.expressionphotobooth;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.google.android.material.navigation.NavigationView;

public class AdminDashboardActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private com.google.android.material.button.MaterialButton btnLanguageToggle;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_admin_dashboard);

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

        TextView tvAdminEmail = findViewById(R.id.tvAdminEmail);
        String email = authRepository.getCurrentEmail();
        tvAdminEmail.setText(email == null ? getString(R.string.admin_email_fallback) : email);

        btnLanguageToggle = findViewById(R.id.btnLanguageToggle);
        updateLanguageButtonText();
        btnLanguageToggle.setOnClickListener(v -> {
            com.example.expressionphotobooth.utils.LocaleManager.toggleLanguage(this);
            recreate();
        });

        ViewPager2 viewPager = findViewById(R.id.viewPager);

        AdminPagerAdapter adapter = new AdminPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // Update Nav Header email
        View headerView = navigationView.getHeaderView(0);
        TextView tvNavEmail = headerView.findViewById(R.id.tvNavHeaderSubtitle);
        if (tvNavEmail != null) {
            tvNavEmail.setText(email == null ? getString(R.string.admin_email_fallback) : email);
        }

        updateNavigationMenuTitles();
    }

    private void updateNavigationMenuTitles() {
        Menu menu = navigationView.getMenu();
        
        // Manual update for menu titles based on current language
        MenuItem navHome = menu.findItem(R.id.nav_home);
        if (navHome != null) navHome.setTitle(com.example.expressionphotobooth.utils.LocaleManager.isVietnamese() ? "Trang chủ" : "Home");
        
        MenuItem navUserFlow = menu.findItem(R.id.nav_user_flow);
        if (navUserFlow != null) navUserFlow.setTitle(com.example.expressionphotobooth.utils.LocaleManager.isVietnamese() ? "Trải nghiệm người dùng" : "User Experience");
        
        MenuItem navReviews = menu.findItem(R.id.nav_reviews);
        if (navReviews != null) navReviews.setTitle(com.example.expressionphotobooth.utils.LocaleManager.isVietnamese() ? "Quản lý đánh giá" : "Review Management");
        
        MenuItem navStickers = menu.findItem(R.id.nav_stickers);
        if (navStickers != null) navStickers.setTitle(com.example.expressionphotobooth.utils.LocaleManager.isVietnamese() ? "Quản lý Sticker" : "Manage Stickers");
        
        MenuItem navUsers = menu.findItem(R.id.nav_users);
        if (navUsers != null) navUsers.setTitle(com.example.expressionphotobooth.utils.LocaleManager.isVietnamese() ? "Quản lý người dùng" : "User Management");

        MenuItem navSignOut = menu.findItem(R.id.nav_sign_out);
        if (navSignOut != null) navSignOut.setTitle(com.example.expressionphotobooth.utils.LocaleManager.isVietnamese() ? "Đăng xuất" : "Sign Out");

        // System group title
        // In XML it's defined as a sub-menu title, might be harder to change dynamically without a header
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

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private void updateLanguageButtonText() {
        if (btnLanguageToggle == null) {
            return;
        }
        int textRes = com.example.expressionphotobooth.utils.LocaleManager.isVietnamese()
                ? R.string.home_switch_to_english
                : R.string.home_switch_to_vietnamese;
        btnLanguageToggle.setText(textRes);
    }
}
