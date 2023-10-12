package com.fphoenixcorneae.webrtc

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.fphoenixcorneae.webrtc.ui.theme.AudioVideoDevelopDemoTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.webrtc.SurfaceViewRenderer

class MainActivity : ComponentActivity() {
    companion object {
        /** 相机权限 */
        private val CAMERA_PERMISSIONS = arrayOf(
            "android.permission.CAMERA",
        )
    }

    private val mPermissionsGranted = MutableStateFlow(false)
    private val mSurfaceViewRenderer = MutableStateFlow<SurfaceViewRenderer?>(null)

    private val mRequestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions: Map<String, Boolean> ->
            var granted = true
            permissions.entries.forEach {
                if (!it.value) {
                    granted = false
                    // 未同意授权
                    if (!shouldShowRequestPermissionRationale(it.key)) {
                        // 用户拒绝权限并且系统不再弹出请求权限的弹窗
                        // 这时需要我们自己处理，比如自定义弹窗告知用户为何必须要申请这个权限
                        Log.d("requestMultiplePermissions", "${it.key} not granted and should not show rationale")
                    }
                }
            }
            mPermissionsGranted.value = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val permissionsGranted by mPermissionsGranted.collectAsState()
            val surfaceViewRenderer by mSurfaceViewRenderer.collectAsState()
            if (permissionsGranted && surfaceViewRenderer != null) {
                WebRTCManager.showVideoTrack(context, surfaceViewRenderer!!)
            }
            AudioVideoDevelopDemoTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AndroidView(
                        factory = {
                            // 视频控件用来承载画面，它是 SurfaceView 的子类
                            SurfaceViewRenderer(context).apply {
                                mSurfaceViewRenderer.value = this
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        // 申请权限
        mRequestMultiplePermissions.launch(CAMERA_PERMISSIONS)
    }
}