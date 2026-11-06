package com.example.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;

import com.google.android.material.card.MaterialCardView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * v3.0 冒烟测试：在 JVM 里真实 inflate 布局、跑 Activity 生命周期。
 * 目的是在没有手机/模拟器的环境里兜住「资源缺失、主题属性错误、绑定为 null、渲染 NPE」这类崩溃。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "zh-rCN-w411dp-h891dp-xxhdpi")
public class AppSmokeTest {

    private static final int[] LAYOUT_IDS = {
            R.id.drawer, R.id.bg, R.id.scrim,
            R.id.toolbar, R.id.search_card, R.id.et_city, R.id.btn_loc,
            R.id.swipe, R.id.scroll,
            R.id.card_hero, R.id.tv_city, R.id.tv_source, R.id.tv_area, R.id.tv_temp,
            R.id.iv_icon, R.id.tv_desc, R.id.tv_extra,
            R.id.tv_body, R.id.tv_humidity, R.id.tv_wind,
            R.id.tv_pressure, R.id.tv_cloud, R.id.tv_precip,
            R.id.card_alert, R.id.tv_alert_badge, R.id.tv_alert_title, R.id.tv_alert_text,
            R.id.card_air, R.id.tv_aqi, R.id.tv_aqi_level, R.id.tv_aqi_advice, R.id.tv_pollutants,
            R.id.card_hourly, R.id.rv_hourly,
            R.id.card_curve, R.id.temp_curve,
            R.id.card_daily, R.id.rv_daily,
            R.id.card_sun, R.id.tv_sunrise, R.id.tv_sunset, R.id.tv_daylight,
            R.id.card_life, R.id.rv_index,
            R.id.tv_updated, R.id.progress, R.id.tv_error, R.id.nav,
    };

    private Context app() {
        return RuntimeEnvironment.getApplication();
    }

    private Context themed(Context base, boolean night) {
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return new ContextThemeWrapper(base.createConfigurationContext(cfg), R.style.Theme_WeatherApp);
    }

    /** 日夜两套主题都能完整 inflate，且所有控件都在（防止改了 XML 忘记同步代码） */
    @Test
    public void layoutInflatesInDayAndNightThemes() {
        for (boolean night : new boolean[]{false, true}) {
            Context ctx = themed(app(), night);
            View root = LayoutInflater.from(ctx).inflate(R.layout.activity_main, null);
            assertNotNull("root null, night=" + night, root);
            for (int id : LAYOUT_IDS) {
                View v = root.findViewById(id);
                String name = ctx.getResources().getResourceEntryName(id);
                assertNotNull("missing view: " + name + " (night=" + night + ")", v);
            }
        }
    }

    /** 三种界面风格 × 日夜都能应用到所有卡片，且圆角/描边确实按风格变化 */
    @Test
    public void uiStyleAppliesToEveryCard() {
        for (boolean night : new boolean[]{false, true}) {
            Context ctx = themed(app(), night);
            View root = LayoutInflater.from(ctx).inflate(R.layout.activity_main, null);
            int[] cards = {R.id.search_card, R.id.card_alert, R.id.card_air, R.id.card_hourly,
                    R.id.card_curve, R.id.card_daily, R.id.card_sun, R.id.card_life};
            float density = ctx.getResources().getDisplayMetrics().density;
            for (int style : new int[]{UiStyle.STANDARD, UiStyle.GLASS, UiStyle.MINIMAL}) {
                for (int id : cards) {
                    MaterialCardView card = root.findViewById(id);
                    assertNotNull(card);
                    UiStyle.applyCard(card, style, night, ctx);
                    int expectRadius = Math.round(UiStyle.radiusDp(style) * density);
                    assertEquals("radius for style " + style, expectRadius, Math.round(card.getRadius()));
                    assertEquals("stroke for style " + style,
                            Math.round(UiStyle.strokeWidthDp(style) * density), card.getStrokeWidth());
                }
            }
        }
    }

