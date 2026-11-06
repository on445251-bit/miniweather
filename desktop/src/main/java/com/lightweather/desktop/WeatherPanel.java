package com.lightweather.desktop;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 自绘天气面板：一块 Canvas 里排版所有卡片，滚轮由 JScrollPane 处理。
 * 所有尺寸按内容宽度动态计算，因此窗口拉伸时布局自动重排。
 */
public class WeatherPanel extends JPanel implements Scrollable {

    private static final int PAD = 18;
    private static final int GAP = 14;
    private static final int RADIUS = 16;

    private final Prefs prefs = Prefs.get();
    private Theme theme;
    private WeatherData data;
    private boolean loading;
    private String error;
    private boolean fromCache;

    private final List<Hit> hits = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();
    private Rectangle hoverRect;

    interface Action {
        void run();
    }

    private static final class Hit {
        final Rectangle rect;
        final Action action;

        Hit(Rectangle rect, Action action) {
            this.rect = rect;
            this.action = action;
        }
    }

    private static final class Card {
        final int type;
        final int x, y, w, h;

        Card(int type, int x, int y, int w, int h) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private static final int HERO = 1, ALERT = 2, METRICS = 3, AIR = 4, HOURLY = 5,
            CURVE = 6, DAILY = 7, SUN = 8, LIFE = 9, FOOTER = 10;

