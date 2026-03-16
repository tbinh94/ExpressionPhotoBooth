package com.example.expressionphotobooth.domain.repository;

import com.example.expressionphotobooth.domain.model.SessionState;

public interface SessionRepository {
    SessionState getSession();

    void saveSession(SessionState sessionState);

    void clearSession();
}

