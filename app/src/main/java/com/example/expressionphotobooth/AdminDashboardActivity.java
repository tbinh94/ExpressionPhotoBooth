package com.example.expressionphotobooth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.expressionphotobooth.domain.repository.AuthRepository;

public class AdminDashboardActivity extends AppCompatActivity {

    private com.google.android.material.button.MaterialButton btnLanguageToggle;

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
