package com.example.animeresolver;

import android.content.Context;

import org.json.JSONObject;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SourceMetricsStore {
    private static final String PREFS = "source_metrics";
    private static final String KEY = "metrics_v1";
    private static final Pattern RESOLUTION = Pattern.compile(
            "(?i)(2160|1440|1080|720|480|360)(?:p|[^0-9])");

    private SourceMetricsStore() {}

    static synchronized void recordSuccess(Context context, String name,
                                           long latencyMs, String mediaUrl) {
        JSONObject root = read(context);
        String key = baseName(name);
        JSONObject item = root.optJSONObject(key);
        if (item == null) item = new JSONObject();
        try {
            item.put("success", item.optInt("success") + 1);
            item.put("latencyTotal", item.optLong("latencyTotal") + clampLatency(latencyMs));
            item.put("bestHeight", Math.max(item.optInt("bestHeight"), inferHeight(mediaUrl)));
            item.put("lastSuccess", System.currentTimeMillis());
            root.put(key, item);
            save(context, root);
        } catch (Exception ignored) {
        }
    }

    static synchronized void recordFailure(Context context, String name, long latencyMs) {
        JSONObject root = read(context);
        String key = baseName(name);
        JSONObject item = root.optJSONObject(key);
        if (item == null) item = new JSONObject();
        try {
            item.put("failure", item.optInt("failure") + 1);
            item.put("failureLatencyTotal", item.optLong("failureLatencyTotal") + clampLatency(latencyMs));
            root.put(key, item);
            save(context, root);
        } catch (Exception ignored) {
        }
    }

    static synchronized double score(Context context, String name, int tier) {
        JSONObject item = read(context).optJSONObject(baseName(name));
        if (item == null) return 0.42 - Math.min(0.2, Math.max(0, tier) * 0.002);
        int success = item.optInt("success");
        int failure = item.optInt("failure");
        int samples = success + failure;
        if (samples == 0) return 0.42 - Math.min(0.2, Math.max(0, tier) * 0.002);
        double successRate = (success + 1.0) / (samples + 2.0);
        double averageLatency = success == 0 ? 45_000.0
                : item.optLong("latencyTotal") / (double) success;
        double latencyScore = Math.max(0.0, 1.0 - averageLatency / 45_000.0);
        double qualityScore = Math.min(1.0, item.optInt("bestHeight") / 1080.0);
        double confidence = Math.min(1.0, samples / 6.0);
        double measured = successRate * 0.62 + latencyScore * 0.25 + qualityScore * 0.13;
        double fallback = 0.42 - Math.min(0.2, Math.max(0, tier) * 0.002);
        return measured * confidence + fallback * (1.0 - confidence);
    }

    static int compare(Context context, String leftName, int leftTier,
                       String rightName, int rightTier) {
        int byScore = Double.compare(score(context, rightName, rightTier),
                score(context, leftName, leftTier));
        return byScore != 0 ? byScore : Integer.compare(leftTier, rightTier);
    }

    static synchronized int displayPriority(Context context, String name) {
        return (int) Math.round((1.0 - score(context, name, 50)) * 1000);
    }

    static synchronized String recommended(Context context, Collection<String> names) {
        String best = "";
        double bestScore = Double.NEGATIVE_INFINITY;
        for (String name : names) {
            JSONObject item = read(context).optJSONObject(baseName(name));
            if (item == null || item.optInt("success") + item.optInt("failure") < 2) continue;
            double value = score(context, name, 50);
            if (value > bestScore) {
                bestScore = value;
                best = baseName(name);
            }
        }
        return best;
    }

    static String baseName(String name) {
        if (name == null) return "";
        String value = name.trim();
        int separator = value.indexOf(" · ");
        if (separator < 0) separator = value.indexOf(" | ");
        return (separator < 0 ? value : value.substring(0, separator)).trim();
    }

    static int inferHeight(String value) {
        if (value == null) return 0;
        Matcher matcher = RESOLUTION.matcher(value + " ");
        int best = 0;
        while (matcher.find()) {
            try { best = Math.max(best, Integer.parseInt(matcher.group(1))); }
            catch (Exception ignored) {}
        }
        return best;
    }

    static synchronized void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static long clampLatency(long value) {
        return Math.max(0L, Math.min(120_000L, value));
    }

    private static JSONObject read(Context context) {
        try {
            return new JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY, "{}"));
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static void save(Context context, JSONObject root) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, root.toString()).apply();
    }
}
