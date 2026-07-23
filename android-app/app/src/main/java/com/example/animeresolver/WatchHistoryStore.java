package com.example.animeresolver;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

final class WatchHistoryStore {
    private static final String PREFS = "watching";
    private static final String KEY = "history";
    private static final int MAX_ITEMS = 100;

    private WatchHistoryStore() {
    }

    static synchronized JSONArray read(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray history = new JSONArray(preferences.getString(KEY, "[]"));
            if (history.length() > 0) return history;
        } catch (Exception ignored) {
        }

        String legacyName = preferences.getString("name", "");
        if (legacyName == null || legacyName.isBlank()) return new JSONArray();
        JSONArray migrated = new JSONArray();
        try {
            JSONObject item = new JSONObject();
            item.put("name", legacyName);
            item.put("cover", preferences.getString("cover", ""));
            item.put("episode", preferences.getInt("episode", 1));
            item.put("videoUrl", preferences.getString("videoUrl", ""));
            item.put("bangumiId", preferences.getInt("bangumiId", 0));
            item.put("position", 0L);
            item.put("duration", 0L);
            item.put("updatedAt", System.currentTimeMillis());
            migrated.put(item);
            preferences.edit().putString(KEY, migrated.toString()).apply();
        } catch (Exception ignored) {
        }
        return migrated;
    }

    static synchronized void record(
            Context context,
            String name,
            String cover,
            int episode,
            String videoUrl,
            int bangumiId,
            long position,
            long duration
    ) {
        if (name == null || name.isBlank() || "正在播放".equals(name)) return;
        JSONArray current = read(context);
        JSONArray updated = new JSONArray();
        try {
            JSONObject latest = new JSONObject();
            latest.put("name", name);
            latest.put("cover", cover == null ? "" : cover);
            latest.put("episode", Math.max(1, episode));
            latest.put("videoUrl", videoUrl == null ? "" : videoUrl);
            latest.put("bangumiId", bangumiId);
            latest.put("position", Math.max(0L, position));
            latest.put("duration", Math.max(0L, duration));
            latest.put("updatedAt", System.currentTimeMillis());
            updated.put(latest);

            for (int index = 0; index < current.length() && updated.length() < MAX_ITEMS; index++) {
                JSONObject item = current.optJSONObject(index);
                if (item == null) continue;
                boolean sameId = bangumiId > 0 && bangumiId == item.optInt("bangumiId", 0);
                boolean sameName = name.equals(item.optString("name"));
                if (!sameId && !sameName) updated.put(item);
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY, updated.toString())
                    .putString("name", name)
                    .putString("cover", cover == null ? "" : cover)
                    .putInt("episode", Math.max(1, episode))
                    .putString("videoUrl", videoUrl == null ? "" : videoUrl)
                    .putInt("bangumiId", bangumiId)
                    .apply();
        } catch (Exception ignored) {
        }
    }

    static synchronized void remove(Context context, JSONObject target) {
        JSONArray current = read(context);
        JSONArray updated = new JSONArray();
        int targetId = target.optInt("bangumiId", 0);
        String targetName = target.optString("name");
        for (int index = 0; index < current.length(); index++) {
            JSONObject item = current.optJSONObject(index);
            if (item == null) continue;
            boolean sameId = targetId > 0 && targetId == item.optInt("bangumiId", 0);
            boolean sameName = targetName.equals(item.optString("name"));
            if (!sameId && !sameName) updated.put(item);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, updated.toString()).apply();
    }

    static synchronized void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, "[]").apply();
    }
}
