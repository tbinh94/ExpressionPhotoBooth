package com.example.expressionphotobooth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.google.android.material.button.MaterialButton;

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

        String email = authRepository.getCurrentEmail();
        tvAdminEmail.setText(getString(R.string.admin_logged_in_as, email == null ? "admin" : email));

        btnOpenUserFlow.setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });

        btnSignOut.setOnClickListener(v -> {
            authRepository.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }
}

