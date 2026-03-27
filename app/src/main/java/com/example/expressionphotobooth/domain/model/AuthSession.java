package com.example.expressionphotobooth.domain.model;

public class AuthSession {
    private final String uid;
    private final String email;
    private final UserRole role;
    private final boolean isGuest;

    public AuthSession(String uid, String email, UserRole role) {
        this(uid, email, role, false);
    }

    public AuthSession(String uid, String email, UserRole role, boolean isGuest) {
        this.uid = uid;
        this.email = email;
        this.role = role;
        this.isGuest = isGuest;
    }

    public String getUid() {
        return uid;
    }

    public String getEmail() {
        return email;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isGuest() {
        return isGuest;
    }
}

