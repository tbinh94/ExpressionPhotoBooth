package com.example.expressionphotobooth.ui.chart;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MonthlyBarChartView extends View {
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint yValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF reusableBarRect = new RectF();
    private final List<MonthlyChartPoint> points = new ArrayList<>();
    private int barColor = Color.parseColor("#3D68E8");
    private boolean showYAxis = true;
    private String legendText = "";
    private float animationProgress = 1f;
    private ValueAnimator barAnimator;

    public MonthlyBarChartView(Context context) {
        super(context);
        init();
    }

    public MonthlyBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MonthlyBarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        axisPaint.setColor(Color.parseColor("#D5DEEA"));
        axisPaint.setStrokeWidth(dp(1f));

        gridPaint.setColor(Color.parseColor("#EEF2F8"));
        gridPaint.setStrokeWidth(dp(1f));

        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setColor(barColor);

        labelPaint.setColor(Color.parseColor("#6D7F99"));
        labelPaint.setTextSize(sp(10f));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        valuePaint.setColor(Color.parseColor("#2D3A4D"));
        valuePaint.setTextSize(sp(10f));
        valuePaint.setFakeBoldText(true);
        valuePaint.setTextAlign(Paint.Align.CENTER);

        yValuePaint.setColor(Color.parseColor("#9AAAC1"));
        yValuePaint.setTextSize(sp(9f));
        yValuePaint.setTextAlign(Paint.Align.RIGHT);

        legendTextPaint.setColor(Color.parseColor("#5E718D"));
        legendTextPaint.setTextSize(sp(11f));
        legendTextPaint.setFakeBoldText(true);

        legendDotPaint.setStyle(Paint.Style.FILL);
        legendDotPaint.setColor(barColor);
    }

    public void setBarColor(int color) {
        this.barColor = color;
        barPaint.setColor(color);
        legendDotPaint.setColor(color);
        invalidate();
    }

    public void setShowYAxis(boolean showYAxis) {
        this.showYAxis = showYAxis;
        invalidate();
    }

    public void setLegendText(String legendText) {
        this.legendText = legendText == null ? "" : legendText;
        invalidate();
    }

    public void setChartData(List<MonthlyChartPoint> data) {
        setChartData(data, true);
    }

    public void setChartData(List<MonthlyChartPoint> data, boolean animate) {
        points.clear();
        if (data != null) {
            points.addAll(data);
        }
        startBarAnimation(animate);
    }

    private void startBarAnimation(boolean animate) {
        if (barAnimator != null) {
            barAnimator.cancel();
            barAnimator = null;
        }
        if (!animate || points.isEmpty()) {
            animationProgress = 1f;
            invalidate();
            return;
        }
        animationProgress = 0f;
        barAnimator = ValueAnimator.ofFloat(0f, 1f);
        barAnimator.setDuration(720L);
        barAnimator.setInterpolator(new DecelerateInterpolator());
        barAnimator.addUpdateListener(animator -> {
            animationProgress = (float) animator.getAnimatedValue();
            invalidate();
        });
        barAnimator.start();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (points.isEmpty()) {
            return;
        }

        float width = getWidth();
        float height = getHeight();
        float left = getPaddingLeft() + (showYAxis ? dp(34f) : dp(8f));
        float right = width - getPaddingRight() - dp(8f);
        float legendExtra = legendText.isEmpty() ? 0f : dp(18f);
        float top = getPaddingTop() + dp(8f) + legendExtra;
        float bottom = height - getPaddingBottom() - dp(24f);

        if (right <= left || bottom <= top) {
            return;
        }

        float maxValue = 1f;
        for (MonthlyChartPoint point : points) {
            maxValue = Math.max(maxValue, point.getValue());
        }

        if (!legendText.isEmpty()) {
            float cy = getPaddingTop() + dp(10f);
            canvas.drawCircle(left, cy, dp(4f), legendDotPaint);
            canvas.drawText(legendText, left + dp(10f), cy + dp(3.5f), legendTextPaint);
        }

        canvas.drawLine(left, bottom, right, bottom, axisPaint);
        if (showYAxis) {
            canvas.drawLine(left, top, left, bottom, axisPaint);
        }

        for (int i = 0; i <= 4; i++) {
            float y = top + ((bottom - top) * i / 4f);
            canvas.drawLine(left, y, right, y, gridPaint);
            if (showYAxis) {
                float yValue = maxValue - ((maxValue * i) / 4f);
                String yText = yValue >= 10f
                        ? String.format(Locale.getDefault(), "%.0f", yValue)
                        : String.format(Locale.getDefault(), "%.1f", yValue);
                canvas.drawText(yText, left - dp(6f), y + dp(3f), yValuePaint);
            }
        }

        float usableWidth = right - left;
        float slotWidth = usableWidth / points.size();
        float barWidth = Math.min(dp(26f), slotWidth * 0.55f);

        for (int i = 0; i < points.size(); i++) {
            MonthlyChartPoint point = points.get(i);
            float centerX = left + (i * slotWidth) + (slotWidth / 2f);
            float animatedValue = point.getValue() * animationProgress;
            float ratio = animatedValue / maxValue;
            float barHeight = Math.max(dp(2f), (bottom - top) * ratio);
            float barTop = bottom - barHeight;

            reusableBarRect.set(centerX - barWidth / 2f, barTop, centerX + barWidth / 2f, bottom);
            canvas.drawRoundRect(reusableBarRect, dp(6f), dp(6f), barPaint);

            String drawValueText = animationProgress >= 0.98f
                    ? point.getValueText()
                    : (animatedValue >= 10f
                    ? String.format(Locale.getDefault(), "%.0f", animatedValue)
                    : String.format(Locale.getDefault(), "%.1f", animatedValue));
            canvas.drawText(drawValueText, centerX, barTop - dp(4f), valuePaint);
            canvas.drawText(point.getLabel(), centerX, bottom + dp(14f), labelPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (barAnimator != null) {
            barAnimator.cancel();
            barAnimator = null;
        }
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}

