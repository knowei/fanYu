package com.example.animeresolver;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Stores source-specific title corrections and the last successfully matched detail page. */
public final class TitleAliasStore {
    private static final String PREFS = "source_title_aliases";

    private TitleAliasStore() {
    }

    public static String preferredName(Context context, String subjectKey, String sourceName) {
        return prefs(context).getString(key("name", subjectKey, sourceName), "");
    }

    public static String detailUrl(Context context, String subjectKey, String sourceName) {
        return prefs(context).getString(key("detail", subjectKey, sourceName), "");
    }

    public static void saveCorrection(
            Context context, String subjectKey, String sourceName, String preferredName) {
        String value = preferredName == null ? "" : preferredName.trim();
        SharedPreferences.Editor editor = prefs(context).edit();
        if (value.isBlank()) editor.remove(key("name", subjectKey, sourceName));
        else editor.putString(key("name", subjectKey, sourceName), value);
        // A changed title must not reuse a detail page selected using the old title.
        editor.remove(key("detail", subjectKey, sourceName)).apply();
    }

    public static void rememberMatch(
            Context context, String subjectKey, String sourceName,
            String searchedName, String detailUrl) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (searchedName != null && !searchedName.isBlank()) {
            editor.putString(key("last_name", subjectKey, sourceName), searchedName.trim());
        }
        if (detailUrl != null && !detailUrl.isBlank()) {
            editor.putString(key("detail", subjectKey, sourceName), detailUrl.trim());
        }
        editor.apply();
    }

    public static String subjectKey(String suppliedKey, int subjectId, String title) {
        if (suppliedKey != null && !suppliedKey.isBlank()) return suppliedKey;
        if (subjectId > 0) return "bangumi:" + subjectId;
        return "title:" + digest(title == null ? "" : title.toLowerCase(Locale.ROOT));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String kind, String subjectKey, String sourceName) {
        return kind + ":" + digest(subjectKey + "\n" + sourceName);
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < 12; index++) {
                result.append(String.format(Locale.ROOT, "%02x", bytes[index]));
            }
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
