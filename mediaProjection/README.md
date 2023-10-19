# MediaProjection 截屏和录屏

### 一、权限

```xml

<manifest>
    <!-- 存储 -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" tools:ignore="ScopedStorage" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" tools:ignore="ScopedStorage" />
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" tools:ignore="ScopedStorage" />
    <!-- 录音 -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <!-- 前台服务 -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application android:requestLegacyExternalStorage="true">
        <provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.FileProvider"
            android:exported="false" android:grantUriPermissions="true" tools:replace="android:authorities">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
```

### 二、媒体投影 `MediaProjection` 的获取必须在 Service 中进行，否则会报异常：java.lang.SecurityException: Media projections require a foreground service of type ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION

```kotlin
class MediaProjectionService : Service() {

    private var mNotificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotification()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        release()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationIntent = Intent(this, MediaProjectionService::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            val notificationBuilder: NotificationCompat.Builder =
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Media Projection Service")
                    .setContentText("Starting Media Projection Service")
                    .setContentIntent(pendingIntent)
            val notification = notificationBuilder.build()
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = NOTIFICATION_CHANNEL_DESC
            mNotificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java)
            mNotificationManager?.createNotificationChannel(channel)
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun release() {
        mNotificationManager?.cancel(NOTIFICATION_ID)
        mNotificationManager = null

        mMediaProjection?.stop()
        mMediaProjection = null

        stopSelf()
    }

    companion object {
        private const val NOTIFICATION_ID = 666
        private const val NOTIFICATION_CHANNEL_ID = "channel_id"
        private const val NOTIFICATION_CHANNEL_NAME = "channel_name"
        private const val NOTIFICATION_CHANNEL_DESC = "channel_desc"

        private var mMediaProjection: MediaProjection? = null

        /**
         * 获取媒体投影
         */
        fun getMediaProjection(
            manager: MediaProjectionManager?,
            resultCode: Int,
            resultData: Intent,
        ): MediaProjection? {
            return manager?.getMediaProjection(resultCode, resultData).also { mMediaProjection = it }
        }
    }
}
```

```xml

<application>
    <!-- 注册截屏需要用到前台服务 -->
    <!-- java.lang.SecurityException: Media projections require a foreground service of type ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION -->
    <service android:name=".MediaProjectionService" android:enabled="true"
        android:foregroundServiceType="mediaProjection" />
</application>
```

### 三、截屏

Android 截图在这里分为三类：

* 截取除状态栏的屏幕
* 截取某个控件或区域
* 使用 MediaProjection 截图

#### 1. 截取除状态栏的屏幕

> 该方式是使用 View 的 Cache 机制生成 View 的图像缓存保存为 Bitmap。
> 主要的 API 如下：
>
> * **void setDrawingCacheEnabled(boolean flag)**: 开启或关闭 View 的 Cache，设置为 false 后，系统也会自动把原来的 Cache
>   > 销毁。
> * **void buildDrawingCache()**: 创建 Cache，可不调用。
> * **Bitmap getDrawingCache()**: 获取 View 的 Cache 图片。
> * **void destroyDrawingCache()**: 销毁 Cache 。若想更新 Cache,必须要调用该方法把旧的 Cache 销毁，才能建立新的。

```kotlin
fun View.createBitmapFormCache(): Bitmap? {
    isDrawingCacheEnabled = true
    buildDrawingCache()
    val bitmap = Bitmap.createBitmap(drawingCache)
    isDrawingCacheEnabled = false
    return bitmap
}
```

#### 2. 截取某个控件或区域

> 这种方式是将 View 绘制到 Canvas。
> ListView、ScrollView、WebView、RecyclerView 截长图都可以用使用此方法。

```kotlin
fun View.createBitmap(config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
    if (!ViewCompat.isLaidOut(this)) {
        throw IllegalStateException("View needs to be laid out before calling toBitmap()")
    }
    return Bitmap.createBitmap(width, height, config).applyCanvas {
        translate(-scrollX.toFloat(), -scrollY.toFloat())
        draw(this)
    }
}
```

#### 3. 使用 MediaProjection 截图

