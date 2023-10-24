package com.fphoenixcorneae.mediacodec.decode

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodec.Callback
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class AbstractAsyncDecode : IAsyncDecode {

    companion object {
        private const val TAG = "AsyncDecode"
    }

    protected val mMediaExtractor by lazy { MediaExtractor() }
    protected var mMediaFormat: MediaFormat? = null
    protected var mMediaCodec: MediaCodec? = null
    private var mQueueInputBufferJob: Job? = null

    constructor(path: String, surface: Surface? = null) {
        runCatching {
            mMediaExtractor.setDataSource(path, null)
            getTrack2Decode(surface)
            setCallback()
        }.onFailure {
            it.printStackTrace()
        }
    }

    constructor(context: Context, uri: Uri, surface: Surface? = null) {
        runCatching {
            mMediaExtractor.setDataSource(context, uri, null)
            getTrack2Decode(surface)
            setCallback()
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

    private fun setCallback() {
        mMediaCodec?.setCallback(object : Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                inputBufferAvailable(codec, index)
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                outputBufferAvailable(codec, index, info)
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            }
        })
    }

    override fun inputBufferAvailable(codec: MediaCodec, index: Int) {
        mQueueInputBufferJob?.cancel()
        mQueueInputBufferJob = CoroutineScope(Dispatchers.IO).launch {
            mMediaCodec?.getInputBuffer(index)?.let { inputBuffer ->
                // 先清空数据
                inputBuffer.clear()
                // 读取当前帧的数据
                val sampleDataSize = mMediaExtractor.readSampleData(inputBuffer, 0)
                if (sampleDataSize > 0) {
                    // 当前时间戳
                    val sampleTime = mMediaExtractor.sampleTime
                    // 当前帧的标志位
                    val sampleFlags = mMediaExtractor.sampleFlags
                    // 解析数据
                    codec.queueInputBuffer(
                        /* index = */ index,
                        /* offset = */ 0,
                        /* size = */ sampleDataSize,
                        /* presentationTimeUs = */ sampleTime,
                        /* flags = */ sampleFlags,
                    )
                    // 进入下一帧
                    mMediaExtractor.advance()
                } else {
                    // 解析结束
                    codec.queueInputBuffer(
                        /* index = */ index,
                        /* offset = */ 0,
                        /* size = */ 0,
                        /* presentationTimeUs = */ 0,
                        /* flags = */ MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }
            }
        }
    }

    override fun start() {
        // 开始进行编解码
        mMediaCodec?.start()
        Log.d(TAG, "开始解码...")
    }

    override fun stop() {
        mQueueInputBufferJob?.cancel()
        runCatching {
            mMediaCodec?.stop()
        }.onFailure {
            it.printStackTrace()
        }
    }

    override fun release() {
        Log.d(TAG, "释放资源")
        stop()
        mMediaCodec?.release()
        mMediaExtractor.release()
    }
}