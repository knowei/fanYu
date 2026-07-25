package com.example.animeresolver;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public final class FanYuApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
