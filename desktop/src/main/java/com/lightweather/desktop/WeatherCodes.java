package com.lightweather.desktop;

/** WMO 天气代码 → 中文描述 / 图标种类 */
public final class WeatherCodes {

    private WeatherCodes() {
    }

    public enum Kind { SUN, MOON, PARTLY, PARTLY_NIGHT, CLOUD, FOG, DRIZZLE, RAIN, SHOWERS, SNOW, STORM }

    public static String desc(int code) {
        switch (code) {
            case 0: return "晴";
            case 1: return "晴间多云";
            case 2: return "多云";
            case 3: return "阴";
            case 45: return "雾";
            case 48: return "雾凇";
            case 51: return "小毛毛雨";
            case 53: return "毛毛雨";
            case 55: return "大毛毛雨";
            case 56: return "冻毛毛雨";
            case 57: return "强冻毛毛雨";
            case 61: return "小雨";
            case 63: return "中雨";
            case 65: return "大雨";
            case 66: return "冻雨";
            case 67: return "强冻雨";
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
            case 96: return "雷阵雨伴冰雹";
            case 99: return "强雷暴伴冰雹";
            default: return "未知";
        }
    }

    public static Kind kind(int code, boolean night) {
        if (code == 0) return night ? Kind.MOON : Kind.SUN;
        if (code == 1) return night ? Kind.PARTLY_NIGHT : Kind.PARTLY;
        if (code == 2 || code == 3) return Kind.CLOUD;
        if (code == 45 || code == 48) return Kind.FOG;
        if (code >= 51 && code <= 57) return Kind.DRIZZLE;
        if (code >= 61 && code <= 67) return Kind.RAIN;
        if (code >= 71 && code <= 77) return Kind.SNOW;
        if (code >= 80 && code <= 82) return Kind.SHOWERS;
        if (code == 85 || code == 86) return Kind.SNOW;
        if (code >= 95) return Kind.STORM;
        return Kind.CLOUD;
    }

    /** 依据 "2026-11-01T14:00" 判断该小时是否属夜间（18:00–06:00） */
    public static boolean isNightHour(String t) {
        if (t == null) return false;
        int i = t.indexOf('T');
        if (i < 0 || t.length() < i + 3) return false;
        try {
            int hour = Integer.parseInt(t.substring(i + 1, i + 3));
            return hour >= 18 || hour < 6;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** wttr.in / WorldWeatherOnline 代码 → WMO */
    public static int fromWwo(int code) {
        switch (code) {
            case 113: return 0;
            case 116: return 1;
            case 119: return 2;
            case 122: return 3;
            case 143: case 248: case 260: return 45;
            case 176: return 80;
            case 179: return 85;
            case 182: case 185: return 66;
            case 200: return 95;
            case 227: return 71;
            case 230: return 75;
            case 263: case 266: return 51;
            case 281: case 284: return 56;
            case 293: case 296: return 61;
            case 299: case 302: return 63;
            case 305: case 308: return 65;
            case 311: case 314: return 66;
            case 317: case 320: return 71;
            case 323: case 326: return 71;
            case 329: case 332: return 73;
            case 335: case 338: return 75;
            case 350: return 77;
            case 353: return 80;
            case 356: return 81;
            case 359: return 82;
            case 362: case 365: return 85;
            case 368: case 371: return 85;
            case 374: case 377: return 86;
            case 386: case 389: return 95;
            case 392: case 395: return 86;
            default: return 3;
        }
    }

    /** MET Norway symbol_code → WMO */
    public static int fromMetSymbol(String symbol) {
        if (symbol == null) return 3;
        String s = symbol;
        int us = s.indexOf('_');
        if (us > 0 && (s.endsWith("_day") || s.endsWith("_night") || s.endsWith("_polartwilight"))) {
            s = s.substring(0, us);
        }
        if (s.startsWith("clearsky")) return 0;
        if (s.startsWith("fair")) return 1;
        if (s.startsWith("partlycloudy")) return 2;
        if (s.startsWith("cloudy")) return 3;
        if (s.startsWith("fog")) return 45;
        if (s.startsWith("lightrain")) return 61;
        if (s.startsWith("rain")) return 63;
        if (s.startsWith("heavyrain")) return 65;
        if (s.startsWith("lightrainshowers")) return 80;
        if (s.startsWith("rainshowers")) return 80;
        if (s.startsWith("heavyrainshowers")) return 82;
        if (s.startsWith("lightsleet")) return 66;
        if (s.startsWith("sleet")) return 66;
        if (s.startsWith("lightsnow")) return 71;
        if (s.startsWith("snow")) return 73;
        if (s.startsWith("heavysnow")) return 75;
        if (s.startsWith("lightsnowshowers")) return 85;
        if (s.startsWith("snowshowers")) return 85;
        if (s.startsWith("heavysnowshowers")) return 86;
        if (s.startsWith("rainandthunder")) return 95;
        if (s.startsWith("snowandthunder")) return 95;
        if (s.startsWith("sleetandthunder")) return 95;
        return 3;
    }
}
