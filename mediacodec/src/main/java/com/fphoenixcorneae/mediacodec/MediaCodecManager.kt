package com.fphoenixcorneae.mediacodec

import android.Manifest
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object MediaCodecManager {

    private const val TAG = "MediaCodecManager"

    /** 采样率 */
    private const val AUDIO_SAMPLE_RATE = 44100

    /** 使用 MediaCodec 去编码 */
    private var mMediaCodecJob: Job? = null

    /**
     * 将 pcm 文件编码为 aac 文件
     */
    @RequiresPermission(allOf = [Manifest.permission_group.STORAGE])
    fun encodePcmFile2AacFile(
        coroutineScope: CoroutineScope,
        outputDir: String,
        pcmFileName: String,
        aacFileName: String,
    ) {
        mMediaCodecJob?.cancel()
        mMediaCodecJob = coroutineScope.launch(Dispatchers.IO) {
            // 设置 MediaFormat
            // 编码为 AAC
            // 采样率为 44.1Khz
            // 声音通道个数为 2，即双声道或者立体声
            val mediaFormat = MediaFormat.createAudioFormat(
                /* mime = */ MediaFormat.MIMETYPE_AUDIO_AAC,
                /* sampleRate = */ AUDIO_SAMPLE_RATE,
                /* channelCount = */ 2,
            )
            // 设置比特率，表示单位时间内传送比特的数目，96000 和 115200 是比较常用的
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000)
            // 设置 AAC 格式，配置 LC 模式，低复杂度规格，编码效率高，比较简单，没有增益控制，目前 MP4 格式就使用这种规格
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 500 * 1024)

            // 创建解码器
            val mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            // 开始编码
            mediaCodec.start()
            Log.d(TAG, "开始编码成 AAC 格式...")
            runCatching {
                FileInputStream(File(outputDir, pcmFileName)).use { pcmFis ->
                    FileOutputStream(getFile(outputDir, aacFileName)).use { aacFos ->
                        val bytes = ByteArray(4 * 1024)
                        var len: Int
                        while (pcmFis.read(bytes).also { len = it } > 0) {
                            val inputBufferId = mediaCodec.dequeueInputBuffer(-1)
                            if (inputBufferId > 0) {
                                // 拿到编码的空 buffer
                                mediaCodec.getInputBuffer(inputBufferId)?.let {
                                    // 写数据到 buffer
                                    it.put(bytes, 0, len)
                                    mediaCodec.queueInputBuffer(inputBufferId, 0, len, 0, 0)
                                }
                                // 编码之后的数据
                                val bufferInfo = MediaCodec.BufferInfo()
                                var outputBufferId: Int
                                while (
                                    mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
                                        .also { outputBufferId = it } > 0
                                ) {
                                    // 拿到当前这一帧的大小
                                    val frameSize = bufferInfo.size
                                    // 7为 ADT 头部的大小
                                    val packetSize = frameSize + 7
                                    mediaCodec.getOutputBuffer(outputBufferId)?.let {
                                        // 数据开始的偏移量
                                        it.position(bufferInfo.offset)
                                        it.limit(bufferInfo.offset + frameSize)
                                        val audioChunk = ByteArray(packetSize)
                                        // 添加 ADTS 头部数据
                                        addADTStoPacket(audioChunk, packetSize)
                                        // 将编码得到的 AAC 数据 取出到 chunkAudio 中
                                        it.get(audioChunk, 7, frameSize)
                                        // 将数据输出到 aac 文件
                                        aacFos.write(audioChunk, 0, audioChunk.size)
                                        it.position(bufferInfo.offset)
                                        mediaCodec.releaseOutputBuffer(outputBufferId, false)
                                    }
                                }
                                aacFos.flush()
                            }
                        }
                        mediaCodec.stop()
                        mediaCodec.release()
                        Log.d(TAG, "编码完成")
                    }
                }
            }.onFailure {
                it.printStackTrace()
                Log.d(TAG, "编码失败")
            }
        }
    }

    private fun getFile(path: String, name: String): File? {
        return runCatching {
            val file = File(path, name)
            if (file.parentFile?.exists() == false) {
                file.parentFile?.mkdirs()
            }
            if (file.exists()) {
                file.delete()
            }
            file.createNewFile()
            file
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    /**
     * 添加 ADTS 头部数据
     *
     * @param packet
     * @param packetLen
     */
    private fun addADTStoPacket(packet: ByteArray, packetLen: Int) {
        val profile = 2 // AAC LC
        val freqIdx = 4 // 44.1KHz
        val chanCfg = 2 // CPE

        // syncword，比如为1，即0xff
        packet[0] = 0xFF.toByte()
        packet[1] = 0xF9.toByte()
        packet[2] = ((profile - 1 shl 6) + (freqIdx shl 2) + (chanCfg shr 2)).toByte()
        packet[3] = ((chanCfg and 3 shl 6) + (packetLen shr 11)).toByte()
        packet[4] = (packetLen and 0x7FF shr 3).toByte()
        packet[5] = ((packetLen and 7 shl 5) + 0x1F).toByte()
        packet[6] = 0xFC.toByte()
    }
}