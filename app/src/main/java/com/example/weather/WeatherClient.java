package com.example.weather;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 极简 HTTP 客户端：仅依赖 org.json（无第三方网络库、无 API Key）。
 * 数据来源：
 *  · Open-Meteo Forecast   https://api.open-meteo.com/v1/forecast
 *  · Open-Meteo Air Quality https://air-quality-api.open-meteo.com/v1/air-quality
 *  · Open-Meteo Geocoding  https://geocoding-api.open-meteo.com/v1/search
 */
public final class WeatherClient {

    private static final String TAG = "WeatherClient";

    private static final int CONNECT_TIMEOUT = 10_000;
    private static final int READ_TIMEOUT = 12_000;

    private static final String GEO_BASE =
            "https://geocoding-api.open-meteo.com/v1/search";

    private static final String FC_BASE =
            "https://api.open-meteo.com/v1/forecast";

    private static final String AIR_BASE =
            "https://air-quality-api.open-meteo.com/v1/air-quality";

    private static final String FC_QUERY =
            "?current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,"
                    + "precipitation,weather_code,cloud_cover,surface_pressure,"
                    + "wind_speed_10m,wind_direction_10m"
                    + "&hourly=temperature_2m,relative_humidity_2m,precipitation_probability,"
                    + "precipitation,weather_code,wind_speed_10m"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,"
                    + "uv_index_max,precipitation_sum,precipitation_probability_max,wind_speed_10m_max"
                    + "&timezone=auto&forecast_days=7";

    private static final String AIR_QUERY =
            "?current=european_aqi,us_aqi,pm10,pm2_5,carbon_monoxide,"
                    + "nitrogen_dioxide,sulphur_dioxide,ozone&timezone=auto";

    private WeatherClient() {}

    // =====================================================================
    // 地理编码
    // =====================================================================

    /** 城市搜索候选 */
    public static class Geo {
        public String name = "";
        public String admin = "";
        public String country = "";
        public double lat, lon;

        /** 例如「北京 · 中国」或「Springfield · 美国 伊利诺伊州」 */
        public String label() {
            StringBuilder sb = new StringBuilder(name);
            if (!admin.isEmpty() && !admin.equals(name)) sb.append(" · ").append(admin);
            if (!country.isEmpty()) sb.append(", ").append(country);
            return sb.toString();
        }
    }

