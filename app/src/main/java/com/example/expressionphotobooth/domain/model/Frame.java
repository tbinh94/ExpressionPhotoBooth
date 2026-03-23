package com.example.expressionphotobooth.domain.model;

public class Frame {
    private final int id;
    private final int imageResId;
    private final String label;
    private final EditState.FrameStyle frameStyle;

    public Frame(int id, int imageResId, String label, EditState.FrameStyle frameStyle) {
        this.id = id;
        this.imageResId = imageResId;
        this.label = label;
        this.frameStyle = frameStyle;
    }

    public int getId() { return id; }
    public int getImageResId() { return imageResId; }
    public String getLabel() { return label; }
    public EditState.FrameStyle getFrameStyle() { return frameStyle; }
}