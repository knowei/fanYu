package com.example.animeresolver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Declarative video extraction configuration used by v2 local rules. */
final class VideoRule {
    static final int SCHEMA_VERSION = 2;

    private final JSONObject json;

    private VideoRule(JSONObject json) {
        this.json = copy(json);
    }

    static VideoRule fromJson(JSONObject value) {
        if (value == null || value.length() == 0) return defaults();
        JSONObject normalized = copy(value);
        try {
            if (normalized.optJSONArray("extractors") == null) {
                normalized.put("extractors", defaultExtractors());
            }
            if (normalized.optJSONObject("requestHeaders") == null) {
                normalized.put("requestHeaders", new JSONObject());
            }
            if (normalized.optJSONObject("playbackHeaders") == null) {
                normalized.put("playbackHeaders", new JSONObject());
            }
        } catch (JSONException error) {
            throw invalid(error);
        }
        return new VideoRule(normalized);
    }

    static VideoRule defaults() {
        JSONObject value = new JSONObject();
        try {
            value.put("extractors", defaultExtractors());
            value.put("requestHeaders", new JSONObject());
            value.put("playbackHeaders", new JSONObject());
        } catch (JSONException error) {
            throw invalid(error);
        }
        return new VideoRule(value);
    }

    static VideoRule fromCss1(JSONObject matchVideo) {
        VideoRule defaults = defaults();
        if (matchVideo == null) return defaults;
        JSONObject value = defaults.toJson();
        try {
            JSONArray extractors = value.getJSONArray("extractors");
            String configuredPattern = matchVideo.optString("matchVideoUrl");
            if (!configuredPattern.isBlank()) {
                JSONObject regex = new JSONObject();
                regex.put("type", "media-regex");
                regex.put("pattern", configuredPattern);
                extractors.put(0, regex);
            }
            if (matchVideo.optBoolean("enableNestedUrl", false)) {
                JSONObject iframe = new JSONObject();
                iframe.put("type", "iframe");
                iframe.put("selector", "iframe[src]");
                iframe.put("attribute", "src");
                iframe.put("maxDepth", 2);
                extractors.put(iframe);
            }
            JSONObject playbackHeaders = matchVideo.optJSONObject("addHeadersToVideo");
            if (playbackHeaders != null) value.put("playbackHeaders", copy(playbackHeaders));
            String cookies = matchVideo.optString("cookies");
            if (!cookies.isBlank()) value.getJSONObject("requestHeaders").put("Cookie", cookies);
        } catch (JSONException error) {
            throw invalid(error);
        }
        return new VideoRule(value);
    }

    JSONObject toJson() {
        return copy(json);
    }

    JSONArray extractors() {
        return json.optJSONArray("extractors");
    }

    JSONObject requestHeaders() {
        return json.optJSONObject("requestHeaders");
    }

    JSONObject playbackHeaders() {
        return json.optJSONObject("playbackHeaders");
    }

    private static JSONArray defaultExtractors() {
        JSONArray extractors = new JSONArray();
        try {
            JSONObject player = new JSONObject();
            player.put("type", "player-variable");
            player.put("variable", "player_aaaa");
            player.put("urlField", "url");
            player.put("encryptField", "encrypt");
            extractors.put(player);

            JSONObject mediaElement = new JSONObject();
            mediaElement.put("type", "css-attribute");
            mediaElement.put("selector", "video[src], video source[src], source[src]");
            mediaElement.put("attribute", "src");
            extractors.put(mediaElement);

            JSONObject iframe = new JSONObject();
            iframe.put("type", "iframe");
            iframe.put("selector", "iframe[src]");
            iframe.put("attribute", "src");
            iframe.put("maxDepth", 2);
            extractors.put(iframe);

            JSONObject regex = new JSONObject();
            regex.put("type", "media-regex");
            regex.put("pattern", "https?://[^\\s\\\"']+\\.(?:m3u8|mp4|flv|mkv)(?:\\?[^\\s\\\"']*)?");
            extractors.put(regex);
        } catch (JSONException error) {
            throw invalid(error);
        }
        return extractors;
    }

    private static JSONObject copy(JSONObject value) {
        if (value == null) return new JSONObject();
        try {
            return new JSONObject(value.toString());
        } catch (JSONException error) {
            throw invalid(error);
        }
    }

    private static IllegalArgumentException invalid(JSONException error) {
        return new IllegalArgumentException("视频规则 JSON 无效", error);
    }
}
