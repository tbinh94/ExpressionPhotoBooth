package com.example.expressionphotobooth.domain.model;

public class Frame {
    private final int id;
    private final int imageResId;
    private final String remoteBase64;
    private final String label;
    private final EditState.FrameStyle frameStyle;

    public Frame(int id, int imageResId, String label, EditState.FrameStyle frameStyle) {
        this.id = id;
        this.imageResId = imageResId;
        this.remoteBase64 = null;
        this.label = label;
        this.frameStyle = frameStyle;
    }

    public Frame(int id, String remoteBase64, String label, EditState.FrameStyle frameStyle) {
        this.id = id;
        this.imageResId = 0;
        this.remoteBase64 = remoteBase64;
        this.label = label;
        this.frameStyle = frameStyle;
    }

    public int getId() { return id; }
    public int getImageResId() { return imageResId; }
    public String getRemoteBase64() { return remoteBase64; }
    public boolean isRemote() { return remoteBase64 != null; }
    public String getLabel() { return label; }
    public EditState.FrameStyle getFrameStyle() { return frameStyle; }
}