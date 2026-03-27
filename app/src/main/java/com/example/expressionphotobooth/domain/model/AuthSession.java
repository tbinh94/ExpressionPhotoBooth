package com.example.expressionphotobooth.domain.model;

public class AuthSession {
    private final String uid;
    private final String email;
    private final UserRole role;

    public AuthSession(String uid, String email, UserRole role) {
        this.uid = uid;
        this.email = email;
        this.role = role;
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
}

