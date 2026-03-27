package com.example.expressionphotobooth.domain.repository;

import com.example.expressionphotobooth.domain.model.AuthSession;
import com.example.expressionphotobooth.domain.model.UserRole;

public interface AuthRepository {
    interface AuthCallback {
        void onSuccess(AuthSession session);
        void onError(String message);
    }

    interface RoleCallback {
        void onSuccess(UserRole role);
        void onError(String message);
    }

    boolean isLoggedIn();

    String getCurrentUid();

    String getCurrentEmail();

    void signIn(String email, String password, AuthCallback callback);

    void register(String email, String password, AuthCallback callback);

    void fetchCurrentRole(RoleCallback callback);

    void signOut();
}

