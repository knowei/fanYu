package com.example.animeresolver;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

final class FavoriteStore {
    private static final String PREFS = "watching";
    private static final String KEY = "favorites";

    private FavoriteStore() {}

    static JSONArray read(Context context) {
        try {
            return new JSONArray(preferences(context).getString(KEY, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    static boolean contains(Context context, int subjectId, String name) {
        JSONArray favorites = read(context);
        for (int index = 0; index < favorites.length(); index++) {
            JSONObject item = favorites.optJSONObject(index);
            if (matches(item, subjectId, name)) return true;
        }
        return false;
    }

    static boolean toggle(Context context, int subjectId, String name, String cover) {
        JSONArray favorites = read(context);
        JSONArray updated = new JSONArray();
        boolean removed = false;
        for (int index = 0; index < favorites.length(); index++) {
            JSONObject item = favorites.optJSONObject(index);
            if (matches(item, subjectId, name)) {
                removed = true;
            } else if (item != null) {
                updated.put(item);
            }
        }
        if (!removed) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", subjectId);
                item.put("name", name == null ? "" : name);
                item.put("cover", cover == null ? "" : cover);
                updated.put(item);
            } catch (Exception ignored) {
            }
        }
        preferences(context).edit().putString(KEY, updated.toString()).apply();
        return !removed;
    }

    private static boolean matches(JSONObject item, int subjectId, String name) {
        if (item == null) return false;
        int savedId = item.optInt("id", 0);
        if (subjectId > 0 && savedId > 0) return subjectId == savedId;
        String savedName = item.optString("name").trim();
        return name != null && !name.isBlank() && savedName.equalsIgnoreCase(name.trim());
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
