package com.example.expressionphotobooth.domain.model;

import java.util.ArrayList;
import java.util.List;

public class HistorySession {
    private String id;
    private String userId;
    private long capturedAt;
    private String frameName;
    private int frameResId = -1;
    private String aspectRatio;
    private final List<String> selectedImageUris = new ArrayList<>();
    private String resultImageUri;
    private String videoUri;
    private float rating = -1f;
    private String feedback;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(long capturedAt) {
        this.capturedAt = capturedAt;
    }

    public String getFrameName() {
        return frameName;
    }

    public void setFrameName(String frameName) {
        this.frameName = frameName;
    }

    public int getFrameResId() {
        return frameResId;
    }

    public void setFrameResId(int frameResId) {
        this.frameResId = frameResId;
    }

    public String getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(String aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    public List<String> getSelectedImageUris() {
        return selectedImageUris;
    }

    public void setSelectedImageUris(List<String> uris) {
        selectedImageUris.clear();
        if (uris != null) {
            selectedImageUris.addAll(uris);
        }
    }

    public String getResultImageUri() {
        return resultImageUri;
    }

    public void setResultImageUri(String resultImageUri) {
        this.resultImageUri = resultImageUri;
    }

    public String getVideoUri() {
        return videoUri;
    }

    public void setVideoUri(String videoUri) {
        this.videoUri = videoUri;
    }

    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public boolean hasVideo() {
        return videoUri != null && !videoUri.isEmpty();
    }

    public boolean hasFeedback() {
        return rating >= 0f || (feedback != null && !feedback.trim().isEmpty());
    }
}

