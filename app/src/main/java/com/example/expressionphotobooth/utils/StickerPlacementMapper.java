package com.example.expressionphotobooth.utils;

import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Shared geometry mapper for sticker placement so edit preview and final collage
 * use the same crop logic.
 */
public final class StickerPlacementMapper {

    private StickerPlacementMapper() {
    }

    public static RectF calculateCenterCropRect(float srcWidth, float srcHeight, float dstWidth, float dstHeight) {
        GeometryMath.CropRect crop = GeometryMath.calculateCenterCrop(srcWidth, srcHeight, dstWidth, dstHeight);
        return new RectF(crop.left, crop.top, crop.right, crop.bottom);
    }

    public static RectF resolveFrameCropRect(int frameResId, float srcWidth, float srcHeight) {
        List<Rect> holes = FrameConfig.getHolesForFrame(frameResId);
        if (holes.isEmpty()) {
            return new RectF(0f, 0f, srcWidth, srcHeight);
        }
        Rect primaryHole = holes.get(0);
        return calculateCenterCropRect(srcWidth, srcHeight, primaryHole.width(), primaryHole.height());
    }

    public static RectF calculateCenterCropViewContentRect(float viewWidth, float viewHeight, float bitmapWidth, float bitmapHeight) {
        if (viewWidth <= 0f || viewHeight <= 0f || bitmapWidth <= 0f || bitmapHeight <= 0f) {
            return new RectF(0f, 0f, viewWidth, viewHeight);
        }

        // ImageView centerCrop uses the larger scale to fill and then crop overflow.
        float scale = Math.max(viewWidth / bitmapWidth, viewHeight / bitmapHeight);
        float drawWidth = bitmapWidth * scale;
        float drawHeight = bitmapHeight * scale;
        float left = (viewWidth - drawWidth) / 2f;
        float top = (viewHeight - drawHeight) / 2f;
        return new RectF(left, top, left + drawWidth, top + drawHeight);
    }

    public static float clamp01(float value) {
        return GeometryMath.clamp01(value);
    }

    @NonNull
    public static RectF toNormalizedRect(@NonNull RectF sourceRect, float bitmapWidth, float bitmapHeight) {
        if (bitmapWidth <= 0f || bitmapHeight <= 0f) {
            return new RectF(0f, 0f, 1f, 1f);
        }
        return new RectF(
                clamp01(sourceRect.left / bitmapWidth),
                clamp01(sourceRect.top / bitmapHeight),
                clamp01(sourceRect.right / bitmapWidth),
                clamp01(sourceRect.bottom / bitmapHeight)
        );
    }

    @NonNull
    public static RectF fromNormalizedRect(@NonNull RectF normalizedRect, float bitmapWidth, float bitmapHeight) {
        return new RectF(
                normalizedRect.left * bitmapWidth,
                normalizedRect.top * bitmapHeight,
                normalizedRect.right * bitmapWidth,
                normalizedRect.bottom * bitmapHeight
        );
    }
}


