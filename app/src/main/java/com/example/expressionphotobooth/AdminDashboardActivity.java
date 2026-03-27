package com.example.expressionphotobooth;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;

public class AdminDashboardActivity extends AppCompatActivity {

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
        MaterialButton btnOpenUserFlow = findViewById(R.id.btnOpenUserFlow);
        MaterialButton btnSignOut = findViewById(R.id.btnSignOut);
        ImageButton btnAdminMenu = findViewById(R.id.btnAdminMenu);
        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        NavigationView navView = findViewById(R.id.navViewAdmin);

        String email = authRepository.getCurrentEmail();
        tvAdminEmail.setText(getString(R.string.admin_logged_in_as, email == null ? "admin" : email));

        btnAdminMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.END));

        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_reviews) {
                startActivity(new Intent(this, AdminReviewsActivity.class));
            } else if (id == R.id.nav_settings) {
                Toast.makeText(this, "Cài đặt hệ thống", Toast.LENGTH_SHORT).show();
            }
            drawerLayout.closeDrawer(GravityCompat.END);
            return true;
        });

        btnOpenUserFlow.setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });


        btnSignOut.setOnClickListener(v -> {
            authRepository.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        // toolbarAdmin click to go back is handled by the NavigationIcon set in XML or manifest
        // Do NOT call finish() here - it conflicts with the review panel overlay
    }

}


