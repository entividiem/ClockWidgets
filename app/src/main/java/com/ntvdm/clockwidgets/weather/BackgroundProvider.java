package com.ntvdm.clockwidgets.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Read-only provider exposing the baked S3 backgrounds in files/framed/ to the launcher. */
public class BackgroundProvider extends ContentProvider {

    static String authority(Context context) {
        return context.getPackageName() + ".bg";
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("read-only");
        }
        try {
            File root = FramedBackground.dir(getContext()).getCanonicalFile();
            File f = new File(root, uri.getPath()).getCanonicalFile();
            // no escaping the framed/ directory
            if (!f.getPath().startsWith(root.getPath() + File.separator) || !f.isFile()) {
                throw new FileNotFoundException(uri.toString());
            }
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public String getType(Uri uri) {
        return "image/png";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
