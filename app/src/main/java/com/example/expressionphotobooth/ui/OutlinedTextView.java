package com.example.expressionphotobooth.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.annotation.ColorInt;
import androidx.appcompat.widget.AppCompatTextView;

import com.example.expressionphotobooth.R;

public class OutlinedTextView extends AppCompatTextView {
    @ColorInt
    private int strokeColor = 0xFFFFFFFF;
    private float strokeWidthPx = 0f;

    public OutlinedTextView(Context context) {
        super(context);
        init(context, null);
    }

    public OutlinedTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public OutlinedTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs == null) {
            return;
        }
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.OutlinedTextView);
        strokeColor = ta.getColor(R.styleable.OutlinedTextView_strokeColor, strokeColor);
        strokeWidthPx = ta.getDimension(R.styleable.OutlinedTextView_strokeWidth, strokeWidthPx);
        ta.recycle();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (strokeWidthPx > 0f) {
            final Paint paint = getPaint();
            final int fillColor = getCurrentTextColor();
            final Paint.Style oldStyle = paint.getStyle();
            final float oldWidth = paint.getStrokeWidth();
            final Paint.Join oldJoin = paint.getStrokeJoin();
            final Paint.Cap oldCap = paint.getStrokeCap();

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidthPx);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            setTextColor(strokeColor);
            super.onDraw(canvas);

            paint.setStyle(Paint.Style.FILL);
            setTextColor(fillColor);
            super.onDraw(canvas);

            paint.setStyle(oldStyle);
            paint.setStrokeWidth(oldWidth);
            paint.setStrokeJoin(oldJoin);
            paint.setStrokeCap(oldCap);
        } else {
            super.onDraw(canvas);
        }
    }
}


