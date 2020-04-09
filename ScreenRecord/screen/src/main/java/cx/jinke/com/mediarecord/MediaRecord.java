package cx.jinke.com.mediarecord;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static android.media.projection.MediaProjection.*;

public class MediaRecord extends Thread {
    private static final String TAG = "MediaRecord";
    private int mWidth;
    private int mHeight;
    private int mDpi;
    private MediaRecorder mMediaRecorder;
    private MediaProjection mMediaProjection;
    private static final int FRAME_RATE = 60; // 60 fps
    private static Activity mActivity;
    private static String fileName;
    private String combineVideoPath;
    private String muxAudioPath;
    private String targetFile;
    private boolean hasDelete = false;
    private boolean saved = false;
    private boolean play = false;
    private static final int MSG_START = 0;
    private static final int MSG_DONE = 7;
    private static final int STOP_WITH_EOS = 1;
    private HandlerThread mWorker;
    private MyHandler myHandler;
    private AtomicBoolean mForceQuit = new AtomicBoolean(false);
    private AtomicBoolean mIsRunning = new AtomicBoolean(false);
    private VirtualDisplay mVirtualDisplay;
    private AtomicLong startTime = new AtomicLong();
    private AtomicLong stopTime = new AtomicLong();
    private String provider;


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private Callback mProjectionCallback = new Callback() {
        @Override
        public void onStop() {
            Log.e("", "");
        }

    };


    public MediaRecord(int dpi, MediaProjection mp, Activity activity) {
        mDpi = dpi;
        mMediaProjection = mp;
        mActivity = activity;

    }

    public MediaRecord(int dpi, MediaProjection mp, Activity activity, int width, int height) {
        mDpi = dpi;
        mMediaProjection = mp;
        mActivity = activity;
        mWidth = width;
        mHeight = height;

    }

    public final void quit() {
        mForceQuit.set(true);
        if (!mIsRunning.get()) {
            stopTime.set(System.currentTimeMillis());
            release();
        } else {
            signalStop(false);
        }

    }


    @Override
    public void run() {
        if (mWorker != null) throw new IllegalStateException();
        mWorker = new HandlerThread(TAG);
        mWorker.start();
        myHandler = new MyHandler(mWorker.getLooper(), mActivity);
        myHandler.sendEmptyMessage(MSG_START);
    }


    private void getScreenParams() {
        DisplayMetrics dm = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        mWidth = dm.widthPixels;
        mHeight = dm.heightPixels;
    }

    /**
     * 初始化MediaRecorder
     *
     * @return
     */
    private void initMediaRecorder() {
        if (mHeight == 1920 && mWidth == 910) {
            Log.e(TAG, "this is oppo r15");
        } else {
            getScreenParams();
        }

        try {
            mMediaRecorder = new MediaRecorder();
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            fileName = MediaUtil.getSaveDirectory("Camera") + UUID.randomUUID().toString().substring(0, 3) + ".mp4";
            mMediaRecorder.setOutputFile(fileName);
            mMediaRecorder.setVideoSize(mWidth, mHeight);
            mMediaRecorder.setVideoFrameRate(FRAME_RATE);
            Log.e(TAG, "mWidth=" + mWidth + "mHeight=" + mHeight);
            mMediaRecorder.setVideoEncodingBitRate(1800 * 1024);
            mMediaRecorder.setMaxFileSize(32 * 1024 * 1024);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.prepare();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                if (mVirtualDisplay != null) {
                    mVirtualDisplay.release();
                    mVirtualDisplay = null;
                }
                if (mMediaRecorder != null) {
                    mMediaRecorder.setOnErrorListener(null);
                    mMediaProjection.stop();
                    mMediaRecorder.release();
                }
                quitWorker();
                if (mMediaProjection != null) {
                    mMediaProjection.unregisterCallback(mProjectionCallback);

                }
                mMediaRecorder = null;
                mMediaProjection = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
            Log.e(TAG, "media recorder release");
        }

    }


    private void signalStop(boolean stopWithEOS) {
        Message msg = Message.obtain(myHandler, MSG_DONE, stopWithEOS ? STOP_WITH_EOS : 0, 0);
        myHandler.sendMessageAtFrontOfQueue(msg);
    }


