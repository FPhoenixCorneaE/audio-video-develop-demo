package com.fphoenixcorneae.mediaprojection

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.lifecycleScope
import com.fphoenixcorneae.mediaprojection.ui.theme.AudioVideoDevelopDemoTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    companion object {
        private val FILE_PATH = Environment.getExternalStorageDirectory().absolutePath + "/MediaProjection"
        private const val RECORD_FILE_NAME = "screen_record.mp4"

        /** 录音、存储权限 */
        private val STORAGE_PERMISSIONS = arrayOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_EXTERNAL_STORAGE",
        )
    }

    private val mScreenCaptureBitmap = MutableStateFlow<Bitmap?>(null)
    private val mCaptureScreenForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.let {
                MediaProjectionHelper.configImageReader(this, result.resultCode, it) {
                    it?.let { lifecycleScope.launch { mScreenCaptureBitmap.emit(it) } }
                }
            }
        }
    private val mCaptureRecordForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.let {
                MediaProjectionHelper.configMediaRecorder(this, FILE_PATH, RECORD_FILE_NAME, result.resultCode, it)
                Toast.makeText(this, "开始录制", Toast.LENGTH_SHORT).show()
            }
        }

    @SuppressLint("ObsoleteSdkInt")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val screenCaptureBitmap by mScreenCaptureBitmap.collectAsState()
            var surfaceView: SurfaceView? = null
            var isRecording by remember { mutableStateOf(false) }
            AudioVideoDevelopDemoTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { SurfaceView(it).also { surfaceView = it } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.8f),
                        ) {
                            screenCaptureBitmap?.run {
                                val canvas = it.holder.lockCanvas()
                                canvas.drawBitmap(
                                    this,
                                    Rect(0, 0, this.width, this.height),
                                    RectF(0f, 0f, it.width.toFloat(), it.height.toFloat()),
                                    null
                                )
                                it.holder.unlockCanvasAndPost(canvas)
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White),
                        ) {
                            Button(
                                onClick = {
                                    MediaProjectionHelper.getMediaProjectionManager(context)?.let {
                                        // 开始截屏
                                        mCaptureScreenForResult.launch(it.createScreenCaptureIntent())
                                    }
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
                                Text(text = "截屏", fontSize = 14.sp)
                            }
                            Button(
                                onClick = {
                                    if (!checkPermissionsGranted(STORAGE_PERMISSIONS)) {
                                        requestPermissions(STORAGE_PERMISSIONS)
                                        return@Button
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                                        && !Settings.canDrawOverlays(context)
                                    ) {
                                        startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:$packageName")
                                            )
                                        )
                                        return@Button
                                    }
                                    isRecording = true
                                    MediaProjectionHelper.getMediaProjectionManager(context)?.let {
                                        // 开始录屏
                                        mCaptureRecordForResult.launch(it.createScreenCaptureIntent())
                                    }
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
                                Text(text = "点击开始录屏", fontSize = 14.sp)
                            }
                            Button(
                                onClick = {
                                    surfaceView?.let {
                                        MediaPlayerHelper.release()
                                        val video = File(FILE_PATH, RECORD_FILE_NAME)
                                        MediaPlayerHelper.prepare(video.absolutePath, it.holder) {
                                            MediaPlayerHelper.start()
                                            Toast.makeText(context, "开始播放", Toast.LENGTH_SHORT).show()
                                        }
                                    }
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
                                Text(text = "播放录制的视频", fontSize = 14.sp)
                            }
                        }
                    }
                }
                if (isRecording) {
                    FloatingWindow {
                        isRecording = false
                        MediaProjectionHelper.release(context)
                    }
                }
            }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(key1 = lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_CREATE -> {
                            startService(Intent(context, MediaProjectionService::class.java))
                        }

                        Lifecycle.Event.ON_START -> {}
                        Lifecycle.Event.ON_RESUME -> {}
                        Lifecycle.Event.ON_PAUSE -> {}
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

