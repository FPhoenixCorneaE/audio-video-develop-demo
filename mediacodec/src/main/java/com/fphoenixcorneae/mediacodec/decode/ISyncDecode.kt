package com.fphoenixcorneae.mediacodec.decode

import android.media.MediaCodec.BufferInfo

interface ISyncDecode {

    fun mimePrefix(): String

    fun handleOutputData(bufferInfo: BufferInfo)
}