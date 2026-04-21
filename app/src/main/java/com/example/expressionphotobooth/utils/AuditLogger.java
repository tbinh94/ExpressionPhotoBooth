package com.example.expressionphotobooth.utils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AuditLogger {
    private static final String COLLECTION_NAME = "admin_logs";

    public static void logAction(String action, String targetId, String metadata) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        Map<String, Object> log = new HashMap<>();
        log.put("adminEmail", user.getEmail());
        log.put("adminUid", user.getUid());
        log.put("action", action);
        log.put("targetId", targetId);
        log.put("metadata", metadata);
        log.put("timestamp", System.currentTimeMillis());

        FirebaseFirestore.getInstance().collection(COLLECTION_NAME).add(log);
    }

    public static void logAction(String action, String targetId) {
        logAction(action, targetId, "");
    }
}
