package com.lightweather.desktop;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

/** 设置与离线缓存持久化：%APPDATA%\LightWeather\settings.properties */
public final class Prefs {

    private static final Prefs INSTANCE = new Prefs();

    public static Prefs get() {
        return INSTANCE;
    }

    public static final class City {
        public final String name;
        public final double lat;
        public final double lon;

        public City(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    private final File file;
    private final Properties p = new Properties();
    private final List<City> cities = new ArrayList<>();

    private Prefs() {
        File dir = new File(dataDir());
        if (!dir.exists()) dir.mkdirs();
        file = new File(dir, "settings.properties");
        load();
    }

    public static String dataDir() {
        String appdata = System.getenv("APPDATA");
        if (appdata == null || appdata.isEmpty()) {
            appdata = System.getProperty("user.home");
        }
        return appdata + File.separator + "LightWeather";
    }

    private void load() {
        if (file.exists()) {
            try (InputStreamReader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException ignored) {
            }
        }
        int n = i("city_count", 0);
        for (int k = 0; k < n; k++) {
            String name = p.getProperty("city_" + k + "_name");
            if (name == null) continue;
            cities.add(new City(name, d("city_" + k + "_lat", 0), d("city_" + k + "_lon", 0)));
        }
    }

    public void save() {
        p.setProperty("city_count", String.valueOf(cities.size()));
        for (int k = 0; k < cities.size(); k++) {
            City c = cities.get(k);
            p.setProperty("city_" + k + "_name", c.name);
            p.setProperty("city_" + k + "_lat", String.valueOf(c.lat));
            p.setProperty("city_" + k + "_lon", String.valueOf(c.lon));
        }
        for (int k = cities.size(); k < cities.size() + 8; k++) {
            p.remove("city_" + k + "_name");
            p.remove("city_" + k + "_lat");
            p.remove("city_" + k + "_lon");
        }
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            p.store(w, "LightWeather desktop settings");
        } catch (IOException ignored) {
        }
    }

    // ---------------- 单位 ----------------

    /** 0 = 自动，1 = 浅色，2 = 深色 */
    public int themeMode() {
        String v = p.getProperty("theme", "auto");
        if ("light".equals(v)) return 1;
        if ("dark".equals(v)) return 2;
        return 0;
    }

    public void setThemeMode(int mode) {
        p.setProperty("theme", mode == 1 ? "light" : mode == 2 ? "dark" : "auto");
    }

    /** 界面风格：0 标准，1 玻璃，2 极简 */
    public int uiStyle() {
        return i("ui_style", 0);
    }

    public void setUiStyle(int style) {
        p.setProperty("ui_style", String.valueOf(style));
    }

    /** 数据源：0 自动，1 Open-Meteo，2 wttr.in，3 MET Norway */
    public int provider() {
        return i("provider", 0);
    }

    public void setProvider(int provider) {
        p.setProperty("provider", String.valueOf(provider));
    }

    public boolean tempF() {
        return "F".equals(p.getProperty("temp_unit", "C"));
    }

    public void setTempF(boolean f) {
        p.setProperty("temp_unit", f ? "F" : "C");
    }

    /** 0 km/h, 1 m/s, 2 mph, 3 级 */
    public int windUnit() {
        return i("wind_unit", 0);
    }

    public void setWindUnit(int u) {
        p.setProperty("wind_unit", String.valueOf(u));
    }

    public String windUnitLabel() {
        switch (windUnit()) {
            case 1: return "m/s";
            case 2: return "mph";
            case 3: return "级";
            default: return "km/h";
        }
    }

    /** 0 hPa, 1 mmHg, 2 inHg */
    public int pressUnit() {
        return i("press_unit", 0);
    }

    public void setPressUnit(int u) {
        p.setProperty("press_unit", String.valueOf(u));
    }

    public String pressUnitLabel() {
        switch (pressUnit()) {
            case 1: return "mmHg";
            case 2: return "inHg";
            default: return "hPa";
        }
    }

    public int conv(int celsius) {
        return tempF() ? (int) Math.round(celsius * 9 / 5.0 + 32) : celsius;
    }

    public String fmtTemp(int celsius) {
        return conv(celsius) + "°";
    }

    public String fmtTempUnit(int celsius) {
        return conv(celsius) + (tempF() ? "°F" : "°C");
    }

    public String fmtWind(double kmh) {
        switch (windUnit()) {
            case 1: return String.format("%.1f", kmh / 3.6);
            case 2: return String.format("%.1f", kmh / 1.609);
            case 3: return String.valueOf(beaufort(kmh));
            default: return String.valueOf(Math.round(kmh));
        }
    }

    public String fmtPressure(double hpa) {
        if (hpa <= 0) return "--";
        switch (pressUnit()) {
            case 1: return String.valueOf(Math.round(hpa * 0.750061683));
            case 2: return String.format("%.2f", hpa * 0.029529983);
            default: return String.valueOf(Math.round(hpa));
        }
    }

    public static int beaufort(double kmh) {
        double[] limits = {1, 6, 12, 20, 29, 39, 50, 62, 75, 89, 103, 118};
        for (int i = 0; i < limits.length; i++) {
            if (kmh < limits[i]) return i;
        }
        return 12;
    }

    public static String windDirText(int deg) {
        if (deg <= 0) return "北风";
        String[] names = {"北风", "东北风", "东风", "东南风", "南风", "西南风", "西风", "西北风"};
        int idx = (int) Math.round(deg / 45.0) % 8;
        return names[idx];
    }

    // ---------------- 城市 ----------------

    public List<City> cities() {
        return cities;
    }

    public City find(String name) {
        for (City c : cities) {
            if (c.name.equals(name)) return c;
        }
        return null;
    }

    public void addCity(String name, double lat, double lon) {
        if (find(name) != null) return;
        cities.add(new City(name, lat, lon));
        if (cities.size() > 12) cities.remove(0);
        save();
    }

    public void removeCity(String name) {
        cities.removeIf(c -> c.name.equals(name));
        save();
    }

    // ---------------- 当前城市 / 缓存 ----------------

    public String lastCity() {
        return p.getProperty("last_city", "");
    }

    public double lastLat() {
        return d("last_lat", 0);
    }

    public double lastLon() {
        return d("last_lon", 0);
    }

    public void setLast(String city, double lat, double lon) {
        p.setProperty("last_city", city == null ? "" : city);
        p.setProperty("last_lat", String.valueOf(lat));
        p.setProperty("last_lon", String.valueOf(lon));
        save();
    }

    public long cacheAt() {
        return Long.parseLong(p.getProperty("cache_at", "0"));
    }

    /** 缓存原始预报 JSON（Base64 便于 Properties 存储），可离线解析出完整数据 */
    public void saveCache(String json) {
        if (json == null) return;
        p.setProperty("cache_json", Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
        p.setProperty("cache_at", String.valueOf(System.currentTimeMillis()));
        save();
    }

    public String loadCache() {
        String b64 = p.getProperty("cache_json", "");
        if (b64.isEmpty()) return null;
        try {
            return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean cacheStale(long maxAgeMs) {
        long at = cacheAt();
        return at <= 0 || System.currentTimeMillis() - at > maxAgeMs;
    }

    // ---------------- 小工具 ----------------

    private int i(String key, int def) {
        try {
            return Integer.parseInt(p.getProperty(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private double d(String key, double def) {
        try {
            return Double.parseDouble(p.getProperty(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
