package com.example.expressionphotobooth.ui.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class AiRatioPieChartView extends View {
    private final Paint registeredPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unregisteredPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private int registeredCount;
    private int totalCount;

    public AiRatioPieChartView(Context context) {
        super(context);
        init();
    }

    public AiRatioPieChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AiRatioPieChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        registeredPaint.setStyle(Paint.Style.FILL);
        registeredPaint.setColor(0xFF3D68E8);

        unregisteredPaint.setStyle(Paint.Style.FILL);
        unregisteredPaint.setColor(0xFFE2E8F0);
    }

    public void setData(int registered, int total) {
        registeredCount = Math.max(0, registered);
        totalCount = Math.max(0, total);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float size = Math.min(getWidth(), getHeight());
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        arcBounds.set(left, top, left + size, top + size);

        if (totalCount <= 0) {
            canvas.drawArc(arcBounds, -90f, 360f, true, unregisteredPaint);
            return;
        }

        float registeredSweep = (registeredCount * 360f) / totalCount;
        canvas.drawArc(arcBounds, -90f, registeredSweep, true, registeredPaint);
        canvas.drawArc(arcBounds, -90f + registeredSweep, 360f - registeredSweep, true, unregisteredPaint);
    }
}

