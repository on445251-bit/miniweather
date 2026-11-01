package com.lightweather.desktop;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** 纯 Java2D 绘制的天气图标，不依赖图片资源 */
public final class Icons {

    private Icons() {
    }

    private static final Color SUN = new Color(0xFFB300);
    private static final Color SUN_DEEP = new Color(0xFF8F00);
    private static final Color MOON = new Color(0xFFD54F);
    private static final Color CLOUD = new Color(0xB0BEC5);
    private static final Color CLOUD_DEEP = new Color(0x90A4AE);
    private static final Color RAIN = new Color(0x42A5F5);
    private static final Color SNOW = new Color(0x81D4FA);
    private static final Color STORM = new Color(0x78909C);
    private static final Color BOLT = new Color(0xFFCA28);

    public static void paint(Graphics2D g0, WeatherCodes.Kind kind, int x, int y, int size) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.translate(x, y);
        double s = size / 100.0;
        g.scale(s, s);
        switch (kind) {
            case SUN:
                sun(g, 50, 50, 24);
                break;
            case MOON:
                moon(g, 50, 48, 27);
                break;
            case PARTLY:
                sun(g, 38, 38, 17);
                cloud(g, 58, 62, 1.0);
                break;
            case PARTLY_NIGHT:
                moon(g, 38, 38, 19);
                cloud(g, 58, 62, 1.0);
                break;
            case CLOUD:
                cloud(g, 50, 54, 1.15);
                break;
            case FOG:
                cloud(g, 50, 42, 1.0);
                g.setColor(CLOUD_DEEP);
                g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < 3; i++) {
                    int yy = 66 + i * 11;
                    g.drawLine(24 + i * 4, yy, 76 - i * 4, yy);
                }
                break;
            case DRIZZLE:
                cloud(g, 50, 42, 1.0);
                drops(g, 3, 0.75);
                break;
            case RAIN:
                cloud(g, 50, 40, 1.05);
                drops(g, 3, 1.0);
                break;
            case SHOWERS:
                sun(g, 30, 32, 15);
                cloud(g, 56, 46, 1.0);
                drops(g, 3, 1.0);
                break;
            case SNOW:
                cloud(g, 50, 40, 1.0);
                flakes(g);
                break;
            case STORM:
                cloud(g, 50, 40, 1.05, STORM);
                bolt(g);
                break;
            default:
                cloud(g, 50, 54, 1.15);
                break;
        }
        g.dispose();
    }

    // ---------------- 元件 ----------------

    private static void sun(Graphics2D g, double cx, double cy, double r) {
        g.setColor(SUN);
        g.setStroke(new BasicStroke((float) (r * 0.28), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            double r1 = r * 1.5;
            double r2 = r * 1.95;
            g.draw(new java.awt.geom.Line2D.Double(
                    cx + Math.cos(a) * r1, cy + Math.sin(a) * r1,
                    cx + Math.cos(a) * r2, cy + Math.sin(a) * r2));
        }
        g.setColor(SUN_DEEP);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setColor(SUN);
        g.fill(new Ellipse2D.Double(cx - r * 0.93, cy - r * 0.93, r * 1.86, r * 1.86));
    }

    private static void moon(Graphics2D g, double cx, double cy, double r) {
        Area a = new Area(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        a.subtract(new Area(new Ellipse2D.Double(cx - r * 0.35, cy - r * 1.15, r * 1.9, r * 1.9)));
        g.setColor(MOON);
        g.fill(a);
    }

    private static void cloud(Graphics2D g, double cx, double cy, double scale) {
        cloud(g, cx, cy, scale, CLOUD);
    }

    private static void cloud(Graphics2D g, double cx, double cy, double scale, Color color) {
        double w = 74 * scale;
        double h = 34 * scale;
        Area area = new Area(new RoundRectangle2D.Double(cx - w / 2, cy - h / 2 + h * 0.28, w, h * 0.72, h * 0.72, h * 0.72));
        area.add(new Area(new Ellipse2D.Double(cx - w * 0.42, cy - h * 0.15, w * 0.44, h * 0.78)));
        area.add(new Area(new Ellipse2D.Double(cx - w * 0.14, cy - h * 0.62, w * 0.46, h * 0.95)));
        area.add(new Area(new Ellipse2D.Double(cx + w * 0.10, cy - h * 0.20, w * 0.36, h * 0.70)));
        g.setColor(color);
        g.fill(area);
    }

    private static void drops(Graphics2D g, int count, double scale) {
        g.setColor(RAIN);
        g.setStroke(new BasicStroke((float) (5.5 * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < count; i++) {
            double x = 34 + i * 16;
            double y = 72 + (i % 2) * 6;
            g.draw(new java.awt.geom.Line2D.Double(x, y, x - 5 * scale, y + 14 * scale));
        }
    }

    private static void flakes(Graphics2D g) {
        g.setColor(SNOW);
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++) {
            double cx = 34 + i * 16;
            double cy = 76 + (i % 2) * 8;
            for (int k = 0; k < 3; k++) {
                double a = Math.toRadians(k * 60);
                double dx = Math.cos(a) * 6;
                double dy = Math.sin(a) * 6;
                g.draw(new java.awt.geom.Line2D.Double(cx - dx, cy - dy, cx + dx, cy + dy));
            }
        }
    }

    private static void bolt(Graphics2D g) {
        GeneralPath p = new GeneralPath(Path2D.WIND_NON_ZERO);
        p.moveTo(52, 62);
        p.lineTo(40, 84);
        p.lineTo(50, 84);
        p.lineTo(44, 100);
        p.lineTo(62, 76);
        p.lineTo(51, 76);
        p.lineTo(58, 62);
        p.closePath();
        g.setColor(BOLT);
        g.fill(p);
    }
}