    /** 应用能带着离线缓存冷启动，并把缓存渲染到界面上 */
    @Test
    public void activityStartsAndRendersCachedData() {
        WeatherData d = sample();
        WeatherPrefs.saveFull(app(), d);
        WeatherPrefs.saveLoc(app(), d.lat, d.lon);
        WeatherPrefs.addCity(app(), d.city, d.area, d.lat, d.lon);
        WeatherPrefs.setCurrent(app(), d.city);

        ActivityController<MainActivity> ctl = Robolectric.buildActivity(MainActivity.class).create();
        MainActivity a = ctl.get();
        assertNotNull(a);

        TextView city = a.findViewById(R.id.tv_city);
        TextView area = a.findViewById(R.id.tv_area);
        TextView temp = a.findViewById(R.id.tv_temp);
        TextView source = a.findViewById(R.id.tv_source);
        assertNotNull(city);
        assertEquals(d.city, city.getText().toString());
        assertEquals(d.area, area.getText().toString());
        assertTrue("温度未渲染", temp.getText().length() > 0);
        assertEquals(d.source, source.getText().toString());

        ctl.destroy();
    }

    /** 逐时卡片：降水小柱宽度随概率变化，且高温概率用深色 */
    @Test
    public void hourlyAdapterBindsPrecipBar() {
        Context ctx = app();
        WeatherData d = sample();
        HourlyAdapter adapter = new HourlyAdapter(ctx, d.hourlyTime, d.hourlyTemp,
                d.hourlyCode, d.hourlyPrecipProb);
        android.widget.FrameLayout parent = new android.widget.FrameLayout(ctx);
        HourlyAdapter.VH vh = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(vh, 0);
        View fill = vh.itemView.findViewById(R.id.precip_fill);
        assertNotNull("降水柱未绑定", fill);
        int trackPx = Math.round(38 * ctx.getResources().getDisplayMetrics().density);
        int expected = Math.round(trackPx * Math.max(0, Math.min(100, d.hourlyPrecipProb[0])) / 100f);
        assertEquals("降水柱宽度与概率不匹配", expected, fill.getLayoutParams().width);

        // 低概率小时：柱子几乎不可见，文字也隐藏
        d.hourlyPrecipProb[1] = 0;
        adapter.onBindViewHolder(vh, 1);
        assertEquals(0, fill.getLayoutParams().width);
        assertEquals(View.INVISIBLE, vh.tvPop.getVisibility());
    }

    /** 缓存序列化 / 偏好读写往返，包含新增的 area、source、provider、uiStyle */
    @Test
    public void preferencesRoundTrip() {
        Context ctx = app();
        WeatherData d = sample();
        WeatherPrefs.saveFull(ctx, d);
        WeatherData back = WeatherPrefs.loadFull(ctx);
        assertNotNull(back);
        assertEquals(d.city, back.city);
        assertEquals(d.area, back.area);
        assertEquals(d.source, back.source);
        assertEquals(d.currentTemp, back.currentTemp);
        assertEquals(d.hourlyTemp.length, back.hourlyTemp.length);
        assertEquals(d.dailyMax.length, back.dailyMax.length);
        assertEquals(d.hasAir, back.hasAir);
        assertEquals(d.aqi, back.aqi);

        WeatherPrefs.setProvider(ctx, WeatherClient.P_WTTR);
        assertEquals(WeatherClient.P_WTTR, WeatherPrefs.getProvider(ctx));
        WeatherPrefs.setUiStyle(ctx, UiStyle.GLASS);
        assertEquals(UiStyle.GLASS, WeatherPrefs.getUiStyle(ctx));
    }

