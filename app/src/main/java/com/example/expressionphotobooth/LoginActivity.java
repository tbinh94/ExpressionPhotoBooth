package com.example.expressionphotobooth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.expressionphotobooth.domain.model.UserRole;
import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword, etName, etBirthday;
    private TextView tvLoginTitle, tvLoginSubtitle, tvLoadingMessage;
    private View layoutRegisterExtra, tvForgotPassword, layoutLoadingOverlay;
    private MaterialButton btnSignIn, btnRegister, btnGuest;
    private AuthRepository authRepository;
    private boolean isRegisterMode = false;

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

        tvLoginTitle = findViewById(R.id.tvLoginTitle);
        tvLoginSubtitle = findViewById(R.id.tvLoginSubtitle);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etName = findViewById(R.id.etName);
        etBirthday = findViewById(R.id.etBirthday);
        layoutRegisterExtra = findViewById(R.id.layoutRegisterExtra);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        btnSignIn = findViewById(R.id.btnSignIn);
        btnRegister = findViewById(R.id.btnRegister);
        btnGuest = findViewById(R.id.btnGuest);
        
        layoutLoadingOverlay = findViewById(R.id.layoutLoadingOverlay);
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage);

        etBirthday.setOnClickListener(v -> showDatePicker());
        btnGuest.setOnClickListener(v -> doSignInAsGuest());
        btnSignIn.setOnClickListener(v -> {
            if (isRegisterMode) {
                doRegister();
            } else {
                doSignIn();
            }
        });
        btnRegister.setOnClickListener(v -> {
            if (!isRegisterMode) {
                setRegisterMode(true);
            } else {
                setRegisterMode(false);
            }
        });
    }

    private void showDatePicker() {
        com.google.android.material.datepicker.MaterialDatePicker<Long> picker = 
            com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker().build();
        picker.addOnPositiveButtonClickListener(selection -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(selection);
            etBirthday.setText(android.text.format.DateFormat.format("dd/MM/yyyy", cal).toString());
        });
        picker.show(getSupportFragmentManager(), "DP");
    }

    private void setRegisterMode(boolean register) {
        this.isRegisterMode = register;
        layoutRegisterExtra.setVisibility(register ? View.VISIBLE : View.GONE);
        tvForgotPassword.setVisibility(register ? View.GONE : View.VISIBLE);
        btnGuest.setVisibility(View.VISIBLE);
        
        tvLoginTitle.setText(register ? getString(R.string.auth_register_title) : getString(R.string.auth_title));
        tvLoginSubtitle.setText(register ? getString(R.string.auth_register_subtitle) : getString(R.string.auth_subtitle));
        
        btnSignIn.setText(register ? getString(R.string.auth_confirm_register) : getString(R.string.auth_sign_in));
        btnRegister.setText(register ? getString(R.string.auth_back_to_login) : getString(R.string.auth_register));
        
        btnSignIn.setIconResource(register ? R.drawable.ic_person_add_24 : R.drawable.ic_login_24);
        btnRegister.setIconResource(register ? R.drawable.ic_arrow_back_24 : R.drawable.ic_person_add_24);
    }

    private void doSignIn() {
        String email = getText(etEmail);
        String password = getText(etPassword);

        if (!isInputValid(email, password)) {
            return;
        }

        setLoading(true, getString(R.string.auth_loading));
        authRepository.signIn(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(com.example.expressionphotobooth.domain.model.AuthSession session) {
                setLoading(false, "");
                routeByRole();
            }

            @Override
            public void onError(String message) {
                setLoading(false, "");
                HelpDialogUtils.showCenteredNotice(
                        LoginActivity.this,
                        getString(R.string.auth_sign_in_failed_title),
                        message,
                        false
                );
            }
        });
    }

    private void doSignInAsGuest() {
        setLoading(true, getString(R.string.auth_loading));
        authRepository.signInAsGuest(new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(com.example.expressionphotobooth.domain.model.AuthSession session) {
                setLoading(false, "");
                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                finish();
            }

            @Override
            public void onError(String message) {
                setLoading(false, "");
                HelpDialogUtils.showCenteredNotice(LoginActivity.this, getString(R.string.auth_error_title), message, false);
            }
        });
    }

    private void doRegister() {
        String email = getText(etEmail);
        String password = getText(etPassword);
        String name = getText(etName);
        String birthday = getText(etBirthday);

        if (!isInputValid(email, password)) {
            return;
        }

        setLoading(true, getString(R.string.auth_register_creating));
        authRepository.register(email, password, name, birthday, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(com.example.expressionphotobooth.domain.model.AuthSession session) {
                setLoading(false, "");
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
                setLoading(false, "");
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
            HelpDialogUtils.showCenteredNotice(this, getString(R.string.auth_invalid_input_title), getString(R.string.auth_invalid_input_message), false);
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            HelpDialogUtils.showCenteredNotice(this, getString(R.string.auth_invalid_input_title), getString(R.string.auth_invalid_email_message), false);
            return false;
        }
        if (password.length() < 6) {
            HelpDialogUtils.showCenteredNotice(this, getString(R.string.auth_invalid_input_title), getString(R.string.auth_invalid_password_message), false);
            return false;
        }
        if (isRegisterMode) {
           if (TextUtils.isEmpty(getText(etName))) {
               HelpDialogUtils.showCenteredNotice(this, getString(R.string.auth_invalid_input_title), getString(R.string.auth_invalid_name_message), false);
               return false;
           }
           if (TextUtils.isEmpty(getText(etBirthday))) {
               HelpDialogUtils.showCenteredNotice(this, getString(R.string.auth_invalid_input_title), getString(R.string.auth_invalid_birthday_message), false);
               return false;
           }
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

    private void setLoading(boolean isLoading, String message) {
        layoutLoadingOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        if (isLoading && !TextUtils.isEmpty(message)) {
            tvLoadingMessage.setText(message);
        }
        
        btnSignIn.setEnabled(!isLoading);
        btnRegister.setEnabled(!isLoading);
        btnGuest.setEnabled(!isLoading);
    }

    private String getText(TextInputEditText editText) {
        CharSequence value = editText.getText();
        return value == null ? "" : value.toString().trim();
    }
}
