package com.lightweather.desktop;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 主窗口：顶栏搜索 + 左侧城市列表 + 中间滚动天气面板 */
public class MainFrame extends JFrame {

    private final Prefs prefs = Prefs.get();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wx-io");
        t.setDaemon(true);
        return t;
    });

    private Theme theme;
    private WeatherPanel panel;
    private JScrollPane scroll;
    private Sidebar sidebar;
    private TopBar topBar;
    private SearchField search;
    private StatusBar statusBar;

    private WeatherData currentData;
    private boolean currentCached;
    private String cityName;
    private double lat;
    private double lon;
    private boolean busy;
    private Timer autoTimer;
    private TrayIcon trayIcon;

    public MainFrame() {
        super("轻天气 · LightWeather");
        theme = resolveTheme();
        setIconImage(appIcon());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(860, 620));
        setSize(1060, 820);
        setLocationRelativeTo(null);
        buildUi();
        seedCities();
        installKeys();
        installTray();
        restoreWindowBounds();
        restore();
        autoTimer = new Timer(10 * 60 * 1000, e -> reloadCurrent());
        autoTimer.stop();
        autoTimer.start();
    }

    // ---------------- 托盘 / 窗口状态 ----------------

    /** 系统托盘：最小化或点关闭时收进托盘，悬停显示当前天气 */
    private void installTray() {
        if (!SystemTray.isSupported()) return;
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem show = new MenuItem("显示主窗口");
            show.addActionListener(e -> restoreFromTray());
            MenuItem refresh = new MenuItem("立即刷新");
            refresh.addActionListener(e -> reloadCurrent());
            MenuItem quit = new MenuItem("退出");
            quit.addActionListener(e -> {
                SystemTray.getSystemTray().remove(trayIcon);
                System.exit(0);
            });
            menu.add(show);
            menu.add(refresh);
            menu.addSeparator();
            menu.add(quit);

            trayIcon = new TrayIcon(AppIcon.image(32), "轻天气", menu);
            trayIcon.setImageAutoSize(true);
            // 双击托盘图标 = 回到主窗口
            trayIcon.addActionListener(e -> restoreFromTray());
            SystemTray.getSystemTray().add(trayIcon);

            addWindowStateListener(e -> {
                if ((e.getNewState() & java.awt.Frame.ICONIFIED) != 0) hideToTray();
            });
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    prefs.setWindowBounds(getBounds());
                    prefs.save();
                    if (prefs.closeToTray() && trayIcon != null) {
                        e.getWindow().setVisible(false);
                        e.getWindow().dispose();
                        // 关闭按钮只收进托盘；真正退出走托盘右键菜单或 Ctrl+Q
                        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
                    }
                }
            });
        } catch (Exception ignored) {
            trayIcon = null;
        }
    }

    private void hideToTray() {
        if (trayIcon == null) return;
        setVisible(false);
    }

    private void restoreFromTray() {
        setVisible(true);
        setState(java.awt.Frame.NORMAL);
        toFront();
        requestFocus();
    }

    private void restoreWindowBounds() {
        Rectangle r = prefs.windowBounds();
        if (r == null) return;
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        // 只接受仍落在屏幕内的位置，避免换显示器后窗口跑到屏幕外
        boolean onScreen = r.x > -r.width && r.y > -r.height
                && r.x < screen.width - 80 && r.y < screen.height - 80;
        setSize(Math.max(860, r.width), Math.max(620, r.height));
        if (onScreen) setLocation(r.x, r.y);
        else setLocationRelativeTo(null);
    }

    private Theme resolveTheme() {
        int style = prefs.uiStyle();
        switch (prefs.themeMode()) {
            case 1:
                return Theme.light(style);
            case 2:
                return Theme.dark(style);
            default:
                return Theme.systemPrefersDark() ? Theme.dark(style) : Theme.light(style);
        }
    }

    private static BufferedImage appIcon() {
        return AppIcon.image(64);
    }

    // ---------------- UI 组装 ----------------

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(theme.bg);
        setContentPane(root);

        topBar = new TopBar();
        root.add(topBar, BorderLayout.NORTH);

        sidebar = new Sidebar();
        root.add(sidebar, BorderLayout.WEST);

        panel = new WeatherPanel(theme);
        panel.onReload(this::reloadCurrent);

        scroll = new JScrollPane(panel);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(theme.bg);
        ScrollUi.install(scroll, theme);
        root.add(scroll, BorderLayout.CENTER);

        statusBar = new StatusBar();
        root.add(statusBar, BorderLayout.SOUTH);
        root.revalidate();
        root.repaint();
    }

    private void seedCities() {
        if (prefs.cities().isEmpty()) {
            prefs.addCity("北京", 39.9042, 116.4074);
            prefs.addCity("上海", 31.2304, 121.4737);
            prefs.addCity("广州", 23.1291, 113.2644);
            prefs.addCity("深圳", 22.5431, 114.0579);
        }
    }

    private void installKeys() {
        getRootPane().registerKeyboardAction(e -> reloadCurrent(),
                KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> {
            search.requestFocusInWindow();
            search.selectAll();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> {
            if (search.getText().isEmpty()) {
                search.setText("");
            } else {
                search.setText("");
                panel.requestFocusInWindow();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    // ---------------- 数据加载 ----------------

    private void restore() {
        String cachedCity = prefs.lastCity();
        String json = prefs.loadCache();
        if (json != null && !json.isEmpty()) {
            try {
                WeatherData d = WeatherClient.parseForecast(json, cachedCity.isEmpty() ? "上次位置" : cachedCity);
                d.lat = prefs.lastLat();
                d.lon = prefs.lastLon();
                currentData = d;
                currentCached = true;
                panel.setData(d, true);
                updateTray();
            } catch (Exception ignored) {
                panel.setLoading(true);
            }
        } else {
            panel.setLoading(true);
        }

        if (!cachedCity.isEmpty() && prefs.lastLat() != 0) {
            cityName = cachedCity;
            lat = prefs.lastLat();
            lon = prefs.lastLon();
            sidebar.select(cityName);
            if (prefs.cacheStale(10 * 60 * 1000L)) {
                load(cityName, lat, lon);
            }
        } else {
            Prefs.City first = prefs.cities().isEmpty() ? null : prefs.cities().get(0);
            if (first != null) load(first.name, first.lat, first.lon);
        }
    }

    private void load(String city, double latitude, double longitude) {
        if (busy) return;
        busy = true;
        cityName = city;
        lat = latitude;
        lon = longitude;
        if (currentData == null) panel.setLoading(true);
        status("正在获取「" + city + "」的天气…");

        io.submit(() -> {
            try {
                WeatherData d = WeatherClient.fetchAll(prefs.provider(), latitude, longitude, city);
                prefs.saveCache(WeatherClient.rawForecast(latitude, longitude));
                prefs.setLast(city, latitude, longitude);
                SwingUtilities.invokeLater(() -> {
                    busy = false;
                    currentData = d;
                    currentCached = false;
                    panel.setData(d, false);
                    sidebar.select(city);
                    sidebar.repaint();
                    updateTray();
                    status("已更新 " + city + " · " + WeatherPanel.timeText(d.updatedAt)
                            + " · 数据源 " + (d.source == null || d.source.isEmpty() ? "Open-Meteo" : d.source));
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    busy = false;
                    String msg = ex.getMessage() == null ? "网络异常" : ex.getMessage();
                    if (currentData == null) {
                        panel.setError("获取天气失败：" + msg);
                    } else {
                        status("刷新失败：" + msg + "（显示缓存数据）");
                    }
                });
            }
        });
    }

    private void reloadCurrent() {
        if (lat == 0 && lon == 0) {
            Prefs.City c = prefs.cities().isEmpty() ? null : prefs.cities().get(0);
            if (c != null) load(c.name, c.lat, c.lon);
            return;
        }
        load(cityName == null ? "当前位置" : cityName, lat, lon);
    }

    private void doSearch() {
        String q = search.getText() == null ? "" : search.getText().trim();
        if (q.isEmpty()) {
            status("请输入城市名称，例如：杭州、Tokyo");
            return;
        }
        status("正在搜索「" + q + "」…");
        io.submit(() -> {
            try {
                List<WeatherClient.Geo> list = WeatherClient.geocode(q);
                SwingUtilities.invokeLater(() -> {
                    if (list.isEmpty()) {
                        status("没有找到「" + q + "」，换个关键词试试");
                        return;
                    }
                    if (list.size() == 1) {
                        WeatherClient.Geo g = list.get(0);
                        status("已匹配 " + g.label());
                        load(g.name, g.lat, g.lon);
                        return;
                    }
                    JPopupMenu popup = new JPopupMenu();
                    popup.setBorder(BorderFactory.createLineBorder(theme.border));
                    for (WeatherClient.Geo g : list) {
                        JMenuItem item = new JMenuItem(g.label());
                        item.setFont(theme.body);
                        item.addActionListener(e -> load(g.name, g.lat, g.lon));
                        popup.add(item);
                    }
                    popup.show(search, 0, search.getHeight() + 4);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> status("搜索失败：" + ex.getMessage()));
            }
        });
    }

    private void addCurrentToSidebar() {
        if (cityName == null || lat == 0) {
            status("请先查询一个城市");
            return;
        }
        prefs.addCity(cityName, lat, lon);
        sidebar.reload();
        status("已收藏「" + cityName + "」");
    }

    private void status(String text) {
        if (statusBar != null) statusBar.show(text);
    }

    /** 托盘提示文字：城市 + 温度 + 天气，鼠标悬停即可看到，不必打开窗口 */
    private void updateTray() {
        if (trayIcon == null) return;
        WeatherData d = currentData;
        if (d == null) {
            trayIcon.setToolTip("轻天气 · 正在获取天气…");
            return;
        }
        String city = (d.city == null || d.city.isEmpty()) ? cityName : d.city;
        trayIcon.setToolTip("轻天气 · " + city + " " + prefs.fmtTemp(d.currentTemp) + " "
                + WeatherCodes.desc(d.currentCode) + (currentCached ? "（缓存）" : ""));
    }

    // ---------------- 自检（截图模式使用） ----------------

    public WeatherPanel panel() {
        return panel;
    }

    public boolean hasData() {
        return panel != null && panel.data() != null;
    }

    // ---------------- 主题 / 菜单 ----------------

    private void applyTheme() {
        Theme fresh = resolveTheme();
        theme = fresh;
        WeatherData keep = currentData != null ? currentData : panel.data();
        boolean cached = currentCached;
        buildUi();
        if (keep != null) {
            panel.setData(keep, cached);
        }
        sidebar.reload();
        sidebar.select(cityName);
        getContentPane().setBackground(theme.bg);
        revalidate();
        repaint();
    }

    private void showMenu(JComponent anchor) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(BorderFactory.createLineBorder(theme.border));

        JMenuItem temp = new JMenuItem(prefs.tempF() ? "温度单位：华氏度 ℉" : "温度单位：摄氏度 ℃");
        temp.addActionListener(e -> {
            prefs.setTempF(!prefs.tempF());
            refreshValues();
        });
        menu.add(temp);

        JMenuItem wind = new JMenuItem("风速单位：" + prefs.windUnitLabel());
        wind.addActionListener(e -> {
            prefs.setWindUnit((prefs.windUnit() + 1) % 4);
            refreshValues();
        });
        menu.add(wind);

        JMenuItem press = new JMenuItem("气压单位：" + prefs.pressUnitLabel());
        press.addActionListener(e -> {
            prefs.setPressUnit((prefs.pressUnit() + 1) % 3);
            refreshValues();
        });
        menu.add(press);

        menu.add(new JSeparator());

        JMenuItem styleItem = new JMenuItem("界面风格：" + Theme.styleName(prefs.uiStyle()));
        styleItem.addActionListener(e -> {
            prefs.setUiStyle((prefs.uiStyle() + 1) % 3);
            prefs.save();
            applyTheme();
            status("界面风格已切换为「" + Theme.styleName(prefs.uiStyle()) + "」");
        });
        menu.add(styleItem);

        JMenuItem providerItem = new JMenuItem("数据源：" + WeatherClient.providerName(prefs.provider()));
        providerItem.addActionListener(e -> {
            prefs.setProvider((prefs.provider() + 1) % 4);
            prefs.save();
            status("数据源已切换为「" + WeatherClient.providerName(prefs.provider()) + "」，正在重新获取…");
            reloadCurrent();
        });
        menu.add(providerItem);

        JMenuItem themeItem = new JMenuItem("外观：" + themeModeLabel());
        themeItem.addActionListener(e -> {
            prefs.setThemeMode((prefs.themeMode() + 1) % 3);
            prefs.save();
            applyTheme();
            status("外观已切换为「" + themeModeLabel() + "」");
        });
        menu.add(themeItem);

        JMenuItem refresh = new JMenuItem("刷新数据  (F5)");
        refresh.addActionListener(e -> reloadCurrent());
        menu.add(refresh);

        JMenuItem about = new JMenuItem("关于轻天气");
        about.addActionListener(e -> about());
        menu.add(about);

        for (java.awt.Component c : menu.getComponents()) {
            c.setFont(theme.body);
            if (c instanceof JMenuItem) {
                c.setBackground(theme.card);
                c.setForeground(theme.text);
            }
        }
        menu.show(anchor, anchor.getWidth() - 160, anchor.getHeight() + 6);
    }

    private void refreshValues() {
        prefs.save();
        if (panel.data() != null) {
            panel.setData(panel.data(), currentCached);
        }
        sidebar.repaint();
        status("单位已更新");
    }

    private String themeModeLabel() {
        switch (prefs.themeMode()) {
            case 1:
                return "浅色";
            case 2:
                return "深色";
            default:
                return "跟随系统";
        }
    }

    private void about() {
        javax.swing.JOptionPane.showMessageDialog(this,
                "<html><body style='width:300px;font-family:Microsoft YaHei UI'>"
                        + "<h3 style='margin:0 0 8px 0'>轻天气 · LightWeather</h3>"
                        + "Windows 桌面版 v3.1（与 Android 版同一仓库）<br><br>"
                        + "数据来源：<b>Open-Meteo / wttr.in / MET Norway</b>（均可自动回退）<br>"
                        + "空气质量：Open-Meteo Air Quality API<br><br>"
                        + "纯 Java 17 + Swing 实现，零第三方依赖。<br>"
                        + "功能：实时天气 / 24 小时逐时 / 7 天趋势曲线 / 空气质量 / "
                        + "生活指数 / 天气提醒 / 离线缓存 / 明暗主题 / 三种界面风格 / 多城市。<br><br>"
                        + "快捷键：F5 刷新，Ctrl+F 搜索<br>"
                        + "缓存目录：" + Prefs.dataDir()
                        + "</body></html>",
                "关于", javax.swing.JOptionPane.PLAIN_MESSAGE);
    }

    // ---------------- 内部组件 ----------------

    /** 顶栏：品牌 + 搜索框 + 操作按钮 */
    private class TopBar extends JPanel {
        TopBar() {
            super(new BorderLayout());
            setOpaque(false);
            setPreferredSize(new Dimension(10, 66));
            setBorder(new javax.swing.border.EmptyBorder(13, 18, 13, 14));

            JPanel brand = new JPanel(new BorderLayout(10, 0));
            brand.setOpaque(false);
            JComponent logo = new JComponent() {
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(34, 34);
                }

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Icons.paint(g2, WeatherCodes.Kind.PARTLY, 1, 1, 32);
                    g2.dispose();
                }
            };
            brand.add(logo, BorderLayout.WEST);
            JLabel title = new JLabel("轻天气");
            title.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 15));
            title.setForeground(theme.text);
            brand.add(title, BorderLayout.CENTER);
            brand.setPreferredSize(new Dimension(150, 34));
            add(brand, BorderLayout.WEST);

            JPanel mid = new JPanel();
            mid.setOpaque(false);
            mid.setLayout(new BoxLayout(mid, BoxLayout.X_AXIS));
            mid.add(Box.createHorizontalGlue());
            search = new SearchField();
            search.setPreferredSize(new Dimension(330, 36));
            search.setMaximumSize(new Dimension(460, 36));
            search.addActionListener(e -> doSearch());
            mid.add(search);
            mid.add(Box.createHorizontalGlue());
            add(mid, BorderLayout.CENTER);

            JPanel right = new JPanel();
            right.setOpaque(false);
            right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
            GlyphButton add = new GlyphButton(GlyphButton.PLUS, theme);
            add.setToolTipText("收藏当前城市");
            add.addActionListener(e -> addCurrentToSidebar());
            GlyphButton themeBtn = new GlyphButton(GlyphButton.THEME, theme);
            themeBtn.setToolTipText("切换外观");
            themeBtn.addActionListener(e -> {
                prefs.setThemeMode((prefs.themeMode() + 1) % 3);
                prefs.save();
                applyTheme();
            });
            GlyphButton refresh = new GlyphButton(GlyphButton.REFRESH, theme);
            refresh.setToolTipText("刷新 (F5)");
            refresh.addActionListener(e -> reloadCurrent());
            GlyphButton menu = new GlyphButton(GlyphButton.MENU, theme);
            menu.setToolTipText("更多设置");
            menu.addActionListener(e -> showMenu(menu));
            right.add(add);
            right.add(Box.createHorizontalStrut(4));
            right.add(themeBtn);
            right.add(Box.createHorizontalStrut(4));
            right.add(refresh);
            right.add(Box.createHorizontalStrut(4));
            right.add(menu);
            add(right, BorderLayout.EAST);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(theme.bg2);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(theme.border);
            g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            g2.dispose();
        }
    }

    /** 左侧城市列表 */
    private class Sidebar extends JPanel {
        private final List<Rectangle> rects = new ArrayList<>();
        private int hover = -1;
        private String selected;

        Sidebar() {
            setOpaque(false);
            setPreferredSize(new Dimension(196, 10));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int idx = indexAt(e.getX(), e.getY());
                    if (idx < 0 || idx >= prefs.cities().size() || idx >= rects.size()) return;
                    Prefs.City c = prefs.cities().get(idx);
                    Rectangle r = rects.get(idx);
                    if (e.getX() > r.x + r.width - 30) {
                        int ok = javax.swing.JOptionPane.showConfirmDialog(MainFrame.this,
                                "从列表中移除「" + c.name + "」？", "移除城市",
                                javax.swing.JOptionPane.OK_CANCEL_OPTION,
                                javax.swing.JOptionPane.QUESTION_MESSAGE);
                        if (ok == javax.swing.JOptionPane.OK_OPTION) {
                            prefs.removeCity(c.name);
                            sidebar.reload();
                            status("已移除「" + c.name + "」");
                        }
                        return;
                    }
                    load(c.name, c.lat, c.lon);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int idx = indexAt(e.getX(), e.getY());
                    if (idx != hover) {
                        hover = idx;
                        setCursor(idx >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                                : Cursor.getDefaultCursor());
                        repaint();
                    }
                }
            });
        }

        void reload() {
            repaint();
        }

        void select(String city) {
            selected = city;
            repaint();
        }

        private int indexAt(int x, int y) {
            for (int i = 0; i < rects.size(); i++) {
                if (rects.get(i).contains(x, y)) return i;
            }
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(theme.bg2);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(theme.border);
            g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());

            g.setFont(theme.small);
            g.setColor(theme.sub);
            g.drawString("城市", 18, 34);

            rects.clear();
            int y = 48;
            for (int i = 0; i < prefs.cities().size(); i++) {
                Prefs.City c = prefs.cities().get(i);
                Rectangle r = new Rectangle(10, y, getWidth() - 22, 36);
                rects.add(r);
                boolean sel = c.name.equals(selected);
                if (sel) {
                    g.setColor(theme.dark ? new Color(0x233649) : new Color(0xE3F0FD));
                    g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 10, 10));
                } else if (i == hover) {
                    g.setColor(theme.chip);
                    g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 10, 10));
                }
                g.setColor(sel ? theme.accent : theme.text);
                g.setFont(sel ? theme.bold : theme.body);
                g.drawString(clip(g, c.name, r.width - 44), r.x + 14, r.y + 23);

                g.setColor(sel ? theme.accent : theme.sub);
                g.setFont(theme.small);
                g.drawString("×", r.x + r.width - 20, r.y + 23);
                y += 36;
            }

            g.setFont(theme.small);
            g.setColor(theme.sub);
            int fy = getHeight() - 42;
            g.drawString("点击 ＋ 收藏当前城市", 18, fy);
            g.setColor(theme.dark ? new Color(0x2A323D) : new Color(0xE3EAF3));
            g.drawLine(14, fy - 16, getWidth() - 14, fy - 16);

            g.dispose();
        }

        private String clip(Graphics2D g, String text, int max) {
            FontMetrics fm = g.getFontMetrics();
            if (fm.stringWidth(text) <= max) return text;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                if (fm.stringWidth(sb.toString() + text.charAt(i) + "…") > max) break;
                sb.append(text.charAt(i));
            }
            return sb + "…";
        }
    }

    /** 底部状态条 */
    private class StatusBar extends JPanel {
        private String text = "";
        private final Timer clear;

        StatusBar() {
            setOpaque(false);
            setPreferredSize(new Dimension(10, 26));
            clear = new Timer(6000, e -> {
                text = "";
                repaint();
            });
            clear.setRepeats(false);
        }

        void show(String t) {
            text = t == null ? "" : t;
            clear.restart();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(theme.bg2);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(theme.sub);
            g.setFont(theme.small);
            g.drawString(text, 18, 17);
            String right = "Open-Meteo · 每 10 分钟自动刷新";
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(theme.sub.getRed(), theme.sub.getGreen(), theme.sub.getBlue(), 150));
            g.drawString(right, getWidth() - fm.stringWidth(right) - 18, 17);
            g.dispose();
        }
    }

    /** 带占位符与放大镜图标的搜索框 */
    private class SearchField extends JTextField {
        private boolean focused;

        SearchField() {
            setFont(theme.body);
            setForeground(theme.text);
            setBackground(theme.card);
            setCaretColor(theme.accent);
            setBorder(BorderFactory.createEmptyBorder(0, 42, 0, 14));
            setOpaque(false);
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    focused = true;
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    focused = false;
                    repaint();
                }
            });
            getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    repaint();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    repaint();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(theme.card);
            g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 18, 18));
            g.setColor(focused ? theme.accent : theme.border);
            g.setStroke(new BasicStroke(focused ? 1.6f : 1f));
            g.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 18, 18));
            g.setColor(theme.sub);
            g.setStroke(new BasicStroke(1.6f));
            g.draw(new Ellipse2D.Double(15, 11, 11, 11));
            g.drawLine(25, 21, 29, 25);
            g.dispose();
            super.paintComponent(g0);
            if (getText().isEmpty()) {
                Graphics2D g2 = (Graphics2D) g0.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(theme.body);
                g2.setColor(new Color(theme.sub.getRed(), theme.sub.getGreen(), theme.sub.getBlue(), 160));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("搜索城市，例如 杭州 / Tokyo", 42, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        }
    }

    /** 矢量图标按钮 */
    private static class GlyphButton extends JComponent {
        static final int REFRESH = 0, MENU = 1, THEME = 2, PLUS = 3, SEARCH = 4;
        private final int glyph;
        private final Theme theme;
        private boolean hover;
        private final List<java.awt.event.ActionListener> listeners = new ArrayList<>();

        GlyphButton(int glyph, Theme theme) {
            this.glyph = glyph;
            this.theme = theme;
            setPreferredSize(new Dimension(32, 32));
            setMinimumSize(new Dimension(32, 32));
            setMaximumSize(new Dimension(32, 32));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    for (java.awt.event.ActionListener l : listeners) {
                        l.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "click"));
                    }
                }
            });
        }

        void addActionListener(java.awt.event.ActionListener l) {
            listeners.add(l);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (hover) {
                g.setColor(theme.chip);
                g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 10, 10));
            }
            g.setColor(theme.text);
            g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double cx = getWidth() / 2.0;
            double cy = getHeight() / 2.0;
            switch (glyph) {
                case REFRESH: {
                    g.draw(new java.awt.geom.Arc2D.Double(cx - 7, cy - 7, 14, 14, 60, 270, java.awt.geom.Arc2D.OPEN));
                    g.drawLine((int) cx + 4, (int) cy - 10, (int) cx + 8, (int) cy - 6);
                    g.drawLine((int) cx + 8, (int) cy - 6, (int) cx + 4, (int) cy - 3);
                    break;
                }
                case MENU: {
                    for (int i = -1; i <= 1; i++) {
                        int y = (int) (cy + i * 5);
                        g.drawLine((int) cx - 7, y, (int) cx + 7, y);
                    }
                    break;
                }
                case THEME: {
                    g.draw(new Ellipse2D.Double(cx - 7, cy - 7, 14, 14));
                    g.fill(new java.awt.geom.Arc2D.Double(cx - 7, cy - 7, 14, 14, 90, 180, java.awt.geom.Arc2D.PIE));
                    break;
                }
                case PLUS: {
                    g.drawLine((int) cx - 6, (int) cy, (int) cx + 6, (int) cy);
                    g.drawLine((int) cx, (int) cy - 6, (int) cx, (int) cy + 6);
                    break;
                }
                default: {
                    g.draw(new Ellipse2D.Double(cx - 6, cy - 6, 10, 10));
                    g.drawLine((int) cx + 3, (int) cy + 3, (int) cx + 7, (int) cy + 7);
                    break;
                }
            }
            g.dispose();
        }
    }
}
