package com.example.seminarandroidapp;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.example.seminarandroidapp.util.Prefs;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        AppCompatDelegate.setDefaultNightMode(
                Prefs.isDarkMode(this)
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO
        );
    }
}