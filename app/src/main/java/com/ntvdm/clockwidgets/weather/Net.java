package com.ntvdm.clockwidgets.weather;

import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.SSLException;

/** Tiny HTTP GET helper. Tries HTTPS on 4.4W+ and falls back to plain HTTP (old TLS stacks). */
final class Net {

    private Net() {
    }

    static String get(String host, String pathAndQuery) throws IOException {
        if (Build.VERSION.SDK_INT >= 20) {
            try {
                return read("https://" + host + pathAndQuery);
            } catch (SSLException ignored) {
                // fall through to http
            }
        }
        return read("http://" + host + pathAndQuery);
    }

    private static String read(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Connection", "close");
        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }
            InputStream in = conn.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                return out.toString("UTF-8");
            } finally {
                in.close();
            }
        } finally {
            conn.disconnect();
        }
    }
}
