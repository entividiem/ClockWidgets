package com.ntvdm.clockwidgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Toast;

import com.ntvdm.clockwidgets.weather.WeatherSetup;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1.0
        Button btnClassic = (Button) findViewById(R.id.btn_classic);
        btnClassic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyTheme("classic");
            }
        });

        // GB
        Button btnGinger = (Button) findViewById(R.id.btn_gingerbread);
        btnGinger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyTheme("gingerbread");
            }
        });

        // HC
        Button btnhc = (Button) findViewById(R.id.btn_hc);
        btnhc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyTheme("honey");
            }
        });

        // ICS
        Button btnics = (Button) findViewById(R.id.btn_ics);
        btnics.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyTheme("ics");
            }
        });

        // ICS GSM Arena
        Button btnicsgsm = (Button) findViewById(R.id.btn_ics_gsm);
        btnicsgsm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyTheme("ics_gsm");
            }
        });

        // JB
        Button btnjb = (Button) findViewById(R.id.btn_jb);
        btnjb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyTheme("jb");
            }
        });

        // IRK40C my fav
        Button btnirk = (Button) findViewById(R.id.btn_irk);
        btnirk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyTheme("irk");
            }
        });

        // samsnug tw """"modern""""
        Button btnmodern = (Button) findViewById(R.id.btn_modern);
        btnmodern.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyTheme("modern");
            }
        });

        // samsnug tw FUNKYY
        Button btnfunky = (Button) findViewById(R.id.btn_funky);
        btnfunky.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyTheme("funky");
            }
        });

        // materiealie
        Button btnmaterial = (Button) findViewById(R.id.btn_material);
        btnmaterial.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyTheme("material");
            }
        });

        // samsnug tw ""modern"" TWO!
        Button btngbsam = (Button) findViewById(R.id.btn_gbsam);
        btngbsam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyTheme("gbsam");
            }
        });

        // samsnug tw ""modern"" TWO!
        Button btngbsam1 = (Button) findViewById(R.id.btn_gbsam1);
        btngbsam1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyTheme("gbsam1");
            }
        });

        // htc gooner i mean sooner
        Button btnsooner = (Button) findViewById(R.id.btn_sooner);
        btnsooner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyTheme("sooner");
            }
        });

        // 4.2 Digital Clock
        Button btndigital = (Button) findViewById(R.id.btn_digital);
        btndigital.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TextClock requires API 17 (Jelly Bean 4.2)
                if (android.os.Build.VERSION.SDK_INT >= 17) {
                    applyTheme("digital");
                } else {
                    Toast.makeText(MainActivity.this, "Android 4.2+ only", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 4.4 Digital Clock
        Button btndigitalkk = (Button) findViewById(R.id.btn_digitalkk);
        btndigitalkk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TextClock requires API 17 (Jelly Bean 4.2)
                if (android.os.Build.VERSION.SDK_INT >= 17) {
                    applyTheme("digitalkk");
                } else {
                    Toast.makeText(MainActivity.this, "Android 4.2+ only", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 4.0 Digital Clock
        Button btndigitalics = (Button) findViewById(R.id.btn_digitalics);
        btndigitalics.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // needs API 17 (4.2) as this uses TextClock for the Date
                if (android.os.Build.VERSION.SDK_INT >= 17) {
                    applyTheme("digitalics");
                } else {
                    Toast.makeText(MainActivity.this, "Android 4.2+ only", Toast.LENGTH_SHORT).show();
                }
                Toast.makeText(MainActivity.this, "meant for the lock screen", Toast.LENGTH_SHORT).show();
                }

        });

        // Weather Clocks setup section (API key, units, location)
        weatherSetup = new WeatherSetup(this);
        weatherSetup.handleIntent(getIntent(), (ScrollView) findViewById(R.id.main_scroll));

    }

    private WeatherSetup weatherSetup;

    @Override
    protected void onResume() {
        super.onResume();
        weatherSetup.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        weatherSetup.onPause();
    }

    @Override
    protected void onDestroy() {
        weatherSetup.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        weatherSetup.onRequestPermissionsResult(requestCode, grantResults);
    }

    private void applyTheme(String themeKey) {
        SharedPreferences prefs = getSharedPreferences("ClockPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("selected_theme", themeKey).apply();

        Intent intent = new Intent(this, AnalogWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);

        AppWidgetManager widgetManager = AppWidgetManager.getInstance(this);
        int[] ids = widgetManager.getAppWidgetIds(new ComponentName(this, AnalogWidgetProvider.class));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);

        sendBroadcast(intent);

        Toast.makeText(this, "Theme used: " + themeKey, Toast.LENGTH_SHORT).show();
    }


}