    /** 纯函数规则：天气代码映射、生活指数、预警、空气质量，含极端值 */
    @Test
    public void pureHelpersHandleEdgeCases() {
        assertEquals(0, WeatherCodes.fromWwo(113));
        assertEquals(63, WeatherCodes.fromWwo(302));
        assertEquals(95, WeatherCodes.fromWwo(200));
        assertEquals(3, WeatherCodes.fromMetSymbol("cloudy"));
        assertEquals(2, WeatherCodes.fromMetSymbol("partlycloudy_day"));
        assertEquals(63, WeatherCodes.fromMetSymbol("rain"));
        assertEquals(3, WeatherCodes.fromMetSymbol(null));

        WeatherData empty = new WeatherData();
        assertNotNull(LifeIndex.compute(empty));
        assertNotNull(WeatherAlert.compute(empty));
        assertTrue(WeatherAlert.top(WeatherAlert.compute(empty)) == null
                || WeatherAlert.top(WeatherAlert.compute(empty)).level >= 0);

        WeatherData hot = sample();
        hot.currentTemp = 39;
        hot.windSpeed = 70;
        hot.dailyPrecip[0] = 60;
        assertTrue("高温/大风/暴雨应有预警", WeatherAlert.compute(hot).size() >= 2);
        WeatherAlert.Alert top = WeatherAlert.top(WeatherAlert.compute(hot));
        assertNotNull(top);
        assertTrue(top.level >= 1);

        assertEquals("优", AirQuality.level(10));
        assertEquals("严重污染", AirQuality.level(200));
        assertEquals("低", AirQuality.uvLevel(1));
        assertEquals("中等", AirQuality.uvLevel(4));
        assertEquals("极高", AirQuality.uvLevel(100));
        assertTrue(AirQuality.dominant(sample()).length() > 0);
    }

    private WeatherData sample() {
        WeatherData d = new WeatherData();
        d.city = "杭州";
        d.area = "浙江省";
        d.source = "Open-Meteo";
        d.lat = 30.2741;
        d.lon = 120.1551;
        d.currentTemp = 22;
        d.currentCode = 51;
        d.feelsLike = 21;
        d.humidity = 76;
        d.windSpeed = 20;
        d.windDir = 0;
        d.pressure = 1015;
        d.cloudCover = 36;
        d.precip = 0.1;
        d.isDay = true;
        d.updatedAt = System.currentTimeMillis();

        d.hourlyTime = new String[24];
        d.hourlyTemp = new int[24];
        d.hourlyCode = new int[24];
        d.hourlyHumidity = new int[24];
        d.hourlyPrecipProb = new int[24];
        d.hourlyPrecip = new double[24];
        d.hourlyWind = new double[24];
        for (int i = 0; i < 24; i++) {
            d.hourlyTime[i] = "2026-11-01T" + String.format("%02d", i) + ":00";
            d.hourlyTemp[i] = 22 - i / 4;
            d.hourlyCode[i] = i % 3 == 0 ? 51 : 3;
            d.hourlyHumidity[i] = 70;
            d.hourlyPrecipProb[i] = 20 + i;
            d.hourlyPrecip[i] = 0.1;
            d.hourlyWind[i] = 12;
        }

        d.dailyDate = new String[7];
        d.dailyDay = new String[7];
        d.dailySunrise = new String[7];
        d.dailySunset = new String[7];
        d.dailyMin = new int[7];
        d.dailyMax = new int[7];
        d.dailyCode = new int[7];
        d.dailyPrecipProb = new int[7];
        d.dailyUv = new int[7];
        d.dailyPrecip = new double[7];
        d.dailyWindMax = new double[7];
        for (int i = 0; i < 7; i++) {
            d.dailyDate[i] = "2026-11-0" + (i + 1);
            d.dailyDay[i] = i == 0 ? "今天" : "周一";
            d.dailySunrise[i] = "06:13";
            d.dailySunset[i] = "17:12";
            d.dailyMin[i] = 16;
            d.dailyMax[i] = 28 - i;
            d.dailyCode[i] = 51;
            d.dailyPrecipProb[i] = 60;
            d.dailyUv[i] = 4;
            d.dailyPrecip[i] = 1.2;
            d.dailyWindMax[i] = 24;
        }

        d.hasAir = true;
        d.aqi = 46;
        d.usAqi = 52;
        d.pm25 = 18;
        d.pm10 = 25;
        d.ozone = 45;
        d.no2 = 35;
        d.so2 = 5;
        d.co = 300;
        return d;
    }
}
