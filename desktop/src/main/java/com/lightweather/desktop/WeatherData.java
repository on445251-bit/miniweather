package com.lightweather.desktop;

/** 天气数据模型（桌面版，与 Android 版字段保持一致，去除 Android 依赖） */
public class WeatherData {

    public String city = "";
    public double lat, lon;

    // 当前实况
    public int currentTemp;
    public int currentCode;
    public int feelsLike;
    public int humidity;
    public double windSpeed;   // km/h
    public int windDir;
    public double pressure;    // hPa
    public int cloudCover;
    public double precip;
    public boolean isDay = true;

    // 逐小时
    public String[] hourlyTime = new String[0];
    public int[] hourlyTemp = new int[0];
    public int[] hourlyCode = new int[0];
    public int[] hourlyHumidity = new int[0];
    public int[] hourlyPrecipProb = new int[0];
    public double[] hourlyPrecip = new double[0];
    public double[] hourlyWind = new double[0];

    // 逐日
    public String[] dailyDate = new String[0];
    public String[] dailyDay = new String[0];
    public String[] dailySunrise = new String[0];
    public String[] dailySunset = new String[0];
    public int[] dailyMin = new int[0];
    public int[] dailyMax = new int[0];
    public int[] dailyCode = new int[0];
    public int[] dailyPrecipProb = new int[0];
    public int[] dailyUv = new int[0];
    public double[] dailyPrecip = new double[0];
    public double[] dailyWindMax = new double[0];

    // 空气质量
    public boolean hasAir;
    public int aqi;
    public int usAqi;
    public double pm25, pm10, ozone, no2, so2, co;

    public long updatedAt;

    public boolean hasHourly() {
        return hourlyTime != null && hourlyTime.length > 0 && hourlyTemp != null && hourlyTemp.length > 0;
    }

    public boolean hasDaily() {
        return dailyDay != null && dailyDay.length > 0 && dailyMax != null && dailyMax.length > 0;
    }
}
