package com.fphoenixcorneae.mediaprojection

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


object MediaProjectionHelper {
    private var mMediaProjection: MediaProjection? = null
    private var mImageReader: ImageReader? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mMediaRecorder: MediaRecorder? = null

    /**
     * 获取 MediaProjectionManager 服务
     */
    fun getMediaProjectionManager(context: Context) =
        ContextCompat.getSystemService(context, MediaProjectionManager::class.java)

    @SuppressLint("WrongConstant")
    fun configImageReader(context: Context, resultCode: Int, resultData: Intent, onImageAvailable: (Bitmap?) -> Unit) {
        // 获取 MediaProjection 对象
        mMediaProjection =
            MediaProjectionService.getMediaProjection(getMediaProjectionManager(context), resultCode, resultData)
        val displayMetrics = context.applicationContext.resources.displayMetrics
        // 参数1：默认图像的宽度像素
        // 参数2：默认图像的高度像素
        // 参数3：图像的像素格式
        // 参数4：用户想要读图像的最大数量
        // 需要注意的是，Camera 获取的是 YUV 数据，而MediaProjection 获取的则是 RGBA 的数据
        mImageReader = ImageReader.newInstance(
            /* width = */ displayMetrics.widthPixels,
            /* height = */ displayMetrics.heightPixels,
            /* format = */ PixelFormat.RGBA_8888,
            /* maxImages = */ 1,
        ).apply {
            setOnImageAvailableListener(
                /* listener = */
                {
                    CoroutineScope(Dispatchers.IO).launch {
                        val bitmap = acquireLatestImageToBitmap(it)
                        withContext(Dispatchers.Main) {
                            onImageAvailable(bitmap)
                        }
                        mMediaProjection?.stop()
                    }
                },
                /* handler = */ null,
            )
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
        }
    }

    fun configMediaRecorder(
        context: Context,
        outputDirPath: String,
        outputFileName: String,
        resultCode: Int,
        resultData: Intent,
    ) {
        val outputDir = File(outputDirPath)
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val outputFile = File(outputDirPath, outputFileName)
        if (outputFile.exists()) {
            outputFile.delete()
        }
        outputFile.createNewFile()
        runCatching {
            mMediaProjection =
                MediaProjectionService.getMediaProjection(getMediaProjectionManager(context), resultCode, resultData)
            val displayMetrics = context.applicationContext.resources.displayMetrics
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

                // 开始录制
                start()
            }
        }.onFailure {
            it.printStackTrace()
        }
    }

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

    fun release(context: Context) {
        mMediaProjection?.stop()
        mMediaProjection = null
        mImageReader?.close()
        mImageReader = null
        runCatching {
            mMediaRecorder?.stop()
            mMediaRecorder?.reset()
            mMediaRecorder?.release()
            mMediaRecorder = null
        }.onFailure {
            it.printStackTrace()
        }
        mVirtualDisplay?.release()
        mVirtualDisplay = null
        context.stopService(Intent(context, MediaProjectionService::class.java))
    }
}