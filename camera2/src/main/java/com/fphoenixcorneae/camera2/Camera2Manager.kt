package com.fphoenixcorneae.camera2

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.util.DisplayMetrics
import android.util.Size
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference
import java.util.Collections
import kotlin.math.sign

object Camera2Manager : SurfaceTextureListener, OnImageAvailableListener, CameraDevice.StateCallback() {
    private var mCameraManager: CameraManager? = null
    private var mFrontCameraId: String? = null
    private var mFrontCameraCharacteristics: CameraCharacteristics? = null
    private var mBackCameraId: String? = null
    private var mBackCameraCharacteristics: CameraCharacteristics? = null
    private var mCurrentCameraId: String? = null
    private var mSensorOrientation: Int? = null
    private var mImageReader: ImageReader? = null
    private var mCameraDevice: CameraDevice? = null
    private var mCameraCaptureSession: CameraCaptureSession? = null
    private var mActivity: WeakReference<Activity>? = null
    private var mSurfaceTexture: SurfaceTexture? = null
    private var mOnImageAvailable: ((bitmap:Bitmap?) -> Unit)? = null

    /**
     * 初始化相机
     */
    fun initCamera(activity: Activity) {
        runCatching {
            mActivity = WeakReference(activity)
            // 获取相机服务 CameraManager
            mCameraManager = ContextCompat.getSystemService(activity, CameraManager::class.java)
            // 遍历设备支持的相机 ID
            val cameraIdList = mCameraManager?.cameraIdList
            cameraIdList?.forEach { cameraId ->
                // 获取相机信息 CameraCharacteristics 类
                val characteristics = mCameraManager?.getCameraCharacteristics(cameraId)
                // 获取相机方向
                val facing = characteristics?.get(CameraCharacteristics.LENS_FACING)
                if (facing != null) {
                    when (facing) {
                        CameraCharacteristics.LENS_FACING_BACK -> {
                            // 后置摄像头
                            mBackCameraId = cameraId
                            mBackCameraCharacteristics = characteristics
                            mCurrentCameraId = cameraId
                        }

                        CameraCharacteristics.LENS_FACING_FRONT -> {
                            // 前置摄像头
                            mFrontCameraId = cameraId
                            mFrontCameraCharacteristics = characteristics
                        }
                    }
                    // 是否支持 Camera2
                    val level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                    if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                        Toast.makeText(activity, "您的手机不支持Camera2！", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.onFailure {
            it.printStackTrace()
        }
    }

    /**
     * 打开摄像头
     */
    @SuppressLint("MissingPermission")
    fun openCamera(surfaceTexture: SurfaceTexture) {
        mSurfaceTexture = surfaceTexture
        val characteristics =
            if (mCurrentCameraId == mFrontCameraId) mFrontCameraCharacteristics else mBackCameraCharacteristics
        // 获取配置
        val streamConfigurationMap = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        // 获取摄像头传感器的方向
        mSensorOrientation = characteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION)
        val previewSizes = streamConfigurationMap?.getOutputSizes(SurfaceTexture::class.java)
        val bestPreviewSize = getBestPreviewSize(previewSizes?.toList())
        // 配置预览属性
        // 与 Camera1 不同，Camera2 是把尺寸信息给到 Surface (SurfaceView 或者 ImageReader)
        // Camera 会根据 Surface 配置的大小，输出对应尺寸的画面
        // 注意摄像头的 width > height ，而我们使用竖屏，所以宽高要变化一下
        surfaceTexture.setDefaultBufferSize(bestPreviewSize.height, bestPreviewSize.width)
        // 设置图片尺寸
        val outputSizes = streamConfigurationMap?.getOutputSizes(ImageFormat.JPEG)
        val largest = Collections.max(outputSizes?.toList().orEmpty(), Comparator<Size> { o1, o2 ->
            return@Comparator sign(o1.width * o1.height.toDouble() - o2.width * o2.height.toDouble()).toInt()
        })
        // 设置 ImageReader 配置大小，且最大 Image 为 1，因为是 JPEG
        mImageReader = ImageReader.newInstance(largest.width, largest.height, ImageFormat.JPEG, 1)
        // 拍照监听
        mImageReader?.setOnImageAvailableListener(this, null)

        runCatching {
            // 打开摄像头，监听数据
            mCameraManager?.openCamera(mCurrentCameraId.orEmpty(), this, null)
        }.onFailure {
            it.printStackTrace()
        }
    }

    /**
     * 切换摄像头
     */
    fun toggleCamera(surfaceTexture: SurfaceTexture) {
        val cameraId = if (mCurrentCameraId == mFrontCameraId) mBackCameraId else mFrontCameraId
        mCurrentCameraId = cameraId
        closeCamera()
        openCamera(surfaceTexture)
    }

    /**
     * 关闭摄像头
     */
    fun closeCamera() {
        // 停止预览
        mCameraCaptureSession?.stopRepeating()
        mCameraCaptureSession = null
        // 关闭设备
        mCameraDevice?.close()
        mCameraDevice = null

        mImageReader?.close()
        mImageReader = null
    }

    /**
     * 开始预览，创建 Session
     */
    private fun startPreview(cameraDevice: CameraDevice) {
        runCatching {
            // 创建作为预览的 CaptureRequest.builder
            val captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            val surface = Surface(mSurfaceTexture)
            // 添加 surface 容器
            captureBuilder.addTarget(surface)
            // 创建 CameraCaptureSession，该对象负责管理处理预览请求和拍照请求,这个必须在创建 Session 之前就准备好，传递给底层用于 pipeline
            cameraDevice.createCaptureSession(
                listOf<Surface>(surface, mImageReader!!.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        mCameraCaptureSession = session
                        runCatching {
                            // 设置自动聚焦
                            captureBuilder.set(
                                CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            // 设置自动曝光
                            captureBuilder.set(
                                CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                            )

                            // 创建 CaptureRequest
                            val build = captureBuilder.build()
                            // 设置预览时连续捕获图片数据
                            session.setRepeatingRequest(build, null, null)
                        }.onFailure {
                            it.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(mActivity?.get(), "配置失败", Toast.LENGTH_SHORT).show()
                    }
                },
                null
            )
        }.onFailure {
            it.printStackTrace()
        }
    }

    /**
     * 拍照
     */
    fun takePicture(onImageAvailable: (bitmap:Bitmap?) -> Unit) {
        mOnImageAvailable = onImageAvailable
        // 创建一个拍照的 session
        val captureRequest = mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        // 设置装载图像数据的 Surface
        mImageReader?.let { captureRequest?.addTarget(it.surface) }
        // 聚焦
        captureRequest?.set(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        )
        // 自动曝光
        captureRequest?.set(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
        )
        // 获取设备方向
        val rotation = mActivity?.get()?.windowManager?.defaultDisplay?.rotation
        // 根据设备方向计算设置照片的方向
        captureRequest?.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation))
        // 先停止预览
        mCameraCaptureSession?.stopRepeating()

        captureRequest?.build()?.let {
            mCameraCaptureSession?.capture(it, object : CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    super.onCaptureCompleted(session, request, result)
                    runCatching {
                        // 拍完之后，让它继续可以预览
                        val captureRequest1 = mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        captureRequest1?.addTarget(Surface(mSurfaceTexture))
                        captureRequest1?.build()?.let {
                            mCameraCaptureSession?.setRepeatingRequest(it, null, null)
                        }
                    }.onFailure {
                        it.printStackTrace()
                    }
                }
            }, null)
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private fun getOrientation(rotation: Int?): Int {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (when (rotation) {
            Surface.ROTATION_0 -> 90

            Surface.ROTATION_90 -> 0

            Surface.ROTATION_180 -> 270

            else -> 180
        } + (mSensorOrientation ?: 0) + 270) % 360
    }

    /**
     * 获取预览最好尺寸
     */
    private fun getBestPreviewSize(localSizes: List<Size>?): Size {
        var biggestSize: Size? = null
        // 优先选屏幕分辨率
        var fitSize: Size? = null
        // 没有屏幕分辨率就取跟屏幕分辨率相近(大)的size
        var targetSize: Size? = null
        // 没有屏幕分辨率就取跟屏幕分辨率相近(小)的size
        var targetSiz2: Size? = null
        localSizes?.forEach { element ->
            val size: Size = element
            if (biggestSize == null || (size.width >= biggestSize!!.width && size.height >= biggestSize!!.height)) {
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

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        // 打开摄像头
        openCamera(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }

    override fun onImageAvailable(reader: ImageReader?) {
        // 获取捕获的照片数据
        val image = mImageReader?.acquireLatestImage()
        // 拿到所有的 Plane 数组
        val planes = image!!.planes
        // 由于是 JPEG ，只需要获取下标为 0 的数据即可
        val buffer = planes[0].buffer
        val data = ByteArray(buffer.remaining())
        // 把 bytebuffer 的数据给 byte数组
        buffer.get(data)
        var bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        // 旋转图片
        if (mCurrentCameraId == mFrontCameraId) {
            bitmap = bitmap.rotate(270f)
            bitmap = bitmap.mirror()
        } else {
            bitmap = bitmap.rotate(90f)
        }
        mOnImageAvailable?.invoke(bitmap)
    }

    override fun onOpened(camera: CameraDevice) {
        // 摄像头已打开，可以预览了
        mCameraDevice = camera
        startPreview(camera)
    }

    override fun onDisconnected(camera: CameraDevice) {
        camera.close()
    }

    override fun onError(camera: CameraDevice, error: Int) {
        camera.close()
    }
}