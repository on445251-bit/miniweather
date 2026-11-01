package com.lightweather.desktop;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Open-Meteo 客户端：地理编码 / 天气预报 / 空气质量（零依赖） */
public final class WeatherClient {

    private static final String GEO = "https://geocoding-api.open-meteo.com/v1/search?count=6&language=zh&format=json&name=";
    private static final String AIR = "https://air-quality-api.open-meteo.com/v1/air-quality?current=european_aqi,us_aqi,"
            + "pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone&timezone=auto&latitude=%s&longitude=%s";
    private static final String FC = "https://api.open-meteo.com/v1/forecast"
            + "?current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,"
            + "wind_direction_10m,surface_pressure,cloud_cover,precipitation,is_day"
            + "&hourly=temperature_2m,weather_code,relative_humidity_2m,precipitation_probability,precipitation,wind_speed_10m"
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max,"
            + "precipitation_sum,wind_speed_10m_max,sunrise,sunset"
            + "&timezone=auto&forecast_days=7&latitude=%s&longitude=%s";

    private WeatherClient() {
    }

    public static final class Geo {
        public final String name;
        public final String admin;
        public final String country;
        public final double lat;
        public final double lon;

        Geo(String name, String admin, String country, double lat, double lon) {
            this.name = name;
            this.admin = admin;
            this.country = country;
            this.lat = lat;
            this.lon = lon;
        }

        public String label() {
            StringBuilder sb = new StringBuilder(name == null || name.isEmpty() ? "未知" : name);
            List<String> extra = new ArrayList<>();
            if (admin != null && !admin.isEmpty() && !admin.equals(name)) extra.add(admin);
            if (country != null && !country.isEmpty()) extra.add(country);
            if (!extra.isEmpty()) sb.append(" · ").append(String.join(", ", extra));
            return sb.toString();
        }

        @Override
        public String toString() {
            return label();
        }
    }

    /** 城市名 → 候选列表 */
    public static List<Geo> geocode(String keyword) throws IOException {
        String url = GEO + URLEncoder.encode(keyword == null ? "" : keyword.trim(), StandardCharsets.UTF_8);
        Object root = Json.parse(get(url));
        List<Geo> out = new ArrayList<>();
        for (Object o : Json.list(root, "results")) {
            out.add(new Geo(
                    Json.s(o, "name", ""),
                    Json.s(o, "admin1", ""),
                    Json.s(o, "country", ""),
                    Json.d(o, "latitude", 0),
                    Json.d(o, "longitude", 0)));
        }
        return out;
    }

    public static WeatherData fetch(double lat, double lon, String city) throws IOException {
        String json = rawForecast(lat, lon);
        WeatherData d = parseForecast(json, city);
        d.lat = lat;
        d.lon = lon;
        try {
            Object air = Json.parse(get(String.format(AIR, fmt(lat), fmt(lon))));
            parseAir(air, d);
        } catch (Exception ignored) {
            d.hasAir = false;
        }
        return d;
    }

    /** 原始预报 JSON（用于离线缓存：以后可直接 parseForecast 还原） */
    public static String rawForecast(double lat, double lon) throws IOException {
        return get(String.format(FC, fmt(lat), fmt(lon)));
    }

    /** 原始空气质量 JSON */
    public static String rawAir(double lat, double lon) throws IOException {
        return get(String.format(AIR, fmt(lat), fmt(lon)));
    }

