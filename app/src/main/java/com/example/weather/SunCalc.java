package com.example.weather;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 简易日出 / 日落计算（NOAA 近似算法）。
 * 用于第三方数据源（MET Norway 等）没有提供日出日落时的兜底，误差通常在 1–2 分钟。
 */
public final class SunCalc {

    private SunCalc() {}

    /**
     * @return {日出 "HH:mm", 日落 "HH:mm"}；极昼极夜等无解情况返回 {"--:--", "--:--"}
     */
    public static String[] times(double lat, double lon, LocalDate date, int tzOffsetMinutes) {
        String[] none = {"--:--", "--:--"};
        if (date == null) return none;
        try {
            int n = (int) ChronoUnit.DAYS.between(LocalDate.of(date.getYear(), 1, 1), date) + 1;
            double gamma = 2 * Math.PI / 365.0 * (n - 1 + (12 - 12) / 24.0);

            double eqTime = 229.18 * (0.000075
                    + 0.001868 * Math.cos(gamma)
                    - 0.032077 * Math.sin(gamma)
                    - 0.014615 * Math.cos(2 * gamma)
                    - 0.040849 * Math.sin(2 * gamma));

            double decl = 0.006918
                    - 0.399912 * Math.cos(gamma)
                    + 0.070257 * Math.sin(gamma)
                    - 0.006758 * Math.cos(2 * gamma)
                    + 0.000907 * Math.sin(2 * gamma)
                    - 0.002697 * Math.cos(3 * gamma)
                    + 0.00148 * Math.sin(3 * gamma);

            double latRad = Math.toRadians(lat);
            double cosHa = Math.cos(Math.toRadians(90.833)) / (Math.cos(latRad) * Math.cos(decl))
                    - Math.tan(latRad) * Math.tan(decl);
            if (cosHa > 1 || cosHa < -1) return none;

            double ha = Math.toDegrees(Math.acos(cosHa));

            // 太阳正午（UTC 分钟）
            double noon = 720 - 4 * lon - eqTime;
            double sunrise = noon - 4 * ha;
            double sunset = noon + 4 * ha;

            return new String[]{fmt(sunrise + tzOffsetMinutes), fmt(sunset + tzOffsetMinutes)};
        } catch (Exception e) {
            return none;
        }
    }

    private static String fmt(double minutes) {
        double m = minutes % 1440;
        if (m < 0) m += 1440;
        int total = (int) Math.round(m);
        return String.format(java.util.Locale.US, "%02d:%02d", (total / 60) % 24, total % 60);
    }
}
