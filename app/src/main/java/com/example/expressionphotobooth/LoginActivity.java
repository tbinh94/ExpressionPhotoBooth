package com.example.expressionphotobooth;

import android.content.Intent;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.example.expressionphotobooth.domain.model.UserRole;
import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword, etName, etBirthday;
    private TextView tvLoginTitle, tvLoginSubtitle, tvLoadingMessage, tvLanguageBadge;
    private View layoutRegisterExtra, tvForgotPassword, layoutLoadingOverlay;
    private MaterialButton btnSignIn, btnRegister, btnGuest, btnLanguageToggle;
    private AuthRepository authRepository;
    private boolean isRegisterMode = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(com.example.expressionphotobooth.utils.LocaleManager.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.launcher_splash_bg)));
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        overridePendingTransition(0, 0);

        authRepository = ((AppContainer) getApplication()).getAuthRepository();
        
        btnLanguageToggle = findViewById(R.id.btnLanguageToggle);
        tvLanguageBadge = findViewById(R.id.tvLanguageBadge);
        updateLanguageToggleUi(com.example.expressionphotobooth.utils.LocaleManager.getCurrentLanguage(this));
        btnLanguageToggle.setOnClickListener(v -> {
            String language = com.example.expressionphotobooth.utils.LocaleManager.toggleLanguageWithoutRecreate(this);
            updateLocalizedUi(language);
        });

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
        tvForgotPassword.setOnClickListener(v -> doForgotPassword());
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

        if (getIntent().getBooleanExtra(IntentKeys.EXTRA_OPEN_REGISTER, false)) {
            setRegisterMode(true);
        }
    }

    private void doForgotPassword() {
        String email = getText(etEmail);
        if (TextUtils.isEmpty(email)) {
            HelpDialogUtils.showCenteredNotice(
                    this,
                    getString(R.string.auth_invalid_input_title),
                    getString(R.string.auth_forgot_password_email_required),
                    false
            );
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            HelpDialogUtils.showCenteredNotice(this, getString(R.string.auth_invalid_input_title), getString(R.string.auth_invalid_email_message), false);
            return;
        }

        setLoading(true, getString(R.string.auth_forgot_password_sending));
        authRepository.sendPasswordResetEmail(email, new AuthRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                setLoading(false, "");
                HelpDialogUtils.showCenteredNotice(
                        LoginActivity.this,
                        getString(R.string.auth_forgot_password_success_title),
                        getString(R.string.auth_forgot_password_success_message),
                        false
                );
            }

            @Override
            public void onError(String message) {
                setLoading(false, "");
                HelpDialogUtils.showCenteredNotice(
                        LoginActivity.this,
                        getString(R.string.auth_error_title),
                        message,
                        false
                );
            }
        });
    }

    private void updateLanguageToggleUi(String languageTag) {
        if (btnLanguageToggle == null) {
            return;
        }
        boolean isVietnamese = com.example.expressionphotobooth.utils.LocaleManager.LANG_VI.equalsIgnoreCase(languageTag);
        int contentDescRes = isVietnamese
                ? R.string.language_toggle_to_english
                : R.string.language_toggle_to_vietnamese;
        String contentDesc = com.example.expressionphotobooth.utils.LocaleManager.getString(this, contentDescRes, languageTag);
        btnLanguageToggle.setContentDescription(contentDesc);
        ViewCompat.setTooltipText(btnLanguageToggle, contentDesc);
        if (tvLanguageBadge != null) {
            int badgeRes = isVietnamese ? R.string.language_badge_vi : R.string.language_badge_en;
            animateLanguageBadgeText(com.example.expressionphotobooth.utils.LocaleManager.getString(this, badgeRes, languageTag));
        }
    }

    private void animateLanguageBadgeText(String newText) {
        if (tvLanguageBadge == null || TextUtils.isEmpty(newText)) {
            return;
        }
        CharSequence current = tvLanguageBadge.getText();
        if (newText.contentEquals(current)) {
            return;
        }
        tvLanguageBadge.animate().cancel();
        tvLanguageBadge.animate()
                .alpha(0f)
                .scaleX(0.88f)
                .scaleY(0.88f)
                .setDuration(120L)
                .withEndAction(() -> {
                    tvLanguageBadge.setText(newText);
                    tvLanguageBadge.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(120L)
                            .start();
                })
                .start();
    }

    private void updateLocalizedUi(String languageTag) {
        updateLanguageToggleUi(languageTag);

        if (tvLoginTitle != null) {
            tvLoginTitle.setText(com.example.expressionphotobooth.utils.LocaleManager.getString(this,
                    isRegisterMode ? R.string.auth_register_title : R.string.auth_title,
                    languageTag));
        }
        if (tvLoginSubtitle != null) {
            tvLoginSubtitle.setText(com.example.expressionphotobooth.utils.LocaleManager.getString(this,
                    isRegisterMode ? R.string.auth_register_subtitle : R.string.auth_subtitle,
                    languageTag));
        }
        if (btnSignIn != null) {
            btnSignIn.setText(com.example.expressionphotobooth.utils.LocaleManager.getString(this,
                    isRegisterMode ? R.string.auth_confirm_register : R.string.auth_sign_in,
                    languageTag));
        }
        if (btnRegister != null) {
            btnRegister.setText(com.example.expressionphotobooth.utils.LocaleManager.getString(this,
                    isRegisterMode ? R.string.auth_back_to_login : R.string.auth_register,
                    languageTag));
        }
        if (btnGuest != null) {
            btnGuest.setText(com.example.expressionphotobooth.utils.LocaleManager.getString(this,
                    R.string.auth_sign_in_as_guest,
                    languageTag));
        }
        if (tvForgotPassword instanceof TextView) {
            ((TextView) tvForgotPassword).setText(com.example.expressionphotobooth.utils.LocaleManager.getString(this,
                    R.string.auth_forgot_password,
                    languageTag));
        }
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
           if (!isBirthYearValid(getText(etBirthday))) {
               HelpDialogUtils.showCenteredNotice(this, getString(R.string.auth_invalid_input_title), getString(R.string.auth_invalid_birth_year_message), false);
               return false;
           }
        }
        return true;
    }

    private boolean isBirthYearValid(String birthdayText) {
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        format.setLenient(false);
        try {
            Date birthday = format.parse(birthdayText);
            if (birthday == null) {
                return false;
            }
            Calendar birthCal = Calendar.getInstance();
            birthCal.setTime(birthday);
            int birthYear = birthCal.get(Calendar.YEAR);
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            return birthYear <= currentYear;
        } catch (ParseException e) {
            return false;
        }
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

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }
}
