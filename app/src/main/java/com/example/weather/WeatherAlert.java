package com.example.weather;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地规则生成的天气预警（不依赖任何第三方预警接口）。
 * 等级：0 提示 / 1 注意 / 2 警告 / 3 严重
 */
public final class WeatherAlert {

    public static class Alert {
        public final int level;
        public final String title;
        public final String text;

        Alert(int level, String title, String text) {
            this.level = level;
            this.title = title;
            this.text = text;
        }

        public String levelText() {
            switch (level) {
                case 3: return "严重";
                case 2: return "警告";
                case 1: return "注意";
                default: return "提示";
            }
        }
    }

    private WeatherAlert() {}

    public static List<Alert> compute(WeatherData d) {
        List<Alert> list = new ArrayList<>();
        if (d == null) return list;

        int hi = d.hasDaily() ? max(d.dailyMax) : d.currentTemp;
        int lo = d.hasDaily() ? min(d.dailyMin) : d.currentTemp;
        double wind = Math.max(d.windSpeed, d.hasDaily() && d.dailyWindMax.length > 0
                ? max(d.dailyWindMax) : 0);
        double rain = d.hasDaily() && d.dailyPrecip.length > 0 ? max(d.dailyPrecip) : 0;
        int rainPop = d.hasDaily() && d.dailyPrecipProb.length > 0 ? max(d.dailyPrecipProb) : 0;

        // 高温
        if (hi >= 37) {
            list.add(new Alert(3, "高温红色预警",
                    "未来几天最高气温可达 " + hi + "°C，请避免户外暴晒、及时补水。"));
        } else if (hi >= 35) {
            list.add(new Alert(2, "高温橙色预警",
                    "未来几天最高气温可达 " + hi + "°C，注意防暑降温。"));
        }

        // 低温
        if (lo <= -15) {
            list.add(new Alert(3, "严寒预警",
                    "最低气温将降至 " + lo + "°C，注意防冻与供暖安全。"));
        } else if (lo <= -8) {
            list.add(new Alert(2, "低温预警",
                    "最低气温将降至 " + lo + "°C，外出注意保暖。"));
        }

        // 大风（6 级 ≈ 39 km/h，8 级 ≈ 62 km/h）
        if (wind >= 62) {
            list.add(new Alert(3, "大风橙色预警",
                    "阵风可达 " + Math.round(wind) + " km/h，请远离广告牌与临时建筑。"));
        } else if (wind >= 39) {
            list.add(new Alert(1, "大风蓝色预警",
                    "风力较大（" + Math.round(wind) + " km/h），注意高空坠物。"));
        }

        // 暴雨
        if (rain >= 50) {
            list.add(new Alert(3, "暴雨红色预警",
                    "累计降水量可达 " + Math.round(rain) + " mm，注意防范积涝与地质灾害。"));
        } else if (rain >= 25) {
            list.add(new Alert(2, "暴雨黄色预警",
                    "累计降水量约 " + Math.round(rain) + " mm，出行请带好雨具。"));
        } else if (rainPop >= 80) {
            list.add(new Alert(1, "降水提醒",
                    "降水概率高达 " + rainPop + "%，出门记得带伞。"));
        }

        // 雷电 / 降雪
        if (containsCode(d, 95) || containsCode(d, 96) || containsCode(d, 99)) {
            list.add(new Alert(2, "雷电预警",
                    "预报有雷雨天气，请避免在空旷地带停留。"));
        }
        if (containsCode(d, 75) || containsCode(d, 86)) {
            list.add(new Alert(2, "降雪预警",
                    "预报有大雪，注意道路结冰与交通安全。"));
        }

        // 空气污染
        if (d.hasAir && d.aqi >= 100) {
            list.add(new Alert(2, "空气重污染",
                    "AQI " + d.aqi + "，" + AirQuality.advice(d.aqi)));
        } else if (d.hasAir && d.aqi >= 80) {
            list.add(new Alert(1, "空气质量较差",
                    "AQI " + d.aqi + "，" + AirQuality.advice(d.aqi)));
        }

        // 昼夜温差
        if (d.hasDaily() && d.dailyMax[0] - d.dailyMin[0] >= 15) {
            list.add(new Alert(0, "昼夜温差大",
                    "今天温差达 " + (d.dailyMax[0] - d.dailyMin[0]) + "°C，早晚注意添衣。"));
        }

        return list;
    }

    /** 取等级最高的一条 */
    public static Alert top(List<Alert> list) {
        if (list == null || list.isEmpty()) return null;
        Alert best = list.get(0);
        for (Alert a : list) if (a.level > best.level) best = a;
        return best;
    }

    public static int color(int level) {
        switch (level) {
            case 3: return 0xFFC62828;
            case 2: return 0xFFEF6C00;
            case 1: return 0xFFF9A825;
            default: return 0xFF1565C0;
        }
    }

    private static boolean containsCode(WeatherData d, int code) {
        if (d.hasHourly()) {
            for (int c : d.hourlyCode) if (c == code) return true;
        }
        if (d.hasDaily()) {
            for (int c : d.dailyCode) if (c == code) return true;
        }
        return d.currentCode == code;
    }

    private static int max(int[] a) {
        int m = Integer.MIN_VALUE;
        for (int v : a) m = Math.max(m, v);
        return m == Integer.MIN_VALUE ? 0 : m;
    }

    private static int min(int[] a) {
        int m = Integer.MAX_VALUE;
        for (int v : a) m = Math.min(m, v);
        return m == Integer.MAX_VALUE ? 0 : m;
    }

    private static double max(double[] a) {
        double m = -Double.MAX_VALUE;
        for (double v : a) m = Math.max(m, v);
        return m == -Double.MAX_VALUE ? 0 : m;
    }
}
