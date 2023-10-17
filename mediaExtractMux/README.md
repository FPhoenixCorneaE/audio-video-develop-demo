# 音视频分离与合成

1. **MediaExtractor** 提取视频轨和音频轨
2. **MediaMuxer** 合成新视频

### 一、分离

MediaExtractor 可以用来分离媒体中的视频轨道和音频轨道，支持多种常见的媒体格式，例如 MP4，3GP，WebM，FLV，MPEG-TS 等等。

> 主要 API 如下：
>
> * **setDataSource(String path)**：设置媒体文件的路径
> * **getTrackCount()**：获取媒体文件中的音视频轨道数量
> * **getTrackFormat(int index)**：获取指定音视频轨道的格式
> * **selectTrack(int index)**：选择/指定音视频轨道
> * **readSampleData(ByteBuffer byteBuf, int offset)**：读取一帧数据
> * **advance()**：读取下一帧数据
> * **release()**：释放资源

**步骤：**

1. 设置数据源
2. 遍历轨道
3. 选择特定轨道
4. 循环读取每帧的样本数据写入对应的轨道
5. 完成后释放资源

**代码如下：**

```kotlin
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
```

**Mime 文件类型：**
常见的MIME类型多媒体格式如下（以audio开头的是音频，以video开头的是视频）：

* “video/x-vnd.on2.vp8” - VP8 video (i.e. video in .webm)
* “video/x-vnd.on2.vp9” - VP9 video (i.e. video in .webm)
* “video/avc” - H.264/AVC video
* “video/mp4v-es” - MPEG4 video
* “video/3gpp” - H.263 video
* “audio/3gpp” - AMR narrowband audio
* “audio/amr-wb” - AMR wideband audio
* “audio/mpeg” - MPEG1/2 audio layer III
* “audio/mp4a-latm” - AAC audio (note, this is raw AAC packets, not packaged in LATM!)
* “audio/vorbis” - vorbis audio
* “audio/g711-alaw” - G.711 alaw audio
* “audio/g711-mlaw” - G.711 ulaw audio

**字幕Track格式：**

* MIMETYPE_TEXT_VTT = “text/vtt”;
* MIMETYPE_TEXT_CEA_608 = “text/cea-608”;
* MIMETYPE_TEXT_CEA_708 = “text/cea-708”;

还有很多格式请参考 MediaFormat 中的格式常量。

```kotlin
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
```

### 二、合成

MediaMuxer 可以用来生成音频或视频文件，也可以把音频和视频合成一个新的音视频文件，目前 MediaMuxer 支持 MP4、Webm 和 3GP
文件作为输出。

> 主要 API 如下：
>
> * **MediaMuxer(String path, int format)**：path 为输出文件的名称，format 指输出文件的格式
> * **addTrack(MediaFormat format)**：添加轨道
> * **start()**：开始合成文件
> * **writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo)**：把 ByteBuffer
    > 中的数据写入到在构造器设置的文件中
> * **stop()**：停止合成文件
> * **release()**：释放资源

**步骤：**

1. 设置目标文件路径和音视频格式
2. 添加要合成的轨道，包括音频轨道和视频轨道
3. 开始合成
4. 循环写入每帧样本数据
5. 完成后释放资源

**代码如下：**

```kotlin
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
                Toast.makeText(context, "muxVideo: success. file path：${outputFile.absolutePath}", Toast.LENGTH_SHORT)
                    .show()
            }
        }.onFailure {
            it.printStackTrace()
        }
    }
}
```
