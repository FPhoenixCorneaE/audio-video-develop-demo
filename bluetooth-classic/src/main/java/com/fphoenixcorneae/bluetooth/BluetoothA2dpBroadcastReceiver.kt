package com.fphoenixcorneae.bluetooth

import android.bluetooth.BluetoothA2dp
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BluetoothA2dpBroadcastReceiver(
    private val onConnecting: () -> Unit = {},
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {},
    private val onPlaying: () -> Unit = {},
    private val onNotPlaying: () -> Unit = {},
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val connectionState = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, BluetoothA2dp.STATE_DISCONNECTED)
                when (connectionState) {
                    BluetoothA2dp.STATE_CONNECTING -> {
                        Log.d("BluetoothA2dp", "正在连接...")
                        onConnecting()
                    }

                    BluetoothA2dp.STATE_CONNECTED -> {
                        Log.d("BluetoothA2dp", "连接成功")
                        onConnected()
                    }

                    BluetoothA2dp.STATE_DISCONNECTED -> {
                        Log.d("BluetoothA2dp", "断开连接")
                        onDisconnected()
                    }

                    else -> {}
                }
            }

            BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED -> {
                val playingState = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, BluetoothA2dp.STATE_NOT_PLAYING)
                when (playingState) {
                    BluetoothA2dp.STATE_PLAYING -> {
                        Log.d("BluetoothA2dp", "正在播放")
                        onPlaying()
                    }

                    BluetoothA2dp.STATE_NOT_PLAYING -> {
                        Log.d("BluetoothA2dp", "播放停止")
                        onNotPlaying()
                    }

                    else -> {}
                }
            }

            else -> {}
        }
    }
}