    public WeatherPanel(Theme theme) {
        this.theme = theme;
        setOpaque(true);
        setBackground(theme.bg);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                for (int i = hits.size() - 1; i >= 0; i--) {
                    Hit hit = hits.get(i);
                    if (hit.rect.contains(e.getPoint())) {
                        hit.action.run();
                        return;
                    }
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                Rectangle found = null;
                for (Hit hit : hits) {
                    if (hit.rect.contains(e.getPoint())) {
                        found = hit.rect;
                        break;
                    }
                }
                boolean changed = found == null ? hoverRect != null : !found.equals(hoverRect);
                hoverRect = found;
                if (changed) {
                    setCursor(found != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                            : Cursor.getDefaultCursor());
                    repaint();
                }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    // ---------------- 对外状态 ----------------

    public void setTheme(Theme t) {
        this.theme = t;
        setBackground(t.bg);
        revalidate();
        repaint();
    }

    public Theme theme() {
        return theme;
    }

    public WeatherData data() {
        return data;
    }

    public void setData(WeatherData d, boolean cached) {
        this.data = d;
        this.fromCache = cached;
        this.error = null;
        this.loading = false;
        revalidate();
        repaint();
    }

    public void setLoading(boolean v) {
        this.loading = v;
        revalidate();
        repaint();
    }

    public void setError(String msg) {
        this.error = msg;
        this.loading = false;
        revalidate();
        repaint();
    }

    // ---------------- 尺寸与滚动 ----------------

    @Override
    public Dimension getPreferredSize() {
        int w = getParent() == null ? 860 : Math.max(520, getParent().getWidth());
        return new Dimension(w, heightFor(w));
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 24;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(60, visibleRect.height - 40);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    private int contentWidth() {
        int w = getWidth();
        if (w <= 0) w = 860;
        return Math.max(420, w - PAD * 2);
    }

    /** 指定宽度下的整体内容高度（供离屏渲染/自检使用） */
    public int preferredHeightFor(int width) {
        return layoutCards(Math.max(420, width - PAD * 2));
    }

    private int heightFor(int panelWidth) {
        layoutCards(Math.max(420, panelWidth - PAD * 2));
        if (cards.isEmpty()) return 200;
        Card last = cards.get(cards.size() - 1);
        return last.y + last.h + PAD;
    }

    /** 计算所有卡片位置，返回总高度 */
    private int layoutCards(int cw) {
        cards.clear();
        if (data == null) {
            return 260;
        }
        int y = PAD;
        cards.add(new Card(HERO, PAD, y, cw, 196));
        y += 196 + GAP;

        List<WeatherAlert.Alert> alerts = data == null ? new ArrayList<>() : WeatherAlert.compute(data);
        if (!alerts.isEmpty()) {
            cards.add(new Card(ALERT, PAD, y, cw, 78));
            y += 78 + GAP;
        }
        if (data != null) {
            cards.add(new Card(METRICS, PAD, y, cw, 178));
            y += 178 + GAP;
            if (data.hasAir) {
                cards.add(new Card(AIR, PAD, y, cw, 118));
                y += 118 + GAP;
            }
            if (data.hasHourly()) {
                cards.add(new Card(HOURLY, PAD, y, cw, 176));
                y += 176 + GAP;
            }
            if (data.hasDaily()) {
                cards.add(new Card(CURVE, PAD, y, cw, 210));
                y += 210 + GAP;
                cards.add(new Card(DAILY, PAD, y, cw, 44 + data.dailyDate.length * 34));
                y += 44 + data.dailyDate.length * 34 + GAP;
                cards.add(new Card(SUN, PAD, y, cw, 96));
                y += 96 + GAP;
            }
            cards.add(new Card(LIFE, PAD, y, cw, 186));
            y += 186 + GAP;
        }
        cards.add(new Card(FOOTER, PAD, y, cw, 46));
        y += 46;
        return y + PAD;
    }

    // ---------------- 绘制 ----------------

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int cw = contentWidth();
        layoutCards(cw);
        hits.clear();

        for (Card c : cards) {
            switch (c.type) {
                case HERO:
                    paintHero(g, c);
                    break;
                case ALERT:
                    paintAlert(g, c);
                    break;
                case METRICS:
                    paintMetrics(g, c);
                    break;
                case AIR:
                    paintAir(g, c);
                    break;
                case HOURLY:
                    paintHourly(g, c);
                    break;
                case CURVE:
                    paintCurve(g, c);
                    break;
                case DAILY:
                    paintDaily(g, c);
                    break;
                case SUN:
                    paintSun(g, c);
                    break;
                case LIFE:
                    paintLife(g, c);
                    break;
                case FOOTER:
                    paintFooter(g, c);
                    break;
                default:
                    break;
            }
        }

        if (data == null) {
            paintEmpty(g, cw);
        }
        g.dispose();
    }

    private void paintEmpty(Graphics2D g, int cw) {
        // 只在实际可见高度内居中，避免窗口较矮时按钮被裁掉一半
        int visible = Math.max(200, Math.min(getHeight(), Math.max(260, preferredHeightFor(getWidth() + 2 * PAD))));
        int y = Math.max(90, visible / 2 - 20);
        String msg = loading ? "正在获取天气数据…" : error == null ? "输入城市名称开始查询" : error;
        g.setFont(theme.h2);
        g.setColor(theme.sub);
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(msg);
        int tx = PAD + Math.max(0, (cw - tw) / 2);
        g.drawString(msg, tx, y);
        if (!loading && error != null) {
            int bw = 108;
            int bh = 34;
            int bx = PAD + Math.max(0, (cw - bw) / 2);
            int by = Math.min(y + 20, visible - bh - 16);
            g.setColor(theme.accent);
            g.fill(new RoundRectangle2D.Double(bx, by, bw, bh, 10, 10));
            g.setColor(Color.WHITE);
            g.setFont(theme.body);
            fm = g.getFontMetrics();
            g.drawString("重新加载", bx + (bw - fm.stringWidth("重新加载")) / 2, by + 22);
            hits.add(new Hit(new Rectangle(bx, by, bw, bh), this::reloadRequested));
        }
    }

    private Action reloadAction = () -> {
    };

    public void onReload(Action a) {
        this.reloadAction = a;
    }

    private void reloadRequested() {
        if (reloadAction != null) reloadAction.run();
    }

    private void paintHero(Graphics2D g, Card c) {
        Color a = theme.dark ? new Color(0x1E3C5A) : new Color(0x1E88E5);
        Color b = theme.dark ? new Color(0x2B5F86) : new Color(0x64B5F6);
        g.setPaint(new GradientPaint(c.x, c.y, a, c.x + c.w, c.y + c.h, b));
        g.fill(round(c));

        if (data == null) {
            g.setColor(new Color(255, 255, 255, 150));
            g.setFont(theme.h1);
            g.drawString("轻天气", c.x + 24, c.y + 44);
            g.setFont(theme.body);
            g.drawString("搜索城市或从左侧选择", c.x + 24, c.y + 68);
            return;
        }

        String city = data.city == null || data.city.isEmpty() ? "当前位置" : data.city;
        g.setFont(theme.h1);
        g.setColor(Color.WHITE);
        g.drawString(city, c.x + 24, c.y + 40);

        String upd = "更新于 " + timeText(data.updatedAt) + (fromCache ? " · 缓存" : "");
        g.setFont(theme.small);
        g.setColor(new Color(255, 255, 255, 185));
        g.drawString(upd, c.x + 24, c.y + 62);

        g.setFont(theme.tempHuge);
        g.setColor(Color.WHITE);
        String temp = prefs.fmtTemp(data.currentTemp);
        g.drawString(temp, c.x + 22, c.y + 138);

        g.setFont(theme.h2);
        g.setColor(Color.WHITE);
        g.drawString(WeatherCodes.desc(data.currentCode), c.x + 26, c.y + 168);

        String feels = "体感 " + prefs.fmtTemp(data.feelsLike)
                + " · " + Prefs.windDirText(data.windDir)
                + " " + prefs.fmtWind(data.windSpeed) + " " + prefs.windUnitLabel();
        g.setFont(theme.small);
        g.setColor(new Color(255, 255, 255, 190));
        g.drawString(feels, c.x + 26, c.y + 188);

        int icon = 104;
        Icons.paint(g, WeatherCodes.kind(data.currentCode, !data.isDay),
                c.x + c.w - icon - 30, c.y + 46, icon);

        int bw = 74;
        int bh = 26;
        int bx = c.x + c.w - bw - 24;
        int by = c.y + 16;
        g.setColor(new Color(255, 255, 255, 40));
        g.fill(new RoundRectangle2D.Double(bx, by, bw, bh, 13, 13));
        g.setColor(Color.WHITE);
        g.setFont(theme.small);
        String tip = fromCache ? "点击刷新" : "已同步";
        g.drawString(tip, bx + (bw - g.getFontMetrics().stringWidth(tip)) / 2, by + 17);
    }

    private void paintAlert(Graphics2D g, Card c) {
        List<WeatherAlert.Alert> list = WeatherAlert.compute(data);
        if (list.isEmpty()) return;
        WeatherAlert.Alert top = WeatherAlert.top(list);
        Color col = WeatherAlert.color(top.level);
        Color bg = theme.dark ? theme.alpha(col, 66) : WeatherAlert.soft(top.level);

        g.setColor(bg);
        g.fill(round(c));
        g.setColor(col);
        g.fill(new RoundRectangle2D.Double(c.x, c.y, 5, c.h, 3, 3));

        int bx = c.x + 18;
        int by = c.y + 16;
        String badge = top.levelText();
        g.setFont(theme.small);
        int bw = g.getFontMetrics().stringWidth(badge) + 20;
        g.setColor(col);
        g.fill(new RoundRectangle2D.Double(bx, by, bw, 22, 11, 11));
        g.setColor(Color.WHITE);
        g.drawString(badge, bx + 10, by + 16);

        g.setColor(theme.text);
        g.setFont(theme.bold);
        String title = top.title;
        if (list.size() > 1) title += "（共 " + list.size() + " 条提醒）";
        g.drawString(title, bx + bw + 12, by + 16);

        g.setColor(theme.dark ? new Color(theme.text.getRed(), theme.text.getGreen(), theme.text.getBlue(), 225)
                : theme.sub);
        g.setFont(theme.small);
        g.drawString(clip(g, top.text, c.w - 40), bx, by + 46);
    }

    private void paintMetrics(Graphics2D g, Card c) {
        card(g, c);
        String[][] cells = {
                {"体感温度", prefs.fmtTempUnit(data.feelsLike)},
                {"相对湿度", data.humidity + "%"},
                {"风向风速", Prefs.windDirText(data.windDir) + " " + prefs.fmtWind(data.windSpeed)
                        + " " + prefs.windUnitLabel()},
                {"气压", prefs.fmtPressure(data.pressure) + " " + prefs.pressUnitLabel()},
                {"云量", data.cloudCover + "%"},
                {"降水量", String.format("%.1f mm", data.precip)},
        };
        int cols = 3;
        int rows = 2;
        int cw = (c.w - 32) / cols;
        int ch = (c.h - 28) / rows;
        for (int i = 0; i < cells.length; i++) {
            int cx = c.x + 16 + (i % cols) * cw;
            int cy = c.y + 14 + (i / cols) * ch;
            g.setFont(theme.small);
            g.setColor(theme.sub);
            g.drawString(cells[i][0], cx, cy + 16);
            g.setFont(theme.tempMid);
            g.setColor(theme.text);
            g.drawString(clip(g, cells[i][1], cw - 12), cx, cy + 44);
        }
    }

    private void paintAir(Graphics2D g, Card c) {
        card(g, c);
        Color col = AirQuality.color(data.aqi);
        int bx = c.x + 22;
        int by = c.y + 56;
        g.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 34));
        g.setColor(col);
        String aqi = String.valueOf(data.aqi);
        g.drawString(aqi, bx, by);
        int aw = g.getFontMetrics().stringWidth(aqi);

        g.setFont(theme.h2);
        g.setColor(col);
        g.drawString(AirQuality.level(data.aqi), bx + aw + 14, by - 14);

        g.setFont(theme.small);
        g.setColor(theme.sub);
        g.drawString("空气质量指数（欧洲 AQI）", bx + aw + 14, by + 2);

        g.setFont(theme.small);
        g.setColor(theme.text);
        g.drawString(clip(g, AirQuality.advice(data.aqi), c.w - 300), c.x + 260, c.y + 40);

        String pol = String.format("PM2.5 %.0f · PM10 %.0f · O3 %.0f · NO2 %.0f · SO2 %.0f μg/m3",
                data.pm25, data.pm10, data.ozone, data.no2, data.so2);
        g.setColor(theme.sub);
        g.drawString(clip(g, pol, c.w - 300), c.x + 260, c.y + 64);
        g.drawString("首要污染物：" + AirQuality.dominant(data), c.x + 260, c.y + 88);
    }

