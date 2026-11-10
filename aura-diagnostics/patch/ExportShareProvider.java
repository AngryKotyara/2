package com.auratv.diagnostics;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Grantable, read-only content URI with reliable size + filename metadata.
 * Prevents a receiving app from misinterpreting an SAF provider's ZIP as 0 B.
 * Does not expose any other app-private paths or files.
 */
public final class ExportShareProvider extends ContentProvider {
    public static final String AUTHORITY="com.auratv.diagnostics.export";
    public static final String FILE_NAME="AURA_TV_Original_Firmware_Files.zip";
    public static final Uri ZIP_URI=Uri.parse("content://"+AUTHORITY+"/firmware.zip");

    public static File cachedZip(Context context) {
        return new File(context.getCacheDir(),FILE_NAME);
    }
    private boolean valid(Uri uri) {
        return ZIP_URI.equals(uri);
    }
    private File usableFile(Uri uri) throws FileNotFoundException {
        if(!valid(uri) || getContext()==null)
            throw new FileNotFoundException("Unknown export URI");
        File file=cachedZip(getContext());
        if(!file.isFile() || file.length()<512)
            throw new FileNotFoundException("Verified export ZIP is unavailable");
        return file;
    }
    @Override public boolean onCreate() {return true;}
    @Override public String getType(Uri uri) {
        return valid(uri)?"application/zip":null;
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,
                                   String[] selectionArgs,String sortOrder) {
        final File file;
        try {file=usableFile(uri);}catch(FileNotFoundException e){return null;}
        String[] cols=projection==null ?
            new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor cursor=new MatrixCursor(cols,1);
        Object[] row=new Object[cols.length];
        for(int i=0;i<cols.length;i++){
            if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]=FILE_NAME;
            else if(OpenableColumns.SIZE.equals(cols[i]))row[i]=file.length();
            else row[i]=null;
        }
        cursor.addRow(row);
        return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)
            throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Read-only export");
        return ParcelFileDescriptor.open(usableFile(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri,ContentValues values) {
        throw new UnsupportedOperationException("Read-only");
    }
    @Override public int update(Uri uri,ContentValues values,String where,String[] args) {
        throw new UnsupportedOperationException("Read-only");
    }
    @Override public int delete(Uri uri,String where,String[] args) {
        throw new UnsupportedOperationException("Read-only");
    }
}
