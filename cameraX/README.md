# CameraX 实现预览、拍照功能

## 前言

相比 Camera1 与 Camera2 ，Api 的调用还是比较繁琐的，而且像一些最佳尺寸、角度还得自己算。所以 google 推出了 CameraX，虽然底层也是使用
Camera2 的功能，但使用更加简介，主要优势如下：

1. 更加简洁的 API 调用，基本十几行代码就能实现预览
2. Camerax 为 Jetpack 的支持库，所以也具备感知生命周期的功能，即你无需自己释放生命周期
3. 更好的兼容性
4. 更多功能，比如图片分析等

CameraX 的使用需要先添加关联库：

```groovy
def camerax_version = "1.2.1"
// CameraX 核心库
implementation "androidx.camera:camera-camera2:$camerax_version"
// CameraX 生命周期
implementation "androidx.camera:camera-lifecycle:$camerax_version"
// CameraX view 集合，比如 cameraview，preview等
implementation "androidx.camera:camera-view:$camerax_version"
implementation "androidx.camera:camera-video:$camerax_version"
implementation "androidx.camera:camera-extensions:$camerax_version"
```

### 一、相机的开启与预览

1. **申请权限**

```xml

<manifest>
    <!-- 相机权限 start -->
    <uses-permission android:name="android.permission.CAMERA" />

    <uses-feature android:name="android.hardware.camera" android:required="true" />
    <uses-feature android:name="android.hardware.camera.autofocus" />
    <uses-feature android:name="android.hardware.camera.any" />
    <!-- 相机权限 end  -->
</manifest>
```

```kotlin
/** 相机权限 */
private val CAMERA_PERMISSIONS = arrayOf("android.permission.CAMERA",)

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (!checkPermissionsGranted()) {
        requestPermissions()
    }
}

private fun checkPermissionsGranted(): Boolean {
    var granted = true
    run {
        CAMERA_PERMISSIONS.forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                granted = false
                return@run
            }
        }
    }
    return granted
}

private fun requestPermissions() {
    CAMERA_PERMISSIONS.forEach {
        if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, 100)
        }
    }
}
```

2. **开启摄像头预览**

```kotlin
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
```

> `ProcessCameraProvider` 会绑定生命周期，无须担心相机开关问题了。执行步骤如下：
>
> 1. `Preview` 是相机预览的数据流，图像数据会通过它来输出，比如输出到 Surface 中
> 2. `ImageCapture` 用于拍照，可以设置比如大小，曝光等参数
> 3. 指定所需的相机 `LensFacing` 选项
> 4. 将所选相机和任意用例绑定到生命周期，通过 `processCameraProvider.bindToLifecycle`
>    ，将宿主的生命周期，与 `cameraSelector`、`preview` 和 `mImageCapture` 绑定起来。
> 5. 通过 `preview.setSurfaceProvider()` 设置要预览的 surface，将 `Preview` 连接到 `PreviewView`

`PreviewView` 是一个可以剪裁、缩放和旋转以确保正确显示的 View，不用自己去设置预览的角度等问题了。

### 二、切换摄像头

```kotlin
// 切换摄像头
if (checkPermissionsGranted(CAMERA_PERMISSIONS)) {
    CameraXManager.toggleCamera(
        context = context,
        lifecycleOwner = lifecycleOwner,
        surfaceProvider = previewView!!.surfaceProvider
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
```

### 三、拍照

> 使用 `mImageCapture?.takePicture()` 方法即可。

```kotlin
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
```
