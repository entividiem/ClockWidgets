package com.ntvdm.clockwidgets.weather;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.RemoteViews;

import com.ntvdm.clockwidgets.MainActivity;
import com.ntvdm.clockwidgets.R;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * LG Optimus (2012, "sizechangable" Weather 3.1) home-screen weather widgets: 4x2, 4x1 and 2x2.
 *
 * The layouts are LG's own (com.lge.sizechangable.weather.theme.optimus, weather_widget_*_normal,
 * portrait + landscape) converted to plain views; this fills them the way Weather4x2_Theme_optimus,
 * Weather4x1_Theme_optimus and Weather_Theme_optimus (2x2) did. LG Home animated the 4x2 portrait
 * background with its own view classes; everywhere else LG showed the still photos used here.
 */
final class Lg {

    private static final int SIZE_4X2 = 0, SIZE_4X1 = 1, SIZE_2X2 = 2;

    /** LG's TODAY_WEATHER_ICON animation / background types (Weather_Rsc_Constant) */
    private static final int CLEAR_DAY = 0, PARTLY_DAY = 1, CLOUDY = 2, RAINY = 3, THUNDER = 4,
            SNOWY = 5, CLEAR_NIGHT = 6, PARTLY_NIGHT = 7, WINDY = 8;

    private static final int[][] BACKGROUNDS = { // [type][size]: _l (4x2), _m (4x1), _s (2x2)
            {R.drawable.lg_bg_clear_day_l, R.drawable.lg_bg_clear_day_m, R.drawable.lg_bg_clear_day_s},
            {R.drawable.lg_bg_partial_cloud_day_l, R.drawable.lg_bg_partial_cloud_day_m, R.drawable.lg_bg_partial_cloud_day_s},
            {R.drawable.lg_bg_cloudy_l, R.drawable.lg_bg_cloudy_m, R.drawable.lg_bg_cloudy_s},
            {R.drawable.lg_bg_rainy_l, R.drawable.lg_bg_rainy_m, R.drawable.lg_bg_rainy_s},
            {R.drawable.lg_bg_thunderstorm_l, R.drawable.lg_bg_thunderstorm_m, R.drawable.lg_bg_thunderstorm_s},
            {R.drawable.lg_bg_snowy_l, R.drawable.lg_bg_snowy_m, R.drawable.lg_bg_snowy_s},
            {R.drawable.lg_bg_clear_night_l, R.drawable.lg_bg_clear_night_m, R.drawable.lg_bg_clear_night_s},
            {R.drawable.lg_bg_partial_cloud_night_l, R.drawable.lg_bg_partial_cloud_night_m, R.drawable.lg_bg_partial_cloud_night_s},
            {R.drawable.lg_bg_windy_l, R.drawable.lg_bg_windy_m, R.drawable.lg_bg_windy_s},
    };

    private Lg() {
    }

