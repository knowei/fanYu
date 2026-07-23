package com.example.animeresolver;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
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
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HomeActivity extends Activity {
    private static final String BANGUMI_BASE = "https://bgm.liwen.icu";
    private static final String ANILIST_GRAPHQL = "https://graphql.anilist.co";
    private static final int BLUE = Color.rgb(25, 112, 243);
    private static final int INK = Color.rgb(22, 25, 31);
    private static final int MUTED = Color.rgb(105, 108, 115);
    private static final int LINE = Color.rgb(229, 231, 235);
    private static final int WARM_WHITE = Color.rgb(253, 252, 250);

    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private LinearLayout page;
    private Button broadcastTab;
    private Button myTab;
    private final Map<Integer, String> updateTimes = new HashMap<>();

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildShell();
        showBroadcastPage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (myTab != null && myTab.isSelected()) showMyPage();
    }

    private TextView label(String value, float size, int color, boolean bold) {
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
        root.setBackgroundColor(WARM_WHITE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(24), dp(22), dp(24));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(5), dp(8), dp(8));
        nav.setBackgroundColor(Color.WHITE);

        broadcastTab = navButton("放送", R.drawable.ic_calendar_today_24);
        myTab = navButton("我的", R.drawable.ic_person_outline_24);
        broadcastTab.setOnClickListener(v -> showBroadcastPage());
        myTab.setOnClickListener(v -> showMyPage());
        nav.addView(broadcastTab, new LinearLayout.LayoutParams(0, dp(62), 1));
        nav.addView(myTab, new LinearLayout.LayoutParams(0, dp(62), 1));
        root.addView(nav);
        SystemBars.apply(this, root, WARM_WHITE);
        setContentView(root);
    }

    private Button navButton(String text, int icon) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0);
        button.setCompoundDrawablePadding(dp(3));
        button.setPadding(0, dp(7), 0, dp(4));
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setStateListAnimator(null);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private void selectTab(boolean broadcast) {
        broadcastTab.setSelected(broadcast);
        myTab.setSelected(!broadcast);
        broadcastTab.setTextColor(broadcast ? BLUE : MUTED);
        myTab.setTextColor(broadcast ? MUTED : BLUE);
        if (broadcastTab.getCompoundDrawables()[1] != null) {
            broadcastTab.getCompoundDrawables()[1].setTint(broadcast ? BLUE : MUTED);
        }
        if (myTab.getCompoundDrawables()[1] != null) {
            myTab.getCompoundDrawables()[1].setTint(broadcast ? MUTED : BLUE);
        }
    }

    private void showBroadcastPage() {
        selectTab(true);
        page.removeAllViews();
        LocalDate today = LocalDate.now();
        page.addView(label(weekdayGreeting(today), 28, INK, true),
                matchWrap(0, 0, 0, 18));

        EditText search = new EditText(this);
        search.setHint("搜索番剧");
        search.setTextSize(15);
        search.setTextColor(INK);
        search.setHintTextColor(Color.rgb(139, 143, 151));
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_search_24, 0, 0, 0);
        search.getCompoundDrawables()[0].setTint(MUTED);
        search.setCompoundDrawablePadding(dp(10));
        search.setPadding(dp(15), 0, dp(15), 0);
        GradientDrawable searchBackground = new GradientDrawable();
        searchBackground.setColor(Color.rgb(243, 245, 248));
        searchBackground.setCornerRadius(dp(14));
        search.setBackground(searchBackground);
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String keyword = search.getText().toString().trim();
                if (!keyword.isEmpty()) {
                    InputMethodManager keyboard = (InputMethodManager)
                            getSystemService(INPUT_METHOD_SERVICE);
                    keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);
                    search.clearFocus();
                    searchSubjects(keyword);
                }
                return true;
            }
            return false;
        });
        page.addView(search, matchWrap(dp(50), 0, 0, 20));
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView broadcastTitle = label("今日放送", 22, INK, true);
        titleRow.addView(broadcastTitle,
                new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView source = label("Bangumi 索引", 13, BLUE, false);
        source.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        titleRow.addView(source, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        page.addView(buildDateRail(today, date -> {
            broadcastTitle.setText(date.equals(today) ? "今日放送"
                    : date.getMonthValue() + "月" + date.getDayOfMonth() + "日放送");
            showBroadcastLoading(list, date.equals(today) ? "正在同步今日放送…"
                    : "正在读取" + shortWeekday(date) + "的放送安排…");
            fetchCalendar(list, date, date.equals(today) ? "今日" : shortWeekday(date));
        }), matchWrap(dp(76), 0, 0, 18));
        page.addView(titleRow);
        showBroadcastLoading(list, "正在同步今日放送…");
        page.addView(list);
        fetchCalendar(list, today, "今日");
    }

    private void showBroadcastLoading(LinearLayout target, String message) {
        target.removeAllViews();
        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(0, dp(4), 0, dp(8));
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(BLUE));
        status.addView(spinner, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView label = label(message, 13, MUTED, false);
        label.setPadding(dp(10), 0, 0, 0);
        status.addView(label, new LinearLayout.LayoutParams(0, dp(32), 1));
        target.addView(status);
        for (int index = 0; index < 3; index++) {
            target.addView(broadcastSkeletonRow());
        }
    }

    private View broadcastSkeletonRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        LinearLayout markerColumn = new LinearLayout(this);
        markerColumn.setGravity(Gravity.CENTER);
        markerColumn.addView(skeletonBlock(dp(40), dp(20), 8),
                skeletonParams(dp(40), dp(20), 0, 0, 0));
        row.addView(markerColumn, new LinearLayout.LayoutParams(dp(48), dp(130)));
        View cover = skeletonBlock(dp(88), dp(126), 8);
        row.addView(cover, new LinearLayout.LayoutParams(dp(88), dp(126)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(16), 0, 0, 0);
        info.addView(skeletonBlock(0, dp(18), 8), skeletonParams(0, dp(18), 0, 0, 0, 1));
        info.addView(skeletonBlock(dp(150), dp(14), 7),
                skeletonParams(dp(150), dp(14), 0, 12, 0));
        info.addView(skeletonBlock(dp(110), dp(14), 7),
                skeletonParams(dp(110), dp(14), 0, 10, 0));
        row.addView(info, new LinearLayout.LayoutParams(0, dp(126), 1));
        return row;
    }

    private View skeletonBlock(int width, int height, int radius) {
        View block = new View(this);
        block.setBackground(roundedRect(Color.rgb(237, 240, 245), radius));
        return block;
    }

    private LinearLayout.LayoutParams skeletonParams(
            int width, int height, int left, int top, int bottom) {
        return skeletonParams(width, height, left, top, bottom, 0);
    }

    private LinearLayout.LayoutParams skeletonParams(
            int width, int height, int left, int top, int bottom, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height, weight);
        params.setMargins(dp(left), dp(top), 0, dp(bottom));
        return params;
    }

    private String weekdayGreeting(LocalDate date) {
        String[] weekdays = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        int hour = LocalTime.now(ZoneId.of("Asia/Shanghai")).getHour();
        String greeting = hour < 11 ? "早上好" : hour < 18 ? "下午好" : "晚上好";
        return weekdays[date.getDayOfWeek().getValue() - 1] + "，" + greeting;
    }

    private View buildDateRail(LocalDate today, java.util.function.Consumer<LocalDate> onSelect) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        LinearLayout rail = new LinearLayout(this);
        rail.setGravity(Gravity.CENTER);
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA);
        String[] shortDays = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            TextView day = label(shortDays[i] + "\n" + date.format(formatter), 12,
                    date.equals(today) ? BLUE : INK, date.equals(today));
            day.setGravity(Gravity.CENTER);
            day.setLineSpacing(dp(3), 1f);
            day.setTag(date);
            if (date.equals(today)) day.setBackground(roundedRect(Color.rgb(235, 243, 255), 10));
            day.setOnClickListener(v -> {
                for (int child = 0; child < rail.getChildCount(); child++) {
                    TextView item = (TextView) rail.getChildAt(child);
                    boolean selected = date.equals(item.getTag());
                    item.setTextColor(selected ? BLUE : INK);
                    item.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
                    item.setBackground(selected ? roundedRect(Color.rgb(235, 243, 255), 10) : null);
                }
                onSelect.accept(date);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(74), dp(68));
            params.setMargins(0, 0, dp(6), 0);
            rail.addView(day, params);
        }
        scroll.addView(rail);
        return scroll;
    }

    private GradientDrawable roundedRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private void fetchCalendar(LinearLayout target, LocalDate date, String badge) {
        executor.execute(() -> {
            try {
                String json = get(BANGUMI_BASE + "/calendar");
                JSONArray weekdays = new JSONArray(json);
                JSONArray items = new JSONArray();
                for (int i = 0; i < weekdays.length(); i++) {
                    JSONObject entry = weekdays.getJSONObject(i);
                    if (entry.getJSONObject("weekday").optInt("id") == date.getDayOfWeek().getValue()) {
                        items = entry.getJSONArray("items");
                        break;
                    }
                }
                List<Subject> subjects = parseLegacySubjects(items, 12);
                enrichWithAiringTimes(subjects, date);
                runOnUiThread(() -> renderSubjects(target, subjects, false, badge));
            } catch (Exception e) {
                runOnUiThread(() -> showError(target, "放送数据加载失败，点击重试", () ->
                        fetchCalendar(target, date, badge)));
            }
        });
    }

    private List<Subject> parseLegacySubjects(JSONArray array, int limit) {
        List<Subject> result = new ArrayList<>();
        for (int i = 0; i < Math.min(array.length(), limit); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            JSONObject images = item.optJSONObject("images");
            JSONObject rating = item.optJSONObject("rating");
            result.add(new Subject(
                    item.optInt("id"),
                    preferredName(item),
                    item.optString("name"),
                    subjectImage(images),
                    rating == null ? 0 : rating.optDouble("score", 0),
                    "今日"
            ));
        }
        return result;
    }

    private String preferredName(JSONObject item) {
        String chinese = item.optString("name_cn").trim();
        return chinese.isEmpty() ? item.optString("name") : chinese;
    }

    private String secureImage(String url) {
        if (url == null) return "";
        return url.replaceFirst("^https?://lain\\.bgm\\.tv", BANGUMI_BASE)
                .replaceFirst("^http://", "https://");
    }

    private String subjectImage(JSONObject images) {
        if (images == null) return "";
        String value = images.optString("large");
        if (value.isBlank()) value = images.optString("common");
        if (value.isBlank()) value = images.optString("medium");
        if (value.isBlank()) value = images.optString("small");
        return secureImage(value);
    }

    private String listImage(String url) {
        // 搜索接口的 medium/common 字段通常是 /r/800/... 或 /r/400/...。
        // 图片代理只提供普通 /pic 路径；保留大图并交给 Picasso 缩小，
        // 避免高像素密度手机把 m 尺寸封面放大后变模糊。
        return secureImage(url)
                .replaceFirst("/r/\\d+(/pic/cover/)", "$1");
    }

    private void renderSubjects(LinearLayout target, List<Subject> subjects, boolean search) {
        renderSubjects(target, subjects, search, search ? "条目" : "今日");
    }

    private void renderSubjects(LinearLayout target, List<Subject> subjects, boolean search, String badge) {
        target.removeAllViews();
        if (subjects.isEmpty()) {
            target.addView(label("暂时没有找到番剧", 15, MUTED, false), matchWrap(dp(96), 0, 0, 0));
            return;
        }
        for (int i = 0; i < subjects.size(); i++) {
            Subject subject = subjects.get(i);
            target.addView(subjectRow(subject, search ? "条目" : badge));
            View divider = new View(this);
            divider.setBackgroundColor(LINE);
            target.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        }
    }

    private String shortWeekday(LocalDate date) {
        String[] values = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return values[date.getDayOfWeek().getValue() - 1];
    }

    private View subjectRow(Subject subject, String marker) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(12), 0, dp(12));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOnClickListener(v -> openSubject(subject));

        TextView time = label(marker, 13, BLUE, true);
        time.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        row.addView(time, new LinearLayout.LayoutParams(dp(48), dp(130)));

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(Color.rgb(238, 240, 244));
        row.addView(cover, new LinearLayout.LayoutParams(dp(88), dp(126)));
        if (!subject.image.isBlank()) {
            Picasso.get().load(listImage(subject.image)).fit().centerCrop().into(cover);
        }

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(16), dp(5), 0, 0);
        TextView title = label(subject.name, 17, INK, true);
        title.setMaxLines(2);
        info.addView(title);
        if (!subject.original.equals(subject.name)) {
            TextView original = label(subject.original, 12, MUTED, false);
            original.setMaxLines(1);
            info.addView(original, matchWrap(0, 0, 8, 0));
        }
        String score = subject.score > 0 ? String.format(Locale.CHINA, "★ %.1f · Bangumi", subject.score)
                : "Bangumi 条目";
        info.addView(label(score, 13, BLUE, false), matchWrap(0, 0, 9, 0));
        String update = updateTimes.get(subject.id);
        if (update != null && !update.isBlank()) {
            info.addView(label(update, 12, MUTED, false), matchWrap(0, 0, 6, 0));
        }
        row.addView(info, new LinearLayout.LayoutParams(0, dp(130), 1));
        return row;
    }

    private void enrichWithAiringTimes(List<Subject> subjects, LocalDate date) {
        if (subjects.isEmpty()) return;
        try {
            long start = date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond();
            long end = date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond();
            JSONObject variables = new JSONObject();
            variables.put("start", start);
            variables.put("end", end);
            JSONObject body = new JSONObject();
            body.put("query", "query($start:Int,$end:Int){Page(page:1,perPage:100){airingSchedules(airingAt_greater:$start,airingAt_lesser:$end,sort:TIME){airingAt episode media{title{native romaji english}}}}}");
            body.put("variables", variables);
            Request request = new Request.Builder()
                    .url(ANILIST_GRAPHQL)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "AnimeSchedule/0.1")
                    .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                    .build();
            JSONObject root = new JSONObject(execute(request));
            JSONObject data = root.optJSONObject("data");
            JSONObject page = data == null ? null : data.optJSONObject("Page");
            JSONArray schedules = page == null ? null : page.optJSONArray("airingSchedules");
            if (schedules == null) return;
            updateTimes.clear();
            for (int i = 0; i < schedules.length(); i++) {
                JSONObject schedule = schedules.optJSONObject(i);
                if (schedule == null) continue;
                JSONObject media = schedule.optJSONObject("media");
                JSONObject title = media == null ? null : media.optJSONObject("title");
                Subject matched = findMatchingSubject(subjects, title);
                if (matched == null) continue;
                LocalTime time = Instant.ofEpochSecond(schedule.optLong("airingAt"))
                        .atZone(ZoneId.of("Asia/Shanghai")).toLocalTime();
                updateTimes.put(matched.id, String.format(Locale.CHINA, "%02d:%02d 更新 · 第%d集",
                        time.getHour(), time.getMinute(), schedule.optInt("episode")));
            }
        } catch (Exception ignored) {
            // AniList is supplemental; Bangumi calendar remains usable if it is unavailable.
        }
    }

    private Subject findMatchingSubject(List<Subject> subjects, JSONObject titles) {
        if (titles == null) return null;
        String[] candidates = {titles.optString("native"), titles.optString("romaji"), titles.optString("english")};
        for (Subject subject : subjects) {
            String primary = normalizeTitle(subject.name);
            String original = normalizeTitle(subject.original);
            for (String candidate : candidates) {
                String normalized = normalizeTitle(candidate);
                if (!normalized.isEmpty() && (normalized.equals(primary) || normalized.equals(original))) {
                    return subject;
                }
            }
        }
        return null;
    }

    private String normalizeTitle(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\p{IsHan}ぁ-んァ-ン]", "");
    }

    private void searchSubjects(String keyword) {
        page.removeAllViews();
        LinearLayout searchHeader = new LinearLayout(this);
        searchHeader.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton back = new MaterialButton(this);
        back.setText("");
        back.setIconResource(R.drawable.ic_arrow_back_24);
        back.setIconTint(ColorStateList.valueOf(INK));
        back.setIconSize(dp(24));
        back.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        back.setIconPadding(0);
        back.setInsetTop(0);
        back.setInsetBottom(0);
        back.setMinWidth(0);
        back.setMinHeight(0);
        back.setPadding(dp(12), 0, dp(12), 0);
        back.setContentDescription("返回放送");
        back.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setRippleColor(ColorStateList.valueOf(Color.argb(24, 20, 105, 245)));
        back.setCornerRadius(dp(24));
        back.setElevation(0f);
        back.setStateListAnimator(null);
        back.setOnClickListener(v -> showBroadcastPage());
        searchHeader.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView searchTitle = label("搜索结果", 21, INK, true);
        searchTitle.setPadding(dp(4), 0, 0, 0);
        searchHeader.addView(searchTitle, new LinearLayout.LayoutParams(0, dp(48), 1));
        page.addView(searchHeader, matchWrap(dp(48), 0, 0, 6));
        page.addView(label("“" + keyword + "”", 14, MUTED, false),
                matchWrap(0, dp(52), 0, 14));
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.addView(new ProgressBar(this), matchWrap(dp(72), 0, 0, 0));
        page.addView(results);

        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("keyword", keyword);
                JSONObject filter = new JSONObject();
                filter.put("type", new JSONArray().put(2));
                body.put("filter", filter);
                Request request = new Request.Builder()
                        .url(BANGUMI_BASE + "/v0/search/subjects?limit=20&offset=0")
                        .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                        .build();
                String response = execute(request);
                JSONArray data = new JSONObject(response).optJSONArray("data");
                List<Subject> subjects = parseV0Subjects(data == null ? new JSONArray() : data);
                runOnUiThread(() -> renderSubjects(results, subjects, true));
            } catch (Exception e) {
                runOnUiThread(() -> showError(results, "搜索失败，点击重试", () -> searchSubjects(keyword)));
            }
        });
    }

    private List<Subject> parseV0Subjects(JSONArray array) {
        List<Subject> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            JSONObject images = item.optJSONObject("images");
            JSONObject rating = item.optJSONObject("rating");
            result.add(new Subject(item.optInt("id"), preferredName(item), item.optString("name"),
                    subjectImage(images),
                    rating == null ? 0 : rating.optDouble("score", 0), "条目"));
        }
        return result;
    }

    private void openSubject(Subject subject) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("subject_name", subject.name);
        intent.putExtra("subject_cover", subject.image);
        intent.putExtra("bangumi_id", subject.id);
        startActivity(intent);
    }

    private void showMyPage() {
        selectTab(false);
        page.removeAllViews();
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("我的", 28, INK, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView settings = label("设置", 14, MUTED, false);
        settings.setGravity(Gravity.CENTER);
        header.addView(settings, new LinearLayout.LayoutParams(dp(54), dp(48)));
        page.addView(header, matchWrap(dp(48), 0, 0, 22));
        android.content.SharedPreferences prefs = getSharedPreferences("watching", MODE_PRIVATE);
        JSONArray history = WatchHistoryStore.read(this);
        JSONObject latest = history.optJSONObject(0);
        String lastName = latest == null ? prefs.getString("name", "") : latest.optString("name");
        String lastCover = latest == null ? prefs.getString("cover", "") : latest.optString("cover");
        int lastEpisode = latest == null ? prefs.getInt("episode", 1) : latest.optInt("episode", 1);
        long lastPosition = latest == null ? 0L : latest.optLong("position", 0L);
        long lastDuration = latest == null ? 0L : latest.optLong("duration", 0L);
        String lastVideoUrl = latest == null ? prefs.getString("videoUrl", "")
                : latest.optString("videoUrl");
        int lastBangumiId = latest == null ? prefs.getInt("bangumiId", 0)
                : latest.optInt("bangumiId", 0);

        page.addView(mySectionTitle("继续观看", ""));
        if (lastName.isEmpty()) {
            TextView empty = label("播放过的番剧会出现在这里", 14, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(roundedRect(Color.rgb(245, 248, 253), 12));
            page.addView(empty, matchWrap(dp(86), 0, 10, 28));
        } else {
            page.addView(recentRow(lastName, lastCover, lastEpisode,
                    lastPosition, lastDuration, lastVideoUrl, lastBangumiId),
                    matchWrap(dp(124), 0, 10, 26));
        }

        try {
            JSONArray favorites = FavoriteStore.read(this);
            page.addView(mySectionTitle("收藏", favorites.length() == 0 ? "" : favorites.length() + " 部"));
            if (favorites.length() == 0) {
                TextView emptyFavorites = label("收藏的番剧会在这里组成片单", 14, MUTED, false);
                emptyFavorites.setGravity(Gravity.CENTER_VERTICAL);
                page.addView(emptyFavorites, matchWrap(dp(70), 0, 8, 22));
            } else {
                page.addView(favoritesRow(favorites), matchWrap(dp(160), 0, 10, 26));
            }
        } catch (Exception ignored) {
        }
        page.addView(mySectionTitle("更多", ""), matchWrap(0, 0, 0, 4));
        View historyRow = settingsRow("播放记录", R.drawable.ic_history_24);
        historyRow.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        page.addView(historyRow);
        View sourceRow = settingsRow("视频源管理", R.drawable.ic_search_24);
        sourceRow.setOnClickListener(v -> startActivity(new Intent(this, SourceManagementActivity.class)));
        page.addView(sourceRow);
        page.addView(settingsRow("数据与缓存", R.drawable.ic_folder_outline_24));
    }

    private View recentRow(
            String name,
            String coverUrl,
            int episode,
            long position,
            long duration,
            String videoUrl,
            int bangumiId
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(roundedRect(Color.rgb(244, 248, 255), 14));
        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(Color.rgb(238, 240, 244));
        row.addView(cover, new LinearLayout.LayoutParams(dp(72), dp(98)));
        if (!coverUrl.isBlank()) Picasso.get().load(listImage(coverUrl)).fit().centerCrop().into(cover);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(14), 0, 0, 0);
        TextView title = label(name, 16, INK, true);
        title.setMaxLines(2);
        info.addView(title, matchWrap(dp(46), 0, 0, 0));
        String metadata = "第 " + episode + " 集";
        if (position > 0) metadata += " · 看到 " + formatPlaybackTime(position);
        info.addView(label(metadata, 13, MUTED, false), matchWrap(dp(24), 0, 0, 0));
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgress(duration > 0 ? (int) Math.min(1000, position * 1000 / duration) : 0);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));
        info.addView(progress, matchWrap(dp(3), 0, 4, 4));
        TextView resume = label("继续观看", 14, BLUE, true);
        info.addView(resume, matchWrap(dp(24), 0, 0, 0));
        row.addView(info, new LinearLayout.LayoutParams(0, dp(98), 1));
        row.setOnClickListener(v -> openHistoryItem(name, coverUrl, episode,
                position, videoUrl, bangumiId));
        return row;
    }

    private View favoritesRow(JSONArray favorites) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.TOP);
        for (int index = 0; index < favorites.length(); index++) {
            JSONObject item = favorites.optJSONObject(index);
            if (item == null) continue;
            String name = item.optString("name");
            String coverUrl = item.optString("cover");
            int subjectId = item.optInt("id", 0);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            if (index > 0) card.setPadding(dp(12), 0, 0, 0);
            ImageView cover = new ImageView(this);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackgroundColor(Color.rgb(238, 240, 244));
            card.addView(cover, new LinearLayout.LayoutParams(
                    dp(90), dp(120)));
            if (!coverUrl.isBlank()) Picasso.get().load(listImage(coverUrl)).fit().centerCrop().into(cover);
            TextView title = label(name, 13, INK, true);
            title.setMaxLines(1);
            card.addView(title, new LinearLayout.LayoutParams(dp(90), dp(30)));
            card.setOnClickListener(v -> openSubject(
                    new Subject(subjectId, name, name, coverUrl, 0, "收藏")));
            row.addView(card, new LinearLayout.LayoutParams(dp(90), dp(150)));
        }
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return scroll;
    }

    private View mySectionTitle(String text, String trailing) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label(text, 20, INK, true);
        row.addView(title, new LinearLayout.LayoutParams(0, dp(32), 1));
        if (!trailing.isEmpty()) {
            TextView count = label(trailing, 13, MUTED, false);
            count.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            row.addView(count, new LinearLayout.LayoutParams(dp(48), dp(32)));
        }
        return row;
    }

    private View settingsRow(String text, int icon) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, 0);
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setColorFilter(BLUE);
        row.addView(image, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView title = label(text, 16, INK, false);
        title.setPadding(dp(14), 0, 0, 0);
        row.addView(title, new LinearLayout.LayoutParams(0, dp(58), 1));
        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_chevron_right_24);
        arrow.setColorFilter(MUTED);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(58)));
        return row;
    }

    private void openHistoryItem(
            String name,
            String cover,
            int episode,
            long position,
            String videoUrl,
            int bangumiId
    ) {
        Intent intent;
        if (videoUrl != null && !videoUrl.isBlank()) {
            intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("video_url", videoUrl);
            intent.putExtra("resume_position", position);
        } else {
            intent = new Intent(this, MainActivity.class);
            intent.putExtra("auto_resolve", true);
        }
        intent.putExtra("subject_name", name);
        intent.putExtra("subject_cover", cover);
        intent.putExtra("episode", episode);
        intent.putExtra("bangumi_id", bangumiId);
        startActivity(intent);
    }

    private String formatPlaybackTime(long milliseconds) {
        long seconds = Math.max(0L, milliseconds / 1000L);
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void showError(LinearLayout target, String message, Runnable retry) {
        target.removeAllViews();
        TextView error = label(message, 15, MUTED, false);
        error.setGravity(Gravity.CENTER);
        error.setOnClickListener(v -> retry.run());
        target.addView(error, matchWrap(dp(100), 0, 0, 0));
    }

    private LinearLayout.LayoutParams matchWrap(int height, int left, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height == 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : height);
        params.setMargins(left, top, 0, bottom);
        return params;
    }

    private String get(String url) throws IOException {
        return execute(new Request.Builder().url(url).get().build());
    }

    private String execute(Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        client.dispatcher().cancelAll();
        super.onDestroy();
    }

    private record Subject(int id, String name, String original, String image, double score, String marker) {}
}
