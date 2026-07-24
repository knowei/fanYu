package com.example.animeresolver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class DataAndCacheActivity extends Activity {
    private static final int REQUEST_EXPORT_DIAGNOSTICS = 811;
    private static final int BLUE = Color.rgb(25, 112, 243);
    private static final int INK = Color.rgb(22, 25, 31);
    private static final int MUTED = Color.rgb(105, 108, 115);
    private static final int LINE = Color.rgb(229, 231, 235);
    private static final int WARM = Color.rgb(253, 252, 250);
    private LinearLayout content;

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        render();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(WARM);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(6), dp(8), 0);
        MaterialButton back = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialIconButtonStyle);
        back.setIconResource(R.drawable.ic_arrow_back_24);
        back.setIconTint(ColorStateList.valueOf(INK));
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(52), dp(56)));
        TextView title = text("数据与缓存", 20, INK, true);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
        header.addView(new View(this), new LinearLayout.LayoutParams(dp(52), dp(56)));
        root.addView(header);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(14), dp(22), dp(34));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        SystemBars.apply(this, root, WARM);
        setContentView(root);
    }

    private void render() {
        content.removeAllViews();
        content.addView(section("缓存与网站会话"));
        content.addView(row("图片与临时缓存", formatBytes(folderSize(getCacheDir())), () -> confirm(
                "清除缓存？", "会重新加载封面，不影响收藏、历史和视频源规则。", this::clearCache)));
        content.addView(row("网站 Cookie", "清除后部分站点需要重新验证", () -> confirm(
                "清除网站 Cookie？", "已完成的网站验证会失效。", this::clearCookies)));
        content.addView(section("本地数据"), margins(0, 24, 4));
        content.addView(row("播放记录", WatchHistoryStore.read(this).length() + " 条", () -> confirm(
                "清除播放记录？", "收藏与本地讨论不会被删除。", () -> {
                    WatchHistoryStore.clear(this); toast("播放记录已清除"); render();
                })));
        content.addView(row("恢复默认设置", "索引源恢复为自动", () -> confirm(
                "恢复默认设置？", "不会删除视频源规则、收藏和播放记录。", () -> {
                    IndexSourceStore.set(this, IndexSourceStore.AUTO); toast("已恢复默认设置"); render();
                })));
        content.addView(section("问题诊断"), margins(0, 24, 4));
        content.addView(row("导出诊断报告", DiagnosticStore.count(this) + " 条事件", this::exportDiagnostics));
        content.addView(row("清除诊断记录", "不影响应用使用", () -> confirm(
                "清除诊断记录？", "已记录的脱敏错误信息会被删除。", () -> {
                    DiagnosticStore.clear(this); toast("诊断记录已清除"); render();
                })));
        TextView privacy = text("诊断报告不会包含 Cookie、查询参数、播放签名或完整视频地址。", 13, MUTED, false);
        privacy.setLineSpacing(dp(3), 1f);
        content.addView(privacy, margins(0, 14, 0));
    }

    private void clearCache() {
        deleteChildren(getCacheDir());
        ImageLoader.clearMemoryCache();
        toast("缓存已清除");
        render();
    }

    private void clearCookies() {
        CookieManager.getInstance().removeAllCookies(value -> runOnUiThread(() -> {
            CookieManager.getInstance().flush();
            toast("网站 Cookie 已清除");
        }));
    }

    private void exportDiagnostics() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "fanyu-diagnostics.json");
        startActivityForResult(intent, REQUEST_EXPORT_DIAGNOSTICS);
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT_DIAGNOSTICS || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        writeDiagnostics(data.getData());
    }

    private void writeDiagnostics(Uri uri) {
        try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) throw new IllegalStateException("无法打开文件");
            output.write(DiagnosticStore.report(this).toString(2).getBytes(StandardCharsets.UTF_8));
            output.flush();
            toast("诊断报告已导出");
        } catch (Exception error) {
            toast("导出失败：" + DiagnosticStore.sanitize(error.getMessage()));
        }
    }

    private View row(String title, String detail, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(6), dp(12), dp(6));
        row.setBackgroundColor(Color.WHITE);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(title, 16, INK, false));
        words.addView(text(detail, 12, MUTED, false));
        row.addView(words, new LinearLayout.LayoutParams(0, dp(58), 1));
        TextView arrow = text("›", 28, MUTED, false);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(58)));
        row.setOnClickListener(v -> action.run());
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(row);
        View divider = new View(this); divider.setBackgroundColor(LINE);
        wrapper.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return wrapper;
    }

    private TextView section(String value) { return text(value, 19, INK, true); }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL); if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return view;
    }
    private LinearLayout.LayoutParams margins(int left, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(left), dp(top), 0, dp(bottom)); return p;
    }
    private void confirm(String title, String message, Runnable action) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("取消", null)
                .setPositiveButton("确认", (dialog, which) -> action.run()).show();
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private long folderSize(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return file.length();
        long size = 0L; File[] children = file.listFiles();
        if (children != null) for (File child : children) size += folderSize(child); return size;
    }
    private void deleteChildren(File directory) {
        File[] children = directory == null ? null : directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) deleteChildren(child);
            child.delete();
        }
    }
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.CHINA, "%.1f KB", bytes / 1024f);
        return String.format(Locale.CHINA, "%.1f MB", bytes / (1024f * 1024f));
    }
}
