package com.auratv.diagnostics;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Read-only sharing provider for the one exported diagnostics text file. */
public final class ReportContentProvider extends ContentProvider {
    private static final String EXPORT_PATH = "/report.txt";
    private static final String FILE_NAME = "AURA_TV_diagnostics.txt";

    private File report() {
        return new File(new File(getContext().getCacheDir(), "reports"), FILE_NAME);
    }
    private boolean valid(Uri uri) {
        return uri != null && EXPORT_PATH.equals(uri.getPath())
                && (getContext().getPackageName() + ".reports").equals(uri.getAuthority());
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        return valid(uri) ? "text/plain" : null;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!valid(uri) || !"r".equals(mode)) throw new FileNotFoundException("Unknown report or access mode");
        File file = report();
        if (!file.isFile()) throw new FileNotFoundException("Report does not exist");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        if (!valid(uri)) return null;
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String name : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(name)) row.add(FILE_NAME);
            else if (OpenableColumns.SIZE.equals(name)) row.add(report().length());
            else row.add(null);
        }
        return cursor;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read-only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read-only"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read-only"); }
}
