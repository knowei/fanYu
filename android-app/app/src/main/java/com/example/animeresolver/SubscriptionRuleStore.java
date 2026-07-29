package com.example.animeresolver;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Stores the optional css1.json rule package in app-private storage. */
final class SubscriptionRuleStore {
    static final String DOWNLOAD_URL = "https://sub.creamycake.org/v1/css1.json";

    private static final String PREFS = "subscription_rule_package";
    private static final String KEY_INSTALLED_AT = "installed_at";
    private static final String KEY_SOURCE_COUNT = "source_count";
    private static final String DIRECTORY = "source_packages";
    private static final String FILE_NAME = "css1.json";

    private SubscriptionRuleStore() {
    }

    static boolean isInstalled(Context context) {
        File file = packageFile(context);
        return file.isFile() && file.length() > 0;
    }

    static String read(Context context) throws IOException {
        File file = packageFile(context);
        if (!file.isFile()) throw new IOException("CSS1 规则包尚未安装");
        try (FileInputStream input = new FileInputStream(file)) {
            String raw = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            validate(raw);
            return raw;
        }
    }

    static synchronized InstallInfo install(Context context, String raw) throws Exception {
        int sourceCount = validate(raw);
        File directory = packageDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建规则包目录");
        }
        File target = packageFile(context);
        File temporary = new File(directory, FILE_NAME + ".download");
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            output.write(raw.getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
        // The old package remains usable until the new download has been fully validated/written.
        try {
            Files.move(temporary.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) {
            temporary.delete();
            throw new IOException("无法保存规则包", error);
        }
        long installedAt = System.currentTimeMillis();
        preferences(context).edit()
                .putLong(KEY_INSTALLED_AT, installedAt)
                .putInt(KEY_SOURCE_COUNT, sourceCount)
                .apply();
        return new InstallInfo(sourceCount, target.length(), installedAt);
    }

    static synchronized boolean uninstall(Context context) {
        File file = packageFile(context);
        File temporary = new File(packageDirectory(context), FILE_NAME + ".download");
        boolean removed = !file.exists() || file.delete();
        if (temporary.exists()) temporary.delete();
        if (removed) preferences(context).edit().clear().apply();
        return removed;
    }

    static InstallInfo info(Context context) {
        File file = packageFile(context);
        SharedPreferences preferences = preferences(context);
        return new InstallInfo(preferences.getInt(KEY_SOURCE_COUNT, 0),
                file.isFile() ? file.length() : 0L,
                preferences.getLong(KEY_INSTALLED_AT, 0L));
    }

    private static int validate(String raw) throws IOException {
        if (raw == null || raw.isBlank()) throw new IOException("下载内容为空");
        try {
            JSONObject root = new JSONObject(raw);
            JSONObject exported = root.optJSONObject("exportedMediaSourceDataList");
            JSONArray sources = exported == null ? null : exported.optJSONArray("mediaSources");
            if (sources == null) throw new IOException("不是兼容的 CSS1 规则包");
            int compatible = 0;
            for (int index = 0; index < sources.length(); index++) {
                JSONObject item = sources.optJSONObject(index);
                if (item == null || !"web-selector".equals(item.optString("factoryId"))) continue;
                JSONObject arguments = item.optJSONObject("arguments");
                JSONObject search = arguments == null ? null : arguments.optJSONObject("searchConfig");
                if (search != null && !search.optString("searchUrl").isBlank()) compatible++;
            }
            if (compatible == 0) throw new IOException("规则包中没有兼容的视频源");
            return compatible;
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("规则包 JSON 格式错误", error);
        }
    }

    private static File packageDirectory(Context context) {
        return new File(context.getFilesDir(), DIRECTORY);
    }

    private static File packageFile(Context context) {
        return new File(packageDirectory(context), FILE_NAME);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    record InstallInfo(int sourceCount, long bytes, long installedAt) {
    }
}
