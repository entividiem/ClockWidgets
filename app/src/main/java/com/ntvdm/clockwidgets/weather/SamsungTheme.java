package com.ntvdm.clockwidgets.weather;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import com.ntvdm.clockwidgets.R;

/** Galaxy S3 / S4 TouchWiz weather clock artwork: image-digit clock, backgrounds and icons. */
final class SamsungTheme {

    static final SamsungTheme S4 = new SamsungTheme("s4",
            new int[]{R.drawable.s4_num_0, R.drawable.s4_num_1, R.drawable.s4_num_2, R.drawable.s4_num_3,
                    R.drawable.s4_num_4, R.drawable.s4_num_5, R.drawable.s4_num_6, R.drawable.s4_num_7,
                    R.drawable.s4_num_8, R.drawable.s4_num_9, R.drawable.s4_num_colon},
            new int[]{0, 70, 140, 158, 228},
            R.drawable.s4_wth_na, R.drawable.s4_icon_7);

    static final SamsungTheme S3 = new SamsungTheme("s3",
            new int[]{R.drawable.s3_num_0, R.drawable.s3_num_1, R.drawable.s3_num_2, R.drawable.s3_num_3,
                    R.drawable.s3_num_4, R.drawable.s3_num_5, R.drawable.s3_num_6, R.drawable.s3_num_7,
                    R.drawable.s3_num_8, R.drawable.s3_num_9, R.drawable.s3_num_colon},
            new int[]{0, 90, 186, 234, 336},
            R.drawable.s3_wth_na, R.drawable.s3_icon_7);

    private final String prefix;
    private final int[] digitRes;
    /** x offset (in source pixels) of each of the 5 glyphs in "HH:mm". */
    private final int[] offsets;
    private final int noData;
    private final int defaultIcon;
    private Bitmap[] digits;

    private SamsungTheme(String prefix, int[] digitRes, int[] offsets, int noData, int defaultIcon) {
        this.prefix = prefix;
        this.digitRes = digitRes;
        this.offsets = offsets;
        this.noData = noData;
        this.defaultIcon = defaultIcon;
    }

    /** Full-widget background photo for an AccuWeather code (0 = no data). */
    int background(Context context, int code) {
        int id = drawable(context, prefix + "_wth_" + code);
        return id != 0 ? id : noData;
    }

    int icon(Context context, int code) {
        int id = drawable(context, prefix + "_icon_" + code);
        return id != 0 ? id : defaultIcon;
    }

    private static int drawable(Context context, String name) {
        return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
    }

    private synchronized Bitmap[] digits(Context context) {
        if (digits == null) {
            Bitmap[] b = new Bitmap[digitRes.length];
            for (int i = 0; i < b.length; i++) {
                b[i] = BitmapFactory.decodeResource(context.getResources(), digitRes[i]);
            }
            digits = b;
        }
        return digits;
    }

    /** Renders "HH:mm" from the digit images, scaled to heightDp. */
    Bitmap renderTime(Context context, String time, float heightDp) {
        Bitmap[] d = digits(context);
        int srcH = d[0].getHeight();
        float scale = context.getResources().getDisplayMetrics().density * heightDp / srcH;

        int count = Math.min(time.length(), offsets.length);
        int srcW = 0;
        for (int i = 0; i < count; i++) {
            srcW = Math.max(srcW, offsets[i] + glyph(d, time.charAt(i)).getWidth());
        }

        Bitmap out = Bitmap.createBitmap(Math.max(1, Math.round(srcW * scale)),
                Math.max(1, Math.round(srcH * scale)), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        for (int i = 0; i < count; i++) {
            Bitmap g = glyph(d, time.charAt(i));
            int x = Math.round(offsets[i] * scale);
            c.drawBitmap(g, null, new Rect(x, 0, x + Math.round(g.getWidth() * scale), out.getHeight()), p);
        }
        return out;
    }

    private static Bitmap glyph(Bitmap[] d, char ch) {
        return (ch >= '0' && ch <= '9') ? d[ch - '0'] : d[10];
    }
}
