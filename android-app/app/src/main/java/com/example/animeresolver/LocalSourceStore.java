package com.example.animeresolver;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class LocalSourceStore {
    private static final String PREFS = "video_sources";
    private static final String KEY = "local_sources_v1";
    private static final String SEEDED = "local_sources_seeded";

    private LocalSourceStore() {}

    static synchronized List<Config> read(Context context) {
        seedIfNeeded(context);
        List<Config> result = new ArrayList<>();
        try {
            JSONArray items = new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY, "[]"));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) result.add(Config.fromJson(item));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    static synchronized Config find(Context context, String id) {
        for (Config config : read(context)) if (config.id.equals(id)) return config;
        return null;
    }

    static synchronized void save(Context context, Config config) {
        List<Config> items = read(context);
        JSONArray updated = new JSONArray();
        boolean replaced = false;
        for (Config item : items) {
            if (item.id.equals(config.id)) {
                updated.put(config.toJson());
                replaced = true;
            } else updated.put(item.toJson());
        }
        if (!replaced) updated.put(config.toJson());
        persist(context, updated);
    }

    static synchronized void remove(Context context, String id) {
        JSONArray updated = new JSONArray();
        for (Config item : read(context)) if (!item.id.equals(id)) updated.put(item.toJson());
        persist(context, updated);
    }

    private static void seedIfNeeded(Context context) {
        android.content.SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (preferences.getBoolean(SEEDED, false)) return;
        JSONArray items = new JSONArray();
        items.put(new Config("orange-anime", "橘子动漫",
                "https://www.mgnacg.com/search/-------------/?wd={keyword}",
                ".search-box .thumb-menu > a", ".anthology-list-box", "a",
                ".anthology-tab > .swiper-wrapper a", 8, true, false).toJson());
        preferences.edit().putString(KEY, items.toString()).putBoolean(SEEDED, true).apply();
    }

    private static void persist(Context context, JSONArray items) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, items.toString()).putBoolean(SEEDED, true).apply();
    }

    static final class Config {
        final String id;
        final String name;
        final String searchUrl;
        final String subjectSelector;
        final String episodeContainer;
        final String episodeSelector;
        final String channelSelector;
        final int tier;
        final boolean enabled;
        final boolean autoDetected;

        Config(String id, String name, String searchUrl, String subjectSelector,
               String episodeContainer, String episodeSelector, String channelSelector,
               int tier, boolean enabled, boolean autoDetected) {
            this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
            this.name = name == null ? "" : name.trim();
            this.searchUrl = searchUrl == null ? "" : searchUrl.trim();
            this.subjectSelector = subjectSelector == null ? "" : subjectSelector.trim();
            this.episodeContainer = episodeContainer == null ? "" : episodeContainer.trim();
            this.episodeSelector = episodeSelector == null || episodeSelector.isBlank() ? "a" : episodeSelector.trim();
            this.channelSelector = channelSelector == null ? "" : channelSelector.trim();
            this.tier = tier;
            this.enabled = enabled;
            this.autoDetected = autoDetected;
        }

        JSONObject toJson() {
            JSONObject item = new JSONObject();
            try {
                item.put("id", id);
                item.put("name", name);
                item.put("searchUrl", searchUrl);
                item.put("subjectSelector", subjectSelector);
                item.put("episodeContainer", episodeContainer);
                item.put("episodeSelector", episodeSelector);
                item.put("channelSelector", channelSelector);
                item.put("tier", tier);
                item.put("enabled", enabled);
                item.put("autoDetected", autoDetected);
            } catch (Exception ignored) {
            }
            return item;
        }

        static Config fromJson(JSONObject item) {
            return new Config(item.optString("id"), item.optString("name"),
                    item.optString("searchUrl"), item.optString("subjectSelector"),
                    item.optString("episodeContainer"), item.optString("episodeSelector", "a"),
                    item.optString("channelSelector"), item.optInt("tier", 20),
                    item.optBoolean("enabled", true), item.optBoolean("autoDetected", false));
        }
    }
}
