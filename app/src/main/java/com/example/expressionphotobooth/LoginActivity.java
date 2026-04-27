package com.example.expressionphotobooth;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import com.example.expressionphotobooth.domain.model.AuthSession;
import com.example.expressionphotobooth.domain.model.UserRole;
import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * LoginActivity — manages sign-in and registration flows.
 *
 * Phase 1 (Security): Google Client ID from resources, anti-enumeration forgot-password,
 *   client-side rate limiting (5 attempts / 30 s), all strings in resources.
 *
 * Phase 2 (Performance): role cached in SharedPreferences, routeBySession() avoids
 *   second Firestore call, form state survives screen rotation.
 *
 * Phase 3 (UX): Confirm Password field, live Password Strength Indicator (4 segments),
 *   Email Verification enforced on sign-in and after registration.
 */
public class LoginActivity extends AppCompatActivity {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final int    MAX_LOGIN_ATTEMPTS = 5;
    private static final long   LOCKOUT_MS         = 30_000L; // 30 seconds
    private static final String STATE_REGISTER_MODE = "isRegisterMode";
    private static final String STATE_EMAIL_DRAFT   = "emailDraft";

    // ── Views ─────────────────────────────────────────────────────────────────
    private TextInputEditText    etEmail, etPassword, etName, etBirthday;
    private TextInputEditText    etConfirmPassword;                          // Phase 3
    private TextView             tvLoginTitle, tvLoginSubtitle, tvLoadingMessage, tvGoogleSignInText;
    private TextView             tvPasswordStrength;                          // Phase 3
    private View                 layoutRegisterExtra, tvForgotPassword, layoutLoadingOverlay;
    private View                 layoutPasswordStrength;                      // Phase 3
    private View                 tilConfirmPassword;                          // Phase 3
    private View                 pwdSeg1, pwdSeg2, pwdSeg3, pwdSeg4;         // Phase 3
    private MaterialButton       btnSignIn, btnRegister, btnGuest;
    private androidx.cardview.widget.CardView btnGoogleSignIn;
    private android.widget.ImageView btnLanguageToggle;

    // ── State ─────────────────────────────────────────────────────────────────
    private AuthRepository       authRepository;
    private GoogleSignInClient   googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private boolean              isRegisterMode = false;

    /** Phase 1: Rate limiting state */
    private int  loginAttempts       = 0;
    private long lastFailedAttemptMs = 0L;

