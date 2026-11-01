package com.lightweather.desktop;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** NOAA 近似算法计算日出日落（不依赖网络） */
public final class SunCalc {

    private SunCalc() {
    }

    /**
     * @param lat            纬度
     * @param lon            经度
     * @param date           日期
     * @param tzOffsetMinutes 时区偏移（分钟，例如 +480 表示东八区）
     * @return {"日出 HH:mm", "日落 HH:mm"}，极昼/极夜返回 {"--:--","--:--"}
     */
    public static String[] times(double lat, double lon, LocalDate date, int tzOffsetMinutes) {
        double rad = Math.PI / 180.0;
        int n = date.getDayOfYear();
        double g = 2 * Math.PI / 365.0 * (n - 1 + (12 - 12) / 24.0);
        double eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g)
                - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
        double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g)
                - 0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g)
                - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);

        double cosHa = Math.cos(90.833 * rad) / (Math.cos(lat * rad) * Math.cos(decl))
                - Math.tan(lat * rad) * Math.tan(decl);
        if (cosHa < -1 || cosHa > 1) {
            return new String[]{"--:--", "--:--"};
        }
        double ha = Math.acos(cosHa) / rad;

        double riseUtc = 720 - 4 * (lon + ha) - eqTime;
        double setUtc = 720 - 4 * (lon - ha) - eqTime;
        return new String[]{fmt(riseUtc + tzOffsetMinutes), fmt(setUtc + tzOffsetMinutes)};
    }

    /** 用当前系统时区计算今天的日出日落 */
    public static String[] today(double lat, double lon) {
        LocalDateTime now = LocalDateTime.now();
        int tz = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000;
        return times(lat, lon, now.toLocalDate(), tz);
    }

    private static String fmt(double minutes) {
        double m = minutes % (24 * 60);
        if (m < 0) m += 24 * 60;
        int total = (int) Math.round(m);
        int hh = (total / 60) % 24;
        int mm = total % 60;
        return String.format(java.util.Locale.US, "%02d:%02d", hh, mm);
    }
}
