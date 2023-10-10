package com.fphoenixcorneae.camera2

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.TextureView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.fphoenixcorneae.camera2.ui.theme.AudioVideoDevelopDemoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    companion object {
        private val IMAGE_PATH = Environment.getExternalStorageDirectory().absolutePath + "/Camera2"

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
            var textureView: TextureView? = null
            AudioVideoDevelopDemoTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        AndroidView(
                            factory = {
                                TextureView(it).apply {
                                    surfaceTextureListener = Camera2Manager
                                    textureView = this
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(ratio = 0.8f),
                        ) {

                        }
                        Row(
                            modifier = Modifier.padding(vertical = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Button(
                                onClick = {
                                    // 切换摄像头
                                    if (checkPermissionsGranted(CAMERA_PERMISSIONS)) {
                                        textureView?.surfaceTexture?.let {
                                            Camera2Manager.toggleCamera(it)
                                        }
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
                                    Camera2Manager.takePicture { bitmap ->
                                        savePicture(bitmap)
                                    }
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
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(key1 = lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_CREATE -> Camera2Manager.initCamera(this@MainActivity)

                        Lifecycle.Event.ON_START -> {}
                        Lifecycle.Event.ON_RESUME -> {
                            if (checkPermissionsGranted(CAMERA_PERMISSIONS)) {
                                textureView?.surfaceTexture?.let {
                                    Camera2Manager.openCamera(it)
                                }
                            }
                        }

                        Lifecycle.Event.ON_PAUSE -> Camera2Manager.closeCamera()
                        Lifecycle.Event.ON_STOP -> {}
                        Lifecycle.Event.ON_DESTROY -> {}
                        Lifecycle.Event.ON_ANY -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer = observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }
        }

        if (!checkPermissionsGranted(CAMERA_PERMISSIONS)) {
            requestPermissions(CAMERA_PERMISSIONS)
        }
    }

    /**
     * 拍照保存图片
     */
    private fun savePicture(bitmap: Bitmap?) {
        runCatching {
            lifecycleScope.launch(Dispatchers.IO) {
                bitmap?.let {
                    val imageDir = File(IMAGE_PATH)
                    if (!imageDir.exists()) {
                        imageDir.mkdirs()
                    }
                    val imageFile = File(imageDir, "testPicture.jpg")
                    if (imageFile.exists()) {
                        imageFile.delete()
                    }
                    imageFile.createNewFile()
                    FileOutputStream(imageFile).use {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "图片保存成功", Toast.LENGTH_SHORT).show()
                        }
                        galleryAddPic(imageFile.absolutePath)
                        Log.i("Camera1", "savePicture: 图片保存成功")
                    }
                }
            }
        }.onFailure {
            it.printStackTrace()
            Toast.makeText(this@MainActivity, "图片保存失败", Toast.LENGTH_SHORT).show()
            Log.i("Camera1", "savePicture: 图片保存失败")
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
                ActivityCompat.requestPermissions(this, CAMERA_PERMISSIONS, 100)
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