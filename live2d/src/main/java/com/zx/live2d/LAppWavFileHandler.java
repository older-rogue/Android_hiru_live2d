package com.zx.live2d;

import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import java.io.IOException;

/*
 * 音频处理器 - 播放动作关联的音频文件
 * 继承Thread以在新线程中播放
 */
public class LAppWavFileHandler extends Thread {

    // 构造函数
    public LAppWavFileHandler(String filePath) {
        this.filePath = filePath;
    }

    // 线程入口
    @Override
    public void run() {
        loadWavFile();
    }

    // 加载并播放WAV音频
    public void loadWavFile() {
        // API 24以下不支持
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }

        // 创建MediaExtractor
        MediaExtractor mediaExtractor = new MediaExtractor();
        try {
            // 从assets打开音频文件
            AssetFileDescriptor afd = LAppDelegate.getInstance().getActivity().getAssets().openFd(filePath);
            mediaExtractor.setDataSource(afd);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // 获取音频格式
        MediaFormat mf = mediaExtractor.getTrackFormat(0);
        int samplingRate = mf.getInteger(MediaFormat.KEY_SAMPLE_RATE);

        // 计算最小缓冲区大小
        int bufferSize = AudioTrack.getMinBufferSize(
            samplingRate,
            AudioFormat.CHANNEL_OUT_MONO,         // 单声道
            AudioFormat.ENCODING_PCM_16BIT        // 16位PCM
        );

        // 创建AudioTrack
        AudioTrack audioTrack;
        audioTrack = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            .setAudioFormat(new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(samplingRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(bufferSize)
            .build();
        audioTrack.play();

        // 跳过开头的杂音
        int offset = 100;
        byte[] voiceBuffer = LAppPal.loadFileAsBytes(filePath);
        audioTrack.write(voiceBuffer, offset, voiceBuffer.length - offset);
    }

    private final String filePath;  // 音频文件路径
}
