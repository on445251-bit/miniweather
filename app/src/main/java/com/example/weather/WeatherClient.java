package com.example.weather;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 极简 HTTP 客户端：仅依赖 android.util.Json（无第三方库）。
 * 数据来自 Open-Meteo（无需 API Key）。
 */
public final class WeatherClient {

    private static final String TAG = "WeatherClient";
    private static final String GEO_URL =
            "https://geocoding-api.open-meteo.com/v1/search?count=1&language=zh&format=json&name=";
    private static final String FC_URL =
            "https://api.open-meteo.com/v1/forecast"
                    + "?current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m"
                    + "&hourly=temperature_2m,weather_code"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                    + "&timezone=auto&forecast_days=7";

    private WeatherClient() {}

    /** 通过城市名解析经纬度；失败返回 null */
    public static double[] geocode(String city) {
        try {
            String url = GEO_URL + java.net.URLEncoder.encode(city, "UTF-8");
            JSONObject root = getJson(url);
            JSONArray results = root.optJSONArray("results");
            if (results == null || results.length() == 0) return null;
            JSONObject r = results.getJSONObject(0);
            double lat = r.getDouble("latitude");
            double lon = r.getDouble("longitude");
            return new double[]{lat, lon};
        } catch (Exception e) {
            Log.w(TAG, "geocode error", e);
            return null;
        }
    }

    /** 拉取天气；city 用于展示名 */
    public static WeatherData fetch(double lat, double lon, String cityFallback) {
        try {
            JSONObject root = getJson(FC_URL + "&latitude=" + lat + "&longitude=" + lon);
            WeatherData d = new WeatherData();
            d.lat = lat;
            d.lon = lon;

            // 城市名：优先用反查（这里简单用 fallback 或坐标）
            d.city = (cityFallback == null || cityFallback.isEmpty())
                    ? String.format(Locale.US, "%.2f, %.2f", lat, lon)
                    : cityFallback;

            JSONObject cur = root.getJSONObject("current");
            d.currentTemp = (int) Math.round(cur.getDouble("temperature_2m"));
            d.currentCode = cur.getInt("weather_code");
            d.feelsLike = (int) Math.round(cur.getDouble("apparent_temperature"));
            d.humidity = cur.getInt("relative_humidity_2m");
            d.windSpeed = cur.getDouble("wind_speed_10m");

            JSONObject hourly = root.getJSONObject("hourly");
            JSONArray hTime = hourly.getJSONArray("time");
            JSONArray hTemp = hourly.getJSONArray("temperature_2m");
            JSONArray hCode = hourly.getJSONArray("weather_code");
            int n = Math.min(24, hTime.length());
            d.hourlyTime = new String[n];
            d.hourlyTemp = new int[n];
            d.hourlyCode = new int[n];
            // 找到距离当前最近的一个整点作为起点
            int start = nearestHourIndex(hTime);
            for (int i = 0; i < n; i++) {
                int idx = (start + i) % hTime.length();
                d.hourlyTime[i] = hTime.getString(idx);
                d.hourlyTemp[i] = (int) Math.round(hTemp.getDouble(idx));
                d.hourlyCode[i] = hCode.getInt(idx);
            }

            JSONObject daily = root.getJSONObject("daily");
            JSONArray dTime = daily.getJSONArray("time");
            JSONArray dMin = daily.getJSONArray("temperature_2m_min");
            JSONArray dMax = daily.getJSONArray("temperature_2m_max");
            JSONArray dCode = daily.getJSONArray("weather_code");
            int dn = dTime.length();
            d.dailyDay = new String[dn];
            d.dailyMin = new int[dn];
            d.dailyMax = new int[dn];
            d.dailyCode = new int[dn];
            for (int i = 0; i < dn; i++) {
                d.dailyDay[i] = weekday(dTime.getString(i), i == 0);
                d.dailyMin[i] = (int) Math.round(dMin.getDouble(i));
                d.dailyMax[i] = (int) Math.round(dMax.getDouble(i));
                d.dailyCode[i] = dCode.getInt(i);
            }
            return d;
        } catch (Exception e) {
            Log.w(TAG, "fetch error", e);
            return null;
        }
    }

    // ---- 内部工具 ----

    private static JSONObject getJson(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            int code = conn.getResponseCode();
            if (code != 200) throw new RuntimeException("HTTP " + code);
            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return new JSONObject(sb.toString());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static int nearestHourIndex(JSONArray times) {
        // 找到不早于当前时刻的第一个整点（Open-Meteo 以本地时区返回）
        try {
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US);
            sdf.setTimeZone(TimeZone.getDefault());
            long now = System.currentTimeMillis();
            for (int i = 0; i < times.length(); i++) {
                long t = sdf.parse(times.getString(i)).getTime();
                if (t >= now) return i;
            }
        } catch (Exception ignore) {}
        return 0;
    }

    private static String weekday(String isoDate, boolean isToday) {
        if (isToday) return "今天";
        try {
            String[] p = isoDate.split("-");
            int y = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            int day = Integer.parseInt(p[2]);
            Calendar c = Calendar.getInstance();
            c.set(y, m - 1, day);
            String[] names = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
            return names[c.get(Calendar.DAY_OF_WEEK) - 1];
        } catch (Exception e) {
            return isoDate;
        }
    }
}
