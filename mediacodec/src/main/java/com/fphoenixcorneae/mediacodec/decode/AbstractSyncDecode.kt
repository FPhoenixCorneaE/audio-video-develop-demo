package com.fphoenixcorneae.mediacodec.decode

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import android.view.Surface

abstract class AbstractSyncDecode : ISyncDecode, Runnable {

    companion object {
        private const val TAG = "SyncDecode"
        const val TIMEOUT_US = 10000L
    }

    private val mBufferInfo by lazy { BufferInfo() }
    protected val mMediaExtractor by lazy { MediaExtractor() }
    protected var mMediaFormat: MediaFormat? = null
    protected var mMediaCodec: MediaCodec? = null
    private lateinit var mState: State

    constructor(path: String, surface: Surface? = null) {
        runCatching {
            mMediaExtractor.setDataSource(path, null)
            getTrack2Decode(surface)
            start()
        }.onFailure {
            it.printStackTrace()
        }
    }

    constructor(context: Context, uri: Uri, surface: Surface? = null) {
        runCatching {
            mMediaExtractor.setDataSource(context, uri, null)
            getTrack2Decode(surface)
            start()
        }.onFailure {
            it.printStackTrace()
        }
    }

    private fun getTrack2Decode(surface: Surface?) {
        run {
            for (i in 0 until mMediaExtractor.trackCount) {
                // 根据轨道 id 获取 MediaFormat
                val mediaFormat = mMediaExtractor.getTrackFormat(i)
                // 获取 mime 类型
                val mime = mediaFormat.getString(MediaFormat.KEY_MIME)
                // 获取轨道
                if (mime?.startsWith(mimePrefix()) == true) {
                    mMediaFormat = mediaFormat
                    // 选择要解析的轨道
                    mMediaExtractor.selectTrack(i)
                    // 创建 MediaCodec
                    mMediaCodec = MediaCodec.createDecoderByType(mime)
                    mMediaCodec?.configure(mMediaFormat, surface, null, 0)
                    return@run
                }
            }
        }
    }

    override fun run() {
        mState = State.Decoding
        Log.d(TAG, "开始解码")
        runCatching {
            if (mMediaCodec == null) {
                return@runCatching
            }
            while (mState == State.Decoding) {
                // 延迟 TIMEOUT_US 等待拿到空的 input buffer 下标，单位为 us
                // -1 表示一直等待，直到拿到数据，0 表示立即返回
                val inputBufferId = mMediaCodec!!.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferId >= 0) {
                    // 拿到空的 input buffer
                    mMediaCodec?.getInputBuffer(inputBufferId)?.let {
                        // 先清空数据
                        it.clear()
                        // 读取当前帧的数据
                        val sampleDataSize = mMediaExtractor.readSampleData(it, 0)
                        if (sampleDataSize > 0) {
                            // 当前时间戳
                            val sampleTime = mMediaExtractor.sampleTime
                            // 当前帧的标志位
                            val sampleFlags = mMediaExtractor.sampleFlags
                            // 解析数据
                            mMediaCodec?.queueInputBuffer(
                                /* index = */ inputBufferId,
                                /* offset = */ 0,
                                /* size = */ sampleDataSize,
                                /* presentationTimeUs = */ sampleTime,
                                /* flags = */ sampleFlags,
                            )
                            // 进入下一帧
                            mMediaExtractor.advance()
                        } else {
                            // 解析结束
                            mMediaCodec?.queueInputBuffer(
                                /* index = */ inputBufferId,
                                /* offset = */ 0,
                                /* size = */ 0,
                                /* presentationTimeUs = */ 0,
                                /* flags = */ MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            mState = State.DecodeFinished
                        }
                    }
                }
                handleOutputData(mBufferInfo)
            }
            // 释放资源
            release()
        }.onFailure {
            it.printStackTrace()
            mState = State.DecodeFailed
        }
    }

    override fun start() {
        // 开始进行编解码
        mMediaCodec?.start()
        mState = State.WaitingDecode
        Log.d(TAG, "等待解码...")
    }

    override fun stop() {
        Log.d(TAG, "停止解码")
        runCatching {
            if (mState == State.Decoding) {
                mState = State.DecodeInterrupt
                mMediaCodec?.stop()
            }
        }.onFailure {
            it.printStackTrace()
        }
    }

    /**
     * 释放资源
     */
    override fun release() {
        Log.d(TAG, "释放资源")
        runCatching {
            mMediaCodec?.stop()
            mMediaCodec?.reset()
            mMediaCodec?.release()
            mMediaExtractor.release()
        }.onFailure {
            it.printStackTrace()
        }
    }

    internal enum class State {
        WaitingDecode,
        Decoding,
        DecodeInterrupt,
        DecodeFinished,
        DecodeFailed,
    }
}