    private void paintHourly(Graphics2D g, Card c) {
        card(g, c);
        g.setFont(theme.bold);
        g.setColor(theme.text);
        g.drawString("逐小时预报", c.x + 18, c.y + 26);

        int n = data.hourlyTime.length;
        int inner = c.w - 28;
        int cell = Math.max(56, inner / Math.max(1, Math.min(n, 12)));
        int count = Math.min(n, Math.max(4, inner / cell));

        int top = c.y + 44;
        for (int i = 0; i < count; i++) {
            int cx = c.x + 14 + i * cell;
            Rectangle cellRect = new Rectangle(cx, top - 4, cell - 2, 112);
            if (cellRect.equals(hoverRect)) {
                g.setColor(theme.chip);
                g.fill(new RoundRectangle2D.Double(cx + 1, top - 4, cell - 2, 112, 12, 12));
            }
            FontMetrics fm;
            g.setFont(theme.small);
            g.setColor(theme.sub);
            fm = g.getFontMetrics();
            String t = isCurrentHour(data.hourlyTime[i]) ? "现在" : hourText(data.hourlyTime[i]);
            g.drawString(t, cx + (cell - fm.stringWidth(t)) / 2, top + 12);

            Icons.paint(g, WeatherCodes.kind(data.hourlyCode[i], WeatherCodes.isNightHour(data.hourlyTime[i])),
                    cx + (cell - 34) / 2, top + 20, 34);

            g.setFont(theme.body);
            g.setColor(theme.text);
            fm = g.getFontMetrics();
            String tp = prefs.fmtTemp(data.hourlyTemp[i]);
            g.drawString(tp, cx + (cell - fm.stringWidth(tp)) / 2, top + 80);

            int pop = data.hourlyPrecipProb[i];
            // 降水概率小柱：一眼看出雨什么时候下（0% 也画空轨道，保持基线整齐）
            int barW = 30;
            int barX = cx + (cell - barW) / 2;
            int barY = top + 88;
            g.setColor(theme.dark ? new Color(255, 255, 255, 32) : new Color(0, 0, 0, 18));
            g.fill(new RoundRectangle2D.Double(barX, barY, barW, 4, 4, 4));
            int fillW = Math.round(barW * Math.min(100, Math.max(0, pop)) / 100f);
            if (fillW > 0) {
                g.setColor(pop >= 60 ? new Color(0x1E88E5) : new Color(0x64B5F6));
                g.fill(new RoundRectangle2D.Double(barX, barY, fillW, 4, 4, 4));
            }
            if (pop >= 20) {
                g.setFont(theme.small);
                g.setColor(new Color(0x42A5F5));
                fm = g.getFontMetrics();
                String ps = pop + "%";
                g.drawString(ps, cx + (cell - fm.stringWidth(ps)) / 2, top + 106);
            }

            final int idx = i;
            hits.add(new Hit(cellRect, () -> showHourDetail(idx)));
        }
    }

