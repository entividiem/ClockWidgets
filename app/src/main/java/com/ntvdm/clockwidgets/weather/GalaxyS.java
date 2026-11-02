package com.ntvdm.clockwidgets.weather;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.widget.RemoteViews;

import com.ntvdm.clockwidgets.R;

import java.util.Calendar;
import java.util.Date;

/**
 * Renders the Galaxy S (TouchWiz 3.0) "AccuWeather Clock", following WeatherClock.java /
 * AccuweatherUiUtil from com.sec.android.widgetapp.weatherclock (GT-I9000, 2010).
 * The hands are a plain AnalogClock in the layout, so only weather, day and the
 * high/low digits are set here.
 */
final class GalaxyS {

    private GalaxyS() {
    }

    /**
     * AccuweatherUiUtil.iconGroups_Main: AccuWeather code -> round dial photo.
     * Index i of GROUPS uses PHOTOS[i].
     */
    private static final int[][] GROUPS = {
            {1, 2, 30}, {3, 4, 5}, {6}, {7, 8}, {11}, {12, 18}, {13}, {14}, {15}, {16}, {17},
            {19, 20, 24, 25, 26, 29}, {21}, {22, 23}, {31}, {32}, {33, 34}, {35, 36, 37}, {38},
            {39, 40}, {41, 42}, {43, 44}};
    private static final int[] PHOTOS = {
            R.drawable.s1_weather_1_01_02_30, R.drawable.s1_weather_1_03_04_05, R.drawable.s1_weather_1_06,
            R.drawable.s1_weather_1_07_08, R.drawable.s1_weather_1_11, R.drawable.s1_weather_1_12_18,
            R.drawable.s1_weather_1_13, R.drawable.s1_weather_1_14, R.drawable.s1_weather_1_15,
            R.drawable.s1_weather_1_16, R.drawable.s1_weather_1_17, R.drawable.s1_weather_1_19_20_24_25_26_29,
            R.drawable.s1_weather_1_21, R.drawable.s1_weather_1_22_23, R.drawable.s1_weather_1_31,
            R.drawable.s1_weather_1_32, R.drawable.s1_weather_1_33_34, R.drawable.s1_weather_1_35_36_37,
            R.drawable.s1_weather_1_38, R.drawable.s1_weather_1_39_40, R.drawable.s1_weather_1_41_42,
            R.drawable.s1_weather_1_43_44};

    private static final int[] DIGITS = {
            R.drawable.s1_weather_clock_no_0, R.drawable.s1_weather_clock_no_1, R.drawable.s1_weather_clock_no_2,
            R.drawable.s1_weather_clock_no_3, R.drawable.s1_weather_clock_no_4, R.drawable.s1_weather_clock_no_5,
            R.drawable.s1_weather_clock_no_6, R.drawable.s1_weather_clock_no_7, R.drawable.s1_weather_clock_no_8,
            R.drawable.s1_weather_clock_no_9};

