package com.example.expressionphotobooth.domain.model;

public enum UserRole {
    USER,
    ADMIN;

    public static UserRole from(String rawValue) {
        if (rawValue == null) {
            return USER;
        }
        return "admin".equalsIgnoreCase(rawValue) ? ADMIN : USER;
    }

    public String toFirestoreValue() {
        return this == ADMIN ? "admin" : "user";
    }
}

