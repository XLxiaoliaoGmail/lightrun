package cn.lightrun.app;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Grants the system installer read access to one private, verified APK only. */
public final class UpdateApkProvider extends ContentProvider {
    public static final String MIME="application/vnd.android.package-archive";
    public static Uri uri(Context context){return Uri.parse("content://"+context.getPackageName()+".updates/update.apk");}
    public boolean onCreate(){return true;}
    private File file(Uri uri){if(!uri(getContext()).equals(uri))throw new SecurityException("Invalid APK URI");return UpdateDownload.apk(getContext());}
    public String getType(Uri uri){file(uri);return MIME;}
    public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode))throw new SecurityException("Read access only");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        File file=file(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor cursor=new MatrixCursor(columns,1);Object[] values=new Object[columns.length];
        for(int i=0;i<columns.length;i++)values[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?"lightrun-update.apk":OpenableColumns.SIZE.equals(columns[i])?file.length():null;
        cursor.addRow(values);return cursor;
    }
    public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