    // ── Locale ────────────────────────────────────────────────────────────────

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(com.example.expressionphotobooth.utils.LocaleManager.wrapContext(newBase));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.launcher_splash_bg)));
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        overridePendingTransition(0, 0);

        authRepository = ((AppContainer) getApplication()).getAuthRepository();

        // ── Language toggle ──────────────────────────────────────────────────
        btnLanguageToggle = findViewById(R.id.btnLanguageToggle);
        updateLanguageToggleUi(com.example.expressionphotobooth.utils.LocaleManager.getCurrentLanguage(this));
        btnLanguageToggle.setOnClickListener(v -> {
            String language = com.example.expressionphotobooth.utils.LocaleManager.toggleLanguageWithoutRecreate(this);
            updateLocalizedUi(language);
        });

        // ── Notice from deep-link intent ─────────────────────────────────────
        String noticeTitle   = getIntent().getStringExtra(IntentKeys.EXTRA_NOTICE_TITLE);
        String noticeMessage = getIntent().getStringExtra(IntentKeys.EXTRA_NOTICE_MESSAGE);
        if (!TextUtils.isEmpty(noticeMessage)) {
            HelpDialogUtils.showCenteredNotice(
                    this,
                    TextUtils.isEmpty(noticeTitle) ? getString(R.string.auth_error_title) : noticeTitle,
                    noticeMessage,
                    getString(R.string.auth_sign_out_title).equals(noticeTitle)
            );
        }

        // ── Already logged in: route immediately ─────────────────────────────
        if (authRepository.isLoggedIn()) {
            routeByRole();
            return;
        }

        // ── Bind views ───────────────────────────────────────────────────────
        tvLoginTitle         = findViewById(R.id.tvLoginTitle);
        tvLoginSubtitle      = findViewById(R.id.tvLoginSubtitle);
        etEmail              = findViewById(R.id.etEmail);
        etPassword           = findViewById(R.id.etPassword);
        etName               = findViewById(R.id.etName);
        etBirthday           = findViewById(R.id.etBirthday);
        layoutRegisterExtra  = findViewById(R.id.layoutRegisterExtra);
        tvForgotPassword     = findViewById(R.id.tvForgotPassword);
        btnSignIn            = findViewById(R.id.btnSignIn);
        btnRegister          = findViewById(R.id.btnRegister);
        btnGuest             = findViewById(R.id.btnGuest);
        layoutLoadingOverlay = findViewById(R.id.layoutLoadingOverlay);
        tvLoadingMessage     = findViewById(R.id.tvLoadingMessage);
        btnGoogleSignIn      = findViewById(R.id.btnGoogleSignIn);
        tvGoogleSignInText   = btnGoogleSignIn.findViewById(R.id.tvGoogleSignInLabel);

        // Phase 3: new register-mode views
        etConfirmPassword    = findViewById(R.id.etConfirmPassword);
        layoutPasswordStrength = findViewById(R.id.layoutPasswordStrength);
        tilConfirmPassword   = findViewById(R.id.tilConfirmPassword);
        tvPasswordStrength   = findViewById(R.id.tvPasswordStrength);
        pwdSeg1 = findViewById(R.id.pwdSeg1);
        pwdSeg2 = findViewById(R.id.pwdSeg2);
        pwdSeg3 = findViewById(R.id.pwdSeg3);
        pwdSeg4 = findViewById(R.id.pwdSeg4);
        setupPasswordStrengthWatcher();

        // ── Google Sign-In (Phase 1: Client ID from resources, not hardcoded) ─
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        handleGoogleSignInResult(GoogleSignIn.getSignedInAccountFromIntent(result.getData()));
                    } else {
                        setLoading(false, "");
                    }
                });

        // ── Click listeners ──────────────────────────────────────────────────
        etBirthday.setOnClickListener(v -> showDatePicker());
        tvForgotPassword.setOnClickListener(v -> doForgotPassword());
        btnGuest.setOnClickListener(v -> doSignInAsGuest());
        btnGoogleSignIn.setOnClickListener(v -> doGoogleSignIn());

        btnSignIn.setOnClickListener(v -> {
            if (isRegisterMode) doRegister();
            else                doSignIn();
        });
        btnRegister.setOnClickListener(v -> setRegisterMode(!isRegisterMode));

        // ── Phase 2: Restore form state after rotation ────────────────────────
        if (savedInstanceState != null) {
            boolean restoreRegister = savedInstanceState.getBoolean(STATE_REGISTER_MODE, false);
            if (restoreRegister) {
                setRegisterMode(true);
            }
            String emailDraft = savedInstanceState.getString(STATE_EMAIL_DRAFT, "");
            if (!TextUtils.isEmpty(emailDraft) && etEmail != null) {
                etEmail.setText(emailDraft);
            }
        } else if (getIntent().getBooleanExtra(IntentKeys.EXTRA_OPEN_REGISTER, false)) {
            setRegisterMode(true);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Phase 2: Persist form state across config changes (e.g. screen rotation)
        outState.putBoolean(STATE_REGISTER_MODE, isRegisterMode);
        if (etEmail != null) {
            outState.putString(STATE_EMAIL_DRAFT, getText(etEmail));
        }
    }

    // ── Phase 3: Password Strength ────────────────────────────────────────────

    private void setupPasswordStrengthWatcher() {
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isRegisterMode) updateStrengthUi(s.toString());
            }
        });
    }

    /** Returns 0=empty, 1=weak, 2=fair, 3=good, 4=strong. */
    private int calcPasswordStrength(String pwd) {
        if (pwd.isEmpty()) return 0;
        int score = 0;
        if (pwd.length() >= 8)  score++;
        if (pwd.matches(".*[A-Z].*")) score++;
        if (pwd.matches(".*[0-9].*")) score++;
        if (pwd.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':,./<>?].*")) score++;
        return score == 0 ? 1 : score; // at least 1 if not empty
    }

    private void updateStrengthUi(String pwd) {
        if (layoutPasswordStrength == null) return;
        int strength = calcPasswordStrength(pwd);
        if (pwd.isEmpty()) {
            layoutPasswordStrength.setVisibility(View.GONE);
            return;
        }
        layoutPasswordStrength.setVisibility(View.VISIBLE);

        int[] segColors = {0xFF_E4_EA_F6, 0xFF_E4_EA_F6, 0xFF_E4_EA_F6, 0xFF_E4_EA_F6};
        int fillColor;
        String label;
        switch (strength) {
            case 1:
                fillColor = 0xFF_EF_44_44; // red — weak
                label = getString(R.string.auth_pwd_strength_weak);
                segColors[0] = fillColor;
                break;
            case 2:
                fillColor = 0xFF_F9_73_16; // orange — fair
                label = getString(R.string.auth_pwd_strength_fair);
                segColors[0] = fillColor; segColors[1] = fillColor;
                break;
            case 3:
                fillColor = 0xFF_EA_B3_08; // yellow — good
                label = getString(R.string.auth_pwd_strength_good);
                segColors[0] = fillColor; segColors[1] = fillColor; segColors[2] = fillColor;
                break;
            default: // 4
                fillColor = 0xFF_22_C5_5E; // green — strong
                label = getString(R.string.auth_pwd_strength_strong);
                segColors[0] = fillColor; segColors[1] = fillColor;
                segColors[2] = fillColor; segColors[3] = fillColor;
                break;
        }

        animateSegment(pwdSeg1, segColors[0]);
        animateSegment(pwdSeg2, segColors[1]);
        animateSegment(pwdSeg3, segColors[2]);
        animateSegment(pwdSeg4, segColors[3]);

        tvPasswordStrength.setText(label);
        tvPasswordStrength.setTextColor(fillColor);
    }

    private void animateSegment(View seg, int toColor) {
        if (seg == null) return;
        int fromColor = 0xFF_E4_EA_F6;
        ValueAnimator anim = ValueAnimator.ofArgb(fromColor, toColor);
        anim.setDuration(220);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> seg.setBackgroundColor((int) a.getAnimatedValue()));
        anim.start();
    }


    /**
     * Returns true if the user is currently locked out from further attempts.
     * Resets the counter automatically after LOCKOUT_MS has elapsed.
     */
    private boolean isRateLimited() {
        long now = System.currentTimeMillis();
        if (loginAttempts >= MAX_LOGIN_ATTEMPTS) {
            long elapsed   = now - lastFailedAttemptMs;
            long remaining = LOCKOUT_MS - elapsed;
            if (remaining > 0) {
                int seconds = (int) Math.ceil(remaining / 1000.0);
                HelpDialogUtils.showCenteredNotice(
                        this,
                        getString(R.string.auth_rate_limit_title),
                        getString(R.string.auth_rate_limit_message, seconds),
                        false
                );
                return true;
            }
            // Lockout period expired — reset counter
            loginAttempts = 0;
        }
        return false;
    }

    private void recordFailedAttempt() {
        loginAttempts++;
        lastFailedAttemptMs = System.currentTimeMillis();
    }

    private void resetRateLimitOnSuccess() {
        loginAttempts       = 0;
        lastFailedAttemptMs = 0L;
    }

    // ── Forgot Password (Phase 1 — anti-enumeration) ──────────────────────────

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
            HelpDialogUtils.showCenteredNotice(
                    this,
                    getString(R.string.auth_invalid_input_title),
                    getString(R.string.auth_invalid_email_message),
                    false
            );
            return;
        }

        setLoading(true, getString(R.string.auth_forgot_password_sending));
        authRepository.sendPasswordResetEmail(email, new AuthRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                setLoading(false, "");
                // Phase 1: Always show neutral message — never reveal whether email exists
                HelpDialogUtils.showCenteredNotice(
                        LoginActivity.this,
                        getString(R.string.auth_forgot_password_success_title),
                        getString(R.string.auth_forgot_password_neutral_message),
                        false
                );
            }

            @Override
            public void onError(String message) {
                setLoading(false, "");
                // Phase 1: Show neutral message even on error to avoid enumeration
                HelpDialogUtils.showCenteredNotice(
                        LoginActivity.this,
                        getString(R.string.auth_forgot_password_success_title),
                        getString(R.string.auth_forgot_password_neutral_message),
                        false
                );
            }
        });
    }

    // ── Sign In ───────────────────────────────────────────────────────────────

    private void doSignIn() {
        // Phase 1: check rate limit before any network call
        if (isRateLimited()) return;

        String email    = getText(etEmail);
        String password = getText(etPassword);
        if (!isInputValid(email, password)) return;

        setLoading(true, getString(R.string.auth_loading));
        authRepository.signIn(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(AuthSession session) {
                resetRateLimitOnSuccess();
                setLoading(false, "");
                routeBySession(session);
            }

            @Override
            public void onError(String message) {
                setLoading(false, "");
                // Phase 3: special marker from FirebaseAuthRepository
                if (message.startsWith("EMAIL_NOT_VERIFIED:")) {
                    String unverifiedEmail = message.substring("EMAIL_NOT_VERIFIED:".length());
                    recordFailedAttempt();
                    showEmailNotVerifiedDialog(unverifiedEmail);
                    return;
                }
                recordFailedAttempt();
                HelpDialogUtils.showCenteredNotice(
                        LoginActivity.this,
                        getString(R.string.auth_sign_in_failed_title),
                        message,
                        false
                );
            }
        });
    }

    // ── Sign In as Guest ──────────────────────────────────────────────────────

    private void doSignInAsGuest() {
        setLoading(true, getString(R.string.auth_loading));
        authRepository.signInAsGuest(new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(AuthSession session) {
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

    // ── Google Sign-In ────────────────────────────────────────────────────────

    private void doGoogleSignIn() {
        setLoading(true, getString(R.string.auth_loading));
        // Sign out first so the account picker always appears on every tap
        googleSignInClient.signOut().addOnCompleteListener(this,
                task -> googleSignInLauncher.launch(googleSignInClient.getSignInIntent()));
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            String idToken = account.getIdToken();
            if (idToken != null) {
                authRepository.signInWithGoogle(idToken, new AuthRepository.AuthCallback() {
                    @Override
                    public void onSuccess(AuthSession session) {
                        resetRateLimitOnSuccess();
                        setLoading(false, "");
                        routeBySession(session);
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false, "");
                        HelpDialogUtils.showCenteredNotice(LoginActivity.this, getString(R.string.auth_error_title), message, false);
                    }
                });
            } else {
                setLoading(false, "");
                // Phase 1: String resource instead of hardcoded
                HelpDialogUtils.showCenteredNotice(this, getString(R.string.auth_error_title), getString(R.string.auth_google_token_error), false);
            }
        } catch (ApiException e) {
            setLoading(false, "");
            String errorMsg;
            switch (e.getStatusCode()) {
                case 12500:
                    errorMsg = "Lỗi đăng nhập Google (12500). Kiểm tra SHA-1 trong Firebase Console.";
                    break;
                case 12501:
                    errorMsg = getString(R.string.auth_error_title);
                    break;
                default:
                    errorMsg = "Lỗi đăng nhập Google (code: " + e.getStatusCode() + ")";
                    break;
            }
            HelpDialogUtils.showCenteredNotice(this, getString(R.string.auth_error_title), errorMsg, false);
        }
    }

    // ── Register ──────────────────────────────────────────────────────────────

    private void doRegister() {
        String email    = getText(etEmail);
        String password = getText(etPassword);
        String name     = getText(etName);
        String birthday = getText(etBirthday);

        if (!isInputValid(email, password)) return;

        // Phase 3: Confirm password check
        String confirm = getText(etConfirmPassword);
        if (!password.equals(confirm)) {
            HelpDialogUtils.showCenteredNotice(
                    this,
                    getString(R.string.auth_invalid_input_title),
                    getString(R.string.auth_passwords_do_not_match),
                    false
            );
            return;
        }

        setLoading(true, getString(R.string.auth_register_creating));
        authRepository.register(email, password, name, birthday, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(AuthSession session) {
                setLoading(false, "");
                // Phase 3: uid marker from FirebaseAuthRepository signals verification sent
                if (session.getUid() != null && session.getUid().startsWith("VERIFY_EMAIL:")) {
                    String verifyEmail = session.getEmail();
                    HelpDialogUtils.showCenteredNotice(
                            LoginActivity.this,
                            getString(R.string.auth_verify_email_title),
                            getString(R.string.auth_verify_email_message, verifyEmail),
                            false
                    );
                    // Return to login mode after showing the notice
                    setRegisterMode(false);
                    return;
                }
                // Legacy path (verification already done / Google)
                HelpDialogUtils.showCenteredNotice(
                        LoginActivity.this,
                        getString(R.string.auth_register_success_title),
                        getString(R.string.auth_register_success_message),
                        true
                );
                routeBySession(session);
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

    // ── Navigation ────────────────────────────────────────────────────────────

    /**
     * Phase 2 + 3: Routes using role already in AuthSession.
     * Also handles email-verification flow after registration.
     */
    private void routeBySession(AuthSession session) {
        if (session.getUid() != null && session.getUid().startsWith("VERIFY_EMAIL:")) {
            // Should be handled in doRegister already; guard just in case
            setRegisterMode(false);
            return;
        }
        Intent intent = new Intent(
                LoginActivity.this,
                session.getRole() == UserRole.ADMIN ? AdminDashboardActivity.class : HomeActivity.class
        );
        startActivity(intent);
        finish();
    }

    /**
     * Fallback used when already logged in on activity start (role not yet in memory).
     * Also used by LauncherActivity — benefits from Phase 2 role cache.
     */
    private void routeByRole() {
        authRepository.fetchCurrentRole(new AuthRepository.RoleCallback() {
            @Override
            public void onSuccess(UserRole role) {
                Intent intent = new Intent(
                        LoginActivity.this,
                        role == UserRole.ADMIN ? AdminDashboardActivity.class : HomeActivity.class
                );
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

    // ── Validation ────────────────────────────────────────────────────────────

    private boolean isInputValid(String email, String password) {
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            HelpDialogUtils.showCenteredNotice(this,
                    getString(R.string.auth_invalid_input_title),
                    getString(R.string.auth_invalid_input_message), false);
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            HelpDialogUtils.showCenteredNotice(this,
                    getString(R.string.auth_invalid_input_title),
                    getString(R.string.auth_invalid_email_message), false);
            return false;
        }
        if (password.length() < 6) {
            HelpDialogUtils.showCenteredNotice(this,
                    getString(R.string.auth_invalid_input_title),
                    getString(R.string.auth_invalid_password_message), false);
            return false;
        }
        if (isRegisterMode) {
            if (TextUtils.isEmpty(getText(etName))) {
                HelpDialogUtils.showCenteredNotice(this,
                        getString(R.string.auth_invalid_input_title),
                        getString(R.string.auth_invalid_name_message), false);
                return false;
            }
            if (TextUtils.isEmpty(getText(etBirthday))) {
                HelpDialogUtils.showCenteredNotice(this,
                        getString(R.string.auth_invalid_input_title),
                        getString(R.string.auth_invalid_birthday_message), false);
                return false;
            }
            if (!isBirthYearValid(getText(etBirthday))) {
                HelpDialogUtils.showCenteredNotice(this,
                        getString(R.string.auth_invalid_input_title),
                        getString(R.string.auth_invalid_birth_year_message), false);
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
            if (birthday == null) return false;
            Calendar birthCal = Calendar.getInstance();
            birthCal.setTime(birthday);
            int birthYear   = birthCal.get(Calendar.YEAR);
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            return birthYear <= currentYear;
        } catch (ParseException e) {
            return false;
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void setRegisterMode(boolean register) {
        this.isRegisterMode = register;
        layoutRegisterExtra.setVisibility(register ? View.VISIBLE : View.GONE);
        // Phase 3: show/hide strength + confirm password
        layoutPasswordStrength.setVisibility(register ? View.GONE : View.GONE); // hidden until typing
        tilConfirmPassword.setVisibility(register ? View.VISIBLE : View.GONE);
        tvForgotPassword.setVisibility(register ? View.GONE : View.VISIBLE);
        btnGuest.setVisibility(View.VISIBLE);

        // Clear Phase 3 fields on mode switch
        if (!register && etConfirmPassword != null) etConfirmPassword.setText("");
        if (!register && tvPasswordStrength != null) {
            updateStrengthUi("");
            layoutPasswordStrength.setVisibility(View.GONE);
        }

        tvLoginTitle.setText(register ? getString(R.string.auth_register_title) : getString(R.string.auth_title));
        tvLoginSubtitle.setText(register ? getString(R.string.auth_register_subtitle) : getString(R.string.auth_subtitle));
        btnSignIn.setText(register ? getString(R.string.auth_confirm_register) : getString(R.string.auth_sign_in));
        btnRegister.setText(register ? getString(R.string.auth_back_to_login) : getString(R.string.auth_register));

        btnSignIn.setIconResource(register ? R.drawable.ic_person_add_24 : R.drawable.ic_login_24);
        btnRegister.setIconResource(register ? R.drawable.ic_arrow_back_24 : R.drawable.ic_person_add_24);
    }

    // ── Phase 3: Email not verified dialog ───────────────────────────────────

    /**
     * Shows a dialog explaining the account requires email verification,
     * with a "Resend" action that re-fires the verification email.
     */
    private void showEmailNotVerifiedDialog(String email) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.auth_verify_email_not_verified_title));
        builder.setMessage(getString(R.string.auth_verify_email_not_verified_message));
        builder.setPositiveButton(getString(R.string.common_ok), (d, w) -> d.dismiss());
        builder.setNeutralButton(getString(R.string.auth_verify_resend), (d, w) -> {
            // Re-sign-in briefly so Firebase has a current user to send to
            String pwd = getText(etPassword);
            if (TextUtils.isEmpty(pwd)) {
                HelpDialogUtils.showCenteredNotice(this,
                        getString(R.string.auth_error_title),
                        getString(R.string.auth_invalid_input_message), false);
                return;
            }
            setLoading(true, getString(R.string.auth_forgot_password_sending));
            // Sign in without verification gate to send email
            com.google.firebase.auth.FirebaseAuth.getInstance()
                    .signInWithEmailAndPassword(email, pwd)
                    .addOnSuccessListener(r -> {
                        com.google.firebase.auth.FirebaseUser u = r.getUser();
                        if (u != null) {
                            u.sendEmailVerification()
                                    .addOnSuccessListener(v -> {
                                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                                        setLoading(false, "");
                                        HelpDialogUtils.showCenteredNotice(this,
                                                getString(R.string.auth_verify_email_title),
                                                getString(R.string.auth_verify_resend_success), false);
                                    })
                                    .addOnFailureListener(e -> {
                                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                                        setLoading(false, "");
                                        HelpDialogUtils.showCenteredNotice(this,
                                                getString(R.string.auth_error_title),
                                                getString(R.string.auth_verify_resend_failed), false);
                                    });
                        } else {
                            setLoading(false, "");
                        }
                    })
                    .addOnFailureListener(e -> {
                        setLoading(false, "");
                        HelpDialogUtils.showCenteredNotice(this,
                                getString(R.string.auth_sign_in_failed_title),
                                e.getMessage(), false);
                    });
        });
        builder.show();
    }

    private void showDatePicker() {
        com.google.android.material.datepicker.MaterialDatePicker<Long> picker =
                com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker().build();
        picker.addOnPositiveButtonClickListener(selection -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(selection);
            etBirthday.setText(android.text.format.DateFormat.format("dd/MM/yyyy", cal).toString());
        });
        picker.show(getSupportFragmentManager(), "DP");
    }

    private void setLoading(boolean isLoading, String message) {
        layoutLoadingOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        if (isLoading && !TextUtils.isEmpty(message)) {
            tvLoadingMessage.setText(message);
        }
        btnSignIn.setEnabled(!isLoading);
        btnRegister.setEnabled(!isLoading);
        btnGuest.setEnabled(!isLoading);
        if (btnGoogleSignIn != null) {
            btnGoogleSignIn.setEnabled(!isLoading);
            btnGoogleSignIn.setAlpha(isLoading ? 0.5f : 1.0f);
        }
    }

    private void updateLanguageToggleUi(String languageTag) {
        if (btnLanguageToggle == null) return;
        boolean isVietnamese = com.example.expressionphotobooth.utils.LocaleManager.LANG_VI.equalsIgnoreCase(languageTag);
        int contentDescRes = isVietnamese ? R.string.language_toggle_to_english : R.string.language_toggle_to_vietnamese;
        String contentDesc = com.example.expressionphotobooth.utils.LocaleManager.getString(this, contentDescRes, languageTag);
        btnLanguageToggle.setContentDescription(contentDesc);
        ViewCompat.setTooltipText(btnLanguageToggle, contentDesc);
        btnLanguageToggle.setImageResource(isVietnamese ? R.drawable.ic_flag_vn : R.drawable.ic_flag_uk);
    }

    private void updateLocalizedUi(String languageTag) {
        updateLanguageToggleUi(languageTag);
        if (tvLoginTitle != null) {
            tvLoginTitle.setText(com.example.expressionphotobooth.utils.LocaleManager.getString(this,
                    isRegisterMode ? R.string.auth_register_title : R.string.auth_title, languageTag));
        }
        if (tvLoginSubtitle != null) {
            tvLoginSubtitle.setText(com.example.expressionphotobooth.utils.LocaleManager.getString(this,
                    isRegisterMode ? R.string.auth_register_subtitle : R.string.auth_subtitle, languageTag));
        }
        if (btnSignIn != null) {
            btnSignIn.setText(com.example.expressionphotobooth.utils.LocaleManager.getString(this,
                    isRegisterMode ? R.string.auth_confirm_register : R.string.auth_sign_in, languageTag));
        }
        if (btnRegister != null) {
            btnRegister.setText(com.example.expressionphotobooth.utils.LocaleManager.getString(this,
                    isRegisterMode ? R.string.auth_back_to_login : R.string.auth_register, languageTag));
        }
        if (btnGuest != null) {
            btnGuest.setText(com.example.expressionphotobooth.utils.LocaleManager.getString(this,
                    R.string.auth_sign_in_as_guest, languageTag));
        }
        if (tvGoogleSignInText != null) {
            tvGoogleSignInText.setText(com.example.expressionphotobooth.utils.LocaleManager.getString(this,
                    R.string.auth_google_sign_in, languageTag));
        }
        if (tvForgotPassword instanceof TextView) {
            ((TextView) tvForgotPassword).setText(com.example.expressionphotobooth.utils.LocaleManager.getString(this,
                    R.string.auth_forgot_password, languageTag));
        }
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
