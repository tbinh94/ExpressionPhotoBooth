package com.example.expressionphotobooth.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeManager {
    private static final String PREFS_NAME = "PhotoboothPrefs";
    private static final String KEY_THEME_MODE = "APP_THEME_MODE";

    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";
    public static final String MODE_SYSTEM = "system";

    private ThemeManager() {
        // Utility class
    }

    public static void applySavedTheme(@NonNull Context context) {
        applyThemeMode(getSavedThemeMode(context));
    }

    public static void setThemeMode(@NonNull Context context, @NonNull String mode) {
        String normalized = normalizeMode(mode);
        persistMode(context, normalized);
        applyThemeMode(normalized);
    }

    @NonNull
    public static String getSavedThemeMode(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return normalizeMode(prefs.getString(KEY_THEME_MODE, MODE_LIGHT));
    }

    private static void applyThemeMode(@NonNull String mode) {
        switch (normalizeMode(mode)) {
            case MODE_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case MODE_SYSTEM:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case MODE_LIGHT:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
        }
    }

    private static void persistMode(@NonNull Context context, @NonNull String mode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_THEME_MODE, mode).apply();
    }

    @NonNull
    private static String normalizeMode(String mode) {
        if (MODE_DARK.equalsIgnoreCase(mode)) {
            return MODE_DARK;
        }
        if (MODE_SYSTEM.equalsIgnoreCase(mode)) {
            return MODE_SYSTEM;
        }
        return MODE_LIGHT;
    }
}

