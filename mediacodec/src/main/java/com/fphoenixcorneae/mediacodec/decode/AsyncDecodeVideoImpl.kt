package com.fphoenixcorneae.mediacodec.decode

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.net.Uri
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AsyncDecodeVideoImpl : AbstractAsyncDecode {

    /** 用于对准视频的时间戳 */
    private val mStartTimeMillis by lazy { System.currentTimeMillis() }

    constructor(
        path: String,
        surfaceTexture: SurfaceTexture?,
    ) : super(path, Surface(surfaceTexture))

    constructor(
        context: Context,
        uri: Uri,
        surfaceTexture: SurfaceTexture?,
    ) : super(context, uri, Surface(surfaceTexture))

    /**
     * 矫正视频显示时间戳
     */
    private suspend fun adjustTimestamp(bufferInfo: MediaCodec.BufferInfo) {
        // 注意这里是以 0 为初始目标的，bufferInfo.presentationTimeUs 的单位为微秒
        // 这里用系统时间来模拟两帧的时间差
        val ptsTimes = bufferInfo.presentationTimeUs / 1000
        val systemTimes = System.currentTimeMillis() - mStartTimeMillis
        val timeDiff = ptsTimes - systemTimes
        if (timeDiff > 0) {
            // 如果当前帧比系统时间差快了，则延时一下
            delay(timeDiff)
        }
    }

    override fun outputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
        CoroutineScope(Dispatchers.IO).launch {
            adjustTimestamp(info)
            mMediaCodec?.releaseOutputBuffer(index, true)
        }
    }

    override fun mimePrefix(): String = "video"
}