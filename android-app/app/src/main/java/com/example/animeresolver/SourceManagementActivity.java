package com.example.animeresolver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


/** A lightweight, search-only health check for the configured video sources. */
public class SourceManagementActivity extends Activity {
    private static final String SUBSCRIPTION_URL = "https://sub.creamycake.org/v1/css1.json";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final int BLUE = Color.rgb(25, 112, 243);
    private static final int INK = Color.rgb(22, 25, 31);
    private static final int MUTED = Color.rgb(105, 108, 115);
    private static final int LINE = Color.rgb(229, 231, 235);
    private static final int REQUEST_VERIFY = 701;
    private static final int REQUEST_EDIT_SOURCE = 702;

    private final OkHttpClient client = new OkHttpClient.Builder().followRedirects(true).build();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final List<SourceSite> sources = new ArrayList<>();
    private EditText keywordInput;
    private TextView summary;
    private LinearLayout list;
    private SourceSite verifyingSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadSources();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), dp(14));
        root.setBackgroundColor(Color.rgb(253, 252, 250));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        View back = iconButton();
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));
        TextView title = text("视频源管理", 22, INK, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button add = actionButton("添加", false);
        add.setOnClickListener(v -> openEditor(null));
        header.addView(add, new LinearLayout.LayoutParams(dp(66), dp(36)));
        root.addView(header);

        TextView tip = text("用一部番剧测试各个搜索源；只检查搜索，不会请求视频。需要验证码的站点可在应用内统一验证。", 13, MUTED, false);
        tip.setLineSpacing(dp(3), 1f);
        root.addView(tip, params(-1, -2, 0, 2, 0, 14));

        LinearLayout search = new LinearLayout(this);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(12), 0, dp(5), 0);
        search.setBackground(round(Color.WHITE, 14, LINE, 1));
        keywordInput = new EditText(this);
        keywordInput.setSingleLine(true);
        keywordInput.setText("航海王");
        keywordInput.setTextSize(15);
        keywordInput.setTextColor(INK);
        keywordInput.setHint("输入想测试的番剧");
        keywordInput.setBackgroundColor(Color.TRANSPARENT);
        search.addView(keywordInput, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button testAll = actionButton("测试全部", true);
        testAll.setOnClickListener(v -> testAll());
        search.addView(testAll, new LinearLayout.LayoutParams(dp(88), dp(38)));
        root.addView(search, params(-1, dp(50), 0, 0, 0, 12));

        summary = text("正在读取视频源…", 13, MUTED, false);
        root.addView(summary, params(-1, dp(28), 0, 0, 0, 4));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void loadSources() {
        executor.execute(() -> {
            List<SourceSite> parsed = new ArrayList<>();
            String remoteError = "";
            try {
                String json = getPage(SUBSCRIPTION_URL).html;
                parsed.addAll(parseSources(json));
            } catch (Exception error) {
                remoteError = "订阅源暂时读取失败，仅显示本地源";
            }
            for (LocalSourceStore.Config local : LocalSourceStore.read(this)) {
                parsed.add(SourceSite.fromLocal(local));
            }
            parsed.sort(Comparator.comparingInt(source -> source.tier));
            String finalRemoteError = remoteError;
            runOnUiThread(() -> {
                sources.clear();
                sources.addAll(parsed);
                renderSources();
                updateSummary();
                if (!finalRemoteError.isBlank()) summary.setText(finalRemoteError + " · 本地 "
                        + LocalSourceStore.read(this).size() + " 个");
            });
        });
    }

    private List<SourceSite> parseSources(String raw) throws Exception {
        List<SourceSite> result = new ArrayList<>();
        JSONArray items = new JSONObject(raw).getJSONObject("exportedMediaSourceDataList")
                .getJSONArray("mediaSources");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null || !"web-selector".equals(item.optString("factoryId"))) continue;
            JSONObject args = item.optJSONObject("arguments");
            JSONObject search = args == null ? null : args.optJSONObject("searchConfig");
            if (args == null || search == null) continue;
            String format = search.optString("subjectFormatId", "a");
            String selector = "";
            if ("a".equals(format)) {
                JSONObject config = search.optJSONObject("selectorSubjectFormatA");
                if (config != null) selector = config.optString("selectLists");
            } else if ("indexed".equals(format)) {
                JSONObject config = search.optJSONObject("selectorSubjectFormatIndexed");
                if (config != null) selector = config.optString("selectNames");
            }
            String url = search.optString("searchUrl");
            if (url.isBlank() || selector.isBlank()) continue;
            result.add(new SourceSite(args.optString("name", "未命名源"), url, selector,
                    args.optInt("tier", 99), ""));
        }
        return result;
    }

    private void testAll() {
        String keyword = keywordInput.getText().toString().trim();
        if (keyword.isEmpty() || sources.isEmpty()) return;
        for (SourceSite source : sources) {
            if (!source.enabled) continue;
            source.status = "正在测试";
            source.detail = "正在检查搜索会话…";
        }
        renderSources();
        updateSummary();
        for (SourceSite source : sources) if (source.enabled) checkSource(source, keyword);
    }

    private void checkSource(SourceSite source, String keyword) {
        if (!source.enabled || keyword == null || keyword.isBlank()) return;
        source.status = "正在测试";
        source.detail = "正在检查搜索会话…";
        runOnUiThread(() -> { renderSources(); updateSummary(); });
        executor.execute(() -> {
            try {
                String url = source.searchUrl.replace("{keyword}", Uri.encode(keyword));
                HttpPage page = getPage(url);
                if (isChallengePage(page.html)) {
                    source.status = "需要验证";
                    source.detail = "完成一次站点验证后可再次测试";
                } else {
                    Document document = Jsoup.parse(page.html, page.url);
                    int count = document.select(source.subjectSelector).size();
                    source.status = count > 0 ? "搜索可用" : "无匹配结果";
                    source.detail = count > 0 ? "找到 " + count + " 个可解析条目" : "页面已打开，但没有匹配条目";
                }
            } catch (Exception error) {
                source.status = "连接失败";
                source.detail = cleanError(error.getMessage());
            }
            runOnUiThread(() -> { renderSources(); updateSummary(); });
        });
    }

    private void renderSources() {
        list.removeAllViews();
        for (SourceSite source : sources) list.addView(sourceRow(source), params(-1, -2, 0, 0, 0, 10));
    }

    private View sourceRow(SourceSite source) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(round(Color.WHITE, 14, LINE, 1));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(source.name, 16, INK, true);
        top.addView(name, new LinearLayout.LayoutParams(0, dp(28), 1));
        TextView badge = text(source.status, 12, statusColor(source.status), true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(9), 0, dp(9), 0);
        badge.setBackground(round(statusBackground(source.status), 12, Color.TRANSPARENT, 0));
        top.addView(badge, new LinearLayout.LayoutParams(-2, dp(26)));
        card.addView(top);
        String detailText = source.localId.isBlank() ? source.detail
                : "本地源 · " + source.detail;
        card.addView(text(detailText, 13, MUTED, false), params(-1, -2, 0, 3, 0, 8));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.RIGHT);
        if (!source.localId.isBlank()) {
            Button edit = actionButton("编辑", false);
            edit.setOnClickListener(v -> openEditor(source.localId));
            actions.addView(edit, new LinearLayout.LayoutParams(dp(64), dp(34)));
        }
        if (source.enabled) {
            Button retry = actionButton("重新测试", false);
            retry.setOnClickListener(v -> checkSource(source, keywordInput.getText().toString().trim()));
            LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(dp(84), dp(34));
            retryParams.setMargins(dp(8), 0, 0, 0);
            actions.addView(retry, retryParams);
        }
        if (source.enabled && "需要验证".equals(source.status)) {
            Button verify = actionButton("应用内验证", true);
            verify.setOnClickListener(v -> verify(source));
            LinearLayout.LayoutParams verifyParams = new LinearLayout.LayoutParams(dp(104), dp(34));
            verifyParams.setMargins(dp(8), 0, 0, 0);
            actions.addView(verify, verifyParams);
        }
        card.addView(actions);
        if (!source.localId.isBlank()) card.setOnLongClickListener(v -> {
            confirmDelete(source);
            return true;
        });
        return card;
    }

    private void openEditor(String sourceId) {
        Intent intent = new Intent(this, SourceEditorActivity.class);
        if (sourceId != null) intent.putExtra("source_id", sourceId);
        startActivityForResult(intent, REQUEST_EDIT_SOURCE);
    }

    private void confirmDelete(SourceSite source) {
        new AlertDialog.Builder(this).setTitle("删除本地源？")
                .setMessage("将删除“" + source.name + "”，不会影响 css1.json 订阅。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    LocalSourceStore.remove(this, source.localId);
                    loadSources();
                }).show();
    }

    private void verify(SourceSite source) {
        String keyword = keywordInput.getText().toString().trim();
        if (keyword.isEmpty()) return;
        verifyingSource = source;
        Intent intent = new Intent(this, SiteVerificationActivity.class);
        intent.putExtra("verification_url", source.searchUrl.replace("{keyword}", Uri.encode(keyword)));
        startActivityForResult(intent, REQUEST_VERIFY);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VERIFY && resultCode == RESULT_OK && verifyingSource != null) {
            checkSource(verifyingSource, keywordInput.getText().toString().trim());
        } else if (requestCode == REQUEST_EDIT_SOURCE && resultCode == RESULT_OK) {
            loadSources();
        }
    }

    private void updateSummary() {
        int ready = 0, challenge = 0, failed = 0, testing = 0;
        for (SourceSite source : sources) {
            if ("搜索可用".equals(source.status)) ready++;
            else if ("需要验证".equals(source.status)) challenge++;
            else if ("连接失败".equals(source.status)) failed++;
            else if ("正在测试".equals(source.status)) testing++;
        }
        if (sources.isEmpty()) return;
        summary.setText("共 " + sources.size() + " 个源  ·  可用 " + ready
                + "  ·  待验证 " + challenge + "  ·  失败 " + failed
                + (testing > 0 ? "  ·  测试中 " + testing : ""));
    }

    private HttpPage getPage(String url) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null && !cookies.isBlank()) builder.header("Cookie", cookies);
        try (Response response = client.newCall(builder.build()).execute()) {
            for (String cookie : response.headers("Set-Cookie")) CookieManager.getInstance().setCookie(url, cookie);
            CookieManager.getInstance().flush();
            if (response.body() == null) throw new IOException("响应内容为空");
            String html = response.body().string();
            if (!response.isSuccessful() && !isChallengePage(html)) throw new IOException("HTTP " + response.code());
            return new HttpPage(response.request().url().toString(), html);
        }
    }

    private boolean isChallengePage(String html) {
        String value = html == null ? "" : html.toLowerCase(Locale.ROOT);
        return value.contains("cf-chl-") || value.contains("challenge-platform")
                || value.contains("cdn-cgi/challenge") || value.contains("turnstile")
                || value.contains("just a moment") || value.contains("安全验证")
                || value.contains("请输入验证码") || value.contains("verify-submit")
                || value.contains("ds-verify") || value.contains("/verify/index.html");
    }

    private String cleanError(String message) {
        if (message == null || message.isBlank()) return "请求没有返回有效内容";
        return message.length() > 45 ? message.substring(0, 45) + "…" : message;
    }

    private int statusColor(String status) {
        if ("搜索可用".equals(status)) return Color.rgb(18, 125, 74);
        if ("需要验证".equals(status) || "正在测试".equals(status)) return Color.rgb(190, 113, 0);
        if ("连接失败".equals(status)) return Color.rgb(205, 61, 61);
        return MUTED;
    }

    private int statusBackground(String status) {
        if ("搜索可用".equals(status)) return Color.rgb(232, 247, 238);
        if ("需要验证".equals(status) || "正在测试".equals(status)) return Color.rgb(255, 246, 224);
        if ("连接失败".equals(status)) return Color.rgb(255, 235, 235);
        return Color.rgb(243, 245, 248);
    }

    private Button actionButton(String value, boolean primary) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(13);
        button.setTextColor(primary ? Color.WHITE : BLUE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(round(primary ? BLUE : Color.rgb(239, 246, 255), 10,
                primary ? BLUE : Color.rgb(211, 229, 255), primary ? 0 : 1));
        return button;
    }

    private ImageButton iconButton() {
        ImageButton button = new ImageButton(this);
        button.setImageResource(R.drawable.ic_arrow_back_24);
        button.setColorFilter(INK);
        button.setScaleType(ImageButton.ScaleType.CENTER);
        button.setPadding(dp(11), dp(11), dp(11), dp(11));
        button.setBackground(round(Color.rgb(244, 247, 252), 22, Color.TRANSPARENT, 0));
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

    private LinearLayout.LayoutParams params(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams result = new LinearLayout.LayoutParams(width, height);
        result.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return result;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        client.dispatcher().cancelAll();
        super.onDestroy();
    }

    private static class SourceSite {
        final String name;
        final String searchUrl;
        final String subjectSelector;
        final int tier;
        final String localId;
        final boolean enabled;
        String status = "等待测试";
        String detail = "输入番剧名后可检查这个源";

        SourceSite(String name, String searchUrl, String subjectSelector, int tier, String localId) {
            this(name, searchUrl, subjectSelector, tier, localId, true);
        }

        SourceSite(String name, String searchUrl, String subjectSelector, int tier,
                   String localId, boolean enabled) {
            this.name = name;
            this.searchUrl = searchUrl;
            this.subjectSelector = subjectSelector;
            this.tier = tier;
            this.localId = localId == null ? "" : localId;
            this.enabled = enabled;
        }

        static SourceSite fromLocal(LocalSourceStore.Config config) {
            SourceSite source = new SourceSite(config.name, config.searchUrl,
                    config.subjectSelector, config.tier, config.id, config.enabled);
            if (!config.enabled) {
                source.status = "已停用";
                source.detail = "当前不会参与视频解析";
            } else if (config.autoDetected) {
                source.detail = "自动探测配置，可编辑或长按删除";
            } else {
                source.detail = "手动配置，可编辑或长按删除";
            }
            return source;
        }
    }

    private record HttpPage(String url, String html) { }
}
