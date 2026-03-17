package com.example.expressionphotobooth.domain.model;

public class Frame {
    private int id;
    private int imageResId;

    public Frame(int id, int imageResId) {
        this.id = id;
        this.imageResId = imageResId;
    }

    public int getId() { return id; }
    public int getImageResId() { return imageResId; }
}