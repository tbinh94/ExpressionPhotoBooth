package com.example.expressionphotobooth.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

public final class LocaleManager {
    private static final String PREFS_NAME = "PhotoboothPrefs";
    private static final String KEY_APP_LANGUAGE = "APP_LANGUAGE";
    public static final String LANG_VI = "vi";
    public static final String LANG_EN = "en";
    private static final long SWITCH_COOLDOWN_MS = 600L;
    private static long lastSwitchTimestamp;

    private LocaleManager() {
        // Utility class
    }

    public static void applySavedLocale(Context context) {
        String savedLanguage = getSavedLanguage(context);
        applyAppLocale(savedLanguage);
    }

    public static boolean toggleLanguage(@NonNull AppCompatActivity activity) {
        String current = getCurrentLanguage(activity);
        String next = LANG_VI.equals(current) ? LANG_EN : LANG_VI;
        return switchLanguage(activity, next);
    }

    // Smooth mode: persist new language and let current screen update texts manually.
    public static String toggleLanguageWithoutRecreate(@NonNull Context context) {
        String current = getCurrentLanguage(context);
        String next = LANG_VI.equals(current) ? LANG_EN : LANG_VI;
        return switchLanguageWithoutRecreate(context, next);
    }

    public static String switchLanguageWithoutRecreate(@NonNull Context context, @NonNull String languageTag) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastSwitchTimestamp < SWITCH_COOLDOWN_MS) {
            return getCurrentLanguage(context);
        }

        String next = normalizeLanguage(languageTag);
        persistLanguage(context, next);
        lastSwitchTimestamp = now;
        return next;
    }

    public static boolean switchLanguage(@NonNull AppCompatActivity activity, @NonNull String languageTag) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastSwitchTimestamp < SWITCH_COOLDOWN_MS) {
            return false;
        }

        String next = normalizeLanguage(languageTag);
        String current = getCurrentLanguage(activity);
        if (next.equals(current)) {
            return false;
        }

        persistLanguage(activity, next);
        lastSwitchTimestamp = now;

        // Keep transition minimal while AppCompat updates localized resources.
        activity.overridePendingTransition(0, 0);
        applyAppLocale(next);
        return true;
    }

    public static Context wrapContext(@NonNull Context base) {
        String language = getSavedLanguage(base);
        return createLocalizedContext(base, language);
    }

    public static Context createLocalizedContext(@NonNull Context base, @NonNull String languageTag) {
        Locale locale = new Locale(normalizeLanguage(languageTag));
        Locale.setDefault(locale);

        Resources resources = base.getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        return base.createConfigurationContext(config);
    }

    public static String getString(@NonNull Context context, @StringRes int resId, @NonNull String languageTag) {
        Context localized = createLocalizedContext(context, languageTag);
        return localized.getString(resId);
    }

    public static boolean isVietnamese(@NonNull Context context) {
        return LANG_VI.equals(getCurrentLanguage(context));
    }

    public static String getSavedLanguage(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_APP_LANGUAGE, LANG_VI);
        return normalizeLanguage(value);
    }

    public static String getCurrentLanguage(@NonNull Context context) {
        // In smooth no-recreate mode, SharedPreferences is the immediate source of truth.
        String saved = getSavedLanguage(context);
        if (!saved.isEmpty()) {
            return saved;
        }

        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (!locales.isEmpty()) {
            Locale locale = locales.get(0);
            if (locale != null) {
                String language = locale.getLanguage();
                if (language != null) {
                    return normalizeLanguage(language);
                }
            }
        }


        return normalizeLanguage(Locale.getDefault().getLanguage());
    }

    private static void applyAppLocale(@NonNull String languageTag) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag));
    }

    private static void persistLanguage(@NonNull Context context, @NonNull String languageTag) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_APP_LANGUAGE, languageTag).apply();
    }

    private static String normalizeLanguage(String value) {
        if (value == null) {
            return LANG_VI;
        }
        return LANG_EN.equalsIgnoreCase(value) ? LANG_EN : LANG_VI;
    }
}


