package com.example.expressionphotobooth.data.graphics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;

import com.example.expressionphotobooth.domain.model.EditState;
import com.example.expressionphotobooth.utils.StickerPlacementMapper;

// Renderer thuc hien toan bo xu ly anh theo EditState (filter -> frame -> sticker).
public class BitmapEditRenderer {

    public Bitmap render(Context context, Bitmap source, EditState state) {
        Bitmap target = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);

        // 1) Ve anh goc + filter mau.
        Paint filterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        filterPaint.setColorFilter(buildColorFilter(state.getFilterStyle(), state.getFilterIntensity()));
        canvas.drawBitmap(source, 0f, 0f, filterPaint);

        // 2) Ve frame theo style duoc chon.
        // drawFrame(canvas, state.getFrameStyle(), source.getWidth(), source.getHeight());

        // 3a) Legacy single sticker (REMOVED to prevent duplicates during migration).
        // drawSticker(context, canvas, state, source.getWidth(), source.getHeight());

        // 3b) New multi-sticker list.
        java.util.List<com.example.expressionphotobooth.domain.model.StickerItem> stickerItems = state.getStickerItems();
        for (com.example.expressionphotobooth.domain.model.StickerItem si : stickerItems) {
            drawStickerItem(context, canvas, si, state, source.getWidth(), source.getHeight());
        }

