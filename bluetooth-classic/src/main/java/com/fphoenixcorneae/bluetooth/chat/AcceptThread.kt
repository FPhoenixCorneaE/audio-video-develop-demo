package com.fphoenixcorneae.bluetooth.chat

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import java.util.UUID

/**
 * 连接蓝牙设备--服务端
 * @param uuid 需与 [ConnectThread] 中的 uuid 一致
 */
@SuppressLint("MissingPermission")
class AcceptThread(
    private val uuid: UUID,
    private val secure: Boolean = true,
    private val onBluetoothListener: OnBluetoothListener? = null,
) : Thread() {
    private val mBluetoothAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }
    private val mBluetoothServerSocket by lazy {
        onBluetoothListener?.onReadyConnect()
        if (secure) {
            mBluetoothAdapter?.listenUsingRfcommWithServiceRecord("ServerSocket", uuid)
        } else {
            mBluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord("ServerSocket", uuid)
        }
    }
    private var mClassicBluetoothMessageHandler: ClassicBluetoothMessageHandler? = null
    private var mCanLoop = true

    override fun run() {
        super.run()
        while (mCanLoop) {
            val bluetoothSocket = runCatching {
                // 监听是否有客户端接入
                mBluetoothServerSocket?.accept()
            }.onFailure {
                it.printStackTrace()
                mCanLoop = false
            }.getOrNull()
            // 获取客户端设备
            val remoteBluetoothDevice = bluetoothSocket?.remoteDevice
            remoteBluetoothDevice?.let {
                // 连接成功
                onBluetoothListener?.onConnected(it)
                mClassicBluetoothMessageHandler = ClassicBluetoothMessageHandler(
                    bluetoothSocket = bluetoothSocket,
                    onBluetoothListener = onBluetoothListener,
                )
                // 关闭服务端，只连接一个
                runCatching {
                    mBluetoothServerSocket?.close()
                }.onFailure {
                    it.printStackTrace()
                }
                mCanLoop = false
            }
        }
    }

    fun sendMsg(msg: String) {
        mClassicBluetoothMessageHandler?.sendMsg(msg)
    }

    fun cancel() {
        runCatching {
            mBluetoothServerSocket?.close()
        }.onFailure {
            it.printStackTrace()
        }
        mClassicBluetoothMessageHandler?.cancel()
    }
}