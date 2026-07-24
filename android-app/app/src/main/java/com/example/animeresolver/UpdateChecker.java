package com.example.animeresolver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

final class UpdateChecker {
    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/knowei/fanYu/releases/latest";
    private static final String RELEASES_URL =
            "https://github.com/knowei/fanYu/releases";
    private static final long AUTO_CHECK_INTERVAL = 24L * 60L * 60L * 1000L;
    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private UpdateChecker() {}

    static String currentVersion(Activity activity) {
        try {
            PackageInfo info = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
            return info.versionName == null ? "0" : info.versionName;
        } catch (Exception ignored) {
            return "0";
        }
    }

    static void check(Activity activity, boolean interactive) {
        if (!interactive) {
            long last = activity.getSharedPreferences("updates", Activity.MODE_PRIVATE)
                    .getLong("last_auto_check", 0L);
            if (System.currentTimeMillis() - last < AUTO_CHECK_INTERVAL) return;
            activity.getSharedPreferences("updates", Activity.MODE_PRIVATE).edit()
                    .putLong("last_auto_check", System.currentTimeMillis()).apply();
        }
        if (interactive) Toast.makeText(activity, "正在检查 GitHub 更新…",
                Toast.LENGTH_SHORT).show();
        EXECUTOR.execute(() -> fetchLatest(activity, interactive));
    }

    private static void fetchLatest(Activity activity, boolean interactive) {
        Request request = new Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "FanYu-Android")
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (response.code() == 404) {
                notifyMessage(activity, interactive, "GitHub 暂无正式 Release");
                return;
            }
            if (!response.isSuccessful() || response.body() == null) {
                notifyMessage(activity, interactive, "检查更新失败（" + response.code() + "）");
                return;
            }
            JSONObject release = new JSONObject(response.body().string());
            String tag = release.optString("tag_name");
            String title = release.optString("name", tag);
            String notes = release.optString("body");
            String pageUrl = release.optString("html_url", RELEASES_URL);
            String current = currentVersion(activity);
            if (!isNewer(tag, current)) {
                notifyMessage(activity, interactive, "已是最新版本（" + current + "）");
                return;
            }
            activity.runOnUiThread(() -> {
                if (!isUsable(activity)) return;
                String summary = notes == null ? "" : notes.trim();
                if (summary.length() > 500) summary = summary.substring(0, 500) + "…";
                String message = "当前版本 " + current + "\n最新版本 " + tag;
                if (!summary.isBlank()) message += "\n\n" + summary;
                new AlertDialog.Builder(activity)
                        .setTitle(title.isBlank() ? "发现新版本" : "发现新版本 · " + title)
                        .setMessage(message)
                        .setNegativeButton("稍后", null)
                        .setPositiveButton("前往 Releases", (dialog, which) ->
                                activity.startActivity(new Intent(Intent.ACTION_VIEW,
                                        Uri.parse(pageUrl))))
                        .show();
            });
        } catch (Exception exception) {
            notifyMessage(activity, interactive, "检查更新失败，请稍后重试");
        }
    }

    static boolean isNewer(String latest, String current) {
        int[] left = versionParts(latest);
        int[] right = versionParts(current);
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int a = i < left.length ? left[i] : 0;
            int b = i < right.length ? right[i] : 0;
            if (a != b) return a > b;
        }
        return false;
    }

    private static int[] versionParts(String value) {
        if (value == null) return new int[0];
        String normalized = value.trim().replaceFirst("^[vV]", "");
        String[] parts = normalized.split("[^0-9]+");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { result[i] = parts[i].isBlank() ? 0 : Integer.parseInt(parts[i]); }
            catch (Exception ignored) { result[i] = 0; }
        }
        return result;
    }

    private static void notifyMessage(Activity activity, boolean interactive, String message) {
        if (!interactive) return;
        activity.runOnUiThread(() -> {
            if (isUsable(activity)) Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        });
    }

    private static boolean isUsable(Activity activity) {
        return !activity.isFinishing() && !activity.isDestroyed();
    }
}
