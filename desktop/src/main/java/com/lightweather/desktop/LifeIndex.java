package com.lightweather.desktop;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** 生活指数：完全本地规则计算，不额外请求接口 */
public final class LifeIndex {

    private LifeIndex() {
    }

    public static final class Item {
        public final String name;
        public final String value;
        public final String detail;
        public final Color color;

        public Item(String name, String value, String detail, Color color) {
            this.name = name;
            this.value = value;
            this.detail = detail;
            this.color = color;
        }
    }

    public static List<Item> compute(WeatherData d) {
        List<Item> out = new ArrayList<>();
        if (d == null) return out;

        // 穿衣
        int t = d.currentTemp;
        String cloth;
        String clothTip;
        if (t >= 30) {
            cloth = "短袖";
            clothTip = "炎热，轻薄透气为主";
        } else if (t >= 24) {
            cloth = "短袖";
            clothTip = "舒适，短袖短裤即可";
        } else if (t >= 18) {
            cloth = "长袖";
            clothTip = "凉爽，建议加件薄外套";
        } else if (t >= 12) {
            cloth = "外套";
            clothTip = "偏凉，注意保暖";
        } else if (t >= 5) {
            cloth = "大衣";
            clothTip = "较冷，厚外套 + 毛衣";
        } else if (t >= 0) {
            cloth = "棉服";
            clothTip = "寒冷，羽绒服安排";
        } else {
            cloth = "羽绒";
            clothTip = "严寒，注意防冻";
        }
        out.add(new Item("穿衣", cloth, clothTip, new Color(0x42A5F5)));

        // 紫外线
        int uv = d.hasDaily() && d.dailyUv.length > 0 ? d.dailyUv[0] : 0;
        out.add(new Item("紫外线", AirQuality.uvLevel(uv), AirQuality.uvAdvice(uv), AirQuality.uvColor(uv)));

        // 洗车
        double rain = d.hasDaily() && d.dailyPrecip.length > 0 ? d.dailyPrecip[0] : 0;
        int pop = d.hasDaily() && d.dailyPrecipProb.length > 0 ? d.dailyPrecipProb[0] : 0;
        if (rain > 1 || pop >= 60) {
            out.add(new Item("洗车", "不宜", "有降水，洗车易脏", new Color(0x90A4AE)));
        } else if (pop >= 30) {
            out.add(new Item("洗车", "较不宜", "可能有零星降水", new Color(0x8D6E63)));
        } else {
            out.add(new Item("洗车", "适宜", "天气不错，放心洗车", new Color(0x26A69A)));
        }

        // 运动
        boolean badWind = d.windSpeed >= 45;
        boolean badRain = (d.hasHourly() && d.hourlyPrecipProb.length > 0 && d.hourlyPrecipProb[0] >= 70);
        if (badWind || badRain || t >= 35 || t <= -5) {
            out.add(new Item("运动", "不宜", "天气条件较差，建议室内", new Color(0xEF5350)));
        } else if (t >= 28 || d.windSpeed >= 30) {
            out.add(new Item("运动", "较适宜", "注意补水与防风", new Color(0xFFA726)));
        } else {
            out.add(new Item("运动", "适宜", "适合户外跑步与球类", new Color(0x66BB6A)));
        }

        // 晾晒
        if (rain > 0.5 || pop >= 50) {
            out.add(new Item("晾晒", "不宜", "有降水，衣物难干", new Color(0x90A4AE)));
        } else if (d.cloudCover >= 80) {
            out.add(new Item("晾晒", "较不宜", "云量较多，日照不足", new Color(0x8D6E63)));
        } else {
            out.add(new Item("晾晒", "适宜", "阳光充足，干得快", new Color(0xFFB300)));
        }

        // 感冒
        int diff = d.hasDaily() && d.dailyMax.length > 0 && d.dailyMin.length > 0
                ? d.dailyMax[0] - d.dailyMin[0] : 0;
        if (diff >= 12 || t <= 3) {
            out.add(new Item("感冒", "易发", "温差大，注意增减衣物", new Color(0xEF5350)));
        } else if (diff >= 8) {
            out.add(new Item("感冒", "较易发", "早晚偏凉，小心着凉", new Color(0xFFA726)));
        } else {
            out.add(new Item("感冒", "少发", "天气平稳，风险较低", new Color(0x66BB6A)));
        }

        return out;
    }
}
