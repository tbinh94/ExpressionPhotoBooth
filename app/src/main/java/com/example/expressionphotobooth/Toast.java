package com.example.expressionphotobooth;

import android.content.Context;

/**
 * Custom Toast class to disable all toasts for a more professional experience.
 */
public class Toast {
    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;

    public static Toast makeText(Context context, CharSequence text, int duration) {
        return new Toast();
    }

    public static Toast makeText(Context context, int resId, int duration) {
        return new Toast();
    }

    public void show() {
        // Do nothing to disable toast
    }
}
