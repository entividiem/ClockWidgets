package com.ntvdm.clockwidgets.weather;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.NinePatchDrawable;
import android.net.Uri;
import android.util.Log;

import com.ntvdm.clockwidgets.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * The S3 widget has rounded corners + a drop shadow over the weather photo. RemoteViews can't
 * mask an image, so we bake mask + shadow into a PNG once per condition and hand the launcher
 * a content:// URI to it (served by {@link BackgroundProvider}).
 */
final class FramedBackground {

    private static final String DIR = "framed";
    private static final int MAX_FILES = 16; // Xperia keeps front/back/setup x port/land

    private FramedBackground() {
    }

    static File dir(Context context) {
        return new File(context.getFilesDir(), DIR);
    }

    static synchronized Uri s3(Context context, int bgRes, String tag) {
        File f = new File(dir(context), "s3_" + tag + ".png");
        if (!f.exists() && !compose(context, bgRes, f)) {
            return null;
        }
        return new Uri.Builder()
                .scheme("content")
                .authority(BackgroundProvider.authority(context))
                .path(f.getName())
                .build();
    }

    private static boolean compose(Context context, int bgRes, File target) {
        Bitmap src = BitmapFactory.decodeResource(context.getResources(), bgRes);
        if (src == null) {
            return false;
        }
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            Canvas c = new Canvas(out);
            Rect r = new Rect(0, 0, out.getWidth(), out.getHeight());
            c.drawBitmap(src, null, r, new Paint(Paint.FILTER_BITMAP_FLAG));
            src.recycle();

            NinePatchDrawable mask = (NinePatchDrawable) context.getResources().getDrawable(R.drawable.s3_frame_mask);
            mask.getPaint().setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            mask.setBounds(r);
            mask.draw(c);

            NinePatchDrawable shadow = (NinePatchDrawable) context.getResources().getDrawable(R.drawable.s3_frame_shadow);
            shadow.setBounds(r);
            shadow.draw(c);

            dir(context).mkdirs();
            File tmp = new File(target.getPath() + ".tmp");
            try {
                FileOutputStream fos = new FileOutputStream(tmp);
                try {
                    out.compress(Bitmap.CompressFormat.PNG, 100, fos);
                } finally {
                    fos.close();
                }
                if (!tmp.renameTo(target)) {
                    return false;
                }
                trim(context, target);
                return true;
            } catch (IOException e) {
                Log.w("WeatherClock", "Could not write framed background", e);
                tmp.delete();
                return false;
            }
        } finally {
            out.recycle();
        }
    }

    /** Keep only the newest MAX_FILES baked backgrounds. */
    static void trim(Context context, File keep) {
        File[] files = dir(context).listFiles();
        if (files == null || files.length <= MAX_FILES) {
            return;
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.valueOf(a.lastModified()).compareTo(b.lastModified());
            }
        });
        for (int i = 0; i < files.length - MAX_FILES; i++) {
            if (!files[i].equals(keep)) {
                files[i].delete();
            }
        }
    }
}
