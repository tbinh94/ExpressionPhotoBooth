package com.example.expressionphotobooth.domain.model;

// EditState giữ toàn bộ lựa chọn chỉnh sửa hiện tại để có thể render lại bất cứ lúc nào.
public class EditState {
    public enum FilterStyle {
        NONE,
        SOFT,
        BW
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
}

