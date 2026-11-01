package com.example.weather;

import android.content.Context;

import androidx.annotation.DrawableRes;

/**
 * Open-Meteo WMO weather code → 中文描述 + 图标
 * 参考：https://open-meteo.com/en/docs  (WMO Weather interpretation codes)
 */
public final class WeatherCodes {

    private WeatherCodes() {}

    public static String desc(int code, Context ctx) {
        // 简洁中文，无需外部资源
        switch (code) {
            case 0:  return "晴";
            case 1:  return "晴间多云";
            case 2:  return "局部多云";
            case 3:  return "阴";
            case 45:
            case 48: return "雾";
            case 51:
            case 53:
            case 55: return "毛毛雨";
            case 56:
            case 57: return "冻毛雨";
            case 61: return "小雨";
            case 63: return "中雨";
            case 65: return "大雨";
            case 66:
            case 67: return "冻雨";
            case 71: return "小雪";
            case 73: return "中雪";
            case 75: return "大雪";
            case 77: return "雪粒";
            case 80: return "阵雨";
            case 81: return "强阵雨";
            case 82: return "暴雨";
            case 85: return "阵雪";
            case 86: return "强阵雪";
            case 95: return "雷阵雨";
            case 96:
            case 99: return "雷阵雨伴冰雹";
            default: return "未知";
        }
    }

    @DrawableRes
    public static int icon(int code) {
        return icon(code, false);
    }

    /** 是否夜间由调用方传入（基于本地时间） */
    @DrawableRes
    public static int icon(int code, boolean night) {
        if (code == 0) return night ? R.drawable.ic_moon : R.drawable.ic_sun;
        if (code == 1) return night ? R.drawable.ic_cloud_moon : R.drawable.ic_cloud_sun;
        if (code == 2 || code == 3 || code == 45 || code == 48)
            return night ? R.drawable.ic_cloud_moon : R.drawable.ic_cloud;
        if (code >= 51 && code <= 67) return night ? R.drawable.ic_rain_night : R.drawable.ic_rain;
        if (code >= 71 && code <= 77) return R.drawable.ic_snow;
        if (code >= 80 && code <= 82) return night ? R.drawable.ic_rain_night : R.drawable.ic_rain;
        if (code >= 85 && code <= 86) return R.drawable.ic_snow;
        if (code >= 95) return R.drawable.ic_storm;
        return night ? R.drawable.ic_moon : R.drawable.ic_sun;
    }

    /** 依据当前本地时间判断是否“夜间”（18:00–06:00） */
    public static boolean isNightNow() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return h >= 18 || h < 6;
    }
}
