package com.decibelmeter;

import android.app.Application;
import android.content.Context;
import androidx.multidex.MultiDex;

public class DecibelMeterApplication extends Application {
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        // 初始化应用级别的配置
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                android.util.Log.e("DecibelMeter", "Uncaught exception: " + throwable.getMessage(), throwable);
                // 可以在这里添加崩溃报告逻辑
                System.exit(1);
            }
        });
    }
}