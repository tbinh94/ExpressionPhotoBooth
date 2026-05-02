package com.example.expressionphotobooth.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.expressionphotobooth.domain.model.AuthSession;
import com.example.expressionphotobooth.domain.model.UserRole;
import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Firebase implementation of AuthRepository.
 *
 * Phase 2 improvements:
 * - Role is cached in SharedPreferences to avoid redundant Firestore fetches on cold start.
 * - signIn() now returns the role embedded in AuthSession, eliminating the second
 *   fetchCurrentRole() call that was previously triggered in LoginActivity.routeByRole().
 * - Forgot-password no longer leaks user-existence information (anti-enumeration fix).
 */
public class FirebaseAuthRepository implements AuthRepository {

    private static final String USERS_COLLECTION = "users";
    private static final String PREFS_NAME        = "auth_cache";
    private static final String KEY_CACHED_ROLE   = "cached_role";

    private final FirebaseAuth      firebaseAuth;
    private final FirebaseFirestore firestore;
    private final Context           context;

    public FirebaseAuthRepository(Context context) {
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.firestore    = FirebaseFirestore.getInstance();
        this.context      = context.getApplicationContext();
    }

    // ── Cache helpers ──────────────────────────────────────────────────────────

    private void cacheRole(@NonNull UserRole role) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CACHED_ROLE, role.toFirestoreValue())
                .apply();
    }

    @Nullable
    private UserRole getCachedRole() {
        String raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_CACHED_ROLE, null);
        return raw != null ? UserRole.from(raw) : null;
    }

    private void clearRoleCache() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_CACHED_ROLE)
                .apply();
    }

    // ── Basic checks ──────────────────────────────────────────────────────────

    @Override
    public boolean isLoggedIn() {
        return firebaseAuth.getCurrentUser() != null;
    }

    @Override
    public boolean isGuest() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        return user != null && user.isAnonymous();
    }

    @Override
    public String getCurrentUid() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    @Override
    public String getCurrentEmail() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        return user != null ? user.getEmail() : null;
    }

    // ── Sign-in (Email / Password) ────────────────────────────────────────────

    /**
     * Phase 2: Role is fetched once inside signIn() and embedded in AuthSession,
     * so callers (LoginActivity) no longer need a second fetchCurrentRole() call.
     */
    @Override
    public void signIn(String email, String password, AuthCallback callback) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) {
                        callback.onError("Không tìm thấy tài khoản sau khi đăng nhập.");
                        return;
                    }
                    
                    // Fast path: use cached role
                    UserRole cached = getCachedRole();
                    if (cached != null) {
                        handleVerificationAndSuccess(user, cached, callback);
                        refreshRoleInBackground(user.getUid());
                        return;
                    }
                    
                    // Slow path: fetch from Firestore first, then handle verification
                    firestore.collection(USERS_COLLECTION)
                            .document(user.getUid())
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                UserRole role = snapshot.exists()
                                        ? UserRole.from(snapshot.getString("role"))
                                        : UserRole.USER;
                                cacheRole(role);
                                handleVerificationAndSuccess(user, role, callback);
                            })
                            .addOnFailureListener(e -> {
                                firebaseAuth.signOut();
                                callback.onError(safeMessage(e, "Không thể lấy thông tin phân quyền."));
                            });
                })
                .addOnFailureListener(e -> callback.onError(safeMessage(e, "Đăng nhập thất bại. Vui lòng thử lại.")));
    }

    private void handleVerificationAndSuccess(FirebaseUser user, UserRole role, AuthCallback callback) {
        // Phase 3 exception: Admins and the default admin@gmail.com can bypass email verification
        boolean isAdmin = role == UserRole.ADMIN || (user.getEmail() != null && user.getEmail().equals("admin@gmail.com"));
        
        // Phase 3 exception: Legacy users (created before April 28, 2026) are grandfathered in
        boolean isLegacy = false;
        try {
            long cutoffTime = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse("2026-04-28").getTime();
            if (user.getMetadata() != null && user.getMetadata().getCreationTimestamp() < cutoffTime) {
                isLegacy = true;
            }
        } catch (Exception e) {
            // Ignored, fallback to false
        }
        
        if (!isAdmin && !isLegacy && !user.isEmailVerified()) {
            firebaseAuth.signOut();
            callback.onError("EMAIL_NOT_VERIFIED:" + user.getEmail());
            return;
        }
        
        callback.onSuccess(new AuthSession(user.getUid(), user.getEmail(), role, false));
    }

    /** Background refresh: keeps cache up-to-date without user-visible latency. */
    private void refreshRoleInBackground(String uid) {
        firestore.collection(USERS_COLLECTION).document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        cacheRole(UserRole.from(snapshot.getString("role")));
                    }
                });
    }

    // ── Google Sign-In ─────────────────────────────────────────────────────────

    @Override
    public void signInWithGoogle(String idToken, AuthCallback callback) {
        com.google.firebase.auth.AuthCredential credential =
                com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null);

        firebaseAuth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) {
                        callback.onError("Đăng nhập Google thành công nhưng không lấy được thông tin.");
                        return;
                    }
                    // Check if profile doc already exists in Firestore
                    firestore.collection(USERS_COLLECTION)
                            .document(user.getUid())
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                if (snapshot.exists()) {
                                    UserRole role = UserRole.from(snapshot.getString("role"));
                                    cacheRole(role);
                                    callback.onSuccess(new AuthSession(user.getUid(), user.getEmail(), role, false));
                                } else {
                                    // New Google user — create profile
                                    Map<String, Object> userDoc = new HashMap<>();
                                    userDoc.put("email",       user.getEmail());
                                    userDoc.put("displayName", user.getDisplayName());
                                    userDoc.put("photoUrl",    user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null);
                                    userDoc.put("role",        UserRole.USER.toFirestoreValue());
                                    userDoc.put("isActive",    true);
                                    userDoc.put("updatedAt",   System.currentTimeMillis());
                                    userDoc.put("createdAt",   System.currentTimeMillis());

                                    firestore.collection(USERS_COLLECTION)
                                            .document(user.getUid())
                                            .set(userDoc)
                                            .addOnSuccessListener(unused -> {
                                                cacheRole(UserRole.USER);
                                                callback.onSuccess(new AuthSession(user.getUid(), user.getEmail(), UserRole.USER, false));
                                            })
                                            .addOnFailureListener(e -> callback.onError(safeMessage(e, "Đăng nhập Google thành công nhưng lỗi tạo hồ sơ.")));
                                }
                            })
                            .addOnFailureListener(e -> callback.onError(safeMessage(e, "Lỗi kiểm tra hồ sơ người dùng.")));
                })
                .addOnFailureListener(e -> callback.onError(safeMessage(e, "Đăng nhập Google thất bại.")));
    }

    // ── Register ───────────────────────────────────────────────────────────────

    @Override
    public void register(String email, String password, String name, String birthday, AuthCallback callback) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) {
                        callback.onError("Tạo tài khoản thành công nhưng không lấy được thông tin người dùng.");
                        return;
                    }

                    Map<String, Object> userDoc = new HashMap<>();
                    userDoc.put("email",       user.getEmail());
                    userDoc.put("displayName", name);
                    userDoc.put("photoUrl",    user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null);
                    userDoc.put("birthday",    birthday);
                    userDoc.put("role",        UserRole.USER.toFirestoreValue());
                    userDoc.put("isActive",    true);
                    userDoc.put("updatedAt",   System.currentTimeMillis());
                    userDoc.put("createdAt",   System.currentTimeMillis());

                    firestore.collection(USERS_COLLECTION)
                            .document(user.getUid())
                            .set(userDoc)
                            .addOnSuccessListener(unused -> {
                                // Phase 3: Send verification email, then sign out to force verification
                                user.sendEmailVerification()
                                        .addOnCompleteListener(task -> {
                                            firebaseAuth.signOut();
                                            clearRoleCache();
                                            
                                            if (task.isSuccessful()) {
                                                // Notify caller with a special marker so the UI shows the right dialog
                                                callback.onSuccess(new AuthSession(
                                                        "VERIFY_EMAIL:" + user.getEmail(),
                                                        user.getEmail(),
                                                        UserRole.USER,
                                                        false
                                                ));
                                            } else {
                                                String errMsg = task.getException() != null ? task.getException().getMessage() : "Lỗi không xác định khi gửi email.";
                                                callback.onError("Đăng ký thành công nhưng không thể gửi email xác thực: " + errMsg);
                                            }
                                        });
                            })
                            .addOnFailureListener(e -> callback.onError(safeMessage(e, "Tạo hồ sơ người dùng thất bại.")));
                })
                .addOnFailureListener(e -> callback.onError(safeMessage(e, "Đăng ký thất bại. Vui lòng thử lại.")));
    }

    // ── Guest ─────────────────────────────────────────────────────────────────

    @Override
    public void signInAsGuest(AuthCallback callback) {
        firebaseAuth.signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user != null) {
                        clearRoleCache(); // Guests must not inherit previous user's role
                        callback.onSuccess(new AuthSession(user.getUid(), "Guest", UserRole.USER, true));
                    } else {
                        callback.onError("Không thể tạo phiên khách.");
                    }
                })
                .addOnFailureListener(e -> {
                    String msg = safeMessage(e, "Lỗi khi đăng nhập khách.");
                    if (msg.contains("CONFIGURATION_NOT_FOUND") || msg.contains("not enabled") || msg.contains("restricted")) {
                        callback.onError("Chế độ Khách chưa được bật.\nVui lòng vào Firebase Console > Authentication > Sign-in method > bật 'Anonymous'.");
                    } else {
                        callback.onError(msg);
                    }
                });
    }

    // ── fetchCurrentRole ──────────────────────────────────────────────────────

    /**
     * Phase 2: Serves from cache first, refreshes Firestore in background.
     * Used by LauncherActivity on cold start.
     */
    @Override
    public void fetchCurrentRole(RoleCallback callback) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            callback.onError("Bạn chưa đăng nhập.");
            return;
        }
        if (user.isAnonymous()) {
            callback.onSuccess(UserRole.USER);
            return;
        }

        // Fast path: serve from cache, refresh in background
        UserRole cached = getCachedRole();
        if (cached != null) {
            callback.onSuccess(cached);
            refreshRoleInBackground(user.getUid());
            return;
        }

        // Slow path: must fetch from Firestore
        firestore.collection(USERS_COLLECTION)
                .document(user.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        UserRole role = UserRole.from(snapshot.getString("role"));
                        cacheRole(role);
                        callback.onSuccess(role);
                        return;
                    }
                    // Auto-create profile for legacy accounts missing Firestore doc
                    Map<String, Object> userDoc = new HashMap<>();
                    userDoc.put("email",     user.getEmail());
                    userDoc.put("role",      UserRole.USER.toFirestoreValue());
                    userDoc.put("isActive",  true);
                    userDoc.put("updatedAt", System.currentTimeMillis());
                    userDoc.put("createdAt", System.currentTimeMillis());
                    firestore.collection(USERS_COLLECTION)
                            .document(user.getUid())
                            .set(userDoc, SetOptions.merge())
                            .addOnSuccessListener(unused -> {
                                cacheRole(UserRole.USER);
                                callback.onSuccess(UserRole.USER);
                            })
                            .addOnFailureListener(e -> callback.onError(safeMessage(e, "Không thể đồng bộ vai trò người dùng.")));
                })
                .addOnFailureListener(e -> callback.onError(safeMessage(e, "Không lấy được vai trò người dùng.")));
    }

    // ── fetchCurrentUserInfo ──────────────────────────────────────────────────

    @Override
    public void fetchCurrentUserInfo(UserInfoCallback callback) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            callback.onError("Bạn chưa đăng nhập.");
            return;
        }
        if (user.isAnonymous()) {
            callback.onSuccess(UserRole.USER, 0L);
            return;
        }
        firestore.collection(USERS_COLLECTION)
                .document(user.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        UserRole role = UserRole.from(snapshot.getString("role"));
                        cacheRole(role);
                        Long premiumUntil = snapshot.getLong("premiumUntil");
                        callback.onSuccess(role, premiumUntil != null ? premiumUntil : 0L);
                        return;
                    }
                    callback.onSuccess(UserRole.USER, 0L);
                })
                .addOnFailureListener(e -> callback.onError(safeMessage(e, "Không lấy được thông tin người dùng.")));
    }

    // ── Forgot Password (Phase 1 — anti-enumeration fix) ─────────────────────

    /**
     * Security improvement: always respond with the same neutral success message
     * regardless of whether the email exists, to prevent user-enumeration attacks.
     */
    @Override
    public void sendPasswordResetEmail(String email, SimpleCallback callback) {
        if (email == null || email.trim().isEmpty()) {
            callback.onError("Vui lòng nhập email.");
            return;
        }
        // Send directly to Firebase Auth — do NOT check Firestore first.
        // This prevents attackers from probing whether an email is registered.
        firebaseAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onSuccess()); // intentional — same response always
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    @Override
    public void fetchProfile(ProfileCallback callback) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            callback.onError("User not logged in");
            return;
        }
        if (user.isAnonymous()) {
            callback.onSuccess("Guest", null, null, UserRole.USER);
            return;
        }
        firestore.collection(USERS_COLLECTION)
                .document(user.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    String email       = user.getEmail();
                    String photoUrl    = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null;
                    String displayName = user.getDisplayName();
                    UserRole role      = UserRole.USER;

                    if (snapshot.exists()) {
                        String dbName = snapshot.getString("displayName");
                        if (dbName != null && !dbName.isEmpty()) {
                            displayName = dbName;
                        }
                        String dbPhotoUrl = snapshot.getString("photoUrl");
                        if (dbPhotoUrl != null && !dbPhotoUrl.isEmpty()) {
                            photoUrl = dbPhotoUrl;
                        }
                        role = UserRole.from(snapshot.getString("role"));
                        cacheRole(role);
                    }
                    callback.onSuccess(displayName, email, photoUrl, role);
                })
                .addOnFailureListener(e -> callback.onSuccess(
                        user.getDisplayName(), user.getEmail(),
                        user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null,
                        UserRole.USER));
    }

    @Override
    public void updateProfilePhoto(String photoUrl, SimpleCallback callback) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            callback.onError("User not logged in");
            return;
        }
        Map<String, Object> update = new HashMap<>();
        update.put("photoUrl",  photoUrl);
        update.put("updatedAt", System.currentTimeMillis());
        firestore.collection(USERS_COLLECTION)
                .document(user.getUid())
                .update(update)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(safeMessage(e, "Lỗi cập nhật ảnh đại diện lên database.")));
    }

    // ── Phase 3: Email Verification ────────────────────────────────────────────

    @Override
    public boolean isEmailVerified() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        return user != null && (user.isAnonymous() || user.isEmailVerified());
    }

    @Override
    public void resendVerificationEmail(SimpleCallback callback) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            callback.onError("No user session found.");
            return;
        }
        user.sendEmailVerification()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(safeMessage(e, "Failed to resend verification email.")));
    }

    // ── Sign-out ──────────────────────────────────────────────────────────────

    @Override
    public void signOut() {
        clearRoleCache();
        firebaseAuth.signOut();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Translates a Firebase exception to Vietnamese via {@link FirebaseAuthErrorMapper}.
     * Falls back to {@code fallback} if no specific translation is found.
     */
    @NonNull
    private String safeMessage(Exception exception, String fallback) {
        return FirebaseAuthErrorMapper.toVietnamese(exception, fallback);
    }
}
