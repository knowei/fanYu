package com.example.animeresolver;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends Activity {
    private static final String BASE_URL = "https://enlienli.link";
    private static final String SUBSCRIPTION_URL =
            "https://sub.creamycake.org/v1/css1.json";
    private static final String SEARCH_RESULT_SELECTOR =
            ".module-card-item>.module-card-item-info>.module-card-item-title>a";
    private static final String EPISODE_LIST_SELECTOR = ".module-play-list-content";
    private static final Pattern MEDIA_URL_PATTERN =
            Pattern.compile("https?://[^\\s\"']+\\.(?:m3u8|mp4|flv|mkv)(?:\\?[^\\s\"']*)?",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_DATA_PATTERN =
            Pattern.compile("player_aaaa\\s*=\\s*(\\{.*?\\})\\s*;?\\s*</script>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";

    private enum Stage {
        IDLE,
        SEARCH,
        DETAIL,
        PLAY,
        DONE
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newCachedThreadPool();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build();
    private EditText nameInput;
    private EditText episodeInput;
    private Button resolveButton;
    private Button copyButton;
    private TextView statusView;
    private TextView resultView;
    private FrameLayout root;
    private FrameLayout verificationPanel;
    private WebView webView;

    private Stage stage = Stage.IDLE;
    private String requestedName;
    private int requestedEpisode;
    private String detailUrl;
    private String episodeUrl;
    private String resolvedSource = "omofun111";
    private int probeGeneration;
    private int navigationGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        configureWebView();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(31, 41, 55));
        return view;
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(36), dp(24), dp(24));

        TextView title = text("番剧资源解析测试", 26);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title);

        TextView description = text("输入番剧名称和集数，解析第一条可用线路的视频地址。", 15);
        description.setPadding(0, dp(8), 0, dp(24));
        content.addView(description);

        nameInput = new EditText(this);
        nameInput.setHint("番剧名称");
        nameInput.setSingleLine(true);
        nameInput.setText("关于我转生变成史莱姆这档事第四季");
        content.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        episodeInput = new EditText(this);
        episodeInput.setHint("集数");
        episodeInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        episodeInput.setText("1");
        LinearLayout.LayoutParams episodeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        episodeParams.topMargin = dp(12);
        content.addView(episodeInput, episodeParams);

        resolveButton = new Button(this);
        resolveButton.setText("解析视频地址");
        resolveButton.setOnClickListener(view -> startResolve());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        buttonParams.topMargin = dp(20);
        content.addView(resolveButton, buttonParams);

        statusView = text("等待开始", 14);
        statusView.setPadding(0, dp(20), 0, dp(10));
        content.addView(statusView);

        resultView = text("", 14);
        resultView.setTextIsSelectable(true);
        resultView.setBackgroundColor(Color.rgb(243, 244, 246));
        resultView.setPadding(dp(14), dp(14), dp(14), dp(14));
        content.addView(resultView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        copyButton = new Button(this);
        copyButton.setText("复制视频地址");
        copyButton.setVisibility(View.GONE);
        copyButton.setOnClickListener(view -> copyResult());
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        copyParams.topMargin = dp(12);
        content.addView(copyButton, copyParams);

        scrollView.addView(content);
        root.addView(scrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        webView = new WebView(this);
        webView.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams hiddenWebViewParams = new FrameLayout.LayoutParams(dp(1), dp(1));
        hiddenWebViewParams.gravity = Gravity.BOTTOM | Gravity.END;
        root.addView(webView, hiddenWebViewParams);

        verificationPanel = new FrameLayout(this);
        verificationPanel.setBackgroundColor(Color.WHITE);
        verificationPanel.setVisibility(View.GONE);
        verificationPanel.addView(text("正在进行网站安全验证…", 16),
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        root.addView(verificationPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setUserAgentString(BROWSER_USER_AGENT);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
                scheduleProbe(500);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request
            ) {
                inspectResourceUrl(request.getUrl().toString());
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                inspectResourceUrl(url);
                super.onLoadResource(view, url);
            }
        });
    }

    private void startResolve() {
        requestedName = nameInput.getText().toString().trim();
        String episodeText = episodeInput.getText().toString().trim();
        if (requestedName.isEmpty() || episodeText.isEmpty()) {
            Toast.makeText(this, "请输入番剧名称和集数", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            requestedEpisode = Integer.parseInt(episodeText);
            if (requestedEpisode < 1) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            Toast.makeText(this, "集数必须是正整数", Toast.LENGTH_SHORT).show();
            return;
        }

        probeGeneration++;
        detailUrl = null;
        episodeUrl = null;
        resolvedSource = "omofun111";
        stage = Stage.SEARCH;
        resolveButton.setEnabled(false);
        copyButton.setVisibility(View.GONE);
        resultView.setText("");
        hideVerification();
        setStatus("正在搜索番剧…");

        String searchUrl = BASE_URL + "/vod/search.html?wd=" +
                Uri.encode(requestedName);
        resolveFromSubscription(searchUrl, probeGeneration);
    }

    private void resolveFromSubscription(String fallbackSearchUrl, int generation) {
        networkExecutor.execute(() -> {
            try {
                HttpPage subscription = getPage(SUBSCRIPTION_URL);
                List<SourceConfig> sources = parseSourceConfigs(subscription.html);
                if (sources.isEmpty()) throw new IOException("订阅中没有兼容的数据源");

                List<Callable<ResolveResult>> tasks = new ArrayList<>();
                for (SourceConfig source : sources) {
                    tasks.add(() -> resolveSource(source, generation));
                }

                ResolveResult winner = networkExecutor.invokeAny(tasks, 45, TimeUnit.SECONDS);
                runOnUiThread(() -> {
                    if (generation != probeGeneration) return;
                    resolvedSource = winner.source;
                    detailUrl = winner.detailUrl;
                    episodeUrl = winner.episodeUrl;
                    stage = Stage.PLAY;
                    deliverVideo(winner.videoUrl);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (generation != probeGeneration) return;
                    setStatus("多源直连未命中，正在使用兼容模式…");
                    resolveWithHttp(fallbackSearchUrl, generation);
                });
            }
        });
    }

    private List<SourceConfig> parseSourceConfigs(String json) throws JSONException {
        List<SourceConfig> result = new ArrayList<>();
        JSONObject rootObject = new JSONObject(json);
        org.json.JSONArray sources = rootObject
                .getJSONObject("exportedMediaSourceDataList")
                .getJSONArray("mediaSources");
        for (int index = 0; index < sources.length(); index++) {
            JSONObject item = sources.optJSONObject(index);
            if (item == null || !"web-selector".equals(item.optString("factoryId"))) continue;
            JSONObject arguments = item.optJSONObject("arguments");
            JSONObject search = arguments == null ? null :
                    arguments.optJSONObject("searchConfig");
            if (arguments == null || search == null) continue;

            String searchUrl = search.optString("searchUrl");
            String subjectFormat = search.optString("subjectFormatId", "a");
            String subjectSelector = "";
            String subjectLinkSelector = "";
            if ("a".equals(subjectFormat)) {
                JSONObject format = search.optJSONObject("selectorSubjectFormatA");
                if (format != null) subjectSelector = format.optString("selectLists");
            } else if ("indexed".equals(subjectFormat)) {
                JSONObject format = search.optJSONObject("selectorSubjectFormatIndexed");
                if (format != null) {
                    subjectSelector = format.optString("selectNames");
                    subjectLinkSelector = format.optString("selectLinks");
                }
            } else {
                continue;
            }

            String channelFormat = search.optString("channelFormatId", "index-grouped");
            String episodeContainer = "";
            String episodeSelector = "a";
            if ("no-channel".equals(channelFormat)) {
                JSONObject format = search.optJSONObject("selectorChannelFormatNoChannel");
                if (format != null) {
                    episodeSelector = format.optString("selectEpisodes");
                    episodeContainer = "";
                }
            } else {
                JSONObject format = search.optJSONObject("selectorChannelFormatFlattened");
                if (format != null) {
                    episodeContainer = format.optString("selectEpisodeLists");
                    episodeSelector = format.optString("selectEpisodesFromList", "a");
                }
            }
            if (searchUrl.isBlank() || subjectSelector.isBlank() ||
                    episodeSelector.isBlank()) continue;
            result.add(new SourceConfig(
                    arguments.optString("name", "unknown"),
                    searchUrl,
                    subjectFormat,
                    subjectSelector,
                    subjectLinkSelector,
                    episodeContainer,
                    episodeSelector,
                    arguments.optInt("tier", 99)
            ));
        }
        result.sort(Comparator.comparingInt(source -> source.tier));
        return result;
    }

    private ResolveResult resolveSource(SourceConfig source, int generation) throws Exception {
        if (generation != probeGeneration) throw new IOException("任务已取消");
        String searchUrl = source.searchUrl.replace(
                "{keyword}", Uri.encode(requestedName));
        HttpPage searchPage = getPage(searchUrl);
        if (isChallengePage(searchPage)) throw new IOException("需要验证");
        Document searchDocument = Jsoup.parse(searchPage.html, searchPage.url);

        String foundDetailUrl;
        if ("indexed".equals(source.subjectFormat)) {
            Elements names = searchDocument.select(source.subjectSelector);
            Elements links = searchDocument.select(source.subjectLinkSelector);
            if (links.isEmpty()) throw new IOException("搜索结果为空");
            int chosen = 0;
            for (int index = 0; index < Math.min(names.size(), links.size()); index++) {
                if (normalizeName(names.get(index).text())
                        .equals(normalizeName(requestedName))) {
                    chosen = index;
                    break;
                }
            }
            Element link = links.get(Math.min(chosen, links.size() - 1));
            foundDetailUrl = link.hasAttr("href") ? link.absUrl("href") :
                    link.selectFirst("a[href]") == null ? "" :
                            link.selectFirst("a[href]").absUrl("href");
        } else {
            Elements subjects = searchDocument.select(source.subjectSelector);
            if (subjects.isEmpty()) throw new IOException("搜索结果为空");
            Element selected = subjects.stream()
                    .filter(element -> normalizeName(element.text())
                            .equals(normalizeName(requestedName)))
                    .findFirst()
                    .orElse(subjects.first());
            Element link = selected.hasAttr("href") ? selected :
                    selected.selectFirst("a[href]");
            foundDetailUrl = link == null ? "" : link.absUrl("href");
        }
        if (foundDetailUrl.isBlank()) throw new IOException("详情链接为空");

        HttpPage detailPage = getPage(foundDetailUrl);
        if (isChallengePage(detailPage)) throw new IOException("需要验证");
        Document detailDocument = Jsoup.parse(detailPage.html, detailPage.url);
        Element episode = selectEpisode(
                detailDocument, source.episodeContainer, source.episodeSelector,
                requestedEpisode);
        if (episode == null) throw new IOException("剧集不存在");
        String foundEpisodeUrl = episode.absUrl("href");
        if (foundEpisodeUrl.isBlank()) throw new IOException("剧集链接为空");

        HttpPage playPage = getPage(foundEpisodeUrl);
        if (isChallengePage(playPage)) throw new IOException("需要验证");
        String mediaUrl = extractPlayerMediaUrl(playPage.html);
        if (mediaUrl == null || !isMediaUrl(mediaUrl)) {
            Matcher mediaMatcher = MEDIA_URL_PATTERN.matcher(playPage.html);
            mediaUrl = mediaMatcher.find() ? mediaMatcher.group() : null;
        }
        if (mediaUrl == null || !isMediaUrl(mediaUrl)) {
            throw new IOException("未发现直连媒体");
        }
        return new ResolveResult(
                source.name, foundDetailUrl, foundEpisodeUrl, mediaUrl);
    }

    private void resolveWithHttp(String searchUrl, int generation) {
        networkExecutor.execute(() -> {
            try {
                HttpPage searchPage = getPage(searchUrl);
                if (isChallengePage(searchPage)) {
                    fallbackToWebView(searchUrl, Stage.SEARCH, generation,
                            "HTTP 请求被网站拦截，正在后台建立访问会话…");
                    return;
                }

                Document searchDocument = Jsoup.parse(
                        searchPage.html, searchPage.url);
                Elements subjects = searchDocument.select(SEARCH_RESULT_SELECTOR);
                if (subjects.isEmpty()) {
                    throw new IOException("搜索结果为空");
                }
                Element selectedSubject = subjects.stream()
                        .filter(element -> normalizeName(element.text())
                                .equals(normalizeName(requestedName)))
                        .findFirst()
                        .orElse(subjects.first());
                detailUrl = selectedSubject.absUrl("href");
                if (detailUrl.isBlank()) {
                    throw new IOException("搜索结果没有详情链接");
                }

                runOnUiThread(() -> {
                    if (generation != probeGeneration) return;
                    stage = Stage.DETAIL;
                    setStatus("已找到作品，正在读取第 " + requestedEpisode + " 集…");
                });

                HttpPage detailPage = getPage(detailUrl);
                if (isChallengePage(detailPage)) {
                    fallbackToWebView(detailUrl, Stage.DETAIL, generation,
                            "详情请求被网站拦截，正在后台恢复访问会话…");
                    return;
                }

                Document detailDocument = Jsoup.parse(
                        detailPage.html, detailPage.url);
                Element selectedEpisode = selectEpisode(detailDocument, requestedEpisode);
                if (selectedEpisode == null) {
                    throw new IOException("没有找到第 " + requestedEpisode + " 集");
                }
                episodeUrl = selectedEpisode.absUrl("href");
                if (episodeUrl.isBlank()) {
                    throw new IOException("剧集链接为空");
                }

                runOnUiThread(() -> {
                    if (generation != probeGeneration) return;
                    stage = Stage.PLAY;
                    setStatus("已找到剧集，正在解析视频地址…");
                });

                HttpPage playPage = getPage(episodeUrl);
                if (isChallengePage(playPage)) {
                    fallbackToWebView(episodeUrl, Stage.PLAY, generation,
                            "播放请求被网站拦截，正在后台恢复访问会话…");
                    return;
                }

                String mediaUrl = extractPlayerMediaUrl(playPage.html);
                if (mediaUrl != null && isMediaUrl(mediaUrl)) {
                    runOnUiThread(() -> {
                        if (generation == probeGeneration) deliverVideo(mediaUrl);
                    });
                    return;
                }

                fallbackToWebView(episodeUrl, Stage.PLAY, generation,
                        "正在后台执行播放器并捕获视频地址…");
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (generation == probeGeneration) {
                        fail("解析失败：" + exception.getMessage());
                    }
                });
            }
        });
    }

    private HttpPage getPage(String url) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null && !cookies.isBlank()) {
            builder.header("Cookie", cookies);
        }
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            for (String cookie : response.headers("Set-Cookie")) {
                CookieManager.getInstance().setCookie(url, cookie);
            }
            CookieManager.getInstance().flush();
            if (response.body() == null) throw new IOException("响应内容为空");
            String html = response.body().string();
            if (!response.isSuccessful()) {
                String lower = html.toLowerCase(Locale.ROOT);
                boolean challenge = lower.contains("cf-chl-") ||
                        lower.contains("challenge-platform") ||
                        lower.contains("cdn-cgi/challenge") ||
                        lower.contains("turnstile") ||
                        lower.contains("just a moment") ||
                        lower.contains("安全验证");
                if (!challenge) throw new IOException("HTTP " + response.code());
            }
            return new HttpPage(response.request().url().toString(), html);
        }
    }

    private Element selectEpisode(Document document, int wanted) {
        return selectEpisode(document, EPISODE_LIST_SELECTOR, "a", wanted);
    }

    private Element selectEpisode(
            Document document,
            String containerSelector,
            String episodeSelector,
            int wanted
    ) {
        List<Elements> groups = new ArrayList<>();
        if (containerSelector == null || containerSelector.isBlank()) {
            groups.add(document.select(episodeSelector));
        } else {
            for (Element container : document.select(containerSelector)) {
                groups.add(container.select(episodeSelector));
            }
        }
        for (Elements links : groups) {
            for (Element link : links) {
                Matcher nameMatcher = Pattern.compile(
                        "第\\s*(\\d+)\\s*[话集]").matcher(link.text());
                if (nameMatcher.find() &&
                        Integer.parseInt(nameMatcher.group(1)) == wanted) {
                    return link;
                }
                Matcher urlMatcher = Pattern.compile(
                        "/nid/(\\d+)(?:\\.html|/|$)").matcher(link.absUrl("href"));
                if (urlMatcher.find() &&
                        Integer.parseInt(urlMatcher.group(1)) == wanted) {
                    return link;
                }
            }
            if (wanted <= links.size()) return links.get(wanted - 1);
        }
        return null;
    }

    private String extractPlayerMediaUrl(String html) {
        Matcher matcher = PLAYER_DATA_PATTERN.matcher(html);
        if (!matcher.find()) return null;
        try {
            JSONObject player = new JSONObject(matcher.group(1));
            String url = player.optString("url");
            int encrypt = player.optInt("encrypt", 0);
            if (encrypt == 1) {
                url = URLDecoder.decode(url, StandardCharsets.UTF_8);
            } else if (encrypt == 2) {
                url = new String(android.util.Base64.decode(url, android.util.Base64.DEFAULT),
                        StandardCharsets.UTF_8);
                url = URLDecoder.decode(url, StandardCharsets.UTF_8);
            }
            return url;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isChallengePage(HttpPage page) {
        String lower = page.html.toLowerCase(Locale.ROOT);
        return lower.contains("cf-chl-") ||
                lower.contains("challenge-platform") ||
                lower.contains("cdn-cgi/challenge") ||
                lower.contains("turnstile") ||
                lower.contains("just a moment") ||
                lower.contains("安全验证");
    }

    private void fallbackToWebView(
            String url,
            Stage fallbackStage,
            int generation,
            String status
    ) {
        runOnUiThread(() -> {
            if (generation != probeGeneration) return;
            stage = fallbackStage;
            setStatus(status);
            hideVerification();
            loadStageUrl(url);
        });
    }

    private String normalizeName(String value) {
        return value == null ? "" :
                value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private record HttpPage(String url, String html) {
    }

    private record SourceConfig(
            String name,
            String searchUrl,
            String subjectFormat,
            String subjectSelector,
            String subjectLinkSelector,
            String episodeContainer,
            String episodeSelector,
            int tier
    ) {
    }

    private record ResolveResult(
            String source,
            String detailUrl,
            String episodeUrl,
            String videoUrl
    ) {
    }

    private void scheduleProbe(long delayMillis) {
        int generation = probeGeneration;
        handler.postDelayed(() -> {
            if (generation != probeGeneration || stage == Stage.IDLE || stage == Stage.DONE) {
                return;
            }
            probeCurrentStage();
        }, delayMillis);
    }

    private void probeCurrentStage() {
        switch (stage) {
            case SEARCH -> probeSearch();
            case DETAIL -> probeEpisode();
            case PLAY -> probePlayer();
            default -> {
            }
        }
    }

    private void probeSearch() {
        String expectedName = JSONObject.quote(requestedName);
        String selector = JSONObject.quote(SEARCH_RESULT_SELECTOR);
        String script = """
                (() => {
                  const expected = %s.replace(/\\s+/g, '').toLowerCase();
                  const items = Array.from(document.querySelectorAll(%s));
                  if (!items.length) return '';
                  const selected = items.find(a =>
                    (a.textContent || '').replace(/\\s+/g, '').trim().toLowerCase() === expected
                  ) || items[0];
                  return JSON.stringify({
                    title: (selected.textContent || '').trim(),
                    url: selected.href
                  });
                })()
                """.formatted(expectedName, selector);

        webView.evaluateJavascript(script, raw -> {
            String value = decodeJavascriptResult(raw);
            if (value == null || value.isBlank()) {
                scheduleProbe(1000);
                return;
            }
            try {
                JSONObject result = new JSONObject(value);
                detailUrl = result.getString("url");
                stage = Stage.DETAIL;
                hideVerification();
                setStatus("已找到作品，正在读取第 " + requestedEpisode + " 集…");
                loadStageUrl(detailUrl);
            } catch (JSONException exception) {
                fail("搜索结果解析失败：" + exception.getMessage());
            }
        });
    }

    private void probeEpisode() {
        String selector = JSONObject.quote(EPISODE_LIST_SELECTOR);
        String script = """
                (() => {
                  const wanted = %d;
                  const lists = Array.from(document.querySelectorAll(%s));
                  if (!lists.length) return '';
                  for (const list of lists) {
                    const links = Array.from(list.querySelectorAll('a[href]'));
                    const selected = links.find(a => {
                      const name = (a.textContent || '').trim();
                      const nameMatch = name.match(/第\\s*(\\d+)\\s*[话集]/);
                      if (nameMatch && Number(nameMatch[1]) === wanted) return true;
                      const urlMatch = a.href.match(/\\/nid\\/(\\d+)(?:\\.html|\\/|$)/);
                      return urlMatch && Number(urlMatch[1]) === wanted;
                    }) || links[wanted - 1];
                    if (selected) {
                      return JSON.stringify({
                        name: (selected.textContent || '').trim(),
                        url: selected.href
                      });
                    }
                  }
                  return '__NOT_FOUND__';
                })()
                """.formatted(requestedEpisode, selector);

        webView.evaluateJavascript(script, raw -> {
            String value = decodeJavascriptResult(raw);
            if (value == null || value.isBlank()) {
                scheduleProbe(1000);
                return;
            }
            if ("__NOT_FOUND__".equals(value)) {
                fail("没有找到第 " + requestedEpisode + " 集");
                return;
            }
            try {
                episodeUrl = new JSONObject(value).getString("url");
                stage = Stage.PLAY;
                hideVerification();
                setStatus("已找到剧集，正在捕获视频地址…");
                loadStageUrl(episodeUrl);
            } catch (JSONException exception) {
                fail("剧集列表解析失败：" + exception.getMessage());
            }
        });
    }

    private void probePlayer() {
        String script = """
                (() => {
                  const p = window.player_aaaa;
                  if (p && p.url) {
                    let url = p.url;
                    try {
                      if (Number(p.encrypt) === 1) url = decodeURIComponent(url);
                      if (Number(p.encrypt) === 2) url = decodeURIComponent(atob(url));
                    } catch (_) {}
                    return url;
                  }
                  const frame = Array.from(document.querySelectorAll('iframe[src]'))
                    .find(f => /[?&]url=https?/.test(f.src));
                  if (frame) {
                    try { return new URL(frame.src).searchParams.get('url') || ''; }
                    catch (_) {}
                  }
                  return '';
                })()
                """;

        webView.evaluateJavascript(script, raw -> {
            String url = decodeJavascriptResult(raw);
            if (url != null && isMediaUrl(url)) {
                deliverVideo(url);
            } else {
                scheduleProbe(750);
            }
        });
    }

    private void inspectResourceUrl(String requestUrl) {
        if (stage != Stage.PLAY || requestUrl == null) return;

        String candidate = extractNestedVideoUrl(requestUrl);
        if (candidate == null && isMediaUrl(requestUrl)) {
            candidate = requestUrl;
        }
        if (candidate != null) {
            String finalCandidate = candidate;
            handler.post(() -> deliverVideo(finalCandidate));
        }
    }

    private String extractNestedVideoUrl(String value) {
        try {
            Uri uri = Uri.parse(value);
            String nested = uri.getQueryParameter("url");
            if (nested != null) {
                nested = URLDecoder.decode(nested, StandardCharsets.UTF_8);
                if (isMediaUrl(nested)) return nested;
            }
        } catch (Exception ignored) {
        }

        Matcher matcher = MEDIA_URL_PATTERN.matcher(value);
        return matcher.find() ? matcher.group() : null;
    }

    private boolean isMediaUrl(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http") &&
                (lower.contains(".m3u8") || lower.contains(".mp4") ||
                        lower.contains(".flv") || lower.contains(".mkv"));
    }

    private void deliverVideo(String videoUrl) {
        if (stage == Stage.DONE) return;
        stage = Stage.DONE;
        probeGeneration++;
        hideVerification();
        resolveButton.setEnabled(true);
        copyButton.setTag(videoUrl);
        copyButton.setVisibility(View.VISIBLE);
        setStatus("解析成功");

        try {
            JSONObject output = new JSONObject();
            output.put("name", requestedName);
            output.put("episode", requestedEpisode);
            output.put("source", resolvedSource);
            output.put("detailUrl", detailUrl);
            output.put("episodeUrl", episodeUrl);
            output.put("videoUrl", videoUrl);
            output.put("mediaType", videoUrl.toLowerCase(Locale.ROOT).contains(".m3u8")
                    ? "hls" : "file");
            resultView.setText(output.toString(2));
        } catch (JSONException exception) {
            resultView.setText(videoUrl);
        }
    }

    private void loadStageUrl(String url) {
        ++navigationGeneration;
        webView.loadUrl(url);
    }

    private void showVerification() {
        if (verificationPanel.getVisibility() == View.VISIBLE) return;
        verificationPanel.removeAllViews();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.WHITE);

        TextView message = text("请完成网站安全验证，验证通过后应用会自动继续。", 15);
        message.setGravity(Gravity.CENTER);
        message.setPadding(dp(12), dp(12), dp(12), dp(12));
        panel.addView(message, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

        if (webView.getParent() instanceof ViewGroup parent) {
            parent.removeView(webView);
        }
        webView.setVisibility(View.VISIBLE);
        panel.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Button close = new Button(this);
        close.setText("取消解析");
        close.setOnClickListener(view -> fail("已取消"));
        panel.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        verificationPanel.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        verificationPanel.setVisibility(View.VISIBLE);
    }

    private void hideVerification() {
        if (webView.getParent() instanceof ViewGroup parent) {
            parent.removeView(webView);
        }
        webView.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams hiddenParams = new FrameLayout.LayoutParams(dp(1), dp(1));
        hiddenParams.gravity = Gravity.BOTTOM | Gravity.END;
        root.addView(webView, hiddenParams);
        verificationPanel.setVisibility(View.GONE);
    }

    private void fail(String message) {
        probeGeneration++;
        stage = Stage.IDLE;
        hideVerification();
        resolveButton.setEnabled(true);
        copyButton.setVisibility(View.GONE);
        setStatus(message);
    }

    private void copyResult() {
        Object tag = copyButton.getTag();
        if (!(tag instanceof String value)) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("videoUrl", value));
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }

    private void setStatus(String message) {
        statusView.setText(message);
    }

    private String decodeJavascriptResult(String raw) {
        if (raw == null || "null".equals(raw) || "undefined".equals(raw)) return null;
        try {
            Object decoded = new JSONTokener(raw).nextValue();
            return decoded instanceof String ? (String) decoded : String.valueOf(decoded);
        } catch (JSONException exception) {
            return raw;
        }
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        probeGeneration++;
        handler.removeCallbacksAndMessages(null);
        networkExecutor.shutdownNow();
        httpClient.dispatcher().cancelAll();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
