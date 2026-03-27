package com.example.expressionphotobooth;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.expressionphotobooth.domain.model.UserRole;
import com.example.expressionphotobooth.domain.repository.AuthRepository;

public class LauncherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AuthRepository authRepository = ((AppContainer) getApplication()).getAuthRepository();
        if (!authRepository.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        authRepository.fetchCurrentRole(new AuthRepository.RoleCallback() {
            @Override
            public void onSuccess(UserRole role) {
                if (role == UserRole.ADMIN) {
                    startActivity(new Intent(LauncherActivity.this, AdminDashboardActivity.class));
                } else {
                    startActivity(new Intent(LauncherActivity.this, HomeActivity.class));
                }
                finish();
            }

            @Override
            public void onError(String message) {
                Intent loginIntent = new Intent(LauncherActivity.this, LoginActivity.class);
                loginIntent.putExtra(IntentKeys.EXTRA_NOTICE_TITLE, getString(R.string.auth_error_title));
                loginIntent.putExtra(IntentKeys.EXTRA_NOTICE_MESSAGE, message);
                startActivity(loginIntent);
                finish();
            }
        });
    }
}



