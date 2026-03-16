package com.example.expressionphotobooth.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.expressionphotobooth.domain.model.EditState;
import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.SessionRepository;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

// Lưu session vào SharedPreferences giúp app vẫn khôi phục được state khi bị rotate/kill process.
public class SharedPrefsSessionRepository implements SessionRepository {
    private static final String PREF_NAME = "photobooth_session";
    private static final String KEY_SESSION_JSON = "session_json";

    private final SharedPreferences sharedPreferences;

    public SharedPrefsSessionRepository(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public SessionState getSession() {
        String raw = sharedPreferences.getString(KEY_SESSION_JSON, null);
        if (raw == null || raw.isEmpty()) {
            return new SessionState();
        }

        try {
            return fromJson(new JSONObject(raw));
        } catch (JSONException e) {
            return new SessionState();
        }
    }

    @Override
    public void saveSession(SessionState sessionState) {
        sharedPreferences.edit().putString(KEY_SESSION_JSON, toJson(sessionState).toString()).apply();
    }

    @Override
    public void clearSession() {
        sharedPreferences.edit().remove(KEY_SESSION_JSON).apply();
    }

    private JSONObject toJson(SessionState state) {
        JSONObject json = new JSONObject();
        try {
            json.put("photoCount", state.getPhotoCount());
            json.put("selectedImageUri", state.getSelectedImageUri());
            json.put("resultImageUri", state.getResultImageUri());

            JSONArray captured = new JSONArray();
            for (String uri : state.getCapturedImageUris()) {
                captured.put(uri);
            }
            json.put("capturedImageUris", captured);

            EditState editState = state.getEditState();
            JSONObject editJson = new JSONObject();
            editJson.put("filterStyle", editState.getFilterStyle().name());
            editJson.put("frameStyle", editState.getFrameStyle().name());
            editJson.put("stickerStyle", editState.getStickerStyle().name());
            json.put("editState", editJson);
        } catch (JSONException ignored) {
            // Không ném lỗi để tránh crash UI, repository sẽ trả default state ở lần đọc sau.
        }
        return json;
    }

    private SessionState fromJson(JSONObject json) {
        SessionState state = new SessionState();
        state.setPhotoCount(json.optInt("photoCount", 4));
        state.setSelectedImageUri(json.optString("selectedImageUri", null));
        state.setResultImageUri(json.optString("resultImageUri", null));

        JSONArray captured = json.optJSONArray("capturedImageUris");
        List<String> uriList = new ArrayList<>();
        if (captured != null) {
            for (int i = 0; i < captured.length(); i++) {
                uriList.add(captured.optString(i));
            }
        }
        state.setCapturedImageUris(uriList);

        JSONObject editJson = json.optJSONObject("editState");
        EditState editState = new EditState();
        if (editJson != null) {
            editState.setFilterStyle(parseEnum(
                    EditState.FilterStyle.class,
                    editJson.optString("filterStyle"),
                    EditState.FilterStyle.NONE
            ));
            editState.setFrameStyle(parseEnum(
                    EditState.FrameStyle.class,
                    editJson.optString("frameStyle"),
                    EditState.FrameStyle.NONE
            ));
            editState.setStickerStyle(parseEnum(
                    EditState.StickerStyle.class,
                    editJson.optString("stickerStyle"),
                    EditState.StickerStyle.NONE
            ));
        }
        state.setEditState(editState);
        return state;
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumClass, String value, T fallback) {
        try {
            return Enum.valueOf(enumClass, value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}