    private void showHourDetail(int i) {
        if (data == null || i >= data.hourlyTime.length) return;
        String time = WeatherClient.hhmm(data.hourlyTime[i]);
        String body = "<html><body style='width:220px;font-family:Microsoft YaHei UI'>"
                + "<h3 style='margin:0 0 6px 0'>" + time + " " + WeatherCodes.desc(data.hourlyCode[i]) + "</h3>"
                + "温度：" + prefs.fmtTempUnit(data.hourlyTemp[i]) + "<br>"
                + "降水概率：" + data.hourlyPrecipProb[i] + "%<br>"
                + String.format("降水量：%.1f mm<br>", data.hourlyPrecip[i])
                + "湿度：" + data.hourlyHumidity[i] + "%<br>"
                + "风速：" + prefs.fmtWind(data.hourlyWind[i]) + " " + prefs.windUnitLabel()
                + "</body></html>";
        JOptionPane.showMessageDialog(this, body, "逐小时详情", JOptionPane.PLAIN_MESSAGE);
    }

    private void paintCurve(Graphics2D g, Card c) {
        card(g, c);
        g.setFont(theme.bold);
        g.setColor(theme.text);
        g.drawString("7 天温度趋势", c.x + 18, c.y + 26);

        int n = data.dailyDate.length;
        if (n == 0) return;
        int left = c.x + 26;
        int right = c.x + c.w - 26;
        int top = c.y + 52;
        int bottom = c.y + c.h - 34;
        int plotH = bottom - top;

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            min = Math.min(min, Math.min(data.dailyMin[i], data.dailyMax[i]));
            max = Math.max(max, Math.max(data.dailyMin[i], data.dailyMax[i]));
        }
        if (max - min < 6) {
            max += 3;
            min -= 3;
        }
        int span = Math.max(1, max - min);

