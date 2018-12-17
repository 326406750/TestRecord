package lyx.testrecord;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

import static lyx.testrecord.ScreenRecorder.AUDIO_AAC;
import static lyx.testrecord.ScreenRecorder.VIDEO_AVC;

/**
 * Created by lyx on 2018/8/14.
 */

public class ScreenRecorderTest {
    private static final String TAG = "ScreenRecorderTest";

    private static final int INVALID_INDEX = -1;

    public static final int START_RECORD = 1;
    public static final int STOP_RECORD = 2;
    private static final int MSG_ERROR = 3;
    private static final int STOP_WITH_EOS = 4;


    private int mWidth;
    private int mHeight;
    private int mBitrate;
    private int mDpi;
    private MediaProjection mMediaProjection;
    private String mFilePath;

    private HandlerThread mThread;
    private CallBackHandler mHandler;

    private Surface mSurface;
    private VirtualDisplay mVirtualDisplay;
    private MediaMuxer mMuxer;

    private MediaCodec mVideoEncoder;
    private MediaCodec mAudioEncoder;

    /** 标志混合器的start()状态 **/
    private boolean mMuxerStarted = false;


    private MediaFormat mVideoMediaFormat;
    private MediaFormat mAudioMediaFormat;

    private long mVideoPtsOffset, mAudioPtsOffset;

    private int mVideoTrackIndex = INVALID_INDEX, mAudioTrackIndex = INVALID_INDEX;

    private LinkedList<Integer> mPendingVideoEncoderBufferIndices = new LinkedList<>();
    private LinkedList<Integer> mPendingAudioEncoderBufferIndices = new LinkedList<>();
    private LinkedList<MediaCodec.BufferInfo> mPendingAudioEncoderBufferInfos = new LinkedList<>();
    private LinkedList<MediaCodec.BufferInfo> mPendingVideoEncoderBufferInfos = new LinkedList<>();

    public ScreenRecorderTest(int width, int height, int bitrate, int dpi, MediaProjection mediaProjection, String filePath) {
        mWidth = width;
        mHeight = height;
        mBitrate = bitrate;
        mDpi = dpi;
        mMediaProjection = mediaProjection;
        mFilePath = filePath;
    }

    public void start() {
        mThread = new HandlerThread(TAG);
        mThread.run();
        mHandler = new CallBackHandler(mThread.getLooper());
        mHandler.sendEmptyMessage(START_RECORD);
    }

