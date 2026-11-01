package com.example.weather;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 极简启动闪屏：展示 800ms 应用图标动画后跳转到主界面。
 * 不引入额外依赖（不用 androidx.core:core-splashscreen）。
 */
public class SplashActivity extends AppCompatActivity {

    private static final long DELAY = 800;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }, DELAY);
    }
}
