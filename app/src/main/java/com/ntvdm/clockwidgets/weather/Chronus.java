package com.ntvdm.clockwidgets.weather;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import com.ntvdm.clockwidgets.R;

import java.util.Date;
import java.util.Locale;

/**
 * Renders the Chronus (com.cyanogenmod.lockclock) widget, following its ClockWidgetService
 * with the default settings: bold hour, thin minutes, bold date, next alarm shown,
 * location + timestamp shown, "low | high", default (colour) icon set.
 */
@TargetApi(17)
final class Chronus {

    private Chronus() {
    }

    static RemoteViews render(Context context, AppWidgetManager awm, int id, SharedPreferences p) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.weather_widget_chronus);

        // clock shrinks on narrow widgets, weather hides on short ones (WidgetUtils in Chronus)
        Bundle opts = awm.getAppWidgetOptions(id);
        float size = context.getResources().getDimension(R.dimen.chronus_big_font_size) * scaleRatio(context, opts);
        rv.setTextViewTextSize(R.id.chronus_hour, TypedValue.COMPLEX_UNIT_PX, size);
        rv.setTextViewTextSize(R.id.chronus_minute, TypedValue.COMPLEX_UNIT_PX, size);
        rv.setViewVisibility(R.id.widget_weather_box, canFitWeather(context, opts) ? View.VISIBLE : View.GONE);

        String alarm = nextAlarm(context);
        if (alarm != null) {
            rv.setTextViewText(R.id.chronus_next_alarm, alarm.toUpperCase(Locale.getDefault()));
            rv.setViewVisibility(R.id.chronus_next_alarm, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.chronus_next_alarm, View.GONE);
        }

        boolean configured = WeatherWidgetProvider.isConfigured(p);
        boolean hasData = configured && p.contains(WeatherWidgetProvider.KEY_TEMP);
        if (hasData) {
            int code = WeatherIcons.yahooCode(p.getString(WeatherWidgetProvider.KEY_ICON, null));
            rv.setImageViewResource(R.id.widget_weather_icon, icon(context, code, altIcons(p)));
            rv.setTextViewText(R.id.widget_weather_place, p.getString(WeatherWidgetProvider.KEY_NAME, ""));
            rv.setTextViewText(R.id.chronus_condition, p.getString(WeatherWidgetProvider.KEY_SUMMARY, ""));

            long updated = p.getLong(WeatherWidgetProvider.KEY_UPDATED, 0L);
            long now = System.currentTimeMillis();
            CharSequence ts = now - updated < 60000
                    ? context.getString(R.string.chronus_just_now)
                    : DateUtils.getRelativeTimeSpanString(updated, now, DateUtils.MINUTE_IN_MILLIS);
            rv.setTextViewText(R.id.chronus_update_time, ts);
            rv.setViewVisibility(R.id.chronus_update_time, View.VISIBLE);

            String unit = p.getBoolean(WeatherWidgetProvider.KEY_FAHRENHEIT, false) ? "F" : "C";
            rv.setTextViewText(R.id.widget_weather_temp, Math.round(p.getFloat(WeatherWidgetProvider.KEY_TEMP, 0f)) + "°" + unit);
            rv.setTextViewText(R.id.chronus_low_high,
                    deg(p.getFloat(WeatherWidgetProvider.KEY_LOW, Float.NaN)) + " | "
                            + deg(p.getFloat(WeatherWidgetProvider.KEY_HIGH, Float.NaN)));
            rv.setViewVisibility(R.id.chronus_temps_panel, View.VISIBLE);
        } else {
            // Chronus' setNoWeatherData()
            rv.setImageViewResource(R.id.widget_weather_icon, icon(context, -1, altIcons(p)));
            rv.setTextViewText(R.id.widget_weather_place, context.getString(R.string.chronus_no_data));
            rv.setTextViewText(R.id.chronus_condition, context.getString(
                    configured ? R.string.chronus_tap_to_refresh : R.string.chronus_tap_to_setup));
            rv.setViewVisibility(R.id.chronus_update_time, View.GONE);
            rv.setViewVisibility(R.id.chronus_temps_panel, View.GONE);
        }
        return rv;
    }

    private static String deg(float v) {
        return Float.isNaN(v) ? "--°" : Math.round(v) + "°";
    }

    /** Chronus' "Use alternate icons" setting: colour weather2_ set instead of the white weather_ set. */
    static boolean altIcons(SharedPreferences p) {
        return p.getBoolean(WeatherWidgetProvider.KEY_CHRONUS_ALT_ICONS, false);
    }

    private static int icon(Context context, int code, boolean alt) {
        String prefix = alt ? "chronus_weather2_" : "chronus_weather_";
        int id = code < 0 ? 0 : context.getResources().getIdentifier(prefix + code, "drawable",
                context.getPackageName());
        if (id != 0) {
            return id;
        }
        return alt ? R.drawable.chronus_weather2_na : R.drawable.chronus_weather_na;
    }

    private static float scaleRatio(Context context, Bundle opts) {
        int minWidth = opts == null ? 0 : opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        if (minWidth == 0) {
            return 1f;
        }
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, minWidth,
                context.getResources().getDisplayMetrics());
        return Math.min(1f, px / context.getResources().getDimension(R.dimen.chronus_def_width));
    }

    private static boolean canFitWeather(Context context, Bundle opts) {
        int minHeight = opts == null ? 0 : opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        if (minHeight == 0) {
            return true;
        }
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, minHeight,
                context.getResources().getDisplayMetrics());
        return px > context.getResources().getDimension(R.dimen.chronus_min_weather_height);
    }

    /** Next alarm, e.g. "Tue 07:30" - AlarmManager on 5.0+, the old system setting before. */
    @SuppressWarnings("deprecation")
    private static String nextAlarm(Context context) {
        if (Build.VERSION.SDK_INT >= 21) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            AlarmManager.AlarmClockInfo info = am == null ? null : am.getNextAlarmClock();
            if (info == null) {
                return null;
            }
            String pattern = DateFormat.is24HourFormat(context) ? "EEE HH:mm" : "EEE h:mm aa";
            return DateFormat.format(pattern, new Date(info.getTriggerTime())).toString();
        }
        String s = Settings.System.getString(context.getContentResolver(), Settings.System.NEXT_ALARM_FORMATTED);
        return s == null || s.length() == 0 ? null : s;
    }
}
