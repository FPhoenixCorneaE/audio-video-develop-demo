package com.fphoenixcorneae.mediacodec.decode

import android.media.MediaCodec.BufferInfo

interface ISyncDecode {

    fun start()

    fun stop()

    fun release()

    fun mimePrefix(): String

    fun handleOutputData(bufferInfo: BufferInfo)
}