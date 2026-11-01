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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 天气数据客户端 —— 零第三方库、无 API Key，支持多数据源：
 *  · Open-Meteo     https://api.open-meteo.com/v1/forecast
 *  · wttr.in        https://wttr.in
 *  · MET Norway     https://api.met.no/weatherapi/locationforecast/2.0/compact
 * 空气质量统一来自 Open-Meteo Air Quality（其它源不支持）。
 *
 * 「自动」模式会按 Open-Meteo → wttr.in → MET Norway 的顺序回退，任意一个成功即返回。
 */
public final class WeatherClient {

    private static final String TAG = "WeatherClient";

    /** 数据源 id */
    public static final int P_AUTO = 0;
    public static final int P_OPEN_METEO = 1;
    public static final int P_WTTR = 2;
    public static final int P_METNO = 3;

    private static final int CONNECT_TIMEOUT = 10_000;
    private static final int READ_TIMEOUT = 12_000;

    private static final String GEO_BASE = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String FC_BASE = "https://api.open-meteo.com/v1/forecast";
    private static final String AIR_BASE = "https://air-quality-api.open-meteo.com/v1/air-quality";
    private static final String WTTR_BASE = "https://wttr.in";
    private static final String METNO_BASE = "https://api.met.no/weatherapi/locationforecast/2.0/compact";

    private static final String UA = "LightWeather/3.0 (Android; +https://github.com/on445251-bit/miniweather)";

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
    // 数据源
    // =====================================================================

    public static String providerName(int id) {
        switch (id) {
            case P_OPEN_METEO: return "Open-Meteo";
            case P_WTTR: return "wttr.in";
            case P_METNO: return "MET Norway";
            default: return "自动";
        }
    }

    private static int[] providerOrder(int id) {
        switch (id) {
            case P_OPEN_METEO: return new int[]{P_OPEN_METEO, P_WTTR, P_METNO};
            case P_WTTR: return new int[]{P_WTTR, P_OPEN_METEO, P_METNO};
            case P_METNO: return new int[]{P_METNO, P_OPEN_METEO, P_WTTR};
            default: return new int[]{P_OPEN_METEO, P_WTTR, P_METNO};
        }
    }

    // =====================================================================
    // 地理编码
    // =====================================================================

    /** 城市搜索候选 */
    public static class Geo {
        public String name = "";
        public String area = "";        // 省 / 州（已补全「省/市/自治区」后缀）
        public String country = "";
        public String countryCode = "";
        public double lat, lon;
        public long population;
        public String feature = "";

        /** 例如「杭州 · 浙江省」或「上海市 · 上海市, 中国」 */
        public String label() {
            StringBuilder sb = new StringBuilder(name);
            if (!area.isEmpty() && !area.equals(name)) sb.append(" · ").append(area);
            if (!"CN".equals(countryCode) && !country.isEmpty()) sb.append(", ").append(country);
            return sb.toString();
        }
    }

    /**
     * 按城市名搜索，返回排序后的候选（同名时优先省会/大城市）。
     * 网络失败返回空列表，不会抛出异常。
     */
    public static List<Geo> geocodeList(String keyword, int count) {
        List<Geo> out = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) return out;
        String kw = keyword.trim();

        parseGeo(kw, out, "zh", Math.max(count, 8));
        if (out.isEmpty()) parseGeo(kw, out, "en", Math.max(count, 8));

        // 去重（同名同省）
        List<Geo> uniq = new ArrayList<>();
        for (Geo g : out) {
            boolean dup = false;
            for (Geo u : uniq) {
                if (u.name.equals(g.name) && u.area.equals(g.area)
                        && Math.abs(u.lat - g.lat) < 0.2 && Math.abs(u.lon - g.lon) < 0.2) {
                    dup = true;
                    break;
                }
            }
            if (!dup) uniq.add(g);
        }

