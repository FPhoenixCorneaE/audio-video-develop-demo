package com.fphoenixcorneae.mediacodec.decode

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer

class SyncDecodeAudioImpl : AbstractSyncDecode {

    private lateinit var mAudioTrack: AudioTrack

    constructor(
        path: String,
    ) : super(path, null)

    constructor(
        context: Context,
        uri: Uri,
    ) : super(context, uri, null)

    init {
        mMediaFormat?.let {
            // 采样位数
            val pcmEncoding = if (it.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                it.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }
            // 音频采样率
            val sampleRate = it.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            // 音频通道数
            val channelCount = it.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            // 获取声道
            val channelConfig = if (channelCount == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, pcmEncoding)
            // 设置音频信息属性
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            // 设置音频数据
            val audioFormat = AudioFormat.Builder()
                .setEncoding(pcmEncoding)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build()
            // 创建 AudioTrack
            mAudioTrack = AudioTrack(
                /* attributes = */ audioAttributes,
                /* format = */ audioFormat,
                /* bufferSizeInBytes = */ minBufferSize,
                /* mode = */ AudioTrack.MODE_STREAM,
                /* sessionId = */ AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
            // 等待解码播放
            mAudioTrack.play()
        }
    }

    override fun mimePrefix(): String = "audio"

    override fun handleOutputData(bufferInfo: MediaCodec.BufferInfo) {
        // 获取输出 buffer 下标
        var outputBufferId = mMediaCodec?.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
        while (outputBufferId != null && outputBufferId >= 0) {
            mMediaCodec?.getOutputBuffer(outputBufferId)?.let {
                // 写数据到 AudioTrack
                mAudioTrack.write(it, bufferInfo.size, AudioTrack.WRITE_BLOCKING)
            }
            // 释放buffer，实现音频播放
            mMediaCodec?.releaseOutputBuffer(outputBufferId, true)
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                // 所有解码后的帧都被渲染
                break
            }
            outputBufferId = mMediaCodec?.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
        }
    }

    override fun stop() {
        super.stop()
        runCatching {
            if (mAudioTrack.state != AudioTrack.STATE_INITIALIZED) {
                mAudioTrack.stop()
            }
        }.onFailure {
            it.printStackTrace()
        }
    }

    override fun release() {
        super.release()
        runCatching {
            if (mAudioTrack.state != AudioTrack.STATE_INITIALIZED) {
                mAudioTrack.stop()
            }
            mAudioTrack.release()
        }.onFailure {
            it.printStackTrace()
        }
    }
}