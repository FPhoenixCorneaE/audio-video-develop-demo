package com.fphoenixcorneae.mediacodec.decode

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.net.Uri
import android.view.Surface

class SyncDecodeVideoImpl : AbstractSyncDecode {

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
    private fun adjustTimestamp(bufferInfo: MediaCodec.BufferInfo) {
        // 注意这里是以 0 为初始目标的，bufferInfo.presentationTimeUs 的单位为微秒
        val ptsTimes = bufferInfo.presentationTimeUs / 1000
        val systemTimes = System.currentTimeMillis() - mStartTimeMillis
        val timeDiff = ptsTimes - systemTimes
        if (timeDiff > 0) {
            // 如果当前帧比系统时间差快了，则延时一下
            runCatching {
                Thread.sleep(timeDiff)
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    override fun mimePrefix(): String = "video"

    override fun handleOutputData(bufferInfo: MediaCodec.BufferInfo) {
        // 获取输出 buffer 下标
        var outputBufferId = mMediaCodec?.dequeueOutputBuffer(bufferInfo, TIME_US)

        while (outputBufferId != null && outputBufferId >= 0) {
            // 矫正 pts
            adjustTimestamp(bufferInfo)
            // 释放buffer，并渲染到 Surface 中
            mMediaCodec?.releaseOutputBuffer(outputBufferId, true)
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                // 所有解码后的帧都被渲染
                break
            }
            outputBufferId = mMediaCodec?.dequeueOutputBuffer(bufferInfo, TIME_US)
        }
    }
}