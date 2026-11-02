package com.ntvdm.clockwidgets.weather;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.ntvdm.clockwidgets.MainActivity;
import com.ntvdm.clockwidgets.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Weather Clock widgets (HTC / Clean / Galaxy S3 / Galaxy S4 / Chronus) backed by Open-Meteo.
 *
 * This class is also the "HTC" widget receiver; the other three styles are empty subclasses
 * so each gets its own entry in the widget picker. Settings (API key, units, location) are
 * set up in the app's main screen ({@link WeatherSetup}) and shared by every weather widget,
 * so one fetch updates all of them.
 */
public class WeatherWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "WeatherClock";

    static final String ACTION_REFRESH = "com.ntvdm.clockwidgets.weather.REFRESH";
    static final String ACTION_TICK = "com.ntvdm.clockwidgets.weather.TICK";

    static final String PREFS = "WeatherPrefs";
    // settings
    static final String KEY_MODE = "mode";          // MODE_GPS / MODE_FIXED, unset = not set up
    static final String KEY_LAT = "lat";
    static final String KEY_LON = "lon";
    static final String KEY_NAME = "name";
    static final String KEY_FAHRENHEIT = "fahrenheit";
    static final String KEY_CHRONUS_ALT_ICONS = "chronus_alt_icons";
    // last fetched weather
    static final String KEY_TEMP = "temp";
    static final String KEY_LOW = "low";
    static final String KEY_HIGH = "high";
    static final String KEY_ICON = "icon";
    static final String KEY_SUMMARY = "summary";
    static final String KEY_UPDATED = "updated";

    static final String MODE_FIXED = "fixed";
    static final String MODE_GPS = "gps";

    static final int STYLE_HTC = 0;
    static final int STYLE_CLEAN = 1;
    static final int STYLE_S4 = 2;
    static final int STYLE_S3 = 3;
    static final int STYLE_CHRONUS = 4;
    static final int STYLE_GALAXY_S = 5;

    /** true while a fetch runs (the Galaxy S clock spins its refresh button meanwhile) */
    static volatile boolean sFetching;

    static final String ACTION_NEXT_ALARM_CHANGED = "android.app.action.NEXT_ALARM_CLOCK_CHANGED"; // 5.0+
    static final String ACTION_ALARM_CHANGED_LEGACY = "android.intent.action.ALARM_CHANGED";

    /** Re-geocode in GPS mode only if we moved more than this. */
    private static final float GPS_MOVE_THRESHOLD_M = 1000f;

    private static final String[] CLOCK_PACKAGES = {
            "com.htc.android.worldclock",
            "com.android.deskclock",
            "com.google.android.deskclock",
            "com.sec.android.app.clockpackage"
    };

    private static final int PI_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT
            | (Build.VERSION.SDK_INT >= 23 ? 0x04000000 /* FLAG_IMMUTABLE */ : 0);

    // ---------------------------------------------------------------- prefs helpers

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isConfigured(SharedPreferences p) {
        return p.getString(KEY_MODE, null) != null;
    }

    /** Redraw every weather widget from cached data (no network), e.g. after a look-only setting. */
    static void redrawAll(Context context) {
        AppWidgetManager awm = AppWidgetManager.getInstance(context);
        for (int id : allIds(context)) {
            render(context, awm, id);
        }
    }

    /** Re-fetch weather and redraw every weather widget. */
    public static void requestRefresh(Context context) {
        context.sendBroadcast(new Intent(context, WeatherWidgetProvider.class).setAction(ACTION_REFRESH));
    }

    // ---------------------------------------------------------------- broadcasts

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (ACTION_TICK.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || "android.intent.action.MY_PACKAGE_REPLACED".equals(action)) {
            int[] ids = tickingIds(context);
            if (ids.length > 0) {
                AppWidgetManager awm = AppWidgetManager.getInstance(context);
                for (int id : ids) {
                    render(context, awm, id);
                }
                scheduleTick(context);
            }
            return;
        }

        if (ACTION_REFRESH.equals(action)) {
            refreshAsync(context, allIds(context));
            return;
        }

        if (ACTION_NEXT_ALARM_CHANGED.equals(action) || ACTION_ALARM_CHANGED_LEGACY.equals(action)) {
            // Chronus shows the next alarm next to the date
            AppWidgetManager awm = AppWidgetManager.getInstance(context);
            for (int id : idsOf(context, ChronusWidgetProvider.class)) {
                render(context, awm, id);
            }
            return;
        }

        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        refreshAsync(context, appWidgetIds);
    }

    /** Resized (4.1+): Chronus scales its clock and hides the weather panel when too small. */
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager awm, int id, Bundle newOptions) {
        render(context, awm, id);
    }

    @Override
    public void onDisabled(Context context) {
        if (tickingIds(context).length == 0) {
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).cancel(tickIntent(context));
        }
    }

    // ---------------------------------------------------------------- widget id bookkeeping

    static int[] allIds(Context context) {
        return concat(idsOf(context, WeatherWidgetProvider.class), idsOf(context, CleanWidgetProvider.class),
                idsOf(context, S4WidgetProvider.class), idsOf(context, S3WidgetProvider.class),
                idsOf(context, ChronusWidgetProvider.class), idsOf(context, GalaxySWidgetProvider.class));
    }

    private static int[] idsOf(Context context, Class<?> cls) {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, cls));
    }

    private static int[] concat(int[]... arrays) {
        int n = 0;
        for (int[] a : arrays) n += a.length;
        int[] out = new int[n];
        int pos = 0;
        for (int[] a : arrays) {
            System.arraycopy(a, 0, out, pos, a.length);
            pos += a.length;
        }
        return out;
    }

    static int style(AppWidgetManager awm, int widgetId) {
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(widgetId);
        String cls = info == null ? "" : info.provider.getClassName();
        if (CleanWidgetProvider.class.getName().equals(cls)) return STYLE_CLEAN;
        if (S4WidgetProvider.class.getName().equals(cls)) return STYLE_S4;
        if (S3WidgetProvider.class.getName().equals(cls)) return STYLE_S3;
        if (ChronusWidgetProvider.class.getName().equals(cls)) return STYLE_CHRONUS;
        if (GalaxySWidgetProvider.class.getName().equals(cls)) return STYLE_GALAXY_S;
        return STYLE_HTC;
    }

    /** true below API 17 (no TextClock) - then every style needs a per-minute tick. */
    static boolean manualClock(Context context) {
        return context.getResources().getBoolean(R.bool.manual_clock);
    }

    /** Widgets whose clock we draw ourselves: S3/S4 always (image digits), all of them pre-4.2. */
    private static int[] tickingIds(Context context) {
        if (manualClock(context)) {
            return allIds(context);
        }
        // Galaxy S: hands are an AnalogClock, but the day-of-month box needs a nudge too
        return concat(idsOf(context, S4WidgetProvider.class), idsOf(context, S3WidgetProvider.class),
                idsOf(context, GalaxySWidgetProvider.class));
    }

    private static PendingIntent tickIntent(Context context) {
        Intent i = new Intent(context, WeatherWidgetProvider.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(context, 0, i, PI_FLAGS);
    }

    private static void scheduleTick(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long nextMinute = (System.currentTimeMillis() / 60000 + 1) * 60000;
        if (Build.VERSION.SDK_INT >= 19) {
            am.setExact(AlarmManager.RTC, nextMinute, tickIntent(context));
        } else {
            am.set(AlarmManager.RTC, nextMinute, tickIntent(context));
        }
    }

    // ---------------------------------------------------------------- fetching

    private void refreshAsync(Context context, final int[] ids) {
        final Context app = context.getApplicationContext();
        final AppWidgetManager awm = AppWidgetManager.getInstance(app);

        if (tickingIds(app).length > 0) {
            scheduleTick(app);
        }
        if (ids.length == 0) {
            return;
        }
        // draw immediately with cached data, then fetch in the background
        sFetching = true;
        for (int id : ids) {
            render(app, awm, id);
        }

        // goAsync() is API 11+; on 2.3 the thread just runs in our process
        final BroadcastReceiver.PendingResult pending = Build.VERSION.SDK_INT >= 11 ? goAsync() : null;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        fetch(app);
                    } catch (Exception e) {
                        Log.w(TAG, "Weather update failed", e);
                    }
                    sFetching = false;
                    for (int id : ids) {
                        render(app, awm, id);
                    }
                } finally {
                    sFetching = false;
                    if (pending != null) {
                        pending.finish();
                    }
                }
            }
        }).start();
    }

    /** One fetch for all widgets. Blocking - call off the main thread. */
    static void fetch(Context context) throws Exception {
        SharedPreferences p = prefs(context);
        String mode = p.getString(KEY_MODE, null);
        if (mode == null) {
            return; // not set up yet
        }

        double lat = Double.parseDouble(p.getString(KEY_LAT, "0"));
        double lon = Double.parseDouble(p.getString(KEY_LON, "0"));
        String name = p.getString(KEY_NAME, "");

        if (MODE_GPS.equals(mode)) {
            Location here = Locator.lastKnown(context);
            if (here != null) {
                float[] dist = new float[1];
                Location.distanceBetween(lat, lon, here.getLatitude(), here.getLongitude(), dist);
                if (dist[0] > GPS_MOVE_THRESHOLD_M) {
                    lat = here.getLatitude();
                    lon = here.getLongitude();
                    name = ""; // moved: forget the old place name
                }
            }
            if (name.length() == 0) {
                String place = Locator.placeName(context, lat, lon);
                if (place != null) {
                    name = place;
                }
            }
        }

        OpenMeteo.Current c = OpenMeteo.fetch(lat, lon, p.getBoolean(KEY_FAHRENHEIT, false));
        if (name.length() == 0) {
            name = WeatherIcons.cityFromTimezone(c.timezone);
        }

        p.edit()
                .putString(KEY_LAT, Double.toString(lat))
                .putString(KEY_LON, Double.toString(lon))
                .putString(KEY_NAME, name)
                .putFloat(KEY_TEMP, (float) c.temperature)
                .putFloat(KEY_LOW, (float) c.low)
                .putFloat(KEY_HIGH, (float) c.high)
                .putString(KEY_ICON, c.icon)
                .putString(KEY_SUMMARY, c.summary)
                .putLong(KEY_UPDATED, System.currentTimeMillis())
                .commit();
    }

    // ---------------------------------------------------------------- rendering

    static void render(Context context, AppWidgetManager awm, int id) {
        SharedPreferences p = prefs(context);
        int style = style(awm, id);
        boolean htc = style == STYLE_HTC;
        boolean configured = isConfigured(p);
        boolean hasData = configured && p.contains(KEY_TEMP);

        RemoteViews rv;
        if (style == STYLE_GALAXY_S) {
            rv = GalaxyS.render(context, p, sFetching);
        } else if (style == STYLE_CHRONUS) {
            rv = Chronus.render(context, awm, id, p);
        } else if (style == STYLE_S4 || style == STYLE_S3) {
            boolean s3 = style == STYLE_S3;
            rv = new RemoteViews(context.getPackageName(), s3 ? R.layout.weather_widget_s3 : R.layout.weather_widget_s4);
            renderTouchWiz(context, p, rv, s3, configured, hasData);
        } else {
            rv = new RemoteViews(context.getPackageName(), htc ? R.layout.weather_widget_htc : R.layout.weather_widget_clean);
            renderClassic(context, p, rv, htc, configured, hasData);
        }
        setClicks(context, id, rv, htc, configured);
        awm.updateAppWidget(id, rv);
    }

    private static void renderTouchWiz(Context context, SharedPreferences p, RemoteViews rv,
                                       boolean s3, boolean configured, boolean hasData) {
        SamsungTheme theme = s3 ? SamsungTheme.S3 : SamsungTheme.S4;
        Date now = new Date();
        int code = hasData ? WeatherIcons.accuCode(p.getString(KEY_ICON, null)) : 0;

        rv.setImageViewBitmap(R.id.tw_time, theme.renderTime(context,
                new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), s3 ? 62f : 52f));
        rv.setTextViewText(R.id.tw_date,
                new SimpleDateFormat(s3 ? "EEE, d MMM" : "EEE, d MMMM", Locale.getDefault()).format(now));

        Uri framed = s3 ? FramedBackground.s3(context, theme.background(context, code),
                code > 0 ? Integer.toString(code) : "na") : null;
        if (framed != null) {
            rv.setImageViewUri(R.id.tw_bg, framed);
        } else {
            rv.setImageViewResource(R.id.tw_bg, theme.background(context, code));
        }

        if (hasData) {
            rv.setImageViewResource(R.id.widget_weather_icon, theme.icon(context, code));
            long temp = Math.round(p.getFloat(KEY_TEMP, 0f));
            String unit = p.getBoolean(KEY_FAHRENHEIT, false) ? "°F" : "°C";
            if (s3) {
                rv.setTextViewText(R.id.widget_weather_temp, Long.toString(temp));
                rv.setTextViewText(R.id.s3_unit_degree, "°");
                rv.setTextViewText(R.id.s3_unit, unit.substring(1));
            } else {
                rv.setTextViewText(R.id.widget_weather_temp, temp + unit);
            }
            rv.setTextViewText(R.id.tw_condition, sentenceCase(p.getString(KEY_SUMMARY, "")));
            long updated = p.getLong(KEY_UPDATED, 0L);
            rv.setTextViewText(R.id.tw_updated, updated == 0 ? "" :
                    new SimpleDateFormat(s3 ? "dd/MM/yyyy HH:mm" : "dd/MM HH:mm", Locale.getDefault())
                            .format(new Date(updated)));
        } else {
            rv.setTextViewText(R.id.widget_weather_temp, "");
            if (s3) {
                rv.setTextViewText(R.id.s3_unit_degree, "");
                rv.setTextViewText(R.id.s3_unit, "");
            }
            rv.setTextViewText(R.id.tw_condition, "");
            rv.setTextViewText(R.id.tw_updated, "");
        }
        rv.setViewVisibility(R.id.widget_weather_icon, hasData ? View.VISIBLE : View.INVISIBLE);
        rv.setTextViewText(R.id.widget_weather_place, configured
                ? p.getString(KEY_NAME, "")
                : context.getString(R.string.tap_to_setup_clean));
    }

    static String sentenceCase(String s) {
        if (s.length() == 0) return s;
        return s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1).toLowerCase(Locale.getDefault());
    }

    private static void renderClassic(Context context, SharedPreferences p, RemoteViews rv,
                                      boolean htc, boolean configured, boolean hasData) {
        if (manualClock(context)) {
            // pre-4.2: no TextClock, push time/date ourselves (layout/ vs layout-v17/)
            Date now = new Date();
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
            if (htc) {
                rv.setImageViewBitmap(R.id.widget_time_image, TimeRenderer.render(context, time));
                SpannableString date = new SpannableString(new SimpleDateFormat("dd/MM", Locale.getDefault()).format(now));
                date.setSpan(new StyleSpan(Typeface.BOLD), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                rv.setTextViewText(R.id.widget_date_text, date);
            } else {
                rv.setTextViewText(R.id.widget_time_text, time);
                rv.setTextViewText(R.id.widget_date_text,
                        new SimpleDateFormat("EEE, MMMM dd", Locale.getDefault()).format(now));
            }
        }

        if (!htc) {
            // Clean
            rv.setTextViewText(R.id.widget_weather_place, configured
                    ? p.getString(KEY_NAME, "")
                    : context.getString(R.string.tap_to_setup_clean));
            if (hasData) {
                rv.setTextViewText(R.id.widget_weather_temp, Math.round(p.getFloat(KEY_TEMP, 0f)) + "°");
                rv.setImageViewResource(R.id.widget_weather_icon, WeatherIcons.iconRes(p.getString(KEY_ICON, null)));
            }
            return;
        }

        // HTC
        if (configured) {
            String place = p.getString(KEY_NAME, "").toUpperCase(Locale.getDefault());
            if (hasData) {
                String temp = Math.round(p.getFloat(KEY_TEMP, 0f)) + "°"
                        + (p.getBoolean(KEY_FAHRENHEIT, false) ? "F" : "C");
                if (place.length() > 0) {
                    temp = ", " + temp;
                }
                rv.setTextViewText(R.id.widget_weather_temp, temp);
                rv.setImageViewResource(R.id.widget_weather_icon, WeatherIcons.iconRes(p.getString(KEY_ICON, null)));
            }
            rv.setTextViewText(R.id.widget_weather_place, place);
        } else {
            rv.setTextViewText(R.id.widget_weather_place, context.getString(R.string.tap_to_setup));
        }
        rv.setViewVisibility(R.id.widget_weather_icon, hasData ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.widget_weather_temp, hasData ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.widget_no_data, hasData ? View.GONE : View.VISIBLE);
    }

    private static void setClicks(Context context, int id, RemoteViews rv, boolean htc, boolean configured) {
        // place name -> the app's weather setup
        Intent setup = new Intent(context, MainActivity.class)
                .setAction(WeatherSetup.ACTION_SHOW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent setupPi = PendingIntent.getActivity(context, 0, setup, PI_FLAGS);
        rv.setOnClickPendingIntent(R.id.widget_weather_place, setupPi);

        // weather box -> refresh (or set up if not done yet)
        if (configured) {
            Intent refresh = new Intent(context, WeatherWidgetProvider.class).setAction(ACTION_REFRESH);
            rv.setOnClickPendingIntent(R.id.widget_weather_box, PendingIntent.getBroadcast(context, 0, refresh, PI_FLAGS));
        } else {
            rv.setOnClickPendingIntent(R.id.widget_weather_box, setupPi);
        }

        // clock -> alarms / clock app
        Intent clock = clockIntent(context);
        if (clock != null) {
            PendingIntent clockPi = PendingIntent.getActivity(context, 1, clock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PI_FLAGS);
            rv.setOnClickPendingIntent(R.id.widget_clock_box, clockPi);
            if (htc) {
                rv.setOnClickPendingIntent(manualClock(context) ? R.id.widget_time_image : R.id.widget_time_text, clockPi);
            }
        }
    }

    private static Intent clockIntent(Context context) {
        if (Build.VERSION.SDK_INT >= 19) {
            return new Intent("android.intent.action.SHOW_ALARMS");
        }
        PackageManager pm = context.getPackageManager();
        for (String pkg : CLOCK_PACKAGES) {
            Intent i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) {
                return i;
            }
        }
        return null;
    }
}
