package com.example.weather;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.navigation.NavigationView;

import com.example.weather.databinding.ActivityMainBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_LOC = 100;
    private ActivityMainBinding b;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.Executor io = Executors.newSingleThreadExecutor();

    private double lastLat = Double.NaN;
    private double lastLon = Double.NaN;
    private String lastCity = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.rvHourly.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // 抽屉：点击城市切换
        b.nav.setNavigationItemSelectedListener(this::onNavItemSelected);

        // toolbar 菜单（单位切换 / 管理城市）
        b.toolbar.inflateMenu(R.menu.main_menu);
        b.toolbar.setNavigationOnClickListener(v -> b.drawer.openDrawer(GravityCompat.START));
        b.toolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);

        b.etCity.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                searchByCity(b.etCity.getText().toString().trim());
                return true;
            }
            return false;
        });

        b.btnLoc.setOnClickListener(v -> locate());

        b.swipe.setOnRefreshListener(() -> {
            if (!Double.isNaN(lastLat)) load(lastLat, lastLon, lastCity);
            else b.swipe.setRefreshing(false);
        });

        // 初始化：恢复上一次查看的城市 / 我的位置
        String cur = WeatherPrefs.getCurrent(this);
        if ("我的位置".equals(cur)) {
            double[] loc = WeatherPrefs.getLoc(this);
            if (loc != null) {
                lastLat = loc[0]; lastLon = loc[1]; lastCity = "我的位置";
                b.etCity.setText("我的位置");
                load(loc[0], loc[1], "我的位置");
            } else {
                b.etCity.setText("北京");
                searchByCity("北京");
            }
        } else if (!cur.isEmpty() && WeatherPrefs.findCity(this, cur) != null) {
            WeatherPrefs.City c = WeatherPrefs.findCity(this, cur);
            lastLat = c.lat; lastLon = c.lon; lastCity = c.name;
            b.etCity.setText(c.name);
            load(c.lat, c.lon, c.name);
        } else {
            b.etCity.setText("北京");
            searchByCity("北京");
        }
        rebuildCityMenu();
    }

    private boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_unit) {
            WeatherPrefs.setF(this, !WeatherPrefs.isF(this));
            // 重新渲染当前数据（用缓存坐标）
            if (!Double.isNaN(lastLat)) load(lastLat, lastLon, lastCity);
            return true;
        } else if (id == R.id.action_manage) {
            b.drawer.openDrawer(GravityCompat.START);
            return true;
        } else if (id == R.id.action_share) {
            shareShot();
            return true;
        }
        return false;
    }

    /** 把当前内容区截图并分享 */
    private void shareShot() {
        // 截取主内容（含天气卡片），避开抽屉
        View target = b.swipe;
        Uri uri = ShareHelper.capture(this, target);
        if (uri == null) {
            Toast.makeText(this, R.string.share_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        ShareHelper.share(this, uri, getString(R.string.share_text));
    }

    private boolean onNavItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_myloc) {
            // “我的位置”快捷项：优先用最后保存的坐标，否则重新定位
            double[] loc = WeatherPrefs.getLoc(this);
            if (loc != null) {
                lastLat = loc[0]; lastLon = loc[1]; lastCity = "我的位置";
                b.etCity.setText("我的位置");
                load(loc[0], loc[1], "我的位置");
            } else {
                locate();
            }
            b.drawer.closeDrawer(GravityCompat.START);
            return true;
        }
        if (id == R.id.nav_add) {
            addCurrentCity();
            return true;
        }
        // 城市项：id 在 rebuildCityMenu 中设置
        if (id >= 1000) {
            String name = item.getTitle().toString();
            WeatherPrefs.City c = WeatherPrefs.findCity(this, name);
            if (c != null) {
                WeatherPrefs.setCurrent(this, name);
                lastLat = c.lat; lastLon = c.lon; lastCity = c.name;
                b.etCity.setText(name);
                load(c.lat, c.lon, name);
            }
            b.drawer.closeDrawer(GravityCompat.START);
            return true;
        }
        return false;
    }

    /** 根据保存的城市重建抽屉菜单（保留固定的“我的位置”与“添加”项） */
    private void rebuildCityMenu() {
        Menu menu = b.nav.getMenu();
        menu.removeGroup(R.id.grp_cities);
        List<WeatherPrefs.City> cities = WeatherPrefs.getCities(this);
        String cur = WeatherPrefs.getCurrent(this);
        int i = 1000;
        for (WeatherPrefs.City c : cities) {
            MenuItem mi = menu.add(R.id.grp_cities, i++, Menu.NONE, c.name);
            mi.setIcon(R.drawable.ic_loc);
            mi.setChecked(c.name.equals(cur));
        }
        menu.setGroupCheckable(R.id.grp_cities, true, true);
        // 同步“我的位置”勾选状态
        MenuItem my = menu.findItem(R.id.nav_myloc);
        if (my != null) my.setChecked("我的位置".equals(cur));
    }

    private void addCurrentCity() {
        String name = b.tvCity.getText().toString();
        if (name.isEmpty() || Double.isNaN(lastLat)) {
            Toast.makeText(this, R.string.add_city, Toast.LENGTH_SHORT).show();
            return;
        }
        if (WeatherPrefs.findCity(this, name) != null) {
            Toast.makeText(this, "已存在：" + name, Toast.LENGTH_SHORT).show();
            return;
        }
        WeatherPrefs.addCity(this, name, lastLat, lastLon);
        WeatherPrefs.setCurrent(this, name);
        rebuildCityMenu();
        Toast.makeText(this, "已添加：" + name, Toast.LENGTH_SHORT).show();
    }

    private void searchByCity(String city) {
        if (city.isEmpty()) {
            Toast.makeText(this, R.string.no_result, Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading(true);
        io.execute(() -> {
            double[] ll = WeatherClient.geocode(city);
            if (ll == null) {
                ui.post(() -> { showLoading(false); showError(R.string.error_empty); });
                return;
            }
            WeatherData d = WeatherClient.fetch(ll[0], ll[1], city);
            render(d);
        });
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
        showLoading(true);
        io.execute(() -> {
            Location loc = getLastKnownLocation();
            if (loc == null) {
                ui.post(() -> { showLoading(false); showError(R.string.error_network); });
                return;
            }
            WeatherData d = WeatherClient.fetch(loc.getLatitude(), loc.getLongitude(), "我的位置");
            render(d);
        });
    }

    @SuppressLint("MissingPermission")
    private Location getLastKnownLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gps != null) return gps;
            if (net != null) return net;
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    private void load(double lat, double lon, String city) {
        showLoading(true);
        io.execute(() -> {
            WeatherData d = WeatherClient.fetch(lat, lon, city);
            render(d);
        });
    }

    private void render(WeatherData d) {
        ui.post(() -> {
            showLoading(false);
            if (d == null) { showError(R.string.error_network); return; }
            lastLat = d.lat; lastLon = d.lon; lastCity = d.city;
            WeatherPrefs.saveLoc(this, d.lat, d.lon);
            WeatherPrefs.cache(this, d);
            // 保存非“我的位置”的城市便于小部件/多城市
            if (!"我的位置".equals(d.city) && WeatherPrefs.findCity(this, d.city) == null)
                WeatherPrefs.addCity(this, d.city, d.lat, d.lon);
            WeatherPrefs.setCurrent(this, d.city);
            rebuildCityMenu();

            b.tvError.setVisibility(View.GONE);
            b.scroll.setVisibility(View.VISIBLE);

            b.tvCity.setText(d.city);
            boolean night = WeatherCodes.isNightNow();
            b.ivIcon.setImageResource(WeatherCodes.icon(d.currentCode, night));
            b.tvTemp.setText(WeatherPrefs.fmtTemp(d.currentTemp, this) + "°");
            b.tvDesc.setText(WeatherCodes.desc(d.currentCode, this));
            b.tvExtra.setText(String.format(Locale.CHINA, getString(R.string.feels_like),
                    WeatherPrefs.fmtTemp(d.feelsLike, this))
                    + "   " + String.format(Locale.CHINA, getString(R.string.humidity), d.humidity)
                    + "   " + String.format(Locale.CHINA, getString(R.string.wind),
                    String.valueOf(Math.round(d.windSpeed))));

            b.rvHourly.setAdapter(new HourlyAdapter(this, night, d.hourlyTime, d.hourlyTemp, d.hourlyCode));
            b.rvDaily.setAdapter(new DailyAdapter(this, night, d.dailyDay, d.dailyMin, d.dailyMax, d.dailyCode));

            applyBackground(d.currentCode, night);
            String t = new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date());
            b.tvUpdated.setText(String.format(Locale.CHINA, getString(R.string.updated), t));
        });
    }

    /** 根据天气代码 + 昼夜切换渐变背景 + 动画 */
    private void applyBackground(int code, boolean night) {
        WeatherBackground.Mode mode;
        int bgRes;
        if (code == 0 || code == 1) { mode = WeatherBackground.Mode.CLEAR; bgRes = night ? R.drawable.bg_night : R.drawable.bg_sunny; }
        else if (code >= 3 && code <= 48) { mode = WeatherBackground.Mode.CLOUD; bgRes = night ? R.drawable.bg_night : R.drawable.bg_cloudy; }
        else if (code >= 51 && code <= 82) { mode = WeatherBackground.Mode.RAIN; bgRes = R.drawable.bg_rain; }
        else if (code >= 85) { mode = WeatherBackground.Mode.CLOUD; bgRes = night ? R.drawable.bg_night : R.drawable.bg_cloudy; }
        else if (code >= 95) { mode = WeatherBackground.Mode.RAIN; bgRes = R.drawable.bg_rain; }
        else { mode = WeatherBackground.Mode.CLEAR; bgRes = night ? R.drawable.bg_night : R.drawable.bg_sunny; }
        b.bg.setBackgroundResource(bgRes);
        b.bg.setMode(mode);
    }

    private void showLoading(boolean on) {
        ui.post(() -> {
            b.progress.setVisibility(on ? View.VISIBLE : View.GONE);
            b.tvError.setVisibility(View.GONE);
        });
    }

    private void showError(int resId) {
        ui.post(() -> {
            b.progress.setVisibility(View.GONE);
            b.scroll.setVisibility(View.GONE);
            b.tvError.setVisibility(View.VISIBLE);
            b.tvError.setText(resId);
        });
    }

    @Override
    public void onBackPressed() {
        if (b.drawer.isDrawerOpen(GravityCompat.START)) b.drawer.closeDrawer(GravityCompat.START);
        else super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_LOC && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) locate();
        else Toast.makeText(this, "需要定位权限才能使用“我的位置”", Toast.LENGTH_SHORT).show();
    }
}
