package com.example.animeresolver;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

final class RuleRepositoryStore {
    private static final String PREFS = "rule_repository";
    private static final String VERSION_PREFIX = "version_";

    private RuleRepositoryStore() {}

    static String installedVersion(Context context, String ruleId) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(VERSION_PREFIX + ruleId, "");
    }

    static void markInstalled(Context context, String ruleId, String version) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(VERSION_PREFIX + ruleId, version == null ? "" : version)
                .apply();
    }

    static void remove(Context context, String ruleId) {
        LocalSourceStore.remove(context, ruleId);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(VERSION_PREFIX + ruleId).apply();
    }

    static boolean isNewer(String remote, String local) {
        if (local == null || local.isBlank()) return false;
        String[] left = remote == null ? new String[0] : remote.split("\\.");
        String[] right = local.split("\\.");
        int size = Math.max(left.length, right.length);
        for (int i = 0; i < size; i++) {
            int a = component(left, i);
            int b = component(right, i);
            if (a != b) return a > b;
        }
        return false;
    }

    private static int component(String[] values, int index) {
        if (index >= values.length) return 0;
        try {
            String numeric = values[index].replaceAll("[^0-9].*$", "");
            return numeric.isEmpty() ? 0 : Integer.parseInt(numeric);
        } catch (Exception ignored) {
            return 0;
        }
    }

    static String encodeRule(LocalSourceStore.Config config) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "fanyu-source-rule");
        root.put("schemaVersion", 1);
        root.put("config", config.toJson());
        String encoded = Base64.encodeToString(
                root.toString().getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        return "fanyu://rule/" + encoded;
    }

    static JSONObject decodeRule(String link) throws Exception {
        String value = link == null ? "" : link.trim();
        String prefix = "fanyu://rule/";
        if (!value.regionMatches(true, 0, prefix, 0, prefix.length())) {
            throw new IllegalArgumentException("不是番遇规则链接");
        }
        String payload = value.substring(prefix.length()).replaceAll("\\s", "");
        byte[] bytes = Base64.decode(payload, Base64.URL_SAFE | Base64.NO_WRAP);
        JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        if (!"fanyu-source-rule".equals(root.optString("format"))) {
            throw new IllegalArgumentException("规则链接格式不正确");
        }
        if (root.optInt("schemaVersion", 0) > 1) {
            throw new IllegalArgumentException("规则需要更高版本的番遇");
        }
        JSONObject config = root.optJSONObject("config");
        if (config == null) throw new IllegalArgumentException("规则缺少配置");
        return config;
    }
}
