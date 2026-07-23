package com.example.animeresolver;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.squareup.picasso.Picasso;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class PlayerActivity extends Activity {
    private static final int REQUEST_SITE_VERIFICATION = 401;
    private static final int EPISODES_PER_RANGE = 12;
    private static final int RANGES_PER_GROUP = 10;
    public static final String ACTION_SOURCE_RESULT =
            "com.example.animeresolver.SOURCE_RESULT";
    public static final String SOURCE_LOADING = "loading";
    public static final String SOURCE_READY = "ready";
    public static final String SOURCE_FAILED = "failed";
    public static final String SOURCE_REMOVED = "removed";
    private static final Map<Integer, LinkedHashMap<String, String>> SOURCE_CACHE =
            new HashMap<>();
    private static final Map<Integer, LinkedHashMap<String, SourceState>> SOURCE_STATE_CACHE =
            new HashMap<>();

    private record SourceState(String status, String url, String error, String siteUrl) {}

    public static synchronized void beginSourceResolution(
            int episode, java.util.Map<String, String> sourceSites) {
        LinkedHashMap<String, SourceState> states = new LinkedHashMap<>();
        for (Map.Entry<String, String> source : sourceSites.entrySet()) {
            states.put(source.getKey(), new SourceState(
                    SOURCE_LOADING, "", "", source.getValue()));
        }
        SOURCE_STATE_CACHE.put(episode, states);
        SOURCE_CACHE.remove(episode);
    }

    public static synchronized void cacheSourceState(
            int episode, String name, String status, String url, String error, String siteUrl) {
        if (SOURCE_REMOVED.equals(status)) {
            LinkedHashMap<String, SourceState> states = SOURCE_STATE_CACHE.get(episode);
            if (states != null) states.remove(name);
            LinkedHashMap<String, String> sources = SOURCE_CACHE.get(episode);
            if (sources != null) sources.remove(name);
            return;
        }
        SourceState previous = SOURCE_STATE_CACHE
                .getOrDefault(episode, new LinkedHashMap<>()).get(name);
        String resolvedSiteUrl = siteUrl == null || siteUrl.isBlank()
                ? previous == null ? "" : previous.siteUrl : siteUrl;
        SOURCE_STATE_CACHE.computeIfAbsent(episode, ignored -> new LinkedHashMap<>())
                .put(name, new SourceState(status, url == null ? "" : url,
                        error == null ? "" : error, resolvedSiteUrl));
        if (SOURCE_READY.equals(status) && url != null && !url.isBlank()) {
            cacheResolvedSource(episode, name, url);
        }
    }

    public static synchronized void cacheResolvedSource(int episode, String name, String url) {
        SOURCE_CACHE.computeIfAbsent(episode, ignored -> new LinkedHashMap<>()).put(name, url);
    }

    private static synchronized LinkedHashMap<String, String> cachedSources(int episode) {
        return new LinkedHashMap<>(SOURCE_CACHE.getOrDefault(episode, new LinkedHashMap<>()));
    }

    private static synchronized LinkedHashMap<String, SourceState> cachedSourceStates(int episode) {
        return new LinkedHashMap<>(SOURCE_STATE_CACHE.getOrDefault(
                episode, new LinkedHashMap<>()));
    }

    private static synchronized void clearCachedSources(int episode) {
        SOURCE_CACHE.remove(episode);
    }
    private static final int BLUE = Color.rgb(20, 105, 245);
    private static final int INK = Color.rgb(21, 24, 29);
    private static final int MUTED = Color.rgb(104, 108, 116);
    private static final int LINE = Color.rgb(225, 228, 233);
    private static final int WARM = Color.rgb(253, 252, 250);

    private ExoPlayer player;
    private PlayerView playerView;
    private String videoUrl;
    private String subjectName;
    private String subjectCover;
    private int episode;
    private int bangumiId;
    private int availableEpisodes;
    private TextView currentEpisodeView;
    private TextView currentEpisodeNameView;
    private TextView sourceStatusView;
    private Button sourceButton;
    private LinearLayout episodeRow;
    private LinearLayout episodeRangeRow;
    private LinearLayout episodeGroupRow;
    private HorizontalScrollView episodeRangeScroll;
    private HorizontalScrollView episodeGroupScroll;
    private int selectedEpisodeRange;
    private ArrayList<String> episodeTitles = new ArrayList<>();
    private String initialSourceName;
    private ImageView previewImage;
    private MaterialButton playPauseButton;
    private SeekBar progressBar;
    private android.widget.FrameLayout videoFrame;
    private LinearLayout appBar;
    private ScrollView pageScroll;
    private LinearLayout rootView;
    private LinearLayout playerArea;
    private LinearLayout controlDock;
    private TextView playbackTimeView;
    private Button dockSourceButton;
    private String currentSourceName = "";
    private long resumePosition;
    private BottomSheetDialog sourceDialog;
    private LinearLayout sourceListContainer;
    private TextView sourceSummaryView;
    private ScrollView sourceScrollView;
    private LinearLayout episodePanel;
    private LinearLayout discussionPanel;
    private Button episodesTabButton;
    private Button discussionTabButton;
    private LinearLayout discussionList;
    private EditText discussionInput;
    private boolean fullscreen;
    private final Runnable hideControls = () -> {
        if (player != null && player.isPlaying() && controlDock != null) controlDock.setVisibility(View.GONE);
    };
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressUpdater = new Runnable() {
        @Override public void run() {
            if (player != null && player.getDuration() > 0 && !progressBar.isPressed()) {
                progressBar.setProgress((int) (player.getCurrentPosition() * 1000 / player.getDuration()));
                playPauseButton.setIconResource(player.isPlaying() ? R.drawable.ic_pause_24 : R.drawable.ic_play_24);
                playbackTimeView.setText(formatTime(player.getCurrentPosition()) + " / " + formatTime(player.getDuration()));
            }
            progressHandler.postDelayed(this, 500);
        }
    };
    private final Player.Listener playbackListener = new Player.Listener() {
        @Override public void onPlaybackStateChanged(int playbackState) {
            if (playbackState != Player.STATE_READY || currentSourceName.isBlank()) return;
            SourceState state = sourceStates.get(currentSourceName);
            if (state == null || state.url.isBlank()) return;
            sourceStates.put(currentSourceName,
                    new SourceState(SOURCE_READY, state.url, "", state.siteUrl));
            cacheSourceState(episode, currentSourceName,
                    SOURCE_READY, state.url, "", state.siteUrl);
            updateSourceStatus();
            renderSourcePicker();
        }

        @Override public void onPlayerError(PlaybackException error) {
            if (currentSourceName.isBlank()) return;
            SourceState state = sourceStates.get(currentSourceName);
            if (state == null) return;
            String message = error.getErrorCodeName();
            sourceStates.put(currentSourceName,
                    new SourceState(SOURCE_FAILED, state.url, message, state.siteUrl));
            cacheSourceState(episode, currentSourceName,
                    SOURCE_FAILED, state.url, message, state.siteUrl);
            updateSourceStatus();
            renderSourcePicker();
        }
    };
    private final LinkedHashMap<String, String> sources = new LinkedHashMap<>();
    private final LinkedHashMap<String, SourceState> sourceStates = new LinkedHashMap<>();
    private final BroadcastReceiver sourceReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent.getIntExtra("episode", -1) != episode) return;
            String name = intent.getStringExtra("source_name");
            String url = intent.getStringExtra("video_url");
            String status = intent.getStringExtra("source_status");
            String error = intent.getStringExtra("source_error");
            String siteUrl = intent.getStringExtra("source_site_url");
            if (name == null || name.isBlank()) return;
            if (status == null || status.isBlank()) status = SOURCE_READY;
            if (SOURCE_REMOVED.equals(status)) {
                sourceStates.remove(name);
                sources.remove(name);
                updateSourceStatus();
                renderSourcePicker();
                return;
            }
            SourceState previous = sourceStates.get(name);
            if ((siteUrl == null || siteUrl.isBlank()) && previous != null) {
                siteUrl = previous.siteUrl;
            }
            sourceStates.put(name, new SourceState(status,
                    url == null ? "" : url, error == null ? "" : error,
                    siteUrl == null ? "" : siteUrl));
            if (SOURCE_READY.equals(status) && url != null && !url.isBlank()) {
                boolean shouldAutoPlay = videoUrl == null || videoUrl.isBlank();
                sources.put(name, url);
                if (shouldAutoPlay) playUrl(name, url);
            }
            updateSourceStatus();
            renderSourcePicker();
        }
    };

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        videoUrl = getIntent().getStringExtra("video_url");
        subjectName = getIntent().getStringExtra("subject_name");
        subjectCover = getIntent().getStringExtra("subject_cover");
        episode = Math.max(1, getIntent().getIntExtra("episode", 1));
        bangumiId = getIntent().getIntExtra("bangumi_id", 0);
        availableEpisodes = Math.max(1, getIntent().getIntExtra("available_episodes", 12));
        ArrayList<String> incomingTitles = getIntent().getStringArrayListExtra("episode_titles");
        if (incomingTitles != null) episodeTitles = incomingTitles;
        selectedEpisodeRange = (episode - 1) / EPISODES_PER_RANGE;
        initialSourceName = getIntent().getStringExtra("source_name");
        resumePosition = Math.max(0L, getIntent().getLongExtra("resume_position", 0L));
        sourceStates.putAll(cachedSourceStates(episode));
        sources.putAll(cachedSources(episode));
        if (initialSourceName != null && videoUrl != null) {
            sources.put(initialSourceName, videoUrl);
            SourceState previous = sourceStates.get(initialSourceName);
            sourceStates.put(initialSourceName,
                    new SourceState(SOURCE_READY, videoUrl, "",
                            previous == null ? "" : previous.siteUrl));
            currentSourceName = initialSourceName;
        }
        if (subjectName == null) subjectName = "正在播放";
        if (subjectCover == null) subjectCover = "";
        buildUi();
        registerReceiver(sourceReceiver, new IntentFilter(ACTION_SOURCE_RESULT), RECEIVER_NOT_EXPORTED);
        initializePlayer();
        if (initialSourceName != null) sourceButton.setText(compactSourceName(initialSourceName));
        updateSourceStatus();
        progressHandler.post(progressUpdater);
        if (videoUrl == null || videoUrl.isBlank()) requestResolution(episode, false);
        getSharedPreferences("watching", MODE_PRIVATE).edit()
                .putString("name", subjectName)
                .putString("cover", subjectCover)
                .putInt("episode", episode)
                .putString("videoUrl", videoUrl == null ? "" : videoUrl)
                .putInt("bangumiId", bangumiId)
                .apply();
        saveWatchHistory();
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

    private void buildUi() {
        rootView = new LinearLayout(this);
        rootView.setOrientation(LinearLayout.VERTICAL);
        rootView.setBackgroundColor(WARM);

        appBar = new LinearLayout(this);
        appBar.setGravity(Gravity.CENTER_VERTICAL);
        appBar.setPadding(dp(8), dp(6), dp(8), 0);
        MaterialButton back = icon(R.drawable.ic_arrow_back_24);
        back.setOnClickListener(v -> finish());
        appBar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(56)));
        TextView title = text(subjectName, 19, INK, true);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(1);
        appBar.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
        MaterialButton more = icon(R.drawable.ic_arrow_back_24);
        more.setVisibility(View.INVISIBLE);
        appBar.addView(more, new LinearLayout.LayoutParams(dp(52), dp(56)));
        rootView.addView(appBar);

        playerArea = new LinearLayout(this);
        playerArea.setOrientation(LinearLayout.VERTICAL);
        playerArea.setBackgroundColor(Color.BLACK);
        videoFrame = new android.widget.FrameLayout(this);
        videoFrame.setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(this);
        playerView.setUseController(false);
        videoFrame.addView(playerView, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if ((videoUrl == null || videoUrl.isBlank()) && !subjectCover.isBlank()) {
            previewImage = new ImageView(this);
            previewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Picasso.get().load(subjectCover).fit().centerCrop().into(previewImage);
            videoFrame.addView(previewImage, new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        videoFrame.setOnClickListener(v -> toggleControls());
        addPlayerControls();
        android.widget.FrameLayout.LayoutParams controlsParams =
                new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(88), Gravity.BOTTOM);
        videoFrame.addView(controlDock, controlsParams);
        playerArea.addView(videoFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(221)));
        rootView.addView(playerArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pageScroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(30));

        LinearLayout episodeTitle = new LinearLayout(this);
        episodeTitle.setGravity(Gravity.CENTER_VERTICAL);
        currentEpisodeView = text("第" + episode + "集", 22, INK, true);
        episodeTitle.addView(currentEpisodeView, new LinearLayout.LayoutParams(0, dp(48), 1));
        sourceButton = new Button(this);
        sourceButton.setText("视频源");
        sourceButton.setTextColor(BLUE);
        sourceButton.setTextSize(14);
        sourceButton.setAllCaps(false);
        sourceButton.setBackgroundColor(Color.TRANSPARENT);
        sourceButton.setOnClickListener(v -> showSourcePicker());
        episodeTitle.addView(sourceButton, new LinearLayout.LayoutParams(dp(110), dp(48)));
        content.addView(episodeTitle);
        currentEpisodeNameView = text("", 14, MUTED, false);
        currentEpisodeNameView.setMaxLines(1);
        currentEpisodeNameView.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(currentEpisodeNameView);
        updateCurrentEpisodeLabels();
        sourceStatusView = text("正在继续加载其它视频源…", 14, MUTED, false);
        content.addView(sourceStatusView);
        content.addView(divider(), margins(0, 20, 18));

        addContentTabs(content);
        episodePanel = new LinearLayout(this);
        episodePanel.setOrientation(LinearLayout.VERTICAL);
        content.addView(episodePanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        episodePanel.addView(text("选集", 21, INK, true));
        if (availableEpisodes > EPISODES_PER_RANGE * RANGES_PER_GROUP) {
            episodeGroupScroll = new HorizontalScrollView(this);
            episodeGroupScroll.setHorizontalScrollBarEnabled(false);
            episodeGroupRow = new LinearLayout(this);
            episodeGroupRow.setPadding(0, dp(10), 0, 0);
            episodeGroupScroll.addView(episodeGroupRow);
            episodePanel.addView(episodeGroupScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        }
        if (availableEpisodes > EPISODES_PER_RANGE) {
            episodeRangeScroll = new HorizontalScrollView(this);
            episodeRangeScroll.setHorizontalScrollBarEnabled(false);
            episodeRangeRow = new LinearLayout(this);
            episodeRangeRow.setPadding(0, dp(10), 0, 0);
            episodeRangeScroll.addView(episodeRangeRow);
            episodePanel.addView(episodeRangeScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        }
        HorizontalScrollView episodeScroll = new HorizontalScrollView(this);
        episodeScroll.setHorizontalScrollBarEnabled(false);
        episodeRow = new LinearLayout(this);
        episodeRow.setPadding(0, dp(12), 0, dp(12));
        episodeScroll.addView(episodeRow);
        episodePanel.addView(episodeScroll);
        renderEpisodeSelector();

        discussionPanel = new LinearLayout(this);
        discussionPanel.setOrientation(LinearLayout.VERTICAL);
        discussionPanel.setVisibility(View.GONE);
        content.addView(discussionPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addDiscussionSection(discussionPanel);
        content.addView(divider(), margins(0, 18, 12));
        LinearLayout notice = new LinearLayout(this);
        notice.setGravity(Gravity.CENTER_VERTICAL);
        notice.addView(text("视频由第三方来源提供", 13, MUTED, false),
                new LinearLayout.LayoutParams(0, dp(50), 1));
        TextView report = text("举报", 14, BLUE, true);
        report.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        notice.addView(report, new LinearLayout.LayoutParams(dp(60), dp(50)));
        content.addView(notice);
        pageScroll.addView(content);
        rootView.addView(pageScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(rootView);
    }

    private void addContentTabs(LinearLayout content) {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(rounded(Color.rgb(242, 245, 250), 14, Color.TRANSPARENT, 0));
        episodesTabButton = contentTabButton("选集", true);
        episodesTabButton.setOnClickListener(v -> selectContentTab(true));
        discussionTabButton = contentTabButton("讨论", false);
        discussionTabButton.setOnClickListener(v -> selectContentTab(false));
        tabs.addView(episodesTabButton, new LinearLayout.LayoutParams(0, dp(38), 1));
        tabs.addView(discussionTabButton, new LinearLayout.LayoutParams(0, dp(38), 1));
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(-1, dp(46));
        tabParams.setMargins(0, 0, 0, dp(18));
        content.addView(tabs, tabParams);
    }

    private Button contentTabButton(String label, boolean selected) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        updateContentTabStyle(button, selected);
        return button;
    }

    private void selectContentTab(boolean episodes) {
        if (episodePanel == null || discussionPanel == null) return;
        episodePanel.setVisibility(episodes ? View.VISIBLE : View.GONE);
        discussionPanel.setVisibility(episodes ? View.GONE : View.VISIBLE);
        updateContentTabStyle(episodesTabButton, episodes);
        updateContentTabStyle(discussionTabButton, !episodes);
        if (!episodes) renderDiscussions();
    }

    private void updateContentTabStyle(Button button, boolean selected) {
        if (button == null) return;
        button.setTextColor(selected ? INK : MUTED);
        button.setBackground(rounded(selected ? Color.WHITE : Color.TRANSPARENT,
                10, Color.TRANSPARENT, 0));
    }

    private void addPlayerControls() {
        controlDock = new LinearLayout(this);
        controlDock.setOrientation(LinearLayout.VERTICAL);
        controlDock.setPadding(dp(12), dp(2), dp(8), dp(6));
        controlDock.setBackgroundColor(Color.argb(224, 20, 24, 29));
        progressBar = new SeekBar(this);
        progressBar.setMax(1000);
        progressBar.setProgressTintList(ColorStateList.valueOf(BLUE));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(90, 95, 102)));
        progressBar.setThumbTintList(ColorStateList.valueOf(BLUE));
        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {}
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {
                if (player != null && player.getDuration() > 0) player.seekTo(player.getDuration() * bar.getProgress() / 1000);
            }
        });
        controlDock.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        playPauseButton = videoIcon(R.drawable.ic_play_24);
        playPauseButton.setOnClickListener(v -> {
            if (player == null) return;
            if (player.isPlaying()) player.pause(); else player.play();
            showControlsTemporarily();
        });
        actions.addView(playPauseButton, new LinearLayout.LayoutParams(dp(48), dp(42)));
        playbackTimeView = text("00:00 / 00:00", 13, Color.WHITE, false);
        actions.addView(playbackTimeView, new LinearLayout.LayoutParams(dp(130), dp(42)));
        actions.addView(new View(this), new LinearLayout.LayoutParams(0, dp(42), 1));
        dockSourceButton = new Button(this);
        dockSourceButton.setText("自动");
        dockSourceButton.setTextColor(Color.WHITE);
        dockSourceButton.setTextSize(12);
        dockSourceButton.setAllCaps(false);
        dockSourceButton.setBackgroundColor(Color.TRANSPARENT);
        dockSourceButton.setOnClickListener(v -> showSourcePicker());
        actions.addView(dockSourceButton, new LinearLayout.LayoutParams(dp(72), dp(42)));
        MaterialButton fullscreenButton = videoIcon(R.drawable.ic_fullscreen_24);
        fullscreenButton.setOnClickListener(v -> toggleFullscreen());
        actions.addView(fullscreenButton, new LinearLayout.LayoutParams(dp(48), dp(42)));
        controlDock.addView(actions);
    }

    private String formatTime(long millis) {
        if (millis < 0) millis = 0;
        long seconds = millis / 1000;
        return String.format(java.util.Locale.CHINA, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void toggleControls() {
        if (controlDock.getVisibility() == View.VISIBLE) {
            controlDock.setVisibility(View.GONE);
            progressHandler.removeCallbacks(hideControls);
        } else {
            showControlsTemporarily();
        }
    }

    private void showControlsTemporarily() {
        controlDock.setVisibility(View.VISIBLE);
        progressHandler.removeCallbacks(hideControls);
        if (player != null && player.isPlaying()) progressHandler.postDelayed(hideControls, 3000);
    }

    private MaterialButton videoIcon(int drawable) {
        MaterialButton button = icon(drawable);
        button.setIconTint(ColorStateList.valueOf(Color.WHITE));
        return button;
    }

    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        appBar.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        pageScroll.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        controlDock.setVisibility(View.VISIBLE);
        setRequestedOrientation(fullscreen ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().getDecorView().setSystemUiVisibility(fullscreen
                ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                : View.SYSTEM_UI_FLAG_VISIBLE);
        LinearLayout.LayoutParams areaParams = fullscreen
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        playerArea.setLayoutParams(areaParams);
        videoFrame.setLayoutParams(fullscreen
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(221)));
    }

    private MaterialButton icon(int res) {
        MaterialButton button = new MaterialButton(this);
        button.setText("");
        button.setIconResource(res);
        button.setIconSize(dp(24));
        button.setIconTint(ColorStateList.valueOf(INK));
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

    private void addDiscussionSection(LinearLayout content) {
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(text("本集讨论", 21, INK, true), new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView local = text("仅保存在本机", 12, MUTED, false);
        local.setGravity(Gravity.CENTER);
        local.setPadding(dp(9), 0, dp(9), 0);
        local.setBackground(rounded(Color.rgb(242, 245, 250), 10, Color.TRANSPARENT, 0));
        titleRow.addView(local, new LinearLayout.LayoutParams(-2, dp(24)));
        content.addView(titleRow);

        TextView hint = text("写下这一集的想法吧，长按自己的留言可以删除。", 13, MUTED, false);
        content.addView(hint, new LinearLayout.LayoutParams(-1, dp(28)));
        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(12), 0, dp(5), 0);
        composer.setBackground(rounded(Color.rgb(244, 247, 252), 14, Color.TRANSPARENT, 0));
        discussionInput = new EditText(this);
        discussionInput.setSingleLine(true);
        discussionInput.setTextSize(14);
        discussionInput.setTextColor(INK);
        discussionInput.setHintTextColor(Color.rgb(145, 150, 160));
        discussionInput.setHint("说点什么…");
        discussionInput.setBackgroundColor(Color.TRANSPARENT);
        composer.addView(discussionInput, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button send = new Button(this);
        send.setText("发布");
        send.setTextSize(13);
        send.setTextColor(Color.WHITE);
        send.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        send.setAllCaps(false);
        send.setMinWidth(0);
        send.setMinHeight(0);
        send.setMinimumWidth(0);
        send.setMinimumHeight(0);
        send.setPadding(dp(12), 0, dp(12), 0);
        send.setBackground(rounded(BLUE, 10, Color.TRANSPARENT, 0));
        send.setOnClickListener(v -> publishDiscussion());
        composer.addView(send, new LinearLayout.LayoutParams(dp(66), dp(34)));
        content.addView(composer, new LinearLayout.LayoutParams(-1, dp(48)));

        discussionList = new LinearLayout(this);
        discussionList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams discussionParams = new LinearLayout.LayoutParams(-1, -2);
        discussionParams.setMargins(0, dp(10), 0, 0);
        content.addView(discussionList, discussionParams);
        renderDiscussions();
    }

    private void publishDiscussion() {
        String value = discussionInput == null ? "" : discussionInput.getText().toString().trim();
        if (value.isBlank()) return;
        if (value.length() > 120) {
            Toast.makeText(this, "留言请控制在 120 字以内", Toast.LENGTH_SHORT).show();
            return;
        }
        DiscussionStore.add(this, bangumiId, subjectName, episode, value);
        discussionInput.setText("");
        renderDiscussions();
    }

    private void renderDiscussions() {
        if (discussionList == null) return;
        discussionList.removeAllViews();
        JSONArray items = DiscussionStore.read(this, bangumiId, subjectName, episode);
        if (items.length() == 0) {
            TextView empty = text("还没有留言，来当第一个发言的人吧。", 13, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(rounded(Color.rgb(248, 249, 252), 12, Color.TRANSPARENT, 0));
            discussionList.addView(empty, new LinearLayout.LayoutParams(-1, dp(62)));
            return;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) discussionList.addView(discussionRow(item), rowMargins(8));
        }
    }

    private View discussionRow(JSONObject item) {
        long id = item.optLong("id");
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(rounded(Color.rgb(248, 249, 252), 12, Color.TRANSPARENT, 0));
        LinearLayout meta = new LinearLayout(this);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.addView(text("我", 13, BLUE, true), new LinearLayout.LayoutParams(0, dp(20), 1));
        TextView time = text(relativeTime(item.optLong("createdAt")), 12, MUTED, false);
        time.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        meta.addView(time, new LinearLayout.LayoutParams(dp(72), dp(20)));
        card.addView(meta);
        TextView body = text(item.optString("content"), 14, INK, false);
        body.setLineSpacing(dp(2), 1f);
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));
        card.setOnLongClickListener(v -> {
            DiscussionStore.remove(this, id);
            renderDiscussions();
            Toast.makeText(this, "留言已删除", Toast.LENGTH_SHORT).show();
            return true;
        });
        return card;
    }

    private LinearLayout.LayoutParams rowMargins(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(top), 0, 0);
        return params;
    }

    private String relativeTime(long time) {
        long seconds = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        if (seconds < 60) return "刚刚";
        if (seconds < 3600) return (seconds / 60) + " 分钟前";
        if (seconds < 86400) return (seconds / 3600) + " 小时前";
        return (seconds / 86400) + " 天前";
    }

    private GradientDrawable rounded(int color, int radius, int stroke, int width) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (width > 0) drawable.setStroke(dp(width), stroke);
        return drawable;
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(LINE);
        return line;
    }

    private LinearLayout.LayoutParams margins(int left, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.setMargins(dp(left), dp(top), 0, dp(bottom));
        return params;
    }

    private void initializePlayer() {
        if (videoUrl == null || videoUrl.isBlank()) return;
        player = createPlayer();
        playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)));
        if (resumePosition > 0) player.seekTo(resumePosition);
        player.prepare();
        player.setPlayWhenReady(true);
        showControlsTemporarily();
    }

    private void playUrl(String sourceName, String url) {
        videoUrl = url;
        currentSourceName = sourceName;
        SourceState previous = sourceStates.get(sourceName);
        sourceStates.put(sourceName, new SourceState(SOURCE_LOADING, url, "",
                previous == null ? "" : previous.siteUrl));
        if (player == null) {
            player = createPlayer();
            playerView.setPlayer(player);
        }
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        if (resumePosition > 0) {
            player.seekTo(resumePosition);
            resumePosition = 0L;
        }
        player.prepare();
        player.setPlayWhenReady(true);
        if (previewImage != null) previewImage.setVisibility(View.GONE);
        sourceButton.setText(compactSourceName(sourceName));
        dockSourceButton.setText(compactSourceName(sourceName));
        updateSourceStatus();
        renderSourcePicker();
    }

    private ExoPlayer createPlayer() {
        ExoPlayer value = new ExoPlayer.Builder(this).build();
        value.addListener(playbackListener);
        return value;
    }

    private void updateSourceStatus() {
        if (sourceStatusView != null) {
            int loading = 0;
            int failed = 0;
            for (SourceState state : sourceStates.values()) {
                if (SOURCE_LOADING.equals(state.status)) loading++;
                if (SOURCE_FAILED.equals(state.status)) failed++;
            }
            int ready = 0;
            for (SourceState state : sourceStates.values()) {
                if (SOURCE_READY.equals(state.status)) ready++;
            }
            String summary = "可用 " + ready + " 条";
            if (loading > 0) summary += " · " + loading + " 条解析中";
            if (failed > 0) summary += " · " + failed + " 条失败";
            sourceStatusView.setText(summary);
        }
    }

    private void showSourcePicker() {
        sourceDialog = new BottomSheetDialog(this);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), dp(16), dp(20), dp(28));
        sheet.setBackground(rounded(Color.WHITE, 20, 0, 0));

        TextView title = text("选择视频源", 21, INK, true);
        sheet.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        sourceSummaryView = text("", 13, MUTED, false);
        sheet.addView(sourceSummaryView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
        sourceListContainer = new LinearLayout(this);
        sourceListContainer.setOrientation(LinearLayout.VERTICAL);
        sourceScrollView = new ScrollView(this);
        sourceScrollView.addView(sourceListContainer);
        sheet.addView(sourceScrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        sourceDialog.setContentView(sheet);
        sourceDialog.setOnDismissListener(dialog -> {
            sourceDialog = null;
            sourceListContainer = null;
            sourceSummaryView = null;
            sourceScrollView = null;
        });
        renderSourcePicker();
        sourceDialog.setOnShowListener(dialog -> {
            View parent = (View) sheet.getParent();
            if (parent != null) {
                parent.getLayoutParams().height = dp(500);
                parent.setBackgroundColor(Color.TRANSPARENT);
            }
        });
        sourceDialog.show();
    }

    private void renderSourcePicker() {
        if (sourceListContainer == null || sourceSummaryView == null) return;
        boolean keepTop = sourceScrollView != null && sourceScrollView.getScrollY() < dp(20);
        sourceListContainer.removeAllViews();
        int loading = 0;
        int ready = 0;
        int failed = 0;
        for (SourceState state : sourceStates.values()) {
            if (SOURCE_LOADING.equals(state.status)) loading++;
            else if (SOURCE_READY.equals(state.status)) ready++;
            else if (SOURCE_FAILED.equals(state.status)) failed++;
        }
        sourceSummaryView.setText("全部 " + sourceStates.size() + " · 可用 " + ready
                + " · 解析中 " + loading + " · 失败 " + failed);
        if (sourceStates.isEmpty()) {
            TextView empty = text("正在读取视频源列表…", 15, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            sourceListContainer.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(100)));
            return;
        }
        java.util.List<Map.Entry<String, SourceState>> ordered =
                new java.util.ArrayList<>(sourceStates.entrySet());
        ordered.sort(java.util.Comparator.comparingInt(entry -> sourceRank(
                entry.getKey(), entry.getValue())));
        for (Map.Entry<String, SourceState> entry : ordered) {
            sourceListContainer.addView(sourceRow(entry.getKey(), entry.getValue()));
        }
        ScrollView scroll = sourceScrollView;
        if (keepTop && scroll != null) {
            scroll.post(() -> scroll.scrollTo(0, 0));
        }
    }

    private int sourceRank(String name, SourceState state) {
        if (name.equals(currentSourceName)) return 0;
        if (SOURCE_READY.equals(state.status)) return 1;
        if (SOURCE_LOADING.equals(state.status)) return 2;
        return 3;
    }

    private View sourceRow(String name, SourceState state) {
        boolean current = name.equals(currentSourceName);
        boolean ready = SOURCE_READY.equals(state.status) && !state.url.isBlank();
        boolean retryable = SOURCE_FAILED.equals(state.status) && !state.url.isBlank();
        String siteName = name;
        String channelName = "";
        int separator = name.indexOf(" · ");
        if (separator >= 0) {
            siteName = name.substring(0, separator);
            channelName = name.substring(separator + 3);
        }
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(12), dp(10));
        row.setBackground(rounded(current ? Color.rgb(235, 243, 255) : Color.WHITE,
                12, current ? Color.rgb(184, 211, 255) : LINE, 1));

        if (SOURCE_LOADING.equals(state.status)) {
            ProgressBar progress = new ProgressBar(this);
            progress.setIndeterminateTintList(ColorStateList.valueOf(BLUE));
            row.addView(progress, new LinearLayout.LayoutParams(dp(24), dp(24)));
        } else {
            View dot = new View(this);
            int color = SOURCE_READY.equals(state.status)
                    ? Color.rgb(31, 157, 85) : Color.rgb(220, 68, 74);
            dot.setBackground(rounded(color, 6, 0, 0));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(12), dp(12));
            dotParams.setMargins(dp(6), 0, dp(6), 0);
            row.addView(dot, dotParams);
        }

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, dp(8), 0);
        TextView sourceName = text(siteName, 16, INK, true);
        labels.addView(sourceName, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
        String detail;
        int detailColor = MUTED;
        if (current && SOURCE_LOADING.equals(state.status)) {
            detail = "当前源正在加载…";
            detailColor = BLUE;
        } else if (current && SOURCE_FAILED.equals(state.status)) {
            detail = "当前源加载失败 · " + compactError(state.error);
            detailColor = Color.rgb(190, 52, 60);
        } else if (current) {
            detail = "当前播放 · 可用";
            detailColor = BLUE;
        } else if (SOURCE_LOADING.equals(state.status)) {
            detail = "解析中…";
        } else if (ready) {
            detail = "可播放";
        } else {
            detail = "加载失败" + (state.error.isBlank() ? "" : " · " + compactError(state.error));
            detailColor = Color.rgb(190, 52, 60);
        }
        if (!channelName.isBlank()) detail = channelName + " · " + detail;
        TextView detailView = text(detail, 13, detailColor, false);
        detailView.setMaxLines(1);
        detailView.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(detailView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));
        row.addView(labels, new LinearLayout.LayoutParams(0, dp(52), 1));

        boolean needsVerification = SOURCE_FAILED.equals(state.status)
                && state.error.contains("验证") && !state.siteUrl.isBlank();
        String actionText = needsVerification ? "去验证"
                : retryable ? "重试" : current ? "使用中" : ready ? "切换" : "";
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        TextView action = text(actionText, 14,
                current || ready || retryable ? BLUE : MUTED, current && !retryable);
        action.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.addView(action, new LinearLayout.LayoutParams(dp(76), dp(28)));
        TextView visit = text(state.siteUrl.isBlank() ? "" :
                needsVerification ? "应用内验证" : "访问网站", 12, BLUE, false);
        visit.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if (!state.siteUrl.isBlank()) {
            visit.setOnClickListener(v -> {
                if (needsVerification) openVerificationSite(state.siteUrl);
                else openSourceSite(state.siteUrl);
            });
        }
        actions.addView(visit, new LinearLayout.LayoutParams(dp(76), dp(24)));
        row.addView(actions, new LinearLayout.LayoutParams(dp(76), dp(52)));
        if ((ready && !current) || retryable) row.setOnClickListener(v -> {
            playUrl(name, state.url);
            if (sourceDialog != null) sourceDialog.dismiss();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(76));
        params.setMargins(0, dp(5), 0, dp(5));
        row.setLayoutParams(params);
        return row;
    }

    private void openVerificationSite(String siteUrl) {
        Intent intent = new Intent(this, SiteVerificationActivity.class);
        intent.putExtra("verification_url", siteUrl);
        startActivityForResult(intent, REQUEST_SITE_VERIFICATION);
    }

    @Override
    @Deprecated
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SITE_VERIFICATION && resultCode == RESULT_OK) {
            requestResolution(episode, false);
        }
    }

    private String compactSourceName(String name) {
        if (name == null) return "视频源";
        int separator = name.indexOf(" · ");
        return separator >= 0 ? name.substring(separator + 3) : name;
    }

    private void openSourceSite(String siteUrl) {
        try {
            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(siteUrl));
            startActivity(browser);
        } catch (Exception exception) {
            android.widget.Toast.makeText(this, "无法打开该网站",
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private String compactError(String error) {
        String value = error.replace('\n', ' ').trim();
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("failed to connect") || lower.contains("connection refused")) {
            return "无法连接网站";
        }
        if (lower.contains("hostname") || lower.contains("certificate")) {
            return "证书校验失败";
        }
        if (lower.contains("tls") || lower.contains("ssl")) return "安全连接失败";
        if (lower.contains("http 403")) return "网站拒绝访问";
        if (lower.contains("timeout") || lower.contains("timed out") || value.contains("超时")) {
            return "连接超时";
        }
        if (value.contains("验证")) return "需要网站验证";
        return value.length() > 24 ? value.substring(0, 24) + "…" : value;
    }

    private void resolveEpisode(int targetEpisode) {
        if (targetEpisode == episode) return;
        requestResolution(targetEpisode, true);
    }

    private void requestResolution(int targetEpisode, boolean changingEpisode) {
        saveWatchHistory();
        episode = targetEpisode;
        videoUrl = "";
        resumePosition = 0L;
        sources.clear();
        sourceStates.clear();
        currentSourceName = "";
        clearCachedSources(episode);
        selectedEpisodeRange = (episode - 1) / EPISODES_PER_RANGE;
        updateCurrentEpisodeLabels();
        renderDiscussions();
        sourceButton.setText("视频源");
        sourceStatusView.setText("正在并发解析第 " + episode + " 集…");
        renderEpisodeSelector();
        if (player != null) player.stop();
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("subject_name", subjectName);
        intent.putExtra("subject_cover", subjectCover);
        intent.putExtra("bangumi_id", bangumiId);
        intent.putExtra("episode", targetEpisode);
        intent.putExtra("auto_resolve", true);
        intent.putExtra("return_to_player", true);
        intent.putExtra("available_episodes", availableEpisodes);
        intent.putStringArrayListExtra("episode_titles", new ArrayList<>(episodeTitles));
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void renderEpisodeSelector() {
        renderEpisodeGroups();
        renderEpisodeRanges();
        if (episodeRow == null) return;
        episodeRow.removeAllViews();
        int first = selectedEpisodeRange * EPISODES_PER_RANGE + 1;
        int last = Math.min(availableEpisodes, first + EPISODES_PER_RANGE - 1);
        for (int value = first; value <= last; value++) {
            Button button = new Button(this);
            String title = episodeTitle(value);
            button.setText(title.isBlank() ? String.valueOf(value) : value + "\n" + title);
            button.setTextSize(title.isBlank() ? 16 : 12);
            button.setAllCaps(false);
            button.setMaxLines(2);
            button.setEllipsize(TextUtils.TruncateAt.END);
            button.setGravity(Gravity.CENTER);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setStateListAnimator(null);
            button.setTextColor(value == episode ? Color.WHITE : INK);
            button.setBackground(rounded(value == episode ? BLUE : Color.TRANSPARENT,
                    9, value == episode ? BLUE : LINE, 1));
            int targetEpisode = value;
            button.setOnClickListener(v -> resolveEpisode(targetEpisode));
            int width = title.isBlank() ? dp(58) : dp(116);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(62));
            params.setMargins(0, 0, dp(10), 0);
            episodeRow.addView(button, params);
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

    private void updateCurrentEpisodeLabels() {
        if (currentEpisodeView != null) currentEpisodeView.setText("第" + episode + "集");
        if (currentEpisodeNameView == null) return;
        String title = episodeTitle(episode);
        currentEpisodeNameView.setText(title);
        currentEpisodeNameView.setVisibility(title.isBlank() ? View.GONE : View.VISIBLE);
    }

    private String episodeTitle(int episodeNumber) {
        int index = episodeNumber - 1;
        return index >= 0 && index < episodeTitles.size() ? episodeTitles.get(index) : "";
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            saveWatchHistory();
            player.pause();
        }
    }

    private void saveWatchHistory() {
        long position = player == null ? resumePosition : Math.max(0L, player.getCurrentPosition());
        long duration = player == null ? 0L : Math.max(0L, player.getDuration());
        WatchHistoryStore.record(this, subjectName, subjectCover, episode,
                videoUrl, bangumiId, position, duration);
    }

    @Override
    protected void onDestroy() {
        saveWatchHistory();
        progressHandler.removeCallbacks(progressUpdater);
        progressHandler.removeCallbacks(hideControls);
        unregisterReceiver(sourceReceiver);
        if (player != null) player.release();
        super.onDestroy();
    }
}