    static RemoteViews render(Context context, SharedPreferences p, boolean fetching) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.weather_widget_galaxys);

        rv.setTextViewText(R.id.s1_day, Integer.toString(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)));

        boolean configured = WeatherWidgetProvider.isConfigured(p);
        boolean hasData = configured && p.contains(WeatherWidgetProvider.KEY_TEMP);

        // dial photo (no data: the bare dark dial, as the original)
        if (hasData) {
            int photo = photo(WeatherIcons.accuCode(p.getString(WeatherWidgetProvider.KEY_ICON, null)));
            rv.setImageViewResource(R.id.s1_weather_photo, photo);
            rv.setViewVisibility(R.id.s1_weather_photo, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.s1_weather_photo, View.INVISIBLE);
        }

        // high / low
        float hi = p.getFloat(WeatherWidgetProvider.KEY_HIGH, Float.NaN);
        float lo = p.getFloat(WeatherWidgetProvider.KEY_LOW, Float.NaN);
        if (hasData && !Float.isNaN(hi) && !Float.isNaN(lo)) {
            boolean f = p.getBoolean(WeatherWidgetProvider.KEY_FAHRENHEIT, false);
            rv.setImageViewBitmap(R.id.s1_temps, temps(context, Math.round(hi), Math.round(lo), f));
            rv.setViewVisibility(R.id.s1_temps, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.s1_temps, View.INVISIBLE);
        }

        // city bar
        if (configured) {
            rv.setViewVisibility(R.id.s1_select_city, View.GONE);
            rv.setViewVisibility(R.id.s1_city_row, View.VISIBLE);
            rv.setTextViewText(R.id.s1_city, p.getString(WeatherWidgetProvider.KEY_NAME, ""));
            long updated = p.getLong(WeatherWidgetProvider.KEY_UPDATED, 0L);
            if (updated != 0) {
                Date d = new Date(updated);
                rv.setTextViewText(R.id.s1_updated_date, android.text.format.DateFormat.getDateFormat(context).format(d));
                rv.setTextViewText(R.id.s1_updated_time, android.text.format.DateFormat.getTimeFormat(context).format(d));
            } else {
                rv.setTextViewText(R.id.s1_updated_date, "");
                rv.setTextViewText(R.id.s1_updated_time, "");
            }
        } else {
            rv.setViewVisibility(R.id.s1_select_city, View.VISIBLE);
            rv.setViewVisibility(R.id.s1_city_row, View.GONE);
        }

        // refresh button spins while a fetch is running
        rv.setViewVisibility(R.id.s1_refresh, fetching ? View.INVISIBLE : View.VISIBLE);
        rv.setViewVisibility(R.id.s1_refreshing, fetching ? View.VISIBLE : View.GONE);
        return rv;
    }

    private static int photo(int accuCode) {
        for (int i = 0; i < GROUPS.length; i++) {
            for (int c : GROUPS[i]) {
                if (c == accuCode) {
                    return PHOTOS[i];
                }
            }
        }
        return PHOTOS[3]; // cloudy
    }

    /**
     * "hi° / lo°" like buildTempInfoLayout(): digit images in 14px cells (hdpi), each glyph
     * drawn 16x21px, unit glyph after the number; high right-aligned in a 60dp box, the
     * slash image (11.4 x 14.5dp), low left-aligned in a 60dp box.
     */
    private static Bitmap temps(Context context, int hi, int lo, boolean fahrenheit) {
        float f = context.getResources().getDisplayMetrics().density / 1.5f; // art is hdpi
        int cell = Math.round(14 * f), gw = Math.round(16 * f), gh = Math.round(21 * f);
        int box = Math.round(90 * f);                                       // 60dp at hdpi
        int slashW = Math.round(17 * f), slashH = Math.round(22 * f);       // 11.4 x 14.5dp
        int w = box * 2 + slashW, h = Math.max(gh, slashH);

        Bitmap out = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        int unit = fahrenheit ? R.drawable.s1_weather_clock_fahrenheit : R.drawable.s1_weather_clock_centigrade;

        String hs = Integer.toString(hi), ls = Integer.toString(lo);
        int hiWidth = (hs.length() + 1) * cell;
        int x = box - hiWidth;                                              // right-aligned
        x = glyphs(context, c, paint, hs, unit, x, (h - gh) / 2, cell, gw, gh);

        Bitmap slash = BitmapFactory.decodeResource(context.getResources(), R.drawable.s1_weather_clock_centigrade_line);
        c.drawBitmap(slash, null, new Rect(box, (h - slashH) / 2, box + slashW, (h - slashH) / 2 + slashH), paint);
        slash.recycle();

        glyphs(context, c, paint, ls, unit, box + slashW, (h - gh) / 2, cell, gw, gh); // left-aligned
        return out;
    }

    private static int glyphs(Context context, Canvas c, Paint paint, String s, int unit,
                              int x, int y, int cell, int gw, int gh) {
        for (int i = 0; i <= s.length(); i++) {
            int res;
            if (i == s.length()) {
                res = unit;
            } else {
                char ch = s.charAt(i);
                res = ch == '-' ? R.drawable.s1_weather_clock_no_m : DIGITS[ch - '0'];
            }
            Bitmap g = BitmapFactory.decodeResource(context.getResources(), res);
            int gx = x + (cell - gw) / 2;
            c.drawBitmap(g, null, new Rect(gx, y, gx + gw, y + gh), paint);
            g.recycle();
            x += cell;
        }
        return x;
    }
}
