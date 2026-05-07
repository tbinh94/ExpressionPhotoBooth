package com.example.expressionphotobooth.domain.model;
import com.example.expressionphotobooth.R;

public enum UserRole {
    USER,
    PREMIUM,
    ADMIN;

    public static UserRole from(String rawValue) {
        if (rawValue == null) {
            return USER;
        }
        if ("admin".equalsIgnoreCase(rawValue)) return ADMIN;
        if ("premium".equalsIgnoreCase(rawValue)) return PREMIUM;
        return USER;
    }

    public static UserRole from(String rawValue, long premiumUntil) {
        UserRole role = from(rawValue);
        if (role == PREMIUM && premiumUntil > 0 && premiumUntil < System.currentTimeMillis()) {
            return USER;
        }
        return role;
    }

    public String toFirestoreValue() {
        if (this == ADMIN) return "admin";
        if (this == PREMIUM) return "premium";
        return "user";
    }

    public int getDisplayNameRes() {
        if (this == ADMIN) return R.string.admin_role_admin;
        if (this == PREMIUM) return R.string.admin_role_premium;
        return R.string.admin_role_member;
    }
}

