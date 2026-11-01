package com.example.weather;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 轻量持久化：温度 / 风速 / 气压单位、城市列表、最近坐标、完整天气离线缓存。
 * 零依赖，仅用 SharedPreferences + org.json。
 */
public final class WeatherPrefs {

    private static final String P = "wx_prefs";

    private static final String K_UNIT = "unit";        // "C" / "F"
    private static final String K_WIND = "wind_unit";   // "kmh" / "ms" / "mph" / "bft"
    private static final String K_PRESS = "press_unit"; // "hpa" / "mmhg" / "inhg"
    private static final String K_CITIES = "cities";    // JSONArray
    private static final String K_CUR = "current";      // 当前城市名
    private static final String K_LAT = "last_lat";
    private static final String K_LON = "last_lon";
    private static final String K_FULL = "cache_full";  // 完整天气离线缓存
    private static final String K_LAST_TS = "last_refresh_ts";

    private WeatherPrefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(P, Context.MODE_PRIVATE);
    }

    // =====================================================================
    // 温度单位
    // =====================================================================

    /** 主题：0 跟随系统 / 1 浅色 / 2 深色 */
    public static int getThemeMode(Context c) {
        return sp(c).getInt("theme_mode", 0);
    }

    public static void setThemeMode(Context c, int mode) {
        sp(c).edit().putInt("theme_mode", mode).apply();
    }

    public static boolean isF(Context c) {
        return "F".equals(sp(c).getString(K_UNIT, "C"));
    }

    public static void setF(Context c, boolean f) {
        sp(c).edit().putString(K_UNIT, f ? "F" : "C").apply();
    }

    /** 摄氏度 → 当前单位字符串（不含 °） */
    public static String fmtTemp(int celsius, Context ctx) {
        if (isF(ctx)) return String.valueOf(Math.round(celsius * 9f / 5 + 32));
        return String.valueOf(celsius);
    }

    /** 带度数符号 */
    public static String fmtTempDeg(int celsius, Context ctx) {
        return fmtTemp(celsius, ctx) + "°";
    }

    // =====================================================================
    // 风速单位
    // =====================================================================

    public static String getWindUnit(Context c) {
        return sp(c).getString(K_WIND, "kmh");
    }

    public static void setWindUnit(Context c, String unit) {
        sp(c).edit().putString(K_WIND, unit).apply();
    }

    public static String windUnitLabel(Context c) {
        switch (getWindUnit(c)) {
            case "ms":  return "m/s";
            case "mph": return "mph";
            case "bft": return "级";
            default:    return "km/h";
        }
    }

    /** 风速（km/h）→ 当前单位的展示值 */
    public static String fmtWind(double kmh, Context c) {
        switch (getWindUnit(c)) {
            case "ms":
                return String.format(Locale.CHINA, "%.1f", kmh / 3.6);
            case "mph":
                return String.valueOf(Math.round(kmh / 1.609344));
            case "bft":
                return String.valueOf(beaufort(kmh));
            default:
                return String.valueOf(Math.round(kmh));
        }
    }

    /** 蒲福风级 0–12 */
    public static int beaufort(double kmh) {
        double[] upper = {1, 6, 12, 20, 29, 39, 50, 62, 75, 89, 103, 118};
        for (int i = 0; i < upper.length; i++) {
            if (kmh < upper[i]) return i;
        }
        return 12;
    }

    /** 风向角度 → 中文八方位 */
    public static String windDirText(int deg) {
        String[] names = {"北", "东北", "东", "东南", "南", "西南", "西", "西北"};
        int idx = (int) Math.round(((deg % 360) + 360) % 360 / 45.0) % 8;
        return names[idx] + "风";
    }

    // =====================================================================
    // 气压单位
    // =====================================================================

    public static String getPressUnit(Context c) {
        return sp(c).getString(K_PRESS, "hpa");
    }

    public static void setPressUnit(Context c, String unit) {
        sp(c).edit().putString(K_PRESS, unit).apply();
    }

    public static String pressUnitLabel(Context c) {
        switch (getPressUnit(c)) {
            case "mmhg": return "mmHg";
            case "inhg": return "inHg";
            default:     return "hPa";
        }
    }

    public static String fmtPressure(double hpa, Context c) {
        if (hpa <= 0) return "--";
        switch (getPressUnit(c)) {
            case "mmhg":
                return String.valueOf(Math.round(hpa * 0.750062));
            case "inhg":
                return String.format(Locale.US, "%.2f", hpa * 0.02953);
            default:
                return String.valueOf(Math.round(hpa));
        }
    }

    // =====================================================================
    // 当前城市
    // =====================================================================

    public static void setCurrent(Context c, String n) {
        sp(c).edit().putString(K_CUR, n).apply();
    }

    public static String getCurrent(Context c) {
        return sp(c).getString(K_CUR, "北京");
    }

    // =====================================================================
    // 最近坐标
    // =====================================================================

    public static void saveLoc(Context c, double la, double lo) {
        sp(c).edit()
                .putFloat(K_LAT, (float) la)
                .putFloat(K_LON, (float) lo)
                .putLong(K_LAST_TS, System.currentTimeMillis())
                .apply();
    }

    public static double[] getLoc(Context c) {
        SharedPreferences s = sp(c);
        if (!s.contains(K_LAT)) return null;
        return new double[]{s.getFloat(K_LAT, 0), s.getFloat(K_LON, 0)};
    }

    public static long lastRefreshAt(Context c) {
        return sp(c).getLong(K_LAST_TS, 0);
    }

    // =====================================================================
    // 城市列表
    // =====================================================================

    public static List<City> getCities(Context c) {
        List<City> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString(K_CITIES, "[]"));
            for (int i = 0; i < a.length(); i++) list.add(City.from(a.getJSONObject(i)));
        } catch (Exception ignore) {
        }
        return list;
    }

    public static void addCity(Context c, String n, double la, double lo) {
        List<City> list = getCities(c);
        for (City x : list) {
            if (x.name.equals(n)) {
                x.lat = la;
                x.lon = lo;
                save(list, c);
                return;
            }
        }
        list.add(new City(n, la, lo));
        save(list, c);
    }

    public static void removeCity(Context c, String n) {
        List<City> list = getCities(c);
        list.removeIf(x -> x.name.equals(n));
        save(list, c);
        if (getCurrent(c).equals(n)) {
            if (!list.isEmpty()) setCurrent(c, list.get(0).name);
            else setCurrent(c, "");
        }
    }

    public static City findCity(Context c, String n) {
        for (City x : getCities(c)) if (x.name.equals(n)) return x;
        return null;
    }

    private static void save(List<City> list, Context c) {
        JSONArray a = new JSONArray();
        for (City x : list) a.put(x.toJson());
        sp(c).edit().putString(K_CITIES, a.toString()).apply();
    }

    // =====================================================================
    // 完整离线缓存
    // =====================================================================

    public static void saveFull(Context c, WeatherData d) {
        if (d == null) return;
        try {
            sp(c).edit().putString(K_FULL, toJson(d).toString()).apply();
        } catch (Exception ignore) {
        }
    }

    public static WeatherData loadFull(Context c) {
        try {
            String s = sp(c).getString(K_FULL, "");
            if (s == null || s.isEmpty()) return null;
            return fromJson(new JSONObject(s));
        } catch (Exception e) {
            return null;
        }
    }

    /** 缓存是否过期（默认 15 分钟） */
    public static boolean isCacheStale(Context c, long millis) {
        long t = lastRefreshAt(c);
        return t <= 0 || System.currentTimeMillis() - t > millis;
    }

    private static JSONObject toJson(WeatherData d) throws Exception {
        JSONObject o = new JSONObject();
        o.put("city", d.city);
        o.put("lat", d.lat);
        o.put("lon", d.lon);
        o.put("t", d.currentTemp);
        o.put("code", d.currentCode);
        o.put("feel", d.feelsLike);
        o.put("hum", d.humidity);
        o.put("wind", d.windSpeed);
        o.put("wdir", d.windDir);
        o.put("press", d.pressure);
        o.put("cloud", d.cloudCover);
        o.put("precip", d.precip);
        o.put("isDay", d.isDay ? 1 : 0);
        o.put("ts", d.updatedAt);

        o.put("hTime", strArr(d.hourlyTime));
        o.put("hTemp", intArr(d.hourlyTemp));
        o.put("hCode", intArr(d.hourlyCode));
        o.put("hHum", intArr(d.hourlyHumidity));
        o.put("hPop", intArr(d.hourlyPrecipProb));
        o.put("hPrc", dblArr(d.hourlyPrecip));
        o.put("hWind", dblArr(d.hourlyWind));

        o.put("dDate", strArr(d.dailyDate));
        o.put("dDay", strArr(d.dailyDay));
        o.put("dRise", strArr(d.dailySunrise));
        o.put("dSet", strArr(d.dailySunset));
        o.put("dMin", intArr(d.dailyMin));
        o.put("dMax", intArr(d.dailyMax));
        o.put("dCode", intArr(d.dailyCode));
        o.put("dPop", intArr(d.dailyPrecipProb));
        o.put("dUv", intArr(d.dailyUv));
        o.put("dPrc", dblArr(d.dailyPrecip));
        o.put("dWind", dblArr(d.dailyWindMax));

        o.put("hasAir", d.hasAir ? 1 : 0);
        o.put("aqi", d.aqi);
        o.put("usAqi", d.usAqi);
        o.put("pm25", d.pm25);
        o.put("pm10", d.pm10);
        o.put("o3", d.ozone);
        o.put("no2", d.no2);
        o.put("so2", d.so2);
        o.put("co", d.co);
        return o;
    }

    private static WeatherData fromJson(JSONObject o) {
        WeatherData d = new WeatherData();
        d.city = o.optString("city", "");
        d.lat = o.optDouble("lat", 0);
        d.lon = o.optDouble("lon", 0);
        d.currentTemp = o.optInt("t", 0);
        d.currentCode = o.optInt("code", 0);
        d.feelsLike = o.optInt("feel", 0);
        d.humidity = o.optInt("hum", 0);
        d.windSpeed = o.optDouble("wind", 0);
        d.windDir = o.optInt("wdir", 0);
        d.pressure = o.optDouble("press", 0);
        d.cloudCover = o.optInt("cloud", 0);
        d.precip = o.optDouble("precip", 0);
        d.isDay = o.optInt("isDay", 1) == 1;
        d.updatedAt = o.optLong("ts", 0);

        d.hourlyTime = strArr(o.optJSONArray("hTime"));
        d.hourlyTemp = intArr(o.optJSONArray("hTemp"));
        d.hourlyCode = intArr(o.optJSONArray("hCode"));
        d.hourlyHumidity = intArr(o.optJSONArray("hHum"));
        d.hourlyPrecipProb = intArr(o.optJSONArray("hPop"));
        d.hourlyPrecip = dblArr(o.optJSONArray("hPrc"));
        d.hourlyWind = dblArr(o.optJSONArray("hWind"));

        d.dailyDate = strArr(o.optJSONArray("dDate"));
        d.dailyDay = strArr(o.optJSONArray("dDay"));
        d.dailySunrise = strArr(o.optJSONArray("dRise"));
        d.dailySunset = strArr(o.optJSONArray("dSet"));
        d.dailyMin = intArr(o.optJSONArray("dMin"));
        d.dailyMax = intArr(o.optJSONArray("dMax"));
        d.dailyCode = intArr(o.optJSONArray("dCode"));
        d.dailyPrecipProb = intArr(o.optJSONArray("dPop"));
        d.dailyUv = intArr(o.optJSONArray("dUv"));
        d.dailyPrecip = dblArr(o.optJSONArray("dPrc"));
        d.dailyWindMax = dblArr(o.optJSONArray("dWind"));

        d.hasAir = o.optInt("hasAir", 0) == 1;
        d.aqi = o.optInt("aqi", -1);
        d.usAqi = o.optInt("usAqi", -1);
        d.pm25 = o.optDouble("pm25", 0);
        d.pm10 = o.optDouble("pm10", 0);
        d.ozone = o.optDouble("o3", 0);
        d.no2 = o.optDouble("no2", 0);
        d.so2 = o.optDouble("so2", 0);
        d.co = o.optDouble("co", 0);
        return d;
    }

    private static JSONArray intArr(int[] a) {
        JSONArray arr = new JSONArray();
        if (a != null) for (int v : a) arr.put(v);
        return arr;
    }

    private static JSONArray dblArr(double[] a) {
        JSONArray arr = new JSONArray();
        // 注意：JSONArray.put(double) 会抛受检异常，这里显式装箱走 put(Object)
        if (a != null) for (double v : a) arr.put((Object) v);
        return arr;
    }

    private static JSONArray strArr(String[] a) {
        JSONArray arr = new JSONArray();
        if (a != null) for (String v : a) arr.put(v == null ? "" : v);
        return arr;
    }

    private static int[] intArr(JSONArray a) {
        if (a == null) return new int[0];
        int[] r = new int[a.length()];
        for (int i = 0; i < r.length; i++) r[i] = a.optInt(i, 0);
        return r;
    }

    private static double[] dblArr(JSONArray a) {
        if (a == null) return new double[0];
        double[] r = new double[a.length()];
        for (int i = 0; i < r.length; i++) r[i] = a.optDouble(i, 0);
        return r;
    }

    private static String[] strArr(JSONArray a) {
        if (a == null) return new String[0];
        String[] r = new String[a.length()];
        for (int i = 0; i < r.length; i++) r[i] = a.optString(i, "");
        return r;
    }

    /** 城市数据 */
    public static class City {
        public String name;
        public double lat, lon;

        City(String n, double la, double lo) {
            name = n;
            lat = la;
            lon = lo;
        }

        JSONObject toJson() {
            try {
                return new JSONObject().put("n", name).put("la", lat).put("lo", lon);
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        static City from(JSONObject o) {
            return new City(o.optString("n"), o.optDouble("la"), o.optDouble("lo"));
        }
    }
}
