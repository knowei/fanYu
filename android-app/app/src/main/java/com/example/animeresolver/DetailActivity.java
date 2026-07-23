package com.example.animeresolver;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DetailActivity extends Activity {
    private static final int EPISODES_PER_RANGE = 12;
    private static final int RANGES_PER_GROUP = 10;
    private static final String BASE = "https://bgm.liwen.icu";
    private static final int BLUE = Color.rgb(20, 105, 245);
    private static final int INK = Color.rgb(21, 24, 29);
    private static final int MUTED = Color.rgb(104, 108, 116);
    private static final int LINE = Color.rgb(225, 228, 233);
    private static final int WARM = Color.rgb(253, 252, 250);

    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private MaterialButton favoriteButton;
    private int subjectId;
    private int selectedEpisode = 1;
    private String subjectName = "";
    private String subjectCover = "";
    private int totalEpisodes = 12;
    private int availableEpisodes = 1;
    private final ArrayList<String> episodeTitles = new ArrayList<>();
    private LinearLayout episodeRangeRow;
    private LinearLayout episodeGroupRow;
    private HorizontalScrollView episodeRangeScroll;
    private HorizontalScrollView episodeGroupScroll;
    private LinearLayout episodeGrid;
    private int selectedEpisodeRange;

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        subjectId = getIntent().getIntExtra("bangumi_id", 0);
        subjectName = getIntent().getStringExtra("subject_name");
        subjectCover = getIntent().getStringExtra("subject_cover");
        if (subjectName == null) subjectName = "番剧详情";
        if (subjectCover == null) subjectCover = "";
        buildShell();
        if (subjectId > 0) loadDetail(); else renderFallback();
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

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(WARM);

        LinearLayout appBar = new LinearLayout(this);
        appBar.setGravity(Gravity.CENTER_VERTICAL);
        appBar.setPadding(dp(10), dp(8), dp(10), 0);
        MaterialButton back = iconButton(R.drawable.ic_arrow_back_24);
        back.setOnClickListener(v -> finish());
        appBar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(56)));
        TextView title = text("番剧详情", 20, INK, true);
        title.setGravity(Gravity.CENTER);
        appBar.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
        favoriteButton = iconButton(R.drawable.ic_favorite_border_24);
        favoriteButton.setOnClickListener(v -> toggleFavorite());
        appBar.addView(favoriteButton, new LinearLayout.LayoutParams(dp(52), dp(56)));
        root.addView(appBar);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(12), dp(20), dp(26));
        showDetailLoading();
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private void showDetailLoading() {
        content.removeAllViews();
        LinearLayout hero = new LinearLayout(this);
        hero.setGravity(Gravity.TOP);
        View cover = loadingBlock(dp(146), dp(218), 12);
        hero.addView(cover, new LinearLayout.LayoutParams(dp(146), dp(218)));
        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(dp(18), 0, 0, 0);
        meta.addView(loadingBlock(0, dp(24), 8), loadingParams(0, dp(24), 0, 0, 0, 1));
        meta.addView(loadingBlock(dp(158), dp(16), 8), loadingParams(dp(158), dp(16), 0, 14, 0));
        meta.addView(loadingBlock(dp(126), dp(16), 8), loadingParams(dp(126), dp(16), 0, 22, 0));
        meta.addView(loadingBlock(dp(96), dp(14), 7), loadingParams(dp(96), dp(14), 0, 16, 0));
        hero.addView(meta, new LinearLayout.LayoutParams(0, dp(218), 1));
        content.addView(hero);
        content.addView(divider(), margins(0, 20, 20));
        content.addView(loadingBlock(dp(84), dp(22), 8), loadingParams(dp(84), dp(22), 0, 0, 0));
        content.addView(loadingBlock(ViewGroup.LayoutParams.MATCH_PARENT, dp(15), 7),
                loadingParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(15), 0, 16, 0));
        content.addView(loadingBlock(dp(260), dp(15), 7), loadingParams(dp(260), dp(15), 0, 10, 0));
        content.addView(loadingBlock(dp(300), dp(15), 7), loadingParams(dp(300), dp(15), 0, 10, 0));

        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(24), 0, dp(12));
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(BLUE));
        status.addView(spinner, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView label = text("正在读取番剧信息与已更新集数…", 13, MUTED, false);
        label.setPadding(dp(10), 0, 0, 0);
        status.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)));
        content.addView(status);
    }

    private View loadingBlock(int width, int height, int radius) {
        View block = new View(this);
        block.setBackground(rounded(Color.rgb(237, 240, 245), radius, 0, 0));
        return block;
    }

    private LinearLayout.LayoutParams loadingParams(int width, int height,
                                                    int left, int top, int bottom) {
        return loadingParams(width, height, left, top, bottom, 0);
    }

    private LinearLayout.LayoutParams loadingParams(int width, int height,
                                                    int left, int top, int bottom, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height, weight);
        params.setMargins(dp(left), dp(top), 0, dp(bottom));
        return params;
    }

    private MaterialButton iconButton(int icon) {
        MaterialButton button = new MaterialButton(this);
        button.setText("");
        button.setIconResource(icon);
        button.setIconSize(dp(24));
        button.setIconTint(ColorStateList.valueOf(INK));
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setRippleColor(ColorStateList.valueOf(Color.argb(24, 20, 105, 245)));
        button.setCornerRadius(dp(24));
        button.setElevation(0f);
        button.setStateListAnimator(null);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setIconPadding(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        return button;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private void loadDetail() {
        executor.execute(() -> {
            try {
                JSONObject subject = new JSONObject(get(BASE + "/v0/subjects/" + subjectId));
                int aired = 0;
                try {
                    aired = loadEpisodeMetadata();
                } catch (Exception ignored) {
                    // The subject page remains usable if episode metadata is temporarily unavailable.
                }
                if (aired > 0) subject.put("available_episodes", aired);
                runOnUiThread(() -> render(subject));
            } catch (Exception exception) {
                runOnUiThread(this::renderFallback);
            }
        });
    }

    private void renderFallback() {
        JSONObject fallback = new JSONObject();
        try {
            fallback.put("name_cn", subjectName);
            fallback.put("name", subjectName);
            fallback.put("summary", "暂无条目简介。可选择集数后使用最快可用线路播放。");
            fallback.put("total_episodes", 12);
            JSONObject images = new JSONObject();
            images.put("large", subjectCover);
            fallback.put("images", images);
        } catch (Exception ignored) {
        }
        render(fallback);
    }

    private void render(JSONObject subject) {
        content.removeAllViews();
        String chineseName = subject.optString("name_cn").trim();
        if (chineseName.isEmpty()) chineseName = subject.optString("name", subjectName);
        subjectName = chineseName;
        String original = subject.optString("name");
        JSONObject images = subject.optJSONObject("images");
        if (images != null) {
            subjectCover = proxyImage(images.optString("large", images.optString("common", subjectCover)));
        }
        totalEpisodes = subject.optInt("total_episodes", 0);
        availableEpisodes = subject.optInt("available_episodes", 0);
        if (availableEpisodes <= 0) availableEpisodes = Math.min(subject.optInt("eps", 1), 1);
        if (totalEpisodes <= 0) totalEpisodes = availableEpisodes;
        if (totalEpisodes <= 0) totalEpisodes = 12;
        if (availableEpisodes <= 0) availableEpisodes = Math.min(1, totalEpisodes);
        if (totalEpisodes < availableEpisodes) totalEpisodes = availableEpisodes;
        selectedEpisode = Math.min(selectedEpisode, availableEpisodes);
        selectedEpisodeRange = (selectedEpisode - 1) / EPISODES_PER_RANGE;

        LinearLayout hero = new LinearLayout(this);
        hero.setGravity(Gravity.TOP);
        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(Color.rgb(235, 238, 243));
        cover.setClipToOutline(true);
        cover.setBackground(rounded(Color.rgb(235, 238, 243), 10, 0, 0));
        hero.addView(cover, new LinearLayout.LayoutParams(dp(146), dp(218)));
        if (!subjectCover.isBlank()) Picasso.get().load(subjectCover).fit().centerCrop().into(cover);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setPadding(dp(18), dp(12), 0, 0);
        TextView name = text(chineseName, 21, INK, true);
        name.setMaxLines(3);
        meta.addView(name);
        if (!original.isBlank() && !original.equals(chineseName)) {
            TextView originalView = text(original, 13, MUTED, false);
            originalView.setMaxLines(2);
            meta.addView(originalView, margins(0, 10, 0));
        }
        JSONObject rating = subject.optJSONObject("rating");
        double score = rating == null ? 0 : rating.optDouble("score", 0);
        meta.addView(text(score > 0 ? String.format(Locale.CHINA, "★ %.1f · Bangumi", score)
                : "Bangumi 条目", 15, BLUE, false), margins(0, 18, 0));
        meta.addView(text("已更新 " + availableEpisodes + " / " + totalEpisodes + " 话",
                14, MUTED, false), margins(0, 17, 0));
        String platform = subject.optString("platform", "动画");
        meta.addView(text(platform.isBlank() ? "动画" : platform, 14, MUTED, false), margins(0, 12, 0));
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        meta.setMinimumHeight(dp(218));
        hero.addView(meta, metaParams);
        content.addView(hero);
        content.addView(divider(), margins(0, 20, 20));

        content.addView(text("简介", 21, INK, true));
        TextView summary = text(subject.optString("summary", "暂无简介"), 15, INK, false);
        summary.setLineSpacing(dp(6), 1f);
        summary.setMaxLines(5);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(summary, margins(0, 12, 12));
        MaterialButton expand = new MaterialButton(this);
        expand.setText("展开简介");
        expand.setTextSize(13);
        expand.setTextColor(BLUE);
        expand.setAllCaps(false);
        expand.setIconResource(R.drawable.ic_expand_more_24);
        expand.setIconTint(ColorStateList.valueOf(BLUE));
        expand.setIconSize(dp(18));
        expand.setIconPadding(dp(4));
        expand.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_END);
        expand.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(244, 248, 255)));
        expand.setRippleColor(ColorStateList.valueOf(Color.argb(24, 20, 105, 245)));
        expand.setCornerRadius(dp(18));
        expand.setInsetTop(0);
        expand.setInsetBottom(0);
        expand.setMinHeight(0);
        expand.setMinWidth(0);
        expand.setStateListAnimator(null);
        expand.setTag(false);
        expand.setOnClickListener(v -> {
            boolean expanded = Boolean.TRUE.equals(expand.getTag());
            if (expanded) {
                summary.setMaxLines(5);
                summary.setEllipsize(TextUtils.TruncateAt.END);
                expand.setText("展开简介");
                expand.setIconResource(R.drawable.ic_expand_more_24);
            } else {
                summary.setMaxLines(Integer.MAX_VALUE);
                summary.setEllipsize(null);
                expand.setText("收起简介");
                expand.setIconResource(R.drawable.ic_expand_less_24);
            }
            expand.setTag(!expanded);
        });
        LinearLayout expandHolder = new LinearLayout(this);
        expandHolder.setGravity(Gravity.CENTER);
        expandHolder.addView(expand, new LinearLayout.LayoutParams(dp(118), dp(38)));
        content.addView(expandHolder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        summary.post(() -> {
            if (summary.getLayout() == null || summary.getLineCount() < 5
                    || summary.getLayout().getEllipsisCount(4) == 0) {
                expandHolder.setVisibility(View.GONE);
            }
        });
        content.addView(divider(), margins(0, 14, 18));

        LinearLayout episodeHeader = new LinearLayout(this);
        episodeHeader.setGravity(Gravity.CENTER_VERTICAL);
        episodeHeader.addView(text("选集", 21, INK, true), new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView source = text("点击集数直接播放", 14, BLUE, true);
        source.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        episodeHeader.addView(source, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        content.addView(episodeHeader);
        buildEpisodeGrid();
        updateFavoriteIcon();
    }

    private void buildEpisodeGrid() {
        LinearLayout selector = new LinearLayout(this);
        selector.setOrientation(LinearLayout.VERTICAL);

        if (availableEpisodes > EPISODES_PER_RANGE * RANGES_PER_GROUP) {
            episodeGroupScroll = new HorizontalScrollView(this);
            episodeGroupScroll.setHorizontalScrollBarEnabled(false);
            episodeGroupRow = new LinearLayout(this);
            episodeGroupRow.setPadding(0, dp(4), 0, 0);
            episodeGroupScroll.addView(episodeGroupRow);
            selector.addView(episodeGroupScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        }

        if (availableEpisodes > EPISODES_PER_RANGE) {
            episodeRangeScroll = new HorizontalScrollView(this);
            episodeRangeScroll.setHorizontalScrollBarEnabled(false);
            episodeRangeRow = new LinearLayout(this);
            episodeRangeRow.setPadding(0, dp(4), 0, dp(8));
            episodeRangeScroll.addView(episodeRangeRow);
            selector.addView(episodeRangeScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        }

        episodeGrid = new LinearLayout(this);
        episodeGrid.setOrientation(LinearLayout.VERTICAL);
        selector.addView(episodeGrid);
        content.addView(selector);
        renderEpisodeSelector();
    }

    private void renderEpisodeSelector() {
        renderEpisodeGroups();
        renderEpisodeRanges();
        episodeGrid.removeAllViews();
        int first = selectedEpisodeRange * EPISODES_PER_RANGE + 1;
        int last = Math.min(availableEpisodes, first + EPISODES_PER_RANGE - 1);
        LinearLayout row = null;
        for (int value = first; value <= last; value++) {
            if ((value - first) % 2 == 0) {
                row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER);
                episodeGrid.addView(row, margins(0, 4, 0));
            }
            Button button = new Button(this);
            String title = episodeTitle(value);
            button.setText(title.isBlank() ? "第 " + value + " 集"
                    : "第 " + value + " 集\n" + title);
            button.setTextSize(title.isBlank() ? 15 : 13);
            button.setAllCaps(false);
            button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            button.setMaxLines(2);
            button.setEllipsize(TextUtils.TruncateAt.END);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setPadding(dp(14), dp(6), dp(12), dp(6));
            button.setStateListAnimator(null);
            button.setTag(value);
            styleEpisode(button, value == selectedEpisode);
            button.setOnClickListener(v -> {
                selectedEpisode = (int) v.getTag();
                resolveSelectedEpisode();
            });
            row.addView(button, episodeCellParams());
        }
        if (row != null) {
            while (row.getChildCount() < 2) {
                row.addView(new View(this), episodeCellParams());
            }
        }
    }

    private void renderEpisodeRanges() {
        if (episodeRangeRow == null) return;
        episodeRangeRow.removeAllViews();
        int ranges = (availableEpisodes + EPISODES_PER_RANGE - 1) / EPISODES_PER_RANGE;
        int group = selectedEpisodeRange / RANGES_PER_GROUP;
        int firstRange = group * RANGES_PER_GROUP;
        int lastRange = Math.min(ranges, firstRange + RANGES_PER_GROUP);
        for (int range = firstRange; range < lastRange; range++) {
            int first = range * EPISODES_PER_RANGE + 1;
            int last = Math.min(availableEpisodes, first + EPISODES_PER_RANGE - 1);
            Button button = new Button(this);
            button.setText(first + "–" + last);
            button.setTextSize(13);
            button.setAllCaps(false);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setPadding(dp(14), 0, dp(14), 0);
            button.setStateListAnimator(null);
            boolean selected = range == selectedEpisodeRange;
            button.setTextColor(selected ? Color.WHITE : MUTED);
            button.setBackground(rounded(selected ? BLUE : Color.TRANSPARENT,
                    18, selected ? BLUE : LINE, 1));
            int targetRange = range;
            button.setOnClickListener(v -> {
                selectedEpisodeRange = targetRange;
                renderEpisodeSelector();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            params.setMargins(0, 0, dp(8), 0);
            episodeRangeRow.addView(button, params);
        }
        scrollToSelected(episodeRangeScroll, episodeRangeRow,
                selectedEpisodeRange - firstRange);
    }

    private void renderEpisodeGroups() {
        if (episodeGroupRow == null) return;
        episodeGroupRow.removeAllViews();
        int groupSize = EPISODES_PER_RANGE * RANGES_PER_GROUP;
        int groups = (availableEpisodes + groupSize - 1) / groupSize;
        int selectedGroup = selectedEpisodeRange / RANGES_PER_GROUP;
        for (int group = 0; group < groups; group++) {
            int first = group * groupSize + 1;
            int last = Math.min(availableEpisodes, first + groupSize - 1);
            Button button = new Button(this);
            button.setText(first + "–" + last + " 集");
            button.setTextSize(13);
            button.setAllCaps(false);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setPadding(dp(14), 0, dp(14), 0);
            button.setStateListAnimator(null);
            boolean selected = group == selectedGroup;
            button.setTextColor(selected ? BLUE : MUTED);
            button.setBackground(rounded(Color.TRANSPARENT, 18,
                    selected ? BLUE : LINE, 1));
            int targetGroup = group;
            button.setOnClickListener(v -> {
                selectedEpisodeRange = targetGroup * RANGES_PER_GROUP;
                renderEpisodeSelector();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            params.setMargins(0, 0, dp(8), 0);
            episodeGroupRow.addView(button, params);
        }
        scrollToSelected(episodeGroupScroll, episodeGroupRow, selectedGroup);
    }

    private void scrollToSelected(HorizontalScrollView scroll, LinearLayout row, int index) {
        if (scroll == null || row == null || index < 0) return;
        scroll.post(() -> {
            if (index < row.getChildCount()) {
                View child = row.getChildAt(index);
                scroll.smoothScrollTo(Math.max(0, child.getLeft() - dp(12)), 0);
            }
        });
    }

    private LinearLayout.LayoutParams episodeCellParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(72), 1);
        params.setMargins(dp(4), dp(3), dp(4), dp(3));
        return params;
    }

    private void styleEpisode(Button button, boolean selected) {
        button.setTextColor(selected ? Color.WHITE : INK);
        button.setBackground(rounded(selected ? BLUE : Color.TRANSPARENT, 9,
                selected ? BLUE : LINE, 1));
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(LINE);
        return line;
    }

    private LinearLayout.LayoutParams margins(int left, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(left), dp(top), 0, dp(bottom));
        return params;
    }

    private void resolveSelectedEpisode() {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("subject_name", subjectName);
        intent.putExtra("subject_cover", subjectCover);
        intent.putExtra("bangumi_id", subjectId);
        intent.putExtra("episode", selectedEpisode);
        intent.putExtra("available_episodes", availableEpisodes);
        intent.putStringArrayListExtra("episode_titles", new ArrayList<>(episodeTitles));
        startActivity(intent);
    }

    private int loadEpisodeMetadata() throws Exception {
        LocalDate today = LocalDate.now();
        int aired = 0;
        int offset = 0;
        int total = Integer.MAX_VALUE;
        ArrayList<String> titles = new ArrayList<>();
        while (offset < total) {
            JSONObject page = new JSONObject(get(BASE + "/v0/episodes?subject_id=" + subjectId
                    + "&type=0&limit=100&offset=" + offset));
            JSONArray episodes = page.optJSONArray("data");
            if (episodes == null || episodes.length() == 0) break;
            total = page.optInt("total", offset + episodes.length());
            for (int i = 0; i < episodes.length(); i++) {
                JSONObject item = episodes.optJSONObject(i);
                if (item == null || item.optInt("type", 0) != 0) continue;
                String airdate = item.optString("airdate");
                if (airdate.isBlank()) continue;
                try {
                    if (LocalDate.parse(airdate).isAfter(today)) continue;
                    int number = (int) Math.floor(item.optDouble("sort",
                            item.optDouble("ep", 0)));
                    if (number <= 0) continue;
                    while (titles.size() < number) titles.add("");
                    String title = item.optString("name_cn").trim();
                    if (title.isBlank()) title = item.optString("name").trim();
                    titles.set(number - 1, title);
                    aired = Math.max(aired, number);
                } catch (Exception ignored) {
                }
            }
            offset += episodes.length();
        }
        synchronized (episodeTitles) {
            episodeTitles.clear();
            episodeTitles.addAll(titles);
        }
        return aired;
    }

    private String episodeTitle(int episode) {
        int index = episode - 1;
        return index >= 0 && index < episodeTitles.size() ? episodeTitles.get(index) : "";
    }

    private void toggleFavorite() {
        boolean favorite = FavoriteStore.toggle(
                this, subjectId, subjectName, subjectCover);
        updateFavoriteIcon();
        android.widget.Toast.makeText(this,
                favorite ? "已加入收藏" : "已取消收藏",
                android.widget.Toast.LENGTH_SHORT).show();
    }

    private void updateFavoriteIcon() {
        boolean favorite = FavoriteStore.contains(this, subjectId, subjectName);
        favoriteButton.setIconResource(favorite ? R.drawable.ic_favorite_24
                : R.drawable.ic_favorite_border_24);
        favoriteButton.setIconTint(ColorStateList.valueOf(favorite ? BLUE : INK));
    }

    private String proxyImage(String url) {
        if (url == null) return "";
        return url.replaceFirst("^https?://lain\\.bgm\\.tv", BASE)
                .replaceFirst("^http://", "https://");
    }

    private String get(String url) throws IOException {
        try (Response response = client.newCall(new Request.Builder().url(url).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("HTTP " + response.code());
            return response.body().string();
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        client.dispatcher().cancelAll();
        super.onDestroy();
    }
}
