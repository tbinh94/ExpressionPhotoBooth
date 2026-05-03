package com.example.expressionphotobooth.domain.model;

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
}

