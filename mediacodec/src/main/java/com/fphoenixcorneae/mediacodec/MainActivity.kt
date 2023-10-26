package com.fphoenixcorneae.mediacodec

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.media.MediaCodecList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fphoenixcorneae.mediacodec.decode.AsyncDecodeAudioImpl
import com.fphoenixcorneae.mediacodec.decode.AsyncDecodeVideoImpl
import com.fphoenixcorneae.mediacodec.decode.SyncDecodeAudioImpl
import com.fphoenixcorneae.mediacodec.decode.SyncDecodeVideoImpl
import com.fphoenixcorneae.mediacodec.ui.theme.AudioVideoDevelopDemoTheme
import com.fphoenixcorneae.mediacodec.ui.theme.Purple40
import com.fphoenixcorneae.mediacodec.ui.theme.PurpleGrey40
import java.io.File
import java.util.concurrent.Executors


class MainActivity : ComponentActivity() {
    companion object {
        /** 音频保存路径 */
        private val FILE_PATH = Environment.getExternalStorageDirectory().absolutePath + "/MediaCodec"

        private const val PCM_FILE_NAME = "codec_audio.pcm"
        private const val AAC_FILE_NAME = "codec_audio.aac"

        /** 权限 */
        private val PERMISSIONS = arrayOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_EXTERNAL_STORAGE",
        )
    }

    private var mExecutorService = Executors.newFixedThreadPool(2)
    private var mSyncDecodeVideoImpl: SyncDecodeVideoImpl? = null
    private var mSyncDecodeAudioImpl: SyncDecodeAudioImpl? = null
    private var mAsyncDecodeVideoImpl: AsyncDecodeVideoImpl? = null
    private var mAsyncDecodeAudioImpl: AsyncDecodeAudioImpl? = null

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var isPressed by remember { mutableStateOf(false) }
            var textureView: TextureView? = null
            AudioVideoDevelopDemoTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(30.dp))
                                .background(if (isPressed) PurpleGrey40 else Purple40)
                                .pointerInput(isPressed) {
                                    awaitPointerEventScope {
                                        if (isPressed) {
                                            // 手指抬起
                                            waitForUpOrCancellation()
                                            isPressed = false
                                            AudioManager.stopRecord()
                                            // 将 pcm 文件编码为 aac 文件
                                            MediaCodecManager.encodePcmFile2AacFile(
                                                lifecycleScope,
                                                FILE_PATH,
                                                PCM_FILE_NAME,
                                                AAC_FILE_NAME
                                            )
                                        } else {
                                            // 手指按下
                                            awaitFirstDown()
                                            isPressed = true
                                            AudioManager.startRecord(
                                                lifecycleScope,
                                                FILE_PATH,
                                                PCM_FILE_NAME
                                            )
                                        }
                                    }
                                }, contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "按住录音，生成的 pcm 文件编码为 aac 文件", fontSize = 14.sp, color = Color.White
                            )
                        }
                        Button(
                            onClick = {
                                // 播放 pcm 音频文件
                                AudioManager.playPcmStream(lifecycleScope, File(FILE_PATH, PCM_FILE_NAME))
                            },
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Gray,
                                contentColor = Color.White,
                            )
                        ) {
                            Text(text = "播放 pcm 音频文件", fontSize = 14.sp)
                        }
                        Button(
                            onClick = {
                                // 播放 aac 音频文件
                                MediaPlayerHelper.prepare(File(FILE_PATH, AAC_FILE_NAME).absolutePath) {
                                    it.start()
                                }
                            },
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Gray,
                                contentColor = Color.White,
                            )
                        ) {
                            Text(text = "播放 aac 音频文件", fontSize = 14.sp)
                        }
                        Button(
                            onClick = {
                                textureView?.apply {
                                    release()
                                    mExecutorService = Executors.newFixedThreadPool(2)
                                    val videoUri =
                                        Uri.parse("android.resource://${context.packageName}/raw/big_buck_bunny")
                                    mSyncDecodeVideoImpl =
                                        SyncDecodeVideoImpl(context, videoUri, surfaceTexture)
                                    mSyncDecodeAudioImpl = SyncDecodeAudioImpl(context, videoUri)
                                    mExecutorService.execute(mSyncDecodeVideoImpl)
                                    mExecutorService.execute(mSyncDecodeAudioImpl)
                                }
                            },
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 36.dp, end = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Purple40,
                                contentColor = Color.White,
                            )
                        ) {
                            Text(text = "MediaCodec 解码音视频(同步模式)", fontSize = 14.sp)
                        }
                        Button(
                            onClick = {
                                textureView?.apply {
                                    release()
                                    val videoUri =
                                        Uri.parse("android.resource://${context.packageName}/raw/big_buck_bunny")
                                    mAsyncDecodeVideoImpl =
                                        AsyncDecodeVideoImpl(context, videoUri, surfaceTexture)
                                    mAsyncDecodeAudioImpl = AsyncDecodeAudioImpl(context, videoUri)
                                    mAsyncDecodeVideoImpl?.start()
                                    mAsyncDecodeAudioImpl?.start()
                                }
                            },
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 0.dp, end = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Purple40,
                                contentColor = Color.White,
                            )
                        ) {
                            Text(text = "MediaCodec 解码音视频(异步模式)", fontSize = 14.sp)
                        }

                        AndroidView(
                            factory = {
                                TextureView(it).apply {
                                    textureView = this
                                    surfaceTextureListener = object : SurfaceTextureListener {
                                        override fun onSurfaceTextureAvailable(
                                            surface: SurfaceTexture,
                                            width: Int,
                                            height: Int,
                                        ) {
                                        }

                                        override fun onSurfaceTextureSizeChanged(
                                            surface: SurfaceTexture,
                                            width: Int,
                                            height: Int,
                                        ) {
                                        }

                                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                            return false
                                        }

                                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                                        }

                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                                .height(240.dp),
                        )
                        var mediaCodecList by remember { mutableStateOf("") }
                        Button(
                            onClick = {
                                val decoderList = StringBuilder("")
                                val encoderList = StringBuilder("")
                                // REGULAR_CODECS参考api说明
                                val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
                                val codecs = list.codecInfos

                                decoderList.append("Decoders: \r\n")
                                for (codec in codecs) {
                                    if (codec.isEncoder) {
                                        continue
                                    }
                                    decoderList.append("${codec.name}\r\n")
                                }
                                encoderList.append("\r\nEncoders: \r\n")
                                for (codec in codecs) {
                                    if (codec.isEncoder) {
                                        encoderList.append("${codec.name}\r\n")
                                    }
                                }
                                mediaCodecList = decoderList.toString() + encoderList.toString()
                            },
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 20.dp, end = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Purple40,
                                contentColor = Color.White,
                            )
                        ) {
                            Text(text = "设备支持的编解码器", fontSize = 14.sp)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .padding(start = 16.dp, top = 8.dp, end = 16.dp)
                                .verticalScroll(state = rememberScrollState()),
                        ) {
                            Text(text = mediaCodecList, fontSize = 12.sp, color = Color.Black)
                        }
                    }
                }
            }
        }
        requestPermissions()
    }

    fun release() {
        mExecutorService.shutdownNow()
        mSyncDecodeVideoImpl?.stop()
        mSyncDecodeAudioImpl?.stop()
        mAsyncDecodeVideoImpl?.release()
        mAsyncDecodeAudioImpl?.release()
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 先判断有没有权限
            if (Environment.isExternalStorageManager()) {
                Toast.makeText(
                    this,
                    "Android VERSION  R OR ABOVE，HAVE MANAGE_EXTERNAL_STORAGE GRANTED!",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Android VERSION  R OR ABOVE，NO MANAGE_EXTERNAL_STORAGE GRANTED!",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:" + this.packageName)
                startActivityForResult(intent, 200)
            }
        } else {
            PERMISSIONS.forEach {
                if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, PERMISSIONS, 100)
                }
            }
        }
    }
}

