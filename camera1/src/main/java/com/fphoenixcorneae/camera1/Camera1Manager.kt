package com.fphoenixcorneae.camera1

import android.app.Activity
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.hardware.Camera
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import java.lang.ref.WeakReference

object Camera1Manager : SurfaceHolder.Callback {
    var mFrontCameraId = -1
    var mFrontCameraInfo: Camera.CameraInfo? = null
    var mBackCameraId = -1
    var mBackCameraInfo: Camera.CameraInfo? = null
    var mCamera: Camera? = null
    var mCurrentCameraId = -1
    var mActivity: WeakReference<Activity>? = null

    /**
     * 初始化相机
     */
    fun initCamera() {
        // 获取相机个数
        val numberOfCameras = Camera.getNumberOfCameras()
        for (i in 0 until numberOfCameras) {
            val info = Camera.CameraInfo()
            // 获取相机信息
            Camera.getCameraInfo(i, info)
            when {
                Camera.CameraInfo.CAMERA_FACING_FRONT == info.facing -> {
                    // 前置摄像头
                    mFrontCameraId = i
                    mFrontCameraInfo = info
                }

                Camera.CameraInfo.CAMERA_FACING_BACK == info.facing -> {
                    // 后置摄像头
                    mBackCameraId = i
                    mBackCameraInfo = info
                }
            }
        }
    }

    /**
     * 打开摄像头
     */
    fun openCamera(activity: Activity) {
        val cameraId = if (mCurrentCameraId != -1) mCurrentCameraId else mBackCameraId
        // 根据 cameraId 打开不同摄像头,注意，Camera1只有打开摄像头之后，才能拿到那些配置数据
        mCamera = Camera.open(cameraId)
        mCurrentCameraId = cameraId
        val info = if (cameraId == mFrontCameraId) mFrontCameraInfo else mBackCameraInfo
        adjustCameraOrientation(activity, info)
    }

    /**
     * 切换摄像头
     */
    fun toggleCamera(activity: Activity) {
        val cameraId = if (mCurrentCameraId == mFrontCameraId) mBackCameraId else mFrontCameraId
        // 根据 cameraId 打开不同摄像头,注意，Camera1只有打开摄像头之后，才能拿到那些配置数据
        mCamera = Camera.open(cameraId)
        mCurrentCameraId = cameraId
        val info = if (cameraId == mFrontCameraId) mFrontCameraInfo else mBackCameraInfo
        adjustCameraOrientation(activity, info)
    }

    /**
     * 关闭摄像头
     */
    fun closeCamera() {
        // 停止预览
        mCamera?.stopPreview()
        mCamera?.release()
        mCamera = null
    }

    /**
     * 拍照
     */
    fun takePicture(onPictureTaken: (data: ByteArray?, facing: Int) -> Unit) {
        val camera = mCamera
        camera?.takePicture(
            /* shutter = */ {  },
            /* raw = */ null,
            /* postview = */ null,
        )
        /* jpeg = */
        { data, _ ->
            val info = if (mCurrentCameraId == mFrontCameraId) mFrontCameraInfo else mBackCameraInfo
            onPictureTaken(data, info?.facing ?: 0)
        }
    }

    /**
     * 矫正相机预览画面
     */
    private fun adjustCameraOrientation(activity: Activity, info: Camera.CameraInfo?) {
        mActivity = WeakReference(activity)
        // 判断当前的横竖屏
        val rotation: Int = activity.windowManager.defaultDisplay.rotation
        var degree = 0
        when (rotation) {
            Surface.ROTATION_0 -> degree = 0
            Surface.ROTATION_90 -> degree = 90
            Surface.ROTATION_180 -> degree = 180
            Surface.ROTATION_270 -> degree = 270
        }
        var result = 0
        when (info?.facing) {
            Camera.CameraInfo.CAMERA_FACING_BACK -> {
                // 后置摄像头
                result = (info.orientation - degree + 360) % 360
            }

            Camera.CameraInfo.CAMERA_FACING_FRONT -> {
                // 前置摄像头
                // 先镜像
                result = (info.orientation + degree) % 360
                result = (360 - result) % 360
            }
        }
        mCamera?.setDisplayOrientation(result)
    }

    /**
     * 开始预览
     */
    fun startPreview(holder: SurfaceHolder, width: Int, height: Int) {
        // 设置预览参数
        initPreviewParams(width, height)
        // 设置预览 SurfaceHolder
        runCatching {
            mCamera?.setPreviewDisplay(holder)
        }.onFailure {
            it.printStackTrace()
        }
        // 开始预览
        mCamera?.startPreview()
    }

