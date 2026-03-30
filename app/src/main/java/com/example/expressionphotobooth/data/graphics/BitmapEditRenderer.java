package com.example.expressionphotobooth.data.graphics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;

import com.example.expressionphotobooth.domain.model.EditState;

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
        drawFrame(canvas, state.getFrameStyle(), source.getWidth(), source.getHeight());

        // 3) Ve sticker icon len goc anh hoặc vị trí người dùng kéo thả.
        drawSticker(context, canvas, state, source.getWidth(), source.getHeight());

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
        int drawableId = resolveStickerDrawable(state.getStickerStyle());
        if (drawableId == 0) {
            return;
        }

        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        if (drawable == null) {
            return;
        }

        int minL = Math.min(width, height);
        // Size của sticker dựa trên kích thước nhỏ nhất
        int size = Math.max(72, minL / 5);

        float sx = state.getStickerX();
        float sy = state.getStickerY();

        int left, top;
        if (sx < 0 || sy < 0) {
            // Tính toán vùng an toàn (Safe Area) với tỷ lệ 3:4 để chống việc ResultActivity cắt mất rìa
            int safeW = (int) (minL * 0.75f);
            int safeH = (int) (minL * 0.75f);

            int centerX = width / 2;
            int centerY = height / 2;

            int padding = 24;
            
            // Đặt sticker ở góc trên - phải của vùng an toàn
            left = centerX + (safeW / 2) - size - padding;
            top = centerY - (safeH / 2) + padding;
        } else {
            // Sử dụng tọa độ do người dùng di chuyển (sx, sy là tâm của sticker)
            left = (int) (sx * width - size / 2f);
            top = (int) (sy * height - size / 2f);
        }

        drawable.setBounds(left, top, left + size, top + size);
        drawable.draw(canvas);
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
            case NONE:
            default:
                return 0;
        }
    }
}

