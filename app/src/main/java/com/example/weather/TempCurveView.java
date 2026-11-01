package com.example.weather;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * 7 天高低温曲线：每天一根渐变温度带 + 高温/低温折线 + 数值与日期标签。
 * 纯 Canvas 绘制，无任何图表库依赖。
 */
public class TempCurveView extends View {

    private static final int COLD = 0xFF42A5F5;
    private static final int HOT = 0xFFFF7043;

    private int[] highs = new int[0];
    private int[] lows = new int[0];
    private String[] labels = new String[0];

    private final Paint hotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path hotPath = new Path();
    private final Path coldPath = new Path();

    private float padH, padTop, padBottom;

    public TempCurveView(Context c) {
        this(c, null);
    }

    public TempCurveView(Context c, AttributeSet attrs) {
        this(c, attrs, 0);
    }

    public TempCurveView(Context c, AttributeSet attrs, int defStyle) {
        super(c, attrs, defStyle);
        padH = dp(20);
        padTop = dp(28);
        padBottom = dp(26);

        hotPaint.setStyle(Paint.Style.STROKE);
        hotPaint.setStrokeWidth(dp(2.2f));
        hotPaint.setStrokeCap(Paint.Cap.ROUND);
        hotPaint.setStrokeJoin(Paint.Join.ROUND);
        hotPaint.setColor(HOT);

        coldPaint.setStyle(Paint.Style.STROKE);
        coldPaint.setStrokeWidth(dp(2.2f));
        coldPaint.setStrokeCap(Paint.Cap.ROUND);
        coldPaint.setStrokeJoin(Paint.Join.ROUND);
        coldPaint.setColor(COLD);

        barPaint.setStyle(Paint.Style.FILL);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(0xFFFFFFFF);

        textPaint.setTextSize(dp(11));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        dayPaint.setTextSize(dp(11));
        dayPaint.setTextAlign(Paint.Align.CENTER);
        dayPaint.setColor(resolveSecondaryText());
    }

    public void setData(int[] highs, int[] lows, String[] labels) {
        this.highs = highs == null ? new int[0] : highs;
        this.lows = lows == null ? new int[0] : lows;
        this.labels = labels == null ? new String[0] : labels;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        if (h == 0 || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            h = (int) dp(170);
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = Math.min(Math.min(highs.length, lows.length), labels.length);
        if (n <= 0) return;

        float w = getWidth();
        float h = getHeight();
        if (w <= padH * 2 || h <= padTop + padBottom) return;

        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            lo = Math.min(lo, Math.min(lows[i], highs[i]));
            hi = Math.max(hi, Math.max(lows[i], highs[i]));
        }
        if (hi == lo) { hi += 1; lo -= 1; }
        // 上下留一点余量，避免标签贴边
        int span = hi - lo;
        lo -= Math.max(1, span / 8);
        hi += Math.max(1, span / 8);

        float top = padTop;
        float bottom = h - padBottom;
        float plotH = bottom - top;
        float usable = w - padH * 2;
        float step = n > 1 ? usable / (n - 1) : 0;

        textPaint.setColor(resolvePrimaryText());

        // 每天的渐变温度带
        float barW = Math.max(dp(6), Math.min(dp(10), step * 0.28f));
        for (int i = 0; i < n; i++) {
            float x = padH + i * step;
            float yHot = y(highs[i], lo, hi, top, plotH);
            float yCold = y(lows[i], lo, hi, top, plotH);
            barPaint.setShader(new LinearGradient(x, yHot, x, yCold,
                    withAlpha(HOT, 90), withAlpha(COLD, 90), Shader.TileMode.CLAMP));
            float r = barW / 2f;
            canvas.drawRoundRect(x - r, yHot, x + r, yCold, r, r, barPaint);
            barPaint.setShader(null);
        }

        // 折线
        buildPath(hotPath, highs, n, step, lo, hi, top, plotH);
        canvas.drawPath(hotPath, hotPaint);
        buildPath(coldPath, lows, n, step, lo, hi, top, plotH);
        canvas.drawPath(coldPath, coldPaint);

        // 圆点 + 数值
        for (int i = 0; i < n; i++) {
            float x = padH + i * step;
            float yHot = y(highs[i], lo, hi, top, plotH);
            float yCold = y(lows[i], lo, hi, top, plotH);

            dotPaint.setColor(HOT);
            canvas.drawCircle(x, yHot, dp(2.6f), dotPaint);
            dotPaint.setColor(COLD);
            canvas.drawCircle(x, yCold, dp(2.6f), dotPaint);

            textPaint.setColor(Color.parseColor("#E64A19"));
            canvas.drawText(String.valueOf(highs[i]), x, yHot - dp(7), textPaint);
            textPaint.setColor(Color.parseColor("#1E88E5"));
            canvas.drawText(String.valueOf(lows[i]), x, yCold + dp(15), textPaint);

            canvas.drawText(labels[i], x, h - dp(8), dayPaint);
        }
    }

    private void buildPath(Path p, int[] vals, int n, float step,
                           int lo, int hi, float top, float plotH) {
        p.reset();
        for (int i = 0; i < n; i++) {
            float x = padH + i * step;
            float y = y(vals[i], lo, hi, top, plotH);
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
    }

    private static float y(int value, int lo, int hi, float top, float plotH) {
        float ratio = (value - lo) / (float) (hi - lo);
        return top + (1f - ratio) * plotH;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int resolvePrimaryText() {
        return resolveThemeColor(android.R.attr.textColorPrimary, 0xFF212121);
    }

    private int resolveSecondaryText() {
        return resolveThemeColor(android.R.attr.textColorSecondary, 0xFF757575);
    }

    private int resolveThemeColor(int attr, int fallback) {
        try {
            TypedValue tv = new TypedValue();
            if (getContext().getTheme().resolveAttribute(attr, tv, true)) {
                if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                        && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    return tv.data;
                }
                if (tv.resourceId != 0) {
                    return androidx.core.content.ContextCompat.getColor(getContext(), tv.resourceId);
                }
            }
        } catch (Exception ignore) {
        }
        return fallback;
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
