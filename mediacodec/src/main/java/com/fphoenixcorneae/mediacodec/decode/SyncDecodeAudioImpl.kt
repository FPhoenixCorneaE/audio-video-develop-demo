package com.fphoenixcorneae.mediacodec.decode

import android.media.MediaCodec

class SyncDecodeAudioImpl(
    path: String,
) : AbstractSyncDecode(path) {
    override fun mimePrefix(): String = "audio"

    override fun handleOutputData(bufferInfo: MediaCodec.BufferInfo) {
    }
}