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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

            JSONObject edited = new JSONObject();
            for (Map.Entry<String, String> entry : state.getEditedImageUris().entrySet()) {
                edited.put(entry.getKey(), entry.getValue());
            }
            json.put("editedImageUris", edited);

            EditState editState = state.getEditState();
            JSONObject editJson = new JSONObject();
            editJson.put("filterStyle", editState.getFilterStyle().name());
            editJson.put("frameStyle", editState.getFrameStyle().name());
            editJson.put("stickerStyle", editState.getStickerStyle().name());
            json.put("editState", editJson);

            JSONObject photoEditsJson = new JSONObject();
            for (Map.Entry<String, EditState> entry : state.getPhotoEditStates().entrySet()) {
                EditState photoEdit = entry.getValue();
                JSONObject onePhotoJson = new JSONObject();
                onePhotoJson.put("filterStyle", photoEdit.getFilterStyle().name());
                onePhotoJson.put("frameStyle", photoEdit.getFrameStyle().name());
                onePhotoJson.put("stickerStyle", photoEdit.getStickerStyle().name());
                photoEditsJson.put(entry.getKey(), onePhotoJson);
            }
            json.put("photoEditStates", photoEditsJson);
        } catch (JSONException ignored) {}
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

        JSONObject editedJsonObj = json.optJSONObject("editedImageUris");
        if (editedJsonObj != null) {
            Iterator<String> keys = editedJsonObj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                state.getEditedImageUris().put(key, editedJsonObj.optString(key));
            }
        }

        JSONObject editJson = json.optJSONObject("editState");
        EditState editState = new EditState();
        if (editJson != null) {
            editState.setFilterStyle(parseEnum(EditState.FilterStyle.class, editJson.optString("filterStyle"), EditState.FilterStyle.NONE));
            editState.setFrameStyle(parseEnum(EditState.FrameStyle.class, editJson.optString("frameStyle"), EditState.FrameStyle.NONE));
            editState.setStickerStyle(parseEnum(EditState.StickerStyle.class, editJson.optString("stickerStyle"), EditState.StickerStyle.NONE));
        }
        state.setEditState(editState);

        JSONObject photoEditsJson = json.optJSONObject("photoEditStates");
        if (photoEditsJson != null) {
            Iterator<String> photoKeys = photoEditsJson.keys();
            while (photoKeys.hasNext()) {
                String originalUri = photoKeys.next();
                JSONObject onePhotoJson = photoEditsJson.optJSONObject(originalUri);
                if (onePhotoJson == null) {
                    continue;
                }
                EditState onePhotoState = new EditState();
                onePhotoState.setFilterStyle(parseEnum(EditState.FilterStyle.class, onePhotoJson.optString("filterStyle"), EditState.FilterStyle.NONE));
                onePhotoState.setFrameStyle(parseEnum(EditState.FrameStyle.class, onePhotoJson.optString("frameStyle"), EditState.FrameStyle.NONE));
                onePhotoState.setStickerStyle(parseEnum(EditState.StickerStyle.class, onePhotoJson.optString("stickerStyle"), EditState.StickerStyle.NONE));
                state.setPhotoEditState(originalUri, onePhotoState);
            }
        }
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
