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
        ProgressBar progress = new ProgressBar(this);
        content.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(100)));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
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
                int aired = loadAiredEpisodeCount();
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
        availableEpisodes = Math.min(availableEpisodes, totalEpisodes);
        selectedEpisode = Math.min(selectedEpisode, availableEpisodes);

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
        content.addView(summary, margins(0, 12, 10));
        TextView expand = text("展开⌄", 14, BLUE, true);
        expand.setGravity(Gravity.CENTER);
        expand.setOnClickListener(v -> {
            summary.setMaxLines(Integer.MAX_VALUE);
            expand.setVisibility(View.GONE);
        });
        content.addView(expand, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
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
        int columns = 6;
        LinearLayout row = null;
        for (int episode = 1; episode <= availableEpisodes; episode++) {
            if ((episode - 1) % columns == 0) {
                row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER);
                content.addView(row, margins(0, 6, 0));
            }
            Button button = new Button(this);
            button.setText(String.valueOf(episode));
            button.setTextSize(16);
            button.setAllCaps(false);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setPadding(0, 0, 0, 0);
            button.setStateListAnimator(null);
            button.setTag(episode);
            styleEpisode(button, false);
            button.setOnClickListener(v -> {
                selectedEpisode = (int) v.getTag();
                resolveSelectedEpisode();
            });
            row.addView(button, episodeCellParams());
        }
        if (row != null) {
            while (row.getChildCount() < columns) {
                row.addView(new View(this), episodeCellParams());
            }
        }
    }

    private LinearLayout.LayoutParams episodeCellParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(58), 1);
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
        startActivity(intent);
    }

    private int loadAiredEpisodeCount() throws Exception {
        JSONObject page = new JSONObject(get(BASE + "/v0/episodes?subject_id=" + subjectId
                + "&type=0&limit=100&offset=0"));
        JSONArray episodes = page.optJSONArray("data");
        if (episodes == null) return 0;
        LocalDate today = LocalDate.now();
        int aired = 0;
        for (int i = 0; i < episodes.length(); i++) {
            JSONObject item = episodes.optJSONObject(i);
            if (item == null || item.optInt("type", 0) != 0) continue;
            String airdate = item.optString("airdate");
            if (airdate.isBlank()) continue;
            try {
                if (!LocalDate.parse(airdate).isAfter(today)) {
                    aired = Math.max(aired, (int) Math.floor(item.optDouble("sort", 0)));
                }
            } catch (Exception ignored) {
            }
        }
        return aired;
    }

    private void toggleFavorite() {
        android.content.SharedPreferences prefs = getSharedPreferences("watching", MODE_PRIVATE);
        try {
            JSONArray old = new JSONArray(prefs.getString("favorites", "[]"));
            JSONArray updated = new JSONArray();
            boolean removed = false;
            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.optJSONObject(i);
                if (item != null && subjectName.equals(item.optString("name"))) removed = true;
                else if (item != null) updated.put(item);
            }
            if (!removed) {
                JSONObject item = new JSONObject();
                item.put("id", subjectId);
                item.put("name", subjectName);
                item.put("cover", subjectCover);
                updated.put(item);
            }
            prefs.edit().putString("favorites", updated.toString()).apply();
            updateFavoriteIcon();
        } catch (Exception ignored) {
        }
    }

    private void updateFavoriteIcon() {
        boolean favorite = false;
        try {
            JSONArray array = new JSONArray(getSharedPreferences("watching", MODE_PRIVATE)
                    .getString("favorites", "[]"));
            for (int i = 0; i < array.length(); i++) {
                if (subjectName.equals(array.getJSONObject(i).optString("name"))) favorite = true;
            }
        } catch (Exception ignored) {
        }
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