        double step = n == 1 ? 0 : (double) (right - left) / (n - 1);
        int[] hx = new int[n];
        int[] hy = new int[n];
        int[] lx = new int[n];
        int[] ly = new int[n];
        for (int i = 0; i < n; i++) {
            hx[i] = (int) Math.round(left + step * i);
            lx[i] = hx[i];
            hy[i] = bottom - (int) Math.round((data.dailyMax[i] - min) * (double) plotH / span);
            ly[i] = bottom - (int) Math.round((data.dailyMin[i] - min) * (double) plotH / span);
        }

        Path2D band = new Path2D.Double();
        band.moveTo(hx[0], hy[0]);
        for (int i = 1; i < n; i++) band.lineTo(hx[i], hy[i]);
        for (int i = n - 1; i >= 0; i--) band.lineTo(lx[i], ly[i]);
        band.closePath();
        Color a1 = new Color(theme.accent.getRed(), theme.accent.getGreen(), theme.accent.getBlue(), theme.dark ? 70 : 50);
        Color a2 = new Color(theme.accent.getRed(), theme.accent.getGreen(), theme.accent.getBlue(), 8);
        g.setPaint(new GradientPaint(0, top, a1, 0, bottom, a2));
        g.fill(band);

        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(theme.accent);
        drawPoly(g, hx, hy);
        g.setColor(theme.dark ? new Color(0x78909C) : new Color(0x90A4AE));
        drawPoly(g, lx, ly);

