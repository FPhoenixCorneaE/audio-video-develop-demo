package com.fphoenixcorneae.mediacodec.decode

import android.media.MediaCodec

interface IAsyncDecode {

    fun inputBufferAvailable(codec: MediaCodec, index: Int)

    fun outputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo)

    fun start()

    fun stop()

    fun release()

    fun mimePrefix(): String
}