    static RemoteViews render(Context context, int style, SharedPreferences p, boolean fetching, int piFlags) {
        int size = style == WeatherWidgetProvider.STYLE_LG_4X1 ? SIZE_4X1
                : style == WeatherWidgetProvider.STYLE_LG_2X2 ? SIZE_2X2 : SIZE_4X2;
        int layout = size == SIZE_4X1 ? R.layout.weather_widget_lg_4x1
                : size == SIZE_2X2 ? R.layout.weather_widget_lg_2x2 : R.layout.weather_widget_lg_4x2;
        RemoteViews rv = new RemoteViews(context.getPackageName(), layout);
        boolean configured = WeatherWidgetProvider.isConfigured(p);
        boolean hasData = configured && p.contains(WeatherWidgetProvider.KEY_TEMP);
        boolean f = p.getBoolean(WeatherWidgetProvider.KEY_FAHRENHEIT, false);
        String icon = p.getString(WeatherWidgetProvider.KEY_ICON, null);

        // things LG only showed for multiple cities / weather warnings / TalkBack
        rv.setViewVisibility(R.id.lg_txt_currentCityIndex, View.GONE);
        rv.setViewVisibility(R.id.lg_widget_warning_icon, View.GONE);
        rv.setViewVisibility(R.id.lg_RelativeLayout01, View.GONE);
        rv.setViewVisibility(R.id.lg_animation_layout, View.GONE);
        rv.setImageViewResource(R.id.lg_UpdbtnIcon, R.drawable.lg_weather_refresh);
        rv.setImageViewResource(R.id.lg_widget_current_img, R.drawable.lg_weather_current);

        rv.setImageViewResource(R.id.lg_imgBg1, BACKGROUNDS[hasData ? type(icon) : CLEAR_DAY][size]);
        if (size != SIZE_2X2) {
            setClock(context, rv, size);
        }

        if (!hasData) {
            // setDefaultDataInView / setNetworkDisabledView
            boolean loading = configured; // set up, first fetch still on its way
            rv.setViewVisibility(R.id.lg_widget_no_city, loading ? View.GONE : View.VISIBLE);
            rv.setViewVisibility(R.id.lg_loading_layout, loading ? View.VISIBLE : View.GONE);
            rv.setTextViewText(R.id.lg_widget_no_city_string, "Tap to set location");
            rv.setTextViewText(R.id.lg_loading_text, "Updating...");
            rv.setTextViewText(R.id.lg_widget_city_name, "");
            rv.setViewVisibility(R.id.lg_widget_current_img, View.GONE);
            rv.setViewVisibility(R.id.lg_widget_tempreture_inner_dataLayout, View.GONE);
            rv.setViewVisibility(R.id.lg_UpdateButtonLayout, View.GONE);
            rv.setViewVisibility(R.id.lg_UpdbtnIcon_touch, View.GONE);
            rv.setViewVisibility(R.id.lg_widget_last_updated, View.GONE);
            setClicks(context, rv, configured, piFlags);
            return rv;
        }

        rv.setViewVisibility(R.id.lg_widget_no_city, View.GONE);
        rv.setViewVisibility(R.id.lg_loading_layout, View.GONE);
        rv.setViewVisibility(R.id.lg_widget_tempreture_inner_dataLayout, View.VISIBLE);
        rv.setViewVisibility(R.id.lg_widget_city_name, View.VISIBLE);
        rv.setTextViewText(R.id.lg_widget_city_name, p.getString(WeatherWidgetProvider.KEY_NAME, ""));
        boolean gps = WeatherWidgetProvider.MODE_GPS.equals(p.getString(WeatherWidgetProvider.KEY_MODE, null));
        rv.setViewVisibility(R.id.lg_widget_current_img, gps ? View.VISIBLE : View.GONE);

        String high = deg(p.getFloat(WeatherWidgetProvider.KEY_HIGH, Float.NaN));
        String low = deg(p.getFloat(WeatherWidgetProvider.KEY_LOW, Float.NaN));

        if (size == SIZE_4X1) {
            forecast(context, rv, p, icon);
        } else {
            rv.setTextViewText(R.id.lg_widget_current_temp,
                    Math.round(p.getFloat(WeatherWidgetProvider.KEY_TEMP, 0f)) + unit(f));
            rv.setTextViewText(R.id.lg_widget_weatherState, p.getString(WeatherWidgetProvider.KEY_SUMMARY, ""));
            rv.setTextViewText(R.id.lg_widget_HL_temp, high + "/" + low);
            if (size == SIZE_4X2) {
                float feels = p.getFloat(WeatherWidgetProvider.KEY_FEELS, Float.NaN);
                rv.setTextViewText(R.id.lg_weather_medium_realfeel_temp,
                        Float.isNaN(feels) ? "" : "Feels like " + deg(feels));
            }
            // update bar: "Update 12:30 PM" + refresh button, or "Updating..." + spinner
            rv.setViewVisibility(R.id.lg_UpdateButtonLayout, View.VISIBLE);
            rv.setViewVisibility(R.id.lg_widget_last_updated, View.VISIBLE);
            rv.setViewVisibility(R.id.lg_UpdbtnIcon, fetching ? View.GONE : View.VISIBLE);
            rv.setViewVisibility(R.id.lg_UpdbtnIcon_touch, fetching ? View.GONE : View.VISIBLE);
            rv.setViewVisibility(R.id.lg_ProgressBarLayoutUpd, fetching ? View.VISIBLE : View.GONE);
            rv.setTextViewText(R.id.lg_widget_last_updated, fetching ? "Updating..."
                    : "Update " + updated(context, p.getLong(WeatherWidgetProvider.KEY_UPDATED, 0L)) + " ");
        }
        setClicks(context, rv, true, piFlags);
        return rv;
    }

    // ================================================================= pieces

