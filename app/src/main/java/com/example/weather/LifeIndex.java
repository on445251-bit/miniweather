package com.example.weather;

import java.util.ArrayList;
import java.util.List;

/**
 * 生活指数：全部由已获取的天气数据本地推算，无需额外接口。
 * 六项：穿衣 / 紫外线 / 洗车 / 运动 / 晾晒 / 感冒
 */
public final class LifeIndex {

    public static class Item {
        public final String name;
        public final String value;
        public final String detail;
        public final int color;

        Item(String name, String value, String detail, int color) {
            this.name = name;
            this.value = value;
            this.detail = detail;
            this.color = color;
        }
    }

    private static final int GREEN  = 0xFF2E7D32;
    private static final int YELLOW = 0xFFF9A825;
    private static final int ORANGE = 0xFFEF6C00;
    private static final int RED    = 0xFFC62828;
    private static final int BLUE   = 0xFF1565C0;

    private LifeIndex() {}

    public static List<Item> compute(WeatherData d) {
        List<Item> list = new ArrayList<>();
        if (d == null) return list;

        int pop = 0;      // 未来 24h 最大降水概率
        double rain = 0;  // 未来 24h 累计降水
        if (d.hourlyPrecipProb != null) {
            for (int v : d.hourlyPrecipProb) pop = Math.max(pop, v);
        }
        if (d.hourlyPrecip != null) {
            for (double v : d.hourlyPrecip) rain += v;
        }
        int diff = 0;
        int uv = 0;
        if (d.hasDaily()) {
            diff = d.dailyMax[0] - d.dailyMin[0];
            uv = d.dailyUv.length > 0 ? d.dailyUv[0] : 0;
        }

        list.add(clothing(d));
        list.add(uvIndex(uv));
        list.add(carWash(pop, rain));
        list.add(sport(d, pop));
        list.add(drying(d.humidity, pop));
        list.add(cold(diff, d.currentTemp));
        return list;
    }

    private static Item clothing(WeatherData d) {
        int t = d.feelsLike;
        if (t >= 30) return new Item("穿衣", "炎热", "短袖短裤，注意补水防晒", RED);
        if (t >= 25) return new Item("穿衣", "热", "短袖薄衫，室内注意空调", ORANGE);
        if (t >= 20) return new Item("穿衣", "舒适", "长袖衬衫或薄外套", GREEN);
        if (t >= 15) return new Item("穿衣", "微凉", "薄外套 + 长裤", GREEN);
        if (t >= 10) return new Item("穿衣", "凉", "夹克或风衣，注意保暖", BLUE);
        if (t >= 5)  return new Item("穿衣", "冷", "厚外套 + 毛衫", BLUE);
        if (t >= 0)  return new Item("穿衣", "寒冷", "羽绒服或棉衣", ORANGE);
        return new Item("穿衣", "严寒", "羽绒服 + 帽子手套", RED);
    }

    private static Item uvIndex(int uv) {
        return new Item("紫外线", AirQuality.uvLevel(uv),
                "UV " + uv + " · " + AirQuality.uvAdvice(uv), AirQuality.uvColor(uv));
    }

    private static Item carWash(int pop, double rain) {
        if (rain >= 5 || pop >= 70) return new Item("洗车", "不宜", "未来 24 小时有降水", RED);
        if (rain >= 1 || pop >= 40) return new Item("洗车", "较不宜", "可能有零星降水", ORANGE);
        return new Item("洗车", "适宜", "未来 24 小时无明显降水", GREEN);
    }

    private static Item sport(WeatherData d, int pop) {
        int t = d.currentTemp;
        if (d.hasAir && d.aqi >= 80) return new Item("运动", "不宜", "空气质量较差，建议室内", RED);
        if (t >= 32 || t <= 0) return new Item("运动", "较不宜", "气温极端，注意强度", ORANGE);
        if (pop >= 60) return new Item("运动", "较不宜", "降水概率高，宜室内", ORANGE);
        if (t >= 15 && t <= 28 && pop < 30) return new Item("运动", "适宜", "温度舒适，适合户外", GREEN);
        return new Item("运动", "一般", "可适量户外运动", YELLOW);
    }

    private static Item drying(int humidity, int pop) {
        if (pop >= 60) return new Item("晾晒", "不宜", "降水概率高", RED);
        if (humidity >= 85) return new Item("晾晒", "较不宜", "湿度大，不易干", ORANGE);
        if (humidity <= 60 && pop < 30) return new Item("晾晒", "非常适宜", "干燥且有阳光", GREEN);
        return new Item("晾晒", "适宜", "可以晾晒衣物", GREEN);
    }

    private static Item cold(int diff, int temp) {
        if (diff >= 12 || temp <= 3) return new Item("感冒", "易发", "昼夜温差大，注意增减衣物", RED);
        if (diff >= 8) return new Item("感冒", "较易发", "早晚温差较大", YELLOW);
        return new Item("感冒", "少发", "气温平稳，注意通风", GREEN);
    }
}
