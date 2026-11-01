package com.example.weather;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * 轻量桌面小部件：先显示离线缓存，再后台拉取最新数据。
 * 点击图标/温度 → 刷新；点击城市名 → 打开 App。
 */
public class WeatherWidget extends AppWidgetProvider {

    public static final String ACTION_REFRESH = "com.example.weather.REFRESH";

    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static final java.util.concurrent.Executor io = Executors.newSingleThreadExecutor();

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(ctx, mgr, id);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            final PendingResult pr = goAsync();
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(
                    new android.content.ComponentName(ctx, WeatherWidget.class));
            for (int id : ids) update(ctx, mgr, id);
            ui.postDelayed(pr::finish, 8000);
        }
    }

    private void update(Context ctx, AppWidgetManager mgr, int id) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_weather);

        // 点击刷新
        Intent refresh = new Intent(ctx, WeatherWidget.class);
        refresh.setAction(ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.w_iv, pi);
        rv.setOnClickPendingIntent(R.id.w_temp, pi);
        rv.setOnClickPendingIntent(R.id.w_updated, pi);
        rv.setOnClickPendingIntent(R.id.w_root, pi);

        // 打开 App
        Intent open = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (open != null) {
            PendingIntent opi = PendingIntent.getActivity(ctx, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(R.id.w_city, opi);
            rv.setOnClickPendingIntent(R.id.w_desc, opi);
        }

        // 先用缓存即时显示
        WeatherData cached = WeatherPrefs.loadFull(ctx);
        if (cached != null) bind(ctx, rv, cached);
        mgr.updateAppWidget(id, rv);

        // 后台拉取最新
        double[] loc = WeatherPrefs.getLoc(ctx);
        if (loc == null) return;
        String city = WeatherPrefs.getCurrent(ctx);
        io.execute(() -> {
            WeatherData d = WeatherClient.fetch(loc[0], loc[1], city);
            if (d == null) return;
            WeatherPrefs.saveFull(ctx, d);
            ui.post(() -> {
                RemoteViews fresh = new RemoteViews(ctx.getPackageName(), R.layout.widget_weather);
                bind(ctx, fresh, d);
                rebindClicks(ctx, fresh);
                mgr.updateAppWidget(id, fresh);
            });
        });
    }

    /** 远程视图每次重建后需要重新挂点击事件 */
    private void rebindClicks(Context ctx, RemoteViews rv) {
        Intent refresh = new Intent(ctx, WeatherWidget.class);
        refresh.setAction(ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.w_iv, pi);
        rv.setOnClickPendingIntent(R.id.w_temp, pi);
        rv.setOnClickPendingIntent(R.id.w_updated, pi);
        rv.setOnClickPendingIntent(R.id.w_root, pi);

        Intent open = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (open != null) {
            PendingIntent opi = PendingIntent.getActivity(ctx, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(R.id.w_city, opi);
            rv.setOnClickPendingIntent(R.id.w_desc, opi);
        }
    }

    private void bind(Context ctx, RemoteViews rv, WeatherData d) {
        boolean night = !d.isDay;
        rv.setTextViewText(R.id.w_city, d.city);
        rv.setTextViewText(R.id.w_temp, WeatherPrefs.fmtTempDeg(d.currentTemp, ctx));
        rv.setTextViewText(R.id.w_desc, WeatherCodes.desc(d.currentCode, ctx));
        rv.setTextViewText(R.id.w_updated,
                new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date(
                        d.updatedAt > 0 ? d.updatedAt : System.currentTimeMillis())) + " 更新");
        rv.setImageViewResource(R.id.w_iv, WeatherCodes.icon(d.currentCode, night));

        int bg;
        if (d.currentCode >= 95) bg = R.drawable.bg_rain;
        else if (d.currentCode >= 51 && d.currentCode <= 86) bg = R.drawable.bg_rain;
        else if (d.currentCode >= 3) bg = night ? R.drawable.bg_night : R.drawable.bg_cloudy;
        else bg = night ? R.drawable.bg_night : R.drawable.bg_sunny;
        rv.setInt(R.id.w_root, "setBackgroundResource", bg);
    }
}
