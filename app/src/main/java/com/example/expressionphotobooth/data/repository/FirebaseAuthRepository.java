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
                        callback.onSuccess(new AuthSession(user.getUid(), user.getEmail(), role));
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                }))
                .addOnFailureListener(e -> callback.onError(safeMessage(e, "Đăng nhập thất bại. Vui lòng thử lại.")));
    }

    @Override
    public void register(String email, String password, AuthCallback callback) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) {
                        callback.onError("Tạo tài khoản thành công nhưng không lấy được thông tin người dùng.");
                        return;
                    }

                    Map<String, Object> userDoc = new HashMap<>();
                    userDoc.put("email", user.getEmail());
                    userDoc.put("role", UserRole.USER.toFirestoreValue());
                    userDoc.put("isActive", true);
                    userDoc.put("updatedAt", System.currentTimeMillis());
                    userDoc.put("createdAt", System.currentTimeMillis());

                    firestore.collection(USERS_COLLECTION)
                            .document(user.getUid())
                            .set(userDoc)
                            .addOnSuccessListener(unused -> callback.onSuccess(new AuthSession(user.getUid(), user.getEmail(), UserRole.USER)))
                            .addOnFailureListener(e -> callback.onError(safeMessage(e, "Tạo hồ sơ người dùng thất bại.")));
                })
                .addOnFailureListener(e -> callback.onError(safeMessage(e, "Đăng ký thất bại. Vui lòng thử lại.")));
    }

    @Override
    public void fetchCurrentRole(RoleCallback callback) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            callback.onError("Bạn chưa đăng nhập.");
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
    public void signOut() {
        firebaseAuth.signOut();
    }

    @NonNull
    private String safeMessage(Exception exception, String fallback) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? fallback : message;
    }
}

