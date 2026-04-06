package com.example.expressionphotobooth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class AiOverlayView extends View {

    private final Paint boxPaint = new Paint();
    private final Paint labelBgPaint = new Paint();
    private final Paint labelTextPaint = new Paint();
    private final List<Rect> boxes = new ArrayList<>();
    private String label = "";
    private int boxColor = Color.parseColor("#00E676"); // Mặc định xanh lá

    public AiOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(6.0f);
        boxPaint.setAntiAlias(true);

        labelBgPaint.setStyle(Paint.Style.FILL);
        labelBgPaint.setAntiAlias(true);

        labelTextPaint.setColor(Color.WHITE);
        labelTextPaint.setTextSize(48f);
        labelTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        labelTextPaint.setAntiAlias(true);
    }

    /**
     * Cập nhật overlay với danh sách hộp và nhãn mới.
     * color: Color.GREEN cho tay, Color.CYAN cho mặt, v.v.
     */
    public void updateOverlay(List<Rect> newBoxes, String newLabel, int color) {
        boxes.clear();
        if (newBoxes != null) boxes.addAll(newBoxes);
        label = newLabel != null ? newLabel : "";
        boxColor = color;
        boxPaint.setColor(color);
        labelBgPaint.setColor(color);
        postInvalidate();
    }

    /** Overload không cần chỉ định màu (dùng màu mặc định xanh lá) */
    public void updateOverlay(List<Rect> newBoxes, String newLabel) {
        updateOverlay(newBoxes, newLabel, Color.parseColor("#00E676"));
    }

    /**
     * Cập nhật overlay cho 1 detection duy nhất (tiện lợi cho analyze callback)
     */
    public void setDetection(Rect box, String label) {
        List<Rect> boxes = new ArrayList<>();
        if (box != null) boxes.add(box);
        updateOverlay(boxes, label);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Rect box : boxes) {
            // Vẽ viền khung
            canvas.drawRect(box, boxPaint);

            // Vẽ nền label phía trên khung
            if (!label.isEmpty()) {
                float textWidth = labelTextPaint.measureText(label);
                float bgLeft = box.left;
                float bgTop =  Math.max(0, box.top - 60);
                float bgRight = bgLeft + textWidth + 20;
                float bgBottom = bgTop + 56;
                canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, labelBgPaint);
                canvas.drawText(label, bgLeft + 10, bgBottom - 8, labelTextPaint);
            }
        }
    }
}
