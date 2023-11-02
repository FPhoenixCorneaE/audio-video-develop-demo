package com.fphoenixcorneae.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log


class BluetoothBondBroadcastReceiver(
    val onBondRequest: () -> Unit = {},
    val onBondFail: () -> Unit = {},
    val onBonding: (BluetoothDevice?) -> Unit = {},
    val onBondSuccess: (BluetoothDevice?) -> Unit = {},
) : BroadcastReceiver() {
    // 此处为你要连接的蓝牙设备的初始密钥，一般为1234或0000
    private val pin = "0000"

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context?, intent: Intent?) {
        val device: BluetoothDevice? = intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        when (intent?.action) {
            BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                runCatching {
                    onBondRequest()
                    // 1.确认配对
                    val setPairingConfirmation = device?.javaClass?.getDeclaredMethod(
                        "setPairingConfirmation",
                        Boolean::class.javaPrimitiveType,
                    )
                    setPairingConfirmation?.invoke(device, true)
                    // 2.终止有序广播
                    Log.d(
                        "order...",
                        "isOrderedBroadcast:$isOrderedBroadcast, isInitialStickyBroadcast:$isInitialStickyBroadcast"
                    )
                    // 如果没有将广播终止，则会出现一个一闪而过的配对框。
                    abortBroadcast()

                    // 3.调用setPin方法进行配对...
                    val removeBondMethod = device?.javaClass?.getDeclaredMethod("setPin", ByteArray::class.java)
                    removeBondMethod?.invoke(device, arrayOf<Any>(pin.toByte()))
                }.onFailure {
                    it.printStackTrace()
                }
            }

            BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                when (device?.bondState) {
                    BluetoothDevice.BOND_NONE -> {
                        Log.d("BluetoothBond", "取消配对")
                        onBondFail()
                    }

                    BluetoothDevice.BOND_BONDING -> {
                        Log.d("BluetoothBond", "配对中")
                        onBonding(device)
                    }

                    BluetoothDevice.BOND_BONDED -> {
                        Log.d("BluetoothBond", "配对成功")
                        onBondSuccess(device)
                    }
                }
            }
        }
    }
}