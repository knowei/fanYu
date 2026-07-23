package com.example.animeresolver;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

final class DiscussionStore {
    private static final String PREFS = "watching";
    private static final String KEY = "local_discussions";
    private static final int MAX_ITEMS = 120;

    private DiscussionStore() {}

    static synchronized JSONArray read(Context context, int subjectId, String subjectName, int episode) {
        JSONArray result = new JSONArray();
        JSONArray all = all(context);
        String subjectKey = subjectKey(subjectId, subjectName);
        for (int i = 0; i < all.length(); i++) {
            JSONObject item = all.optJSONObject(i);
            if (item != null && subjectKey.equals(item.optString("subjectKey"))
                    && episode == item.optInt("episode")) result.put(item);
        }
        return result;
    }

    static synchronized void add(Context context, int subjectId, String subjectName, int episode, String content) {
        String text = content == null ? "" : content.trim();
        if (text.isBlank()) return;
        JSONArray old = all(context);
        JSONArray updated = new JSONArray();
        try {
            JSONObject item = new JSONObject();
            item.put("id", System.currentTimeMillis());
            item.put("subjectKey", subjectKey(subjectId, subjectName));
            item.put("episode", Math.max(1, episode));
            item.put("content", text);
            item.put("createdAt", System.currentTimeMillis());
            updated.put(item);
            for (int i = 0; i < old.length() && updated.length() < MAX_ITEMS; i++) {
                JSONObject previous = old.optJSONObject(i);
                if (previous != null) updated.put(previous);
            }
            save(context, updated);
        } catch (Exception ignored) {
        }
    }

    static synchronized void remove(Context context, long id) {
        JSONArray updated = new JSONArray();
        JSONArray old = all(context);
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item != null && item.optLong("id") != id) updated.put(item);
        }
        save(context, updated);
    }

    private static JSONArray all(Context context) {
        try {
            return new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static void save(Context context, JSONArray items) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, items.toString()).apply();
    }

    private static String subjectKey(int subjectId, String subjectName) {
        return subjectId > 0 ? "id:" + subjectId : "name:" + (subjectName == null ? "" : subjectName.trim());
    }
}
