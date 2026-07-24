package com.example.animeresolver;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends Activity {
    private static final int BLUE = Color.rgb(25, 112, 243);
    private static final int INK = Color.rgb(22, 25, 31);
    private static final int MUTED = Color.rgb(105, 108, 115);
    private static final int LINE = Color.rgb(229, 231, 235);
    private static final int WARM_WHITE = Color.rgb(253, 252, 250);

    private LinearLayout content;
    private LinearLayout filterRow;
    private TextView manageButton;
    private String selectedFilter = "全部";
    private boolean managing;

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) render();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(WARM_WHITE);

        LinearLayout appBar = new LinearLayout(this);
        appBar.setGravity(Gravity.CENTER_VERTICAL);
        appBar.setPadding(dp(8), dp(6), dp(8), 0);
        MaterialButton back = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialIconButtonStyle);
        back.setIconResource(R.drawable.ic_arrow_back_24);
        back.setIconTint(ColorStateList.valueOf(INK));
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnClickListener(v -> finish());
        appBar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(56)));

        TextView title = text("播放历史", 20, INK, true);
        title.setGravity(Gravity.CENTER);
        appBar.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
        manageButton = text("管理", 15, BLUE, false);
        manageButton.setGravity(Gravity.CENTER);
        manageButton.setOnClickListener(v -> {
            managing = !managing;
            manageButton.setText(managing ? "完成" : "管理");
            render();
        });
        appBar.addView(manageButton, new LinearLayout.LayoutParams(dp(60), dp(56)));
        root.addView(appBar);

        filterRow = new LinearLayout(this);
        filterRow.setPadding(dp(22), dp(8), dp(22), dp(8));
        root.addView(filterRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(6), dp(22), dp(36));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        SystemBars.apply(this, root, WARM_WHITE);
        setContentView(root);
    }

    private void render() {
        renderFilters();
        content.removeAllViews();
        JSONArray history = WatchHistoryStore.read(this);
        List<JSONObject> visible = new ArrayList<>();
        for (int index = 0; index < history.length(); index++) {
            JSONObject item = history.optJSONObject(index);
            if (item != null && matchesFilter(item)) visible.add(item);
        }

        if (managing && history.length() > 0) {
            TextView clear = text("清空播放历史", 14, Color.rgb(194, 55, 62), false);
            clear.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            clear.setOnClickListener(v -> confirmClearHistory());
            content.addView(clear, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        }

        if (visible.isEmpty()) {
            TextView empty = text(history.length() == 0
                    ? "看过的番剧会出现在这里" : "这个时间段还没有播放记录",
                    15, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            content.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
            return;
        }

        for (int index = 0; index < visible.size(); index += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.TOP);
            row.addView(historyCard(visible.get(index), index == 0), cardParams(true));
            if (index + 1 < visible.size()) {
                row.addView(historyCard(visible.get(index + 1), false), cardParams(false));
            } else {
                row.addView(new View(this), cardParams(false));
            }
            content.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(index == 0 ? 320 : 292)));
        }
    }

    private LinearLayout.LayoutParams cardParams(boolean left) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        if (left) params.setMargins(0, 0, dp(7), 0);
        else params.setMargins(dp(7), 0, 0, 0);
        return params;
    }

    private View historyCard(JSONObject item, boolean primary) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);

        FrameLayout poster = new FrameLayout(this);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(238, 240, 244));
        poster.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        String cover = item.optString("cover");
        if (!cover.isBlank()) ImageLoader.with(this).load(largeImage(cover)).fit().centerCrop().into(image);

        ProgressBar progress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        long duration = item.optLong("duration", 0L);
        long position = item.optLong("position", 0L);
        progress.setMax(1000);
        progress.setProgress(duration > 0 ? (int) Math.min(1000, position * 1000 / duration) : 0);
        progress.setProgressTintList(ColorStateList.valueOf(BLUE));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(218, 221, 226)));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4), Gravity.BOTTOM);
        poster.addView(progress, progressParams);

        if (managing) {
            TextView remove = text("删除", 12, Color.WHITE, true);
            remove.setGravity(Gravity.CENTER);
            remove.setBackground(rounded(Color.argb(220, 194, 55, 62), 11));
            remove.setOnClickListener(v -> {
                WatchHistoryStore.remove(this, item);
                render();
            });
            FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(
                    dp(54), dp(32), Gravity.TOP | Gravity.END);
            removeParams.setMargins(0, dp(8), dp(8), 0);
            poster.addView(remove, removeParams);
        }
        card.addView(poster, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(205)));

        TextView title = text(item.optString("name"), 15, INK, true);
        title.setMaxLines(2);
        title.setPadding(0, dp(8), 0, 0);
        card.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        String metadata = "第 " + item.optInt("episode", 1) + " 集";
        if (position > 0) metadata += " · 看到 " + formatTime(position);
        TextView meta = text(metadata, 13, MUTED, false);
        card.addView(meta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
        if (primary && !managing) {
            LinearLayout resume = new LinearLayout(this);
            resume.setGravity(Gravity.CENTER_VERTICAL);
            ImageView play = new ImageView(this);
            play.setImageResource(R.drawable.ic_play_24);
            play.setColorFilter(BLUE);
            resume.addView(play, new LinearLayout.LayoutParams(dp(20), dp(20)));
            TextView label = text("继续", 13, BLUE, true);
            label.setPadding(dp(5), 0, 0, 0);
            resume.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));
            card.addView(resume, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
        }
        if (!managing) card.setOnClickListener(v -> resume(item));
        return card;
    }

    private void renderFilters() {
        filterRow.removeAllViews();
        for (String filter : new String[]{"全部", "今天", "本周"}) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            TextView title = text(filter, 15,
                    filter.equals(selectedFilter) ? BLUE : MUTED,
                    filter.equals(selectedFilter));
            title.setGravity(Gravity.CENTER);
            item.addView(title, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            View indicator = new View(this);
            indicator.setBackgroundColor(filter.equals(selectedFilter) ? BLUE : Color.TRANSPARENT);
            LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(dp(42), dp(3));
            item.addView(indicator, indicatorParams);
            item.setOnClickListener(v -> {
                selectedFilter = filter;
                render();
            });
            filterRow.addView(item, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
    }

    private boolean matchesFilter(JSONObject item) {
        if ("全部".equals(selectedFilter)) return true;
        long timestamp = item.optLong("updatedAt", 0L);
        LocalDate date = Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = LocalDate.now();
        if ("今天".equals(selectedFilter)) return date.equals(today);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return !date.isBefore(monday) && !date.isAfter(today);
    }

    private void resume(JSONObject item) {
        String videoUrl = item.optString("videoUrl");
        Intent intent;
        if (!videoUrl.isBlank()) {
            intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("video_url", videoUrl);
            intent.putExtra("resume_position", item.optLong("position", 0L));
        } else {
            intent = new Intent(this, MainActivity.class);
            intent.putExtra("auto_resolve", true);
        }
        intent.putExtra("subject_name", item.optString("name"));
        intent.putExtra("subject_cover", item.optString("cover"));
        intent.putExtra("episode", item.optInt("episode", 1));
        intent.putExtra("bangumi_id", item.optInt("bangumiId", 0));
        startActivity(intent);
    }

    private void confirmClearHistory() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("清空播放历史？")
                .setMessage("清空后无法恢复，但不会影响收藏。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    WatchHistoryStore.clear(this);
                    managing = false;
                    manageButton.setText("管理");
                    render();
                })
                .show();
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private String formatTime(long milliseconds) {
        long totalSeconds = Math.max(0L, milliseconds / 1000L);
        return String.format(java.util.Locale.ROOT, "%02d:%02d",
                totalSeconds / 60, totalSeconds % 60);
    }

    private String largeImage(String url) {
        if (url == null) return "";
        return url.replace("/pic/cover/c/", "/pic/cover/l/")
                .replace("/pic/cover/m/", "/pic/cover/l/")
                .replace("/pic/cover/s/", "/pic/cover/l/");
    }
}
