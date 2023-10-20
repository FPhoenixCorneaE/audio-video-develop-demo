package com.fphoenixcorneae.audio

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile

object AudioManager {

    private const val TAG = "AudioManager"

    /** 采样率 */
    private const val AUDIO_SAMPLE_RATE = 44100

    /** 使用 AudioRecord 去录音 */
    private var mAudioRecordJob: Job? = null

    /** 使用 AudioTrack 播放音频, 只能播放 PCM 音频流 */
    private var mAudioTrackJob: Job? = null

    /**
     * 开始录音
     */
    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission_group.STORAGE])
    fun startRecord(
        coroutineScope: CoroutineScope,
        outputDir: String,
        pcmFileName: String,
        wavFileName: String,
    ) {
        stopRecord()
        mAudioRecordJob = coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                // 获取最小 buffer 大小，采样率为 44100，双声道，采样位数为 16bit
                val minBufferSize = AudioRecord.getMinBufferSize(
                    /* sampleRateInHz = */ AUDIO_SAMPLE_RATE,
                    /* channelConfig = */ AudioFormat.CHANNEL_IN_STEREO,
                    /* audioFormat = */ AudioFormat.ENCODING_PCM_16BIT,
                )
                // 创建 AudioRecord
                val audioRecord = AudioRecord(
                    /* audioSource = */ MediaRecorder.AudioSource.MIC,
                    /* sampleRateInHz = */ AUDIO_SAMPLE_RATE,
                    /* channelConfig = */ AudioFormat.CHANNEL_IN_STEREO,
                    /* audioFormat = */ AudioFormat.ENCODING_PCM_16BIT,
                    /* bufferSizeInBytes = */ minBufferSize
                )
                // 创建 pcm 文件
                val pcmFile = getFile(path = outputDir, name = pcmFileName)
                // 创建wav文件
                val wavFile = getFile(path = outputDir, name = wavFileName)
                FileOutputStream(wavFile).use { wavFos ->
                    FileOutputStream(pcmFile).use { pcmFos ->
                        // 先写头部，但现在并不知道 pcm 文件的大小
                        val headers = generateWavFileHeader(
                            pcmAudioByteCount = 0,
                            longSampleRate = AUDIO_SAMPLE_RATE.toLong(),
                            channels = audioRecord.channelCount
                        )
                        wavFos.write(headers, 0, headers.size)

                        // 开始录制
                        audioRecord.startRecording()
                        Log.i(TAG, "开始录音")
                        val buffer = ByteArray(minBufferSize)
                        while (mAudioRecordJob?.isActive == true) {
                            // 读取数据
                            val read = audioRecord.read(buffer, 0, buffer.size)
                            if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                                // 写 pcm 数据
                                pcmFos.write(buffer, 0, read)
                                // 写 wav 数据
                                wavFos.write(buffer, 0, read)
                            }
                        }

                        pcmFos.flush()
                        wavFos.flush()

                        RandomAccessFile(wavFile, "rw").use { wavRaf ->
                            // 修改头部的 pcm文件 大小
                            val header = generateWavFileHeader(
                                pcmAudioByteCount = pcmFile?.length() ?: 0,
                                longSampleRate = AUDIO_SAMPLE_RATE.toLong(),
                                channels = audioRecord.channelCount
                            )
                            wavRaf.seek(0)
                            wavRaf.write(header)
                        }

                        // 录制结束
                        audioRecord.release()
                        Log.i(TAG, "录音结束")
                    }
                }
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    /**
     * 停止录音
     */
    fun stopRecord() {
        mAudioRecordJob?.cancel()
        mAudioRecordJob = null
    }

    /**
     * 播放 Wav
     */
    fun playWav(
        context: Context,
        dirPath: String,
        wavFileName: String,
    ) {
        // wav文件
        val wavFile = File(dirPath, wavFileName)
        if (wavFile.exists()) {
            playMedia(wavFile.absolutePath)
        } else {
            Toast.makeText(context, "请先录制", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 播放 pcm 音频（Stream模式）
     */
    fun playPcmStream(coroutineScope: CoroutineScope, pcmFile: File) {
        mAudioTrackJob?.cancel()
        mAudioTrackJob = coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                if (!pcmFile.exists()) {
                    Log.d(TAG, "音频文件不存在：${pcmFile.absolutePath}")
                    return@runCatching
                }
                // 声道
                val channel = AudioFormat.CHANNEL_IN_STEREO
                // 采样位数
                val encoding = AudioFormat.ENCODING_PCM_16BIT
                // 设置音频信息属性
                val audioAttrs = AudioAttributes.Builder()
                    // 设置支持多媒体属性
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    // 设置音频格式
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                // 设置音频格式
                val audioFormat = AudioFormat.Builder()
                    // 设置采样率
                    .setSampleRate(AUDIO_SAMPLE_RATE)
                    // 设置采样位数
                    .setEncoding(encoding)
                    // 设置声道
                    .setChannelMask(channel)
                    .build()
                // 由于是流模式，大小只需获取一帧的最小 buffer 即可，采样率为 44100，双声道，采样位数为 16bit
                val minBufferSize = AudioTrack.getMinBufferSize(
                    /* sampleRateInHz = */ AUDIO_SAMPLE_RATE,
                    /* channelConfig = */ channel,
                    /* audioFormat = */ encoding,
                )
                // 创建 AudioTrack
                val audioTrack = AudioTrack(
                    /* attributes = */ audioAttrs,
                    /* format = */ audioFormat,
                    /* bufferSizeInBytes = */ minBufferSize,
                    /* mode = */ AudioTrack.MODE_STREAM,
                    /* sessionId = */ AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                // 开始播放，等待数据
                audioTrack.play()
                Log.d(TAG, "开始播放音频：${pcmFile.absolutePath}")
                FileInputStream(pcmFile).use {
                    val buffer = ByteArray(minBufferSize)
                    var len: Int
                    while (it.read(buffer).also { len = it } > 0) {
                        // 写入连续的数据流，有数据就会播放
                        audioTrack.write(buffer, 0, len)
                    }
                }
                // 释放资源
                audioTrack.stop()
                audioTrack.release()
                Log.d(TAG, "音频播放完毕：${pcmFile.absolutePath}")
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    /**
     * 播放PCM音频（Static模式）
     */
    fun playPcmStatic(coroutineScope: CoroutineScope, pcmFile: File) {
        mAudioTrackJob?.cancel()
        mAudioTrackJob = coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                if (!pcmFile.exists()) {
                    Log.d(TAG, "音频文件不存在：${pcmFile.absolutePath}")
                    return@runCatching
                }
                // 声道
                val channel = AudioFormat.CHANNEL_IN_STEREO
                // 采样位数
                val encoding = AudioFormat.ENCODING_PCM_16BIT
                // 设置音频信息属性
                val audioAttrs = AudioAttributes.Builder()
                    // 设置支持多媒体属性
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    // 设置音频格式
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                // 设置音频格式
                val audioFormat = AudioFormat.Builder()
                    // 设置采样率
                    .setSampleRate(AUDIO_SAMPLE_RATE)
                    // 设置采样位数
                    .setEncoding(encoding)
                    // 设置声道
                    .setChannelMask(channel)
                    .build()
                FileInputStream(pcmFile).use {
                    ByteArrayOutputStream().use { baos ->
                        val buffer = ByteArray(1024)
                        var len: Int
                        while (it.read(buffer).also { len = it } > 0) {
                            baos.write(buffer, 0, len)
                        }
                        // 拿到音频数据
                        val audioBytes = baos.toByteArray()
                        // 创建 AudioTrack
                        val audioTrack = AudioTrack(
                            /* attributes = */ audioAttrs,
                            /* format = */ audioFormat,
                            // 使用音频大小
                            /* bufferSizeInBytes = */ audioBytes.size,
                            /* mode = */ AudioTrack.MODE_STREAM,
                            /* sessionId = */ AudioManager.AUDIO_SESSION_ID_GENERATE
                        )
                        // 一次性写入
                        audioTrack.write(audioBytes, 0, audioBytes.size)
                        // 开始播放
                        audioTrack.play()
                    }
                }
            }.onFailure {
                it.printStackTrace()
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
     * 任何一种文件在头部添加相应的头文件才能够确定的表示这种文件的格式，
     * wave是RIFF文件结构，每一部分为一个chunk，其中有RIFF WAVE chunk，
     * FMT Chunk，Fact chunk,Data chunk,其中Fact chunk是可以选择的
     *
     * @param pcmAudioByteCount 不包括header的音频数据总长度
     * @param longSampleRate    采样率,也就是录制时使用的频率
     * @param channels          audioRecord的频道数量
     */
    private fun generateWavFileHeader(pcmAudioByteCount: Long, longSampleRate: Long, channels: Int): ByteArray {
        val totalDataLen = pcmAudioByteCount + 36 // 不包含前8个字节的WAV文件总长度
        val byteRate = longSampleRate * 2 * channels
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xffL).toByte() //数据大小
        header[5] = (totalDataLen shr 8 and 0xffL).toByte()
        header[6] = (totalDataLen shr 16 and 0xffL).toByte()
        header[7] = (totalDataLen shr 24 and 0xffL).toByte()
        header[8] = 'W'.code.toByte() //WAVE
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        // FMT Chunk
        header[12] = 'f'.code.toByte() // 'fmt '
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte() //过渡字节
        // 数据大小
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        // 编码方式 10H为PCM编码格式
        header[20] = 1 // format = 1
        header[21] = 0
        // 通道数
        header[22] = channels.toByte()
        header[23] = 0
        // 采样率，每个通道的播放速度
        header[24] = (longSampleRate and 0xffL).toByte()
        header[25] = (longSampleRate shr 8 and 0xffL).toByte()
        header[26] = (longSampleRate shr 16 and 0xffL).toByte()
        header[27] = (longSampleRate shr 24 and 0xffL).toByte()
        // 音频数据传送速率,采样率*通道数*采样深度/8
        header[28] = (byteRate and 0xffL).toByte()
        header[29] = (byteRate shr 8 and 0xffL).toByte()
        header[30] = (byteRate shr 16 and 0xffL).toByte()
        header[31] = (byteRate shr 24 and 0xffL).toByte()
        // 确定系统一次要处理多少个这样字节的数据，确定缓冲区，通道数*采样位数
        header[32] = (2 * channels).toByte()
        header[33] = 0
        // 每个样本的数据位数
        header[34] = 16
        header[35] = 0
        // Data chunk
        header[36] = 'd'.code.toByte() //data
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmAudioByteCount and 0xffL).toByte()
        header[41] = (pcmAudioByteCount shr 8 and 0xffL).toByte()
        header[42] = (pcmAudioByteCount shr 16 and 0xffL).toByte()
        header[43] = (pcmAudioByteCount shr 24 and 0xffL).toByte()
        return header
    }
}