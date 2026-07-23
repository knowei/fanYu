package com.example.animeresolver;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
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
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private Button favoriteButton;
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
    private volatile boolean collectingSources;
    private String subjectCover = "";
    private int probeGeneration;
    private int navigationGeneration;
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        boolean returnToPlayer = getIntent().getBooleanExtra("return_to_player", false);
        super.onCreate(savedInstanceState);
        if (returnToPlayer) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            getWindow().getDecorView().setAlpha(0f);
        }
        buildUi();
        configureWebView();
        String incomingName = getIntent().getStringExtra("subject_name");
        if (incomingName != null && !incomingName.isBlank()) {
            nameInput.setText(incomingName);
            requestedName = incomingName;
        }
        subjectCover = getIntent().getStringExtra("subject_cover");
        if (subjectCover == null) subjectCover = "";
        favoriteButton.setVisibility(incomingName == null || incomingName.isBlank()
                ? View.GONE : View.VISIBLE);
        int incomingEpisode = getIntent().getIntExtra("episode", 1);
        episodeInput.setText(String.valueOf(Math.max(1, incomingEpisode)));
        if (getIntent().getBooleanExtra("auto_resolve", false)) {
            if (!returnToPlayer) showAutoResolveOverlay();
            handler.postDelayed(this::startResolve, 300);
        }
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

        favoriteButton = new Button(this);
        favoriteButton.setText("收藏这部番剧");
        favoriteButton.setVisibility(View.GONE);
        favoriteButton.setOnClickListener(view -> toggleFavorite());
        LinearLayout.LayoutParams favoriteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        favoriteParams.topMargin = dp(10);
        content.addView(favoriteButton, favoriteParams);

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

    private void showAutoResolveOverlay() {
        LinearLayout loading = new LinearLayout(this);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setGravity(Gravity.CENTER);
        loading.setBackgroundColor(Color.WHITE);
        loading.setPadding(dp(36), dp(36), dp(36), dp(36));
        android.widget.ProgressBar progress = new android.widget.ProgressBar(this);
        loading.addView(progress, new LinearLayout.LayoutParams(dp(56), dp(56)));
        TextView title = text("正在寻找最快线路…", 20);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        titleParams.topMargin = dp(18);
        loading.addView(title, titleParams);
        TextView hint = text("解析成功后会自动进入播放页", 14);
        hint.setGravity(Gravity.CENTER);
        loading.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        root.addView(loading, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
                addLocalSources(sources);
                if (sources.isEmpty()) throw new IOException("订阅中没有兼容的数据源");

                java.util.LinkedHashMap<String, String> sourceSearchUrls =
                        new java.util.LinkedHashMap<>();
                for (SourceConfig source : sources) {
                    sourceSearchUrls.put(source.name, source.searchUrl.replace(
                            "{keyword}", Uri.encode(requestedName)));
                }
                PlayerActivity.beginSourceResolution(requestedEpisode, sourceSearchUrls);
                for (java.util.Map.Entry<String, String> source : sourceSearchUrls.entrySet()) {
                    broadcastSourceState(source.getKey(), PlayerActivity.SOURCE_LOADING,
                            "", "", source.getValue());
                }

                collectingSources = true;
                CompletionService<SourceAttempt> completion =
                        new ExecutorCompletionService<>(networkExecutor);
                AtomicBoolean delivered = new AtomicBoolean(false);
                for (SourceConfig source : sources) {
                    completion.submit(() -> {
                        try {
                            int resolvedCount = resolveSource(source, generation,
                                    new SourceProgress() {
                                        @Override
                                        public void onDiscovered(List<EpisodeCandidate> candidates) {
                                            broadcastSourceState(source.name,
                                                    PlayerActivity.SOURCE_REMOVED,
                                                    "", "", "");
                                            for (EpisodeCandidate candidate : candidates) {
                                                broadcastSourceState(candidate.sourceKey,
                                                        PlayerActivity.SOURCE_LOADING,
                                                        "", "", candidate.episodeUrl);
                                            }
                                        }

                                        @Override
                                        public void onReady(ResolveResult result) {
                                            broadcastSourceState(result.source,
                                                    PlayerActivity.SOURCE_READY,
                                                    result.videoUrl, "", result.episodeUrl);
                                            if (delivered.compareAndSet(false, true)) {
                                                runOnUiThread(() -> {
                                                    if (generation != probeGeneration) return;
                                                    resolvedSource = result.source;
                                                    detailUrl = result.detailUrl;
                                                    episodeUrl = result.episodeUrl;
                                                    stage = Stage.PLAY;
                                                    deliverVideo(result.videoUrl);
                                                });
                                            }
                                        }

                                        @Override
                                        public void onFailed(EpisodeCandidate candidate,
                                                             String error) {
                                            broadcastSourceState(candidate.sourceKey,
                                                    PlayerActivity.SOURCE_FAILED,
                                                    "", error, candidate.episodeUrl);
                                        }
                                    });
                            return new SourceAttempt(source.name,
                                    sourceSearchUrls.get(source.name),
                                    resolvedCount, "");
                        } catch (Exception exception) {
                            String message = exception.getMessage();
                            return new SourceAttempt(source.name,
                                    sourceSearchUrls.get(source.name), 0,
                                    message == null || message.isBlank() ? "解析失败" : message);
                        }
                    });
                }
                java.util.HashSet<String> completedSources = new java.util.HashSet<>();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
                for (int completed = 0; completed < sources.size(); completed++) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) break;
                    java.util.concurrent.Future<SourceAttempt> future =
                            completion.poll(remaining, TimeUnit.NANOSECONDS);
                    if (future == null) break;
                    try {
                        SourceAttempt attempt = future.get();
                        completedSources.add(attempt.source);
                        if (attempt.resolvedCount == 0 && !attempt.error.isBlank()) {
                            broadcastSourceState(attempt.source,
                                    PlayerActivity.SOURCE_FAILED, "", attempt.error,
                                    attempt.siteUrl);
                        }
                    } catch (Exception ignored) {
                    }
                }
                for (SourceConfig source : sources) {
                    if (!completedSources.contains(source.name)) {
                        broadcastSourceState(source.name, PlayerActivity.SOURCE_FAILED,
                                "", "解析超时", sourceSearchUrls.get(source.name));
                    }
                }
                collectingSources = false;
                if (destroyed) {
                    networkExecutor.shutdown();
                    httpClient.connectionPool().evictAll();
                    httpClient.dispatcher().executorService().shutdown();
                }
                if (!delivered.get()) throw new IOException("没有可用的视频源");
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
            String channelSelector = "";
            String channelNamePattern = "";
            String episodeContainer = "";
            String episodeSelector = "a";
            String episodeLinkSelector = "";
            String episodeNamePattern = "第\\s*(?<ep>.+)\\s*[话集]";
            if ("no-channel".equals(channelFormat)) {
                JSONObject format = search.optJSONObject("selectorChannelFormatNoChannel");
                if (format != null) {
                    episodeSelector = format.optString("selectEpisodes");
                    episodeLinkSelector = format.optString("selectEpisodeLinks");
                    episodeNamePattern = format.optString(
                            "matchEpisodeSortFromName", episodeNamePattern);
                    episodeContainer = "";
                }
            } else {
                JSONObject format = search.optJSONObject("selectorChannelFormatFlattened");
                if (format != null) {
                    channelSelector = format.optString("selectChannelNames");
                    channelNamePattern = format.optString("matchChannelName");
                    episodeContainer = format.optString("selectEpisodeLists");
                    episodeSelector = format.optString("selectEpisodesFromList", "a");
                    episodeLinkSelector = format.optString("selectEpisodeLinksFromList");
                    episodeNamePattern = format.optString(
                            "matchEpisodeSortFromName", episodeNamePattern);
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
                    channelFormat,
                    channelSelector,
                    channelNamePattern,
                    episodeContainer,
                    episodeSelector,
                    episodeLinkSelector,
                    episodeNamePattern,
                    arguments.optInt("tier", 99)
            ));
        }
        result.sort(Comparator.comparingInt(source -> source.tier));
        return result;
    }

    private void addLocalSources(List<SourceConfig> sources) {
        boolean present = sources.stream().anyMatch(source ->
                source.name.equals("橘子动漫") || source.searchUrl.contains("mgnacg.com"));
        if (present) return;
        sources.add(new SourceConfig(
                "橘子动漫",
                "https://www.mgnacg.com/search/-------------/?wd={keyword}",
                "indexed",
                ".search-box .thumb-content > .thumb-txt",
                ".search-box .thumb-menu > a",
                "index-grouped",
                ".anthology-tab > .swiper-wrapper a",
                "^(?<ch>.+?)(\\d+)?$",
                ".anthology-list-box",
                "a",
                "",
                "第\\s*(?<ep>.+)\\s*[话集]",
                8
        ));
        sources.sort(Comparator.comparingInt(source -> source.tier));
    }

    private int resolveSource(
            SourceConfig source,
            int generation,
            SourceProgress progress
    ) throws Exception {
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
        List<EpisodeCandidate> candidates = selectEpisodeCandidates(
                detailDocument, foundDetailUrl, source, requestedEpisode);
        if (candidates.isEmpty()) throw new IOException("剧集不存在");
        progress.onDiscovered(candidates);

        CompletionService<ChannelAttempt> completion =
                new ExecutorCompletionService<>(networkExecutor);
        for (EpisodeCandidate candidate : candidates) {
            completion.submit(() -> {
                try {
                    HttpPage playPage = getPage(candidate.episodeUrl);
                    if (isChallengePage(playPage)) throw new IOException("需要验证");
                    String mediaUrl = extractPlayerMediaUrl(playPage.html);
                    if (mediaUrl == null || !isMediaUrl(mediaUrl)) {
                        Matcher mediaMatcher = MEDIA_URL_PATTERN.matcher(playPage.html);
                        mediaUrl = mediaMatcher.find() ? mediaMatcher.group() : null;
                    }
                    if (mediaUrl == null || !isMediaUrl(mediaUrl)) {
                        throw new IOException("未发现直连媒体");
                    }
                    return new ChannelAttempt(candidate,
                            new ResolveResult(candidate.sourceKey, foundDetailUrl,
                                    candidate.episodeUrl, mediaUrl), "");
                } catch (Exception exception) {
                    String message = exception.getMessage();
                    return new ChannelAttempt(candidate, null,
                            message == null || message.isBlank() ? "解析失败" : message);
                }
            });
        }

        int resolvedCount = 0;
        java.util.HashSet<String> completed = new java.util.HashSet<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
        for (int index = 0; index < candidates.size(); index++) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            java.util.concurrent.Future<ChannelAttempt> future =
                    completion.poll(remaining, TimeUnit.NANOSECONDS);
            if (future == null) break;
            ChannelAttempt attempt = future.get();
            completed.add(attempt.candidate.sourceKey);
            if (attempt.result != null) {
                resolvedCount++;
                progress.onReady(attempt.result);
            } else {
                progress.onFailed(attempt.candidate, attempt.error);
            }
        }
        for (EpisodeCandidate candidate : candidates) {
            if (!completed.contains(candidate.sourceKey)) {
                progress.onFailed(candidate, "解析超时");
            }
        }
        return resolvedCount;
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

    private List<EpisodeCandidate> selectEpisodeCandidates(
            Document document,
            String detailUrl,
            SourceConfig source,
            int wanted
    ) {
        List<EpisodeCandidate> raw = new ArrayList<>();
        if ("no-channel".equals(source.channelFormat) ||
                source.episodeContainer == null || source.episodeContainer.isBlank()) {
            EpisodeLink selected = selectEpisodeFromGroup(document,
                    source.episodeSelector, source.episodeLinkSelector,
                    source.episodeNamePattern, wanted);
            if (selected != null) {
                raw.add(new EpisodeCandidate(source.name, "", selected.name,
                        detailUrl, selected.url));
            }
            return raw;
        }

        Elements lists = document.select(source.episodeContainer);
        Elements channelElements = source.channelSelector == null ||
                source.channelSelector.isBlank()
                ? new Elements() : document.select(source.channelSelector);
        for (int index = 0; index < lists.size(); index++) {
            String rawChannel = index < channelElements.size()
                    ? channelElements.get(index).text().trim() : "线路" + (index + 1);
            String channel = extractChannelName(rawChannel, source.channelNamePattern);
            if (channel == null) continue;
            if (channel.isBlank()) channel = "线路" + (index + 1);
            EpisodeLink selected = selectEpisodeFromGroup(lists.get(index),
                    source.episodeSelector, source.episodeLinkSelector,
                    source.episodeNamePattern, wanted);
            if (selected != null) {
                raw.add(new EpisodeCandidate("", channel, selected.name,
                        detailUrl, selected.url));
            }
        }

        java.util.HashMap<String, Integer> duplicateCounts = new java.util.HashMap<>();
        List<EpisodeCandidate> result = new ArrayList<>();
        for (int index = 0; index < raw.size(); index++) {
            EpisodeCandidate candidate = raw.get(index);
            String baseKey = source.name + " · " + candidate.channel;
            int occurrence = duplicateCounts.merge(baseKey, 1, Integer::sum);
            String key = occurrence == 1 ? baseKey : baseKey + " " + occurrence;
            result.add(new EpisodeCandidate(key, candidate.channel,
                    candidate.episodeName, candidate.detailUrl, candidate.episodeUrl));
        }
        return result;
    }

    private EpisodeLink selectEpisodeFromGroup(
            Element group,
            String episodeSelector,
            String episodeLinkSelector,
            String episodeNamePattern,
            int wanted
    ) {
        Elements episodes = group.select(episodeSelector);
        Elements links = episodeLinkSelector == null || episodeLinkSelector.isBlank()
                ? null : group.select(episodeLinkSelector);
        for (int index = 0; index < episodes.size(); index++) {
            Element episode = episodes.get(index);
            Element link = links == null ? findLink(episode)
                    : index < links.size() ? findLink(links.get(index)) : null;
            String url = link == null ? "" : link.absUrl("href");
            if (matchesEpisode(episode.text(), url, episodeNamePattern, wanted)) {
                return url.isBlank() ? null : new EpisodeLink(episode.text(), url);
            }
        }
        if (wanted <= episodes.size()) {
            int index = wanted - 1;
            Element episode = episodes.get(index);
            Element link = links == null ? findLink(episode)
                    : index < links.size() ? findLink(links.get(index)) : null;
            String url = link == null ? "" : link.absUrl("href");
            return url.isBlank() ? null : new EpisodeLink(episode.text(), url);
        }
        return null;
    }

    private Element findLink(Element element) {
        if (element == null) return null;
        if (element.hasAttr("href")) return element;
        return element.selectFirst("a[href]");
    }

    private boolean matchesEpisode(
            String name,
            String url,
            String configuredPattern,
            int wanted
    ) {
        if (configuredPattern != null && !configuredPattern.isBlank()) {
            try {
                Matcher matcher = Pattern.compile(configuredPattern).matcher(name);
                if (matcher.find()) {
                    String value;
                    try {
                        value = matcher.group("ep");
                    } catch (IllegalArgumentException ignored) {
                        value = matcher.group();
                    }
                    Matcher number = Pattern.compile("\\d+(?:\\.\\d+)?").matcher(value);
                    if (number.find() && Double.parseDouble(number.group()) == wanted) return true;
                }
            } catch (Exception ignored) {
            }
        }
        Matcher nameMatcher = Pattern.compile("第\\s*(\\d+)\\s*[话集]").matcher(name);
        if (nameMatcher.find() && Integer.parseInt(nameMatcher.group(1)) == wanted) return true;
        Matcher urlMatcher = Pattern.compile("/nid/(\\d+)(?:\\.html|/|$)").matcher(url);
        return urlMatcher.find() && Integer.parseInt(urlMatcher.group(1)) == wanted;
    }

    private String extractChannelName(String text, String configuredPattern) {
        if (configuredPattern == null || configuredPattern.isBlank()) return text;
        try {
            Matcher matcher = Pattern.compile(configuredPattern).matcher(text);
            if (!matcher.find()) return null;
            try {
                String group = matcher.group("ch");
                return group == null ? text : group.trim();
            } catch (IllegalArgumentException ignored) {
                return text;
            }
        } catch (Exception ignored) {
            return text;
        }
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
                lower.contains("安全验证") ||
                // Streamlab/MacCMS sites can show a first-party image captcha
                // inside an otherwise normal search response.
                lower.contains("请输入验证码") ||
                lower.contains("verify-submit") ||
                lower.contains("ds-verify") ||
                lower.contains("/verify/index.html");
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
            String channelFormat,
            String channelSelector,
            String channelNamePattern,
            String episodeContainer,
            String episodeSelector,
            String episodeLinkSelector,
            String episodeNamePattern,
            int tier
    ) {
    }

    private record EpisodeLink(String name, String url) {
    }

    private record EpisodeCandidate(
            String sourceKey,
            String channel,
            String episodeName,
            String detailUrl,
            String episodeUrl
    ) {
    }

    private record ResolveResult(
            String source,
            String detailUrl,
            String episodeUrl,
            String videoUrl
    ) {
    }

    private record SourceAttempt(
            String source, String siteUrl, int resolvedCount, String error) {
    }

    private record ChannelAttempt(
            EpisodeCandidate candidate, ResolveResult result, String error) {
    }

    private interface SourceProgress {
        void onDiscovered(List<EpisodeCandidate> candidates);

        void onReady(ResolveResult result);

        void onFailed(EpisodeCandidate candidate, String error);
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
        hideVerification();
        resolveButton.setEnabled(true);
        copyButton.setTag(videoUrl);
        copyButton.setVisibility(View.VISIBLE);
        setStatus("解析成功");
        getSharedPreferences("watching", MODE_PRIVATE).edit()
                .putString("name", requestedName)
                .putString("cover", subjectCover)
                .putInt("episode", requestedEpisode)
                .putString("videoUrl", videoUrl)
                .apply();

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
        if (getIntent().getBooleanExtra("return_to_player", false)) {
            finish();
            return;
        }
        Intent playerIntent = new Intent(this, PlayerActivity.class);
        playerIntent.putExtra("video_url", videoUrl);
        playerIntent.putExtra("subject_name", requestedName);
        playerIntent.putExtra("subject_cover", subjectCover);
        playerIntent.putExtra("episode", requestedEpisode);
        playerIntent.putExtra("bangumi_id", getIntent().getIntExtra("bangumi_id", 0));
        playerIntent.putExtra("available_episodes",
                getIntent().getIntExtra("available_episodes", 12));
        playerIntent.putStringArrayListExtra("episode_titles",
                getIntent().getStringArrayListExtra("episode_titles"));
        playerIntent.putExtra("source_name", resolvedSource);
        startActivity(playerIntent);
        finish();
    }

    private void broadcastSourceState(
            String sourceName, String status, String videoUrl, String error, String siteUrl) {
        PlayerActivity.cacheSourceState(
                requestedEpisode, sourceName, status, videoUrl, error, siteUrl);
        Intent update = new Intent(PlayerActivity.ACTION_SOURCE_RESULT);
        update.setPackage(getPackageName());
        update.putExtra("episode", requestedEpisode);
        update.putExtra("source_name", sourceName);
        update.putExtra("source_status", status);
        update.putExtra("video_url", videoUrl);
        update.putExtra("source_error", error);
        update.putExtra("source_site_url", siteUrl);
        sendBroadcast(update);
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

    private void toggleFavorite() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) return;
        int subjectId = getIntent().getIntExtra("bangumi_id", 0);
        boolean favorite = FavoriteStore.toggle(this, subjectId, name, subjectCover);
        favoriteButton.setText(favorite ? "已收藏，点击取消" : "收藏这部番剧");
        Toast.makeText(this, favorite ? "已加入收藏" : "已取消收藏",
                Toast.LENGTH_SHORT).show();
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
        destroyed = true;
        probeGeneration++;
        handler.removeCallbacksAndMessages(null);
        if (!collectingSources) networkExecutor.shutdownNow();
        if (!collectingSources) httpClient.dispatcher().cancelAll();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