    /**
     * 开始录制
     */
    private void startRecord() {
        try {


            mMuxer = new MediaMuxer(mFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            prepareVideoEncoder();
            prepareAudioRecord();
        }catch (IOException e){
            Log.e(TAG, e.toString());
        }

        mVirtualDisplay = mMediaProjection.createVirtualDisplay(TAG + "-display",
                mWidth, mHeight, mDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                mSurface, null, null);
    }

    /**
     * 停止录制
     */
    private void stopRecord() {

    }

    /**
     * 准备视频解码器
     **/
    private void prepareVideoEncoder() throws IOException{
        mVideoMediaFormat = MediaFormat.createVideoFormat(VIDEO_AVC, mWidth, mHeight);
        mVideoMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mVideoMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitrate);
        mVideoMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30); //帧率
        mVideoMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);

        String mimeType = mVideoMediaFormat.getString(MediaFormat.KEY_MIME);
        mVideoEncoder = MediaCodec.createEncoderByType(mimeType);

        mVideoEncoder.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {

            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                muxVideo(index, info);
            }

            @Override
            public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {

            }

            @Override
            public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                mVideoMediaFormat = format;
                startMuxIfReady();
            }
        });

        mVideoEncoder.configure(mVideoMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mSurface = mVideoEncoder.createInputSurface(); //必须在encoder.configure()方法后及encoder.start()方法前调用
        mVideoEncoder.start();
    }


    /**
     * 准备音频解码器
     */
    private void prepareAudioRecord() throws IOException{
        MediaFormat mediaFormat = MediaFormat.createAudioFormat(AUDIO_AAC, 100, 1);
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectMain);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitrate);

        String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
        mAudioEncoder = MediaCodec.createEncoderByType(mimeType);

        mAudioEncoder.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {

            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                muxAudio(index, info);
            }

            @Override
            public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {

            }

            @Override
            public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                Log.d(TAG, "[" + Thread.currentThread().getId() + "] AudioEncoder returned new format " + format);
            }
        });

        mAudioEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mAudioEncoder.start();
    }

    private void startMuxIfReady(){
        if (mMuxerStarted || mVideoMediaFormat == null
                || (mAudioEncoder != null && mAudioMediaFormat == null)) {
            return;
        }

        mVideoTrackIndex = mMuxer.addTrack(mVideoMediaFormat);
        mAudioTrackIndex = mAudioEncoder == null ? INVALID_INDEX : mMuxer.addTrack(mAudioMediaFormat);
        mMuxer.start();
        mMuxerStarted = true;
        Log.i(TAG, "Started media muxer, videoIndex=" + mVideoTrackIndex);
        if (mPendingVideoEncoderBufferIndices.isEmpty() && mPendingAudioEncoderBufferIndices.isEmpty()) {
            return;
        }
        Log.i(TAG, "Mux pending video output buffers...");
        MediaCodec.BufferInfo info;
        while ((info = mPendingVideoEncoderBufferInfos.poll()) != null) {
            int index = mPendingVideoEncoderBufferIndices.poll();
            muxVideo(index, info);
        }
        if (mAudioEncoder != null) {
            while ((info = mPendingAudioEncoderBufferInfos.poll()) != null) {
                int index = mPendingAudioEncoderBufferIndices.poll();
                muxAudio(index, info);
            }
        }
    }

    private void muxVideo(int index, MediaCodec.BufferInfo buffer) {
        if(!mMuxerStarted || mVideoTrackIndex == INVALID_INDEX ){
            mPendingVideoEncoderBufferIndices.add(index);
            mPendingVideoEncoderBufferInfos.add(buffer);
            return;
        }

        ByteBuffer encodedData = mVideoEncoder.getOutputBuffer(index);
        writeSampleData(mVideoTrackIndex, buffer, encodedData);
        mVideoEncoder.releaseOutputBuffer(index,false);
        if ((buffer.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.d(TAG, "Stop encoder and muxer, since the buffer has been marked with EOS");
            // send release msg
            mVideoTrackIndex = INVALID_INDEX;
            signalStop(true);
        }
    }

    private void muxAudio(int index, MediaCodec.BufferInfo buffer) {
        if(!mMuxerStarted || mAudioTrackIndex == INVALID_INDEX ){
            mPendingAudioEncoderBufferIndices.add(index);
            mPendingAudioEncoderBufferInfos.add(buffer);
        }

        ByteBuffer encodedData = mAudioEncoder.getOutputBuffer(index);
        writeSampleData(mAudioTrackIndex, buffer, encodedData);
        mAudioEncoder.releaseOutputBuffer(index, false);
        if ((buffer.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.d(TAG, "Stop encoder and muxer, since the buffer has been marked with EOS");
            mAudioTrackIndex = INVALID_INDEX;
            signalStop(true);
        }
    }

    private void signalStop(boolean stopWithEOS) {
        Message msg = Message.obtain(mHandler, STOP_RECORD, stopWithEOS ? STOP_WITH_EOS : 0, 0);
        mHandler.sendMessageAtFrontOfQueue(msg);
    }

    private void writeSampleData(int track, MediaCodec.BufferInfo buffer, ByteBuffer encodedData) {
        if ((buffer.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            // The codec config data was pulled out and fed to the muxer when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status.
            // Ignore it.
            Log.d(TAG, "Ignoring BUFFER_FLAG_CODEC_CONFIG");
            buffer.size = 0;
        }
        boolean eos = (buffer.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        if (buffer.size == 0 && !eos) {
            Log.d(TAG, "info.size == 0, drop it.");
            encodedData = null;
        } else {
            if (buffer.presentationTimeUs != 0) { // maybe 0 if eos
                if (track == mVideoTrackIndex) {
                    resetVideoPts(buffer);
                } else if (track == mAudioTrackIndex) {
                    resetAudioPts(buffer);
                }
            }
                Log.d(TAG, "[" + Thread.currentThread().getId() + "] Got buffer, track=" + track
                        + ", info: size=" + buffer.size
                        + ", presentationTimeUs=" + buffer.presentationTimeUs);

            //用于通知用户仍在录制中
//            if (!eos && mCallback != null) {
//                mCallback.onRecording(buffer.presentationTimeUs);
//            }
        }
        if (encodedData != null) {
            encodedData.position(buffer.offset);
            encodedData.limit(buffer.offset + buffer.size);
            mMuxer.writeSampleData(track, encodedData, buffer);
                Log.i(TAG, "Sent " + buffer.size + " bytes to MediaMuxer on track " + track);
        }
    }

    private void resetAudioPts(MediaCodec.BufferInfo buffer) {
        if (mAudioPtsOffset == 0) {
            mAudioPtsOffset = buffer.presentationTimeUs;
            buffer.presentationTimeUs = 0;
        } else {
            buffer.presentationTimeUs -= mAudioPtsOffset;
        }
    }

    private void resetVideoPts(MediaCodec.BufferInfo buffer) {
        if (mVideoPtsOffset == 0) {
            mVideoPtsOffset = buffer.presentationTimeUs;
            buffer.presentationTimeUs = 0;
        } else {
            buffer.presentationTimeUs -= mVideoPtsOffset;
        }
    }


    class CallBackHandler extends Handler {

        CallBackHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case START_RECORD:
                    startRecord();
                    break;
                case STOP_RECORD:
                    stopRecord();
                    break;
            }
        }
    }
}
