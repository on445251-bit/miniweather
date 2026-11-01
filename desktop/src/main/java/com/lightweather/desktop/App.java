package com.lightweather.desktop;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 程序入口：正常启动窗口，或 --screenshot 离屏渲染自检 */
public final class App {

    public static void main(String[] args) {
        java.util.List<String> a = new ArrayList<>();
        for (String s : args) a.add(s);
        if (a.contains("--screenshot")) {
            screenshot(a);
            return;
        }
        if (a.contains("--screenshot-frame")) {
            screenshotFrame(a);
            return;
        }
        if (a.contains("--make-icon")) {
            try {
                File ico = new File(arg(a, "--make-icon", "lightweather.ico"));
                AppIcon.writeIco(ico, 256);
                System.out.println("icon: " + ico.getAbsolutePath() + " (" + ico.length() + " bytes)");
            } catch (Exception e) {
                System.err.println("icon failed: " + e);
            }
            return;
        }
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        UIManager.put("OptionPane.messageFont", new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        UIManager.put("OptionPane.buttonFont", new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    /** 真实窗口（含顶栏/侧栏）截图，用于检查整体外壳 */
    private static void screenshotFrame(List<String> a) {
        String out = arg(a, "--screenshot-frame", "frame.png");
        int width = Integer.parseInt(arg(a, "--width", "1060"));
        int height = Integer.parseInt(arg(a, "--height", "820"));
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        final MainFrame[] holder = new MainFrame[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                MainFrame f = new MainFrame();
                f.setSize(width, height);
                f.setLocation(60, 40);
                f.setVisible(true);
                holder[0] = f;
            });
            for (int i = 0; i < 60 && !holder[0].hasData(); i++) {
                Thread.sleep(250);
            }
            Thread.sleep(600);
            System.out.println("cities in sidebar: " + Prefs.get().cities().size()
                    + " last=" + Prefs.get().lastCity());
            SwingUtilities.invokeAndWait(() -> {
                MainFrame f = holder[0];
                BufferedImage img = new BufferedImage(f.getWidth(), f.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                f.paintAll(g);
                g.dispose();
                try {
                    ImageIO.write(img, "png", new File(out));
                    System.out.println("frame screenshot: " + new File(out).getAbsolutePath());
                } catch (Exception e) {
                    System.err.println("write failed: " + e);
                }
                f.dispose();
            });
        } catch (Exception e) {
            System.err.println("frame screenshot failed: " + e);
        }
        System.exit(0);
    }

    private static String arg(List<String> a, String key, String def) {
        int i = a.indexOf(key);
        if (i >= 0 && i + 1 < a.size()) return a.get(i + 1);
        return def;
    }

    /** 离屏渲染：不依赖真实窗口/显示环境，用于回归检查界面 */
    private static void screenshot(List<String> a) {
        String out = arg(a, "--screenshot", "preview.png");
        String themeName = arg(a, "--theme", "light");
        String city = arg(a, "--city", "杭州");
        int width = Integer.parseInt(arg(a, "--width", "980"));
        int height = Integer.parseInt(arg(a, "--height", "1560"));
        String path = arg(a, "--cache", null);

        Theme theme = "dark".equals(themeName) ? Theme.dark() : Theme.light();
        WeatherData data = null;
        if (path != null) {
            try {
                data = WeatherClient.parseForecast(new String(java.nio.file.Files.readAllBytes(
                        new File(path).toPath()), java.nio.charset.StandardCharsets.UTF_8), city);
            } catch (Exception ignored) {
            }
        }
        if (data == null) {
            double lat = Double.parseDouble(arg(a, "--lat", "30.2741"));
            double lon = Double.parseDouble(arg(a, "--lon", "120.1595"));
            try {
                System.out.println("fetching " + city + " ...");
                data = WeatherClient.fetch(lat, lon, city);
                System.out.println("ok: " + WeatherCodes.desc(data.currentCode) + " " + data.currentTemp + "°");
            } catch (Exception e) {
                System.out.println("fetch failed (" + e.getMessage() + "), using sample data");
                data = sample(city);
            }
        }

        WeatherPanel panel = new WeatherPanel(theme);
        panel.setSize(width, height);
        panel.setData(data, false);
        int realHeight = Math.max(400, panel.preferredHeightFor(width));
        panel.setSize(width, realHeight);
        height = realHeight;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(theme.bg);
        g.fillRect(0, 0, width, height);
        panel.paint(g);
        g.dispose();

        try {
            File f = new File(out);
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            ImageIO.write(img, "png", f);
            System.out.println("screenshot: " + f.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("write failed: " + e);
        }
    }

    /** 离线示例数据，仅在无网络时用于验证排版 */
    static WeatherData sample(String city) {
        WeatherData d = new WeatherData();
        d.city = city;
        d.currentTemp = 22;
        d.currentCode = 2;
        d.feelsLike = 24;
        d.humidity = 63;
        d.windSpeed = 12;
        d.windDir = 135;
        d.pressure = 1013;
        d.cloudCover = 55;
        d.precip = 0.2;
        d.isDay = true;
        d.hourlyTime = new String[12];
        d.hourlyTemp = new int[]{22, 23, 24, 24, 23, 22, 21, 20, 19, 19, 18, 18};
        d.hourlyCode = new int[]{2, 2, 3, 61, 61, 80, 2, 1, 1, 0, 0, 1};
        d.hourlyHumidity = new int[]{63, 60, 58, 66, 70, 72, 68, 64, 62, 60, 59, 58};
        d.hourlyPrecipProb = new int[]{10, 10, 20, 55, 65, 45, 20, 10, 5, 5, 0, 0};
        d.hourlyPrecip = new double[]{0, 0, 0.1, 0.8, 1.2, 0.4, 0.1, 0, 0, 0, 0, 0};
        d.hourlyWind = new double[]{12, 13, 14, 16, 15, 12, 10, 9, 8, 7, 6, 6};
        String[] days = {"今天", "周二", "周三", "周四", "周五", "周六", "周日"};
        d.dailyDate = days;
        d.dailyDay = days;
        d.dailySunrise = new String[]{"06:31", "06:32", "06:33", "06:34", "06:35", "06:36", "06:37"};
        d.dailySunset = new String[]{"17:02", "17:01", "17:00", "16:59", "16:58", "16:57", "16:56"};
        d.dailyMin = new int[]{18, 17, 19, 20, 16, 15, 18};
        d.dailyMax = new int[]{24, 25, 26, 23, 21, 20, 24};
        d.dailyCode = new int[]{2, 3, 61, 80, 1, 0, 2};
        d.dailyPrecipProb = new int[]{20, 40, 80, 60, 10, 0, 20};
        d.dailyUv = new int[]{5, 6, 3, 4, 7, 8, 5};
        d.dailyPrecip = new double[]{0.2, 1.5, 8.4, 4.2, 0, 0, 0.1};
        d.dailyWindMax = new double[]{18, 20, 24, 22, 15, 13, 17};
        d.hasAir = true;
        d.aqi = 34;
        d.usAqi = 42;
        d.pm25 = 12;
        d.pm10 = 24;
        d.ozone = 62;
        d.no2 = 11;
        d.so2 = 3;
        d.co = 180;
        d.updatedAt = System.currentTimeMillis();
        return d;
    }

    static {
        Color.WHITE.getClass();
    }
}
