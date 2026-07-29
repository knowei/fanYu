package com.example.animeresolver;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SourceEditorActivity extends Activity {
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final int BLUE = Color.rgb(47, 111, 237);
    private static final int INK = Color.rgb(22, 25, 31);
    private static final int MUTED = Color.rgb(105, 108, 115);
    private static final int LINE = Color.rgb(229, 231, 235);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient client = new OkHttpClient.Builder().followRedirects(true).build();

    private String sourceId = "";
    private EditText nameInput;
    private EditText addressInput;
    private EditText searchSelectorInput;
    private EditText episodeContainerInput;
    private EditText episodeSelectorInput;
    private EditText channelSelectorInput;
    private EditText videoRuleInput;
    private TextView probeStatus;
    private Button probeButton;
    private Button advancedToggle;
    private LinearLayout advancedFields;
    private Switch enabledSwitch;
    private boolean autoDetected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        sourceId = getIntent().getStringExtra("source_id");
        if (sourceId == null) sourceId = "";
        if (!sourceId.isBlank()) fill(LocalSourceStore.find(this, sourceId));
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), dp(14));
        root.setBackgroundColor(Color.rgb(250, 252, 255));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = iconButton();
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = text("本地视频源", 22, INK, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, dp(8), 0, dp(20));
        UiMotion.animateLayout(form);
        TextView intro = text("输入搜索页面或网站首页，完整探测会验证搜索、详情、选集和真实播放地址，并生成 v2 规则。", 13, MUTED, false);
        intro.setLineSpacing(dp(3), 1f);
        form.addView(intro, margins(-1, -2, 0, 0, 14));
        nameInput = field(form, "名称", "例如：橘子动漫", false);
        addressInput = field(form, "网站或搜索地址", "https://example.com 或包含 {keyword} 的地址", false);

        probeButton = actionButton("完整探测并测试播放", true);
        probeButton.setOnClickListener(v -> probe());
        form.addView(probeButton, margins(-1, dp(46), 0, 2, 8));
        probeStatus = text("将使用“史莱姆”测试第一个搜索结果及其第一集，请仅探测你有权访问的网站。", 12, MUTED, false);
        probeStatus.setLineSpacing(dp(2), 1f);
        form.addView(probeStatus, margins(-1, -2, 0, 0, 18));

        TextView modeHint = text("普通模式只需要填写名称和网站地址，再运行完整探测。", 13,
                Color.rgb(55, 82, 130), true);
        modeHint.setPadding(dp(12), dp(9), dp(12), dp(9));
        modeHint.setBackground(round(Color.rgb(234, 242, 255), 12,
                Color.TRANSPARENT, 0));
        form.addView(modeHint, margins(-1, -2, 0, 0, 12));

        advancedToggle = actionButton("展开高级设置", false);
        advancedToggle.setContentDescription("展开搜索和选集选择器设置");
        form.addView(advancedToggle, margins(-1, dp(44), 0, 0, 8));
        advancedFields = new LinearLayout(this);
        advancedFields.setOrientation(LinearLayout.VERTICAL);
        advancedFields.setVisibility(View.GONE);
        UiMotion.animateLayout(advancedFields);
        searchSelectorInput = field(advancedFields, "搜索结果选择器",
                ".module-card-item-title > a", true);
        episodeContainerInput = field(advancedFields, "选集容器选择器",
                ".module-play-list-content", true);
        episodeSelectorInput = field(advancedFields, "单集选择器", "a", true);
        channelSelectorInput = field(advancedFields, "线路名称选择器（可留空）",
                ".module-tab-item > span", true);
        videoRuleInput = jsonField(advancedFields, "视频提取规则（v2 JSON）",
                "自动探测后会生成，也可以手动修改");
        form.addView(advancedFields);
        advancedToggle.setOnClickListener(v -> setAdvancedVisible(
                advancedFields.getVisibility() != View.VISIBLE));

        LinearLayout enabledRow = new LinearLayout(this);
        enabledRow.setGravity(Gravity.CENTER_VERTICAL);
        enabledRow.addView(text("启用这个源", 15, INK, true), new LinearLayout.LayoutParams(0, dp(52), 1));
        enabledSwitch = new Switch(this);
        enabledSwitch.setChecked(true);
        enabledRow.addView(enabledSwitch, new LinearLayout.LayoutParams(dp(64), dp(52)));
        form.addView(enabledRow);

        Button save = actionButton("保存到本地源", true);
        save.setTextSize(15);
        save.setOnClickListener(v -> save());
        form.addView(save, margins(-1, dp(50), 0, 10, 0));
        scroll.addView(form, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        SystemBars.apply(this, root, Color.rgb(250, 252, 255));
        setContentView(root);
    }

    private EditText field(LinearLayout form, String label, String hint, boolean technical) {
        form.addView(text(label, 14, INK, true), margins(-1, dp(28), 0, 6, 4));
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(technical ? 13 : 14);
        input.setTextColor(INK);
        input.setHintTextColor(Color.rgb(150, 154, 163));
        input.setHint(hint);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(round(Color.WHITE, 12, LINE, 1));
        form.addView(input, margins(-1, dp(48), 0, 0, 10));
        return input;
    }

    private EditText jsonField(LinearLayout form, String label, String hint) {
        form.addView(text(label, 14, INK, true), margins(-1, dp(28), 0, 6, 4));
        EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(6);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setTextSize(12);
        input.setTextColor(INK);
        input.setHintTextColor(Color.rgb(150, 154, 163));
        input.setHint(hint);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(round(Color.WHITE, 12, LINE, 1));
        form.addView(input, margins(-1, -2, 0, 0, 10));
        return input;
    }

    private void fill(LocalSourceStore.Config config) {
        if (config == null) return;
        nameInput.setText(config.name);
        addressInput.setText(config.searchUrl);
        searchSelectorInput.setText(config.subjectSelector);
        episodeContainerInput.setText(config.episodeContainer);
        episodeSelectorInput.setText(config.episodeSelector);
        channelSelectorInput.setText(config.channelSelector);
        videoRuleInput.setText(config.videoRule.toJson().toString());
        enabledSwitch.setChecked(config.enabled);
        autoDetected = config.autoDetected;
        probeStatus.setText(config.autoDetected ? "这个配置最初由自动探测生成，可继续修改。" : "正在编辑手动配置。"
        );
    }

    private void probe() {
        String address = addressInput.getText().toString().trim();
        if (address.isBlank()) {
            Toast.makeText(this, "请先输入网站地址", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!address.startsWith("http://") && !address.startsWith("https://")) address = "https://" + address;
        final String target = address;
        probeButton.setEnabled(false);
        probeButton.setText("正在探测…");
        probeStatus.setText("正在识别搜索入口和页面结构…");
        executor.execute(() -> {
            try {
                ProbeResult result = detect(target);
                runOnUiThread(() -> applyProbe(result));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    probeButton.setEnabled(true);
                    probeButton.setText("重新完整探测");
                    String message = error.getMessage() == null ? "无法识别这个网站" : error.getMessage();
                    probeStatus.setText("探测失败：" + message + "。可以手动填写选择器后保存。"
                    );
                    setAdvancedVisible(true);
                });
            }
        });
    }

    private ProbeResult detect(String address) throws Exception {
        String template = address.contains("{keyword}") ? address : searchTemplateFromAddress(address);
        if (template == null) {
            HttpPage home = getPage(address);
            if (isChallenge(home.html)) throw new IOException("网站要求先完成验证");
            template = findSearchForm(Jsoup.parse(home.html, home.url));
            if (template == null) template = findWorkingCommonSearch(home.url);
        }
        HttpPage search = getPage(template.replace("{keyword}", Uri.encode("史莱姆")));
        if (isChallenge(search.html)) throw new IOException("搜索页面要求先完成验证");
        Document searchDocument = Jsoup.parse(search.html, search.url);
        String subjectSelector = firstWorkingSelector(searchDocument, new String[]{
                ".module-card-item>.module-card-item-info>.module-card-item-title>a",
                ".module-search-item a[href]", ".search-box .thumb-menu > a",
                ".search-box .thumb-content a[href]", ".module-card-item a[href]",
                ".stui-vodlist__thumb[href]", ".vodlist a[href]"
        }, 1);
        if (subjectSelector == null) throw new IOException("已找到搜索页，但无法判断结果列表");
        Element first = searchDocument.selectFirst(subjectSelector);
        Element link = first != null && first.hasAttr("href") ? first : first == null ? null : first.selectFirst("a[href]");
        String detailUrl = link == null ? "" : link.absUrl("href");
        if (detailUrl.isBlank()) throw new IOException("搜索结果没有详情链接");
        HttpPage detail = getPage(detailUrl);
        if (isChallenge(detail.html)) throw new IOException("详情页面要求先完成验证");
        Document detailDocument = Jsoup.parse(detail.html, detail.url);
        String episodeContainer = firstWorkingSelector(detailDocument, new String[]{
                ".module-play-list-content", ".anthology-list-box", ".stui-content__playlist",
                ".module-play-list", ".playlist", ".play-list"
        }, 2);
        if (episodeContainer == null) throw new IOException("已找到作品，但无法判断选集列表");
        String channelSelector = firstWorkingSelector(detailDocument, new String[]{
                ".module-tab-item>span", ".anthology-tab>.swiper-wrapper a",
                ".play_source_tab a", ".nav-tabs li"
        }, 1);
        Element episodeRoot = detailDocument.selectFirst(episodeContainer);
        Element episodeLink = episodeRoot == null ? null : episodeRoot.selectFirst("a[href]");
        if (episodeLink == null || episodeLink.absUrl("href").isBlank()) {
            throw new IOException("已识别选集列表，但没有可测试的剧集链接");
        }
        String episodeUrl = episodeLink.absUrl("href");
        VideoRule videoRule = VideoRule.defaults();
        Map<String, String> requestHeaders = VideoExtractorEngine.requestHeaders(videoRule, episodeUrl);
        HttpPage play = getPage(episodeUrl, requestHeaders);
        VideoExtractorEngine.Result media = VideoExtractorEngine.extract(episodeUrl,
                new VideoExtractorEngine.Page(play.url, play.html),
                videoRule,
                (url, headers) -> {
                    HttpPage nested = getPage(url, headers);
                    return new VideoExtractorEngine.Page(nested.url, nested.html);
                });
        if (media == null || media.url().isBlank()) {
            throw new IOException("选集已找到，但默认提取器没有发现播放地址");
        }
        verifyMedia(media.url(), media.playbackHeaders());
        return new ProbeResult(template, subjectSelector, episodeContainer, "a",
                channelSelector == null ? "" : channelSelector, hostName(template),
                videoRule, media.url());
    }

    private String searchTemplateFromAddress(String address) {
        if (address.matches(".*[?&](wd|keyword|q|search)=[^&]*.*")) {
            return address.replaceFirst("([?&](?:wd|keyword|q|search)=)[^&]*", "$1{keyword}");
        }
        return null;
    }

    private String findSearchForm(Document document) {
        for (Element form : document.select("form")) {
            Element input = form.selectFirst("input[name=wd],input[name=keyword],input[name=q],input[name=search]");
            if (input == null || "post".equalsIgnoreCase(form.attr("method"))) continue;
            String action = form.absUrl("action");
            if (action.isBlank()) action = document.baseUri();
            String separator = action.contains("?") ? "&" : "?";
            return action + separator + Uri.encode(input.attr("name")) + "={keyword}";
        }
        return null;
    }

    private String findWorkingCommonSearch(String base) throws Exception {
        URI uri = URI.create(base);
        String origin = uri.getScheme() + "://" + uri.getAuthority();
        String[] candidates = {
                origin + "/vod/search.html?wd={keyword}",
                origin + "/search/-------------/?wd={keyword}",
                origin + "/search.html?wd={keyword}"
        };
        for (String candidate : candidates) {
            try {
                HttpPage page = getPage(candidate.replace("{keyword}", Uri.encode("史莱姆")));
                if (!isChallenge(page.html) && page.html.length() > 500) return candidate;
            } catch (Exception ignored) {
            }
        }
        throw new IOException("没有识别到 GET 搜索入口");
    }

    private String firstWorkingSelector(Document document, String[] candidates, int minimum) {
        for (String selector : candidates) if (document.select(selector).size() >= minimum) return selector;
        return null;
    }

    private void applyProbe(ProbeResult result) {
        addressInput.setText(result.searchUrl);
        searchSelectorInput.setText(result.subjectSelector);
        episodeContainerInput.setText(result.episodeContainer);
        episodeSelectorInput.setText(result.episodeSelector);
        channelSelectorInput.setText(result.channelSelector);
        videoRuleInput.setText(result.videoRule.toJson().toString());
        if (nameInput.getText().toString().trim().isBlank()) nameInput.setText(result.suggestedName);
        autoDetected = true;
        probeButton.setEnabled(true);
        probeButton.setText("重新完整探测");
        probeStatus.setText("完整探测成功：搜索、详情、选集和播放地址均已验证。\n"
                + result.videoUrl);
    }

    private void setAdvancedVisible(boolean visible) {
        advancedFields.setVisibility(visible ? View.VISIBLE : View.GONE);
        advancedToggle.setText(visible ? "收起高级设置" : "展开高级设置");
        advancedToggle.setContentDescription(visible
                ? "收起搜索和选集选择器设置" : "展开搜索和选集选择器设置");
        if (visible) advancedFields.post(() -> UiMotion.fadeIn(advancedFields));
    }

    private void save() {
        String name = nameInput.getText().toString().trim();
        String searchUrl = addressInput.getText().toString().trim();
        String subject = searchSelectorInput.getText().toString().trim();
        String episodes = episodeContainerInput.getText().toString().trim();
        if (name.isBlank() || searchUrl.isBlank() || !searchUrl.contains("{keyword}")
                || subject.isBlank() || episodes.isBlank()) {
            Toast.makeText(this, "请补全名称、搜索模板、搜索结果和选集选择器", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            String videoJson = videoRuleInput.getText().toString().trim();
            VideoRule videoRule = videoJson.isBlank()
                    ? VideoRule.defaults() : VideoRule.fromJson(new JSONObject(videoJson));
            LocalSourceStore.save(this, new LocalSourceStore.Config(sourceId, name, searchUrl,
                    subject, episodes, episodeSelectorInput.getText().toString().trim(),
                    channelSelectorInput.getText().toString().trim(), 20,
                    enabledSwitch.isChecked(), autoDetected, videoRule));
        } catch (Exception error) {
            Toast.makeText(this, "视频提取规则 JSON 无效：" + error.getMessage(), Toast.LENGTH_LONG).show();
            setAdvancedVisible(true);
            return;
        }
        setResult(RESULT_OK);
        finish();
    }

    private HttpPage getPage(String url) throws IOException {
        return getPage(url, Map.of());
    }

    private HttpPage getPage(String url, Map<String, String> configuredHeaders) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        if (configuredHeaders != null) {
            for (Map.Entry<String, String> header : configuredHeaders.entrySet()) {
                if (!header.getKey().isBlank() && header.getValue() != null && !header.getValue().isBlank()) {
                    builder.header(header.getKey(), header.getValue());
                }
            }
        }
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null && !cookies.isBlank()) builder.header("Cookie", cookies);
        try (Response response = client.newCall(builder.build()).execute()) {
            for (String cookie : response.headers("Set-Cookie")) CookieManager.getInstance().setCookie(url, cookie);
            CookieManager.getInstance().flush();
            if (response.body() == null) throw new IOException("响应内容为空");
            String html = response.body().string();
            if (!response.isSuccessful() && !isChallenge(html)) throw new IOException("HTTP " + response.code());
            return new HttpPage(response.request().url().toString(), html);
        }
    }

    private void verifyMedia(String url, Map<String, String> headers) throws IOException {
        Request.Builder builder = new Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Range", "bytes=0-2047");
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (!header.getKey().isBlank() && header.getValue() != null && !header.getValue().isBlank()) {
                    builder.header(header.getKey(), header.getValue());
                }
            }
        }
        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful() && response.code() != 206) {
                throw new IOException("播放地址测试失败：HTTP " + response.code());
            }
            if (response.body() == null) throw new IOException("播放地址测试失败：响应为空");
        }
    }

    private boolean isChallenge(String html) {
        String value = html == null ? "" : html.toLowerCase(Locale.ROOT);
        return value.contains("cf-chl-") || value.contains("challenge-platform")
                || value.contains("turnstile") || value.contains("安全验证")
                || value.contains("请输入验证码") || value.contains("verify-submit")
                || value.contains("ds-verify") || value.contains("/verify/index.html");
    }

    private String hostName(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return "本地视频源";
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception ignored) {
            return "本地视频源";
        }
    }

    private Button actionButton(String value, boolean primary) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(14);
        button.setTextColor(primary ? Color.WHITE : BLUE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setBackground(round(primary ? BLUE : Color.rgb(234, 242, 255), 11,
                primary ? Color.TRANSPARENT : Color.rgb(191, 211, 255), primary ? 0 : 1));
        return button;
    }

    private ImageButton iconButton() {
        ImageButton button = new ImageButton(this);
        button.setImageResource(R.drawable.ic_arrow_back_24);
        button.setColorFilter(INK);
        button.setScaleType(ImageButton.ScaleType.CENTER);
        button.setPadding(dp(11), dp(11), dp(11), dp(11));
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(int color, int radius, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams margins(int width, int height, int left, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), 0, dp(bottom));
        return params;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        client.dispatcher().cancelAll();
        super.onDestroy();
    }

    private record HttpPage(String url, String html) {}
    private record ProbeResult(String searchUrl, String subjectSelector,
                               String episodeContainer, String episodeSelector,
                               String channelSelector, String suggestedName,
                               VideoRule videoRule, String videoUrl) {}
}
