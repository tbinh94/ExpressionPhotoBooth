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
        CAMERA,
        HEART,
        CROWN,
        SMILE,
        FLOWER,
        BOW,
        SPARKLE,
        BUTTERFLY,
        CHERRY,
        MUSIC,
        CUSTOM // Added CUSTOM type
    }

    private FilterStyle filterStyle = FilterStyle.NONE;
    private FrameStyle frameStyle = FrameStyle.NONE;
    private StickerStyle stickerStyle = StickerStyle.NONE;
    private String customStickerBase64 = null; // New field for custom stickers
    private float filterIntensity = 0.8f;
    private float stickerX = -1f;
    private float stickerY = -1f;
    // Canonical placement inside frame crop rect (0..1), independent from raw bitmap size.
    private float stickerCropX = -1f;
    private float stickerCropY = -1f;
    // Normalized crop bounds in source bitmap (0..1) used by both preview and export rendering.
    private float stickerCropLeftNorm = -1f;
    private float stickerCropTopNorm = -1f;
    private float stickerCropRightNorm = -1f;
    private float stickerCropBottomNorm = -1f;
    private float stickerScale = 1.0f;
    private float stickerRotation = 0f;

    public EditState() {
    }

    public EditState(EditState other) {
        if (other == null) {
            return;
        }
        this.filterStyle = other.filterStyle;
        this.frameStyle = other.frameStyle;
        this.stickerStyle = other.stickerStyle;
        this.customStickerBase64 = other.customStickerBase64;
        this.filterIntensity = other.filterIntensity;
        this.stickerX = other.stickerX;
        this.stickerY = other.stickerY;
        this.stickerCropX = other.stickerCropX;
        this.stickerCropY = other.stickerCropY;
        this.stickerCropLeftNorm = other.stickerCropLeftNorm;
        this.stickerCropTopNorm = other.stickerCropTopNorm;
        this.stickerCropRightNorm = other.stickerCropRightNorm;
        this.stickerCropBottomNorm = other.stickerCropBottomNorm;
        this.stickerScale = other.stickerScale;
        this.stickerRotation = other.stickerRotation;
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

    public String getCustomStickerBase64() {
        return customStickerBase64;
    }

    public void setCustomStickerBase64(String customStickerBase64) {
        this.customStickerBase64 = customStickerBase64;
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

    public float getStickerCropX() {
        return stickerCropX;
    }

    public void setStickerCropX(float stickerCropX) {
        this.stickerCropX = stickerCropX;
    }

    public float getStickerCropY() {
        return stickerCropY;
    }

    public void setStickerCropY(float stickerCropY) {
        this.stickerCropY = stickerCropY;
    }

    public float getStickerCropLeftNorm() {
        return stickerCropLeftNorm;
    }

    public void setStickerCropLeftNorm(float stickerCropLeftNorm) {
        this.stickerCropLeftNorm = stickerCropLeftNorm;
    }

    public float getStickerCropTopNorm() {
        return stickerCropTopNorm;
    }

    public void setStickerCropTopNorm(float stickerCropTopNorm) {
        this.stickerCropTopNorm = stickerCropTopNorm;
    }

    public float getStickerCropRightNorm() {
        return stickerCropRightNorm;
    }

    public void setStickerCropRightNorm(float stickerCropRightNorm) {
        this.stickerCropRightNorm = stickerCropRightNorm;
    }

    public float getStickerCropBottomNorm() {
        return stickerCropBottomNorm;
    }

    public void setStickerCropBottomNorm(float stickerCropBottomNorm) {
        this.stickerCropBottomNorm = stickerCropBottomNorm;
    }

    public float getStickerScale() {
        return stickerScale;
    }

    public void setStickerScale(float stickerScale) {
        this.stickerScale = stickerScale;
    }

    public float getStickerRotation() {
        return stickerRotation;
    }

    public void setStickerRotation(float stickerRotation) {
        this.stickerRotation = stickerRotation;
    }
}
