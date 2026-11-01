package com.example.weather;

/**
 * 天气数据模型（v2.0 扩展版）。
 * 纯 POJO，不依赖任何第三方库；字段全部 public，供 UI / 缓存直接读写。
 */
public class WeatherData {

    /** 城市展示名 */
    public String city = "";
    /** 所属省 / 州（例如「浙江省」），用于避免同名城市混淆 */
    public String area = "";
    /** 数据来源名称（Open-Meteo / wttr.in / MET Norway） */
    public String source = "";
    /** 纬度 / 经度 */
    public double lat, lon;

    // ---------- 当前实况 ----------
    public int currentTemp;        // ℃
    public int currentCode;        // WMO 天气代码
    public int feelsLike;          // 体感 ℃
    public int humidity;           // 湿度 %
    public double windSpeed;       // 风速 km/h
    public int windDir;            // 风向角度（0=北，顺时针）
    public double pressure;        // 气压 hPa
    public int cloudCover;         // 云量 %
    public double precip;          // 当前降水 mm
    public boolean isDay = true;   // 白天 / 夜间（服务端判定，比本地时间更准）

    // ---------- 逐小时（24 小时） ----------
    public String[] hourlyTime;        // "yyyy-MM-dd'T'HH:mm"
    public int[] hourlyTemp;
    public int[] hourlyCode;
    public int[] hourlyHumidity;
    public int[] hourlyPrecipProb;     // 降水概率 %
    public double[] hourlyPrecip;      // 降水量 mm
    public double[] hourlyWind;        // 风速 km/h

    // ---------- 逐日（7 天） ----------
    public String[] dailyDate;         // "yyyy-MM-dd"
    public String[] dailyDay;          // 今天 / 周一 …
    public String[] dailySunrise;      // "HH:mm"
    public String[] dailySunset;       // "HH:mm"
    public int[] dailyMin;
    public int[] dailyMax;
    public int[] dailyCode;
    public int[] dailyPrecipProb;      // 降水概率 %
    public int[] dailyUv;              // 紫外线指数
    public double[] dailyPrecip;       // 降水量 mm
    public double[] dailyWindMax;      // 最大风速 km/h

    // ---------- 空气质量（可能缺失） ----------
    public boolean hasAir = false;
    public int aqi;                    // 欧洲 AQI（0–500，主指标）
    public int usAqi;                  // 美国 AQI
    public double pm25;
    public double pm10;
    public double ozone;
    public double no2;
    public double so2;
    public double co;

    /** 数据写入时间戳（毫秒） */
    public long updatedAt;

    /** 便于判断数组是否可用 */
    public boolean hasHourly() {
        return hourlyTime != null && hourlyTemp != null && hourlyTemp.length > 0;
    }

    public boolean hasDaily() {
        return dailyDay != null && dailyMin != null && dailyMax != null && dailyDay.length > 0;
    }
}
