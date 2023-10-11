package com.fphoenixcorneae.camerax

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageSavedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.fphoenixcorneae.camerax.ui.theme.AudioVideoDevelopDemoTheme
import java.io.File

class MainActivity : ComponentActivity() {

    companion object {
        private val IMAGE_PATH = Environment.getExternalStorageDirectory().absolutePath + "/CameraX"

        /** 相机权限 */
        private val CAMERA_PERMISSIONS = arrayOf(
            "android.permission.CAMERA",
        )

        /** 存储权限 */
        private val STORAGE_PERMISSIONS = arrayOf(
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_EXTERNAL_STORAGE",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            var previewView: PreviewView? = null
            AudioVideoDevelopDemoTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        AndroidView(
                            factory = {
                                // PreviewView 是一个可以剪裁、缩放和旋转以确保正确显示的 View
                                PreviewView(it).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    previewView = this
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(ratio = 0.8f),
                        ) {
                            CameraXManager.startCamera(
                                context = context,
                                lifecycleOwner = lifecycleOwner,
                                surfaceProvider = it.surfaceProvider
                            )
                        }
                        Row(
                            modifier = Modifier.padding(vertical = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Button(
                                onClick = {
                                    // 切换摄像头
                                    if (checkPermissionsGranted(CAMERA_PERMISSIONS)) {
                                        CameraXManager.toggleCamera(
                                            context = context,
                                            lifecycleOwner = lifecycleOwner,
                                            surfaceProvider = previewView!!.surfaceProvider
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Gray,
                                    contentColor = Color.Black,
                                ),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(text = "切换摄像头", fontSize = 16.sp)
                            }
                            Button(
                                onClick = {
                                    // 拍照保存
                                    if (!checkPermissionsGranted(STORAGE_PERMISSIONS)) {
                                        requestPermissions(STORAGE_PERMISSIONS)
                                        return@Button
                                    }
                                    val imageDir = File(IMAGE_PATH)
                                    if (!imageDir.exists()) {
                                        imageDir.mkdirs()
                                    }
                                    val imageFile = File(imageDir, "testPicture.jpg")
                                    if (imageFile.exists()) {
                                        imageFile.delete()
                                    }
                                    imageFile.createNewFile()
                                    CameraXManager.takePicture(context, imageFile, object : OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            galleryAddPic(imageFile.absolutePath)
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                        }
                                    })
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Gray,
                                    contentColor = Color.Black,
                                ),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(text = "拍照", fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }

        if (!checkPermissionsGranted(CAMERA_PERMISSIONS)) {
            requestPermissions(CAMERA_PERMISSIONS)
        }
    }

    private fun galleryAddPic(filePath: String) {
        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also { mediaScanIntent ->
            val f = File(filePath)
            mediaScanIntent.data = Uri.fromFile(f)
            sendBroadcast(mediaScanIntent)
        }
    }

    private fun requestPermissions(permissions: Array<String>) {
        permissions.forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, 100)
            }
        }
    }

    private fun checkPermissionsGranted(permissions: Array<String>): Boolean {
        var granted = true
        run {
            permissions.forEach {
                if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                    granted = false
                    return@run
                }
            }
        }
        return granted
    }
}