        for (int i = 0; i < n; i++) {
            g.setColor(theme.accent);
            g.fill(new java.awt.geom.Ellipse2D.Double(hx[i] - 3.2, hy[i] - 3.2, 6.4, 6.4));
            g.setColor(theme.dark ? new Color(0x78909C) : new Color(0x90A4AE));
            g.fill(new java.awt.geom.Ellipse2D.Double(lx[i] - 3.2, ly[i] - 3.2, 6.4, 6.4));

            g.setFont(theme.small);
            FontMetrics fm = g.getFontMetrics();
            String hi = prefs.fmtTemp(data.dailyMax[i]);
            String lo = prefs.fmtTemp(data.dailyMin[i]);
            g.setColor(theme.text);
            g.drawString(hi, clamp(hx[i] - fm.stringWidth(hi) / 2, c.x + 14, c.x + c.w - 14 - fm.stringWidth(hi)),
                    hy[i] - 9);
            g.setColor(theme.sub);
            g.drawString(lo, clamp(lx[i] - fm.stringWidth(lo) / 2, c.x + 14, c.x + c.w - 14 - fm.stringWidth(lo)),
                    ly[i] + 17);

            String day = data.dailyDay[i];
            g.setColor(theme.sub);
            g.drawString(day, clamp(hx[i] - fm.stringWidth(day) / 2, c.x + 14, c.x + c.w - 14 - fm.stringWidth(day)),
                    bottom + 28);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void drawPoly(Graphics2D g, int[] xs, int[] ys) {
        for (int i = 1; i < xs.length; i++) {
            g.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i]);
        }
    }

    private void paintDaily(Graphics2D g, Card c) {
        card(g, c);
        int n = data.dailyDate.length;
        int rowH = 34;
        int top = c.y + 44;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            min = Math.min(min, data.dailyMin[i]);
            max = Math.max(max, data.dailyMax[i]);
        }
        int span = Math.max(1, max - min);

        int labelX = c.x + 18;
        int iconX = c.x + 104;
        int popX = c.x + 142;
        int minRight = c.x + 246;
        int barX = c.x + 254;
        int barW = Math.max(90, c.w - 254 - 100);
        int maxRight = c.x + c.w - 18;

        g.setFont(theme.small);
        g.setColor(theme.sub);
        g.drawString("日期", labelX, c.y + 28);
        g.drawString("降水概率", popX, c.y + 28);

