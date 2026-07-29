package com.example.animeresolver;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Executes the safe, declarative subset of v2 video extraction rules. */
final class VideoExtractorEngine {
    interface PageLoader {
        Page load(String url, Map<String, String> headers) throws Exception;
    }

    record Page(String url, String body) {
    }

    record Result(String url, Map<String, String> playbackHeaders) {
    }

    private VideoExtractorEngine() {
    }

    static Result extract(String episodeUrl, Page initialPage, VideoRule rule,
            PageLoader loader) throws Exception {
        VideoRule safeRule = rule == null ? VideoRule.defaults() : rule;
        Context context = new Context(episodeUrl, episodeUrl, "");
        String mediaUrl = extractPage(initialPage, safeRule, loader, context, 0, new HashSet<>());
        if (mediaUrl == null || mediaUrl.isBlank()) return null;
        Context playbackContext = new Context(episodeUrl, initialPage.url(), mediaUrl);
        return new Result(mediaUrl, headers(safeRule.playbackHeaders(), playbackContext));
    }

    static Map<String, String> requestHeaders(VideoRule rule, String episodeUrl) {
        return headers((rule == null ? VideoRule.defaults() : rule).requestHeaders(),
                new Context(episodeUrl, episodeUrl, ""));
    }

    private static String extractPage(Page page, VideoRule rule, PageLoader loader,
            Context context, int depth, Set<String> visited) throws Exception {
        if (page == null || page.url() == null || !visited.add(page.url())) return null;
        JSONArray extractors = rule.extractors();
        if (extractors == null) return null;
        Document document = null;
        for (int index = 0; index < extractors.length(); index++) {
            JSONObject extractor = extractors.optJSONObject(index);
            if (extractor == null) continue;
            String type = extractor.optString("type");
            String candidate = null;
            if ("player-variable".equals(type)) {
                candidate = playerVariable(page.body(), extractor);
            } else if ("css-attribute".equals(type)) {
                if (document == null) document = Jsoup.parse(page.body(), page.url());
                candidate = selectedUrl(document, extractor, page.url());
            } else if ("media-regex".equals(type)) {
                candidate = regex(page.body(), extractor.optString("pattern"));
            } else if ("json-api".equals(type)) {
                if (document == null) document = Jsoup.parse(page.body(), page.url());
                candidate = jsonApi(document, page, extractor, rule, loader, context);
            } else if ("iframe".equals(type)) {
                int maxDepth = Math.max(1, extractor.optInt("maxDepth", 2));
                if (depth >= maxDepth) continue;
                if (document == null) document = Jsoup.parse(page.body(), page.url());
                String nestedUrl = selectedUrl(document, extractor, page.url());
                if (nestedUrl == null || nestedUrl.isBlank()) continue;
                Map<String, String> nestedHeaders = merge(
                        headers(rule.requestHeaders(), context.withPage(page.url())),
                        headers(extractor.optJSONObject("headers"), context.withPage(page.url())));
                Page nested = loader.load(nestedUrl, nestedHeaders);
                candidate = extractPage(nested, rule, loader,
                        context.withPage(nested.url()), depth + 1, visited);
            }
            candidate = transforms(candidate, extractor.optJSONArray("transforms"));
            if (candidate != null && !candidate.isBlank()) {
                return absolute(page.url(), candidate.trim());
            }
        }
        return null;
    }