        return target;
    }

    private ColorMatrixColorFilter buildColorFilter(EditState.FilterStyle filterStyle, float intensity) {
        ColorMatrix matrix = new ColorMatrix();
        switch (filterStyle) {
            case SOFT:
                matrix.setSaturation(1.15f);
                ColorMatrix brightness = new ColorMatrix(new float[]{
                        1f, 0f, 0f, 0f, 14f,
                        0f, 1f, 0f, 0f, 14f,
                        0f, 0f, 1f, 0f, 14f,
                        0f, 0f, 0f, 1f, 0f
                });
                matrix.postConcat(brightness);
                break;
            case BW:
                matrix.setSaturation(0f);
                break;
            case VINTAGE:
                matrix.setSaturation(0.6f);
                ColorMatrix vintageColor = new ColorMatrix(new float[]{
                        1.2f, 0.2f, 0.0f, 0f, 0f,
                        0.0f, 1.0f, 0.1f, 0f, 0f,
                        0.0f, 0.0f, 0.8f, 0f, 0f,
                        0f,   0f,   0f,   1f, 0f
                });
                matrix.postConcat(vintageColor);
                break;
            case COOL:
                matrix.setSaturation(1.1f);
                ColorMatrix coolColor = new ColorMatrix(new float[]{
                        0.8f, 0.0f, 0.0f, 0f, 0f,
                        0.0f, 0.9f, 0.0f, 0f, 0f,
                        0.0f, 0.2f, 1.3f, 0f, 0f,
                        0f,   0f,   0f,   1f, 0f
                });
                matrix.postConcat(coolColor);
                break;
            case WARM:
                matrix.setSaturation(1.1f);
                ColorMatrix warmColor = new ColorMatrix(new float[]{
                        1.2f, 0.1f, 0.0f, 0f, 0f,
                        0.1f, 1.1f, 0.0f, 0f, 0f,
                        0.0f, 0.0f, 0.8f, 0f, 0f,
                        0f,   0f,   0f,   1f, 0f
                });
                matrix.postConcat(warmColor);
                break;
            case SEPIA:
                matrix.setSaturation(0f);
                ColorMatrix sepiaColor = new ColorMatrix(new float[]{
                        0.393f, 0.769f, 0.189f, 0f, 0f,
                        0.349f, 0.686f, 0.168f, 0f, 0f,
                        0.272f, 0.534f, 0.131f, 0f, 0f,
                        0f,     0f,     0f,     1f, 0f
                });
                matrix.postConcat(sepiaColor);
                break;
            case NONE:
            default:
                break;
        }

        if (filterStyle != EditState.FilterStyle.NONE) {
            float[] fm = matrix.getArray();
            float[] id = new float[]{
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
            };
            float[] res = new float[20];
            for (int i = 0; i < 20; i++) {
                res[i] = id[i] + (fm[i] - id[i]) * intensity;
            }
            matrix.set(res);
        }

        return new ColorMatrixColorFilter(matrix);
    }

    private void drawFrame(Canvas canvas, EditState.FrameStyle frameStyle, int width, int height) {
        if (frameStyle == EditState.FrameStyle.NONE) {
            return;
        }

        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(Math.max(10f, Math.min(width, height) * 0.04f));
        strokePaint.setColor(resolveFrameColor(frameStyle));
        canvas.drawRect(0f, 0f, width, height, strokePaint);

        // Ve label nho de nguoi dung nhin ro frame dang dung.
        Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgePaint.setStyle(Paint.Style.FILL);
        badgePaint.setColor(Color.argb(180, 0, 0, 0));

        Rect badge = new Rect(24, 24, 24 + 220, 24 + 68);
        canvas.drawRect(badge, badgePaint);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(34f);
        canvas.drawText(frameStyle.name(), 36f, 70f, textPaint);
    }

    private void drawSticker(Context context, Canvas canvas, EditState state, int width, int height) {
        if (state.getStickerStyle() == EditState.StickerStyle.NONE) {
            return;
        }

        Bitmap stickerBitmap = null;
        Drawable drawable = null;

        if (state.getStickerStyle() == EditState.StickerStyle.CUSTOM && state.getCustomStickerBase64() != null) {
            try {
                byte[] decodedString = android.util.Base64.decode(state.getCustomStickerBase64(), android.util.Base64.DEFAULT);
                stickerBitmap = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            int drawableId = resolveStickerDrawable(state.getStickerStyle());
            if (drawableId != 0) {
                drawable = ContextCompat.getDrawable(context, drawableId);
            }
        }

        if (stickerBitmap == null && drawable == null) {
            return;
        }

        int minL = Math.min(width, height);
        // Size của sticker dựa trên kích thước nhỏ nhất
        int size = Math.max(72, minL / 5);

        RectF cropRect = resolveCropRect(state, width, height);
        float cropX = state.getStickerCropX();
        float cropY = state.getStickerCropY();

        // Backward compatibility for older sessions that only stored full-image normalized coordinates.
        if (cropX < 0f || cropY < 0f) {
            float legacyX = state.getStickerX();
            float legacyY = state.getStickerY();
            if (legacyX >= 0f && legacyY >= 0f) {
                float cx = legacyX * width;
                float cy = legacyY * height;
                cropX = StickerPlacementMapper.clamp01((cx - cropRect.left) / Math.max(1f, cropRect.width()));
                cropY = StickerPlacementMapper.clamp01((cy - cropRect.top) / Math.max(1f, cropRect.height()));
            }
        }

        if (cropX < 0f || cropY < 0f) {
            cropX = 0.84f;
            cropY = 0.18f;
        }

        float centerX = cropRect.left + StickerPlacementMapper.clamp01(cropX) * cropRect.width();
        float centerY = cropRect.top + StickerPlacementMapper.clamp01(cropY) * cropRect.height();

        canvas.save();
        canvas.translate(centerX, centerY);
        canvas.scale(state.getStickerScale(), state.getStickerScale());
        canvas.rotate(state.getStickerRotation());

        if (stickerBitmap != null) {
            float ratio = (float) stickerBitmap.getWidth() / stickerBitmap.getHeight();
            int w, h;
            if (ratio > 1) {
                w = size;
                h = (int) (size / ratio);
            } else {
                h = size;
                w = (int) (size * ratio);
            }
            int hw = w / 2;
            int hh = h / 2;
            Rect dest = new Rect(-hw, -hh, hw, hh);
            canvas.drawBitmap(stickerBitmap, null, dest, new Paint(Paint.FILTER_BITMAP_FLAG));
        } else {
            int halfSize = size / 2;
            drawable.setBounds(-halfSize, -halfSize, halfSize, halfSize);
            drawable.draw(canvas);
        }
        canvas.restore();
    }

    private RectF resolveCropRect(EditState state, int width, int height) {
        float leftNorm = state.getStickerCropLeftNorm();
        float topNorm = state.getStickerCropTopNorm();
        float rightNorm = state.getStickerCropRightNorm();
        float bottomNorm = state.getStickerCropBottomNorm();

        if (leftNorm >= 0f && topNorm >= 0f && rightNorm > leftNorm && bottomNorm > topNorm) {
            return StickerPlacementMapper.fromNormalizedRect(
                    new RectF(leftNorm, topNorm, rightNorm, bottomNorm),
                    width,
                    height
            );
        }
        return new RectF(0f, 0f, width, height);
    }

    @ColorInt
    private int resolveFrameColor(EditState.FrameStyle frameStyle) {
        switch (frameStyle) {
            case CORTIS:
                return Color.parseColor("#E91E63");
            case T1:
                return Color.parseColor("#D32F2F");
            case AESPA:
                return Color.parseColor("#7E57C2");
            case NONE:
            default:
                return Color.TRANSPARENT;
        }
    }

    private int resolveStickerDrawable(EditState.StickerStyle stickerStyle) {
        switch (stickerStyle) {
            case STAR:
                return com.example.expressionphotobooth.R.drawable.ic_star_24;
            case FLASH:
                return com.example.expressionphotobooth.R.drawable.ic_flash_on_24;
            case CAMERA:
                return com.example.expressionphotobooth.R.drawable.ic_videocam_24;
            case HEART:
                return com.example.expressionphotobooth.R.drawable.ic_sticker_heart;
            case CROWN:
                return com.example.expressionphotobooth.R.drawable.ic_sticker_crown;
            case SMILE:
                return com.example.expressionphotobooth.R.drawable.ic_sticker_smile;
            case FLOWER:
                return com.example.expressionphotobooth.R.drawable.ic_sticker_flower;
            case BOW:
                return com.example.expressionphotobooth.R.drawable.ic_sticker_bow;
            case SPARKLE:
                return com.example.expressionphotobooth.R.drawable.ic_sticker_sparkle;
            case BUTTERFLY:
                return com.example.expressionphotobooth.R.drawable.ic_sticker_butterfly;
            case CHERRY:
                return com.example.expressionphotobooth.R.drawable.ic_sticker_cherry;
            case MUSIC:
                return com.example.expressionphotobooth.R.drawable.ic_sticker_music;
            case NONE:
            default:
                return 0;
        }
    }

    /**
     * Draws a single {@link com.example.expressionphotobooth.domain.model.StickerItem}
     * from the multi-sticker list onto the given canvas.
     */
    private void drawStickerItem(Context context, Canvas canvas,
                                 com.example.expressionphotobooth.domain.model.StickerItem si,
                                 EditState state, int width, int height) {
        if (si == null || si.getStyle() == EditState.StickerStyle.NONE) return;

        // Resolve the crop rect (frame hole boundary)
        RectF cropRect;
        float ln = si.getCropLeftNorm(), tn = si.getCropTopNorm(),
              rn = si.getCropRightNorm(), bn = si.getCropBottomNorm();
        if (ln >= 0f && tn >= 0f && rn > ln && bn > tn) {
            cropRect = StickerPlacementMapper.fromNormalizedRect(new RectF(ln, tn, rn, bn), width, height);
        } else {
            cropRect = new RectF(0f, 0f, width, height);
        }

        float centerX = cropRect.left + StickerPlacementMapper.clamp01(si.getCropX()) * cropRect.width();
        float centerY = cropRect.top  + StickerPlacementMapper.clamp01(si.getCropY()) * cropRect.height();

        int minL = Math.min(width, height);
        int size = Math.max(72, minL / 5);

        Bitmap stickerBitmap = null;
        Drawable drawable = null;

        if (si.getStyle() == EditState.StickerStyle.CUSTOM && si.getCustomBase64() != null) {
            try {
                byte[] bytes = android.util.Base64.decode(si.getCustomBase64(), android.util.Base64.DEFAULT);
                stickerBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            int drawableId = resolveStickerDrawable(si.getStyle());
            if (drawableId != 0) drawable = ContextCompat.getDrawable(context, drawableId);
        }

        if (stickerBitmap == null && drawable == null) return;

        canvas.save();
        canvas.translate(centerX, centerY);
        canvas.scale(si.getScale(), si.getScale());
        canvas.rotate(si.getRotation());

        if (stickerBitmap != null) {
            float ratio = (float) stickerBitmap.getWidth() / stickerBitmap.getHeight();
            int w = ratio > 1 ? size : (int)(size * ratio);
            int h = ratio > 1 ? (int)(size / ratio) : size;
            Rect dest = new Rect(-w/2, -h/2, w/2, h/2);
            canvas.drawBitmap(stickerBitmap, null, dest, new Paint(Paint.FILTER_BITMAP_FLAG));
        } else {
            int half = size / 2;
            drawable.setBounds(-half, -half, half, half);
            drawable.draw(canvas);
        }
        canvas.restore();
    }
}
