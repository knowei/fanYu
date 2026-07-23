package com.example.animeresolver;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.WeakHashMap;

final class SystemBars {
    private static final WeakHashMap<View, int[]> BASE_PADDING = new WeakHashMap<>();

    private SystemBars() {}

    static void apply(Activity activity, View root, int backgroundColor) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        activity.getWindow().setStatusBarColor(backgroundColor);
        activity.getWindow().setNavigationBarColor(backgroundColor);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                activity.getWindow(), activity.getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);

        int[] base = new int[]{root.getPaddingLeft(), root.getPaddingTop(),
                root.getPaddingRight(), root.getPaddingBottom()};
        BASE_PADDING.put(root, base);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            int[] padding = BASE_PADDING.get(view);
            if (padding == null) return windowInsets;
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(padding[0] + bars.left, padding[1] + bars.top,
                    padding[2] + bars.right, padding[3] + bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    static void setFullscreen(View root, boolean fullscreen) {
        int[] padding = BASE_PADDING.get(root);
        if (padding == null) return;
        if (fullscreen) {
            root.setPadding(padding[0], padding[1], padding[2], padding[3]);
        } else {
            ViewCompat.requestApplyInsets(root);
        }
    }
}
