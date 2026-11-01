package com.example.weather;

import android.content.Context;
import android.content.res.ColorStateList;

import com.google.android.material.card.MaterialCardView;

/**
 * 界面风格：标准（Material You 色调）、玻璃（半透明磨砂感）、极简（扁平细边）。
 * 只影响卡片底色 / 圆角 / 描边与背景遮罩强度，不改变布局，因此两套平台表现一致。
 */
public final class UiStyle {

    public static final int STANDARD = 0;
    public static final int GLASS = 1;
    public static final int MINIMAL = 2;

    private UiStyle() {}

    public static String name(int id) {
        switch (id) {
            case GLASS: return "玻璃";
            case MINIMAL: return "极简";
            default: return "标准";
        }
    }

    /** 卡片底色（含透明度） */
    public static int cardColor(int style, boolean night) {
        switch (style) {
            case GLASS:
                return night ? 0x99151B24 : 0xB3FFFFFF;
            case MINIMAL:
                return night ? 0xFF161B23 : 0xFFF7F9FC;
            default:
                return night ? 0xFF1B212B : 0xFFFFFFFF;
        }
    }

    public static int radiusDp(int style) {
        switch (style) {
            case GLASS: return 22;
            case MINIMAL: return 10;
            default: return 20;
        }
    }

    public static int strokeWidthDp(int style) {
        return style == MINIMAL ? 1 : style == GLASS ? 1 : 0;
    }

    public static int strokeColor(int style, boolean night) {
        if (style == GLASS) return night ? 0x33FFFFFF : 0x40FFFFFF;
        if (style == MINIMAL) return night ? 0x22FFFFFF : 0x14000000;
        return 0;
    }

    /** 背景遮罩强度：晴天天亮时靠它保证文字可读 */
    public static int scrimColor(int style, boolean night, boolean bright) {
        if (night) return 0x33000000;
        switch (style) {
            case GLASS: return bright ? 0x66000000 : 0x40000000;
            case MINIMAL: return bright ? 0x55000000 : 0x33000000;
            default: return bright ? 0x5C000000 : 0x33000000;
        }
    }

    public static void applyCard(MaterialCardView card, int style, boolean night, Context ctx) {
        if (card == null) return;
        card.setCardBackgroundColor(ColorStateList.valueOf(cardColor(style, night)));
        card.setRadius(dp(ctx, radiusDp(style)));
        card.setCardElevation(0f);
        int sw = strokeWidthDp(style);
        card.setStrokeWidth(sw == 0 ? 0 : (int) dp(ctx, sw));
        card.setStrokeColor(strokeColor(style, night));
    }

    private static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }
}
