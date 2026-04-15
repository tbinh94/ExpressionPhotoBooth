package com.example.expressionphotobooth.data.repository;

import androidx.annotation.NonNull;

import com.example.expressionphotobooth.domain.model.AuthSession;
import com.example.expressionphotobooth.domain.model.UserRole;
import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class FirebaseAuthRepository implements AuthRepository {
    private static final String USERS_COLLECTION = "users";

    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;

    public FirebaseAuthRepository() {
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

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

    @Override
    public void signIn(String email, String password, AuthCallback callback) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> fetchCurrentRole(new RoleCallback() {
                    @Override
                    public void onSuccess(UserRole role) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        if (user == null) {
                            callback.onError("Không tìm thấy tài khoản sau khi đăng nhập.");
                            return;
                        }
                        callback.onSuccess(new AuthSession(user.getUid(), user.getEmail(), role, false));
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                }))
                .addOnFailureListener(e -> callback.onError(safeMessage(e, "Đăng nhập thất bại. Vui lòng thử lại.")));
    }

    @Override
    public void signInWithGoogle(String idToken, AuthCallback callback) {
        com.google.firebase.auth.AuthCredential credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) {
                        callback.onError("Đăng nhập Google thành công nhưng không lấy được thông tin.");
                        return;
                    }

                    // Kiểm tra xem user đã có profile trong Firestore chưa
                    firestore.collection(USERS_COLLECTION)
                            .document(user.getUid())
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                if (snapshot.exists()) {
                                    // User đã tồn tại, lấy role
                                    UserRole role = UserRole.from(snapshot.getString("role"));
                                    callback.onSuccess(new AuthSession(user.getUid(), user.getEmail(), role, false));
                                } else {
                                    // User mới, tạo profile mặc định
                                    Map<String, Object> userDoc = new HashMap<>();
                                    userDoc.put("email", user.getEmail());
                                    userDoc.put("displayName", user.getDisplayName());
                                    userDoc.put("role", UserRole.USER.toFirestoreValue());
                                    userDoc.put("isActive", true);
                                    userDoc.put("updatedAt", System.currentTimeMillis());
                                    userDoc.put("createdAt", System.currentTimeMillis());

                                    firestore.collection(USERS_COLLECTION)
                                            .document(user.getUid())
                                            .set(userDoc)
                                            .addOnSuccessListener(unused -> callback.onSuccess(new AuthSession(user.getUid(), user.getEmail(), UserRole.USER, false)))
                                            .addOnFailureListener(e -> callback.onError(safeMessage(e, "Đăng nhập Google thành công nhưng lỗi tạo hồ sơ.")));
                                }
                            })
                            .addOnFailureListener(e -> callback.onError(safeMessage(e, "Lỗi kiểm tra hồ sơ người dùng.")));
                })
                .addOnFailureListener(e -> callback.onError(safeMessage(e, "Đăng nhập Google thất bại.")));
    }

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
                    userDoc.put("email", user.getEmail());
                    userDoc.put("displayName", name);
                    userDoc.put("birthday", birthday);
                    userDoc.put("role", UserRole.USER.toFirestoreValue());
                    userDoc.put("isActive", true);
                    userDoc.put("updatedAt", System.currentTimeMillis());
                    userDoc.put("createdAt", System.currentTimeMillis());

                    firestore.collection(USERS_COLLECTION)
                            .document(user.getUid())
                            .set(userDoc)
                            .addOnSuccessListener(unused -> callback.onSuccess(new AuthSession(user.getUid(), user.getEmail(), UserRole.USER, false)))
                            .addOnFailureListener(e -> callback.onError(safeMessage(e, "Tạo hồ sơ người dùng thất bại.")));
                })
                .addOnFailureListener(e -> callback.onError(safeMessage(e, "Đăng ký thất bại. Vui lòng thử lại.")));
    }

    @Override
    public void signInAsGuest(AuthCallback callback) {
        firebaseAuth.signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user != null) {
                        callback.onSuccess(new AuthSession(user.getUid(), "Guest", UserRole.USER, true));
                    } else {
                        callback.onError("Không thể tạo phiên khách.");
                    }
                })
                .addOnFailureListener(e -> {
                    String msg = safeMessage(e, "Lỗi khi đăng nhập khách.");
                    // Common cause: Anonymous Authentication is disabled in Firebase Console
                    if (msg.contains("CONFIGURATION_NOT_FOUND") || msg.contains("not enabled") || msg.contains("restricted")) {
                        callback.onError("Chế độ Khách chưa được bật.\nVui lòng vào Firebase Console > Authentication > Sign-in method > bật 'Anonymous'.");
                    } else {
                        callback.onError(msg);
                    }
                });
    }

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

        firestore.collection(USERS_COLLECTION)
                .document(user.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        callback.onSuccess(UserRole.from(snapshot.getString("role")));
                        return;
                    }

                    // Ensure old accounts can still continue as USER if profile doc was missing.
                    Map<String, Object> userDoc = new HashMap<>();
                    userDoc.put("email", user.getEmail());
                    userDoc.put("role", UserRole.USER.toFirestoreValue());
                    userDoc.put("isActive", true);
                    userDoc.put("updatedAt", System.currentTimeMillis());
                    userDoc.put("createdAt", System.currentTimeMillis());
                    firestore.collection(USERS_COLLECTION)
                            .document(user.getUid())
                            .set(userDoc)
                            .addOnSuccessListener(unused -> callback.onSuccess(UserRole.USER))
                            .addOnFailureListener(e -> callback.onError(safeMessage(e, "Không thể đồng bộ vai trò người dùng.")));
                })
                .addOnFailureListener(e -> callback.onError(safeMessage(e, "Không lấy được vai trò người dùng.")));
    }

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
                        Long premiumUntil = snapshot.getLong("premiumUntil");
                        callback.onSuccess(role, premiumUntil != null ? premiumUntil : 0L);
                        return;
                    }
                    callback.onSuccess(UserRole.USER, 0L);
                })
                .addOnFailureListener(e -> callback.onError(safeMessage(e, "Không lấy được thông tin người dùng.")));
    }

    @Override
    public void sendPasswordResetEmail(String email, SimpleCallback callback) {
        if (email == null || email.trim().isEmpty()) {
            callback.onError("Vui lòng nhập email.");
            return;
        }
        
        // Kiểm tra xem email có tồn tại trong Firestore hay không trước khi gửi
        firestore.collection(USERS_COLLECTION)
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        callback.onError("Email này chưa được đăng ký trong hệ thống.");
                        return;
                    }
                    
                    // Nếu tồn tại mới gọi Firebase Auth để gửi email
                    firebaseAuth.sendPasswordResetEmail(email)
                            .addOnSuccessListener(unused -> callback.onSuccess())
                            .addOnFailureListener(e -> callback.onError(safeMessage(e, "Không thể gửi email đặt lại mật khẩu.")));
                })
                .addOnFailureListener(e -> callback.onError(safeMessage(e, "Lỗi khi kiểm tra thông tin tài khoản.")));
    }

    @Override
    public void signOut() {
        firebaseAuth.signOut();
    }

    @NonNull
    private String safeMessage(Exception exception, String fallback) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? fallback : message;
    }
}

