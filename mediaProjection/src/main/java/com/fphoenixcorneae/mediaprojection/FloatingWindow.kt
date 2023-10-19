package com.fphoenixcorneae.mediaprojection

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.Timer
import kotlin.concurrent.fixedRateTimer
import kotlin.math.roundToInt

@SuppressLint("SetTextI18n")
@Composable
fun FloatingWindow(onClick: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val windowManager = ContextCompat.getSystemService(context, WindowManager::class.java)
    val layoutParams = remember { WindowManager.LayoutParams() }
    var timingText: TextView? = null
    var timer: Timer? = null

    LaunchedEffect(key1 = Unit) {
        val currentTimeMillis = System.currentTimeMillis()
        val simpleDateFormat = SimpleDateFormat("mm:ss", Locale.CHINA)
        simpleDateFormat.timeZone = TimeZone.getTimeZone("UT+08:00")
        timer = fixedRateTimer("ScreenRecord", initialDelay = 1000, period = 1000) {
            coroutineScope.launch(Dispatchers.Main) {
                val timing = scheduledExecutionTime() - currentTimeMillis
                timingText?.text = simpleDateFormat.format(timing)
            }
        }
    }

    // 设置悬浮窗的参数
    layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
    layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    }
    layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    layoutParams.format = PixelFormat.TRANSLUCENT
    layoutParams.gravity = Gravity.BOTTOM or Gravity.END
    layoutParams.x = density.run { 20.dp.toPx().roundToInt() }
    layoutParams.y = density.run { 20.dp.toPx().roundToInt() }

    // 创建一个 floatingView，用于显示悬浮窗
    val floatingView = remember {
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener {
                timer?.cancel()
                onClick()
            }
            View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.Red.toArgb())
                    cornerRadius = density.run { 2.dp.toPx() }
                }
            }.also {
                addView(
                    it,
                    LinearLayout.LayoutParams(
                        density.run { 8.dp.toPx().roundToInt() },
                        density.run { 8.dp.toPx().roundToInt() },
                    ),
                )
            }
            Space(context).also {
                addView(
                    it,
                    LinearLayout.LayoutParams(
                        density.run { 4.dp.toPx().roundToInt() },
                        density.run { 0.dp.toPx().roundToInt() },
                    ),
                )
            }
            TextView(context).apply {
                text = "00:00"
                setTextColor(android.graphics.Color.RED)
                textSize = 12f
            }.also {
                timingText = it
                addView(it)
            }
        }
    }

    // 将 floatingView 添加到 WindowManager 中
    DisposableEffect(Unit) {
        windowManager?.addView(floatingView, layoutParams)
        onDispose {
            windowManager?.removeView(floatingView)
        }
    }
}