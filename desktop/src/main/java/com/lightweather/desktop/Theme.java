package com.lightweather.desktop;

import java.awt.Color;
import java.awt.Font;

/** 明暗两套配色 + 字体，全局唯一样式来源 */
public final class Theme {

    public final boolean dark;
    public final Color bg;
    public final Color bg2;
    public final Color card;
    public final Color cardAlt;
    public final Color border;
    public final Color text;
    public final Color sub;
    public final Color accent;
    public final Color accentSoft;
    public final Color divider;
    public final Color chip;
    public final Color scrollThumb;

    public final Font h1 = new Font("Microsoft YaHei UI", Font.PLAIN, 15);
    public final Font h2 = new Font("Microsoft YaHei UI", Font.PLAIN, 13);
    public final Font body = new Font("Microsoft YaHei UI", Font.PLAIN, 12);
    public final Font small = new Font("Microsoft YaHei UI", Font.PLAIN, 11);
    public final Font tempHuge = new Font("Microsoft YaHei UI", Font.PLAIN, 64);
    public final Font tempBig = new Font("Microsoft YaHei UI", Font.PLAIN, 22);
    public final Font tempMid = new Font("Microsoft YaHei UI", Font.PLAIN, 16);
    public final Font bold = new Font("Microsoft YaHei UI", Font.BOLD, 12);

    private Theme(boolean dark) {
        this.dark = dark;
        if (dark) {
            bg = new Color(0x11151C);
            bg2 = new Color(0x1A2030);
            card = new Color(0x1B212B);
            cardAlt = new Color(0x232B38);
            border = new Color(0x2C3542);
            text = new Color(0xECEFF4);
            sub = new Color(0x93A0B4);
            accent = new Color(0x64B5F6);
            accentSoft = new Color(0x64B5F6);
            divider = new Color(0x2A323D);
            chip = new Color(0x25303F);
            scrollThumb = new Color(0x3A4553);
        } else {
            bg = new Color(0xF2F5FA);
            bg2 = new Color(0xE8EFF9);
            card = new Color(0xFFFFFF);
            cardAlt = new Color(0xF6F8FC);
            border = new Color(0xE1E7F0);
            text = new Color(0x1B2430);
            sub = new Color(0x6B7A8F);
            accent = new Color(0x1E88E5);
            accentSoft = new Color(0x1E88E5);
            divider = new Color(0xE8EDF5);
            chip = new Color(0xEDF3FB);
            scrollThumb = new Color(0xC7D2E2);
        }
    }

    public static Theme light() {
        return new Theme(false);
    }

    public static Theme dark() {
        return new Theme(true);
    }

    /** 依据系统外观偏好（Windows 深色模式）决定 */
    public static boolean systemPrefersDark() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            try {
                Process p = new ProcessBuilder("reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme").redirectErrorStream(true).start();
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                try (java.io.InputStream in = p.getInputStream()) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                }
                p.waitFor();
                String out = bos.toString("GBK");
                int idx = out.indexOf("REG_DWORD");
                if (idx >= 0) {
                    String hex = out.substring(idx + 9).trim();
                    if (hex.startsWith("0x")) {
                        return Integer.parseInt(hex.substring(2, 3), 16) == 0;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
