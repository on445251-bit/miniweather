package com.example.weather;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

import java.util.concurrent.Executors;

/**
 * 轻量桌面小部件：展示“最近一次”天气（来自 WeatherPrefs 缓存 / 坐标）。
 * 点击小部件 → 刷新一次并打开 App。
 */
public class WeatherWidget extends AppWidgetProvider {

    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static final java.util.concurrent.Executor io = Executors.newSingleThreadExecutor();

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(ctx, mgr, id);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if ("com.example.weather.REFRESH".equals(intent.getAction())) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new android.content.ComponentName(ctx, WeatherWidget.class));
            for (int id : ids) update(ctx, mgr, id);
        }
    }

    private void update(Context ctx, AppWidgetManager mgr, int id) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_weather);

        // 点击 → 刷新
        Intent refresh = new Intent(ctx, WeatherWidget.class);
        refresh.setAction("com.example.weather.REFRESH");
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.w_iv, pi);
        rv.setOnClickPendingIntent(R.id.w_temp, pi);

        // 打开 App
        Intent open = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (open != null) {
            PendingIntent opi = PendingIntent.getActivity(ctx, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(R.id.w_city, opi);
        }

        // 先用缓存即时显示
        showCache(ctx, rv, mgr, id);

        // 后台拉取最新
        double[] loc = WeatherPrefs.getLoc(ctx);
        if (loc != null) {
            io.execute(() -> {
                WeatherData d = WeatherClient.fetch(loc[0], loc[1], WeatherPrefs.getCurrent(ctx));
                ui.post(() -> {
                    if (d != null) {
                        WeatherPrefs.cache(ctx, d);
                        bind(ctx, rv, d);
                        mgr.updateAppWidget(id, rv);
                    }
                });
            });
        }
        mgr.updateAppWidget(id, rv);
    }

    private void showCache(Context ctx, RemoteViews rv, AppWidgetManager mgr, int id) {
        WeatherData d = WeatherPrefs.getCache(ctx);
        if (d != null) {
            bind(ctx, rv, d);
            mgr.updateAppWidget(id, rv);
        }
    }

    private void bind(Context ctx, RemoteViews rv, WeatherData d) {
        boolean night = WeatherCodes.isNightNow();
        rv.setTextViewText(R.id.w_city, d.city);
        rv.setTextViewText(R.id.w_temp, WeatherPrefs.fmtTemp(d.currentTemp, ctx) + "°");
        rv.setTextViewText(R.id.w_desc, WeatherCodes.desc(d.currentCode, ctx));
        rv.setImageViewResource(R.id.w_iv, WeatherCodes.icon(d.currentCode, night));
        int bg = (d.currentCode >= 51 && d.currentCode <= 82) ? R.drawable.bg_rain
                : (d.currentCode >= 3 && d.currentCode <= 48) ? (night ? R.drawable.bg_night : R.drawable.bg_cloudy)
                : (night ? R.drawable.bg_night : R.drawable.bg_sunny);
        rv.setInt(R.id.w_root, "setBackgroundResource", bg);
    }
}
