package com.lightweather.desktop;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 应用图标：蓝底圆角方块 + 太阳 + 云，直接矢量绘制，输出 PNG / ICO。
 * ICO 采用 Vista 之后的 PNG 压缩条目格式，因此不需要额外的图像库。
 */
public final class AppIcon {

    private AppIcon() {
    }

    public static BufferedImage image(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        double radius = size * 0.22;
        g.setPaint(new GradientPaint(0, 0, new Color(0x2196F3), size, size, new Color(0x1565C0)));
        g.fill(new RoundRectangle2D.Double(0, 0, size, size, radius, radius));

        // 太阳
        double sunR = size * 0.13;
        double sunCx = size * 0.66;
        double sunCy = size * 0.34;
        g.setColor(new Color(0xFFD54F));
        g.fill(new Ellipse2D.Double(sunCx - sunR, sunCy - sunR, sunR * 2, sunR * 2));
        g.setStroke(new BasicStroke((float) (size * 0.035), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xFFD54F));
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8 + 0.39;
            double r1 = sunR * 1.45;
            double r2 = sunR * 2.0;
            g.draw(new java.awt.geom.Line2D.Double(
                    sunCx + Math.cos(a) * r1, sunCy + Math.sin(a) * r1,
                    sunCx + Math.cos(a) * r2, sunCy + Math.sin(a) * r2));
        }

        // 云
        g.setColor(new Color(255, 255, 255, 240));
        double cy = size * 0.62;
        g.fill(new Ellipse2D.Double(size * 0.22, cy - size * 0.10, size * 0.30, size * 0.30));
        g.fill(new Ellipse2D.Double(size * 0.38, cy - size * 0.17, size * 0.36, size * 0.36));
        g.fill(new Ellipse2D.Double(size * 0.57, cy - size * 0.06, size * 0.26, size * 0.26));
        g.fill(new RoundRectangle2D.Double(size * 0.22, cy + size * 0.04, size * 0.61, size * 0.16,
                size * 0.16, size * 0.16));

        g.dispose();
        return img;
    }

    /** 生成单条目 ICO（256×256 PNG 负载） */
    public static byte[] ico(int size) throws IOException {
        BufferedImage img = image(size);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(img, "png", png);
        byte[] data = png.toByteArray();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeShort(out, 0);      // reserved
        writeShort(out, 1);      // type: icon
        writeShort(out, 1);      // count
        out.write(size >= 256 ? 0 : size); // width
        out.write(size >= 256 ? 0 : size); // height
        out.write(0);            // palette
        out.write(0);            // reserved
        writeShort(out, 1);      // planes
        writeShort(out, 32);     // bpp
        writeInt(out, data.length);
        writeInt(out, 22);       // offset: 6 + 16
        out.write(data);
        return out.toByteArray();
    }

    public static void writeIco(File file, int size) throws IOException {
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(ico(size));
        }
    }

    private static void writeShort(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }
}
