package com.example.expressionphotobooth.domain.model;

public class User {
    private String uid;
    private String email;
    private String displayName;
    private UserRole role;
    private long premiumUntil;

    public User(String uid, String email, String displayName, UserRole role, long premiumUntil) {
        this.uid = uid;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.premiumUntil = premiumUntil;
    }

    public String getUid() {
        return uid;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public long getPremiumUntil() {
        return premiumUntil;
    }

    public void setPremiumUntil(long premiumUntil) {
        this.premiumUntil = premiumUntil;
    }
}
