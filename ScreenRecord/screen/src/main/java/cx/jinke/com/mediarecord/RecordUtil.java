package cx.jinke.com.mediarecord;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.FileDataSourceImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AACTrackImpl;
import com.googlecode.mp4parser.authoring.tracks.MP3TrackImpl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecordUtil {
    private static final String TAG = RecordUtil.class.getSimpleName();


    private final static int ALLOCATE_BUFFER = 1024 * 1024 * 5;

    private static class RecordUtilHolder {
        private static final RecordUtil INSTANCE = new RecordUtil();
    }

    private RecordUtil() {
    }

    public static final RecordUtil getInstance() {
        return RecordUtilHolder.INSTANCE;
    }

    /**
     * split/extract video or audio through MediaExtractor
     */
    public boolean extractVideo(String source, String outPath) {
        try {


            MediaMuxer muxer = null;
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(source);

            int mVideoTrackIndex = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (!mime.startsWith("video")) {
                    continue;
                }
                extractor.selectTrack(i);
                muxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                mVideoTrackIndex = muxer.addTrack(format);
                muxer.start();
            }

            if (muxer == null) {
                return false;
            }

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            info.presentationTimeUs = 0;
            ByteBuffer buffer = ByteBuffer.allocate(ALLOCATE_BUFFER);
            while (true) {
                int sampleSize = extractor.readSampleData(buffer, 0);
                if (sampleSize < 0) {
                    break;
                }
                info.offset = 0;
                info.size = sampleSize;
                info.flags = extractor.getSampleFlags();

                info.presentationTimeUs = extractor.getSampleTime();
                muxer.writeSampleData(mVideoTrackIndex, buffer, info);
                extractor.advance();
            }
            extractor.release();
            muxer.stop();
            muxer.release();
        } catch (Exception e) {

        }

        return true;
    }

    public boolean extractAudio(String source, String outPath) throws IOException {
        MediaMuxer muxer = null;
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(source);


        int mAudioTrackIndex = -1;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (!mime.startsWith("audio")) {
                continue;
            }
            extractor.selectTrack(i);
            muxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mAudioTrackIndex = muxer.addTrack(format);
            muxer.start();
        }

        if (muxer == null) {
            return false;
        }

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        info.presentationTimeUs = 0;
        ByteBuffer buffer = ByteBuffer.allocate(ALLOCATE_BUFFER);
        while (true) {
            int sampleSize = extractor.readSampleData(buffer, 0);
            if (sampleSize < 0) {
                break;
            }
            info.offset = 0;
            info.size = sampleSize;
            info.flags = extractor.getSampleFlags();
            info.presentationTimeUs = extractor.getSampleTime();
            muxer.writeSampleData(mAudioTrackIndex, buffer, info);
            extractor.advance();
        }
        extractor.release();
        muxer.stop();
        muxer.release();

        return true;
    }


    public boolean combineMedia(String videoPath, String audioPath, String outPath) {
        MediaExtractor videoExtractor = new MediaExtractor();
        try {
            videoExtractor.setDataSource(videoPath);

            MediaFormat videoFormat = null;
            int videoTrackIndex = -1;
            int videoTrackCount = videoExtractor.getTrackCount();
            for (int i = 0; i < videoTrackCount; i++) {
                videoFormat = videoExtractor.getTrackFormat(i);
                String mime = videoFormat.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i;
                    break;
                }
            }

            MediaExtractor audioExtractor = new MediaExtractor();

            audioExtractor.setDataSource(audioPath);

            MediaFormat audioFormat = null;
            int audioTrackIndex = -1;
            int audioTrackCount = audioExtractor.getTrackCount();
            for (int i = 0; i < audioTrackCount; i++) {
                audioFormat = audioExtractor.getTrackFormat(i);
                String mime = audioFormat.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    break;
                }
            }

            videoExtractor.selectTrack(videoTrackIndex);
            audioExtractor.selectTrack(audioTrackIndex);

            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();


            MediaMuxer mediaMuxer = null;
            try {
                mediaMuxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } catch (IOException e) {
                e.printStackTrace();
            }
            int writeVideoTrackIndex = mediaMuxer.addTrack(videoFormat);
            int writeAudioTrackIndex = mediaMuxer.addTrack(audioFormat);
            mediaMuxer.start();
            ByteBuffer byteBuffer = ByteBuffer.allocate(ALLOCATE_BUFFER);
            long videoSampleTime = getSampleTime(videoExtractor, byteBuffer);
            videoExtractor.unselectTrack(videoTrackIndex);
            videoExtractor.selectTrack(videoTrackIndex);
            while (true) {
                int readVideoSampleSize = videoExtractor.readSampleData(byteBuffer, 0);
                if (readVideoSampleSize < 0) {
                    break;
                }
                videoBufferInfo.size = readVideoSampleSize;
                videoBufferInfo.presentationTimeUs += videoSampleTime;
                videoBufferInfo.offset = 0;
                videoBufferInfo.flags = videoExtractor.getSampleFlags();
                mediaMuxer.writeSampleData(writeVideoTrackIndex, byteBuffer, videoBufferInfo);
                videoExtractor.advance();
            }
            long audioSampleTime = getSampleTime(videoExtractor, byteBuffer);
            while (true) {
                int readAudioSampleSize = audioExtractor.readSampleData(byteBuffer, 0);
                if (readAudioSampleSize < 0) {
                    break;
                }
                audioBufferInfo.size = readAudioSampleSize;
                audioBufferInfo.presentationTimeUs += audioSampleTime;
                audioBufferInfo.offset = 0;
                audioBufferInfo.flags = videoExtractor.getSampleFlags();
                mediaMuxer.writeSampleData(writeAudioTrackIndex, byteBuffer, audioBufferInfo);
                audioExtractor.advance();

            }
            mediaMuxer.stop();
            mediaMuxer.release();
            videoExtractor.release();
            audioExtractor.release();
            return true;
        } catch (Exception e) {
            return false;
        }


    }


    /**
     * 获取每帧的之间的时间
     *
     * @return
     */
    private long getSampleTime(MediaExtractor mediaExtractor, ByteBuffer byteBuffer) {
        long sampleTime;
        mediaExtractor.readSampleData(byteBuffer, 0);
        if (mediaExtractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC) {
            mediaExtractor.advance();
        }
        mediaExtractor.readSampleData(byteBuffer, 0);
        long firstVideoPTS = mediaExtractor.getSampleTime();
        mediaExtractor.advance();
        mediaExtractor.readSampleData(byteBuffer, 0);
        long SecondVideoPTS = mediaExtractor.getSampleTime();
        sampleTime = Math.abs(SecondVideoPTS - firstVideoPTS);
        Log.d(TAG, "videoSampleTime is " + sampleTime);
        return sampleTime;
    }

    //合并完美方案
    public boolean muxMedia(String videoFilePath, String audioFilePath, String outputFile) {
        try {
            MediaExtractor videoExtractor = new MediaExtractor();
            videoExtractor.setDataSource(videoFilePath);
            MediaExtractor audioExtractor = new MediaExtractor();
            audioExtractor.setDataSource(audioFilePath);
            MediaMuxer muxer = new MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            videoExtractor.selectTrack(0);
            MediaFormat videoFormat = videoExtractor.getTrackFormat(0);
            int videoTrack = muxer.addTrack(videoFormat);
            audioExtractor.selectTrack(0);
            MediaFormat audioFormat = audioExtractor.getTrackFormat(0);
            int audioTrack = muxer.addTrack(audioFormat);
            Log.d(TAG, "Video Format " + videoFormat.toString());
            Log.d(TAG, "Audio Format " + audioFormat.toString());
            boolean sawEOS = false;
            int frameCount = 0;
            int offset = 100;
            int sampleSize = ALLOCATE_BUFFER;
            ByteBuffer videoBuf = ByteBuffer.allocate(sampleSize);
            ByteBuffer audioBuf = ByteBuffer.allocate(sampleSize);
            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
            videoExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            muxer.start();
            while (!sawEOS) {
                videoBufferInfo.offset = offset;
                videoBufferInfo.size = videoExtractor.readSampleData(videoBuf, offset);
                if (videoBufferInfo.size < 0 || audioBufferInfo.size < 0) {
                    sawEOS = true;
                    videoBufferInfo.size = 0;
                } else {
                    videoBufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                    //noinspection WrongConstant
                    videoBufferInfo.flags = videoExtractor.getSampleFlags();
                    muxer.writeSampleData(videoTrack, videoBuf, videoBufferInfo);
                    videoExtractor.advance();
                    frameCount++;
                }
            }

            boolean sawEOS2 = false;
            int frameCount2 = 0;
            while (!sawEOS2) {
                frameCount2++;
                audioBufferInfo.offset = offset;
                audioBufferInfo.size = audioExtractor.readSampleData(audioBuf, offset);
                if (videoBufferInfo.size < 0 || audioBufferInfo.size < 0) {
                    sawEOS2 = true;
                    audioBufferInfo.size = 0;
                } else {
                    audioBufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                    //noinspection WrongConstant
                    audioBufferInfo.flags = audioExtractor.getSampleFlags();
                    muxer.writeSampleData(audioTrack, audioBuf, audioBufferInfo);
                    audioExtractor.advance();
                }
            }
            muxer.stop();
            muxer.release();
            Log.d(TAG, "Output: " + outputFile);
            return true;
        } catch (IOException e) {
            Log.d(TAG, "Mixer Error 1 " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.d(TAG, "Mixer Error 2 " + e.getMessage());
            return false;
        }
    }

    /**
     * 将 AAC 和 MP4 进行混合[替换了视频的音轨]
     *
     * @param aacPath .aac
     * @param mp4Path .mp4
     * @param outPath .mp4
     */
    public boolean muxAacMp4(String aacPath, String mp4Path, String outPath) {
        AtomicBoolean flag = new AtomicBoolean(false);
        try {
            AACTrackImpl aacTrack = new AACTrackImpl(new FileDataSourceImpl(aacPath));
            Movie videoMovie = MovieCreator.build(mp4Path);
            Track videoTracks = null;// 获取视频的单纯视频部分
            for (Track videoMovieTrack : videoMovie.getTracks()) {
                if ("vide".equals(videoMovieTrack.getHandler())) {
                    videoTracks = videoMovieTrack;
                }
            }

            Movie resultMovie = new Movie();
            resultMovie.addTrack(videoTracks);// 视频部分
            resultMovie.addTrack(aacTrack);// 音频部分

            Container out = new DefaultMp4Builder().build(resultMovie);
            FileOutputStream fos = new FileOutputStream(new File(outPath));
            out.writeContainer(fos.getChannel());
            fos.close();
            flag.set(true);
            Log.e("update_tag", "merge finish");
        } catch (Exception e) {
            e.printStackTrace();
            flag.set(false);
        }
        return flag.get();
    }

    /**
     * 将 AAC 和 MP4 进行混合[替换了视频的音轨]
     *
     * @param mp3Path .mp3
     * @param mp4Path .mp4
     * @param outPath .mp4
     */
    public boolean muxMp3Mp4(String mp3Path, String mp4Path, String outPath) {
        AtomicBoolean flag = new AtomicBoolean(false);
        try {
            MP3TrackImpl mp3Track = new MP3TrackImpl(new FileDataSourceImpl(mp3Path));
            Movie videoMovie = MovieCreator.build(mp4Path);
            Track videoTracks = null;// 获取视频的单纯视频部分
            for (Track videoMovieTrack : videoMovie.getTracks()) {
                if ("vide".equals(videoMovieTrack.getHandler())) {
                    videoTracks = videoMovieTrack;
                }
            }

            Movie resultMovie = new Movie();
            resultMovie.addTrack(Objects.requireNonNull(videoTracks));// 视频部分
            resultMovie.addTrack(mp3Track);// 音频部分

            Container out = new DefaultMp4Builder().build(resultMovie);
            FileOutputStream fos = new FileOutputStream(new File(outPath));
            out.writeContainer(fos.getChannel());
            fos.close();
            flag.set(true);
            Log.e("update_tag", "merge finish");
        } catch (Exception e) {
            e.printStackTrace();
            flag.set(false);
        }
        return flag.get();
    }


    /**
     * 将 M4A 和 MP4 进行混合[替换了视频的音轨]
     *
     * @param m4aPath .m4a[同样可以使用.mp4]
     * @param mp4Path .mp4
     * @param outPath .mp4
     */
    public boolean muxM4AMp4(String m4aPath, String mp4Path, String outPath) {
        Movie audioMovie ;
        AtomicBoolean flag = new AtomicBoolean(false);
        try {
            audioMovie = MovieCreator.build(m4aPath);
            Track audioTracks = null;// 获取视频的单纯音频部分
            for (Track audioMovieTrack : audioMovie.getTracks()) {
                if ("soun".equals(audioMovieTrack.getHandler())) {
                    audioTracks = audioMovieTrack;
                }
            }

            Movie videoMovie = MovieCreator.build(mp4Path);
            Track videoTracks = null;// 获取视频的单纯视频部分
            for (Track videoMovieTrack : videoMovie.getTracks()) {
                if ("vide".equals(videoMovieTrack.getHandler())) {
                    videoTracks = videoMovieTrack;
                }
            }

            Movie resultMovie = new Movie();
            resultMovie.addTrack(videoTracks);// 视频部分
            resultMovie.addTrack(audioTracks);// 音频部分

            Container out = new DefaultMp4Builder().build(resultMovie);
            FileOutputStream fos = new FileOutputStream(new File(outPath));
            out.writeContainer(fos.getChannel());
            fos.close();
            flag.set(true);
        } catch (Exception e) {
            e.printStackTrace();
            flag.set(false);
        }
        return flag.get();

    }


}
