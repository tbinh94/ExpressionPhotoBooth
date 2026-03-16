package com.example.expressionphotobooth.domain.model;

import java.util.ArrayList;
import java.util.List;

// SessionState gom dữ liệu chạy xuyên suốt 1 lần photobooth (setup -> capture -> select -> edit -> result).
public class SessionState {
    private int photoCount = 4;
    private final List<String> capturedImageUris = new ArrayList<>();
    private String selectedImageUri;
    private String resultImageUri;
    private EditState editState = new EditState();

    public int getPhotoCount() {
        return photoCount;
    }

    public void setPhotoCount(int photoCount) {
        this.photoCount = photoCount;
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

