package com.example.seminarandroidapp;

import android.app.Application;
import com.example.seminarandroidapp.util.Prefs;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
    }
}