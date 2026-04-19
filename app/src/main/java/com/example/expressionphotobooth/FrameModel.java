package com.example.expressionphotobooth;

import java.util.Objects;

public class FrameModel {
    private String id;
    private String base64;
    private String label;
    private String layoutType; // "3x4_4", "16x9_3", "16x9_4"
    private long createdAt;

    public FrameModel() {}

    public FrameModel(String id, String base64, String label, String layoutType, long createdAt) {
        this.id = id;
        this.base64 = base64;
        this.label = label;
        this.layoutType = layoutType;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBase64() { return base64; }
    public void setBase64(String base64) { this.base64 = base64; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getLayoutType() { return layoutType; }
    public void setLayoutType(String layoutType) { this.layoutType = layoutType; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FrameModel that = (FrameModel) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
