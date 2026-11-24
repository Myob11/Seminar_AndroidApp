package com.example.seminarandroidapp;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.seminarandroidapp.util.Prefs;
import com.example.seminarandroidapp.util.RealtimeSocketManager;

public class MyApplication extends Application {
    private int startedActivityCount;

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        AppCompatDelegate.setDefaultNightMode(
                Prefs.isDarkMode(this)
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO
        );

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity activity) {
                startedActivityCount++;
                RealtimeSocketManager.getInstance().onForeground(MyApplication.this);
            }

            @Override
            public void onActivityStopped(Activity activity) {
                startedActivityCount = Math.max(0, startedActivityCount - 1);
                if (startedActivityCount == 0) {
                    RealtimeSocketManager.getInstance().onBackground();
                }
            }

            @Override public void onActivityCreated(Activity activity, Bundle state) { }
            @Override public void onActivityResumed(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
    }
}