package com.ntvdm.clockwidgets.weather;

import com.ntvdm.clockwidgets.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

/**
 * Pirate Weather (https://pirateweather.net) client + mapping of its icon names
 * onto the old AccuWeather condition codes (1-44) that the HTC / TouchWiz artwork uses.
 */
final class PirateWeather {

    private PirateWeather() {
    }

    static final class Current {
        double temperature;
        String icon;
        String summary;
        String timezone;
    }

    static Current fetch(String apiKey, double lat, double lon, boolean fahrenheit)
            throws IOException, JSONException {
        String path = "/forecast/" + apiKey + "/"
                + String.format(Locale.US, "%.4f,%.4f", lat, lon)
                + "?exclude=minutely,hourly,daily,alerts,flags"
                + "&units=" + (fahrenheit ? "us" : "si")
                + "&version=2&icon=pirate";
        JSONObject root = new JSONObject(Net.get("api.pirateweather.net", path));
        JSONObject now = root.getJSONObject("currently");

        Current c = new Current();
        c.temperature = now.getDouble("temperature");
        c.icon = now.optString("icon", "cloudy");
        c.timezone = root.optString("timezone", "");
        c.summary = now.optString("summary", "");
        return c;
    }

    /** Pirate Weather icon name -> AccuWeather code (day 1-32, night 33-44). */
    static int accuCode(String icon) {
        if (icon == null) {
            return 7;
        }
        boolean night = icon.endsWith("-night");
        String base = icon.replace("-day", "").replace("-night", "");

        if (base.startsWith("possible-")) {
            String rest = base.substring("possible-".length());
            if (rest.equals("thunderstorm")) return night ? 41 : 17;
            if (rest.equals("snow")) return night ? 43 : 21;
            if (rest.contains("sleet")) return 25;
            return night ? 39 : 14; // possible rain / precipitation
        }

        if (base.equals("clear")) return night ? 33 : 1;
        if (base.equals("mostly-clear") || base.equals("partly-cloudy")) return night ? 35 : 3;
        if (base.equals("mostly-cloudy")) return night ? 38 : 6;
        if (base.equals("cloudy")) return 7;
        if (base.equals("fog") || base.equals("mist") || base.equals("haze") || base.equals("smoke")) return 11;
        if (base.equals("drizzle") || base.equals("light-rain")) return 12;
        if (base.equals("rain") || base.equals("heavy-rain") || base.equals("precipitation")) return 18;
        if (base.equals("thunderstorm")) return 15;
        if (base.equals("flurries") || base.equals("light-snow")) return 19;
        if (base.equals("snow") || base.equals("heavy-snow")) return 22;
        if (base.equals("mixed")) return 29;
        if (base.equals("wind") || base.equals("breezy") || base.equals("dangerous-wind")) return 32;

        if (base.contains("sleet") || base.contains("hail") || base.contains("freezing") || base.contains("ice")) {
            return 25;
        }
        if (base.contains("snow")) return 22;
        if (base.contains("rain")) return 18;
        return 7;
    }

    /** Icon for the HTC / Clean widgets (shared Sense-style set, several codes per image). */
    static int iconRes(String icon) {
        switch (accuCode(icon)) {
            case 1: case 2:
                return R.drawable.weather_01_02;
            case 3: case 4: case 5:
                return R.drawable.weather_03_04_05;
            case 11:
                return R.drawable.weather_11;
            case 12: case 13: case 39: case 40:
                return R.drawable.weather_12_13_39_40;
            case 14:
                return R.drawable.weather_14;
            case 15: case 41: case 42:
                return R.drawable.weather_15_41_42;
            case 16: case 17:
                return R.drawable.weather_16_17;
            case 18:
                return R.drawable.weather_18;
            case 19: case 43:
                return R.drawable.weather_19_43;
            case 20: case 21:
                return R.drawable.weather_20_21;
            case 22: case 23: case 44:
                return R.drawable.weather_22_23_44;
            case 24: case 25: case 26:
                return R.drawable.weather_24_25_26;
            case 29:
                return R.drawable.weather_29;
            case 30:
                return R.drawable.weather_30;
            case 31:
                return R.drawable.weather_31;
            case 32:
                return R.drawable.weather_32;
            case 33:
                return R.drawable.weather_33;
            case 34: case 35: case 36: case 37:
                return R.drawable.weather_34_35_36_37;
            case 38:
                return R.drawable.weather_38;
            default: // 6-10, 27, 28
                return R.drawable.weather_06_07_08;
        }
    }

    /** "Europe/Berlin" -> "Berlin", used when no place name is known. */
    static String cityFromTimezone(String tz) {
        if (tz == null || tz.length() == 0) {
            return "";
        }
        return tz.substring(tz.lastIndexOf('/') + 1).replace('_', ' ');
    }
}
