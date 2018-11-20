package cx.jinke.com.mediarecord;

import android.app.Activity;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.iceteck.silicompressorr.SiliCompressor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class MediaRecord extends Thread {
    private static final String TAG = "MediaRecord";
    private int mWidth;
    private int mHeight;
    private int mDpi;
    private MediaRecorder mMediaRecorder;
    private MediaProjection mMediaProjection;
    private static final int FRAME_RATE = 15; // 15 fps
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
    private static final int MSG_PLAY = 12;
    private static final int STOP_WITH_EOS = 1;
    private HandlerThread mWorker;
    private MyHandler myHandler;
    private AtomicBoolean mForceQuit = new AtomicBoolean(false);
    private AtomicBoolean mIsRunning = new AtomicBoolean(false);
    private VirtualDisplay mVirtualDisplay;
    private AtomicLong startTime = new AtomicLong();
    private AtomicLong stopTime = new AtomicLong();
    private String provider;


    private MediaProjection.Callback mProjectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
        }
    };


    public MediaRecord(int dpi, MediaProjection mp, Activity activity) {
        mDpi = dpi;
        mMediaProjection = mp;
        mActivity = activity;

    }


    public final void quit() {
        mForceQuit.set(true);
        if (!mIsRunning.get()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                stopTime.set(System.currentTimeMillis());
                release();
            }
        } else {
            signalStop(false);
        }

    }


    @Override
    public void run() {
        if (mWorker != null) throw new IllegalStateException();
        mWorker = new HandlerThread(TAG);
        mWorker.start();
        myHandler = new MyHandler(mWorker.getLooper(), new WeakReference<>(mActivity));
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
        getScreenParams();
        try {
            mMediaRecorder = new MediaRecorder();
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
//            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            fileName = MediaUtil.getSaveDirectory() + System.currentTimeMillis() + ".mp4";
            mMediaRecorder.setOutputFile(fileName);
            mMediaRecorder.setVideoSize(mWidth, mHeight);
            mMediaRecorder.setVideoFrameRate(FRAME_RATE);
            Log.e(TAG, "mWidth=" + mWidth + "mHeight=" + mHeight);
            mMediaRecorder.setVideoEncodingBitRate(1024 * 1024 * 3);
            mMediaRecorder.setMaxFileSize(32 * 1024 * 1024);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
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


    private void dealMedia(boolean play) {
        this.play = play;
        targetFile = MediaUtil.getSaveDirectory() + System.currentTimeMillis() + "_muxAudio.mp4";
        String dateStr = getDateString();
        combineVideoPath = MediaUtil.getSaveDirectory("Camera") + "MyTalkingTom-" +
                UUID.randomUUID().toString().substring(0, 3) + ".mp4";

        new Thread(new Runnable() {
            @Override
            public void run() {
                WavFileReader reader = new WavFileReader();
                try {
                    if (reader.openFile(muxAudioPath)) {
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
                    myHandler = new MyHandler(mWorker.getLooper(), new WeakReference<>(mActivity));
                    myHandler.sendEmptyMessageDelayed(MSG_DONE, 350);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();


    }

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
            callSystemPlayer(this.provider);
        } else {
            dealMedia(true);
        }


    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
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


    private void record() {
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
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mActivity, "启动录屏失败，音频设备被占用", Toast.LENGTH_SHORT).show();
                    }
                });

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


        MyHandler(Looper looper, WeakReference<Activity> mActivityReference) {
            super(looper);
            this.mActivityReference = mActivityReference;
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_START:
                    try {
                        record();
                        break;
                    } catch (Exception e) {
                        msg.obj = e;
                    }
                case MSG_DONE:
                    quitWorker();
                    prepareVideo();
                    MediaScannerConnection.scanFile(mActivity, new String[]{combineVideoPath},
                            new String[]{"video/mp4"}, new MediaScannerConnection.OnScanCompletedListener() {
                                @Override
                                public void onScanCompleted(String path, Uri uri) {
                                     Log.e(TAG,"scan done");
                                }
                            });
                    break;


            }
        }

        public WeakReference<Activity> getActivityReference() {
            return mActivityReference;
        }
    }

    private void prepareVideo() {
        try {
            if (RecordUtil.getInstance().muxM4AMp4(targetFile, fileName, combineVideoPath)) {
                if (play) {
                    callSystemPlayer(MediaRecord.this.provider);

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
            mWorker.quitSafely();
            mWorker = null;
        }
    }


    //调用VideoView
    private void callSystemPlayer(String provider) {

        Intent intent = new Intent();
        intent.setAction(android.content.Intent.ACTION_VIEW);
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri contentUri = FileProvider.getUriForFile(mActivity, provider, new File(combineVideoPath));
            intent.setDataAndType(contentUri, "video/*");
        } else {
            uri = Uri.parse(combineVideoPath);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(uri, "video/*");
        }
        mActivity.startActivity(intent);


        quitWorker();

    }


}


