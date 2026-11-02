package com.ntvdm.clockwidgets.weather;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;

import com.ntvdm.clockwidgets.MainActivity;
import com.ntvdm.clockwidgets.R;

import java.io.File;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;

/**
 * Sony Ericsson Xperia (2011: Play, Arc, Neo, Ray...) "Weather widget"
 * (com.sonyericsson.advancedwidget.weather), ported from a Xperia Home "advanced widget" to a
 * normal AppWidget.
 *
 * The original animates a layered scene (WeatherWidgetView + conditions/*). A widget can't
 * animate, so this composes the same layers, at the same positions and with the same alpha /
 * rotation formulas, with Sony's animation clock frozen at {@link #T}. Labels are drawn like
 * Sony's uicomponent.Label (centre-pivot, ascent/descent box). Everything works in the
 * original's hdpi pixel space (plate 220x430) and is scaled to the screen density.
 *
 * The finished picture is cached as a PNG in files/framed/ (served by {@link BackgroundProvider})
 * under a name derived from its content, so it is only re-drawn when something on it changes.
 */
final class Xperia {

    private static final String TAG = "WeatherClock";

    static final String ACTION_FLIP = "com.ntvdm.clockwidgets.weather.XPERIA_FLIP";
    private static final String KEY_BACK = "xperia_back_";

    /** Sony's WeatherClock frozen here (ms): cross-fades half way, rain mid-fall. */
    private static final float T = 1571f;

    // plate size and Sony's text sizes (values-port-hdpi/integers.xml), hdpi px
    private static final float W = 220f, H = 430f;
    private static final int TEXT_TEMPERATURE = 80, TEXT_LOCATION = 32, TEXT_HEADLINE = 25,
            TEXT_LARGE = 22, TEXT_SMALL = 16, DEFAULT_FONT_SIZE = 16;
    private static final int GREY = 0xff919baa; // -7234646
    /** Room around the plate. The plate PNG already has 48 transparent rows on top for the
     *  sun / moon; Sony's view only adds a few px (scenes poke out <= ~18px and get clipped
     *  there too). Same margin top and bottom so fitCenter keeps the plate where Sony had it. */
    private static final float MX = 8f, MT = 10f;

    private Xperia() {
    }

    // ================================================================= widget glue

    static boolean showingBack(SharedPreferences p, int id) {
        return p.getBoolean(KEY_BACK + id, false);
    }

    static void flip(SharedPreferences p, int id) {
        p.edit().putBoolean(KEY_BACK + id, !showingBack(p, id)).commit();
    }

    static void forget(SharedPreferences p, int id) {
        p.edit().remove(KEY_BACK + id).commit();
    }