> Android 在 5.0 之后支持了实时录屏的功能。通过实时录屏我们可以拿到截屏的图像。

大体步骤如下：

* 3.1. **获取 `MediaProjectionManager` 服务**

```kotlin
/**
 * 获取 MediaProjectionManager 服务
 */
fun getMediaProjectionManager(context: Context) =
    ContextCompat.getSystemService(context, MediaProjectionManager::class.java)
```

* 3.2. **通过 `MediaProjectionManager` 创建屏幕捕获 Intent 并启动**

```kotlin
private val mCaptureScreenForResult =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.let {
            MediaProjectionHelper.configImageReader(this, result.resultCode, it) {
                it?.let { lifecycleScope.launch { mScreenCaptureBitmap.emit(it) } }
            }
        }
    }
```

```kotlin
MediaProjectionHelper.getMediaProjectionManager(context)?.let {
    // 开始截屏
    mCaptureScreenForResult.launch(it.createScreenCaptureIntent())
}
```

* 3.3. **在 ActivityResult 中通过 `MediaProjectionService` 获取 `MediaProjection` 对象**

```kotlin
// 获取 MediaProjection 对象
mMediaProjection =
    MediaProjectionService.getMediaProjection(getMediaProjectionManager(context), resultCode, resultData)
```

* 3.4. **创建 `ImageReader` 对象**

```kotlin
val displayMetrics = context.applicationContext.resources.displayMetrics
// 参数1：默认图像的宽度像素
// 参数2：默认图像的高度像素
// 参数3：图像的像素格式
// 参数4：用户想要读图像的最大数量
mImageReader = ImageReader.newInstance(
    /* width = */ displayMetrics.widthPixels,
    /* height = */ displayMetrics.heightPixels,
    /* format = */ PixelFormat.RGBA_8888,
    /* maxImages = */ 1,
)
```

> `ImageReader` 类允许应用程序直接访问呈现表面的图像数据创建 ImageReader 对象
> 主要 Api 操作：
>
> * **getSurface()**: 得到一个表面,可用于生产这个 ImageReader 的图像
> * **acquireLatestImage()**: 从 ImageReader 的队列获得最新的图像,放弃旧的图像。
> * **acquireNextImage()**: 从 ImageReader 的队列获取下一个图像
> * **getMaxImages()**: 最大数量的图像
> * **getWidth()**: 每个图像的宽度,以像素为单位。
> * **getHeight()**: 每个图像的高度,以像素为单位。
> * **getImageFormat()**: 图像格式
> * **close()**: 释放与此ImageReader相关的所有资源。用完记得关
> * **setOnImageAvailableListener(OnImageAvailableListener listener, Handler handler)**:
>   > 注册一个监听器，当ImageReader有一个新的Image变得可用时候调用。

* 3.5. **通过 `MediaProjection` 创建 `VirtualDisplay` 对象，把内容渲染给 `ImageReader` 的 `Surface` 控件**

```kotlin
// 将内容投射到 ImageReader 的 surface，获取屏幕数据
mVirtualDisplay = mMediaProjection?.createVirtualDisplay(
    /* name = */ "ScreenCapture",
    /* width = */ displayMetrics.widthPixels,
    /* height = */ displayMetrics.heightPixels,
    /* dpi = */ displayMetrics.densityDpi,
    /* flags = */ DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
    /* surface = */ surface,
    /* callback = */ null,
    /* handler = */ null,
)
```

> `VirtualDisplay` 类代表一个虚拟显示器，调用 `createVirtualDisplay()` 方法，将虚拟显示器的内容渲染在一个 `Surface`
> 控件上，当进程终止时虚拟显示器会被自动的释放，并且所有的 Window 都会被强制移除。当不再使用他时，你应该调用 `release()`
> 方法来释放资源。

* 3.6. **通过 `ImageReader` 获取 `Image` 生成 `Bitmap`**

