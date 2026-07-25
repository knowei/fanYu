package com.example.animeresolver;

import android.animation.LayoutTransition;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;

final class UiMotion {
    private UiMotion() {}

    static boolean enabled(View view) {
        try {
            return Settings.Global.getFloat(view.getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (Exception ignored) {
            return true;
        }
    }

    static void fadeIn(View view) {
        if (!enabled(view)) return;
        view.setAlpha(0f);
        view.setTranslationY(dp(view, 8));
        view.animate().alpha(1f).translationY(0f).setDuration(180L).start();
    }

    static void crossFade(View view, Runnable update) {
        if (!enabled(view)) {
            update.run();
            return;
        }
        view.animate().alpha(0f).setDuration(70L).withEndAction(() -> {
            update.run();
            view.animate().alpha(1f).setDuration(140L).start();
        }).start();
    }

    static void animateLayout(ViewGroup group) {
        if (!enabled(group)) return;
        LayoutTransition transition = new LayoutTransition();
        transition.setDuration(220L);
        transition.enableTransitionType(LayoutTransition.CHANGING);
        group.setLayoutTransition(transition);
    }

    static void slideControls(View view, boolean visible, boolean fromBottom) {
        view.animate().cancel();
        if (!enabled(view)) {
            view.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            view.setAlpha(visible ? 1f : 0f);
            return;
        }
        float offset = dp(view, 18) * (fromBottom ? 1f : -1f);
        if (visible) {
            view.setVisibility(View.VISIBLE);
            view.setAlpha(0f);
            view.setTranslationY(offset);
            view.animate().alpha(1f).translationY(0f).setDuration(240L).start();
        } else {
            view.animate().alpha(0f).translationY(offset).setDuration(180L)
                    .withEndAction(() -> view.setVisibility(View.INVISIBLE)).start();
        }
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