    static RemoteViews render(Context context, int id, SharedPreferences p, int piFlags) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.weather_widget_xperia);
        boolean configured = WeatherWidgetProvider.isConfigured(p);
        boolean hasData = configured && p.contains(WeatherWidgetProvider.KEY_TEMP);

        String side = !hasData ? "setup" : showingBack(p, id) ? "back" : "front";
        Uri uri = null;
        try {
            uri = picture(context, p, side, configured);
        } catch (Throwable t) { // OOM on a 2.x phone etc.: keep the widget alive
            Log.w(TAG, "Xperia render failed", t);
        }
        if (uri != null) {
            rv.setImageViewUri(R.id.xperia_image, uri);
        } else {
            rv.setImageViewResource(R.id.xperia_image, R.drawable.xp_plate);
        }

        PendingIntent pi;
        if (hasData) {
            Intent flip = new Intent(context, XperiaWidgetProvider.class)
                    .setAction(ACTION_FLIP)
                    .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            pi = PendingIntent.getBroadcast(context, 1000 + id, flip, piFlags);
        } else {
            Intent setup = new Intent(context, MainActivity.class)
                    .setAction(WeatherSetup.ACTION_SHOW)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            pi = PendingIntent.getActivity(context, 0, setup, piFlags);
        }
        rv.setOnClickPendingIntent(R.id.xperia_image, pi);
        return rv;
    }

    // ================================================================= picture cache

    private static Uri picture(Context context, SharedPreferences p, String side, boolean configured) throws Exception {
        float f = context.getResources().getDisplayMetrics().density / 1.5f;
        Data d = new Data(context, p);
        String key = "v3|" + side + "|" + f + "|" + d.key() + "|" + Locale.getDefault();
        File dir = FramedBackground.dir(context);
        File file = new File(dir, "xp_" + Integer.toHexString(key.hashCode()) + ".png");

        if (!file.exists()) {
            Bitmap out = Bitmap.createBitmap(Math.max(1, Math.round((W + 2 * MX) * f)),
                    Math.max(1, Math.round((H + 2 * MT) * f)), Bitmap.Config.ARGB_8888);
            try {
                Canvas canvas = new Canvas(out);
                canvas.scale(f, f);
                Painter pt = new Painter(context, canvas);
                if ("setup".equals(side)) {
                    drawSetup(pt);
                } else if ("back".equals(side)) {
                    drawBack(pt, d);
                } else {
                    drawFront(pt, d);
                }
                pt.recycle();

                dir.mkdirs();
                File tmp = new File(file.getPath() + ".tmp");
                FileOutputStream fos = new FileOutputStream(tmp);
                try {
                    out.compress(Bitmap.CompressFormat.PNG, 100, fos);
                } finally {
                    fos.close();
                }
                if (!tmp.renameTo(file)) {
                    return null;
                }
                FramedBackground.trim(context, file);
            } finally {
                out.recycle();
            }
        }
        return new Uri.Builder().scheme("content").authority(BackgroundProvider.authority(context))
                .path(file.getName()).build();
    }

    /** Everything shown, read from the shared weather prefs. */
    private static final class Data {
        final String city;
        final boolean f;
        final int temp;
        final int current;              // AccuWeather code of the current condition
        final int[] day = new int[OpenMeteo.DAYS], night = new int[OpenMeteo.DAYS];
        final String[] max = new String[OpenMeteo.DAYS], min = new String[OpenMeteo.DAYS];
        final String date;
        final String[] weekday = new String[OpenMeteo.DAYS];

        Data(Context c, SharedPreferences p) {
            city = p.getString(WeatherWidgetProvider.KEY_NAME, "");
            f = p.getBoolean(WeatherWidgetProvider.KEY_FAHRENHEIT, false);
            temp = Math.round(p.getFloat(WeatherWidgetProvider.KEY_TEMP, 0f));
            current = WeatherIcons.accuCode(p.getString(WeatherWidgetProvider.KEY_ICON, null));
            String[] codes = split(p.getString(WeatherWidgetProvider.KEY_DAILY_CODES, ""));
            String[] mx = split(p.getString(WeatherWidgetProvider.KEY_DAILY_MAX, ""));
            String[] mn = split(p.getString(WeatherWidgetProvider.KEY_DAILY_MIN, ""));
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat eee = new SimpleDateFormat("EEE", Locale.getDefault());
            for (int i = 0; i < OpenMeteo.DAYS; i++) {
                int wmo = i < codes.length && codes[i].length() > 0 ? parse(codes[i], 3) : -1;
                day[i] = wmo < 0 ? 0 : WeatherIcons.accuCode(OpenMeteo.icon(wmo, true));
                night[i] = wmo < 0 ? 0 : WeatherIcons.accuCode(OpenMeteo.icon(wmo, false));
                max[i] = i < mx.length && mx[i].length() > 0 ? mx[i] + "°" : "--°";
                min[i] = i < mn.length && mn[i].length() > 0 ? mn[i] + "°" : "--°";
                weekday[i] = eee.format(cal.getTime());
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            date = DateFormat.getDateInstance(DateFormat.LONG).format(new Date());
        }

        String key() {
            StringBuilder sb = new StringBuilder(city).append('|').append(temp).append('|').append(current)
                    .append('|').append(date);
            for (int i = 0; i < OpenMeteo.DAYS; i++) {
                sb.append('|').append(day[i]).append(',').append(night[i]).append(',')
                        .append(max[i]).append(',').append(min[i]);
            }
            return sb.toString();
        }

        private static String[] split(String s) {
            return s.length() == 0 ? new String[0] : s.split(",", -1);
        }

        private static int parse(String s, int def) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }
    }

    // ================================================================= the three sides

    /** WeatherWidgetView.createLayouts() - the "weather" side. */
    private static void drawFront(Painter pt, Data d) {
        pt.image("plate", 0, 0, 255);

        pt.imageAnchored("divider_horizontal", 0, H * 0.28f, 0.5f, 1.0f);
        pt.label(d.city, pt.fit(d.city, TEXT_LOCATION), 0xffffffff, 0, (-H / 2f) * 0.18f);
        pt.label(d.date, TEXT_SMALL, 0xffffffff, 0, 0);
        pt.label(d.temp + "°", TEXT_TEMPERATURE, 0xffffffff, 10f, H / 8f);
        pt.labelAnchored("Day", TEXT_SMALL, 0xffffffff, -W * 0.29f, H * 0.35f, 0.5f, 1.0f);
        pt.labelAnchored("Night", TEXT_SMALL, 0xffffffff, W * 0.29f, H * 0.35f, 0.5f, 1.0f);
        // icons were positioned while still icon_empty (1x1): mY = y - 0.5
        pt.image(icon(d.day[0]), -W * 0.29f, H * 0.41f - 0.5f, 255);
        pt.image(icon(d.night[0]), W * 0.29f, H * 0.41f - 0.5f, 255);
        pt.label(d.max[0], TEXT_LARGE, 0xffffffff, 0, H * 0.35f);
        pt.label(d.min[0], TEXT_LARGE, GREY, 0, H * 0.42f);

        scene(pt, d.current); // mWeather is added last: drawn over the labels, as in the original
    }

    /** The forecast side (mForecastBackground). */
    private static void drawBack(Painter pt, Data d) {
        pt.image("plate", 0, 0, 255);
        float[] dividers = {-0.204f, -0.031f, 0.146f, 0.323f};
        for (float y : dividers) {
            pt.image("divider_horizontal", 0, H * y, 255);
        }
        pt.label("Forecast", pt.fit("Forecast", TEXT_HEADLINE), 0xffffffff, 0, -H * 0.29f);

        float[] rowY = {-0.119f, 0.058f, 0.235f};
        float[] maxY = {-0.149f, 0.028f, 0.205f};
        float[] minY = {-0.088f, 0.088f, 0.265f};
        for (int i = 0; i < 3; i++) {
            pt.labelPivot(d.weekday[i + 1], TEXT_LARGE, 0xffffffff, -W * 0.43f, H * rowY[i], 0f);
            pt.labelPivot(d.max[i + 1], TEXT_LARGE, 0xffffffff, W * 0.43f, H * maxY[i], 1f);
            pt.labelPivot(d.min[i + 1], TEXT_LARGE, GREY, W * 0.43f, H * minY[i], 1f);
            pt.image(icon(d.day[i + 1]), 0, H * rowY[i], 255);
        }
        // where the AccuWeather logotype was: Open-Meteo's CC BY credit
        pt.label("Open-Meteo.com", TEXT_SMALL, 0xffffffff, 0, H * 0.412f);
    }

    /** createSetupLayout(): "Welcome" / "Tap to set up". */
    private static void drawSetup(Painter pt) {
        pt.image("plate", 0, 0, 255);
        pt.image("welcome_icon", 0, -H / 6f, 255);
        pt.label("Welcome", pt.fit("Welcome", TEXT_LOCATION), 0xffffffff, 0, H / 8f);
        pt.label("Tap to set up", pt.fit("Tap to set up", TEXT_SMALL), 0xffffffff, 0, H / 5f);
    }

    // ================================================================= icons + scenes

    /** WeatherWidgetView.getForecastImage(): AccuWeather code -> small icon (null = icon_empty). */
    private static String icon(int c) {
        switch (c) {
            case 1: case 30: return "icon_sun";
            case 2: case 3: case 4: return "icon_partly_cloud";
            case 5: return "icon_haze";
            case 6: return "icon_mostly_clouds";
            case 7: case 8: return "icon_clouds";
            case 9: case 10: case 21: case 27: case 28: return "icon_partly_cloud";
            case 11: return "icon_fog_small";
            case 12: case 13: case 39: case 40: return "icon_showers";
            case 14: return "icon_sunny_rain";
            case 15: case 16: case 17: case 41: case 42: return "icon_lightning";
            case 18: return "icon_rain";
            case 19: case 20: case 43: case 44: return "icon_flurries";
            case 22: case 23: return "icon_snow";
            case 24: case 31: return "icon_snowflake";
            case 25: case 26: case 29: return "icon_snow_and_rain";
            case 32: return "icon_windy";
            case 33: return "icon_moon";
            case 34: case 35: case 36: return "icon_partly_cloud_moon";
            case 37: return "icon_haze_night";
            case 38: return "icon_mostly_clouds_night";
            default: return null;
        }
    }

    /** WeatherWidgetView.createAnimatedWeather() */
    private static void scene(Painter pt, int c) {
        switch (c) {
            case 1: case 30: sunny(pt, false); break;
            case 2: case 3: case 4: partlyCloudyScene(pt); break;
            case 5: sunny(pt, false); haze(pt, "haze_clouds", 3f); break;
            case 6: bgClouds(pt); sunny(pt, true); mostlyCloudy(pt); partlyCloudy(pt); break;
            case 7: case 8: bgClouds(pt); mostlyCloudy(pt); partlyCloudy(pt); break;
            case 11: fog(pt); break;
            case 12: case 13: case 39: case 40: showers(pt); rainClouds(pt); break;
            case 14: showers(pt); partlyCloudyScene(pt); break;
            case 15: case 16: case 17: case 41: case 42: thunderstorm(pt); break;
            case 18: showers(pt); rain(pt); rainClouds(pt); break;
            case 19: case 20: case 43: case 44:
            case 21: // partly sunny w/ flurries: no scene in the original, use flurries
                flurries(pt); break;
            case 22: case 23: snow(pt); flurries(pt); break;
            case 24: case 31: icy(pt); break;
            case 25: case 26: case 29: showers(pt); snow(pt); rainClouds(pt); break;
            case 32: windy(pt); break;
            case 33: moon(pt); break;
            case 34: case 35: case 36: bgClouds(pt); moon(pt); partlyCloudy(pt); break;
            case 37: moon(pt); haze(pt, "haze_night_clouds", -3f); break;
            case 38: bgClouds(pt); moon(pt); mostlyCloudy(pt); partlyCloudy(pt); break;
            default: break;
        }
    }

    private static final float SKY = -H * 0.349f; // the scene's centre line

    private static int cosA(double base, double amp, double speed) {
        return (int) (base + amp * Math.cos(T * speed));
    }

    private static void partlyCloudyScene(Painter pt) {
        bgClouds(pt);
        sunny(pt, true);
        partlyCloudy(pt);
    }

    private static void sunny(Painter pt, boolean weak) {
        int a1, a2, fat;
        if (weak) {
            a1 = (int) ((Math.cos(T * 0.001d) * 57d) + 57d);
            a2 = 114 - a1;
            fat = 130;
        } else {
            a1 = (int) ((Math.cos(T * 0.001d) * 127d) + 127d);
            a2 = 255 - a1;
            fat = 255;
        }
        pt.image("sun_rays_long", 0, SKY, fat, 0, 0, T * -0.006f, 1f);
        pt.image("sun_sun", 0, SKY, 255);
        pt.image("sun_rays_1", 0, SKY, a1, 0, 0, T * -0.006f, 1f);
        pt.image("sun_rays_2", 0, SKY, a2, 0, 0, T * -0.004f, 1f);
    }

    private static void bgClouds(Painter pt) {
        int a = (int) ((Math.cos(T * 5.0E-4d) * 127d) + 127d);
        pt.image("clouds_backdrop", -3f, SKY, a);
        pt.image("clouds_backdrop", 3f, SKY, 255 - a);
    }

    private static void partlyCloudy(Painter pt) {
        int a1 = cosA(155, 100, 0.0019), a2 = cosA(155, 100, 0.0015);
        pt.image("clouds_light_layer_1", -W * 0.114f, -H * 0.279f, a1);
        pt.image("cloud_light_layer_4", W * 0.114f, -H * 0.256f, a2);
        pt.image("clouds_light_layer_1", -W * 0.136f, -H * 0.279f, 255 - a1);
        pt.image("cloud_light_layer_4", W * 0.136f, -H * 0.256f, 255 - a2);
    }

    private static void mostlyCloudy(Painter pt) {
        int a1 = cosA(130, 125, 0.0013), a2 = cosA(130, 125, 0.0011), a3 = cosA(130, 125, 0.0015);
        pt.image("clouds_light_layer_2", -W * 0.114f, -H * 0.349f, a1);
        pt.image("cloud_light_layer_3", W * 0.182f, -H * 0.337f, a2);
        pt.image("clouds_light_layer_2", -W * 0.15f, -H * 0.349f, 255 - a1);
        pt.image("cloud_light_layer_3", W * 0.218f, -H * 0.337f, 255 - a2);
        pt.image("clouds_light_layer_2", -W * 0.114f, -H * 0.342f, a3);
        pt.image("cloud_light_layer_3", W * 0.182f, -H * 0.353f, a3);
        pt.image("clouds_light_layer_2", -W * 0.15f, -H * 0.342f, 255 - a3);
        pt.image("cloud_light_layer_3", W * 0.218f, -H * 0.353f, 255 - a3);
    }

    private static void haze(Painter pt, String res, float firstX) {
        int a = (int) ((Math.cos(T * 0.001d) * 127d) + 127d);
        pt.image(res, firstX, SKY, a);
        pt.image(res, -firstX, SKY, 255 - a);
    }

    private static void rainClouds(Painter pt) {
        pt.image("rain_clouds_back", -W * 0.045f, -H * 0.395f, 255);
        pt.image("rain_clouds_front", W * 0.182f, SKY, 255);
    }

    /** fade-in, hold, fade-out alpha of a falling layer */
    private static int fall(long elapsed, long duration, long fade) {
        if (elapsed < fade) return (int) ((255 * elapsed) / fade);
        if (elapsed > duration - fade) return (int) ((255 * (duration - elapsed)) / fade);
        return 255;
    }

    private static void showers(Painter pt) {
        long now = (long) T;
        long e1 = now % 3000, e3 = (1500 + now) % 3000;
        pt.image("rain_backdrop", 0, SKY, 255);
        pt.image("rain_layer_2", 0, -30f + (25f * e1) / 3000f, fall(e1, 3000, 1000));
        pt.image("rain_small_layer_1", 0, -30f + (25f * e1) / 3000f, fall(e1, 3000, 1000));
        pt.image("rain_layer_3", 0, -30f + (25f * e3) / 3000f, fall(e3, 3000, 1000));
        pt.image("rain_small_layer_2", 0, -30f + (25f * e3) / 3000f, fall(e3, 3000, 1000));
    }

    private static void rain(Painter pt) {
        long now = (long) T;
        long[] e = {now % 2000, (1000 + now) % 2000, now % 2000, (1000 + now) % 2000};
        String[] img = {"rain_layer_1", "rain_layer_1", "rain_layer_2", "rain_layer_2"};
        pt.image("rain_backdrop", 0, SKY, 255);
        for (int i = 0; i < 4; i++) {
            pt.image(img[i], 0, -30f + (35f * e[i]) / 2000f, fall(e[i], 2000, 666));
        }
    }

    private static void snow(Painter pt) {
        long now = (long) T;
        long[] e = {now % 3000, (now + 1500) % 3000};
        String[] img = {"snow_layer_3", "snow_layer_4"};
        int[] off = {3, -3};
        for (int i = 0; i < 2; i++) {
            int a = e[i] < 1500 ? (int) ((255 * e[i]) / 1500) : (int) ((255 * (3000 - e[i])) / 1500);
            pt.image(img[i], off[i], -30f + (25f * e[i]) / 3000f, a);
        }
    }

    /** Flurries: 6 flakes, first frame after init (flake i is i/6 into its fall); fixed seed. */
    private static void flurries(Painter pt) {
        Random r = new Random(2011);
        String[] img = {"snowflake_1", "snowflake_2", "snowflake_3", "snowflake_4", "snowflake_5", "snowflake_6"};
        for (int i = 0; i < 6; i++) {
            float xRot = (r.nextFloat() * 0.4f * 90f) + 0.3f;
            float yRot = (0.6f * r.nextFloat() * 90f) + 0.3f;
            float zRot = 1f + (3f * r.nextFloat() * -90f);
            float size = 0.1f + (r.nextFloat() * 0.4f);
            float startX = (-1f + 2f * r.nextFloat()) * 90f;
            float dir = (startX > 0 ? -1 : 1) * r.nextFloat();
            long elapsed = (i * 2000L) / 6;
            float prog = elapsed / 2000f;
            int a = fall(elapsed, 2000, 666);
            if (i == 0) a = 140; // first flake would be fully transparent on its first frame
            pt.image(img[i], startX + dir * 80f * prog, -180f + 120f * prog, a,
                    xRot * prog, yRot * prog, zRot * prog, size);
        }
    }

    private static void icy(Painter pt) {
        pt.image("frost", 0, -H * 0.14f, 255);
        pt.image("snowflake_1", 0, SKY, 255,
                (float) (20d * Math.cos(T * 6.0E-4d)), (float) (30d * Math.cos(T * 8.0E-4d)), T * 0.008f, 1f);
    }

    private static void fog(Painter pt) {
        float elapsed = (T / 3000f) % 1f;
        if (elapsed == 0f) elapsed = 1f;
        int a1 = cosA(130, 125, 0.0013), a2 = cosA(130, 125, 0.0011);
        pt.imageScaled("searchlight", 0.85f, -W * 0.2f, -H * 0.33f, 255, 0, -elapsed * 360f, 0);
        pt.image("lighthouse", -W * 0.2f, -H * 0.25f, 255);
        // highlights: alpha is 0 at this phase
        pt.image("fog_1", -W * 0.06f, -H * 0.18f, a1);
        pt.image("fog_1", W * 0.06f, -H * 0.22f, 255 - a1);
        pt.image("fog_2", -W * 0.06f, -H * 0.18f, a2);
        pt.image("fog_2", W * 0.06f, -H * 0.22f, 255 - a2);
    }

    /** Thunderstorm: frozen on the first lightning flash (lightning_01 + its highlights at full). */
    private static void thunderstorm(Painter pt) {
        pt.image("thunder_clouds_back", 0, -H * 0.395f, 255);
        pt.image("thunder_clouds_back_highlight", 0, -H * 0.395f, 255);
        pt.image("lightning_01", 0, -H * 0.279f, 255);
        pt.image("lightning_highlight_3", 10f, -H * 0.372f, 255);
        pt.image("thunder_clouds_front", 30f, SKY, 255);
        pt.image("lightning_highlight_2", -40f, SKY, 255);
    }

    private static void windy(Painter pt) {
        pt.image("windy_06", 0, -H * 0.209f, 255);
    }

    private static void moon(Painter pt) {
        pt.image("night_stars", -10f, SKY, 255);
        float[][] stars = { // x, y, large?, alpha speed (0 = steady)
                {W / 4f, -H * 0.442f, 1, 0.0016f}, {-W * 0.191f, -H * 0.256f, 0, 0},
                {-W * 0.409f, -H * 0.14f, 0, 0}, {W * 0.045f, -H * 0.186f, 1, 0},
                {W * 0.155f, -H * 0.221f, 1, 0.0019f}, {W * 0.227f, -H * 0.14f, 0, 0},
                {-W / 4f, -H * 0.163f, 1, 0.0017f}, {W * 0.409f, -H * 0.198f, 1, 0.0018f}};
        for (float[] s : stars) {
            int a = s[3] == 0 ? 255 : cosA(130, 125, s[3]);
            if (s[2] == 1) {
                pt.image("night_single_star", s[0], s[1], a);
            } else {
                pt.imageScaled("night_single_star", 2f / 3f, s[0], s[1], a, 0, 0, 0);
            }
        }
        pt.image("moon_shadow", 0, SKY, cosA(170, 85, 8.0E-4));
        pt.image("moon", 0, SKY, 255);
    }

    // ================================================================= Sony uicomponent, ported

    /** Draws like Sony's Image / Label (positions relative to the plate centre). */
    private static final class Painter {
        private final Context ctx;
        private final Canvas canvas;
        private final HashMap<String, Bitmap> cache = new HashMap<String, Bitmap>();
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Camera camera = new Camera();
        private final Matrix matrix = new Matrix();
        private final float cx = MX + W / 2f, cy = MT + H / 2f;

        Painter(Context ctx, Canvas canvas) {
            this.ctx = ctx;
            this.canvas = canvas;
        }

        Bitmap bmp(String name) {
            Bitmap b = cache.get(name);
            if (b == null) {
                int id = ctx.getResources().getIdentifier("xp_" + name, "drawable", ctx.getPackageName());
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inScaled = false; // Sony's hdpi pixels, whatever the screen
                b = BitmapFactory.decodeResource(ctx.getResources(), id, o);
                cache.put(name, b);
            }
            return b;
        }

        void recycle() {
            for (Bitmap b : cache.values()) {
                if (b != null) b.recycle();
            }
            cache.clear();
        }

        void image(String name, float x, float y, int alpha) {
            image(name, x, y, alpha, 0, 0, 0, 1f);
        }

        /** setPosition(x, y, pivotX, pivotY) on a centre-pivot image */
        void imageAnchored(String name, float x, float y, float px, float py) {
            Bitmap b = bmp(name);
            image(name, x - (px - 0.5f) * b.getWidth(), y - (py - 0.5f) * b.getHeight(), 255);
        }

        /** loadScaledBitmapIfNeeded(res, id, s, s): a pre-scaled copy */
        void imageScaled(String name, float s, float x, float y, int alpha, float rx, float ry, float rz) {
            String key = name + "@" + s;
            Bitmap b = cache.get(key);
            if (b == null) {
                Bitmap src = bmp(name);
                b = Bitmap.createScaledBitmap(src, Math.max(1, (int) (src.getWidth() * s)),
                        Math.max(1, (int) (src.getHeight() * s)), true);
                cache.put(key, b);
            }
            draw(b, x, y, alpha, rx, ry, rz, 1f);
        }

        void image(String name, float x, float y, int alpha, float rx, float ry, float rz, float scale) {
            if (name == null) return;
            draw(bmp(name), x, y, alpha, rx, ry, rz, scale);
        }

        /** uicomponent.Image.onDraw(), including its Camera rotation about the image centre */
        private void draw(Bitmap b, float x, float y, int alpha, float rx, float ry, float rz, float scale) {
            if (b == null || alpha <= 0) return;
            float w = b.getWidth() * scale, h = b.getHeight() * scale;
            float left = cx + x - w / 2f, top = cy + y - h / 2f;
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            paint.setAlpha(Math.min(255, alpha));
            if (rx == 0 && ry == 0 && rz == 0 && scale == 1f) {
                canvas.drawBitmap(b, left, top, paint);
                return;
            }
            matrix.reset();
            if (rx != 0 || ry != 0 || rz != 0) {
                camera.save();
                camera.rotateZ(rz);
                camera.rotateY(ry);
                camera.rotateX(rx);
                camera.getMatrix(matrix);
                camera.restore();
                matrix.preTranslate(-w / 2f, -h / 2f);
                matrix.postTranslate(w / 2f + left, h / 2f + top);
            } else {
                matrix.postTranslate(left, top);
            }
            if (scale != 1f) {
                matrix.preScale(scale, scale);
            }
            canvas.drawBitmap(b, matrix, paint);
        }

        /** WeatherWidgetView.getTextSize(): largest size <= max that fits the plate width - 20 */
        int fit(String s, int max) {
            for (int size = max; size > 0; size--) {
                text.setTextSize(size);
                if (text.measureText(s) <= W - 20f) return size;
            }
            return 1;
        }

        /** centre-pivot label at (x, y) */
        void label(String s, int size, int color, float x, float y) {
            labelPivot(s, size, color, x + 0f, y, 0.5f);
        }

        /** label with setPivotPosition(px, 0.5) */
        void labelPivot(String s, int size, int color, float x, float y, float px) {
            if (s == null || s.length() == 0) return;
            text.setTextSize(size);
            text.setColor(color);
            int ascent = (int) Math.ceil(-text.ascent()), descent = (int) Math.ceil(text.descent());
            float w = (float) Math.ceil(text.measureText(s)), h = ascent + descent;
            float left = cx + x - w * px, top = cy + y - h / 2f;
            canvas.drawText(s, left, top + ascent, text);
        }

        /** setPosition(x, y, px, py) before the text was set (box measured at the default size) */
        void labelAnchored(String s, int size, int color, float x, float y, float px, float py) {
            text.setTextSize(DEFAULT_FONT_SIZE);
            float h0 = (float) (Math.ceil(-text.ascent()) + Math.ceil(text.descent()));
            label(s, size, color, x, y - (py - 0.5f) * h0);
        }
    }
}
