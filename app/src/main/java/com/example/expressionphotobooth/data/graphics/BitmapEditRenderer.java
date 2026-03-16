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
        filterPaint.setColorFilter(buildColorFilter(state.getFilterStyle()));
        canvas.drawBitmap(source, 0f, 0f, filterPaint);

        // 2) Ve frame theo style duoc chon.
        drawFrame(canvas, state.getFrameStyle(), source.getWidth(), source.getHeight());

        // 3) Ve sticker icon len goc anh.
        drawSticker(context, canvas, state.getStickerStyle(), source.getWidth(), source.getHeight());

        return target;
    }

    private ColorMatrixColorFilter buildColorFilter(EditState.FilterStyle filterStyle) {
        ColorMatrix matrix = new ColorMatrix();
        if (filterStyle == EditState.FilterStyle.SOFT) {
            matrix.setSaturation(1.15f);
            ColorMatrix brightness = new ColorMatrix(new float[]{
                    1f, 0f, 0f, 0f, 14f,
                    0f, 1f, 0f, 0f, 14f,
                    0f, 0f, 1f, 0f, 14f,
                    0f, 0f, 0f, 1f, 0f
            });
            matrix.postConcat(brightness);
        } else if (filterStyle == EditState.FilterStyle.BW) {
            matrix.setSaturation(0f);
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

    private void drawSticker(Context context, Canvas canvas, EditState.StickerStyle stickerStyle, int width, int height) {
        int drawableId = resolveStickerDrawable(stickerStyle);
        if (drawableId == 0) {
            return;
        }

        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        if (drawable == null) {
            return;
        }

        int size = Math.max(72, Math.min(width, height) / 6);
        int left = width - size - 28;
        int top = 28;
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
                return android.R.drawable.btn_star_big_on;
            case FLASH:
                return android.R.drawable.ic_menu_compass;
            case CAMERA:
                return android.R.drawable.ic_menu_camera;
            case NONE:
            default:
                return 0;
        }
    }
}

