package com.example.expressionphotobooth.domain.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionState {
    private int photoCount = 4;
    private int selectedFrameResId;
    private final List<String> capturedImageUris = new ArrayList<>();
    private final List<String> timelapseImageUris = new ArrayList<>(); // Thêm list riêng cho timelapse
    private final Map<String, String> editedImageUris = new HashMap<>();
    private final Map<String, EditState> photoEditStates = new HashMap<>();
    private String selectedImageUri;
    private String resultImageUri;
    private EditState editState = new EditState();

    public int getPhotoCount() {
        return photoCount;
    }

    public void setPhotoCount(int photoCount) {
        this.photoCount = photoCount;
    }

    public int getSelectedFrameResId() {
        return selectedFrameResId;
    }

    public void setSelectedFrameResId(int selectedFrameResId) {
        this.selectedFrameResId = selectedFrameResId;
    }

    public List<String> getCapturedImageUris() {
        return capturedImageUris;
    }

    public void setCapturedImageUris(List<String> uris) {
        capturedImageUris.clear();
        if (uris != null) {
            capturedImageUris.addAll(uris);
        }
    }

    public List<String> getTimelapseImageUris() {
        return timelapseImageUris;
    }

    public void setTimelapseImageUris(List<String> uris) {
        this.timelapseImageUris.clear();
        if (uris != null) {
            this.timelapseImageUris.addAll(uris);
        }
    }

    public Map<String, String> getEditedImageUris() {
        return editedImageUris;
    }

    public Map<String, EditState> getPhotoEditStates() {
        return photoEditStates;
    }

    public EditState getPhotoEditState(String originalUri) {
        if (originalUri == null || originalUri.isEmpty()) {
            return getEditState();
        }
        EditState photoState = photoEditStates.get(originalUri);
        if (photoState == null) {
            photoState = getEditState().copy();
            photoEditStates.put(originalUri, photoState);
        }
        return photoState;
    }

    public void setPhotoEditState(String originalUri, EditState state) {
        if (originalUri == null || originalUri.isEmpty()) {
            return;
        }
        photoEditStates.put(originalUri, state == null ? new EditState() : state.copy());
    }

    public String getSelectedImageUri() {
        return selectedImageUri;
    }

    public void setSelectedImageUri(String selectedImageUri) {
        this.selectedImageUri = selectedImageUri;
    }

    public String getResultImageUri() {
        return resultImageUri;
    }

    public void setResultImageUri(String resultImageUri) {
        this.resultImageUri = resultImageUri;
    }

    public EditState getEditState() {
        if (editState == null) {
            editState = new EditState();
        }
        return editState;
    }

    public void setEditState(EditState editState) {
        this.editState = editState == null ? new EditState() : editState;
    }
}