        for (int i = 0; i < n; i++) {
            int y = top + i * rowH;
            g.setFont(theme.body);
            g.setColor(theme.text);
            g.drawString(data.dailyDay[i], labelX, y + 16);

            Icons.paint(g, WeatherCodes.kind(data.dailyCode[i], false), iconX, y + 1, 26);

            g.setFont(theme.small);
            FontMetrics fm = g.getFontMetrics();
            int pop = data.dailyPrecipProb[i];
            if (pop >= 20) {
                g.setColor(new Color(0x42A5F5));
                String ps = pop + "%";
                g.drawString(ps, popX + 44 - fm.stringWidth(ps), y + 16);
            }

            g.setColor(theme.sub);
            String lo = prefs.fmtTemp(data.dailyMin[i]);
            g.drawString(lo, minRight - fm.stringWidth(lo), y + 16);

            int trackY = y + 12;
            g.setColor(theme.dark ? new Color(0x2A323D) : new Color(0xE8EEF6));
            g.fill(new RoundRectangle2D.Double(barX, trackY, barW, 6, 3, 3));
            double from = (data.dailyMin[i] - min) / (double) span;
            double to = (data.dailyMax[i] - min) / (double) span;
            if (to - from < 0.06) to = from + 0.06;
            int bx = barX + (int) Math.round(from * barW);
            int bw = Math.max(8, (int) Math.round((to - from) * barW));
            g.setPaint(new GradientPaint(bx, 0, new Color(0x64B5F6), bx + bw, 0, new Color(0xFF8A65)));
            g.fill(new RoundRectangle2D.Double(bx, trackY, bw, 6, 3, 3));

            g.setFont(theme.body);
            g.setColor(theme.text);
            fm = g.getFontMetrics();
            String hi = prefs.fmtTemp(data.dailyMax[i]);
            g.drawString(hi, maxRight - fm.stringWidth(hi), y + 16);
        }
    }

    private void paintSun(Graphics2D g, Card c) {
        card(g, c);
        String rise = data.dailySunrise.length > 0 ? data.dailySunrise[0] : "--:--";
        String set = data.dailySunset.length > 0 ? data.dailySunset[0] : "--:--";
        String[][] cols = {
                {"日出", rise},
                {"日落", set},
                {"昼长", daylight(rise, set)},
        };
        int cw = c.w / 3;
        for (int i = 0; i < cols.length; i++) {
            int cx = c.x + 22 + i * cw;
            g.setFont(theme.small);
            g.setColor(theme.sub);
            g.drawString(cols[i][0], cx, c.y + 32);
            g.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 20));
            g.setColor(theme.text);
            g.drawString(cols[i][1], cx, c.y + 66);
        }
        g.setColor(theme.accent);
        g.fill(new RoundRectangle2D.Double(c.x + c.w - 64, c.y + 18, 44, 44, 22, 22));
        Icons.paint(g, WeatherCodes.Kind.SUN, c.x + c.w - 61, c.y + 21, 38);
    }

    private void paintLife(Graphics2D g, Card c) {
        card(g, c);
        g.setFont(theme.bold);
        g.setColor(theme.text);
        g.drawString("生活指数", c.x + 18, c.y + 26);

        List<LifeIndex.Item> items = LifeIndex.compute(data);
        int cols = 3;
        int rows = 2;
        int cw = (c.w - 32) / cols;
        int chh = (c.h - 50) / rows;
        for (int i = 0; i < items.size() && i < 6; i++) {
            LifeIndex.Item it = items.get(i);
            int cx = c.x + 16 + (i % cols) * cw;
            int cy = c.y + 44 + (i / cols) * chh;
            g.setColor(it.color);
            g.fill(new java.awt.geom.Ellipse2D.Double(cx, cy + 2, 7, 7));
            g.setFont(theme.small);
            g.setColor(theme.sub);
            g.drawString(it.name, cx + 13, cy + 10);
            g.setFont(theme.bold);
            g.setColor(it.color);
            g.drawString(it.value, cx + 13, cy + 29);
            g.setFont(theme.small);
            g.setColor(theme.sub);
            g.drawString(clip(g, it.detail, cw - 24), cx, cy + 48);
        }
    }

    private void paintFooter(Graphics2D g, Card c) {
        g.setFont(theme.small);
        g.setColor(theme.sub);
        String src = data == null || data.source == null || data.source.isEmpty() ? "Open-Meteo" : data.source;
        String left = "数据来源 " + src + " · 无需 API Key";
        g.drawString(left, c.x + 4, c.y + 24);
        if (data != null) {
            String right = "轻天气桌面版 v3.1 · " + timeText(data.updatedAt);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(right, c.x + c.w - fm.stringWidth(right) - 4, c.y + 24);
        }
    }

    // ---------------- 绘制小工具 ----------------

    private void card(Graphics2D g, Card c) {
        g.setColor(theme.card);
        g.fill(round(c));
        g.setColor(theme.border);
        g.setStroke(new BasicStroke(1f));
        g.draw(round(c));
    }

    private RoundRectangle2D round(Card c) {
        return new RoundRectangle2D.Double(c.x, c.y, c.w, c.h, theme.radius, theme.radius);
    }

    private String clip(Graphics2D g, String text, int maxWidth) {
        if (text == null) return "";
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth) return text;
        String ell = "…";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (fm.stringWidth(sb.toString() + text.charAt(i) + ell) > maxWidth) break;
            sb.append(text.charAt(i));
        }
        return sb + ell;
    }

    public static String timeText(long millis) {
        if (millis <= 0) return "--:--";
        LocalDateTime t = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis),
                java.time.ZoneId.systemDefault());
        return t.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private static String hourText(String iso) {
        String t = WeatherClient.hhmm(iso);
        return t.length() >= 5 ? t.substring(0, 5) : t;
    }

    private static boolean isCurrentHour(String iso) {
        if (iso == null || iso.length() < 13) return false;
        try {
            int h = Integer.parseInt(iso.substring(11, 13));
            int d = Integer.parseInt(iso.substring(8, 10));
            LocalDateTime now = LocalDateTime.now();
            return h == now.getHour() && d == now.getDayOfMonth();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static String daylight(String rise, String set) {
        int a = minutes(rise);
        int b = minutes(set);
        if (a < 0 || b < 0) return "--";
        int total = b - a;
        if (total < 0) total += 24 * 60;
        return (total / 60) + " 小时 " + (total % 60) + " 分";
    }

    private static int minutes(String hhmm) {
        if (hhmm == null || hhmm.length() < 5) return -1;
        try {
            return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static {
        // 保证 Swing 组件在无显示环境下也能构造（截图模式使用）
        SwingConstants.class.getName();
    }
}
