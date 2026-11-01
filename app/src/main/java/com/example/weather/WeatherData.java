package com.example.weather;

/** 天气数据载体（解析自 Open-Meteo JSON） */
public class WeatherData {
    public String city = "";
    public double lat;
    public double lon;

    // 当前
    public int currentTemp;       // 四舍五入
    public int currentCode;
    public int feelsLike;
    public int humidity;
    public double windSpeed;

    // 24 小时（接下来 24 个时段）
    public String[] hourlyTime;
    public int[] hourlyTemp;
    public int[] hourlyCode;

    // 7 天
    public String[] dailyDay;     // 周几 / 日期
    public int[] dailyMin;
    public int[] dailyMax;
    public int[] dailyCode;
}
