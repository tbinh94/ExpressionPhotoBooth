package com.example.expressionphotobooth.domain.model;

/**
 * Represents a single placed sticker on a photo.
 * Replaces the single-sticker fields in EditState to support multiple stickers.
 */
public class StickerItem {

    // Which built-in or custom style
    private EditState.StickerStyle style = EditState.StickerStyle.NONE;

    // For CUSTOM stickers: base64-encoded PNG
    private String customBase64 = null;
    private String customId = null;      // Firestore doc ID
    private boolean fromStore = false;

    // Position (normalised 0-1 within frame crop rect)
    private float cropX = 0.84f;
    private float cropY = 0.18f;

    // Absolute normalised position within full bitmap (0-1) — kept in sync for legacy renderer
    private float absX = -1f;
    private float absY = -1f;

    // Normalised crop-rect bounds within source bitmap (0-1)
    private float cropLeftNorm  = -1f;
    private float cropTopNorm   = -1f;
    private float cropRightNorm = -1f;
    private float cropBottomNorm = -1f;

    private float scale    = 1.0f;
    private float rotation = 0f;

    public StickerItem() {}

    public StickerItem(StickerItem other) {
        if (other == null) return;
        this.style           = other.style;
        this.customBase64    = other.customBase64;
        this.customId        = other.customId;
        this.fromStore       = other.fromStore;
        this.cropX           = other.cropX;
        this.cropY           = other.cropY;
        this.absX            = other.absX;
        this.absY            = other.absY;
        this.cropLeftNorm    = other.cropLeftNorm;
        this.cropTopNorm     = other.cropTopNorm;
        this.cropRightNorm   = other.cropRightNorm;
        this.cropBottomNorm  = other.cropBottomNorm;
        this.scale           = other.scale;
        this.rotation        = other.rotation;
    }

    public StickerItem copy() { return new StickerItem(this); }

    // ── Getters / Setters ──────────────────────────────────────────────────────

    public EditState.StickerStyle getStyle()                     { return style; }
    public void setStyle(EditState.StickerStyle style)           { this.style = style; }

    public String getCustomBase64()                              { return customBase64; }
    public void setCustomBase64(String b64)                      { this.customBase64 = b64; }

    public String getCustomId()                                  { return customId; }
    public void setCustomId(String id)                           { this.customId = id; }

    public boolean isFromStore()                                 { return fromStore; }
    public void setFromStore(boolean fromStore)                  { this.fromStore = fromStore; }

    public float getCropX()                                      { return cropX; }
    public void setCropX(float cropX)                            { this.cropX = cropX; }

    public float getCropY()                                      { return cropY; }
    public void setCropY(float cropY)                            { this.cropY = cropY; }

    public float getAbsX()                                       { return absX; }
    public void setAbsX(float absX)                              { this.absX = absX; }

    public float getAbsY()                                       { return absY; }
    public void setAbsY(float absY)                              { this.absY = absY; }

    public float getCropLeftNorm()                               { return cropLeftNorm; }
    public void setCropLeftNorm(float v)                         { this.cropLeftNorm = v; }

    public float getCropTopNorm()                                { return cropTopNorm; }
    public void setCropTopNorm(float v)                          { this.cropTopNorm = v; }

    public float getCropRightNorm()                              { return cropRightNorm; }
    public void setCropRightNorm(float v)                        { this.cropRightNorm = v; }

    public float getCropBottomNorm()                             { return cropBottomNorm; }
    public void setCropBottomNorm(float v)                       { this.cropBottomNorm = v; }

    public float getScale()                                      { return scale; }
    public void setScale(float scale)                            { this.scale = scale; }

    public float getRotation()                                   { return rotation; }
    public void setRotation(float rotation)                      { this.rotation = rotation; }
}
