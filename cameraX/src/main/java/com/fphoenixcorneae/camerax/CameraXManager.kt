package com.fphoenixcorneae.camerax

import android.content.Context
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageSavedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.Preview.SurfaceProvider
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

object CameraXManager {

    private var mFacing = CameraSelector.LENS_FACING_BACK
    private var mImageCapture: ImageCapture? = null

    /**
     * 开启摄像头
     */
    fun startCamera(context: Context, lifecycleOwner: LifecycleOwner, surfaceProvider: SurfaceProvider) {
        // 返回当前可以绑定生命周期的 ProcessCameraProvider，camerax 会自己释放
        val processCameraProviderFuture = ProcessCameraProvider.getInstance(context)
        processCameraProviderFuture.addListener(
            /* listener = */
            {
                runCatching {
                    val processCameraProvider = processCameraProviderFuture.get()
                    // 预览的 capture，支持角度换算
                    val preview = Preview.Builder().build()
                    // 创建图片的 capture
                    mImageCapture = ImageCapture.Builder()
                        .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                        .build()
                    // 选择后置摄像头
                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(mFacing)
                        .build()
                    // 预览之前先解绑
                    processCameraProvider.unbindAll()
                    // 将数据绑定到相机的生命周期中
                    val camera = processCameraProvider.bindToLifecycle(
                        /* lifecycleOwner = */ lifecycleOwner,
                        /* cameraSelector = */ cameraSelector,
                        /* ...useCases = */ preview, mImageCapture,
                    )
                    // 将 previewView 的 surface 给相机预览
                    preview.setSurfaceProvider(surfaceProvider)
                    camera.cameraInfo.cameraState.observe(lifecycleOwner) {
                        Toast.makeText(context, "CameraState: ${it.type}", Toast.LENGTH_SHORT).show()
                        it.error?.let {
                            Toast.makeText(context, "CameraState error: ${it.code}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.onFailure {
                    it.printStackTrace()
                }
            },
            /* executor = */
            ContextCompat.getMainExecutor(context),
        )
    }

    /**
     * 切换摄像头
     */
    fun toggleCamera(context: Context, lifecycleOwner: LifecycleOwner, surfaceProvider: SurfaceProvider) {
        /**
         * 白屏的问题是 PreviewView 移除所有 View，且没数据到 Surface，
         * 所以只留背景色，可以对此做处理
         */
        mFacing =
            if (mFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
        startCamera(context, lifecycleOwner, surfaceProvider)
    }

    /**
     * 拍照
     */
    fun takePicture(context: Context, picFile: File, onImageSavedCallback: OnImageSavedCallback?) {
        // 创建包文件的数据，比如创建文件
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(picFile).build()
        // 开始拍照
        mImageCapture?.takePicture(
            /* outputFileOptions = */ outputFileOptions,
            /* executor = */ ContextCompat.getMainExecutor(context),
            /* imageSavedCallback = */
            object :
                ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onImageSavedCallback?.onImageSaved(outputFileResults)
                    Toast.makeText(context, "图片保存成功！", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    onImageSavedCallback?.onError(exception)
                    Toast.makeText(context, "图片保存失败！", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}