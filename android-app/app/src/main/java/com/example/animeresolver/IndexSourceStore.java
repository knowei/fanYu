package com.example.animeresolver;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores the user's preferred source for the broadcast index. */
public final class IndexSourceStore {
    public static final String AUTO = "auto";
    public static final String BANGUMI = "bangumi";
    public static final String ANIFUN = "anifun";

    private static final String PREFS = "index_source";
    private static final String KEY = "selected";

    private IndexSourceStore() {}

    public static String get(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, AUTO);
    }

    public static void set(Context context, String value) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, value).apply();
    }

    public static String displayName(Context context) {
        return switch (get(context)) {
            case BANGUMI -> "Bangumi / AniList";
            case ANIFUN -> "AniFun";
            default -> "自动";
        };
    }
}
