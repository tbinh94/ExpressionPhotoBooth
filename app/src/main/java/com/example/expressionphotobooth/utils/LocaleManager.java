package com.example.expressionphotobooth.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

public final class LocaleManager {
    private static final String PREFS_NAME = "PhotoboothPrefs";
    private static final String KEY_APP_LANGUAGE = "APP_LANGUAGE";
    private static final String LANG_VI = "vi";
    private static final String LANG_EN = "en";

    private LocaleManager() {
        // Utility class
    }

    public static void applySavedLocale(Context context) {
        String savedLanguage = getSavedLanguage(context);
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedLanguage));
    }

    public static void toggleLanguage(Context context) {
        String current = getCurrentLanguage();
        String next = LANG_VI.equals(current) ? LANG_EN : LANG_VI;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_APP_LANGUAGE, next).apply();
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(next));
    }

    public static boolean isVietnamese() {
        return LANG_VI.equals(getCurrentLanguage());
    }

    private static String getSavedLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_APP_LANGUAGE, LANG_VI);
        if (!LANG_VI.equals(value) && !LANG_EN.equals(value)) {
            return LANG_VI;
        }
        return value;
    }

    private static String getCurrentLanguage() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (!locales.isEmpty()) {
            Locale locale = locales.get(0);
            if (locale != null) {
                String language = locale.getLanguage();
                if (language != null && (LANG_VI.equals(language) || LANG_EN.equals(language))) {
                    return language;
                }
            }
        }

        String fallback = Locale.getDefault().getLanguage();
        return LANG_EN.equals(fallback) ? LANG_EN : LANG_VI;
    }
}


