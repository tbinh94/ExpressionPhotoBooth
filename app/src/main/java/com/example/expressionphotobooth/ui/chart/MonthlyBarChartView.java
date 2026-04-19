package com.example.expressionphotobooth.ui.chart;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
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
    private int barStartColor = Color.parseColor("#5A8AEE");
    private int barEndColor = Color.parseColor("#4266B8");
    private boolean showYAxis = true;
    private String legendText = "";
    private float animationProgress = 0f;
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
        axisPaint.setColor(Color.parseColor("#E2E8F0"));
        axisPaint.setStrokeWidth(dp(1.5f));

        gridPaint.setColor(Color.parseColor("#E2E8F0"));
        gridPaint.setStrokeWidth(dp(1f));

        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setColor(barStartColor);

        labelPaint.setColor(Color.parseColor("#94A3B8"));
        labelPaint.setTextSize(sp(10.5f));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        valuePaint.setColor(Color.parseColor("#1E293B"));
        valuePaint.setTextSize(sp(10f));
        valuePaint.setFakeBoldText(true);
        valuePaint.setTextAlign(Paint.Align.CENTER);

        yValuePaint.setColor(Color.parseColor("#94A3B8"));
        yValuePaint.setTextSize(sp(9f));
        yValuePaint.setTextAlign(Paint.Align.RIGHT);

        legendTextPaint.setColor(Color.parseColor("#475569"));
        legendTextPaint.setTextSize(sp(12f));
        legendTextPaint.setFakeBoldText(true);

        legendDotPaint.setStyle(Paint.Style.FILL);
        legendDotPaint.setColor(barStartColor);
    }

    public void setBarColors(int startColor, int endColor) {
        this.barStartColor = startColor;
        this.barEndColor = endColor;
        barPaint.setColor(startColor);
        legendDotPaint.setColor(startColor);
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
        barAnimator.setDuration(1000L);
        barAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
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
        float leftMargin = showYAxis ? dp(40f) : dp(12f);
        float rightMargin = dp(12f);
        float bottomMargin = dp(28f);
        float legendExtra = legendText.isEmpty() ? 0f : dp(24f);
        float topMargin = dp(12f) + legendExtra;

        float left = getPaddingLeft() + leftMargin;
        float right = width - getPaddingRight() - rightMargin;
        float top = getPaddingTop() + topMargin;
        float bottom = height - getPaddingBottom() - bottomMargin;

        if (right <= left || bottom <= top) return;

        float maxValue = 0.1f;
        for (MonthlyChartPoint point : points) {
            maxValue = Math.max(maxValue, point.getValue());
        }
        maxValue *= 1.15f; // Add 15% head room

        // Legend
        if (!legendText.isEmpty()) {
            float lx = left;
            float ly = getPaddingTop() + dp(14f);
            reusableBarRect.set(lx, ly - dp(6f), lx + dp(12f), ly + dp(6f));
            canvas.drawRoundRect(reusableBarRect, dp(4f), dp(4f), legendDotPaint);
            canvas.drawText(legendText, lx + dp(20f), ly + dp(4.5f), legendTextPaint);
        }

        boolean isIntegerData = true;
        for (MonthlyChartPoint point : points) {
            if (point.getValue() != Math.floor(point.getValue())) {
                isIntegerData = false;
                break;
            }
        }
        
        if (isIntegerData) {
            maxValue = (float) Math.ceil(maxValue);
            if (maxValue < 4) maxValue = 4f;
        }

        // Horizontal Grid Lines (Dashed)
        gridPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(4f), dp(4f)}, 0f));
        int gridCount = 4; // 0, 33%, 66%, 100%
        for (int i = 0; i < gridCount; i++) {
            float y = bottom - ((bottom - top) * i / (float)(gridCount - 1));
            canvas.drawLine(left, y, right, y, gridPaint);
            if (showYAxis) {
                float v = (maxValue * i) / (float)(gridCount - 1);
                String yText;
                if (isIntegerData && v == Math.floor(v)) {
                    yText = String.format(Locale.getDefault(), "%.0f", v);
                } else {
                    yText = String.format(Locale.getDefault(), "%.1f", v);
                    if (yText.endsWith(",0") || yText.endsWith(".0")) {
                        yText = yText.substring(0, yText.length() - 2);
                    }
                }
                canvas.drawText(yText, left - dp(10f), y + dp(4f), yValuePaint);
            }
        }
        gridPaint.setPathEffect(null);

        // Draw Bars
        float usableWidth = right - left;
        float slotWidth = usableWidth / points.size();
        float barWidth = slotWidth * 0.65f;
        float maxBarWidth = dp(24f);
        if (barWidth > maxBarWidth) barWidth = maxBarWidth;

        for (int i = 0; i < points.size(); i++) {
            MonthlyChartPoint point = points.get(i);
            float centerX = left + (i * slotWidth) + (slotWidth / 2f);
            float animatedValue = point.getValue() * animationProgress;
            float barHeight = (bottom - top) * (animatedValue / maxValue);
            float barTop = bottom - barHeight;

            reusableBarRect.set(centerX - barWidth / 2f, barTop, centerX + barWidth / 2f, bottom);

            // Gradient for bar
            Shader shader = new LinearGradient(centerX, barTop, centerX, bottom, barStartColor, barEndColor, Shader.TileMode.CLAMP);
            barPaint.setShader(shader);

            // Draw full capsule (radius = width / 2)
            float radius = barWidth / 2f;
            canvas.drawRoundRect(reusableBarRect, radius, radius, barPaint);
            barPaint.setShader(null); // Clear shader for next draws

            // Value label on top of bar
            if (animationProgress > 0.8f) {
                canvas.drawText(point.getValueText(), centerX, barTop - dp(6f), valuePaint);
            }

            // X-Axis label (Month)
            canvas.drawText(point.getLabel(), centerX, bottom + dp(18f), labelPaint);
        }
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
