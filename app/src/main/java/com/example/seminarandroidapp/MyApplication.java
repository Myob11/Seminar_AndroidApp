package com.example.seminarandroidapp;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.seminarandroidapp.util.Prefs;
import com.example.seminarandroidapp.util.RealtimeSocketManager;

public class MyApplication extends Application {
    private static String lastHandledRevokedToken;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int startedActivityCount;

    public static void handleTokenRevoked(Context context, String revokedToken) {
        if (context == null || revokedToken == null || revokedToken.isEmpty()) {
            return;
        }
        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof MyApplication)) {
            return;
        }
        MyApplication app = (MyApplication) appContext;
        synchronized (MyApplication.class) {
            if (revokedToken.equals(lastHandledRevokedToken)) {
                return;
            }
            lastHandledRevokedToken = revokedToken;
        }

        app.mainHandler.post(() -> {
            if (!revokedToken.equals(Prefs.getToken(app))) {
                return;
            }

            Prefs.clearAuth(app);
            RealtimeSocketManager.getInstance().onAuthStateChanged(app);
            Toast.makeText(app, R.string.token_revoked, Toast.LENGTH_LONG).show();

            Intent loginIntent = new Intent(app, LoginActivity.class);
            loginIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            app.startActivity(loginIntent);
        });
    }

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