package com.example.weather;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.weather.databinding.ActivityMainBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_LOC = 100;
    /** 回到前台后超过该时间自动刷新 */
    private static final long AUTO_REFRESH_MS = 10 * 60 * 1000L;
    /** 抽屉里城市条目的起始 id */
    private static final int CITY_ID_BASE = 1000;

    private ActivityMainBinding b;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Executor io = Executors.newSingleThreadExecutor();
    private final LifeIndexAdapter indexAdapter = new LifeIndexAdapter();

    private double lastLat = Double.NaN;
    private double lastLon = Double.NaN;
    private String lastCity = "";

    /** 最近一次成功渲染的数据，用于切换单位时零网络重绘 */
    private WeatherData current;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.rvHourly.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        b.rvIndex.setLayoutManager(new GridLayoutManager(this, 3));
        b.rvIndex.setAdapter(indexAdapter);

        b.nav.setNavigationItemSelectedListener(this::onNavItemSelected);

        b.toolbar.inflateMenu(R.menu.main_menu);
        b.toolbar.setNavigationOnClickListener(v -> b.drawer.openDrawer(GravityCompat.START));
        b.toolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);

        b.etCity.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                hideKeyboard();
                searchByCity(b.etCity.getText().toString().trim());
                return true;
            }
            return false;
        });

        b.btnLoc.setOnClickListener(v -> locate());

        b.swipe.setOnRefreshListener(() -> {
            if (!Double.isNaN(lastLat)) load(lastLat, lastLon, lastCity);
            else {
                b.swipe.setRefreshing(false);
                locate();
            }
        });

        // 先渲染离线缓存，避免白屏
        WeatherData cached = WeatherPrefs.loadFull(this);
        if (cached != null) render(cached, true);

        restoreLastCity();
        rebuildCityMenu();
    }

    /** 恢复上次查看的城市；没有记录则默认北京 */
    private void restoreLastCity() {
        String cur = WeatherPrefs.getCurrent(this);
        if ("我的位置".equals(cur)) {
            double[] loc = WeatherPrefs.getLoc(this);
            if (loc != null) {
                lastLat = loc[0];
                lastLon = loc[1];
                lastCity = "我的位置";
                b.etCity.setText("我的位置");
                load(loc[0], loc[1], "我的位置");
                return;
            }
        }
        WeatherPrefs.City c = TextUtils.isEmpty(cur) ? null : WeatherPrefs.findCity(this, cur);
        if (c != null) {
            lastLat = c.lat;
            lastLon = c.lon;
            lastCity = c.name;
            b.etCity.setText(c.name);
            load(c.lat, c.lon, c.name);
            return;
        }
        b.etCity.setText("北京");
        searchByCity("北京");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (current != null && !Double.isNaN(lastLat)
                && WeatherPrefs.isCacheStale(this, AUTO_REFRESH_MS)) {
            load(lastLat, lastLon, lastCity);
        }
    }

    // =====================================================================
    // 菜单
    // =====================================================================

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_unit_temp) {
            pickTempUnit();
            return true;
        } else if (id == R.id.action_unit_wind) {
            pickWindUnit();
            return true;
        } else if (id == R.id.action_unit_press) {
            pickPressureUnit();
            return true;
        } else if (id == R.id.action_manage) {
            b.drawer.openDrawer(GravityCompat.START);
            return true;
        } else if (id == R.id.action_about) {
            showAbout();
            return true;
        } else if (id == R.id.action_share) {
            shareShot();
            return true;
        }
        return false;
    }

    private void pickTempUnit() {
        String[] items = {getString(R.string.unit_c), getString(R.string.unit_f)};
        int checked = WeatherPrefs.isF(this) ? 1 : 0;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_unit)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    WeatherPrefs.setF(this, which == 1);
                    d.dismiss();
                    rerender();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickWindUnit() {
        String[] keys = {"kmh", "ms", "mph", "bft"};
        String[] items = {
                getString(R.string.unit_kmh), getString(R.string.unit_ms),
                getString(R.string.unit_mph), getString(R.string.unit_bft)};
        int checked = 0;
        String cur = WeatherPrefs.getWindUnit(this);
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(cur)) checked = i;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_wind)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    WeatherPrefs.setWindUnit(this, keys[which]);
                    d.dismiss();
                    rerender();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickPressureUnit() {
        String[] keys = {"hpa", "mmhg", "inhg"};
        String[] items = {
                getString(R.string.unit_hpa), getString(R.string.unit_mmhg),
                getString(R.string.unit_inhg)};
        int checked = 0;
        String cur = WeatherPrefs.getPressUnit(this);
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(cur)) checked = i;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_press)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    WeatherPrefs.setPressUnit(this, keys[which]);
                    d.dismiss();
                    rerender();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAbout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.about_title)
                .setMessage(R.string.about_text)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /** 用已有数据重绘（切换单位、来自缓存的刷新） */
    private void rerender() {
        if (current != null) render(current, true);
    }

    /** 把当前内容区截图并分享 */
    private void shareShot() {
        View target = b.swipe;
        Uri uri = ShareHelper.capture(this, target);
        if (uri == null) {
            Toast.makeText(this, R.string.share_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        ShareHelper.share(this, uri, getString(R.string.share_text));
    }

    // =====================================================================
    // 抽屉 / 城市
    // =====================================================================

    private boolean onNavItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_myloc) {
            double[] loc = WeatherPrefs.getLoc(this);
            if (loc != null) {
                switchTo("我的位置", loc[0], loc[1]);
            } else {
                locate();
            }
            closeDrawer();
            return true;
        }
        if (id == R.id.nav_add) {
            addCurrentCity();
            return true;
        }
        if (id == R.id.nav_manage) {
            showManageDialog();
            return true;
        }
        if (id >= CITY_ID_BASE) {
            String name = item.getTitle().toString();
            WeatherPrefs.City c = WeatherPrefs.findCity(this, name);
            if (c != null) switchTo(name, c.lat, c.lon);
            closeDrawer();
            return true;
        }
        return false;
    }

    private void switchTo(String name, double lat, double lon) {
        WeatherPrefs.setCurrent(this, name);
        lastLat = lat;
        lastLon = lon;
        lastCity = name;
        b.etCity.setText(name);
        load(lat, lon, name);
    }

    private void closeDrawer() {
        if (b.drawer.isDrawerOpen(GravityCompat.START)) {
            b.drawer.closeDrawer(GravityCompat.START);
        }
    }

    /** 城市项：点击切换，长按删除 */
    private void rebuildCityMenu() {
        Menu menu = b.nav.getMenu();
        menu.removeGroup(R.id.grp_cities);
        List<WeatherPrefs.City> cities = WeatherPrefs.getCities(this);
        String cur = WeatherPrefs.getCurrent(this);
        int i = CITY_ID_BASE;
        for (WeatherPrefs.City c : cities) {
            MenuItem mi = menu.add(R.id.grp_cities, i++, Menu.NONE, c.name);
            mi.setIcon(R.drawable.ic_loc);
            mi.setChecked(c.name.equals(cur));
        }
        menu.setGroupCheckable(R.id.grp_cities, true, true);
        MenuItem my = menu.findItem(R.id.nav_myloc);
        if (my != null) my.setChecked("我的位置".equals(cur));
    }

    private void showManageDialog() {
        List<WeatherPrefs.City> cities = WeatherPrefs.getCities(this);
        if (cities.isEmpty()) {
            Toast.makeText(this, R.string.manage_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[cities.size()];
        for (int i = 0; i < cities.size(); i++) names[i] = cities.get(i).name;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.manage_title)
                .setItems(names, (d, which) -> confirmDelete(names[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete(String name) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.delete_confirm, name))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    WeatherPrefs.removeCity(this, name);
                    rebuildCityMenu();
                    Toast.makeText(this, getString(R.string.city_removed, name),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void addCurrentCity() {
        String name = b.tvCity.getText().toString();
        if (name.isEmpty() || Double.isNaN(lastLat)) {
            Toast.makeText(this, R.string.add_city, Toast.LENGTH_SHORT).show();
            return;
        }
        if (WeatherPrefs.findCity(this, name) != null) {
            Toast.makeText(this, getString(R.string.city_exists, name), Toast.LENGTH_SHORT).show();
            return;
        }
        WeatherPrefs.addCity(this, name, lastLat, lastLon);
        WeatherPrefs.setCurrent(this, name);
        rebuildCityMenu();
        Toast.makeText(this, getString(R.string.city_added, name), Toast.LENGTH_SHORT).show();
    }

    // =====================================================================
    // 搜索 / 定位 / 加载
    // =====================================================================

    private void searchByCity(String keyword) {
        if (keyword.isEmpty()) {
            Toast.makeText(this, R.string.no_result, Toast.LENGTH_SHORT).show();
            return;
        }
        if (current == null) showLoading(true);
        io.execute(() -> {
            List<WeatherClient.Geo> list = WeatherClient.geocodeList(keyword, 6);
            ui.post(() -> {
                if (list.isEmpty()) {
                    showLoading(false);
                    b.swipe.setRefreshing(false);
                    onLoadFailed();
                    return;
                }
                if (list.size() == 1) {
                    WeatherClient.Geo g = list.get(0);
                    load(g.lat, g.lon, g.name);
                } else {
                    showLoading(false);
                    b.swipe.setRefreshing(false);
                    showChooser(keyword, list);
                }
            });
        });
    }

    private void showChooser(String keyword, List<WeatherClient.Geo> list) {
        String[] labels = new String[list.size()];
        for (int i = 0; i < list.size(); i++) labels[i] = list.get(i).label();
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.multi_match, keyword))
                .setItems(labels, (d, which) -> {
                    WeatherClient.Geo g = list.get(which);
                    load(g.lat, g.lon, g.name);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void locate() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
            return;
        }
        if (current == null) showLoading(true);
        io.execute(() -> {
            Location loc = getLastKnownLocation();
            ui.post(() -> {
                if (loc == null) {
                    showLoading(false);
                    b.swipe.setRefreshing(false);
                    onLoadFailed();
                    return;
                }
                load(loc.getLatitude(), loc.getLongitude(), "我的位置");
            });
        });
    }

    @SuppressLint("MissingPermission")
    private Location getLastKnownLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gps != null && net != null) {
                return gps.getTime() >= net.getTime() ? gps : net;
            }
            if (gps != null) return gps;
            return net;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void load(double lat, double lon, String city) {
        if (current == null) showLoading(true);
        io.execute(() -> {
            WeatherData d = WeatherClient.fetchAll(lat, lon, city);
            ui.post(() -> {
                showLoading(false);
                b.swipe.setRefreshing(false);
                if (d == null) {
                    onLoadFailed();
                } else {
                    lastLat = d.lat;
                    lastLon = d.lon;
                    lastCity = d.city;
                    render(d, false);
                }
            });
        });
    }

    /** 拉取失败：有旧数据就保留，没有就提示 */
    private void onLoadFailed() {
        if (current != null) {
            Toast.makeText(this, R.string.error_network, Toast.LENGTH_SHORT).show();
            return;
        }
        WeatherData cached = WeatherPrefs.loadFull(this);
        if (cached != null) {
            render(cached, true);
            Toast.makeText(this, R.string.error_network, Toast.LENGTH_SHORT).show();
        } else {
            showError(R.string.error_offline);
        }
    }

    // =====================================================================
    // 渲染
    // =====================================================================

    private void render(WeatherData d, boolean fromCache) {
        if (d == null) return;
        current = d;

        if (!fromCache) {
            WeatherPrefs.saveLoc(this, d.lat, d.lon);
            WeatherPrefs.saveFull(this, d);
            if (!"我的位置".equals(d.city) && WeatherPrefs.findCity(this, d.city) == null) {
                WeatherPrefs.addCity(this, d.city, d.lat, d.lon);
            }
            WeatherPrefs.setCurrent(this, d.city);
            rebuildCityMenu();
        }

        b.tvError.setVisibility(View.GONE);
        b.scroll.setVisibility(View.VISIBLE);

        boolean night = !d.isDay;

        // ---------- 当前 ----------
        b.tvCity.setText(d.city);
        b.ivIcon.setImageResource(WeatherCodes.icon(d.currentCode, night));
        b.tvTemp.setText(WeatherPrefs.fmtTempDeg(d.currentTemp, this));
        b.tvDesc.setText(WeatherCodes.desc(d.currentCode, this));
        b.tvExtra.setText(WeatherPrefs.windDirText(d.windDir) + " · "
                + WeatherPrefs.fmtWind(d.windSpeed, this) + " "
                + WeatherPrefs.windUnitLabel(this));

        b.tvBody.setText(WeatherPrefs.fmtTempDeg(d.feelsLike, this));
        b.tvHumidity.setText(d.humidity + "%");
        b.tvWind.setText(WeatherPrefs.fmtWind(d.windSpeed, this) + " "
                + WeatherPrefs.windUnitLabel(this));
        b.tvPressure.setText(WeatherPrefs.fmtPressure(d.pressure, this) + " "
                + WeatherPrefs.pressUnitLabel(this));
        b.tvCloud.setText(d.cloudCover + "%");
        b.tvPrecip.setText(String.format(Locale.CHINA, "%.1f mm", d.precip));

        // ---------- 预警 ----------
        bindAlert(d);

        // ---------- 空气质量 ----------
        bindAir(d);

        // ---------- 逐小时 ----------
        if (d.hasHourly()) {
            b.rvHourly.setAdapter(new HourlyAdapter(this, d.hourlyTime, d.hourlyTemp,
                    d.hourlyCode, d.hourlyPrecipProb));
        } else {
            b.rvHourly.setAdapter(new HourlyAdapter(this, new String[0], new int[0],
                    new int[0], new int[0]));
        }

        // ---------- 趋势曲线 ----------
        if (d.hasDaily()) {
            int n = d.dailyMax.length;
            int[] highs = new int[n];
            int[] lows = new int[n];
            String[] labels = new String[n];
            for (int i = 0; i < n; i++) {
                highs[i] = conv(d.dailyMax[i]);
                lows[i] = conv(d.dailyMin[i]);
                labels[i] = d.dailyDay[i];
            }
            b.tempCurve.setData(highs, lows, labels);
        }

        // ---------- 7 天列表 ----------
        if (d.hasDaily()) {
            b.rvDaily.setAdapter(new DailyAdapter(this, d.dailyDay, d.dailyMin,
                    d.dailyMax, d.dailyCode, d.dailyPrecipProb));
        } else {
            b.rvDaily.setAdapter(new DailyAdapter(this, new String[0], new int[0],
                    new int[0], new int[0], new int[0]));
        }

        // ---------- 日出日落 ----------
        if (d.dailySunrise != null && d.dailySunrise.length > 0
                && d.dailySunset != null && d.dailySunset.length > 0) {
            b.tvSunrise.setText(d.dailySunrise[0]);
            b.tvSunset.setText(d.dailySunset[0]);
            b.tvDaylight.setText(daylight(d.dailySunrise[0], d.dailySunset[0]));
        } else {
            b.tvSunrise.setText("--:--");
            b.tvSunset.setText("--:--");
            b.tvDaylight.setText("--");
        }

        // ---------- 生活指数 ----------
        indexAdapter.setItems(LifeIndex.compute(d));

        // ---------- 背景 ----------
        applyBackground(d.currentCode, night);

        // ---------- 更新时间 ----------
        long ts = d.updatedAt > 0 ? d.updatedAt : System.currentTimeMillis();
        String t = new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date(ts));
        b.tvUpdated.setText(fromCache
                ? getString(R.string.updated_cached, t)
                : getString(R.string.updated, t));
    }

    private void bindAlert(WeatherData d) {
        List<WeatherAlert.Alert> alerts = WeatherAlert.compute(d);
        WeatherAlert.Alert top = WeatherAlert.top(alerts);
        if (top == null) {
            b.cardAlert.setVisibility(View.GONE);
            return;
        }
        b.cardAlert.setVisibility(View.VISIBLE);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(9));
        bg.setColor(WeatherAlert.color(top.level));
        b.tvAlertBadge.setBackground(bg);
        b.tvAlertBadge.setText(top.levelText());

        b.tvAlertTitle.setText(top.title);

        StringBuilder sb = new StringBuilder(top.text);
        if (alerts.size() > 1) {
            sb.append("（共 ").append(alerts.size()).append(" 条提醒）");
        }
        b.tvAlertText.setText(sb.toString());
    }

    private void bindAir(WeatherData d) {
        if (!d.hasAir) {
            b.cardAir.setVisibility(View.GONE);
            return;
        }
        b.cardAir.setVisibility(View.VISIBLE);
        int color = AirQuality.color(d.aqi);
        b.tvAqi.setText(String.valueOf(d.aqi));
        b.tvAqi.setTextColor(color);
        b.tvAqiLevel.setText(AirQuality.level(d.aqi));
        b.tvAqiLevel.setTextColor(color);
        b.tvAqiAdvice.setText(AirQuality.advice(d.aqi));

        String dom = AirQuality.dominant(d);
        String pollutants = String.format(Locale.CHINA,
                "PM2.5 %.0f · PM10 %.0f · O₃ %.0f · NO₂ %.0f · SO₂ %.0f μg/m³"
                        + "\n首要污染物：%s",
                d.pm25, d.pm10, d.ozone, d.no2, d.so2, dom);
        b.tvPollutants.setText(pollutants);
    }

    /** 根据天气代码 + 昼夜切换渐变背景 + 动画 */
    private void applyBackground(int code, boolean night) {
        WeatherBackground.Mode mode;
        int bgRes;
        if (code >= 95) {
            mode = WeatherBackground.Mode.RAIN;
            bgRes = R.drawable.bg_rain;
        } else if (code >= 85) {
            mode = WeatherBackground.Mode.CLOUD;
            bgRes = night ? R.drawable.bg_night : R.drawable.bg_cloudy;
        } else if (code >= 51) {
            mode = WeatherBackground.Mode.RAIN;
            bgRes = R.drawable.bg_rain;
        } else if (code >= 3) {
            mode = WeatherBackground.Mode.CLOUD;
            bgRes = night ? R.drawable.bg_night : R.drawable.bg_cloudy;
        } else if (code == 1 || code == 2) {
            mode = WeatherBackground.Mode.CLOUD;
            bgRes = night ? R.drawable.bg_night : R.drawable.bg_cloudy;
        } else {
            mode = WeatherBackground.Mode.CLEAR;
            bgRes = night ? R.drawable.bg_night : R.drawable.bg_sunny;
        }
        b.bg.setBackgroundResource(bgRes);
        b.bg.setMode(mode);
    }

    // =====================================================================
    // 工具
    // =====================================================================

    /** 摄氏度 → 当前温度单位下的整数值（用于曲线图） */
    private int conv(int celsius) {
        return WeatherPrefs.isF(this)
                ? Math.round(celsius * 9f / 5 + 32)
                : celsius;
    }

    /** "06:47" + "17:12" → "10 小时 25 分" */
    private String daylight(String rise, String set) {
        try {
            int a = minutes(rise);
            int b = minutes(set);
            if (a < 0 || b < 0) return "--";
            int diff = b - a;
            if (diff < 0) diff += 24 * 60;
            return (diff / 60) + " 小时 " + (diff % 60) + " 分";
        } catch (Exception e) {
            return "--";
        }
    }

    private int minutes(String hhmm) {
        if (hhmm == null || hhmm.length() < 5) return -1;
        try {
            return Integer.parseInt(hhmm.substring(0, 2)) * 60
                    + Integer.parseInt(hhmm.substring(3, 5));
        } catch (Exception e) {
            return -1;
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private void hideKeyboard() {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }
        } catch (Exception ignore) {
        }
    }

    private void showLoading(boolean on) {
        ui.post(() -> {
            b.progress.setVisibility(on ? View.VISIBLE : View.GONE);
            if (on) {
                b.tvError.setVisibility(View.GONE);
            }
        });
    }

    private void showError(int resId) {
        ui.post(() -> {
            b.progress.setVisibility(View.GONE);
            b.swipe.setRefreshing(false);
            b.scroll.setVisibility(View.GONE);
            b.tvError.setVisibility(View.VISIBLE);
            b.tvError.setText(resId);
        });
    }

    @Override
    public void onBackPressed() {
        if (b.drawer.isDrawerOpen(GravityCompat.START)) {
            b.drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_LOC && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            locate();
        } else if (req == REQ_LOC) {
            Toast.makeText(this, "需要定位权限才能使用「我的位置」", Toast.LENGTH_SHORT).show();
        }
    }
}
