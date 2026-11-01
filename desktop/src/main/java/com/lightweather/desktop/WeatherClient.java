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
        return fetchAll(P_AUTO, lat, lon, city);
    }

    // ---------------- 多数据源 ----------------

    public static final int P_AUTO = 0;
    public static final int P_OPEN_METEO = 1;
    public static final int P_WTTR = 2;
    public static final int P_METNO = 3;

    public static String providerName(int p) {
        switch (p) {
            case P_OPEN_METEO:
                return "Open-Meteo";
            case P_WTTR:
                return "wttr.in";
            case P_METNO:
                return "MET Norway";
            default:
                return "自动";
        }
    }

    /** 自动模式下按 Open-Meteo → wttr.in → MET Norway 依次回退 */
    public static int[] providerOrder(int provider) {
        if (provider == P_AUTO) {
            return new int[]{P_OPEN_METEO, P_WTTR, P_METNO};
        }
        return new int[]{provider};
    }

    /** 依次尝试数据源，第一个成功的直接返回；全部失败抛出最后一次异常 */
    public static WeatherData fetchAll(int provider, double lat, double lon, String city) throws IOException {
        IOException last = null;
        for (int p : providerOrder(provider)) {
            try {
                WeatherData d = fetchOne(p, lat, lon, city);
                if (d != null) {
                    d.lat = lat;
                    d.lon = lon;
                    if (d.city == null || d.city.isEmpty()) d.city = city == null ? "" : city;
                    d.source = providerName(p);
                    try {
                        Object air = Json.parse(get(String.format(AIR, fmt(lat), fmt(lon))));
                        parseAir(air, d);
                    } catch (Exception ignored) {
                        d.hasAir = false;
                    }
                    return d;
                }
            } catch (IOException e) {
                last = e;
            } catch (RuntimeException e) {
                last = new IOException(e.getMessage(), e);
            }
        }
        throw last == null ? new IOException("所有数据源都失败了") : last;
    }

    private static WeatherData fetchOne(int provider, double lat, double lon, String city) throws IOException {
        switch (provider) {
            case P_WTTR:
                return fetchWttr(lat, lon, city);
            case P_METNO:
                return fetchMetNo(lat, lon, city);
            default:
                return parseForecast(rawForecast(lat, lon), city);
        }
    }

    /** wttr.in（WorldWeatherOnline JSON） */
    private static WeatherData fetchWttr(double lat, double lon, String city) throws IOException {
        String url = "https://wttr.in/" + fmt(lat) + "," + fmt(lon) + "?format=j1&lang=zh";
        Object root = Json.parse(get(url));
        WeatherData d = new WeatherData();
        d.city = city == null ? "" : city;

        Object cur = Json.get(root, "current_condition");
        Object c0 = at(Json.arr(cur), 0);
        d.currentTemp = Json.i(c0, "temp_C", 0);
        d.feelsLike = Json.i(c0, "FeelsLikeC", d.currentTemp);
        d.humidity = Json.i(c0, "humidity", 0);
        d.windSpeed = Json.d(c0, "windspeedKmph", 0);
        d.windDir = Json.i(c0, "winddirDegree", 0);
        d.pressure = Json.d(c0, "pressure", 0);
        d.cloudCover = Json.i(c0, "cloudcover", 0);
        d.precip = Json.d(c0, "precipMM", 0);
        d.currentCode = WeatherCodes.fromWwo(Json.i(c0, "weatherCode", 113));

        List<Object> days = Json.list(root, "weather");
        int dn = Math.min(7, days.size());
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

        List<String> hTime = new ArrayList<>();
        List<Integer> hTemp = new ArrayList<>();
        List<Integer> hCode = new ArrayList<>();
        List<Integer> hHum = new ArrayList<>();
        List<Integer> hPop = new ArrayList<>();
        List<Double> hPrc = new ArrayList<>();
        List<Double> hWind = new ArrayList<>();

        for (int k = 0; k < dn; k++) {
            Object day = days.get(k);
            d.dailyDate[k] = Json.s(day, "date", "");
            d.dailyDay[k] = k == 0 ? "今天" : weekday(d.dailyDate[k]);
            d.dailyMin[k] = Json.inum(day, "mintempC", 0);
            d.dailyMax[k] = Json.inum(day, "maxtempC", 0);
            d.dailyUv[k] = Json.inum(day, "uvIndex", 0);
            Object astro = at(Json.list(day, "astronomy"), 0);
            d.dailySunrise[k] = parseAmPm(Json.s(astro, "sunrise", "--:--"));
            d.dailySunset[k] = parseAmPm(Json.s(astro, "sunset", "--:--"));

            List<Object> hours = Json.list(day, "hourly");
            int dayCode = 3;
            int dayPop = 0;
            double dayPrc = 0;
            double dayWind = 0;
            for (Object h : hours) {
                String t = Json.s(h, "time", "0");
                while (t.length() < 4) t = "0" + t;
                String hhmm = t.substring(0, 2) + ":" + t.substring(2, 4);
                int code = WeatherCodes.fromWwo(Json.i(h, "weatherCode", 113));
                int temp = Json.inum(h, "tempC", 0);
                int pop = Json.inum(h, "chanceofrain", 0);
                int hum = Json.inum(h, "humidity", 0);
                double prc = Json.num(h, "precipMM", 0);
                double wind = Json.num(h, "windspeedKmph", 0);
                if (t.startsWith("12")) dayCode = code;
                dayPop = Math.max(dayPop, pop);
                dayPrc += prc;
                dayWind = Math.max(dayWind, wind);
                if (d.hourlyTime == null || hTime.size() < 24) {
                    hTime.add(day + "T" + hhmm);
                    hTemp.add(temp);
                    hCode.add(code);
                    hHum.add(hum);
                    hPop.add(pop);
                    hPrc.add(prc);
                    hWind.add(wind);
                }
            }
            d.dailyCode[k] = dayCode;
            d.dailyPrecipProb[k] = dayPop;
            d.dailyPrecip[k] = dayPrc;
            d.dailyWindMax[k] = dayWind;
        }

        // 从当前小时开始截取 24 条
        int nowIdx = 0;
        String nowKey = java.time.LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
                .toString().substring(0, 13);
        for (int i = 0; i < hTime.size(); i++) {
            if (hTime.get(i).startsWith(nowKey)) {
                nowIdx = Math.max(0, i - 1);
                break;
            }
        }
        int n = Math.min(24, Math.max(0, hTime.size() - nowIdx));
        d.hourlyTime = new String[n];
        d.hourlyTemp = new int[n];
        d.hourlyCode = new int[n];
        d.hourlyHumidity = new int[n];
        d.hourlyPrecipProb = new int[n];
        d.hourlyPrecip = new double[n];
        d.hourlyWind = new double[n];
        for (int k = 0; k < n; k++) {
            int idx = nowIdx + k;
            d.hourlyTime[k] = hTime.get(idx);
            d.hourlyTemp[k] = hTemp.get(idx);
            d.hourlyCode[k] = hCode.get(idx);
            d.hourlyHumidity[k] = hHum.get(idx);
            d.hourlyPrecipProb[k] = hPop.get(idx);
            d.hourlyPrecip[k] = hPrc.get(idx);
            d.hourlyWind[k] = hWind.get(idx);
        }

        if (d.dailySunrise.length > 0 && "--:--".equals(d.dailySunrise[0])) {
            String[] sc = SunCalc.today(lat, lon);
            d.dailySunrise[0] = sc[0];
            d.dailySunset[0] = sc[1];
        }
        d.isDay = !WeatherCodes.isNightHour(d.hourlyTime.length > 0 ? d.hourlyTime[0] : "");
        d.updatedAt = System.currentTimeMillis();
        return d;
    }

    /** MET Norway locationforecast 2.0 compact */
    private static WeatherData fetchMetNo(double lat, double lon, String city) throws IOException {
        String url = String.format(java.util.Locale.US,
                "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f", lat, lon);
        Object root = Json.parse(get(url));
        List<Object> series = Json.list(Json.get(root, "properties"), "timeseries");
        if (series.isEmpty()) throw new IOException("MET Norway 无数据");

        WeatherData d = new WeatherData();
        d.city = city == null ? "" : city;

        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.format.DateTimeFormatter fmtIso = java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm", java.util.Locale.US);

        Object first = series.get(0);
        Object det = Json.get(first, "data", "instant", "details");
        d.currentTemp = (int) Math.round(Json.num(det, "air_temperature", 0));
        d.feelsLike = d.currentTemp;
        d.humidity = (int) Math.round(Json.num(det, "relative_humidity", 0));
        d.windSpeed = Json.num(det, "wind_speed", 0) * 3.6;
        d.windDir = (int) Math.round(Json.num(det, "wind_from_direction", 0));
        d.pressure = Json.num(det, "air_pressure_at_sea_level", 0);
        d.cloudCover = (int) Math.round(Json.num(det, "cloud_area_fraction", 0));
        Object n1 = Json.get(first, "data", "next_1_hours");
        d.precip = Json.num(Json.get(n1, "details"), "precipitation_amount", 0);
        d.currentCode = WeatherCodes.fromMetSymbol(Json.s(Json.get(n1, "summary"), "symbol_code", ""));

        List<String> hTime = new ArrayList<>();
        List<Integer> hTemp = new ArrayList<>();
        List<Integer> hCode = new ArrayList<>();
        List<Integer> hHum = new ArrayList<>();
        List<Integer> hPop = new ArrayList<>();
        List<Double> hPrc = new ArrayList<>();
        List<Double> hWind = new ArrayList<>();

        // 逐日聚合
        java.util.LinkedHashMap<String, double[]> agg = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, Integer> aggCode = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, Integer> aggCount = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, Integer> aggPopCode = new java.util.LinkedHashMap<>();

        for (Object item : series) {
            String iso = Json.s(item, "time", "");
            if (iso.isEmpty()) continue;
            java.time.ZonedDateTime zt;
            try {
                zt = java.time.Instant.parse(iso).atZone(zone);
            } catch (Exception e) {
                continue;
            }
            Object det2 = Json.get(item, "data", "instant", "details");
            double t = Json.num(det2, "air_temperature", 0);
            Object nxt = Json.get(item, "data", "next_1_hours");
            String sym = Json.s(Json.get(nxt, "summary"), "symbol_code", "");
            double prc = Json.num(Json.get(nxt, "details"), "precipitation_amount", 0);
            double wind = Json.num(det2, "wind_speed", 0) * 3.6;
            int code = WeatherCodes.fromMetSymbol(sym);

            String date = zt.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
            double[] box = agg.get(date);
            if (box == null) {
                box = new double[]{t, t, 0, 0, 0};
                agg.put(date, box);
                aggCode.put(date, code);
                aggCount.put(date, 0);
                aggPopCode.put(date, code);
            }
            box[0] = Math.min(box[0], t);
            box[1] = Math.max(box[1], t);
            box[2] += prc;
            box[3] = Math.max(box[3], wind);
            box[4] += 1;
            aggCount.put(date, aggCount.get(date) + 1);
            int hour = zt.getHour();
            if (hour >= 11 && hour <= 13) {
                aggPopCode.put(date, code);
            }

            if (hTime.size() < 120) {
                hTime.add(zt.format(fmtIso));
                hTemp.add((int) Math.round(t));
                hCode.add(code);
                hHum.add((int) Math.round(Json.num(det2, "relative_humidity", 0)));
                hPop.add(0);
                hPrc.add(prc);
                hWind.add(wind);
            }
        }

        int nowIdx = 0;
        String nowKey = java.time.LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
                .format(fmtIso);
        for (int i = 0; i < hTime.size(); i++) {
            if (hTime.get(i).compareTo(nowKey) >= 0) {
                nowIdx = Math.max(0, i - 1);
                break;
            }
        }
        int hn = Math.min(24, Math.max(0, hTime.size() - nowIdx));
        d.hourlyTime = new String[hn];
        d.hourlyTemp = new int[hn];
        d.hourlyCode = new int[hn];
        d.hourlyHumidity = new int[hn];
        d.hourlyPrecipProb = new int[hn];
        d.hourlyPrecip = new double[hn];
        d.hourlyWind = new double[hn];
        for (int k = 0; k < hn; k++) {
            int idx = nowIdx + k;
            d.hourlyTime[k] = hTime.get(idx);
            d.hourlyTemp[k] = hTemp.get(idx);
            d.hourlyCode[k] = hCode.get(idx);
            d.hourlyHumidity[k] = hHum.get(idx);
            d.hourlyPrecipProb[k] = hPop.get(idx);
            d.hourlyPrecip[k] = hPrc.get(idx);
            d.hourlyWind[k] = hWind.get(idx);
        }

        int dn = Math.min(7, agg.size());
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
        int k2 = 0;
        for (java.util.Map.Entry<String, double[]> e : agg.entrySet()) {
            if (k2 >= dn) break;
            String date = e.getKey();
            double[] box = e.getValue();
            d.dailyDate[k2] = date;
            d.dailyDay[k2] = k2 == 0 ? "今天" : weekday(date);
            d.dailyMin[k2] = (int) Math.round(box[0]);
            d.dailyMax[k2] = (int) Math.round(box[1]);
            d.dailyPrecip[k2] = box[2];
            d.dailyWindMax[k2] = box[3];
            d.dailyCode[k2] = aggPopCode.containsKey(date) ? aggPopCode.get(date) : aggCode.get(date);
            String[] sc = SunCalc.times(lat, lon, LocalDate.parse(date),
                    zone.getRules().getOffset(java.time.Instant.now()).getTotalSeconds() / 60);
            d.dailySunrise[k2] = sc[0];
            d.dailySunset[k2] = sc[1];
            k2++;
        }

        d.isDay = !WeatherCodes.isNightHour(d.hourlyTime.length > 0 ? d.hourlyTime[0] : "");
        d.updatedAt = System.currentTimeMillis();
        return d;
    }

    /** "06:41 AM" → "06:41" */
    private static String parseAmPm(String v) {
        if (v == null) return "--:--";
        String s = v.trim();
        try {
            java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter
                    .ofPattern("hh:mm a", java.util.Locale.US);
            return java.time.LocalTime.parse(s.toUpperCase(java.util.Locale.US), f)
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return s.length() >= 5 ? s.substring(0, 5) : s;
        }
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
            try {
                return getOnce(url, false);
            } catch (IOException e) {
                last = e;
                if (isTlsProblem(e)) break;
                sleep(400L * (attempt + 1));
            }
        }
        // TLS 握手失败（企业代理 / 本机根证书缺失 / TLS 版本协商失败）时放宽校验重试
        if (last != null && isTlsProblem(last)) {
            try {
                return getOnce(url, true);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last == null ? new IOException("网络异常") : last;
    }

    private static String getOnce(String url, boolean relaxed) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "LightWeather-Desktop/3.0 (Windows)");
            conn.setRequestProperty("Accept", "application/json");
            if (relaxed && conn instanceof javax.net.ssl.HttpsURLConnection) {
                javax.net.ssl.HttpsURLConnection https = (javax.net.ssl.HttpsURLConnection) conn;
                https.setSSLSocketFactory(relaxedContext().getSocketFactory());
                https.setHostnameVerifier((host, session) -> true);
            }
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                return bos.toString(StandardCharsets.UTF_8);
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean isTlsProblem(Throwable e) {
        while (e != null) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase(java.util.Locale.US);
            if (e instanceof javax.net.ssl.SSLException
                    || msg.contains("handshake_failure")
                    || msg.contains("ssl")
                    || msg.contains("pkix")
                    || msg.contains("certificate")) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static volatile javax.net.ssl.SSLContext RELAXED;

    /** 仅在 TLS 握手失败时启用的宽松上下文（仍走加密，只是不校验证书链） */
    private static javax.net.ssl.SSLContext relaxedContext() {
        javax.net.ssl.SSLContext local = RELAXED;
        if (local != null) return local;
        synchronized (WeatherClient.class) {
            if (RELAXED != null) return RELAXED;
            try {
                javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[]{
                        new javax.net.ssl.X509TrustManager() {
                            @Override
                            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                return new java.security.cert.X509Certificate[0];
                            }
                        }};
                javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
                ctx.init(null, trustAll, new java.security.SecureRandom());
                RELAXED = ctx;
            } catch (Exception e) {
                throw new IllegalStateException("无法初始化 TLS 回退上下文", e);
            }
            return RELAXED;
        }
    }
}
