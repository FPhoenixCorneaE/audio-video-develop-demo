# 音频的录制和播放

1. 使用 **AudioRecord** 录制 PCM（录音）
2. 生成 wav 格式的音频并使用 MediaPlayer 播放
3. 使用 **AudioTrack** 播放 PCM 格式音频（Stream 和 Static 模式）

### 一、声音是怎么被保存起来的？

在我们的世界中，声音是连续不断的，是一种模拟信号。但计算机能识别的就是二进制，所以，对声音这种模拟信号，采用数字化，即转换成数字信号，就能保存了。

声音是一种波，有自己的振幅和频率，如果要保存声音，就要保存各个时间点上的振幅，而数字信号并不能保存所有时间点的振幅，但实际上并不需要保存连续的信号，
就可以还原到人耳可接受的声音。

根据奈奎斯特定律：**为了不失真地恢复模拟信号，采样频率应该不小于模拟信号频谱中最高频率的2倍**。

**音频数据的承载方式，最常用的就是 脉冲编码调制，即 PCM。**

根据上述分析，PCM 的采集步骤如下：
模拟信号 --> 采样 --> 量化 --> 编码 --> 数字信号

1. **采样率**

人耳能听到的最高频率为 20,000hz，所以，为了满足人耳的听觉要求，采样率至少要大于原声波频率的2倍，即至少为 40khz，通常就是为
44.1khz， 更高则是 48khz。一般**采用 44.1khz 即可达到无损音质**。

2. **采样位数**

模拟信号是连续的样本值，而数字信号一般是不连续的，所以模拟信号量化，只能取一个近似的整数值，为了记录这些振幅值，采样器会采用一个固定的位数来记录这些
振幅值，通常有 8位，16位，32位。位数越大，记录的值越准确，还原度越高。

最后就是编码了，数字信号由0,1组成的，因此，需要将振幅转换成一系列0和1进行存储，也就是编码，最后得到的数据就是数字信号：一串0和1组成的数据。

3. **声道数**

指支持能 **不同发声(注意是不同声音)** 的音响的个数。

- 单声道：一个声道
- 双声道：2个声道
- 立体声：2个声道
- 立体声(4声道)：4个声道

### 二、使用 AudioRecord 录音

1. **首先申请好权限**

```xml

<manifest>
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" tools:ignore="ScopedStorage" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" tools:ignore="ScopedStorage" />
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" tools:ignore="ScopedStorage" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
</manifest>
```

```kotlin
companion object {
    /** 权限 */
    private val PERMISSIONS = arrayOf(
        "android.permission.RECORD_AUDIO",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_EXTERNAL_STORAGE",
    )
}

private fun requestPermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // 先判断有没有权限
        if (Environment.isExternalStorageManager()) {
            Toast.makeText(
                this,
                "Android VERSION  R OR ABOVE，HAVE MANAGE_EXTERNAL_STORAGE GRANTED!",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                this,
                "Android VERSION  R OR ABOVE，NO MANAGE_EXTERNAL_STORAGE GRANTED!",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:" + this.packageName)
            startActivityForResult(intent, 200)
        }
    } else {
        PERMISSIONS.forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS, 100)
            }
        }
    }
}
```

2. **按钮按下开始录音，抬起停止录音**

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(40.dp)
        .clip(RoundedCornerShape(30.dp))
        .background(if (isPressed) PurpleGrey40 else Purple40)
        .pointerInput(isPressed) {
            awaitPointerEventScope {
                if (isPressed) {
                    waitForUpOrCancellation()
                    Log.i(TAG, "onCreate: 手指抬起")
                    isPressed = false
                    audioRecordJob?.cancel()
                    audioRecordJob = null
                } else {
                    awaitFirstDown()
                    Log.i(TAG, "onCreate: 手指按下")
                    isPressed = true
                    startRecord()
                }
            }
        }, contentAlignment = Alignment.Center
) {
    Text(
        text = "按住录音", fontSize = 14.sp, color = Color.White
    )
}
```

3. **使用 AudioRecord 来录制原始数据，即 PCM 数据**

> 当手机的硬件录音之后，AudioRecord 可以从该硬件提取音频资源；读取的方法可以使用 read() 方法来实现，它会把数据读写到 byte[]
> 数组中，然后返回写入的大小，根据 byte 就可以保存到文件中了。

```kotlin
/**
 * 开始录制
 */
