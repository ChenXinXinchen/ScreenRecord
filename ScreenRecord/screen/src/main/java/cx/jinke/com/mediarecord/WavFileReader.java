package cx.jinke.com.mediarecord;


import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class WavFileReader {

    private static final String TAG = WavFileReader.class.getSimpleName();

    private DataInputStream mDataInputStream;
    private WavFileHeader mWavFileHeader;

    public boolean openFile(String filepath) throws IOException {
        if (mDataInputStream != null) {
            closeFile();
        }
        mDataInputStream = new DataInputStream(new FileInputStream(filepath));
        return readHeader();
    }

    public void closeFile() throws IOException {
        if (mDataInputStream != null) {
            mDataInputStream.close();
            mDataInputStream = null;
        }
    }

    public WavFileHeader getmWavFileHeader() {
        return mWavFileHeader;
    }

    public int readData(byte[] buffer, int offset, int count) {
        if (mDataInputStream == null || mWavFileHeader == null) {
            return -1;
        }

        try {
            int nbytes = mDataInputStream.read(buffer, offset, count);
            if (nbytes == -1) {
                return 0;
            }
            return nbytes;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return -1;
    }

    private boolean readHeader() {
        if (mDataInputStream == null) {
            return false;
        }

        WavFileHeader header = new WavFileHeader();

        byte[] intValue = new byte[4];
        byte[] shortValue = new byte[2];

        try {
            header.mChunkID = "" + (char) mDataInputStream.readByte() + (char) mDataInputStream.readByte() + (char) mDataInputStream.readByte() + (char) mDataInputStream.readByte();
            mDataInputStream.read(intValue);
            header.mChunkSize = byteArrayToInt(intValue);
            header.mFormat = "" + (char) mDataInputStream.readByte() + (char) mDataInputStream.readByte() + (char) mDataInputStream.readByte() + (char) mDataInputStream.readByte();
            header.mSubChunk1ID = "" + (char) mDataInputStream.readByte() + (char) mDataInputStream.readByte() + (char) mDataInputStream.readByte() + (char) mDataInputStream.readByte();
            mDataInputStream.read(intValue);
            header.mSubChunk1Size = byteArrayToInt(intValue);
            mDataInputStream.read(shortValue);
            header.mAudioFormat = byteArrayToShort(shortValue);
            mDataInputStream.read(shortValue);
            header.mNumChannel = byteArrayToShort(shortValue);
            mDataInputStream.read(intValue);
            header.mSampleRate = byteArrayToInt(intValue);
            mDataInputStream.read(intValue);
            header.mByteRate = byteArrayToInt(intValue);
            mDataInputStream.read(shortValue);
            header.mBlockAlign = byteArrayToShort(shortValue);
            mDataInputStream.read(shortValue);
            header.mBitsPerSample = byteArrayToShort(shortValue);
            header.mSubChunk2ID = "" + (char) mDataInputStream.readByte() + (char) mDataInputStream.readByte() + (char) mDataInputStream.readByte() + (char) mDataInputStream.readByte();
            mDataInputStream.read(intValue);
            header.mSubChunk2Size = byteArrayToInt(intValue);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        mWavFileHeader = header;

        return true;
    }

    private static short byteArrayToShort(byte[] b) {
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    private static int byteArrayToInt(byte[] b) {
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }
}
