package com.fphoenixcorneae.audio

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fphoenixcorneae.audio.ui.theme.AudioVideoDevelopDemoTheme
import com.fphoenixcorneae.audio.ui.theme.Purple40
import com.fphoenixcorneae.audio.ui.theme.PurpleGrey40
import java.io.File

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AudioRecord"

        /** 音频保存路径 */
        private val AUDIO_SAVE_PATH = Environment.getExternalStorageDirectory().absolutePath + "/Audio"

        private const val PCM_FILE_NAME = "testAudio.pcm"
        private const val WAV_FILE_NAME = "testAudio.wav"

        /** 权限 */
        private val PERMISSIONS = arrayOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_EXTERNAL_STORAGE",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudioVideoDevelopDemoTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var isPressed by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(30.dp))
                                .background(if (isPressed) PurpleGrey40 else Purple40)
                                .pointerInput(isPressed) {
                                    awaitPointerEventScope {
                                        if (isPressed) {
                                            // 手指抬起
                                            waitForUpOrCancellation()
                                            Log.i(TAG, "手指抬起")
                                            isPressed = false
                                            AudioManager.stopRecord()
                                        } else {
                                            // 手指按下
                                            awaitFirstDown()
                                            Log.i(TAG, "手指按下")
                                            isPressed = true
                                            AudioManager.startRecord(
                                                lifecycleScope,
                                                AUDIO_SAVE_PATH,
                                                PCM_FILE_NAME,
                                                WAV_FILE_NAME
                                            )
                                        }
                                    }
                                }, contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "按住录音", fontSize = 14.sp, color = Color.White
                            )
                        }
                        Button(
                            onClick = {
                                AudioManager.playWav(this@MainActivity, AUDIO_SAVE_PATH, WAV_FILE_NAME)
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Purple40,
                                contentColor = Color.White,
                            )
                        ) {
                            Text(text = "点击播放wav音频", fontSize = 14.sp)
                        }
                        Button(
                            onClick = {
                                AudioManager.playPcmStream(
                                    lifecycleScope,
                                    File(AUDIO_SAVE_PATH, PCM_FILE_NAME)
                                )
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Purple40,
                                contentColor = Color.White,
                            )
                        ) {
                            Text(text = "点击播放PCM音频（Stream模式）", fontSize = 14.sp)
                        }
                        Button(
                            onClick = {
                                AudioManager.playPcmStatic(
                                    lifecycleScope,
                                    File(AUDIO_SAVE_PATH, PCM_FILE_NAME)
                                )
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Purple40,
                                contentColor = Color.White,
                            )
                        ) {
                            Text(text = "点击播放PCM音频（Static模式）", fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        requestPermissions()
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