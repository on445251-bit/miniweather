package com.lightweather.desktop;

import java.awt.Color;

/** 空气质量 / 紫外线分级与配色（欧洲 AQI 分段） */
public final class AirQuality {

    private AirQuality() {
    }

    public static String level(int aqi) {
        if (aqi < 0) return "暂无";
        if (aqi <= 20) return "优";
        if (aqi <= 40) return "良";
        if (aqi <= 60) return "轻度污染";
        if (aqi <= 80) return "中度污染";
        if (aqi <= 100) return "重度污染";
        return "严重污染";
    }

    public static Color color(int aqi) {
        if (aqi < 0) return new Color(0x9E9E9E);
        if (aqi <= 20) return new Color(0x4CAF50);
        if (aqi <= 40) return new Color(0x8BC34A);
        if (aqi <= 60) return new Color(0xFFC107);
        if (aqi <= 80) return new Color(0xFF9800);
        if (aqi <= 100) return new Color(0xFF5722);
        return new Color(0xB71C1C);
    }

    public static Color soft(Color c) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 46);
    }

    public static String advice(int aqi) {
        if (aqi < 0) return "暂无空气质量数据（接口未返回）。";
        if (aqi <= 20) return "空气很好，适合开窗通风与户外活动。";
        if (aqi <= 40) return "空气质量不错，绝大多数人可正常户外活动。";
        if (aqi <= 60) return "敏感人群（儿童、老人、呼吸道疾病者）减少长时间户外活动。";
        if (aqi <= 80) return "建议减少户外运动，外出佩戴口罩。";
        if (aqi <= 100) return "尽量避免户外活动，关闭门窗并使用空气净化设备。";
        return "空气严重污染，老人儿童应留在室内，外出务必佩戴口罩。";
    }

    public static String dominant(WeatherData d) {
        if (!d.hasAir) return "--";
        double max = -1;
        String name = "--";
        double[] vals = {d.pm25, d.pm10, d.ozone / 2, d.no2 * 2, d.so2 * 4};
        String[] names = {"PM2.5", "PM10", "O3", "NO2", "SO2"};
        for (int i = 0; i < vals.length; i++) {
            if (vals[i] > max) {
                max = vals[i];
                name = names[i];
            }
        }
        return max <= 0 ? "--" : name;
    }

    public static String uvLevel(int uv) {
        if (uv <= 2) return "弱";
        if (uv <= 5) return "中等";
        if (uv <= 7) return "强";
        if (uv <= 10) return "很强";
        return "极强";
    }

    public static Color uvColor(int uv) {
        if (uv <= 2) return new Color(0x66BB6A);
        if (uv <= 5) return new Color(0xFFCA28);
        if (uv <= 7) return new Color(0xFFA726);
        if (uv <= 10) return new Color(0xEF5350);
        return new Color(0x8E24AA);
    }

    public static String uvAdvice(int uv) {
        if (uv <= 2) return "紫外线弱，无需特别防晒。";
        if (uv <= 5) return "外出建议涂抹防晒霜。";
        if (uv <= 7) return "戴帽子墨镜，避免正午暴晒。";
        if (uv <= 10) return "尽量减少户外，做好硬防晒。";
        return "避免外出，必须外出需全面防护。";
    }
}
