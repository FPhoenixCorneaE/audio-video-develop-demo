package com.fphoenixcorneae.bluetooth.chat

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import java.util.UUID

/**
 * 连接蓝牙设备--客户端
 * @param uuid 需与 [AcceptThread] 中的 uuid 一致
 */
@SuppressLint("MissingPermission")
class ConnectThread(
    private val bluetoothDevice: BluetoothDevice?,
    private val uuid: UUID,
    private val secure: Boolean = true,
    private val onBluetoothListener: OnBluetoothListener?,
) : Thread() {
    private val mBluetoothAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }
    private val mBluetoothSocket by lazy {
        onBluetoothListener?.onReadyConnect()
        // 监听该 uuid
        if (secure) {
            bluetoothDevice?.createRfcommSocketToServiceRecord(uuid)
        } else {
            bluetoothDevice?.createInsecureRfcommSocketToServiceRecord(uuid)
        }
    }
    private var mClassicBluetoothMessageHandler: ClassicBluetoothMessageHandler? = null

    override fun run() {
        super.run()
        // 先取消扫描
        mBluetoothAdapter?.cancelDiscovery()
        runCatching {
            // connect() 方法为阻塞调用
            mBluetoothSocket?.connect()
            // 连接成功，获取服务端设备
            val remoteBluetoothDevice = mBluetoothSocket?.remoteDevice
            onBluetoothListener?.onConnected(remoteBluetoothDevice)
            // 初始化蓝牙数据传输处理器
            mClassicBluetoothMessageHandler = ClassicBluetoothMessageHandler(
                bluetoothSocket = mBluetoothSocket,
                onBluetoothListener = onBluetoothListener
            )
        }.onFailure {
            it.printStackTrace()
            onBluetoothListener?.onFailed(it.message)
        }
    }

    fun sendMsg(msg: String) {
        mClassicBluetoothMessageHandler?.sendMsg(msg)
    }

    fun cancel() {
        mClassicBluetoothMessageHandler?.cancel()
    }
}