    /**
     * 按城市名搜索，返回多个候选（最多 count 个）。
     * 网络失败返回空列表，不会抛出异常。
     */
    public static List<Geo> geocodeList(String keyword, int count) {
        List<Geo> out = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) return out;
        try {
            String url = GEO_BASE + "?count=" + count + "&language=zh&format=json&name="
                    + URLEncoder.encode(keyword.trim(), "UTF-8");
            JSONObject root = getJson(url);
            JSONArray arr = root.optJSONArray("results");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject r = arr.optJSONObject(i);
                if (r == null) continue;
                Geo g = new Geo();
                g.name = r.optString("name", keyword);
                g.admin = r.optString("admin1", "");
                g.country = r.optString("country", "");
                g.lat = r.optDouble("latitude", 0);
                g.lon = r.optDouble("longitude", 0);
                out.add(g);
            }
        } catch (Exception e) {
            Log.w(TAG, "geocodeList error", e);
        }
        return out;
    }

    /** 兼容旧接口：只取第一个结果，失败返回 null */
    public static double[] geocode(String city) {
        List<Geo> list = geocodeList(city, 1);
        if (list.isEmpty()) return null;
        Geo g = list.get(0);
        return new double[]{g.lat, g.lon};
    }

    // =====================================================================
    // 天气 + 空气质量
    // =====================================================================

    /** 拉取完整天气（含空气质量，空气质量失败不影响主流程） */
    public static WeatherData fetchAll(double lat, double lon, String cityFallback) {
        WeatherData d = fetch(lat, lon, cityFallback);
        if (d != null) fillAirQuality(d);
        return d;
    }

    /** 拉取天气预报；失败返回 null */
    public static WeatherData fetch(double lat, double lon, String cityFallback) {
        try {
            String url = FC_BASE + FC_QUERY
                    + "&latitude=" + fmtCoord(lat)
                    + "&longitude=" + fmtCoord(lon);
            JSONObject root = getJson(url);
            WeatherData d = new WeatherData();
            d.lat = lat;
            d.lon = lon;
            d.city = (cityFallback == null || cityFallback.trim().isEmpty())
                    ? String.format(Locale.US, "%.2f, %.2f", lat, lon)
                    : cityFallback;
            d.updatedAt = System.currentTimeMillis();

            int utcOffset = root.optInt("utc_offset_seconds", 0);
            String units = root.optJSONObject("current_units") == null
                    ? "" : root.optJSONObject("current_units").optString("wind_speed_10m", "");

            // ---------- 当前 ----------
            JSONObject cur = root.optJSONObject("current");
            if (cur == null) return null;
            d.currentTemp = (int) Math.round(cur.optDouble("temperature_2m", 0));
            d.currentCode = cur.optInt("weather_code", 0);
            d.feelsLike = (int) Math.round(cur.optDouble("apparent_temperature", d.currentTemp));
            d.humidity = cur.optInt("relative_humidity_2m", 0);
            d.windSpeed = cur.optDouble("wind_speed_10m", 0);
            d.windDir = cur.optInt("wind_direction_10m", 0);
            d.pressure = cur.optDouble("surface_pressure", 1013);
            d.cloudCover = cur.optInt("cloud_cover", 0);
            d.precip = cur.optDouble("precipitation", 0);
            d.isDay = cur.optInt("is_day", 1) == 1;

            // 风速统一换算为 km/h（不同地区默认单位可能不同）
            if (units.contains("m/s")) d.windSpeed *= 3.6;

            // ---------- 逐小时 ----------
            JSONObject hourly = root.optJSONObject("hourly");
            if (hourly != null) {
                JSONArray hTime = hourly.optJSONArray("time");
                if (hTime != null && hTime.length() > 0) {
                    int total = hTime.length();
                    int n = Math.min(24, total);
                    int start = nearestHourIndex(hTime, utcOffset);
                    d.hourlyTime = new String[n];
                    d.hourlyTemp = new int[n];
                    d.hourlyCode = new int[n];
                    d.hourlyHumidity = new int[n];
                    d.hourlyPrecipProb = new int[n];
                    d.hourlyPrecip = new double[n];
                    d.hourlyWind = new double[n];
                    JSONArray hTemp = hourly.optJSONArray("temperature_2m");
                    JSONArray hCode = hourly.optJSONArray("weather_code");
                    JSONArray hHum = hourly.optJSONArray("relative_humidity_2m");
                    JSONArray hPop = hourly.optJSONArray("precipitation_probability");
                    JSONArray hPrc = hourly.optJSONArray("precipitation");
                    JSONArray hWind = hourly.optJSONArray("wind_speed_10m");
                    for (int i = 0; i < n; i++) {
                        int idx = (start + i) % total;
                        d.hourlyTime[i] = hTime.optString(idx, "");
                        d.hourlyTemp[i] = (int) Math.round(num(hTemp, idx));
                        d.hourlyCode[i] = (int) num(hCode, idx);
                        d.hourlyHumidity[i] = (int) num(hHum, idx);
                        d.hourlyPrecipProb[i] = (int) num(hPop, idx);
                        d.hourlyPrecip[i] = num(hPrc, idx);
                        d.hourlyWind[i] = num(hWind, idx);
                    }
                }
            }

            // ---------- 逐日 ----------
            JSONObject daily = root.optJSONObject("daily");
            if (daily != null) {
                JSONArray dTime = daily.optJSONArray("time");
                if (dTime != null && dTime.length() > 0) {
                    int dn = dTime.length();
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
                    JSONArray dMin = daily.optJSONArray("temperature_2m_min");
                    JSONArray dMax = daily.optJSONArray("temperature_2m_max");
                    JSONArray dCode = daily.optJSONArray("weather_code");
                    JSONArray dRise = daily.optJSONArray("sunrise");
                    JSONArray dSet = daily.optJSONArray("sunset");
                    JSONArray dUv = daily.optJSONArray("uv_index_max");
                    JSONArray dPrc = daily.optJSONArray("precipitation_sum");
                    JSONArray dPop = daily.optJSONArray("precipitation_probability_max");
                    JSONArray dWind = daily.optJSONArray("wind_speed_10m_max");
                    for (int i = 0; i < dn; i++) {
                        String date = dTime.optString(i, "");
                        d.dailyDate[i] = date;
                        d.dailyDay[i] = weekday(date, i == 0);
                        d.dailySunrise[i] = clock(dRise == null ? "" : dRise.optString(i, ""));
                        d.dailySunset[i] = clock(dSet == null ? "" : dSet.optString(i, ""));
                        d.dailyMin[i] = (int) Math.round(num(dMin, i));
                        d.dailyMax[i] = (int) Math.round(num(dMax, i));
                        d.dailyCode[i] = (int) num(dCode, i);
                        d.dailyUv[i] = (int) Math.round(num(dUv, i));
                        d.dailyPrecip[i] = num(dPrc, i);
                        d.dailyPrecipProb[i] = (int) num(dPop, i);
                        d.dailyWindMax[i] = num(dWind, i);
                    }
                }
            }
            return d;
        } catch (Exception e) {
            Log.w(TAG, "fetch error", e);
            return null;
        }
    }

    /** 补齐空气质量；失败时保持 hasAir=false，不抛异常 */
    public static void fillAirQuality(WeatherData d) {
        if (d == null) return;
        try {
            String url = AIR_BASE + AIR_QUERY
                    + "&latitude=" + fmtCoord(d.lat)
                    + "&longitude=" + fmtCoord(d.lon);
            JSONObject root = getJson(url);
            JSONObject cur = root.optJSONObject("current");
            if (cur == null) return;
            d.aqi = cur.optInt("european_aqi", -1);
            d.usAqi = cur.optInt("us_aqi", -1);
            d.pm25 = cur.optDouble("pm2_5", 0);
            d.pm10 = cur.optDouble("pm10", 0);
            d.ozone = cur.optDouble("ozone", 0);
            d.no2 = cur.optDouble("nitrogen_dioxide", 0);
            d.so2 = cur.optDouble("sulphur_dioxide", 0);
            d.co = cur.optDouble("carbon_monoxide", 0);
            d.hasAir = d.aqi >= 0;
        } catch (Exception e) {
            Log.w(TAG, "air quality error", e);
        }
    }

    // =====================================================================
    // 内部工具
    // =====================================================================

    private static String fmtCoord(double v) {
        return String.format(Locale.US, "%.4f", v);
    }

    private static double num(JSONArray arr, int i) {
        if (arr == null || i < 0 || i >= arr.length()) return 0;
        if (arr.isNull(i)) return 0;
        return arr.optDouble(i, 0);
    }

    /** 带一次重试的 GET → JSON */
    private static JSONObject getJson(String urlStr) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return getJsonOnce(urlStr);
            } catch (Exception e) {
                last = e;
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw last == null ? new RuntimeException("unknown network error") : last;
    }

    private static JSONObject getJsonOnce(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "LightWeather/2.0 (Android)");
            int code = conn.getResponseCode();
            if (code != 200) throw new RuntimeException("HTTP " + code);

            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int read;
            while ((read = reader.read(buf)) != -1) sb.append(buf, 0, read);
            reader.close();
            return new JSONObject(sb.toString());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 找到「不早于当前时刻」的第一个整点。
     * Open-Meteo 返回的是目标城市本地时间，用 utc_offset_seconds 换算，避免跨时区错位。
     */
    private static int nearestHourIndex(JSONArray times, int utcOffsetSeconds) {
        try {
            long nowLocal = System.currentTimeMillis() + utcOffsetSeconds * 1000L;
            // 向下取整到整点，使输出从「当前小时」开始
            long base = nowLocal - (nowLocal % 3_600_000L);
            int best = -1;
            long bestT = Long.MAX_VALUE;
            for (int i = 0; i < times.length(); i++) {
                long t = parseLocalMs(times.optString(i, ""));
                if (t < 0) continue;
                if (t >= base && t < bestT) {
                    bestT = t;
                    best = i;
                }
            }
            if (best >= 0) return best;
        } catch (Exception ignore) {
        }
        return 0;
    }

    /** 把「yyyy-MM-dd'T'HH:mm」当作 UTC 解析成毫秒（配合 utc_offset 使用） */
    private static long parseLocalMs(String iso) {
        try {
            // 2026-11-01T14:00
            int y = Integer.parseInt(iso.substring(0, 4));
            int m = Integer.parseInt(iso.substring(5, 7));
            int d = Integer.parseInt(iso.substring(8, 10));
            int hh = Integer.parseInt(iso.substring(11, 13));
            int mm = Integer.parseInt(iso.substring(14, 16));
            return java.time.LocalDateTime.of(y, m, d, hh, mm)
                    .toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        } catch (Exception e) {
            return -1;
        }
    }

    /** "2026-11-01T06:47" → "06:47" */
    private static String clock(String iso) {
        if (iso == null || iso.length() < 16) return "--:--";
        int t = iso.indexOf('T');
        if (t < 0 || iso.length() < t + 6) return "--:--";
        return iso.substring(t + 1, t + 6);
    }

    private static String weekday(String isoDate, boolean isToday) {
        if (isToday) return "今天";
        try {
            LocalDate date = LocalDate.parse(isoDate.substring(0, 10));
            switch (date.getDayOfWeek()) {
                case MONDAY:    return "周一";
                case TUESDAY:   return "周二";
                case WEDNESDAY: return "周三";
                case THURSDAY:  return "周四";
                case FRIDAY:    return "周五";
                case SATURDAY:  return "周六";
                default:        return "周日";
            }
        } catch (Exception e) {
            return isoDate;
        }
    }
}
