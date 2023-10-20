package com.fphoenixcorneae.mediacodec

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

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
    fun startRecord(coroutineScope: CoroutineScope, outputDir: String, outputFileName: String) {
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
                val pcmFile = getFile(path = outputDir, name = outputFileName)
                FileOutputStream(pcmFile).use { pcmFos ->
                    // 开始录制
                    audioRecord.startRecording()
                    Log.d(TAG, "开始录音")
                    val buffer = ByteArray(minBufferSize)
                    while (mAudioRecordJob?.isActive == true) {
                        // 读取数据
                        val read = audioRecord.read(buffer, 0, buffer.size)
                        if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                            // 写 pcm 数据
                            pcmFos.write(buffer, 0, read)
                        }
                    }
                    pcmFos.flush()
                    // 录制结束
                    audioRecord.stop()
                    audioRecord.release()
                    Log.d(TAG, "录音结束")
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
}