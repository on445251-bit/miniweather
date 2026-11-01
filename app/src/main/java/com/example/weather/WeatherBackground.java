package com.example.weather;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Random;

/**
 * 轻量天气背景动画：
 *  - RAIN  : 蓝色雨丝下落
 *  - CLOUD : 缓慢飘动的云（用图标位图）
 *  - NIGHT : 闪烁的星星
 *  - CLEAR : 静态（无动画）
 */
public class WeatherBackground extends View {

    public enum Mode { CLEAR, CLOUD, RAIN, NIGHT }

    private Mode mode = Mode.CLEAR;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            update();
            invalidate();
            h.postDelayed(this, 16);
        }
    };

    private final ArrayList<Drop> drops = new ArrayList<>();
    private final ArrayList<Star> stars = new ArrayList<>();
    private final ArrayList<Cloud> clouds = new ArrayList<>();
    private Drawable cloudDrawable;

    public WeatherBackground(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        cloudDrawable = ContextCompat.getDrawable(context, R.drawable.ic_cloud);
    }

    public void setMode(Mode m) {
        if (this.mode == m) return;
        this.mode = m;
        drops.clear(); stars.clear(); clouds.clear();
        int w = getWidth(), hgt = getHeight();
        if (w == 0 || hgt == 0) return;
        initForMode(w, hgt);
        if (mode == Mode.CLEAR) h.removeCallbacks(tick);
        else h.post(tick);
    }

    private void initForMode(int w, int h) {
        if (mode == Mode.RAIN) {
            for (int i = 0; i < 60; i++) drops.add(new Drop(w, h, rnd));
        } else if (mode == Mode.NIGHT) {
            for (int i = 0; i < 40; i++) stars.add(new Star(w, h, rnd));
        } else if (mode == Mode.CLOUD) {
            for (int i = 0; i < 4; i++) clouds.add(new Cloud(w, h, rnd));
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0 && mode != Mode.CLEAR) initForMode(w, h);
    }

    private void update() {
        int h = getHeight();
        for (Drop d : drops) { d.y += d.vy; d.x += d.vx; if (d.y > h) { d.y = -10; d.x = rnd.nextInt(getWidth()); } }
        for (Star s : stars) s.tw = (float) Math.abs(Math.sin(System.currentTimeMillis() / 600.0 + s.phase));
        for (Cloud c : clouds) { c.x += c.vx; if (c.x > getWidth() + 80) c.x = -80; }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (mode == Mode.RAIN) {
            paint.setColor(0x88FFFFFF);
            paint.setStrokeWidth(2);
            for (Drop d : drops) canvas.drawLine(d.x, d.y, d.x - d.vx, d.y - d.vy * 2, paint);
        } else if (mode == Mode.NIGHT) {
            paint.setColor(0xFFFFFFFF);
            for (Star s : stars) {
                paint.setAlpha((int) (180 * s.tw));
                canvas.drawCircle(s.x, s.y, s.r, paint);
            }
        } else if (mode == Mode.CLOUD) {
            for (Cloud c : clouds) {
                if (cloudDrawable != null) {
                    cloudDrawable.setAlpha(120);
                    cloudDrawable.setBounds((int) c.x, (int) c.y, (int) c.x + c.size, (int) c.y + c.size);
                    cloudDrawable.draw(canvas);
                }
            }
        }
    }

    // ---- 粒子 ----
    static class Drop { float x, y, vx, vy; Drop(int w, int h, Random r) {
        x = r.nextInt(w); y = r.nextInt(h); vy = 8 + r.nextInt(8); vx = 2; } }
    static class Star { float x, y, r, phase, tw; Star(int w, int h, Random r) {
        x = r.nextInt(w); y = r.nextInt(h); r = 1 + r.nextInt(2); phase = r.nextInt(100); } }
    static class Cloud { float x, y, vx, size; Cloud(int w, int h, Random r) {
        x = r.nextInt(w); y = 20 + r.nextInt(h / 3); size = 60 + r.nextInt(60); vx = 0.2f + r.nextFloat() * 0.4f; } }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        h.removeCallbacks(tick);
    }
}
