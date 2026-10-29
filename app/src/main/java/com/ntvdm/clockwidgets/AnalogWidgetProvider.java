package com.ntvdm.clockwidgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class AnalogWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        SharedPreferences prefs = context.getSharedPreferences("ClockPrefs", Context.MODE_PRIVATE);
        String theme = prefs.getString("selected_theme", "classic");

        for (int appWidgetId : appWidgetIds) {
            int layoutId;

            if (theme.equals("gingerbread")) {
                layoutId = R.layout.widget_gingerbread;
            } else if (theme.equals("honey")) {
                layoutId = R.layout.widget_honey;
            } else if (theme.equals("ics")) {
                layoutId = R.layout.widget_ics;
            } else if (theme.equals("ics_gsm")) {
                layoutId = R.layout.widget_ics_gsm;
            } else if (theme.equals("jb")) {
                layoutId = R.layout.widget_jb;
            } else if (theme.equals("irk")) {
                layoutId = R.layout.widget_irk;
            } else if (theme.equals("modern")) {
                layoutId = R.layout.widget_modern;
            } else if (theme.equals("funky")) {
                layoutId = R.layout.widget_funky;
            } else if (theme.equals("material")) {
                layoutId = R.layout.widget_material;
            } else if (theme.equals("gbsam")) {
                layoutId = R.layout.widget_gbsam;
            } else if (theme.equals("gbsam1")) {
                layoutId = R.layout.widget_gbsam1;
            } else if (theme.equals("digital")) {
                layoutId = R.layout.widget_digital;
            } else if (theme.equals("digitalkk")) {
                layoutId = R.layout.widget_digitalkk;
            } else if (theme.equals("digitalics")) {
                layoutId = R.layout.widget_digital_sprite;
            } else if (theme.equals("sooner")) {
                layoutId = R.layout.widget_sooner;
            } else {
                layoutId = R.layout.widget_clock; // 1.0 as standard or dalvik gets mad
            }

            RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);

            if (theme.equals("digitalics")) {
                updateDigitalSprite(context, views);
            }

            appWidgetManager.updateAppWidget(appWidgetId, views);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
        }

    private void updateDigitalSprite(Context context, RemoteViews views) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        boolean is24Hour = android.text.format.DateFormat.is24HourFormat(context);

        int hour;
        if (is24Hour) {
            hour = c.get(java.util.Calendar.HOUR_OF_DAY);
        } else {
            hour = c.get(java.util.Calendar.HOUR);
            if (hour == 0) hour = 12; // 12 hour
        }

        int min = c.get(java.util.Calendar.MINUTE);

        int[] digitRes = {
                R.drawable.n0, R.drawable.n1, R.drawable.n2, R.drawable.n3, R.drawable.n4,
                R.drawable.n5, R.drawable.n6, R.drawable.n7, R.drawable.n8, R.drawable.n9
        };
        // hour check
        if (hour < 10 && !is24Hour) {
            views.setViewVisibility(R.id.img_h_tens, android.view.View.GONE);
        } else {
            views.setViewVisibility(R.id.img_h_tens, android.view.View.VISIBLE);
            views.setImageViewResource(R.id.img_h_tens, digitRes[hour / 10]);
        }

        views.setImageViewResource(R.id.img_h_ones, digitRes[hour % 10]);
        views.setImageViewResource(R.id.img_m_tens, digitRes[min / 10]);
        views.setImageViewResource(R.id.img_m_ones, digitRes[min % 10]);
        views.setImageViewResource(R.id.img_colon, R.drawable.colon);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        scheduleNextUpdate(context);
    }

    private void scheduleNextUpdate(Context context) {
        android.app.AlarmManager am = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        android.content.Intent intent = new android.content.Intent(context, AnalogWidgetProvider.class);
        intent.setAction(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE);

        int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            flags |= 134217728; // FLAG_IMMUTABLE
        }

        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(context, 0, intent, flags);

        long now = System.currentTimeMillis();
        long nextMinute = (now / 60000 + 1) * 60000;

        if (android.os.Build.VERSION.SDK_INT >= 19) {
            am.setExact(android.app.AlarmManager.RTC, nextMinute, pi);
        } else {
            am.set(android.app.AlarmManager.RTC, nextMinute, pi);
        }
    }

    @Override
    public void onDisabled(Context context) {
        android.app.AlarmManager am = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        android.content.Intent intent = new android.content.Intent(context, AnalogWidgetProvider.class);
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(context, 0, intent, 0);
        am.cancel(pi);
        super.onDisabled(context);
    }

    @Override
    public void onReceive(Context context, android.content.Intent intent) {
        super.onReceive(context, intent);

        if (android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(intent.getAction())) {
            scheduleNextUpdate(context);

            android.appwidget.AppWidgetManager wm = android.appwidget.AppWidgetManager.getInstance(context);
            android.content.ComponentName cn = new android.content.ComponentName(context, AnalogWidgetProvider.class);
            int[] ids = wm.getAppWidgetIds(cn);

            onUpdate(context, wm, ids);
        }
    }
    }