        uniq.sort(geoComparator(kw));
        if (uniq.size() > count) uniq = new ArrayList<>(uniq.subList(0, count));
        return uniq;
    }

    /** 兼容旧接口：只取第一个结果，失败返回 null */
    public static double[] geocode(String city) {
        List<Geo> list = geocodeList(city, 1);
        if (list.isEmpty()) return null;
        Geo g = list.get(0);
        return new double[]{g.lat, g.lon};
    }

    private static Comparator<Geo> geoComparator(final String keyword) {
        return (a, b) -> {
            boolean ea = a.name.equalsIgnoreCase(keyword);
            boolean eb = b.name.equalsIgnoreCase(keyword);
            if (ea != eb) return ea ? -1 : 1;

            boolean ca = isChinaArea(a.countryCode);
            boolean cb = isChinaArea(b.countryCode);
            if (ca != cb) return ca ? -1 : 1;

            if (a.population != b.population) return Long.compare(b.population, a.population);

            return Integer.compare(rank(a.feature), rank(b.feature));
        };
    }

    private static boolean isChinaArea(String cc) {
        return "CN".equals(cc) || "HK".equals(cc) || "MO".equals(cc) || "TW".equals(cc);
    }

    private static int rank(String feature) {
        if (feature == null) return 9;
        switch (feature) {
            case "PPLC": return 0;   // 首都
            case "PPLA": return 1;   // 省会 / 一级行政区首府
            case "PPLA2": return 2;
            case "PPLA3": return 3;
            case "PPL":  return 4;
            default:     return 8;
        }
    }

    private static void parseGeo(String keyword, List<Geo> out, String lang, int count) {
        try {
            String url = GEO_BASE + "?count=" + count + "&language=" + lang + "&format=json&name="
                    + URLEncoder.encode(keyword, "UTF-8");
            JSONObject root = getJson(url);
            JSONArray arr = root.optJSONArray("results");
            if (arr == null) return;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject r = arr.optJSONObject(i);
                if (r == null) continue;
                Geo g = new Geo();
                g.name = r.optString("name", keyword);
                g.countryCode = r.optString("country_code", "");
                g.country = r.optString("country", "");
                g.area = areaText(r.optString("admin1", ""), g.countryCode);
                g.lat = r.optDouble("latitude", 0);
                g.lon = r.optDouble("longitude", 0);
                g.population = r.optLong("population", 0);
                g.feature = r.optString("feature_code", "");
                out.add(g);
            }
        } catch (Exception e) {
            Log.w(TAG, "geocode(" + lang + ") error: " + e.getMessage());
        }
    }

    /** 省份名称补全：「浙江」→「浙江省」，直辖市 →「上海市」，自治区 → 全称 */
    public static String areaText(String admin1, String countryCode) {
        if (admin1 == null || admin1.isEmpty()) return "";
        switch (admin1) {
            case "北京": return "北京市";
            case "上海": return "上海市";
            case "天津": return "天津市";
            case "重庆": return "重庆市";
            case "内蒙古": return "内蒙古自治区";
            case "新疆": return "新疆维吾尔自治区";
            case "西藏": return "西藏自治区";
            case "宁夏": return "宁夏回族自治区";
            case "广西": return "广西壮族自治区";
            case "香港": return "香港特别行政区";
            case "澳门": return "澳门特别行政区";
            default:
        }
        if (admin1.length() >= 4) return admin1;               // 已是全称
        if (admin1.endsWith("省") || admin1.endsWith("市")) return admin1;
        if ("CN".equals(countryCode)) return admin1 + "省";
        return admin1;
    }

    // =====================================================================
    // 天气 + 空气质量
    // =====================================================================

    /** 拉取完整天气，按数据源顺序回退；空气质量失败不影响主流程 */
    public static WeatherData fetchAll(int provider, double lat, double lon,
                                       String cityFallback, String area) {
        WeatherData d = fetch(provider, lat, lon, cityFallback, area);
        if (d != null) fillAirQuality(d);
        return d;
    }

    /** 依次尝试数据源，任一成功即返回；全部失败返回 null */
    public static WeatherData fetch(int provider, double lat, double lon,
                                    String cityFallback, String area) {
        String city = (cityFallback == null || cityFallback.trim().isEmpty())
                ? String.format(Locale.US, "%.2f, %.2f", lat, lon)
                : cityFallback;

        for (int p : providerOrder(provider)) {
            try {
                WeatherData d;
                switch (p) {
                    case P_WTTR:
                        d = fetchWttr(lat, lon);
                        break;
                    case P_METNO:
                        d = fetchMetNo(lat, lon);
                        break;
                    default:
                        d = fetchOpenMeteo(lat, lon);
                        break;
                }
                if (d != null) {
                    d.city = city;
                    d.area = area == null ? "" : area;
                    d.lat = lat;
                    d.lon = lon;
                    d.source = providerName(p);
                    if (d.updatedAt <= 0) d.updatedAt = System.currentTimeMillis();
                    return d;
                }
            } catch (Exception e) {
                Log.w(TAG, providerName(p) + " 失败: " + e.getMessage());
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // Open-Meteo
    // ---------------------------------------------------------------------

    private static WeatherData fetchOpenMeteo(double lat, double lon) throws Exception {
        String url = FC_BASE + FC_QUERY
                + "&latitude=" + fmtCoord(lat)
                + "&longitude=" + fmtCoord(lon);
        JSONObject root = getJson(url);
        WeatherData d = new WeatherData();
        d.updatedAt = System.currentTimeMillis();

        int utcOffset = root.optInt("utc_offset_seconds", 0);
        JSONObject cUnits = root.optJSONObject("current_units");
        String units = cUnits == null ? "" : cUnits.optString("wind_speed_10m", "");

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
        if (units.contains("m/s")) d.windSpeed *= 3.6;

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
    }

    // ---------------------------------------------------------------------
    // wttr.in（WorldWeatherOnline 数据）
    // ---------------------------------------------------------------------

    private static WeatherData fetchWttr(double lat, double lon) throws Exception {
        String url = WTTR_BASE + "/" + fmtCoord(lat) + "," + fmtCoord(lon) + "?format=j1&lang=zh";
        JSONObject root = getJson(url);

        JSONArray cc = root.optJSONArray("current_condition");
        if (cc == null || cc.length() == 0) return null;
        JSONObject c = cc.optJSONObject(0);

        WeatherData d = new WeatherData();
        d.updatedAt = System.currentTimeMillis();
        d.currentTemp = (int) Math.round(c.optDouble("temp_C", 0));
        d.feelsLike = (int) Math.round(c.optDouble("FeelsLikeC", d.currentTemp));
        d.humidity = c.optInt("humidity", 0);
        d.currentCode = WeatherCodes.fromWwo(c.optInt("weatherCode", 113));
        d.windSpeed = c.optDouble("windspeedKmph", 0);
        d.windDir = c.optInt("winddirDegree", 0);
        d.pressure = c.optDouble("pressure", 1013);
        d.cloudCover = c.optInt("cloudcover", 0);
        d.precip = c.optDouble("precipMM", 0);

        JSONArray days = root.optJSONArray("weather");
        if (days == null || days.length() == 0) return null;

        // 逐小时：每个 day 有 8 条 3 小时间隔的 hourly
        ZoneId zone = ZoneId.systemDefault();
        List<long[]> hTimes = new ArrayList<>();
        List<Integer> hTemp = new ArrayList<>();
        List<Integer> hCode = new ArrayList<>();
        List<Integer> hPop = new ArrayList<>();
        List<Double> hPrc = new ArrayList<>();
        List<Integer> hHum = new ArrayList<>();
        List<Double> hWind = new ArrayList<>();

        String todayStr = days.optJSONObject(0).optString("date", "");
        int sunriseMin = -1, sunsetMin = -1;

        for (int i = 0; i < days.length(); i++) {
            JSONObject day = days.optJSONObject(i);
            if (day == null) continue;
            String dateStr = day.optString("date", "");
            JSONArray hh = day.optJSONArray("hourly");
            if (hh == null) continue;
            for (int j = 0; j < hh.length(); j++) {
                JSONObject h = hh.optJSONObject(j);
                if (h == null) continue;
                try {
                    int hour = Integer.parseInt(h.optString("time", "0")) / 100;
                    LocalDateTime ldt = LocalDate.parse(dateStr).atTime(hour, 0);
                    hTimes.add(new long[]{ldt.atZone(zone).toInstant().toEpochMilli()});
                    hTemp.add((int) Math.round(h.optDouble("tempC", 0)));
                    hCode.add(WeatherCodes.fromWwo(h.optInt("weatherCode", 113)));
                    hPop.add(h.optInt("chanceofrain", 0));
                    hPrc.add(h.optDouble("precipMM", 0));
                    hHum.add(h.optInt("humidity", 0));
                    hWind.add(h.optDouble("windspeedKmph", 0));
                } catch (Exception ignore) {
                }
            }
            if (i == 0) {
                JSONArray astro = day.optJSONArray("astronomy");
                if (astro != null && astro.length() > 0) {
                    JSONObject a = astro.optJSONObject(0);
                    sunriseMin = parseAmPm(a.optString("sunrise", ""));
                    sunsetMin = parseAmPm(a.optString("sunset", ""));
                }
            }
        }

        // 从当前小时开始截取 24 条
        long nowHour = System.currentTimeMillis() / 3_600_000L * 3_600_000L;
        int startIdx = -1;
        for (int i = 0; i < hTimes.size(); i++) {
            if (hTimes.get(i)[0] >= nowHour) {
                startIdx = i;
                break;
            }
        }
        if (startIdx < 0) startIdx = Math.max(0, hTimes.size() - 24);

        int n = Math.min(24, hTimes.size() - startIdx);
        if (n > 0) {
            d.hourlyTime = new String[n];
            d.hourlyTemp = new int[n];
            d.hourlyCode = new int[n];
            d.hourlyHumidity = new int[n];
            d.hourlyPrecipProb = new int[n];
            d.hourlyPrecip = new double[n];
            d.hourlyWind = new double[n];
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            for (int i = 0; i < n; i++) {
                int k = startIdx + i;
                LocalDateTime ldt = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(hTimes.get(k)[0]), zone);
                d.hourlyTime[i] = ldt.format(fmt);
                d.hourlyTemp[i] = hTemp.get(k);
                d.hourlyCode[i] = hCode.get(k);
                d.hourlyHumidity[i] = hHum.get(k);
                d.hourlyPrecipProb[i] = hPop.get(k);
                d.hourlyPrecip[i] = hPrc.get(k);
                d.hourlyWind[i] = hWind.get(k);
            }
        }

        // 逐日
        int dn = Math.min(7, days.length());
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

        LocalDate today = LocalDate.now();
        for (int i = 0; i < dn; i++) {
            JSONObject day = days.optJSONObject(i);
            String dateStr = day == null ? "" : day.optString("date", "");
            d.dailyDate[i] = dateStr;
            d.dailyDay[i] = weekday(dateStr, i == 0);
            d.dailyMin[i] = (int) Math.round(day == null ? 0 : day.optDouble("mintempC", 0));
            d.dailyMax[i] = (int) Math.round(day == null ? 0 : day.optDouble("maxtempC", 0));
            d.dailyUv[i] = (int) Math.round(day == null ? 0 : day.optDouble("uvIndex", 0));

            int maxPop = 0;
            double sumPrc = 0;
            double maxWind = 0;
            int bestCode = 3;
            int bestPop = -1;
            JSONArray hh = day == null ? null : day.optJSONArray("hourly");
            if (hh != null) {
                for (int j = 0; j < hh.length(); j++) {
                    JSONObject h = hh.optJSONObject(j);
                    if (h == null) continue;
                    int pop = h.optInt("chanceofrain", 0);
                    maxPop = Math.max(maxPop, pop);
                    sumPrc += h.optDouble("precipMM", 0);
                    maxWind = Math.max(maxWind, h.optDouble("windspeedKmph", 0));
                    // 取正午附近的天气代码作为当天代表
                    String t = h.optString("time", "0");
                    if ("1200".equals(t) || bestPop < 0) {
                        bestCode = WeatherCodes.fromWwo(h.optInt("weatherCode", 113));
                        bestPop = 0;
                    }
                }
            }
            d.dailyPrecipProb[i] = maxPop;
            d.dailyPrecip[i] = sumPrc;
            d.dailyWindMax[i] = maxWind;
            d.dailyCode[i] = bestCode;

            if (i == 0 && sunriseMin >= 0 && sunsetMin >= 0) {
                d.dailySunrise[i] = String.format(Locale.US, "%02d:%02d", sunriseMin / 60, sunriseMin % 60);
                d.dailySunset[i] = String.format(Locale.US, "%02d:%02d", sunsetMin / 60, sunsetMin % 60);
            } else {
                try {
                    LocalDate date = LocalDate.parse(dateStr);
                    String[] t = SunCalc.times(lat, lon, date, zone.getRules()
                            .getOffset(date.atTime(12, 0).atZone(zone).toInstant()).getTotalSeconds() / 60);
                    d.dailySunrise[i] = t[0];
                    d.dailySunset[i] = t[1];
                } catch (Exception ignore) {
                    d.dailySunrise[i] = "--:--";
                    d.dailySunset[i] = "--:--";
                }
            }
        }

        // 昼夜判断：用当地此刻与日出日落比较
        int nowMin = LocalDateTime.now().getHour() * 60 + LocalDateTime.now().getMinute();
        if (sunriseMin >= 0 && sunsetMin >= 0) {
            d.isDay = nowMin >= sunriseMin && nowMin < sunsetMin;
        } else {
            d.isDay = nowMin >= 360 && nowMin < 1080;
        }
        return d;
    }

    /** "06:41 AM" → 401 */
    private static int parseAmPm(String s) {
        try {
            String t = s.trim().toUpperCase(Locale.US);
            boolean pm = t.endsWith("PM");
            t = t.replace("AM", "").replace("PM", "").trim();
            String[] hm = t.split(":");
            int h = Integer.parseInt(hm[0].trim());
            int m = hm.length > 1 ? Integer.parseInt(hm[1].trim()) : 0;
            if (pm && h < 12) h += 12;
            if (!pm && h == 12) h = 0;
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------------
    // MET Norway
    // ---------------------------------------------------------------------

    private static WeatherData fetchMetNo(double lat, double lon) throws Exception {
        String url = METNO_BASE + "?lat=" + fmtCoord(lat) + "&lon=" + fmtCoord(lon);
        JSONObject root = getJson(url);

        JSONObject props = root.optJSONObject("properties");
        JSONArray series = props == null ? null : props.optJSONArray("timeseries");
        if (series == null || series.length() == 0) return null;

        ZoneId zone = ZoneId.systemDefault();
        WeatherData d = new WeatherData();
        d.updatedAt = System.currentTimeMillis();

        List<long[]> times = new ArrayList<>();
        List<Double> temps = new ArrayList<>();
        List<Integer> codes = new ArrayList<>();
        List<Integer> hums = new ArrayList<>();
        List<Integer> pops = new ArrayList<>();
        List<Double> prcs = new ArrayList<>();
        List<Double> winds = new ArrayList<>();
        List<Double> dirs = new ArrayList<>();
        List<Double> press = new ArrayList<>();
        List<Double> clouds = new ArrayList<>();
        List<Boolean> dayFlags = new ArrayList<>();

        for (int i = 0; i < series.length(); i++) {
            JSONObject e = series.optJSONObject(i);
            if (e == null) continue;
            String t = e.optString("time", "");
            JSONObject data = e.optJSONObject("data");
            if (data == null) continue;
            JSONObject inst = data.optJSONObject("instant");
            JSONObject det = inst == null ? null : inst.optJSONObject("details");
            if (det == null) continue;

            long epoch;
            try {
                epoch = Instant.parse(t).toEpochMilli();
            } catch (Exception ex) {
                continue;
            }

            JSONObject n1 = data.optJSONObject("next_1_hours");
            JSONObject n6 = data.optJSONObject("next_6_hours");
            String sym = null;
            int pop = 0;
            double prc = 0;
            if (n1 != null) {
                JSONObject s = n1.optJSONObject("summary");
                sym = s == null ? null : s.optString("symbol_code", null);
                JSONObject dd = n1.optJSONObject("details");
                if (dd != null) {
                    prc = dd.optDouble("precipitation_amount", 0);
                    pop = dd.optInt("probability_of_precipitation", 0);
                }
            }
            if (sym == null && n6 != null) {
                JSONObject s = n6.optJSONObject("summary");
                sym = s == null ? null : s.optString("symbol_code", null);
                JSONObject dd = n6.optJSONObject("details");
                if (dd != null) {
                    if (prc == 0) prc = dd.optDouble("precipitation_amount", 0) / 6.0;
                    pop = dd.optInt("probability_of_precipitation", pop);
                }
            }

            times.add(new long[]{epoch});
            temps.add(det.optDouble("air_temperature", 0));
            codes.add(WeatherCodes.fromMetSymbol(sym));
            hums.add((int) Math.round(det.optDouble("relative_humidity", 0)));
            pops.add(pop);
            prcs.add(prc);
            winds.add(det.optDouble("wind_speed", 0) * 3.6);
            dirs.add(det.optDouble("wind_from_direction", 0));
            press.add(det.optDouble("air_pressure_at_sea_level", 1013));
            clouds.add(det.optDouble("cloud_area_fraction", 0));
            dayFlags.add(sym != null && sym.endsWith("_day"));
        }

        if (times.isEmpty()) return null;

        // 找当前时刻最近的一条
        long now = System.currentTimeMillis();
        int cur = 0;
        for (int i = 0; i < times.size(); i++) {
            if (times.get(i)[0] <= now) cur = i;
            else break;
        }

        d.currentTemp = (int) Math.round(temps.get(cur));
        d.feelsLike = d.currentTemp;
        d.currentCode = codes.get(cur);
        d.humidity = hums.get(cur);
        d.windSpeed = winds.get(cur);
        d.windDir = (int) Math.round(dirs.get(cur));
        d.pressure = press.get(cur);
        d.cloudCover = (int) Math.round(clouds.get(cur));
        d.precip = prcs.get(cur);
        d.isDay = dayFlags.get(cur);

        // 逐小时（截取 24 条，间隔可能为 1h 或 6h）
        int n = Math.min(24, times.size() - cur);
        if (n > 0) {
            d.hourlyTime = new String[n];
            d.hourlyTemp = new int[n];
            d.hourlyCode = new int[n];
            d.hourlyHumidity = new int[n];
            d.hourlyPrecipProb = new int[n];
            d.hourlyPrecip = new double[n];
            d.hourlyWind = new double[n];
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            for (int i = 0; i < n; i++) {
                int k = cur + i;
                LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(times.get(k)[0]), zone);
                d.hourlyTime[i] = ldt.format(fmt);
                d.hourlyTemp[i] = (int) Math.round(temps.get(k));
                d.hourlyCode[i] = codes.get(k);
                d.hourlyHumidity[i] = hums.get(k);
                d.hourlyPrecipProb[i] = pops.get(k);
                d.hourlyPrecip[i] = prcs.get(k);
                d.hourlyWind[i] = winds.get(k);
            }
        }

        // 逐日聚合
        Map<String, DayAgg> agg = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(times.get(i)[0]), zone);
            String key = ldt.toLocalDate().toString();
            DayAgg a = agg.get(key);
            if (a == null) {
                a = new DayAgg();
                a.date = key;
                a.min = Double.MAX_VALUE;
                a.max = -Double.MAX_VALUE;
                agg.put(key, a);
                order.add(key);
            }
            a.min = Math.min(a.min, temps.get(i));
            a.max = Math.max(a.max, temps.get(i));
            a.pop = Math.max(a.pop, pops.get(i));
            a.prc += prcs.get(i);
            a.wind = Math.max(a.wind, winds.get(i));
            if (ldt.getHour() >= 9 && ldt.getHour() <= 15) a.code = codes.get(i);
        }

        order.sort(String::compareTo);
        int dn = Math.min(7, order.size());
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

        for (int i = 0; i < dn; i++) {
            DayAgg a = agg.get(order.get(i));
            d.dailyDate[i] = a.date;
            d.dailyDay[i] = weekday(a.date, i == 0);
            d.dailyMin[i] = (int) Math.round(a.min == Double.MAX_VALUE ? 0 : a.min);
            d.dailyMax[i] = (int) Math.round(a.max == -Double.MAX_VALUE ? 0 : a.max);
            d.dailyCode[i] = a.code;
            d.dailyPrecipProb[i] = a.pop;
            d.dailyPrecip[i] = a.prc;
            d.dailyWindMax[i] = a.wind;
            d.dailyUv[i] = -1;
            try {
                LocalDate date = LocalDate.parse(a.date);
                String[] t = SunCalc.times(lat, lon, date, zone.getRules()
                        .getOffset(date.atTime(12, 0).atZone(zone).toInstant()).getTotalSeconds() / 60);
                d.dailySunrise[i] = t[0];
                d.dailySunset[i] = t[1];
            } catch (Exception ignore) {
                d.dailySunrise[i] = "--:--";
                d.dailySunset[i] = "--:--";
            }
        }
        return d;
    }

    private static class DayAgg {
        String date;
        double min, max, prc, wind;
        int pop;
        int code = 3;
    }

    // ---------------------------------------------------------------------
    // 空气质量（统一走 Open-Meteo）
    // ---------------------------------------------------------------------

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
            Log.w(TAG, "air quality error: " + e.getMessage());
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
            conn.setRequestProperty("User-Agent", UA);
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
            int y = Integer.parseInt(iso.substring(0, 4));
            int m = Integer.parseInt(iso.substring(5, 7));
            int d = Integer.parseInt(iso.substring(8, 10));
            int hh = Integer.parseInt(iso.substring(11, 13));
            int mm = Integer.parseInt(iso.substring(14, 16));
            return LocalDateTime.of(y, m, d, hh, mm)
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