    /** setDateNTime(): hours, colon, minutes and AM/PM in separate views (AM/PM first for ko/ja/zh) */
    private static void setClock(Context context, RemoteViews rv, int size) {
        Date now = new Date();
        boolean h24 = android.text.format.DateFormat.is24HourFormat(context);
        String lang = Locale.getDefault().getLanguage();
        boolean amFirst = "ko".equals(lang) || "ja".equals(lang) || "zh".equals(lang);
        rv.setTextViewText(R.id.lg_weather_medium_clock_hours, new SimpleDateFormat(h24 ? "HH" : "h", Locale.getDefault()).format(now));
        rv.setTextViewText(R.id.lg_weather_medium_clock_colon, ":");
        rv.setTextViewText(R.id.lg_weather_medium_clock_minutes, new SimpleDateFormat("mm", Locale.getDefault()).format(now));
        Calendar c = Calendar.getInstance();
        String ampm = new DateFormatSymbols().getAmPmStrings()[c.get(Calendar.AM_PM)];
        rv.setTextViewText(R.id.lg_weather_medium_am_pm_front, ampm);
        rv.setTextViewText(R.id.lg_weather_medium_am_pm_back, ampm);
        rv.setViewVisibility(R.id.lg_weather_medium_am_pm_front, !h24 && amFirst ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.lg_weather_medium_am_pm_back, !h24 && !amFirst ? View.VISIBLE : View.GONE);
        if (size == SIZE_4X2) { // "Tue, Aug 16"
            rv.setTextViewText(R.id.lg_weather_medium_date, DateUtils.formatDateTime(context, now.getTime(),
                    DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY | DateUtils.FORMAT_SHOW_DATE
                            | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_MONTH));
        }
    }

