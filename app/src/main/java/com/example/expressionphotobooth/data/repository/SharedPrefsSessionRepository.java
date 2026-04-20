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
            json.put("selectedFrameResId", state.getSelectedFrameResId());
            json.put("selectedFrameLayout", state.getSelectedFrameLayout());
            json.put("selectedFirestoreFrameId", state.getSelectedFirestoreFrameId());
            json.put("flashEnabled", state.isFlashEnabled());
            json.put("screenFlashStrong", state.isScreenFlashStrong());
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
            JSONObject editJson = toEditStateJson(editState);
            json.put("editState", editJson);

            JSONObject photoEditsJson = new JSONObject();
            for (Map.Entry<String, EditState> entry : state.getPhotoEditStates().entrySet()) {
                photoEditsJson.put(entry.getKey(), toEditStateJson(entry.getValue()));
            }
            json.put("photoEditStates", photoEditsJson);

            JSONArray handGestures = new JSONArray();
            for (String g : state.getEnabledHandGestures()) handGestures.put(g);
            json.put("enabledHandGestures", handGestures);

            JSONArray faceExpressions = new JSONArray();
            for (String e : state.getEnabledFaceExpressions()) faceExpressions.put(e);
            json.put("enabledFaceExpressions", faceExpressions);
        } catch (JSONException ignored) {}
        return json;
    }

    private SessionState fromJson(JSONObject json) {
        SessionState state = new SessionState();
        state.setPhotoCount(json.optInt("photoCount", 4));
        state.setSelectedFrameResId(json.optInt("selectedFrameResId", -1));
        String savedLayout = json.optString("selectedFrameLayout", null);
        if (savedLayout != null && !savedLayout.isEmpty()) state.setSelectedFrameLayout(savedLayout);
        String savedFsId = json.optString("selectedFirestoreFrameId", null);
        if (savedFsId != null && !savedFsId.isEmpty()) state.setSelectedFirestoreFrameId(savedFsId);
        state.setFlashEnabled(json.optBoolean("flashEnabled", false));
        state.setScreenFlashStrong(json.optBoolean("screenFlashStrong", false));
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
        EditState editState = fromEditStateJson(editJson);
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
                EditState onePhotoState = fromEditStateJson(onePhotoJson);
                state.setPhotoEditState(originalUri, onePhotoState);
            }
        }

        JSONArray handGestures = json.optJSONArray("enabledHandGestures");
        if (handGestures != null) {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < handGestures.length(); i++) list.add(handGestures.optString(i));
            state.setEnabledHandGestures(list);
        }

        JSONArray faceExpressions = json.optJSONArray("enabledFaceExpressions");
        if (faceExpressions != null) {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < faceExpressions.length(); i++) list.add(faceExpressions.optString(i));
            state.setEnabledFaceExpressions(list);
        }

        return state;
    }

    private JSONObject toEditStateJson(EditState editState) throws JSONException {
        JSONObject editJson = new JSONObject();
        editJson.put("filterStyle", editState.getFilterStyle().name());
        editJson.put("frameStyle", editState.getFrameStyle().name());
        editJson.put("stickerStyle", editState.getStickerStyle().name());
        editJson.put("backgroundStyle", editState.getBackgroundStyle().name());
        editJson.put("customBackgroundBase64", editState.getCustomBackgroundBase64());
        editJson.put("blurRadius", editState.getBlurRadius());
        editJson.put("customStickerBase64", editState.getCustomStickerBase64());
        editJson.put("customStickerId", editState.getCustomStickerId());
        editJson.put("fromStore", editState.isFromStore());
        editJson.put("filterIntensity", editState.getFilterIntensity());
        editJson.put("stickerX", editState.getStickerX());
        editJson.put("stickerY", editState.getStickerY());
        editJson.put("stickerCropX", editState.getStickerCropX());
        editJson.put("stickerCropY", editState.getStickerCropY());
        editJson.put("stickerCropLeftNorm", editState.getStickerCropLeftNorm());
        editJson.put("stickerCropTopNorm", editState.getStickerCropTopNorm());
        editJson.put("stickerCropRightNorm", editState.getStickerCropRightNorm());
        editJson.put("stickerCropBottomNorm", editState.getStickerCropBottomNorm());
        editJson.put("stickerScale", editState.getStickerScale());
        editJson.put("stickerRotation", editState.getStickerRotation());

        // Multi-sticker list
        JSONArray stickersArr = new JSONArray();
        for (com.example.expressionphotobooth.domain.model.StickerItem si : editState.getStickerItems()) {
            JSONObject sJson = new JSONObject();
            sJson.put("style", si.getStyle().name());
            sJson.put("customBase64", si.getCustomBase64());
            sJson.put("customId", si.getCustomId());
            sJson.put("fromStore", si.isFromStore());
            sJson.put("cropX", si.getCropX());
            sJson.put("cropY", si.getCropY());
            sJson.put("absX", si.getAbsX());
            sJson.put("absY", si.getAbsY());
            sJson.put("cropL", si.getCropLeftNorm());
            sJson.put("cropT", si.getCropTopNorm());
            sJson.put("cropR", si.getCropRightNorm());
            sJson.put("cropB", si.getCropBottomNorm());
            sJson.put("scale", si.getScale());
            sJson.put("rotation", si.getRotation());
            stickersArr.put(sJson);
        }
        editJson.put("stickerItems", stickersArr);
        editJson.put("activeStickerIndex", editState.getActiveStickerIndex());

        return editJson;
    }

    private EditState fromEditStateJson(JSONObject editJson) {
        EditState editState = new EditState();
        if (editJson == null) {
            return editState;
        }
        editState.setFilterStyle(parseEnum(EditState.FilterStyle.class, editJson.optString("filterStyle"), EditState.FilterStyle.NONE));
        editState.setFrameStyle(parseEnum(EditState.FrameStyle.class, editJson.optString("frameStyle"), EditState.FrameStyle.NONE));
        editState.setStickerStyle(parseEnum(EditState.StickerStyle.class, editJson.optString("stickerStyle"), EditState.StickerStyle.NONE));
        editState.setBackgroundStyle(parseEnum(EditState.BackgroundStyle.class, editJson.optString("backgroundStyle"), EditState.BackgroundStyle.NONE));
        editState.setCustomBackgroundBase64(editJson.optString("customBackgroundBase64", null));
        editState.setBlurRadius((float) editJson.optDouble("blurRadius", 25.0));
        editState.setCustomStickerBase64(editJson.optString("customStickerBase64", null));
        editState.setCustomStickerId(editJson.optString("customStickerId", null));
        editState.setFromStore(editJson.optBoolean("fromStore", false));
        editState.setFilterIntensity((float) editJson.optDouble("filterIntensity", 0.8));
        editState.setStickerX((float) editJson.optDouble("stickerX", -1.0));
        editState.setStickerY((float) editJson.optDouble("stickerY", -1.0));
        editState.setStickerCropX((float) editJson.optDouble("stickerCropX", -1.0));
        editState.setStickerCropY((float) editJson.optDouble("stickerCropY", -1.0));
        editState.setStickerCropLeftNorm((float) editJson.optDouble("stickerCropLeftNorm", -1.0));
        editState.setStickerCropTopNorm((float) editJson.optDouble("stickerCropTopNorm", -1.0));
        editState.setStickerCropRightNorm((float) editJson.optDouble("stickerCropRightNorm", -1.0));
        editState.setStickerCropBottomNorm((float) editJson.optDouble("stickerCropBottomNorm", -1.0));
        editState.setStickerScale((float) editJson.optDouble("stickerScale", 1.0));
        editState.setStickerRotation((float) editJson.optDouble("stickerRotation", 0.0));

        JSONArray stickersArr = editJson.optJSONArray("stickerItems");
        if (stickersArr != null) {
            List<com.example.expressionphotobooth.domain.model.StickerItem> list = new ArrayList<>();
            for (int i = 0; i < stickersArr.length(); i++) {
                JSONObject sJson = stickersArr.optJSONObject(i);
                if (sJson == null) continue;
                com.example.expressionphotobooth.domain.model.StickerItem si = new com.example.expressionphotobooth.domain.model.StickerItem();
                si.setStyle(parseEnum(EditState.StickerStyle.class, sJson.optString("style"), EditState.StickerStyle.NONE));
                si.setCustomBase64(sJson.optString("customBase64", null));
                si.setCustomId(sJson.optString("customId", null));
                si.setFromStore(sJson.optBoolean("fromStore", false));
                si.setCropX((float) sJson.optDouble("cropX", 0.5));
                si.setCropY((float) sJson.optDouble("cropY", 0.5));
                si.setAbsX((float) sJson.optDouble("absX", -1.0));
                si.setAbsY((float) sJson.optDouble("absY", -1.0));
                si.setCropLeftNorm((float) sJson.optDouble("cropL", -1.0));
                si.setCropTopNorm((float) sJson.optDouble("cropT", -1.0));
                si.setCropRightNorm((float) sJson.optDouble("cropR", -1.0));
                si.setCropBottomNorm((float) sJson.optDouble("cropB", -1.0));
                si.setScale((float) sJson.optDouble("scale", 1.0));
                si.setRotation((float) sJson.optDouble("rotation", 0.0));
                list.add(si);
            }
            editState.setStickerItems(list);
        }
        editState.setActiveStickerIndex(editJson.optInt("activeStickerIndex", -1));

        return editState;
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumClass, String value, T fallback) {
        try {
            return Enum.valueOf(enumClass, value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