    private static String playerVariable(String html, JSONObject extractor) {
        String variable = extractor.optString("variable", "player_aaaa");
        String urlField = extractor.optString("urlField", "url");
        String encryptField = extractor.optString("encryptField", "encrypt");
        Pattern pattern = Pattern.compile("(?s)(?:var|let|const)?\\s*"
                + Pattern.quote(variable) + "\\s*=\\s*(\\{.*?})\\s*;?");
        Matcher matcher = pattern.matcher(html == null ? "" : html);
        if (!matcher.find()) return null;
        try {
            JSONObject player = new JSONObject(matcher.group(1));
            String value = player.optString(urlField);
            int encrypt = player.optInt(encryptField, 0);
            if (encrypt == 1) return URLDecoder.decode(value, StandardCharsets.UTF_8);
            if (encrypt == 2) {
                String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
                return URLDecoder.decode(decoded, StandardCharsets.UTF_8);
            }
            return value;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String selectedUrl(Document document, JSONObject extractor, String baseUrl) {
        String selector = extractor.optString("selector");
        String attribute = extractor.optString("attribute", "src");
        if (selector.isBlank()) return null;
        for (Element element : document.select(selector)) {
            String value = element.hasAttr(attribute) ? element.attr(attribute) : "";
            if (!value.isBlank()) return absolute(baseUrl, value);
        }
        return null;
    }

    private static String regex(String body, String expression) {
        if (expression == null || expression.isBlank()) return null;
        try {
            Matcher matcher = Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                    .matcher(body == null ? "" : body);
            if (!matcher.find()) return null;
            try {
                String named = matcher.group("v");
                if (named != null && !named.isBlank()) return named;
            } catch (IllegalArgumentException ignored) {
                // The optional named group is not present.
            }
            return matcher.group();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String jsonApi(Document document, Page page, JSONObject extractor,
            VideoRule rule, PageLoader loader, Context context) throws Exception {
        String endpoint = resolve(extractor.optString("url"), context.withPage(page.url()));
        if (endpoint.isBlank()) endpoint = selectedUrl(document, extractor, page.url());
        if (endpoint == null || endpoint.isBlank()) return null;
        Map<String, String> requestHeaders = merge(
                headers(rule.requestHeaders(), context.withPage(page.url())),
                headers(extractor.optJSONObject("headers"), context.withPage(page.url())));
        Page response = loader.load(absolute(page.url(), endpoint), requestHeaders);
        Object value = new JSONTokener(response.body()).nextValue();
        Object selected = jsonPath(value, extractor.optString("path", "$.url"));
        return selected == null ? null : String.valueOf(selected);
    }

    private static Object jsonPath(Object root, String path) {
        if (root == null) return null;
        String normalized = path == null ? "" : path.trim();
        if (normalized.startsWith("$")) normalized = normalized.substring(1);
        if (normalized.startsWith(".")) normalized = normalized.substring(1);
        if (normalized.isBlank()) return root;
        Object current = root;
        for (String part : normalized.split("\\.")) {
            Matcher indexed = Pattern.compile("([^\\[]+)?(?:\\[(\\d+)])?").matcher(part);
            if (!indexed.matches()) return null;
            String key = indexed.group(1);
            String index = indexed.group(2);
            if (key != null && !key.isBlank()) {
                if (!(current instanceof JSONObject object)) return null;
                current = object.opt(key);
            }
            if (index != null) {
                if (!(current instanceof JSONArray array)) return null;
                current = array.opt(Integer.parseInt(index));
            }
        }
        return current == JSONObject.NULL ? null : current;
    }

    private static String transforms(String value, JSONArray transforms) {
        if (value == null || transforms == null) return value;
        String result = value;
        for (int index = 0; index < transforms.length(); index++) {
            String transform = transforms.optString(index);
            try {
                if ("base64-decode".equals(transform)) {
                    result = new String(Base64.getDecoder().decode(result), StandardCharsets.UTF_8);
                } else if ("url-decode".equals(transform)) {
                    result = URLDecoder.decode(result, StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
                return null;
            }
        }
        return result;
    }

    private static Map<String, String> headers(JSONObject json, Context context) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (json == null) return result;
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = resolve(json.optString(key), context);
            if (!key.isBlank() && !value.isBlank()) result.put(key, value);
        }
        return result;
    }

    private static Map<String, String> merge(Map<String, String> first, Map<String, String> second) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>(first);
        result.putAll(second);
        return result;
    }

    private static String resolve(String value, Context context) {
        if (value == null) return "";
        return value.replace("{episodeUrl}", context.episodeUrl)
                .replace("{pageUrl}", context.pageUrl)
                .replace("{mediaUrl}", context.mediaUrl)
                .replace("{referer}", context.pageUrl)
                .replace("{origin}", origin(context.pageUrl));
    }

    private static String absolute(String baseUrl, String value) {
        try {
            return URI.create(baseUrl).resolve(value).toString();
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String origin(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getScheme() == null || uri.getAuthority() == null) return "";
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (Exception ignored) {
            return "";
        }
    }

    private record Context(String episodeUrl, String pageUrl, String mediaUrl) {
        Context withPage(String value) {
            return new Context(episodeUrl, value, mediaUrl);
        }
    }
}