    /** 4x1: today (current condition icon, cyan date) + the next two days */
    private static void forecast(Context context, RemoteViews rv, SharedPreferences p, String currentIcon) {
        String[] codes = split(p.getString(WeatherWidgetProvider.KEY_DAILY_CODES, ""));
        String[] mx = split(p.getString(WeatherWidgetProvider.KEY_DAILY_MAX, ""));
        String[] mn = split(p.getString(WeatherWidgetProvider.KEY_DAILY_MIN, ""));
        int[] day = {R.id.lg_weather_widget_today_string, R.id.lg_weather_widget_second_day_string, R.id.lg_weather_widget_third_day_string};
        int[] ic = {R.id.lg_weather_widget_today_icon, R.id.lg_weather_widget_second_icon, R.id.lg_weather_widget_third_icon};
        int[] hl = {R.id.lg_weather_widget_forecast_temp_hi_low, R.id.lg_weather_widget_second_forecast_temp_hi_low,
                R.id.lg_weather_widget_third_forecast_temp_hi_low};
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < 3; i++) {
            // getMediumDateFormat(): DateUtils flags 65560 = SHOW_DATE | NO_YEAR | ABBREV_MONTH -> "Aug 16"
            rv.setTextViewText(day[i], DateUtils.formatDateTime(context, cal.getTimeInMillis(),
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_MONTH));
            cal.add(Calendar.DAY_OF_MONTH, 1);
            String cond;
            if (i == 0) {
                cond = currentIcon; // FIRST_WEATHER_ICON is the current condition
            } else {
                int wmo = i < codes.length && codes[i].length() > 0 ? parse(codes[i]) : -1;
                cond = wmo < 0 ? null : OpenMeteo.icon(wmo, true);
            }
            rv.setImageViewResource(ic[i], forecastIcon(cond));
            String max = i < mx.length && mx[i].length() > 0 ? mx[i] + "°" : "--°";
            String min = i < mn.length && mn[i].length() > 0 ? mn[i] + "°" : "--°";
            rv.setTextViewText(hl[i], max + "/" + min);
        }
    }

    private static void setClicks(Context context, RemoteViews rv, boolean configured, int piFlags) {
        Intent setup = new Intent(context, MainActivity.class)
                .setAction(WeatherSetup.ACTION_SHOW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent setupPi = PendingIntent.getActivity(context, 0, setup, piFlags);
        rv.setOnClickPendingIntent(R.id.lg_root, setupPi);
        rv.setOnClickPendingIntent(R.id.lg_widget_no_city, setupPi);

        if (configured) { // the refresh button's touch area (UpdbtnIcon_touch)
            Intent refresh = new Intent(context, WeatherWidgetProvider.class).setAction(WeatherWidgetProvider.ACTION_REFRESH);
            rv.setOnClickPendingIntent(R.id.lg_UpdbtnIcon_touch, PendingIntent.getBroadcast(context, 0, refresh, piFlags));
        }
        // the logo opened the provider's page (LAUNCH_WEB)
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        rv.setOnClickPendingIntent(R.id.lg_widget_weather_logo_bt, PendingIntent.getActivity(context, 3, web, piFlags));
        // clock -> alarms / clock app
        Intent clock = WeatherWidgetProvider.clockIntent(context);
        if (clock != null) {
            PendingIntent clockPi = PendingIntent.getActivity(context, 1, clock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), piFlags);
            rv.setOnClickPendingIntent(R.id.lg_weather_large_date_time_layout, clockPi);
            rv.setOnClickPendingIntent(R.id.lg_ClockBackground, clockPi);
        }
    }

    // ================================================================= mapping + formatting

    /** condition name -> LG background type (WeatherCodeData_Accu.WeatherCodeToAnimation groups) */
    private static int type(String icon) {
        if (icon == null) return CLEAR_DAY;
        if (icon.equals("clear-day") || icon.equals("mostly-clear-day")) return CLEAR_DAY;
        if (icon.equals("clear-night") || icon.equals("mostly-clear-night")) return CLEAR_NIGHT;
        if (icon.equals("partly-cloudy-day")) return PARTLY_DAY;
        if (icon.equals("partly-cloudy-night")) return PARTLY_NIGHT;
        if (icon.equals("thunderstorm")) return THUNDER;
        if (icon.equals("wind")) return WINDY;
        if (icon.contains("snow") || icon.equals("flurries") || icon.startsWith("freezing") || icon.equals("sleet")) {
            return SNOWY; // LG: snow, ice, sleet and freezing rain all use the snowy photo
        }
        if (icon.contains("rain") || icon.contains("drizzle")) return RAINY;
        return CLOUDY; // cloudy, fog
    }

    /** condition name -> LG forecast icon (lg_weather_info_weather_*) */
    private static int forecastIcon(String icon) {
        if (icon == null) return R.drawable.lg_icon_a_1_2_k_1; // chooseForecastIcon default
        if (icon.equals("clear-day")) return R.drawable.lg_icon_a_1_2_k_1;
        if (icon.equals("clear-night") || icon.equals("mostly-clear-night")) return R.drawable.lg_icon_a_1_29_30_k_1;
        if (icon.equals("mostly-clear-day") || icon.equals("partly-cloudy-day")) return R.drawable.lg_icon_a_3_4_5_6_9_12_33_k_40;
        if (icon.equals("partly-cloudy-night")) return R.drawable.lg_icon_a_31_32_34_k_2_3_5_6;
        if (icon.equals("cloudy")) return R.drawable.lg_icon_a_7_8_k_4;
        if (icon.equals("fog")) return R.drawable.lg_icon_a_8_11_37_k_4_40;
        if (icon.equals("thunderstorm")) return R.drawable.lg_icon_a_13_14_15_37_38_k_39;
        if (icon.equals("wind")) return R.drawable.lg_icon_a_28;
        if (icon.equals("possible-rain-day")) return R.drawable.lg_icon_k_15_16_17;
        if (icon.equals("possible-snow-day")) return R.drawable.lg_icon_k_23_34_25;
        if (icon.startsWith("freezing") || icon.equals("sleet")) return R.drawable.lg_icon_a_22_23;
        if (icon.contains("snow") || icon.equals("flurries")) return R.drawable.lg_icon_a_20_21_40_k_18_19_20_21_22;
        if (icon.contains("rain") || icon.contains("drizzle")) return R.drawable.lg_icon_a_10_11_16_35_36_k_7_8_9_10_11_12_13_14;
        return R.drawable.lg_icon_a_7_8_k_4;
    }

    /** Utils.getTempUnitString(): a single-char unit in some countries, a bare degree elsewhere */
    private static String unit(boolean fahrenheit) {
        String country = Locale.getDefault().getCountry();
        String[] unitCountries = {"CA", "US", "CN", "JP", "KR", "GB", "BR", "MX"};
        for (String c : unitCountries) {
            if (c.equalsIgnoreCase(country)) {
                return fahrenheit ? "℉" : "℃";
            }
        }
        return "°";
    }

    private static String deg(float v) {
        return Float.isNaN(v) ? "--°" : Math.round(v) + "°";
    }

    /** Utils.getUpdatedTimeFormatted(): "12:30 PM" / "14:30" today, else the short date */
    private static String updated(Context context, long millis) {
        if (millis <= 0) return "";
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(millis);
        Calendar today = Calendar.getInstance();
        if (day.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && day.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
            if (android.text.format.DateFormat.is24HourFormat(context)) {
                return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(day.getTime());
            }
            return new SimpleDateFormat("h:mm", Locale.getDefault()).format(day.getTime())
                    + (day.get(Calendar.AM_PM) == Calendar.AM ? " AM" : " PM");
        }
        return android.text.format.DateFormat.getDateFormat(context).format(day.getTime());
    }

    private static String[] split(String s) {
        return s.length() == 0 ? new String[0] : s.split(",", -1);
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
