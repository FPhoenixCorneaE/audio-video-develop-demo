package com.fphoenixcorneae.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log


class BluetoothScanBroadcastReceiver(
    val onScanStarted: () -> Unit = {},
    val onScanning: (BluetoothDevice?) -> Unit = {},
    val onScanFinished: () -> Unit = {},
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                Log.d("BluetoothScan", "开始扫描...")
                onScanStarted()
            }

            BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                Log.d("BluetoothScan", "结束扫描...")
                onScanFinished()
            }

            BluetoothDevice.ACTION_FOUND -> {
                Log.d("BluetoothScan", "发现设备...")
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                onScanning(device)
            }
        }
    }
}