package com.example.animeresolver;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.regex.Pattern;

final class DiagnosticStore {
    private static final String PREFS = "diagnostics";
    private static final String KEY = "events";
    private static final int MAX_EVENTS = 120;
    private static final Pattern URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(cookie|authorization|token|sign|signature|key|session|cf_clearance)\\s*[:=]\\s*[^\\s,;]+"
    );

    private DiagnosticStore() {}

    static synchronized void record(Context context, String stage, String source,
                                    String message, String url) {
        JSONArray old = read(context);
        JSONArray updated = new JSONArray();
        try {
            JSONObject event = new JSONObject();
            event.put("time", System.currentTimeMillis());
            event.put("stage", sanitize(stage));
            event.put("source", sanitize(source));
            event.put("message", sanitize(message));
            event.put("site", sanitizeUrl(url));
            updated.put(event);
            for (int i = 0; i < old.length() && updated.length() < MAX_EVENTS; i++) {
                JSONObject item = old.optJSONObject(i);
                if (item != null) updated.put(item);
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY, updated.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static synchronized JSONObject report(Context context) {
        JSONObject report = new JSONObject();
        try {
            report.put("generatedAt", System.currentTimeMillis());
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            report.put("appVersion", packageInfo.versionName == null ? "" : packageInfo.versionName);
            report.put("versionCode", Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? packageInfo.getLongVersionCode() : packageInfo.versionCode);
            report.put("androidSdk", Build.VERSION.SDK_INT);
            report.put("device", sanitize(Build.MANUFACTURER + " " + Build.MODEL));
            report.put("indexSource", IndexSourceStore.get(context));
            report.put("events", read(context));
            report.put("privacy", "Cookies, query parameters and full media URLs are not included.");
        } catch (Exception ignored) {
        }
        return report;
    }

    static synchronized int count(Context context) {
        return read(context).length();
    }

    static synchronized void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    static String sanitize(String value) {
        if (value == null) return "";
        String cleaned = URL.matcher(value.replace('\n', ' ').replace('\r', ' ')).replaceAll("[url]");
        cleaned = SECRET.matcher(cleaned).replaceAll("$1=[redacted]");
        cleaned = cleaned.trim();
        return cleaned.length() > 240 ? cleaned.substring(0, 240) : cleaned;
    }

    static String sanitizeUrl(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            String host = uri.getHost();
            if (host == null) return "";
            String path = uri.getPath() == null ? "" : uri.getPath();
            return scheme + "://" + host + path;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static JSONArray read(Context context) {
        try {
            return new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }
}
