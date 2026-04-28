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

    interface UserInfoCallback {
        void onSuccess(UserRole role, long premiumUntil);
        void onError(String message);
    }

    interface ProfileCallback {
        void onSuccess(String displayName, String email, String photoUrl, UserRole role);
        void onError(String message);
    }

    interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    boolean isLoggedIn();

    String getCurrentUid();

    String getCurrentEmail();

    boolean isGuest();

    void signIn(String email, String password, AuthCallback callback);

    void signInWithGoogle(String idToken, AuthCallback callback);

    void register(String email, String password, String name, String birthday, AuthCallback callback);

    void signInAsGuest(AuthCallback callback);

    void fetchCurrentRole(RoleCallback callback);

    void fetchCurrentUserInfo(UserInfoCallback callback);

    void sendPasswordResetEmail(String email, SimpleCallback callback);

    void fetchProfile(ProfileCallback callback);

    void updateProfilePhoto(String photoUrl, SimpleCallback callback);

    /** Phase 3: Check if the current Firebase user has verified their email. */
    boolean isEmailVerified();

    /** Phase 3: Send (or resend) an email verification link to the current user. */
    void resendVerificationEmail(SimpleCallback callback);

    void signOut();
}
// Trigger re-index

