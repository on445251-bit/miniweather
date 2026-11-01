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

    // =====================================================================
    // 第三方数据源代码 → WMO 代码（统一图标与描述）
    // =====================================================================

    /** WorldWeatherOnline（wttr.in）天气代码 → WMO */
    public static int fromWwo(int code) {
        switch (code) {
            case 113: return 0;
            case 116: return 1;
            case 119: return 2;
            case 122: return 3;
            case 143:
            case 248: return 45;
            case 260: return 48;
            case 176:
            case 353: return 80;
            case 263: return 51;
            case 266: return 53;
            case 293:
            case 296: return 61;
            case 299:
            case 302: return 63;
            case 305:
            case 308: return 65;
            case 356: return 81;
            case 359: return 82;
            case 179:
            case 182:
            case 185:
            case 281:
            case 284:
            case 311:
            case 314: return 56;
            case 227:
            case 320:
            case 323: return 71;
            case 326:
            case 329: return 73;
            case 230:
            case 332:
            case 335:
            case 338: return 75;
            case 350:
            case 374:
            case 377: return 77;
            case 368: return 85;
            case 371: return 86;
            case 200:
            case 386: return 95;
            case 389:
            case 392:
            case 395: return 96;
            default: return 3;
        }
    }

    /** MET Norway symbol_code（如 clearsky_day / lightrainandthunder）→ WMO */
    public static int fromMetSymbol(String symbol) {
        if (symbol == null) return 3;
        String s = symbol;
        int us = s.indexOf('_');
        if (us > 0) s = s.substring(0, us);
        switch (s) {
            case "clearsky": return 0;
            case "fair": return 1;
            case "partlycloudy": return 2;
            case "cloudy": return 3;
            case "fog": return 45;
            case "lightrain": return 61;
            case "rain": return 63;
            case "heavyrain": return 65;
            case "lightrainshowers": return 80;
            case "rainshowers": return 81;
            case "heavyrainshowers": return 82;
            case "lightrainshowersandthunder": return 95;
            case "rainshowersandthunder": return 96;
            case "lightdrizzle": return 51;
            case "drizzle": return 53;
            case "heavydrizzle": return 55;
            case "lightdrizzleandrain": return 61;
            case "drizzleandrain": return 63;
            case "lightrainanddrizzle": return 61;
            case "lightsleet": return 66;
            case "sleet": return 66;
            case "heavysleet": return 67;
            case "lightsnow": return 71;
            case "snow": return 73;
            case "heavysnow": return 75;
            case "lightsnowshowers": return 85;
            case "snowshowers": return 86;
            case "lightsleetshowers":
            case "sleetshowers":
            case "heavysleetshowers": return 66;
            case "rainandthunder": return 95;
            case "heavyrainandthunder": return 96;
            case "snowandthunder": return 95;
            case "sleetandthunder": return 96;
            case "partlycloudyandthunder":
            case "cloudyandthunder": return 95;
            default: return 3;
        }
    }
}
