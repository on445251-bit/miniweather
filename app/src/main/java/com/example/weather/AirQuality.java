package com.example.weather;

import android.graphics.Color;

/**
 * 空气质量等级换算与配色（欧洲 AQI，Open-Meteo 的 european_aqi）。
 * 0–20 优 / 21–40 良 / 41–60 轻度 / 61–80 中度 / 81–100 重度 / >100 严重
 */
public final class AirQuality {

    private AirQuality() {}

    public static String level(int aqi) {
        if (aqi < 0) return "未知";
        if (aqi <= 20) return "优";
        if (aqi <= 40) return "良";
        if (aqi <= 60) return "轻度污染";
        if (aqi <= 80) return "中度污染";
        if (aqi <= 100) return "重度污染";
        return "严重污染";
    }

    public static int color(int aqi) {
        if (aqi < 0) return 0xFF9E9E9E;
        if (aqi <= 20) return 0xFF2E7D32;   // 绿
        if (aqi <= 40) return 0xFF9E9D24;   // 黄绿
        if (aqi <= 60) return 0xFFEF6C00;   // 橙
        if (aqi <= 80) return 0xFFC62828;   // 红
        if (aqi <= 100) return 0xFF6A1B9A;  // 紫
        return 0xFF4E0A15;                  // 褐红
    }

    /** 更浅的同色系，用于卡片底色 */
    public static int softColor(int aqi) {
        int c = color(aqi);
        return Color.argb(28, Color.red(c), Color.green(c), Color.blue(c));
    }

    public static String advice(int aqi) {
        if (aqi < 0) return "暂无空气质量数据";
        if (aqi <= 20) return "空气很棒，适合户外活动";
        if (aqi <= 40) return "空气不错，可正常户外活动";
        if (aqi <= 60) return "敏感人群减少长时间户外活动";
        if (aqi <= 80) return "建议减少户外运动，外出戴口罩";
        if (aqi <= 100) return "尽量待在室内，关闭门窗";
        return "避免外出，务必佩戴口罩";
    }

    /** 首要污染物（按浓度排序，简单取最大者） */
    public static String dominant(WeatherData d) {
        if (d == null || !d.hasAir) return "";
        double pm25 = d.pm25, pm10 = d.pm10, o3 = d.ozone, no2 = d.no2;
        String name = "PM2.5";
        double best = pm25;
        if (pm10 > best) { best = pm10; name = "PM10"; }
        if (o3 > best) { best = o3; name = "O₃"; }
        if (no2 > best) { best = no2; name = "NO₂"; }
        return name;
    }

    /** 紫外线等级 */
    public static String uvLevel(int uv) {
        if (uv < 0) return "未知";
        if (uv <= 2) return "低";
        if (uv <= 5) return "中等";
        if (uv <= 7) return "高";
        if (uv <= 10) return "很高";
        return "极高";
    }

    public static int uvColor(int uv) {
        if (uv <= 2) return 0xFF2E7D32;
        if (uv <= 5) return 0xFFF9A825;
        if (uv <= 7) return 0xFFEF6C00;
        if (uv <= 10) return 0xFFC62828;
        return 0xFF6A1B9A;
    }

    public static String uvAdvice(int uv) {
        if (uv <= 2) return "无需特别防护";
        if (uv <= 5) return "建议涂抹防晒霜";
        if (uv <= 7) return "避免正午暴晒，戴帽子";
        if (uv <= 10) return "尽量待在阴凉处";
        return "避免外出，做好全面防护";
    }
}
