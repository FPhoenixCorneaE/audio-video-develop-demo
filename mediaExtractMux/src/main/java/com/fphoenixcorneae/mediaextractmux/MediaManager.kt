package com.fphoenixcorneae.mediaextractmux

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object MediaManager {

    private const val BYTE_BUFFER_CAPACITY = 500 * 1024
    private const val VIDEO_NAME = "video.h264"
    private const val AUDIO_NAME = "audio.aac"

    /**
     * 从媒体文件中提取音频和视频
     * @param uri 视频地址
     */
    fun extractVideo(context: Context, uri: Uri, outputDir: File) {
        runCatching {
            // 初始化 MediaExtractor
            val mediaExtractor = MediaExtractor()
            // 设置数据源，可以是本地文件或者网络地址。
            mediaExtractor.setDataSource(context, uri, null)
            // 获取轨道数
            val trackCount = mediaExtractor.trackCount
            // 遍历轨道，查看音频轨道或视频轨道信息
            for (i in 0 until trackCount) {
                // 获取某一个轨道的媒体格式
                val trackFormat = mediaExtractor.getTrackFormat(i)
                val keyMime = trackFormat.getString(MediaFormat.KEY_MIME)
                Log.i("MediaManager", "extractVideo: keyMime: $keyMime")
                if (keyMime.isNullOrEmpty()) {
                    continue
                }
                // 通过 MIME 信息识别音频轨道和视频轨道
                when {
                    keyMime.startsWith("video") -> {
                        CoroutineScope(Dispatchers.IO).launch {
                            val outputFile = getOutputFile(mediaExtractor, i, outputDir, VIDEO_NAME)
                            Log.i("MediaManager", "extractVideo: video file path：${outputFile.absolutePath}")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "extractVideo: video file path：${outputFile.absolutePath}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    keyMime.startsWith("audio") -> {
                        CoroutineScope(Dispatchers.IO).launch {
                            val outputFile = getOutputFile(mediaExtractor, i, outputDir, AUDIO_NAME)
                            Log.i("MediaManager", "extractVideo: audio file path：${outputFile.absolutePath}")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "extractVideo: audio file path：${outputFile.absolutePath}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        }.onFailure {
            it.printStackTrace()
        }
    }

    /**
     * 将 .acc 音频文件和 .h264 视频文件合成新的音视频文件
     */
    @SuppressLint("WrongConstant")
    fun muxVideo(
        context: Context,
        outputDir: File,
        outputName: String,
        format: Int = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
    ) {
        val videoFile = File(outputDir, VIDEO_NAME)
        val audioFile = File(outputDir, AUDIO_NAME)
        if (!videoFile.exists() || !audioFile.exists()) {
            return
        }
        // 输出文件
        val outputFile = File(outputDir, outputName)
        if (outputFile.exists()) {
            outputFile.delete()
        }
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val mediaMuxer = MediaMuxer(outputFile.absolutePath, format)
                // 添加视频轨道
                val videoExtractor = MediaExtractor()
                videoExtractor.setDataSource(videoFile.absolutePath)
                var videoTrackIndex = 0
                run {
                    for (i in 0 until videoExtractor.trackCount) {
                        // 获取某一个轨道的媒体格式
                        val trackFormat = videoExtractor.getTrackFormat(i)
                        val keyMime = trackFormat.getString(MediaFormat.KEY_MIME)
                        if (keyMime.isNullOrEmpty()) {
                            continue
                        }
                        if (keyMime.startsWith("video")) {
                            videoExtractor.selectTrack(i)
                            videoTrackIndex = mediaMuxer.addTrack(trackFormat)
                            return@run
                        }
                    }
                }

                // 添加音频轨道
                val audioExtractor = MediaExtractor()
                audioExtractor.setDataSource(audioFile.absolutePath)
                var audioTrackIndex = 0
                run {
                    for (i in 0 until audioExtractor.trackCount) {
                        // 获取某一个轨道的媒体格式
                        val trackFormat = audioExtractor.getTrackFormat(i)
                        val keyMime = trackFormat.getString(MediaFormat.KEY_MIME)
                        if (keyMime.isNullOrEmpty()) {
                            continue
                        }
                        if (keyMime.startsWith("audio")) {
                            audioExtractor.selectTrack(i)
                            audioTrackIndex = mediaMuxer.addTrack(trackFormat)
                            return@run
                        }
                    }
                }
                // 开始合成
                mediaMuxer.start()
                val byteBuffer = ByteBuffer.allocate(BYTE_BUFFER_CAPACITY)
                val videoBufferInfo = MediaCodec.BufferInfo()
                var videoSimpleSize: Int
                // 先清空数据
                byteBuffer.clear()
                while (videoExtractor.readSampleData(byteBuffer, 0).also { videoSimpleSize = it } > 0) {
                    videoBufferInfo.apply {
                        flags = videoExtractor.sampleFlags
                        offset = 0
                        size = videoSimpleSize
                        presentationTimeUs = videoExtractor.sampleTime
                    }
                    mediaMuxer.writeSampleData(videoTrackIndex, byteBuffer, videoBufferInfo)
                    // 读取下一帧数据
                    videoExtractor.advance()
                }
                val audioBufferInfo = MediaCodec.BufferInfo()
                var audioSimpleSize: Int
                // 先清空数据
                byteBuffer.clear()
                while (audioExtractor.readSampleData(byteBuffer, 0).also { audioSimpleSize = it } > 0) {
                    audioBufferInfo.apply {
                        flags = audioExtractor.sampleFlags
                        offset = 0
                        size = audioSimpleSize
                        presentationTimeUs = audioExtractor.sampleTime
                    }
                    mediaMuxer.writeSampleData(audioTrackIndex, byteBuffer, audioBufferInfo)
                    // 读取下一帧数据
                    audioExtractor.advance()
                }
                // 释放资源
                videoExtractor.release()
                audioExtractor.release()
                mediaMuxer.stop()
                mediaMuxer.release()

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "muxVideo: success. file path：${outputFile.absolutePath}",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    @SuppressLint("WrongConstant")
    private fun getOutputFile(mediaExtractor: MediaExtractor, i: Int, outputDir: File, outputName: String): File {
        val trackFormat = mediaExtractor.getTrackFormat(i)
        mediaExtractor.selectTrack(i)
        // 文件保存路径
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val outputFile = File(outputDir, outputName)
        if (outputFile.exists()) {
            outputFile.delete()
        }
        outputFile.createNewFile()
        val mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        // 添加轨道信息
        mediaMuxer.addTrack(trackFormat)
        // 开始合成
        mediaMuxer.start()
        // 设置每一帧的大小
        val buffer = ByteBuffer.allocate(BYTE_BUFFER_CAPACITY)
        val bufferInfo = MediaCodec.BufferInfo()
        var sampleSize: Int
        // 先清空数据
        buffer.clear()
        // 循环读取每帧样本数据
        while (mediaExtractor.readSampleData(buffer, 0).also { sampleSize = it } > 0) {
            bufferInfo.apply {
                flags = mediaExtractor.sampleFlags
                offset = 0
                size = sampleSize
                presentationTimeUs = mediaExtractor.sampleTime
            }
            // 通过 mediaExtractor 解封装的数据通过 writeSampleData 写入到对应的轨道
            mediaMuxer.writeSampleData(0, buffer, bufferInfo)
            // 读取下一帧数据
            mediaExtractor.advance()
        }
        // 提取完毕
        mediaExtractor.unselectTrack(i)
        mediaMuxer.stop()
        mediaMuxer.release()
        return outputFile
    }
}