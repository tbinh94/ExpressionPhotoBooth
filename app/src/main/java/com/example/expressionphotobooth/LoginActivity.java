package com.example.expressionphotobooth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.expressionphotobooth.domain.model.UserRole;
import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private MaterialButton btnSignIn;
    private MaterialButton btnRegister;
    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        authRepository = ((AppContainer) getApplication()).getAuthRepository();
        String noticeTitle = getIntent().getStringExtra(IntentKeys.EXTRA_NOTICE_TITLE);
        String noticeMessage = getIntent().getStringExtra(IntentKeys.EXTRA_NOTICE_MESSAGE);
        if (!TextUtils.isEmpty(noticeMessage)) {
            HelpDialogUtils.showCenteredNotice(
                    this,
                    TextUtils.isEmpty(noticeTitle) ? getString(R.string.auth_error_title) : noticeTitle,
                    noticeMessage,
                    getString(R.string.auth_sign_out_title).equals(noticeTitle)
            );
        }

        if (authRepository.isLoggedIn()) {
            routeByRole();
            return;
        }

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSignIn = findViewById(R.id.btnSignIn);
        btnRegister = findViewById(R.id.btnRegister);

        btnSignIn.setOnClickListener(v -> doSignIn());
        btnRegister.setOnClickListener(v -> doRegister());
    }

    private void doSignIn() {
        String email = getText(etEmail);
        String password = getText(etPassword);

        if (!isInputValid(email, password)) {
            return;
        }

        setLoading(true);
        authRepository.signIn(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(com.example.expressionphotobooth.domain.model.AuthSession session) {
                setLoading(false);
                routeByRole();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                HelpDialogUtils.showCenteredNotice(
                        LoginActivity.this,
                        getString(R.string.auth_sign_in_failed_title),
                        message,
                        false
                );
            }
        });
    }

    private void doRegister() {
        String email = getText(etEmail);
        String password = getText(etPassword);

        if (!isInputValid(email, password)) {
            return;
        }

        setLoading(true);
        authRepository.register(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(com.example.expressionphotobooth.domain.model.AuthSession session) {
                setLoading(false);
                HelpDialogUtils.showCenteredNotice(
                        LoginActivity.this,
                        getString(R.string.auth_register_success_title),
                        getString(R.string.auth_register_success_message),
                        true
                );
                routeByRole();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                HelpDialogUtils.showCenteredNotice(
                        LoginActivity.this,
                        getString(R.string.auth_register_failed_title),
                        message,
                        false
                );
            }
        });
    }

    private boolean isInputValid(String email, String password) {
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            HelpDialogUtils.showCenteredNotice(
                    this,
                    getString(R.string.auth_invalid_input_title),
                    getString(R.string.auth_invalid_input_message),
                    false
            );
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            HelpDialogUtils.showCenteredNotice(
                    this,
                    getString(R.string.auth_invalid_input_title),
                    getString(R.string.auth_invalid_email_message),
                    false
            );
            return false;
        }
        if (password.length() < 6) {
            HelpDialogUtils.showCenteredNotice(
                    this,
                    getString(R.string.auth_invalid_input_title),
                    getString(R.string.auth_invalid_password_message),
                    false
            );
            return false;
        }
        return true;
    }

    private void routeByRole() {
        authRepository.fetchCurrentRole(new AuthRepository.RoleCallback() {
            @Override
            public void onSuccess(UserRole role) {
                Intent intent = new Intent(LoginActivity.this,
                        role == UserRole.ADMIN ? AdminDashboardActivity.class : HomeActivity.class);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String message) {
                authRepository.signOut();
                HelpDialogUtils.showCenteredNotice(
                        LoginActivity.this,
                        getString(R.string.auth_error_title),
                        message,
                        false
                );
            }
        });
    }

    private void setLoading(boolean isLoading) {
        btnSignIn.setEnabled(!isLoading);
        btnRegister.setEnabled(!isLoading);
        btnSignIn.setText(isLoading ? R.string.auth_loading : R.string.auth_sign_in);
        btnRegister.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE);
    }

    private String getText(TextInputEditText editText) {
        CharSequence value = editText.getText();
        return value == null ? "" : value.toString().trim();
    }
}



