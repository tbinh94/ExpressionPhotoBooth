package com.example.expressionphotobooth.domain.model;

public enum DownloadType {
    IMAGE("image"),
    VIDEO("video");

    private final String firestoreValue;

    DownloadType(String firestoreValue) {
        this.firestoreValue = firestoreValue;
    }

    public String toFirestoreValue() {
        return firestoreValue;
    }
}