private fun startRecord() {
    audioRecordJob?.cancel()
    audioRecordJob = lifecycleScope.launch(Dispatchers.IO) {
        runCatching {
            // 获取最小 buffer 大小，采样率为 44100，双声道，采样位数为 16bit
            val minBufferSize by lazy {
                AudioRecord.getMinBufferSize(
                    /* sampleRateInHz = */ AUDIO_SAMPLE_RATE,
                    /* channelConfig = */ AudioFormat.CHANNEL_IN_STEREO,
                    /* audioFormat = */ AudioFormat.ENCODING_PCM_16BIT,
                )
            }
            // 创建 AudioRecord
            val audioRecord = AudioRecord(
                /* audioSource = */ MediaRecorder.AudioSource.MIC,
                /* sampleRateInHz = */ AUDIO_SAMPLE_RATE,
                /* channelConfig = */ AudioFormat.CHANNEL_IN_STEREO,
                /* audioFormat = */ AudioFormat.ENCODING_PCM_16BIT,
                /* bufferSizeInBytes = */ minBufferSize
            )
            // 创建pcm文件
            val pcmFile = getFile(path = AUDIO_SAVE_PATH, name = "testAudio.pcm")
            // 创建wav文件
            val wavFile = getFile(path = AUDIO_SAVE_PATH, name = "testAudio.wav")
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
                    Log.i(TAG, "startRecord: 开始录音")
                    val buffer = ByteArray(minBufferSize)
                    while (audioRecordJob != null) {
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
                    Log.i(TAG, "startRecord: 录音结束")
                }
            }
        }.onFailure {
            Log.e(TAG, "startRecord: $it")
        }
    }
}
```

### 三、Wav文件

> 原始的 pcm 文件是不支持播放的，需要将它转换成 wav 这种可以被识别解码的音频格式。在 pcm 文件的起始位置加上至少44个字节的
> wav 头信息即可把 pcm 格式转换成 wav 格式，该头信息记录着音频流的编码参数。

来一张官方图：
![wav音频格式文件头信息][1]

1. **wav 头部生成方法**

```kotlin
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
```

2. **播放 wav 文件**

```kotlin
    /**
 * 播放 Wav
 */
private fun playWav() {
    // wav文件
    val wavFile = File(AUDIO_SAVE_PATH, "testAudio.wav")
    if (wavFile.exists()) {
        playMedia(wavFile.absolutePath)
    } else {
        Toast.makeText(this, "请先录制", Toast.LENGTH_SHORT).show()
    }
}
```

```kotlin
/**
 * 媒体播放器
 */
private var mMediaPlayer: MediaPlayer? = null

/**
 * @param path 本地路径
 */
fun playMedia(path: String) {
    runCatching {
        mMediaPlayer = MediaPlayer().apply {
            setDataSource(path)
            prepare()
            start()
            setOnCompletionListener {
                closeMedia()
            }
        }
    }.onFailure {
        it.printStackTrace()
    }
}
```

### 四、播放 PCM 音频

> pcm 文件是不能够被多媒体解码识别的，但如果我就是想播放 pcm 文件呢？当然是可以的。可以通过 **AudioTrack** 来实现该功能，它是
> Android 管理和播放音频的管理类，允许 PCM 音频通过 write() 方法将数据流推送到 AudioTrack 来实现音频的播放(当然也不局限
> pcm，其他音频格式也支持的)。AudioTrack 有两种模式：流模式和静态模式。

1. **流模式**

> write() 时向 AudioTrack 写入连续的数据流，数据会从 Java 层传输到底层，并排队阻塞等待播放。当音频数据太大无法存入内存，或者接收或生成时先前排队的音频正在播放，流模式比较好用。

```kotlin
    /**
 * 播放PCM音频（Stream模式）
 */
private fun playPcmStream() {
    audioTrackJob?.cancel()
    audioTrackJob = lifecycleScope.launch(Dispatchers.IO) {
        runCatching {
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
            val minBufferSize by lazy {
                AudioTrack.getMinBufferSize(
                    /* sampleRateInHz = */ AUDIO_SAMPLE_RATE,
                    /* channelConfig = */ channel,
                    /* audioFormat = */ encoding,
                )
            }
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
            val pcmFile = File(AUDIO_SAVE_PATH, "testAudio.pcm")
            if (pcmFile.exists()) {
                FileInputStream(pcmFile).use {
                    val buffer = ByteArray(minBufferSize)
                    var len: Int
                    while (it.read(buffer).also { len = it } > 0) {
                        // 写入连续的数据流，有数据就会播放
                        audioTrack.write(buffer, 0, len)
                    }
                    audioTrack.stop()
                    audioTrack.release()
                }
            }
        }.onFailure {
            it.printStackTrace()
        }
    }
}
```

2. **静态模式**

> 需要一次性把数据写到 buffer 中，适合小音频，小延迟的音频播放，在 UI 和游戏中比较实用。

```kotlin
/**
 * 播放PCM音频（Static模式）
 */
private fun playPcmStatic() {
    runCatching {
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
        val pcmFile = File(AUDIO_SAVE_PATH, "testAudio.pcm")
        if (pcmFile.exists()) {
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
        }
    }.onFailure {
        it.printStackTrace()
    }
}
```

[1]: data:image/png;base64,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
