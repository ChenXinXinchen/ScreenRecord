package cx.jinke.com.mediarecord;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MediaUtil {
    private static final String TAG = "MediaUtil";
    public static final String USER_ID_FILE_NAME = "uid.txt";
    public static final String USER_ID_DIRECTORY_NAME = "/dashUser/";
    private static volatile String cachedUserID;
    private static final int WRITE_REQUEST_CODE = 43;

    public static String getSaveDirectory() {

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            String rootDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + "ScreenRecord" + "/";

            File file = new File(rootDir);
            if (!file.exists()) {
                if (!file.mkdirs()) {
                    return null;
                }
            }


            return rootDir;
        } else {
            return null;
        }

    }


    public static String getSaveDirectory(String path) {

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            String rootDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/" + path + "/";

            File file = new File(rootDir);
            if (!file.exists()) {
                if (!file.mkdirs()) {
                    return null;
                }
            }


            return rootDir;
        } else {
            return null;
        }
    }


    public static File byteToWav(byte[] buffer, String wavPath) {
        File tempWav = new File(wavPath);
        try {
            if (!tempWav.exists()) {
                tempWav.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(tempWav);
            fos.write(buffer);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return tempWav;
        }

    }

    public static Uri insertVideo(Context context,String filePath) {
        ContentResolver localContentResolver = context.getContentResolver();
        ContentValues localContentValues = getVideoContentValues(context, new File(filePath), System.currentTimeMillis());
        Uri localUri = localContentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, localContentValues);
        return  localUri ;
    }


    public static ContentValues getVideoContentValues(Context paramContext, File paramFile, long paramLong) {
        ContentValues localContentValues = new ContentValues();
        localContentValues.put("title", paramFile.getName());
        localContentValues.put("_display_name", paramFile.getName());
        localContentValues.put("mime_type", "video/mp4");
        localContentValues.put("datetaken", Long.valueOf(paramLong));
        localContentValues.put("date_modified", Long.valueOf(paramLong));
        localContentValues.put("date_added", Long.valueOf(paramLong));
        localContentValues.put("_data", paramFile.getAbsolutePath());
        localContentValues.put("_size", Long.valueOf(paramFile.length()));
        return localContentValues;
    }


    public static void deleteFile(File file) {
        try {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                for (int i = 0; i < files.length; i++) {
                    File f = files[i];
                    deleteFile(f);
                }
                file.delete();//如要保留文件夹，只删除文件，请注释这行
            } else if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static String getCachedUserID(Activity activity) {
        if(TextUtils.isEmpty(cachedUserID)){
            cachedUserID = loadLocalUID(activity);
        }
        return cachedUserID;
    }

    public static void setCachedUserID(Activity activity ,String cachedUserID) {
        MediaUtil.cachedUserID = cachedUserID;
        saveUID(activity,cachedUserID);
    }

    public enum Format {

        Mon("\"yyyy年MM月dd日 HH时mm分ss秒\""), Tue("yyyy/MM/dd HH:mm"), Wed("yyyy-MM-dd HH:mm:ss");
        private final String format;

        Format(String format) {
            this.format = format;
        }

        public String getFormat() {
            return format;
        }
    }

    public static String ConvertDate(Long time, Format fm) {
        SimpleDateFormat sdf = new SimpleDateFormat(fm.getFormat());

        String str = sdf.format(new Date(time));

        return str;

    }

    public static void saveUID(Activity activity ,String uid) {
        BufferedWriter writer = null;
        FileLock lock = null;
        try {
            String fileName = getSaveDirectory(activity,USER_ID_FILE_NAME);
            File uidFile = new File(fileName);
            if(!uidFile.exists()){
                uidFile.createNewFile();
            }
            // Create file lock
            FileOutputStream outputStream = new FileOutputStream(uidFile);
            lock = outputStream.getChannel().lock();

            // Write ro file
            writer = new BufferedWriter(new FileWriter(uidFile));
            writer.write(uid);

            // Flush buffers
            writer.flush();
            outputStream.flush();

            // Force write to disk
            outputStream.getFD().sync();
            Log.e(TAG, "write uid file suc");
        } catch (Exception e) {
            Log.e(TAG, "Unexpected Error" +  e.getLocalizedMessage());
            e.printStackTrace();
        } catch (Error e) {
            Log.e(TAG, "Unexpected Error", e);
        } finally {

            if (lock != null) {
                try {
                    lock.release();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // Close stream
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }

    }


    public static String getSaveDirectory(Activity activity , String fileName) {
        String rootDir;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            rootDir = Environment.getDataDirectory().getAbsolutePath() + USER_ID_DIRECTORY_NAME + fileName ;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);

            // Filter to only show results that can be "opened", such as
            // a file (as opposed to a list of contacts or timezones).
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            // Create a file with the requested MIME type.
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, rootDir);
            activity.startActivityForResult(intent, WRITE_REQUEST_CODE);
            return  rootDir;
        }else{
            rootDir = Environment.getExternalStorageDirectory().getAbsolutePath() + USER_ID_DIRECTORY_NAME + fileName ;
        }
        Log.d(TAG,"rootDir: " +  rootDir);
        File file = new File(rootDir);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return rootDir;
    }


    private static String loadLocalUID(Activity activity) {

        File uidFile = new File(getSaveDirectory(activity, USER_ID_FILE_NAME));
        Log.d(TAG, "Loading UID from local storage start " );
        if (uidFile.exists()) {
            Log.d(TAG, "Loading UID from local storage:  " +  uidFile.getAbsolutePath());

            DataInputStream dis = null;
            FileLock lock = null;
            try {
                // Create file lock
                FileInputStream fileInputStream = new FileInputStream(uidFile);
                lock = fileInputStream.getChannel().lock(0L, Long.MAX_VALUE, true);

                // Read file
                dis = new DataInputStream(fileInputStream);
                String uid = dis.readLine();

                // Return uid
                if (uid != null && !uid.trim().equals("") && !uid.equals("null")) {
                    Log.e(TAG, "load uid file suc: " + uid);
                    return uid;
                } else {
                    throw new IllegalStateException("failed loading UID from file, invalid value: " + uid);
                }
            } catch (Exception e) {
                Log.e(TAG, "failed loading UID from file ");
                return  "" ;
            } finally {
                // Release lock
                if (lock != null) {
                    try {
                        lock.release();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                // Close stream
                if (dis != null) {
                    try {
                        dis.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }
        }
        Log.d(TAG, "Loading UID from local storage fail " );
        return "";
    }



}
