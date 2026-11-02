package com.ntvdm.clockwidgets.weather;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

import java.util.List;
import java.util.Locale;

final class Locator {

    private Locator() {
    }

    static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        return context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Newest last-known fix across all enabled providers, or null. */
    static Location lastKnown(Context context) {
        if (!hasPermission(context)) {
            return null;
        }
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            return null;
        }
        Location best = null;
        for (String provider : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) {
                    best = l;
                }
            } catch (SecurityException ignored) {
            }
        }
        return best;
    }

    /** Reverse-geocode to a city name, or null if no geocoder / no result. */
    static String placeName(Context context, double lat, double lon) {
        // Geocoder.isPresent() is 2.3+: just try it, no backend throws IOException
        try {
            List<Address> list = new Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1);
            if (list == null || list.isEmpty()) {
                return null;
            }
            Address a = list.get(0);
            if (a.getLocality() != null) return a.getLocality();
            if (a.getSubAdminArea() != null) return a.getSubAdminArea();
            return a.getAdminArea();
        } catch (Exception e) { // IOException / IllegalArgumentException
            return null;
        }
    }
}
