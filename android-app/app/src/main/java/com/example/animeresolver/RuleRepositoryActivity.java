package com.example.animeresolver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** A user-facing catalog for downloadable and locally installed source rules. */
public class RuleRepositoryActivity extends Activity {
    private static final String INDEX_URL =
            "https://raw.githubusercontent.com/knowei/fanYu/master/rules/index.json";
    private static final int BLUE = Color.rgb(47, 111, 237);
    private static final int INK = Color.rgb(22, 25, 31);
    private static final int MUTED = Color.rgb(105, 108, 115);
    private static final int LINE = Color.rgb(226, 232, 240);
    private static final int BACKGROUND = Color.rgb(248, 251, 255);
    private static final int REQUEST_IMPORT = 921;
    private static final int REQUEST_EXPORT = 922;

    private final OkHttpClient client = new OkHttpClient.Builder().followRedirects(true).build();
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final List<CatalogRule> catalog = new ArrayList<>();
    private LinearLayout content;
    private Button repositoryTab;
    private Button installedTab;
    private TextView subtitle;
    private boolean showInstalled;
    private boolean catalogLoaded;
    private String catalogMessage = "正在连接规则仓库…";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadCatalog(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) render();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), dp(12));
        root.setBackgroundColor(BACKGROUND);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton back = iconButton(R.drawable.ic_arrow_back_24);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("视频源规则", 22, INK, true), new LinearLayout.LayoutParams(-1, dp(30)));
        TextView headerHint = text("安装、更新和管理解析规则", 12, MUTED, false);
        titles.addView(headerHint, new LinearLayout.LayoutParams(-1, dp(20)));
        header.addView(titles, new LinearLayout.LayoutParams(0, dp(54), 1));
        Button menu = actionButton("更多", false);
        menu.setOnClickListener(this::showMoreMenu);
        header.addView(menu, new LinearLayout.LayoutParams(dp(68), dp(36)));
        root.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(round(Color.rgb(234, 240, 249), 14, Color.TRANSPARENT, 0));
        repositoryTab = tabButton("规则仓库");
        installedTab = tabButton("已安装");
        repositoryTab.setOnClickListener(v -> switchTab(false));
        installedTab.setOnClickListener(v -> switchTab(true));
        tabs.addView(repositoryTab, new LinearLayout.LayoutParams(0, dp(42), 1));
        tabs.addView(installedTab, new LinearLayout.LayoutParams(0, dp(42), 1));
        root.addView(tabs, margins(-1, dp(50), 0, 14, 0, 14));

        subtitle = text(catalogMessage, 13, MUTED, false);
        root.addView(subtitle, margins(-1, dp(28), 2, 0, 2, 6));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(18));
        UiMotion.animateLayout(content);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        SystemBars.apply(this, root, BACKGROUND);
        setContentView(root);
        updateTabs();
        render();
    }

    private void switchTab(boolean installed) {
        if (showInstalled == installed) return;
        showInstalled = installed;
        updateTabs();
        render();
    }

    private void updateTabs() {
        styleTab(repositoryTab, !showInstalled);
        styleTab(installedTab, showInstalled);
    }

    private void styleTab(Button button, boolean selected) {
        button.setTextColor(selected ? BLUE : MUTED);
        button.setBackground(round(selected ? Color.WHITE : Color.TRANSPARENT,
                11, selected ? Color.rgb(210, 223, 246) : Color.TRANSPARENT, selected ? 1 : 0));
        button.setElevation(selected ? dp(1) : 0);
    }

    private void loadCatalog(boolean force) {
        if (!force && catalogLoaded) return;
        catalogMessage = "正在连接规则仓库…";
        if (!showInstalled) render();
        executor.execute(() -> {
            boolean fallback = false;
            try {
                parseCatalog(getText(INDEX_URL));
                catalogMessage = "仓库已更新 · 共 " + catalog.size() + " 条规则";
            } catch (Exception remoteError) {
                try {
                    parseCatalog(readAsset("rules/index.json"));
                    fallback = true;
                    catalogMessage = "网络暂不可用 · 正在显示内置目录";
                } catch (Exception assetError) {
                    catalog.clear();
                    catalogMessage = "规则仓库暂时无法读取";
                }
            }
            catalogLoaded = true;
            boolean usedFallback = fallback;
            runOnUiThread(() -> {
                render();
                if (usedFallback) Toast.makeText(this, "已切换到内置规则目录", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private synchronized void parseCatalog(String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        if (!"fanyu-rule-index".equals(root.optString("format"))) {
            throw new IllegalArgumentException("仓库索引格式不正确");
        }
        if (root.optInt("version", 0) > 1) throw new IllegalArgumentException("仓库版本过新");
        JSONArray rules = root.getJSONArray("rules");
        List<CatalogRule> parsed = new ArrayList<>();
        for (int i = 0; i < rules.length(); i++) {
            JSONObject item = rules.optJSONObject(i);
            if (item == null) continue;
            CatalogRule rule = CatalogRule.fromJson(item);
            if (rule.valid() && !"deprecated".equals(rule.status)) parsed.add(rule);
        }
        catalog.clear();
        catalog.addAll(parsed);
    }

    private void render() {
        if (content == null) return;
        content.removeAllViews();
        if (showInstalled) renderInstalled(); else renderRepository();
    }

    private void renderRepository() {
        subtitle.setText(catalogMessage);
        content.addView(cssPackageRepositoryCard(), margins(-1, -2, 0, 0, 0, 12));
        if (!catalogLoaded) {
            content.addView(infoCard("正在加载", "正在获取最新规则和版本信息。"));
            return;
        }
        if (catalog.isEmpty()) {
            content.addView(infoCard("暂时无法显示仓库", "请检查网络后点击重新加载。"));
            Button retry = actionButton("重新加载", true);
            retry.setOnClickListener(v -> loadCatalog(true));
            content.addView(retry, margins(-1, dp(46), 0, 12, 0, 0));
            return;
        }
        for (CatalogRule rule : catalog) content.addView(repositoryCard(rule), margins(-1, -2, 0, 0, 0, 10));
    }

    private View repositoryCard(CatalogRule rule) {
        LinearLayout card = card();
        LinearLayout first = new LinearLayout(this);
        first.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(rule.name, 17, INK, true);
        first.addView(name, new LinearLayout.LayoutParams(0, dp(30), 1));
        TextView version = badge("v" + rule.version, Color.rgb(232, 240, 255), BLUE);
        first.addView(version, new LinearLayout.LayoutParams(-2, dp(25)));
        card.addView(first);

        String detail = rule.description.isBlank() ? "由 " + rule.author + " 提供" : rule.description;
        TextView description = text(detail, 13, MUTED, false);
        description.setLineSpacing(dp(2), 1f);
        card.addView(description, margins(-1, -2, 0, 5, 0, 9));

        LinearLayout tags = new LinearLayout(this);
        if (rule.multiRoad) tags.addView(tag("多线路"), margins(-2, dp(24), 0, 0, 6, 0));
        if (rule.antiCrawler) tags.addView(tag("需要验证"), margins(-2, dp(24), 0, 0, 6, 0));
        tags.addView(tag(rule.mode.toUpperCase(Locale.ROOT)), margins(-2, dp(24), 0, 0, 6, 0));
        card.addView(tags, margins(-1, dp(26), 0, 0, 0, 12));

        String installedVersion = RuleRepositoryStore.installedVersion(this, rule.id);
        boolean installed = LocalSourceStore.find(this, rule.id) != null;
        boolean adopt = installed && installedVersion.isBlank();
        boolean update = installed && RuleRepositoryStore.isNewer(rule.version, installedVersion);
        Button install = actionButton(adopt ? "关联仓库" : update ? "更新" : installed ? "已安装" : "安装",
                !installed || update || adopt);
        install.setEnabled(!installed || update);
        install.setAlpha(install.isEnabled() ? 1f : .62f);
        install.setOnClickListener(v -> installRule(rule, install));
        card.addView(install, new LinearLayout.LayoutParams(-1, dp(42)));
        return card;
    }

    private void renderInstalled() {
        List<LocalSourceStore.Config> installed = LocalSourceStore.read(this);
        boolean cssInstalled = SubscriptionRuleStore.isInstalled(this);
        int installedCount = installed.size() + (cssInstalled ? 1 : 0);
        subtitle.setText("已安装 " + installedCount + " 项 · 规则包和单条规则均可卸载");

        Button health = actionButton("检测所有已启用规则", false);
        health.setOnClickListener(v -> startActivity(new Intent(this, SourceManagementActivity.class)));
        content.addView(health, margins(-1, dp(44), 0, 0, 0, 12));

        if (cssInstalled) {
            content.addView(cssPackageInstalledCard(), margins(-1, -2, 0, 0, 0, 10));
        }

        if (installed.isEmpty() && !cssInstalled) {
            content.addView(infoCard("还没有安装规则", "切换到规则仓库，选择需要的视频源即可。"));
            return;
        }
        for (LocalSourceStore.Config config : installed) {
            content.addView(installedCard(config), margins(-1, -2, 0, 0, 0, 10));
        }
    }

    private View cssPackageRepositoryCard() {
        boolean installed = SubscriptionRuleStore.isInstalled(this);
        SubscriptionRuleStore.InstallInfo info = SubscriptionRuleStore.info(this);
        LinearLayout card = card();
        LinearLayout first = new LinearLayout(this);
        first.setGravity(Gravity.CENTER_VERTICAL);
        first.addView(text("CSS1 视频源合集", 17, INK, true),
                new LinearLayout.LayoutParams(0, dp(30), 1));
        first.addView(badge(installed ? "已下载" : "可选规则包",
                installed ? Color.rgb(232, 247, 238) : Color.rgb(232, 240, 255),
                installed ? Color.rgb(18, 125, 74) : BLUE),
                new LinearLayout.LayoutParams(-2, dp(25)));
        card.addView(first);
        String detail = installed
                ? "本地包含 " + info.sourceCount() + " 个兼容源 · " + formatBytes(info.bytes())
                : "一次下载多个 CSS 视频源；播放解析只读取下载后的本地副本。";
        TextView description = text(detail, 13, MUTED, false);
        description.setLineSpacing(dp(2), 1f);
        card.addView(description, margins(-1, -2, 0, 5, 0, 10));
        LinearLayout tags = new LinearLayout(this);
        tags.addView(tag("本地使用"), margins(-2, dp(24), 0, 0, 6, 0));
        tags.addView(tag("多视频源"), margins(-2, dp(24), 0, 0, 6, 0));
        tags.addView(tag("可卸载"), margins(-2, dp(24), 0, 0, 6, 0));
        card.addView(tags, margins(-1, dp(26), 0, 0, 0, 12));
        Button download = actionButton(installed ? "重新下载" : "下载规则包", true);
        download.setOnClickListener(v -> downloadCssPackage(download));
        card.addView(download, new LinearLayout.LayoutParams(-1, dp(42)));
        return card;
    }

    private View cssPackageInstalledCard() {
        SubscriptionRuleStore.InstallInfo info = SubscriptionRuleStore.info(this);
        LinearLayout card = card();
        LinearLayout first = new LinearLayout(this);
        first.setGravity(Gravity.CENTER_VERTICAL);
        first.addView(text("CSS1 视频源合集", 17, INK, true),
                new LinearLayout.LayoutParams(0, dp(30), 1));
        first.addView(badge("已安装", Color.rgb(232, 247, 238), Color.rgb(18, 125, 74)),
                new LinearLayout.LayoutParams(-2, dp(25)));
        card.addView(first);
        card.addView(text(info.sourceCount() + " 个兼容源 · " + formatBytes(info.bytes())
                        + " · 仅从本地读取", 13, MUTED, false),
                margins(-1, dp(26), 0, 2, 0, 10));
        LinearLayout actions = new LinearLayout(this);
        Button refresh = actionButton("重新下载", false);
        refresh.setOnClickListener(v -> downloadCssPackage(refresh));
        actions.addView(refresh, new LinearLayout.LayoutParams(0, dp(38), 1));
        Button uninstall = actionButton("卸载", false);
        uninstall.setOnClickListener(v -> confirmUninstallCssPackage());
        LinearLayout.LayoutParams uninstallParams = new LinearLayout.LayoutParams(0, dp(38), 1);
        uninstallParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(uninstall, uninstallParams);
        card.addView(actions);
        return card;
    }

    private void downloadCssPackage(Button button) {
        button.setEnabled(false);
        button.setText("正在下载…");
        executor.execute(() -> {
            try {
                SubscriptionRuleStore.InstallInfo info = SubscriptionRuleStore.install(this,
                        getText(SubscriptionRuleStore.DOWNLOAD_URL));
                runOnUiThread(() -> {
                    Toast.makeText(this, "规则包已下载，共 " + info.sourceCount() + " 个兼容源",
                            Toast.LENGTH_SHORT).show();
                    render();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "下载失败：" + shortError(error), Toast.LENGTH_LONG).show();
                    render();
                });
            }
        });
    }

    private void confirmUninstallCssPackage() {
        new AlertDialog.Builder(this)
                .setTitle("卸载 CSS1 规则包？")
                .setMessage("卸载后，其中包含的视频源将立即停止参与搜索和解析。需要时可再次下载。")
                .setNegativeButton("取消", null)
                .setPositiveButton("卸载", (dialog, which) -> {
                    if (SubscriptionRuleStore.uninstall(this)) {
                        Toast.makeText(this, "CSS1 规则包已卸载", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "卸载失败，请稍后重试", Toast.LENGTH_LONG).show();
                    }
                    render();
                }).show();
    }

    private View installedCard(LocalSourceStore.Config config) {
        LinearLayout card = card();
        LinearLayout first = new LinearLayout(this);
        first.setGravity(Gravity.CENTER_VERTICAL);
        first.addView(text(config.name, 17, INK, true), new LinearLayout.LayoutParams(0, dp(30), 1));
        String version = RuleRepositoryStore.installedVersion(this, config.id);
        first.addView(badge(version.isBlank() ? "本地" : "v" + version,
                config.enabled ? Color.rgb(232, 247, 238) : Color.rgb(241, 243, 246),
                config.enabled ? Color.rgb(18, 125, 74) : MUTED), new LinearLayout.LayoutParams(-2, dp(25)));
        card.addView(first);
        TextView host = text(host(config.searchUrl)
                + (config.enabled ? " · 已启用" : " · 已停用") + " · 长按卸载", 13, MUTED, false);
        card.addView(host, margins(-1, dp(25), 0, 2, 0, 10));

        LinearLayout actions = new LinearLayout(this);
        Button toggle = actionButton(config.enabled ? "停用" : "启用", false);
        toggle.setOnClickListener(v -> {
            LocalSourceStore.save(this, new LocalSourceStore.Config(config.id, config.name,
                    config.searchUrl, config.subjectSelector, config.episodeContainer,
                    config.episodeSelector, config.channelSelector, config.tier,
                    !config.enabled, config.autoDetected));
            render();
        });
        actions.addView(toggle, new LinearLayout.LayoutParams(0, dp(38), 1));
        Button share = actionButton("分享", false);
        share.setOnClickListener(v -> share(config));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(38), 1);
        actionParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(share, actionParams);
        Button edit = actionButton("编辑", false);
        edit.setOnClickListener(v -> {
            Intent intent = new Intent(this, SourceEditorActivity.class);
            intent.putExtra("source_id", config.id);
            startActivity(intent);
        });
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(0, dp(38), 1);
        editParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(edit, editParams);
        card.addView(actions);
        card.setOnLongClickListener(v -> { confirmRemove(config); return true; });
        return card;
    }

    private void installRule(CatalogRule rule, Button button) {
        button.setEnabled(false);
        button.setText("正在安装…");
        executor.execute(() -> {
            try {
                String raw;
                try {
                    raw = getText(resolveDownloadUrl(rule.downloadUrl));
                } catch (Exception remoteError) {
                    raw = readAsset("rules/" + rule.id + ".json");
                }
                JSONObject payload = new JSONObject(raw);
                if (!rule.id.equals(payload.optString("id"))) throw new IOException("规则身份不匹配");
                if (payload.optInt("schemaVersion", 0) > 1) throw new IOException("规则需要更高版本的番遇");
                JSONObject configJson = payload.getJSONObject("config");
                LocalSourceStore.Config config = LocalSourceStore.Config.fromJson(configJson);
                if (!config.isValid()) throw new IOException("规则内容不完整");
                LocalSourceStore.save(this, config);
                RuleRepositoryStore.markInstalled(this, rule.id, payload.optString("version", rule.version));
                runOnUiThread(() -> {
                    Toast.makeText(this, rule.name + " 已安装", Toast.LENGTH_SHORT).show();
                    render();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "安装失败：" + shortError(error), Toast.LENGTH_LONG).show();
                    render();
                });
            }
        });
    }

    private String resolveDownloadUrl(String value) {
        if (value.startsWith("https://")) return value;
        int slash = INDEX_URL.lastIndexOf('/');
        return INDEX_URL.substring(0, slash + 1) + value.replaceFirst("^\\./", "");
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("添加网站");
        menu.getMenu().add("导入 JSON 文件");
        menu.getMenu().add("粘贴规则链接");
        menu.getMenu().add("导出全部规则");
        menu.getMenu().add("刷新仓库");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("添加网站".equals(title)) startActivity(new Intent(this, SourceEditorActivity.class));
            else if ("导入 JSON 文件".equals(title)) chooseImport();
            else if ("粘贴规则链接".equals(title)) showLinkImport();
            else if ("导出全部规则".equals(title)) chooseExport();
            else if ("刷新仓库".equals(title)) loadCatalog(true);
            return true;
        });
        menu.show();
    }

    private void showLinkImport() {
        EditText input = new EditText(this);
        input.setHint("fanyu://rule/…");
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        new AlertDialog.Builder(this)
                .setTitle("粘贴规则链接")
                .setMessage("规则链接会显示来源配置，导入后仍可编辑或停用。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("导入", (dialog, which) -> {
                    try {
                        JSONObject configJson = RuleRepositoryStore.decodeRule(input.getText().toString());
                        LocalSourceStore.Config config = LocalSourceStore.Config.fromJson(configJson);
                        if (!config.isValid()) throw new IllegalArgumentException("规则内容不完整");
                        LocalSourceStore.save(this, config);
                        showInstalled = true;
                        updateTabs();
                        render();
                        Toast.makeText(this, "规则已导入", Toast.LENGTH_SHORT).show();
                    } catch (Exception error) {
                        Toast.makeText(this, "导入失败：" + shortError(error), Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void share(LocalSourceStore.Config config) {
        try {
            String link = RuleRepositoryStore.encodeRule(config);
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("番遇规则", link));
            Toast.makeText(this, "规则链接已复制", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "无法生成规则链接", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmRemove(LocalSourceStore.Config config) {
        new AlertDialog.Builder(this)
                .setTitle("卸载这条规则？")
                .setMessage("将移除“" + config.name + "”，之后可以从仓库重新安装。")
                .setNegativeButton("取消", null)
                .setPositiveButton("卸载", (dialog, which) -> {
                    RuleRepositoryStore.remove(this, config.id);
                    render();
                }).show();
    }

    private void chooseImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    private void chooseExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "fanyu-source-rules.json");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        executor.execute(() -> {
            try {
                if (requestCode == REQUEST_IMPORT) {
                    try (InputStream input = getContentResolver().openInputStream(uri)) {
                        if (input == null) throw new IOException("无法读取文件");
                        LocalSourceStore.ImportResult result = LocalSourceStore.importJson(this,
                                new String(input.readAllBytes(), StandardCharsets.UTF_8));
                        runOnUiThread(() -> {
                            showInstalled = true;
                            updateTabs();
                            render();
                            Toast.makeText(this, "已导入 " + result.imported() + " 条规则", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else if (requestCode == REQUEST_EXPORT) {
                    try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                        if (output == null) throw new IOException("无法写入文件");
                        output.write(LocalSourceStore.exportJson(this).getBytes(StandardCharsets.UTF_8));
                        runOnUiThread(() -> Toast.makeText(this, "规则已导出", Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this, "操作失败：" + shortError(error), Toast.LENGTH_LONG).show());
            }
        });
    }

    private String getText(String url) throws IOException {
        Request request = new Request.Builder().url(url)
                .header("User-Agent", "FanYu-Android/1.1")
                .header("Accept", "application/json").build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("HTTP " + response.code());
            return response.body().string();
        }
    }

    private String readAsset(String path) throws IOException {
        try (InputStream input = getAssets().open(path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String host(String url) {
        try { return Uri.parse(url).getHost(); } catch (Exception ignored) { return "本地规则"; }
    }

    private String shortError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return "未知错误";
        return message.length() > 48 ? message.substring(0, 48) + "…" : message;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.CHINA, "%.1f KB", bytes / 1024f);
        return String.format(Locale.CHINA, "%.1f MB", bytes / (1024f * 1024f));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(round(Color.WHITE, 16, LINE, 1));
        return card;
    }

    private View infoCard(String title, String message) {
        LinearLayout card = card();
        card.addView(text(title, 17, INK, true), new LinearLayout.LayoutParams(-1, dp(30)));
        TextView detail = text(message, 13, MUTED, false);
        detail.setLineSpacing(dp(2), 1f);
        card.addView(detail, margins(-1, -2, 0, 4, 0, 0));
        return card;
    }

    private TextView tag(String value) {
        return badge(value, Color.rgb(242, 246, 252), Color.rgb(72, 91, 119));
    }

    private TextView badge(String value, int background, int foreground) {
        TextView view = text(value, 11, foreground, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(9), 0, dp(9), 0);
        view.setBackground(round(background, 10, Color.TRANSPARENT, 0));
        return view;
    }

    private Button tabButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(0, 0, 0, 0);
        return button;
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
        button.setBackground(round(primary ? BLUE : Color.rgb(234, 242, 255), 11,
                primary ? BLUE : Color.rgb(194, 213, 250), primary ? 0 : 1));
        button.setElevation(0);
        return button;
    }

    private MaterialButton iconButton(int icon) {
        MaterialButton button = new MaterialButton(this);
        button.setText("");
        button.setIconResource(icon);
        button.setIconTint(ColorStateList.valueOf(INK));
        button.setIconSize(dp(24));
        button.setIconPadding(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setRippleColor(ColorStateList.valueOf(Color.argb(24, 47, 111, 237)));
        button.setCornerRadius(dp(24));
        button.setElevation(0);
        button.setStateListAnimator(null);
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

    private LinearLayout.LayoutParams margins(int width, int height, int left, int top, int right, int bottom) {
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

    private static final class CatalogRule {
        final String id;
        final String name;
        final String version;
        final String author;
        final String description;
        final String status;
        final String mode;
        final String downloadUrl;
        final boolean antiCrawler;
        final boolean multiRoad;

        CatalogRule(String id, String name, String version, String author, String description,
                    String status, String mode, String downloadUrl,
                    boolean antiCrawler, boolean multiRoad) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.author = author;
            this.description = description;
            this.status = status;
            this.mode = mode;
            this.downloadUrl = downloadUrl;
            this.antiCrawler = antiCrawler;
            this.multiRoad = multiRoad;
        }

        static CatalogRule fromJson(JSONObject json) {
            JSONObject capabilities = json.optJSONObject("capabilities");
            return new CatalogRule(json.optString("id"), json.optString("name"),
                    json.optString("version"), json.optString("author", "社区"),
                    json.optString("description"), json.optString("status", "active"),
                    json.optString("mode", "css"), json.optString("downloadUrl"),
                    capabilities != null && capabilities.optBoolean("antiCrawler"),
                    capabilities == null || capabilities.optBoolean("multiRoad", true));
        }

        boolean valid() {
            return !id.isBlank() && !name.isBlank() && !version.isBlank() && !downloadUrl.isBlank();
        }
    }
}