    /**
     * 设置预览参数，需要制定尺寸才行
     * 在相机中，width > height 的，而我们的 UI 是 4:5，所以这里也要做换算
     *
     * @param shortSize
     * @param longSize
     */
    private fun initPreviewParams(shortSize: Int, longSize: Int) {
        val camera = mCamera
        if (camera != null) {
            val parameters = camera.parameters
            // 获取手机支持的尺寸
            val sizes = parameters.supportedPreviewSizes
            val bestPreviewSize: Camera.Size = getBestPreviewSize(sizes)
            // 设置预览大小
            parameters.setPreviewSize(bestPreviewSize.width, bestPreviewSize.height)
            // 设置格式，所有的相机都支持 NV21格式
            parameters.previewFormat = ImageFormat.NV21
            // 设置图片大小，拍照
            val bestPictureSize = getBestPictureSize(sizes, parameters.previewSize)
            parameters.setPictureSize(bestPictureSize.width, bestPictureSize.height)
            // 设置照片输出的格式
            parameters.pictureFormat = PixelFormat.JPEG
            // 设置照片质量
            parameters.set("jpeg-quality", 100)

            // 设置自动聚焦
            val modes = parameters.supportedFocusModes
            // 查看支持的聚焦模式
            for (mode in modes) {
                // 默认图片聚焦模式
                if (mode.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                    break
                }
            }
            camera.parameters = parameters
        }
    }

    /**
     * 获取预览最后尺寸
     *
     * @param shortSize
     * @param longSize
     * @param sizes
     */
    private fun getBestSize(shortSize: Int, longSize: Int, sizes: List<Camera.Size>): Camera.Size {
        var bestSize: Camera.Size? = null
        val uiRatio = longSize.toFloat() / shortSize
        var minRatio = uiRatio
        run {
            for (previewSize in sizes) {
                val cameraRatio = previewSize.width.toFloat() / previewSize.height

                // 比例相同
                if (uiRatio == cameraRatio) {
                    bestSize = previewSize
                    return@run
                }

                // 如果找不到比例相同的，找一个最近的,防止预览变形
                val offset = Math.abs(cameraRatio - minRatio)
                if (offset < minRatio) {
                    minRatio = offset
                    bestSize = previewSize
                }
            }
        }
        return bestSize!!
    }

    /**
     * 获取预览最好尺寸
     */
    fun getBestPreviewSize(localSizes: List<Camera.Size>): Camera.Size {
        var biggestSize: Camera.Size? = null
        // 优先选屏幕分辨率
        var fitSize: Camera.Size? = null
        // 没有屏幕分辨率就取跟屏幕分辨率相近(大)的size
        var targetSize: Camera.Size? = null
        // 没有屏幕分辨率就取跟屏幕分辨率相近(小)的size
        var targetSiz2: Camera.Size? = null
        for (element in localSizes) {
            val size: Camera.Size = element
            if (biggestSize == null || size.width >= biggestSize.width && size.height >= biggestSize.height) {
                biggestSize = size
            }
            when {
                size.width == screenHeight()
                        && size.height == screenWidth() -> {
                    fitSize = size
                }

                size.width == screenHeight() || size.height == screenWidth() -> {
                    when {
                        targetSize == null -> {
                            targetSize = size
                        }

                        size.width < screenHeight() || size.height < screenWidth() -> {
                            targetSiz2 = size
                        }
                    }
                }
            }
        }
        if (fitSize == null) {
            fitSize = targetSize
        }
        if (fitSize == null) {
            fitSize = targetSiz2
        }
        if (fitSize == null) {
            fitSize = biggestSize
        }
        return fitSize!!
    }

    /**
     * 输出的照片为最高像素
     */
    fun getBestPictureSize(localSizes: List<Camera.Size>, previewSize: Camera.Size?): Camera.Size {
        var biggestSize: Camera.Size? = null
        // 优先选预览界面的尺寸
        var fitSize: Camera.Size? = null
        var previewSizeScale = 0f
        if (previewSize != null) {
            previewSizeScale = previewSize.width / previewSize.height.toFloat()
        }
        for (element in localSizes) {
            val size: Camera.Size = element
            if (biggestSize == null) {
                biggestSize = size
            } else if (size.width >= biggestSize.width && size.height >= biggestSize.height) {
                biggestSize = size
            }

            // 选出与预览界面等比的最高分辨率
            if (previewSizeScale > 0 && size.width >= previewSize!!.width && size.height >= previewSize.height) {
                val sizeScale = size.width / size.height.toFloat()
                if (sizeScale == previewSizeScale) {
                    if (fitSize == null) {
                        fitSize = size
                    } else if (size.width >= fitSize.width && size.height >= fitSize.height) {
                        fitSize = size
                    }
                }
            }
        }

        // 如果没有选出fitSize, 那么最大的Size就是FitSize
        if (fitSize == null) {
            fitSize = biggestSize
        }
        return fitSize!!
    }

    private fun screenWidth() = run {
        val displayMetrics = DisplayMetrics()
        mActivity?.get()?.windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        displayMetrics.widthPixels
    }

    private fun screenHeight() = run {
        val displayMetrics = DisplayMetrics()
        mActivity?.get()?.windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        displayMetrics.heightPixels
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.i("SurfacePreview", "surfaceChanged: width: $width, height: $height")
        startPreview(holder = holder, width = width, height = height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
    }
}