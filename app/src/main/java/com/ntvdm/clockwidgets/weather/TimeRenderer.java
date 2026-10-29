package com.ntvdm.clockwidgets.weather;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import java.io.File;

/** Draws the big thin HTC Sense-style time as a bitmap (used when TextClock isn't available). */
final class TimeRenderer {

    private static final float DIGIT_HEIGHT_DP = 58f;
    private static final String[] THIN_FONTS = {
            "/system/fonts/HelveticaNeueLTPro35Th.ttf", // HTC Sense
            "/system/fonts/Roboto-Thin.ttf",
            "/system/fonts/Roboto-Light.ttf"
    };
    private static Typeface typeface;

    private TimeRenderer() {
    }

    private static synchronized Typeface typeface() {
        if (typeface == null) {
            for (String path : THIN_FONTS) {
                if (new File(path).exists()) {
                    try {
                        typeface = Typeface.createFromFile(path);
                        break;
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            if (typeface == null) {
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL);
            }
        }
        return typeface;
    }

    static Bitmap render(Context context, String text) {
        return render(context, text, DIGIT_HEIGHT_DP);
    }

    static Bitmap render(Context context, String text, float digitHeightDp) {
        float density = context.getResources().getDisplayMetrics().density;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setColor(0xFFFFFFFF);
        p.setTypeface(typeface());

        // scale so the digit glyphs are exactly digitHeightDp tall
        Rect digits = new Rect();
        p.setTextSize(100f);
        p.getTextBounds("0123456789", 0, 10, digits);
        p.setTextSize(density * 100f * digitHeightDp / digits.height());
        p.getTextBounds("0123456789", 0, 10, digits);

        Rect bounds = new Rect();
        p.getTextBounds(text, 0, text.length(), bounds);
        Bitmap bmp = Bitmap.createBitmap(
                Math.max(1, bounds.right - bounds.left + 2),
                Math.max(1, digits.height() + 2),
                Bitmap.Config.ARGB_8888);
        new Canvas(bmp).drawText(text, 1 - bounds.left, 1 - digits.top, p);
        return bmp;
    }
}