```kotlin
/**
 * 通过 ImageReader 获取 Image 生成 Bitmap
 */
private fun acquireLatestImageToBitmap(imageReader: ImageReader): Bitmap? {
    return runCatching {
        var bitmap: Bitmap?
        // 获取捕获的照片数据
        imageReader.acquireLatestImage().use { image ->
            val width = image.width
            val height = image.height
            // 拿到所有的 Plane 数组
            val planes = image.planes
            val plane = planes[0]
            // 相邻像素样本之间的距离，因为是 RGBA，所以间距是4个字节
            val pixelStride = plane.pixelStride
            // 每行的宽度
            val rowStride = plane.rowStride
            // 因为内存对齐问题，每个 buffer 宽度不同，所以通过 pixelStride * width 得到大概的宽度，
            // 然后通过 rowStride 去减，得到大概的内存偏移量，不过一般都是对齐的。
            val rowPadding = rowStride - pixelStride * width
            // 创建具体的 bitmap 大小，由于 rowPadding 是 RGBA 4个通道的，所以也要除以 pixelStride ，得到实际的宽
            bitmap = Bitmap.createBitmap(
                /* width = */ width + rowPadding / pixelStride,
                /* height = */ height,
                /* config = */ Bitmap.Config.ARGB_8888
            )
            val buffer = plane.buffer
            bitmap?.copyPixelsFromBuffer(buffer)
            // 释放资源
            mVirtualDisplay?.release()
            mVirtualDisplay = null
        }
        bitmap
    }.onFailure {
        it.printStackTrace()
    }.getOrNull()
}
```

> Image 为图片数据，Plane 为 Image 的抽象内部类，`image.getPlanes()` 获取该图片的像素矩阵，返回值为一个 Plane[]
> 矩阵，`plane.getBuffer()` 获取图像数据，`getPixelStride()` 和 `getRowStride()` 为获取 Image 的一些跨距，经过一系列转换得到图像的尺寸，创建
> Bitmap 对象，然后从 Image 的 `ByteBuffer` 中拷贝像素数据生成 Bitmap

### 四、录屏

> 录屏的实现需要使用 `MediaRecorder` 类
> 前面几步同截图相同

1. **获取 `MediaProjectionManager` 服务**
2. **通过 `MediaProjectionManager` 创建屏幕捕获 Intent 并启动**
3. **在 ActivityResult 中通过 `MediaProjectionService` 获取 `MediaProjection` 对象**
4. **初始化 `MediaRecorder` 并准备录制**

```kotlin
// 初始化 MediaRecorder
mMediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    MediaRecorder(context)
} else {
    MediaRecorder()
}
mMediaRecorder?.apply {
    // 音频来源
    setAudioSource(MediaRecorder.AudioSource.MIC)
    // 视频来源
    setVideoSource(MediaRecorder.VideoSource.SURFACE)
    // 输出格式
    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    // 音频编码
    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
    // 视频编码
    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
    // 视频大小，清晰度
    setVideoSize(displayMetrics.widthPixels, displayMetrics.heightPixels)
    // 帧率，每秒视频帧数
    setVideoFrameRate(30)
    // 比特率，以位/秒为单位
    setVideoEncodingBitRate(3 * 1024 * 1024)
    // 设置输出文件
    setOutputFile(outputFile.absolutePath)
    // 初始化完成，进入准备阶段
    prepare()
}
```

5. **创建 `VirtualDisplay` 以进行录屏**

```kotlin
// 虚拟屏幕通过 MediaProjection 获取
mVirtualDisplay = mMediaProjection?.createVirtualDisplay(
    /* name = */ "ScreenRecord",
    /* width = */ displayMetrics.widthPixels,
    /* height = */ displayMetrics.heightPixels,
    /* dpi = */ displayMetrics.densityDpi,
    /* flags = */ DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
    /* surface = */ surface,
    /* callback = */ null,
    /* handler = */ null,
)
```

> 把 `VirtualDisplay` 的渲染目标 `Surface` 设置为 `MediaRecorder` 的 `Surface`，后面就可以通过 `MediaRecorder` 将屏幕内容录制下来。

6. **开始录制**

```kotlin
// 开始录制
start()
```

7. **停止录制**

```kotlin
runCatching {
    mMediaRecorder?.stop()
    mMediaRecorder?.reset()
    mMediaRecorder?.release()
    mMediaRecorder = null
}.onFailure {
    it.printStackTrace()
}
```
