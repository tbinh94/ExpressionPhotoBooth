package com.example.expressionphotobooth.utils;

/**
 * Pure math helpers so geometry logic can be unit-tested on JVM.
 */
public final class GeometryMath {

    private GeometryMath() {
    }

    public static CropRect calculateCenterCrop(float srcWidth, float srcHeight, float dstWidth, float dstHeight) {
        if (srcWidth <= 0f || srcHeight <= 0f || dstWidth <= 0f || dstHeight <= 0f) {
            return new CropRect(0f, 0f, srcWidth, srcHeight);
        }

        float srcAspect = srcWidth / srcHeight;
        float dstAspect = dstWidth / dstHeight;

        if (srcAspect > dstAspect) {
            float cropWidth = srcHeight * dstAspect;
            float left = (srcWidth - cropWidth) / 2f;
            return new CropRect(left, 0f, left + cropWidth, srcHeight);
        }

        float cropHeight = srcWidth / dstAspect;
        float top = (srcHeight - cropHeight) / 2f;
        return new CropRect(0f, top, srcWidth, top + cropHeight);
    }

    public static float clamp01(float value) {
        if (value < 0f) return 0f;
        if (value > 1f) return 1f;
        return value;
    }

    public static float normalizedToAbsolute(float start, float length, float normalized) {
        return start + clamp01(normalized) * length;
    }

    public static float absoluteToNormalized(float start, float length, float absolute) {
        if (length <= 0f) {
            return 0f;
        }
        return clamp01((absolute - start) / length);
    }

    public static final class CropRect {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public CropRect(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public float width() {
            return right - left;
        }

        public float height() {
            return bottom - top;
        }
    }
}

