package com.example.weather;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.view.View;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 极简截图分享：把某个 View 画成 Bitmap → 存缓存 → FileProvider 分享。
 * 无需第三方库。
 */
public final class ShareHelper {

    private ShareHelper() {}

    /** 截取 view 并返回图片 Uri（失败返回 null） */
    public static Uri capture(Context ctx, View view) {
        try {
            view.setDrawingCacheEnabled(false);
            Bitmap bmp = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            view.draw(c);

            File dir = new File(ctx.getCacheDir(), "shots");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "weather_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream os = new FileOutputStream(f)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            bmp.recycle();
            return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", f);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** 调起系统分享 */
    public static void share(Context ctx, Uri uri, String text) {
        if (uri == null) return;
        Intent it = new Intent(Intent.ACTION_SEND);
        it.setType("image/png");
        it.putExtra(Intent.EXTRA_STREAM, uri);
        it.putExtra(Intent.EXTRA_TEXT, text);
        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ctx.startActivity(Intent.createChooser(it, ctx.getString(R.string.share)));
    }
}
