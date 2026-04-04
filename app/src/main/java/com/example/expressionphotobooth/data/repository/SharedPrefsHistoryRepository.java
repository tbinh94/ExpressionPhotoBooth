package com.example.expressionphotobooth.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.expressionphotobooth.domain.model.HistorySession;
import com.example.expressionphotobooth.domain.repository.HistoryRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class SharedPrefsHistoryRepository implements HistoryRepository {
    private static final String PREF_NAME = "photobooth_history";

    private final SharedPreferences sharedPreferences;

    public SharedPrefsHistoryRepository(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public String createSession(HistorySession session) {
        if (session == null || session.getUserId() == null || session.getUserId().isEmpty()) {
            return null;
        }
        String sessionId = session.getId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
            session.setId(sessionId);
        }

        List<HistorySession> sessions = getSessions(session.getUserId());
        sessions.add(0, session);
        saveSessions(session.getUserId(), sessions);
        return sessionId;
    }

    @Override
    public void updateVideoUri(String userId, String sessionId, String videoUri) {
        List<HistorySession> sessions = getSessions(userId);
        for (HistorySession item : sessions) {
            if (sessionId.equals(item.getId())) {
                item.setVideoUri(videoUri);
                break;
            }
        }
        saveSessions(userId, sessions);
    }

    @Override
    public void updateFeedback(String userId, String sessionId, float rating, String feedback) {
        List<HistorySession> sessions = getSessions(userId);
        for (HistorySession item : sessions) {
            if (sessionId.equals(item.getId())) {
                item.setRating(rating);
                item.setFeedback(feedback);
                break;
            }
        }
        saveSessions(userId, sessions);
    }

    @Override
    public List<HistorySession> getSessions(String userId) {
        if (userId == null || userId.isEmpty()) {
            return Collections.emptyList();
        }
        String raw = sharedPreferences.getString(buildUserKey(userId), "[]");
        List<HistorySession> items = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json != null) {
                    items.add(fromJson(json));
                }
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    @Override
    public HistorySession getById(String userId, String sessionId) {
        List<HistorySession> sessions = getSessions(userId);
        for (HistorySession item : sessions) {
            if (sessionId.equals(item.getId())) {
                return item;
            }
        }
        return null;
    }

    @Override
    public void deleteSession(String userId, String sessionId) {
        List<HistorySession> sessions = getSessions(userId);
        Iterator<HistorySession> iterator = sessions.iterator();
        while (iterator.hasNext()) {
            if (sessionId.equals(iterator.next().getId())) {
                iterator.remove();
                break;
            }
        }
        saveSessions(userId, sessions);
    }

    private void saveSessions(String userId, List<HistorySession> sessions) {
        JSONArray array = new JSONArray();
        for (HistorySession item : sessions) {
            array.put(toJson(item));
        }
        sharedPreferences.edit().putString(buildUserKey(userId), array.toString()).apply();
    }

    private String buildUserKey(String userId) {
        return "history_" + userId;
    }

    private JSONObject toJson(HistorySession session) {
        JSONObject json = new JSONObject();
        try {
            json.put("id", session.getId());
            json.put("userId", session.getUserId());
            json.put("capturedAt", session.getCapturedAt());
            json.put("frameName", session.getFrameName());
            json.put("frameResId", session.getFrameResId());
            json.put("aspectRatio", session.getAspectRatio());
            json.put("resultImageUri", session.getResultImageUri());
            json.put("videoUri", session.getVideoUri());
            json.put("rating", session.getRating());
            json.put("feedback", session.getFeedback());

            JSONArray selected = new JSONArray();
            for (String uri : session.getSelectedImageUris()) {
                selected.put(uri);
            }
            json.put("selectedImageUris", selected);
        } catch (Exception ignored) {
        }
        return json;
    }

    private HistorySession fromJson(JSONObject json) {
        HistorySession session = new HistorySession();
        session.setId(json.optString("id", null));
        session.setUserId(json.optString("userId", null));
        session.setCapturedAt(json.optLong("capturedAt", System.currentTimeMillis()));
        session.setFrameName(json.optString("frameName", "Unknown"));
        session.setFrameResId(json.optInt("frameResId", -1));
        session.setAspectRatio(json.optString("aspectRatio", "N/A"));
        session.setResultImageUri(json.optString("resultImageUri", null));
        session.setVideoUri(json.optString("videoUri", null));
        session.setRating((float) json.optDouble("rating", -1f));
        session.setFeedback(json.optString("feedback", ""));

        List<String> selected = new ArrayList<>();
        JSONArray selectedArray = json.optJSONArray("selectedImageUris");
        if (selectedArray != null) {
            for (int i = 0; i < selectedArray.length(); i++) {
                selected.add(selectedArray.optString(i));
            }
        }
        session.setSelectedImageUris(selected);
        return session;
    }
}