    private void dealMedia(final boolean play) {
        this.play = play;
        targetFile = MediaUtil.getSaveDirectory() + System.currentTimeMillis() + "_muxAudio.mp4";
        String appName = "";

        if (mActivity.getPackageName().contains("tom")) {
            appName = "MyTalkingTom-";
        } else if (mActivity.getPackageName().contains("angela")) {
            appName = "MyTalkingAngela-";
        }
        combineVideoPath = MediaUtil.getSaveDirectory("Camera") + appName +
                UUID.randomUUID().toString().substring(0, 3) + ".mp4";

        new Thread(() -> {
            WavFileReader reader = new WavFileReader();
            try {
                if (reader.openFile(muxAudioPath)) {
                    Log.e(TAG, "有音频文件 ");
                    WavFileHeader wavFileHeader = reader.getmWavFileHeader();
                    final PCMEncoder pcmEncoder = new PCMEncoder(wavFileHeader.getmByteRate(), wavFileHeader.getmSampleRate(), 1);
                    pcmEncoder.setOutputPath(targetFile);
                    pcmEncoder.prepare();
                    InputStream inputStream = new FileInputStream(new File(muxAudioPath));
                    inputStream.skip(44);
                    pcmEncoder.encode(inputStream, wavFileHeader.getmSampleRate());
                    pcmEncoder.stop();
                }

                if (mWorker != null) throw new IllegalStateException();
                mWorker = new HandlerThread("MSG_DONE");
                mWorker.start();
                myHandler = new MyHandler(mWorker.getLooper(), mActivity);
                myHandler.sendEmptyMessageDelayed(MSG_DONE, 350);


            } catch (Exception e) {
                Log.e(TAG, "没有音频文件 " + e.getMessage());
                combineVideoPath = fileName;
                if (play) {
                    callSystemPlayer(mActivity, combineVideoPath, MediaRecord.this.provider);

                }
                saved = true;
                MediaScannerConnection.scanFile(mActivity, new String[]{combineVideoPath},
                        new String[]{"video/mp4"}, (path, uri) -> Log.e(TAG, "scan done"));
            }
        }).start();


    }

    @SuppressLint("SimpleDateFormat")
    private String getDateString() {
       SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        return df.format(new Date());
    }


    public void save(boolean canSave) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (canSave && !hasDelete) {
                if (saved) {
                    return;
                }
                dealMedia(false);
            } else {
                saved = false;
            }
        }


    }


    public void play(String provider) {
        this.provider = provider;
        if (saved) {
            callSystemPlayer(mActivity, combineVideoPath, this.provider);
        } else {
            dealMedia(true);
        }


    }


    public void stopRecord(String audioPath, boolean delete) {
        mIsRunning.set(false);
        saved = false;
        if (delete) {
            deleteFile();
        } else {
            if (null != audioPath && !audioPath.isEmpty()) {
                muxAudioPath = audioPath;
                Log.e(TAG, muxAudioPath);
            }
            hasDelete = false;


        }
    }


    private void record(final Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mIsRunning.get() || mForceQuit.get()) {
                throw new IllegalStateException();
            }
            if (mMediaProjection == null) {
                throw new IllegalStateException("maybe release");
            }
            mIsRunning.set(true);

            mMediaProjection.registerCallback(mProjectionCallback, myHandler);
            try {
                initMediaRecorder();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mVirtualDisplay = mMediaProjection.createVirtualDisplay(TAG + "-display", mWidth, mHeight, mDpi,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mMediaRecorder.getSurface(), null, null);
                }
                Log.i(TAG, "created virtual display: " + mVirtualDisplay);
                mMediaRecorder.start();
                startTime.set(System.currentTimeMillis());
                Log.i(TAG, "mediarecorder start");
            } catch (Exception e) {
                e.printStackTrace();
                activity.runOnUiThread(() -> Toast.makeText(activity, "启动录屏失败，音频设备被占用", Toast.LENGTH_SHORT).show());

            }
        }
    }


    private void deleteFile() {
        File file = new File(MediaUtil.getSaveDirectory());
        MediaUtil.deleteFile(file);
        hasDelete = true;
        Log.e(TAG, "hasDelete  called!");
    }


    private class MyHandler extends Handler {

        private final WeakReference<Activity> mActivityReference;


        MyHandler(Looper looper, Activity activity) {
            super(looper);
            mActivityReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_START:
                    try {
                        record(mActivityReference.get());
                        break;
                    } catch (Exception e) {
                        msg.obj = e;
                    }
                case MSG_DONE:
                    quitWorker();
                    prepareVideo(mActivityReference.get());
                    MediaScannerConnection.scanFile(mActivityReference.get(), new String[]{combineVideoPath},
                            new String[]{"video/mp4"}, (path, uri) -> Log.e(TAG, "scan done"));
                    break;


            }
        }

    }

    private void prepareVideo(final Activity activity) {
        try {
            if (RecordUtil.getInstance().muxM4AMp4(targetFile, fileName, combineVideoPath)) {
                if (play) {
                    callSystemPlayer(activity, combineVideoPath, MediaRecord.this.provider);

                }
                saved = true;

            }
            deleteFile();
        } catch (Exception e) {
            e.printStackTrace();
            saved = false;
        }
    }

    private void quitWorker() {
        if (mWorker != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mWorker.quitSafely();
            }
            mWorker = null;
        }
    }


    //调用VideoView
    private void callSystemPlayer(final Activity activity, String path, String provider) {

        Intent intent = new Intent();
        intent.setAction(android.content.Intent.ACTION_VIEW);
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri contentUri = FileProvider.getUriForFile(activity, provider, new File(path));
            intent.setDataAndType(contentUri, "video/*");
        } else {
            uri = Uri.parse("file://" + path);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(uri, "video/*");
        }
        activity.startActivity(intent);


        quitWorker();

    }


}


