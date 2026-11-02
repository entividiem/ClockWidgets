package com.ntvdm.clockwidgets.weather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

/**
 * Open-Meteo (https://open-meteo.com) forecast client. Free for non-commercial use, no API key,
 * data under CC BY 4.0 (credited in the setup section and on the S3 widget).
 *
 * Open-Meteo reports a WMO weather code instead of an icon name, so it is turned into the
 * condition names {@link WeatherIcons} maps onto each widget's artwork, plus a short summary.
 */
final class OpenMeteo {

    private OpenMeteo() {
    }

    static final class Current {
        double temperature;
        /** today's min / max (NaN if not sent) */
        double low = Double.NaN;
        double high = Double.NaN;
        String icon;
        String summary;
        String timezone;
    }

    static Current fetch(double lat, double lon, boolean fahrenheit) throws IOException, JSONException {
        String path = "/v1/forecast"
                + String.format(Locale.US, "?latitude=%.4f&longitude=%.4f", lat, lon)
                + "&current=temperature_2m,weather_code,is_day"
                + "&daily=temperature_2m_max,temperature_2m_min"
                + "&timezone=auto&forecast_days=1"
                + (fahrenheit ? "&temperature_unit=fahrenheit" : "");
        JSONObject root = new JSONObject(Net.get("api.open-meteo.com", path));
        JSONObject now = root.getJSONObject("current");

        int code = now.optInt("weather_code", 3);
        boolean day = now.optInt("is_day", 1) == 1;

        Current c = new Current();
        c.temperature = now.getDouble("temperature_2m");
        c.icon = icon(code, day);
        c.summary = summary(code);
        c.timezone = root.optString("timezone", "");

        JSONObject daily = root.optJSONObject("daily");
        if (daily != null) {
            c.high = first(daily.optJSONArray("temperature_2m_max"));
            c.low = first(daily.optJSONArray("temperature_2m_min"));
        }
        return c;
    }

    private static double first(JSONArray a) {
        return a == null || a.length() == 0 || a.isNull(0) ? Double.NaN : a.optDouble(0, Double.NaN);
    }

    /** WMO code + day/night -> condition name understood by {@link WeatherIcons}. */
    static String icon(int code, boolean day) {
        String dn = day ? "-day" : "-night";
        switch (code) {
            case 0: return "clear" + dn;
            case 1: return "mostly-clear" + dn;
            case 2: return "partly-cloudy" + dn;
            case 3: return "cloudy";
            case 45: case 48: return "fog";
            case 51: case 53: case 55: return "drizzle";
            case 56: case 57: return "freezing-drizzle";
            case 61: return "light-rain";
            case 63: return "rain";
            case 65: return "heavy-rain";
            case 66: case 67: return "freezing-rain";
            case 71: return "light-snow";
            case 73: return "snow";
            case 75: return "heavy-snow";
            case 77: return "flurries";
            case 80: return "possible-rain" + dn;      // light showers: sun/moon + rain artwork
            case 81: return "rain";
            case 82: return "heavy-rain";
            case 85: return "possible-snow" + dn;      // light snow showers
            case 86: return "heavy-snow";
            case 95: case 96: case 97: case 99: return "thunderstorm";
            default: return "cloudy";
        }
    }

    /** Short condition text shown on the widgets. */
    static String summary(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mostly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45: return "Fog";
            case 48: return "Rime fog";
            case 51: return "Light drizzle";
            case 53: return "Drizzle";
            case 55: return "Dense drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: return "Light rain";
            case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66: case 67: return "Freezing rain";
            case 71: return "Light snow";
            case 73: return "Snow";
            case 75: return "Heavy snow";
            case 77: return "Snow grains";
            case 80: return "Light showers";
            case 81: return "Showers";
            case 82: return "Heavy showers";
            case 85: return "Snow showers";
            case 86: return "Heavy snow showers";
            case 95: case 97: return "Thunderstorm";
            case 96: case 99: return "Thunderstorm with hail";
            default: return "";
        }
    }
}
