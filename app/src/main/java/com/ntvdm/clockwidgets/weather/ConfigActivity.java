package com.ntvdm.clockwidgets.weather;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
 * Widget configure dialog: Pirate Weather API key, °C/°F, and location
 * (current GPS/network fix, or a city searched via Open-Meteo's free geocoder).
 * Also opened by tapping the place name on a placed widget.
 */
public class ConfigActivity extends Activity {

    private static final List<String> FAHRENHEIT_COUNTRIES = Arrays.asList("US", "LR", "MM", "BS", "BZ", "KY", "PW");
    private static final long LOCATE_TIMEOUT_MS = 20000;
    private static final long MAX_FIX_AGE_MS = 30 * 60 * 1000;

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private EditText apiKey;
    private EditText query;
    private ListView results;
    private TextView status;
    private RadioButton unitF;

    private LocationManager locationManager;
    private LocationListener pendingListener;
    private Runnable locateTimeout;

    private final List<double[]> resultCoords = new ArrayList<double[]>();
    private final List<String> resultNames = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        setContentView(R.layout.activity_weather_config);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        status = (TextView) findViewById(R.id.status);
        results = (ListView) findViewById(R.id.results);
        query = (EditText) findViewById(R.id.query);
        unitF = (RadioButton) findViewById(R.id.unit_f);
        apiKey = (EditText) findViewById(R.id.api_key);
        apiKey.setText(WeatherWidgetProvider.apiKey(this));

        final SharedPreferences prefs = WeatherWidgetProvider.prefs(this);
        String fKey = WeatherWidgetProvider.key(widgetId, "fahrenheit");
        boolean fahrenheit = prefs.contains(fKey)
                ? prefs.getBoolean(fKey, false)
                : FAHRENHEIT_COUNTRIES.contains(Locale.getDefault().getCountry());

        RadioGroup units = (RadioGroup) findViewById(R.id.units);
        units.check(fahrenheit ? R.id.unit_f : R.id.unit_c);
        units.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // already-configured widget: apply the unit change right away
                if (prefs.getString(WeatherWidgetProvider.key(widgetId, "mode"), null) != null) {
                    prefs.edit()
                            .putBoolean(WeatherWidgetProvider.key(widgetId, "fahrenheit"), checkedId == R.id.unit_f)
                            .remove(WeatherWidgetProvider.key(widgetId, "temp"))
                            .commit();
                    WeatherWidgetProvider.requestRefresh(ConfigActivity.this, widgetId);
                }
            }
        });

        findViewById(R.id.use_location).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLocate();
            }
        });
        findViewById(R.id.search).setOnClickListener(new View.OnClickListener() {
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
        results.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                double[] c = resultCoords.get(position);
                save(WeatherWidgetProvider.MODE_FIXED, c[0], c[1], resultNames.get(position));
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (saveApiKey()) {
            WeatherWidgetProvider.requestRefresh(this); // key changed -> refresh every widget
        }
    }

    @Override
    protected void onDestroy() {
        stopLocate();
        super.onDestroy();
    }

    private boolean saveApiKey() {
        if (apiKey == null) {
            return false;
        }
        String key = apiKey.getText().toString().trim();
        if (key.equals(WeatherWidgetProvider.apiKey(this))) {
            return false;
        }
        WeatherWidgetProvider.prefs(this).edit().putString(WeatherWidgetProvider.KEY_API_KEY, key).commit();
        return true;
    }

    // ---------------------------------------------------------------- current location

    private void startLocate() {
        if (Build.VERSION.SDK_INT >= 23 && !Locator.hasPermission(this)) {
            requestPermissions(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        } else {
            locate();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        boolean granted = false;
        for (int r : grantResults) {
            granted |= r == 0; // PERMISSION_GRANTED
        }
        if (granted) {
            locate();
        } else {
            showStatus(getString(R.string.permission_denied));
        }
    }

    private void locate() {
        stopLocate();
        showStatus(getString(R.string.locating));

        final Location last = Locator.lastKnown(this);
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
                showStatus(getString(R.string.no_location));
            }
            return;
        }

        pendingListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                stopLocate();
                onLocated(location);
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
                    showStatus(getString(R.string.no_location));
                }
            }
        };
        try {
            locationManager.requestSingleUpdate(provider, pendingListener, Looper.getMainLooper());
            handler.postDelayed(locateTimeout, LOCATE_TIMEOUT_MS);
        } catch (SecurityException e) {
            pendingListener = null;
            showStatus(getString(R.string.permission_denied));
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
        // empty name -> reverse-geocoded (or timezone city) on first fetch
        save(WeatherWidgetProvider.MODE_GPS, l.getLatitude(), l.getLongitude(), "");
    }

    // ---------------------------------------------------------------- city search (Open-Meteo)

    private void search() {
        final String q = query.getText().toString().trim();
        if (q.length() == 0) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(query.getWindowToken(), 0);
        }
        results.setVisibility(View.GONE);
        showStatus(getString(R.string.searching));
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        if (error != null) {
                            showStatus(getString(R.string.network_error, error));
                            return;
                        }
                        if (labels.isEmpty()) {
                            showStatus(getString(R.string.no_results));
                            return;
                        }
                        status.setVisibility(View.GONE);
                        resultNames.clear();
                        resultNames.addAll(names);
                        resultCoords.clear();
                        resultCoords.addAll(coords);
                        results.setAdapter(new ArrayAdapter<String>(ConfigActivity.this,
                                android.R.layout.simple_list_item_1, labels));
                        results.setVisibility(View.VISIBLE);
                    }
                });
            }
        }).start();
    }

    // ---------------------------------------------------------------- save

    private void save(String mode, double lat, double lon, String name) {
        if (apiKey.getText().toString().trim().length() == 0) {
            showStatus(getString(R.string.api_key_missing));
            apiKey.requestFocus();
            return;
        }
        boolean keyChanged = saveApiKey();

        WeatherWidgetProvider.prefs(this).edit()
                .putString(WeatherWidgetProvider.key(widgetId, "mode"), mode)
                .putString(WeatherWidgetProvider.key(widgetId, "lat"), Double.toString(lat))
                .putString(WeatherWidgetProvider.key(widgetId, "lon"), Double.toString(lon))
                .putString(WeatherWidgetProvider.key(widgetId, "name"), name)
                .putBoolean(WeatherWidgetProvider.key(widgetId, "fahrenheit"), unitF.isChecked())
                .remove(WeatherWidgetProvider.key(widgetId, "temp"))
                .commit();

        if (keyChanged) {
            WeatherWidgetProvider.requestRefresh(this);
        } else {
            WeatherWidgetProvider.requestRefresh(this, widgetId);
        }
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        finish();
    }

    private void showStatus(String s) {
        status.setText(s);
        status.setVisibility(View.VISIBLE);
    }
}
