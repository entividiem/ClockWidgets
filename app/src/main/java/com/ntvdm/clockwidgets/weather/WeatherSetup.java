package com.ntvdm.clockwidgets.weather;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ntvdm.clockwidgets.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Drives the "Weather Clocks" section of the main screen: °C/°F and location (current
 * GPS/network fix, or a city searched via Open-Meteo's free geocoder). Weather comes from
 * Open-Meteo, which needs no API key.
 * Settings are shared by every weather widget and saved as soon as they change.
 *
 * The host activity forwards onPause / onDestroy / onRequestPermissionsResult.
 */
public class WeatherSetup {

    /** Intent action the widgets use to open the app scrolled to this section. */
    public static final String ACTION_SHOW = "com.ntvdm.clockwidgets.weather.SHOW_SETUP";
    public static final int PERMISSION_REQUEST = 4711;

    private static final List<String> FAHRENHEIT_COUNTRIES = Arrays.asList("US", "LR", "MM", "BS", "BZ", "KY", "PW");
    private static final long LOCATE_TIMEOUT_MS = 20000;
    private static final long MAX_FIX_AGE_MS = 30 * 60 * 1000;

    private final Activity activity;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final View section;
    private final EditText query;
    private final TextView location;
    private final TextView status;

    private final LocationManager locationManager;
    private LocationListener pendingListener;
    private Runnable locateTimeout;
    private boolean destroyed;

    public WeatherSetup(final Activity activity) {
        this.activity = activity;
        this.prefs = WeatherWidgetProvider.prefs(activity);
        this.locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);

        section = activity.findViewById(R.id.weather_section);
        query = (EditText) activity.findViewById(R.id.weather_query);
        location = (TextView) activity.findViewById(R.id.weather_location);
        status = (TextView) activity.findViewById(R.id.weather_status);

        boolean fahrenheit = prefs.contains(WeatherWidgetProvider.KEY_FAHRENHEIT)
                ? prefs.getBoolean(WeatherWidgetProvider.KEY_FAHRENHEIT, false)
                : FAHRENHEIT_COUNTRIES.contains(Locale.getDefault().getCountry());
        if (!prefs.contains(WeatherWidgetProvider.KEY_FAHRENHEIT)) {
            prefs.edit().putBoolean(WeatherWidgetProvider.KEY_FAHRENHEIT, fahrenheit).commit();
        }
        RadioGroup units = (RadioGroup) activity.findViewById(R.id.weather_units);
        units.check(fahrenheit ? R.id.weather_unit_f : R.id.weather_unit_c);
        units.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                prefs.edit()
                        .putBoolean(WeatherWidgetProvider.KEY_FAHRENHEIT, checkedId == R.id.weather_unit_f)
                        .remove(WeatherWidgetProvider.KEY_TEMP) // old number is in the wrong unit
                        .commit();
                refreshWidgets();
            }
        });

        // Chronus: colour (alternate) icon set. Only a look change, so redraw from cache.
        CheckBox altIcons = (CheckBox) activity.findViewById(R.id.weather_chronus_alt_icons);
        if (activity.getResources().getBoolean(R.bool.chronus_enabled)) {
            altIcons.setChecked(prefs.getBoolean(WeatherWidgetProvider.KEY_CHRONUS_ALT_ICONS, false));
            altIcons.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean checked) {
                    prefs.edit().putBoolean(WeatherWidgetProvider.KEY_CHRONUS_ALT_ICONS, checked).commit();
                    WeatherWidgetProvider.redrawAll(activity);
                }
            });
        } else {
            altIcons.setVisibility(View.GONE);
        }

        activity.findViewById(R.id.weather_use_location).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLocate();
            }
        });
        activity.findViewById(R.id.weather_search).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                search();
            }
        });
        query.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    search();
                    return true;
                }
                return false;
            }
        });
        activity.findViewById(R.id.weather_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshWidgets();
            }
        });

        updateLocationLabel();
    }

    /** Scroll to the section if the activity was opened from a widget. */
    public void handleIntent(Intent intent, final ScrollView scroll) {
        if (intent == null || !ACTION_SHOW.equals(intent.getAction())) {
            return;
        }
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.smoothScrollTo(0, section.getTop());
            }
        });
    }

    public void onResume() {
        updateLocationLabel(); // the widget may have resolved a place name meanwhile
    }

    public void onPause() {
        // nothing to save: every setting is stored as soon as it changes
    }

    public void onDestroy() {
        destroyed = true;
        stopLocate();
    }

    // ---------------------------------------------------------------- settings

    private void saveLocation(String mode, double lat, double lon, String name) {
        prefs.edit()
                .putString(WeatherWidgetProvider.KEY_MODE, mode)
                .putString(WeatherWidgetProvider.KEY_LAT, Double.toString(lat))
                .putString(WeatherWidgetProvider.KEY_LON, Double.toString(lon))
                .putString(WeatherWidgetProvider.KEY_NAME, name)
                .remove(WeatherWidgetProvider.KEY_TEMP)
                .commit();
        updateLocationLabel();
        refreshWidgets();
    }

    private void refreshWidgets() {
        if (!WeatherWidgetProvider.isConfigured(prefs)) {
            showStatus(activity.getString(R.string.weather_pick_location));
        } else if (WeatherWidgetProvider.allIds(activity).length == 0) {
            showStatus(activity.getString(R.string.weather_saved_no_widgets));
        } else {
            showStatus(activity.getString(R.string.weather_updating));
        }
        WeatherWidgetProvider.requestRefresh(activity);
    }

    private void updateLocationLabel() {
        String text;
        if (!WeatherWidgetProvider.isConfigured(prefs)) {
            text = activity.getString(R.string.weather_location_none);
        } else {
            String name = prefs.getString(WeatherWidgetProvider.KEY_NAME, "");
            boolean gps = WeatherWidgetProvider.MODE_GPS.equals(prefs.getString(WeatherWidgetProvider.KEY_MODE, null));
            if (name.length() == 0) {
                name = activity.getString(R.string.current_location);
            } else if (gps) {
                name = name + " (" + activity.getString(R.string.current_location).toLowerCase(Locale.getDefault()) + ")";
            }
            text = activity.getString(R.string.weather_location_label, name);
        }
        location.setText(text);
    }

    private void showStatus(String s) {
        status.setText(s);
        status.setVisibility(View.VISIBLE);
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    // ---------------------------------------------------------------- current location

    private void startLocate() {
        if (Build.VERSION.SDK_INT >= 23 && !Locator.hasPermission(activity)) {
            activity.requestPermissions(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST);
        } else {
            locate();
        }
    }

    public void onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode != PERMISSION_REQUEST) {
            return;
        }
        boolean granted = false;
        for (int r : grantResults) {
            granted |= r == 0; // PERMISSION_GRANTED
        }
        if (granted) {
            locate();
        } else {
            showStatus(activity.getString(R.string.permission_denied));
        }
    }

    private void locate() {
        stopLocate();
        showStatus(activity.getString(R.string.locating));

        final Location last = Locator.lastKnown(activity);
        if (last != null && System.currentTimeMillis() - last.getTime() < MAX_FIX_AGE_MS) {
            onLocated(last); // recent enough
            return;
        }

        String provider = null;
        if (locationManager != null) {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                provider = LocationManager.NETWORK_PROVIDER;
            } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                provider = LocationManager.GPS_PROVIDER;
            }
        }
        if (provider == null) {
            if (last != null) {
                onLocated(last);
            } else {
                showStatus(activity.getString(R.string.no_location));
            }
            return;
        }

        pendingListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location l) {
                stopLocate();
                onLocated(l);
            }

            @Override
            public void onStatusChanged(String p, int s, Bundle b) {
            }

            @Override
            public void onProviderEnabled(String p) {
            }

            @Override
            public void onProviderDisabled(String p) {
            }
        };
        locateTimeout = new Runnable() {
            @Override
            public void run() {
                stopLocate();
                if (last != null) {
                    onLocated(last); // stale fix beats nothing
                } else {
                    showStatus(activity.getString(R.string.no_location));
                }
            }
        };
        try {
            // requestSingleUpdate() is 2.3+; the listener removes itself after the first fix
            locationManager.requestLocationUpdates(provider, 0, 0f, pendingListener, Looper.getMainLooper());
            handler.postDelayed(locateTimeout, LOCATE_TIMEOUT_MS);
        } catch (SecurityException e) {
            pendingListener = null;
            showStatus(activity.getString(R.string.permission_denied));
        }
    }

    private void stopLocate() {
        if (pendingListener != null && locationManager != null) {
            try {
                locationManager.removeUpdates(pendingListener);
            } catch (SecurityException ignored) {
            }
        }
        pendingListener = null;
        if (locateTimeout != null) {
            handler.removeCallbacks(locateTimeout);
        }
        locateTimeout = null;
    }

    private void onLocated(Location l) {
        // empty name -> reverse-geocoded (or timezone city) on the first fetch
        saveLocation(WeatherWidgetProvider.MODE_GPS, l.getLatitude(), l.getLongitude(), "");
    }

    // ---------------------------------------------------------------- city search (Open-Meteo)

    private void search() {
        final String q = query.getText().toString().trim();
        if (q.length() == 0) {
            return;
        }
        hideKeyboard(query);
        showStatus(activity.getString(R.string.searching));
        final String lang = Locale.getDefault().getLanguage();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<String> names = new ArrayList<String>();
                final List<String> labels = new ArrayList<String>();
                final List<double[]> coords = new ArrayList<double[]>();
                String err = null;
                try {
                    JSONObject root = new JSONObject(Net.get("geocoding-api.open-meteo.com",
                            "/v1/search?count=10&format=json&language=" + lang
                                    + "&name=" + URLEncoder.encode(q, "UTF-8")));
                    JSONArray arr = root.optJSONArray("results");
                    for (int i = 0; arr != null && i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String name = o.getString("name");
                        StringBuilder label = new StringBuilder(name);
                        for (String f : new String[]{"admin1", "country"}) {
                            String v = o.optString(f, "");
                            if (v.length() > 0 && !v.equals(name)) {
                                label.append(", ").append(v);
                            }
                        }
                        names.add(name);
                        labels.add(label.toString());
                        coords.add(new double[]{o.getDouble("latitude"), o.getDouble("longitude")});
                    }
                } catch (Exception e) {
                    err = e.getMessage() != null ? e.getMessage() : e.toString();
                }

                final String error = err;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed || activity.isFinishing()) {
                            return;
                        }
                        if (error != null) {
                            showStatus(activity.getString(R.string.network_error, error));
                            return;
                        }
                        if (labels.isEmpty()) {
                            showStatus(activity.getString(R.string.no_results));
                            return;
                        }
                        status.setVisibility(View.GONE);
                        new AlertDialog.Builder(activity)
                                .setTitle(R.string.weather_pick_city)
                                .setItems(labels.toArray(new String[labels.size()]), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        double[] c = coords.get(which);
                                        saveLocation(WeatherWidgetProvider.MODE_FIXED, c[0], c[1], names.get(which));
                                    }
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }
                });
            }
        }).start();
    }
}
