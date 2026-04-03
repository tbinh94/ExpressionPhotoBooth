package com.example.expressionphotobooth.domain.repository;

import com.example.expressionphotobooth.domain.model.HistorySession;

import java.util.List;

public interface HistoryRepository {
    String createSession(HistorySession session);

    void updateVideoUri(String userId, String sessionId, String videoUri);

    void updateFeedback(String userId, String sessionId, float rating, String feedback);

    List<HistorySession> getSessions(String userId);

    HistorySession getById(String userId, String sessionId);

    void deleteSession(String userId, String sessionId);
}