    /** 供离线缓存复用：直接把缓存的 JSON 解析成 WeatherData */
    public static WeatherData parseForecast(String json, String city) {
        WeatherData d = new WeatherData();
        d.city = city == null ? "" : city;
        Object root = Json.parse(json);

        Object cur = Json.get(root, "current");
        d.currentTemp = Json.i(cur, "temperature_2m", 0);
        d.currentCode = Json.i(cur, "weather_code", 0);
        d.feelsLike = Json.i(cur, "apparent_temperature", d.currentTemp);
        d.humidity = Json.i(cur, "relative_humidity_2m", 0);
        d.windSpeed = Json.d(cur, "wind_speed_10m", 0);
        d.windDir = Json.i(cur, "wind_direction_10m", 0);
        d.pressure = Json.d(cur, "surface_pressure", 0);
        d.cloudCover = Json.i(cur, "cloud_cover", 0);
        d.precip = Json.d(cur, "precipitation", 0);
        d.isDay = Json.i(cur, "is_day", 1) == 1;

        List<Object> times = Json.list(Json.get(root, "hourly"), "time");
        List<Object> temps = Json.list(Json.get(root, "hourly"), "temperature_2m");
        List<Object> codes = Json.list(Json.get(root, "hourly"), "weather_code");
        List<Object> hums = Json.list(Json.get(root, "hourly"), "relative_humidity_2m");
        List<Object> pops = Json.list(Json.get(root, "hourly"), "precipitation_probability");
        List<Object> prcs = Json.list(Json.get(root, "hourly"), "precipitation");
        List<Object> winds = Json.list(Json.get(root, "hourly"), "wind_speed_10m");

        int offset = findCurrentHour(times);
        int start = Math.max(0, offset);
        int n = Math.min(24, Math.max(0, times.size() - start));
        d.hourlyTime = new String[n];
        d.hourlyTemp = new int[n];
        d.hourlyCode = new int[n];
        d.hourlyHumidity = new int[n];
        d.hourlyPrecipProb = new int[n];
        d.hourlyPrecip = new double[n];
        d.hourlyWind = new double[n];
        for (int k = 0; k < n; k++) {
            int idx = start + k;
            d.hourlyTime[k] = Json.str(times.get(idx));
            d.hourlyTemp[k] = Json.inum(at(temps, idx), 0);
            d.hourlyCode[k] = Json.inum(at(codes, idx), 0);
            d.hourlyHumidity[k] = Json.inum(at(hums, idx), 0);
            d.hourlyPrecipProb[k] = Json.inum(at(pops, idx), 0);
            d.hourlyPrecip[k] = Json.num(at(prcs, idx), 0);
            d.hourlyWind[k] = Json.num(at(winds, idx), 0);
        }

        Object daily = Json.get(root, "daily");
        List<Object> dates = Json.list(daily, "time");
        List<Object> dCode = Json.list(daily, "weather_code");
        List<Object> dMax = Json.list(daily, "temperature_2m_max");
        List<Object> dMin = Json.list(daily, "temperature_2m_min");
        List<Object> dPop = Json.list(daily, "precipitation_probability_max");
        List<Object> dUv = Json.list(daily, "uv_index_max");
        List<Object> dPrc = Json.list(daily, "precipitation_sum");
        List<Object> dWind = Json.list(daily, "wind_speed_10m_max");
        List<Object> dRise = Json.list(daily, "sunrise");
        List<Object> dSet = Json.list(daily, "sunset");

        int dn = dates.size();
        d.dailyDate = new String[dn];
        d.dailyDay = new String[dn];
        d.dailySunrise = new String[dn];
        d.dailySunset = new String[dn];
        d.dailyMin = new int[dn];
        d.dailyMax = new int[dn];
        d.dailyCode = new int[dn];
        d.dailyPrecipProb = new int[dn];
        d.dailyUv = new int[dn];
        d.dailyPrecip = new double[dn];
        d.dailyWindMax = new double[dn];
        for (int k = 0; k < dn; k++) {
            d.dailyDate[k] = Json.str(dates.get(k));
            d.dailyDay[k] = k == 0 ? "今天" : weekday(d.dailyDate[k]);
            d.dailySunrise[k] = hhmm(Json.str(at(dRise, k)));
            d.dailySunset[k] = hhmm(Json.str(at(dSet, k)));
            d.dailyMin[k] = Json.inum(at(dMin, k), 0);
            d.dailyMax[k] = Json.inum(at(dMax, k), 0);
            d.dailyCode[k] = Json.inum(at(dCode, k), 0);
            d.dailyPrecipProb[k] = Json.inum(at(dPop, k), 0);
            d.dailyUv[k] = Json.inum(at(dUv, k), 0);
            d.dailyPrecip[k] = Json.num(at(dPrc, k), 0);
            d.dailyWindMax[k] = Json.num(at(dWind, k), 0);
        }

        d.updatedAt = System.currentTimeMillis();
        return d;
    }

    public static void parseAir(Object root, WeatherData d) {
        Object cur = Json.get(root, "current");
        if (cur == null) {
            d.hasAir = false;
            return;
        }
        d.aqi = Json.i(cur, "european_aqi", -1);
        d.usAqi = Json.i(cur, "us_aqi", -1);
        d.pm25 = Json.d(cur, "pm2_5", 0);
        d.pm10 = Json.d(cur, "pm10", 0);
        d.ozone = Json.d(cur, "ozone", 0);
        d.no2 = Json.d(cur, "nitrogen_dioxide", 0);
        d.so2 = Json.d(cur, "sulphur_dioxide", 0);
        d.co = Json.d(cur, "carbon_monoxide", 0);
        d.hasAir = d.aqi >= 0;
    }

    // ---------------- 内部工具 ----------------

    private static Object at(List<Object> list, int i) {
        return i >= 0 && i < list.size() ? list.get(i) : null;
    }

    /** 在 hourly.time 中找到"当前这一小时"的下标 */
    private static int findCurrentHour(List<Object> times) {
        if (times.isEmpty()) return 0;
        String now = java.time.LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).toString();
        now = now.substring(0, 16);
        for (int i = 0; i < times.size(); i++) {
            if (now.compareTo(Json.str(times.get(i))) <= 0) return Math.max(0, i - 1);
        }
        return times.size() - 1;
    }

    private static String weekday(String iso) {
        try {
            LocalDate date = LocalDate.parse(iso);
            String[] w = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
            return w[date.getDayOfWeek().getValue() - 1];
        } catch (Exception e) {
            return iso;
        }
    }

    /** "2026-11-01T06:31" → "06:31" */
    public static String hhmm(String iso) {
        if (iso == null) return "--:--";
        int i = iso.indexOf('T');
        if (i < 0) return iso;
        String t = iso.substring(i + 1);
        return t.length() >= 5 ? t.substring(0, 5) : t;
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.4f", v);
    }

    private static String get(String url) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "LightWeather-Desktop/1.0 (Windows)");
                conn.setRequestProperty("Accept", "application/json");
                int code = conn.getResponseCode();
                if (code != 200) throw new IOException("HTTP " + code);
                try (InputStream in = conn.getInputStream()) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                    return bos.toString(StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(400L * (attempt + 1));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        throw last == null ? new IOException("网络异常") : last;
    }
}
