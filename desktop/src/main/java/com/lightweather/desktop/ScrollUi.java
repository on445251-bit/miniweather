package com.lightweather.desktop;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/** 轻量滚动条外观（圆角滑块、无箭头按钮） */
public final class ScrollUi {

    private ScrollUi() {
    }

    public static void install(JScrollPane pane, Theme theme) {
        pane.setBorder(null);
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        JScrollBar bar = pane.getVerticalScrollBar();
        bar.setUI(new Ui(theme));
        bar.setPreferredSize(new Dimension(10, 0));
        bar.setUnitIncrement(28);
        bar.setOpaque(false);
    }

    private static final class Ui extends BasicScrollBarUI {
        private final Theme theme;

        Ui(Theme theme) {
            this.theme = theme;
        }

        @Override
        protected void configureScrollBarColors() {
            trackColor = new Color(0, 0, 0, 0);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zero();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zero();
        }

        private JButton zero() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            b.setBorderPainted(false);
            b.setOpaque(false);
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            // 透明轨道
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(theme.scrollThumb);
            int w = 6;
            int x = r.x + (r.width - w) / 2;
            g2.fill(new RoundRectangle2D.Double(x, r.y + 2, w, Math.max(20, r.height - 4), w, w));
            g2.dispose();
        }
    }
}
