package com.example.weather;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 轻量持久化：温度单位、城市列表、当前城市、最近坐标、小部件缓存。
 * 零依赖，仅用 SharedPreferences + org.json。
 */
public final class WeatherPrefs {

    private static final String P = "wx_prefs";
    private static final String K_UNIT = "unit";      // "C" / "F"
    private static final String K_CITIES = "cities";  // JSONArray
    private static final String K_CUR = "current";    // 当前城市名
    private static final String K_LAT = "last_lat";
    private static final String K_LON = "last_lon";
    private static final String K_CACHE = "cache";    // 小部件缓存

    private WeatherPrefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(P, Context.MODE_PRIVATE);
    }

    // ---- 温度单位 ----
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

    // ---- 当前城市 ----
    public static void setCurrent(Context c, String n) {
        sp(c).edit().putString(K_CUR, n).apply();
    }

    public static String getCurrent(Context c) {
        return sp(c).getString(K_CUR, "北京");
    }

    // ---- 最近坐标（小部件刷新用） ----
    public static void saveLoc(Context c, double la, double lo) {
        sp(c).edit().putFloat(K_LAT, (float) la).putFloat(K_LON, (float) lo).apply();
    }

    public static double[] getLoc(Context c) {
        SharedPreferences s = sp(c);
        if (!s.contains(K_LAT)) return null;
        return new double[]{s.getFloat(K_LAT, 0), s.getFloat(K_LON, 0)};
    }

    // ---- 城市列表 ----
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
        for (City x : list) if (x.name.equals(n)) return; // 已存在
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

    // ---- 小部件缓存（仅当前天气摘要） ----
    public static void cache(Context c, WeatherData d) {
        try {
            JSONObject o = new JSONObject();
            o.put("city", d.city);
            o.put("t", d.currentTemp);
            o.put("code", d.currentCode);
            sp(c).edit().putString(K_CACHE, o.toString()).apply();
        } catch (Exception ignore) {
        }
    }

    public static WeatherData getCache(Context c) {
        try {
            String s = sp(c).getString(K_CACHE, "");
            if (s.isEmpty()) return null;
            JSONObject o = new JSONObject(s);
            WeatherData d = new WeatherData();
            d.city = o.getString("city");
            d.currentTemp = o.getInt("t");
            d.currentCode = o.getInt("code");
            return d;
        } catch (Exception e) {
            return null;
        }
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
