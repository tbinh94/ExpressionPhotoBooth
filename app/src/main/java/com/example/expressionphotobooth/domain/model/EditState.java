package com.example.expressionphotobooth.domain.model;

// EditState giữ toàn bộ lựa chọn chỉnh sửa hiện tại để có thể render lại bất cứ lúc nào.
public class EditState {
    public enum FilterStyle {
        NONE,
        SOFT,
        BW,
        VINTAGE,
        COOL,
        WARM,
        SEPIA
    }

    public enum FrameStyle {
        NONE,
        CORTIS,
        T1,
        AESPA
    }

    public enum StickerStyle {
        NONE,
        STAR,
        FLASH,
        CAMERA
    }

    private FilterStyle filterStyle = FilterStyle.NONE;
    private FrameStyle frameStyle = FrameStyle.NONE;
    private StickerStyle stickerStyle = StickerStyle.NONE;
    private float filterIntensity = 0.8f;
    private float stickerX = -1f;
    private float stickerY = -1f;

    public EditState() {
    }

    public EditState(EditState other) {
        if (other == null) {
            return;
        }
        this.filterStyle = other.filterStyle;
        this.frameStyle = other.frameStyle;
        this.stickerStyle = other.stickerStyle;
        this.filterIntensity = other.filterIntensity;
        this.stickerX = other.stickerX;
        this.stickerY = other.stickerY;
    }

    public EditState copy() {
        return new EditState(this);
    }

    public FilterStyle getFilterStyle() {
        return filterStyle;
    }

    public void setFilterStyle(FilterStyle filterStyle) {
        this.filterStyle = filterStyle;
    }

    public FrameStyle getFrameStyle() {
        return frameStyle;
    }

    public void setFrameStyle(FrameStyle frameStyle) {
        this.frameStyle = frameStyle;
    }

    public StickerStyle getStickerStyle() {
        return stickerStyle;
    }

    public void setStickerStyle(StickerStyle stickerStyle) {
        this.stickerStyle = stickerStyle;
    }

    public float getFilterIntensity() {
        return filterIntensity;
    }

    public void setFilterIntensity(float filterIntensity) {
        this.filterIntensity = filterIntensity;
    }

    public float getStickerX() {
        return stickerX;
    }

    public void setStickerX(float stickerX) {
        this.stickerX = stickerX;
    }

    public float getStickerY() {
        return stickerY;
    }

    public void setStickerY(float stickerY) {
        this.stickerY = stickerY;
    }
}
