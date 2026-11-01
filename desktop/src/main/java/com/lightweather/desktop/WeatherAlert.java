package com.lightweather.desktop;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** 本地规则生成的天气提醒（不依赖第三方预警接口） */
public final class WeatherAlert {

    private WeatherAlert() {
    }

    public static final class Alert {
        public final int level;      // 0 提示 1 注意 2 较高 3 严重
        public final String title;
        public final String text;

        public Alert(int level, String title, String text) {
            this.level = level;
            this.title = title;
            this.text = text;
        }

        public String levelText() {
            switch (level) {
                case 0: return "提示";
                case 1: return "注意";
                case 2: return "较高";
                default: return "严重";
            }
        }
    }

    public static List<Alert> compute(WeatherData d) {
        List<Alert> out = new ArrayList<>();
        if (d == null) return out;

        if (d.currentTemp >= 35) {
            out.add(new Alert(2, "高温", "当前 " + d.currentTemp + "°，注意防暑补水，避免长时间户外。"));
        } else if (d.currentTemp <= -5) {
            out.add(new Alert(2, "严寒", "当前 " + d.currentTemp + "°，注意保暖防冻。"));
        }

        if (d.windSpeed >= 62) {
            out.add(new Alert(3, "大风", String.format("风速约 %.0f km/h，尽量避免外出，远离广告牌与临时搭建物。", d.windSpeed)));
        } else if (d.windSpeed >= 39) {
            out.add(new Alert(1, "大风", String.format("风速约 %.0f km/h，注意高空坠物。", d.windSpeed)));
        }

        if (d.currentCode >= 95) {
            out.add(new Alert(2, "雷电", "有雷暴天气，暂停户外活动，远离金属与大树。"));
        }

        double rain = d.hasDaily() && d.dailyPrecip.length > 0 ? d.dailyPrecip[0] : 0;
        int pop = d.hasDaily() && d.dailyPrecipProb.length > 0 ? d.dailyPrecipProb[0] : 0;
        if (rain >= 50) {
            out.add(new Alert(3, "暴雨", String.format("今日降水约 %.0f mm，注意城市内涝与低洼路段。", rain)));
        } else if (rain >= 25) {
            out.add(new Alert(2, "强降雨", String.format("今日降水约 %.0f mm，出行请带雨具并留意路面积水。", rain)));
        } else if (pop >= 80 && rain < 1) {
            out.add(new Alert(0, "阵雨", "降水概率较高，出门记得带伞。"));
        }

        if (d.currentCode >= 71 && d.currentCode <= 86) {
            out.add(new Alert(1, "降雪", "有降雪，路面湿滑，注意出行安全。"));
        }

        if (d.hasAir && d.aqi > 80) {
            out.add(new Alert(d.aqi > 100 ? 3 : 2, "空气污染",
                    "AQI " + d.aqi + "（" + AirQuality.level(d.aqi) + "），减少户外活动。"));
        }

        if (d.hasDaily() && d.dailyMax.length > 0 && d.dailyMin.length > 0) {
            int diff = d.dailyMax[0] - d.dailyMin[0];
            if (diff >= 12) {
                out.add(new Alert(1, "昼夜温差", "今日温差达 " + diff + "°，注意增减衣物。"));
            }
        }
        return out;
    }

    public static Alert top(List<Alert> list) {
        if (list == null || list.isEmpty()) return null;
        Alert best = list.get(0);
        for (Alert a : list) {
            if (a.level > best.level) best = a;
        }
        return best;
    }

    public static Color color(int level) {
        switch (level) {
            case 0: return new Color(0x2E7D32);
            case 1: return new Color(0xEF6C00);
            case 2: return new Color(0xE53935);
            default: return new Color(0xAD1457);
        }
    }

    public static Color soft(int level) {
        Color c = color(level);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 34);
    }
}
