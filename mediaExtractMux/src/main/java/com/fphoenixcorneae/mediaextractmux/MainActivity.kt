package com.fphoenixcorneae.mediaextractmux

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fphoenixcorneae.mediaextractmux.ui.theme.AudioVideoDevelopDemoTheme
import java.io.File

class MainActivity : ComponentActivity() {

    companion object {
        private val FILE_PATH = Environment.getExternalStorageDirectory().absolutePath + "/MediaExtractMux"

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
            AudioVideoDevelopDemoTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize(),
                    ) {
                        AndroidView(
                            factory = {
                                SurfaceView(it).apply {
                                    holder.addCallback(object : SurfaceHolder.Callback {
                                        override fun surfaceCreated(holder: SurfaceHolder) {
                                            MediaPlayerHelper.prepare(context, R.raw.big_buck_bunny, holder)
                                        }

                                        override fun surfaceChanged(
                                            holder: SurfaceHolder,
                                            format: Int,
                                            width: Int,
                                            height: Int,
                                        ) {
                                        }

                                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        }
                                    })
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color.White),
                        ) {
                            Button(
                                onClick = {
                                    MediaPlayerHelper.start()
                                },
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, top = 8.dp, end = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Gray,
                                    contentColor = Color.Black,
                                )
                            ) {
                                Text(text = "播放", fontSize = 14.sp)
                            }
                            Button(
                                onClick = {
                                    MediaPlayerHelper.pause()
                                },
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, top = 8.dp, end = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Gray,
                                    contentColor = Color.Black,
                                )
                            ) {
                                Text(text = "暂停", fontSize = 14.sp)
                            }
                            Button(
                                onClick = {
                                    if (!checkPermissionsGranted(STORAGE_PERMISSIONS)) {
                                        requestPermissions(STORAGE_PERMISSIONS)
                                        return@Button
                                    }
                                    val videoUri =
                                        Uri.parse("android.resource://${context.packageName}/raw/big_buck_bunny")
                                    MediaManager.extractVideo(
                                        context = context,
                                        uri = videoUri,
                                        outputDir = File(FILE_PATH)
                                    )
                                },
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, top = 8.dp, end = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Gray,
                                    contentColor = Color.Black,
                                )
                            ) {
                                Text(text = "MediaExtractor 解析视频，将视频和音频分离开", fontSize = 14.sp)
                            }
                            Button(
                                onClick = {
                                    if (!checkPermissionsGranted(STORAGE_PERMISSIONS)) {
                                        requestPermissions(STORAGE_PERMISSIONS)
                                        return@Button
                                    }
                                    MediaManager.muxVideo(
                                        context = context,
                                        outputDir = File(FILE_PATH),
                                        outputName = "new_video.mp4"
                                    )
                                },
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, top = 8.dp, end = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Gray,
                                    contentColor = Color.Black,
                                )
                            ) {
                                Text(text = "MediaMuxer 封装视频，将视频和音频合成新视频", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(key1 = lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_CREATE -> {}
                        Lifecycle.Event.ON_START -> {}
                        Lifecycle.Event.ON_RESUME -> {}
                        Lifecycle.Event.ON_PAUSE -> {}
                        Lifecycle.Event.ON_STOP -> {}
                        Lifecycle.Event.ON_DESTROY -> {
                            MediaPlayerHelper.release()
                        }

                        Lifecycle.Event.ON_ANY -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer = observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer = observer)
                }
            }
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

