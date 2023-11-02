package com.fphoenixcorneae.bluetooth.chat

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedInputStream

/**
 * 经典蓝牙 BluetoothSocket 消息处理器
 */
class ClassicBluetoothMessageHandler(
    private val bluetoothSocket: BluetoothSocket?,
    private val onBluetoothListener: OnBluetoothListener?,
) {

    private val mReceiveMessageThread by lazy { ReceiveMessageThread(bluetoothSocket, onBluetoothListener) }
    private val mSendMessageImpl by lazy { SendMessageImpl(bluetoothSocket, onBluetoothListener) }

    init {
        mReceiveMessageThread.start()
    }

    fun sendMsg(msg: String) {
        mSendMessageImpl.sendMsg(msg)
    }

    fun cancel() {
        mReceiveMessageThread.cancel()
        mSendMessageImpl.cancel()
    }

    /**
     * 接收消息
     */
    internal class ReceiveMessageThread(
        private val bluetoothSocket: BluetoothSocket?,
        private val onBluetoothListener: OnBluetoothListener?,
    ) : Thread() {
        private val mBufferedInputStream = BufferedInputStream(bluetoothSocket?.inputStream)
        private val mByteArray = ByteArray(1024)
        private var mEnable = true

        override fun run() {
            super.run()
            var size = 0
            while (mEnable) {
                runCatching {
                    size = mBufferedInputStream.read(mByteArray)
                }.onFailure {
                    it.printStackTrace()
                    mEnable = false
                    onBluetoothListener?.onFailed(it.message)
                }
                if (size > 0) {
                    onBluetoothListener?.onReceiveMsg(bluetoothSocket?.remoteDevice, String(mByteArray, 0, size))
                } else {
                    onBluetoothListener?.onFailed("断开连接")
                    mEnable = false
                }
            }
        }

        fun cancel() {
            mEnable = false
            runCatching {
                bluetoothSocket?.close()
            }.onFailure {
                it.printStackTrace()
            }
            runCatching {
                mBufferedInputStream.close()
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    /**
     * 发送消息
     */
    internal class SendMessageImpl(
        private val bluetoothSocket: BluetoothSocket?,
        private val onBluetoothListener: OnBluetoothListener?,
    ) {
        private val mOutputStream = bluetoothSocket?.outputStream

        fun sendMsg(msg: String) {
            CoroutineScope(Dispatchers.IO).launch {
                var errorMsg: String? = null
                val result = runCatching {
                    mOutputStream?.write(msg.toByteArray())
                    mOutputStream?.flush()
                    true
                }.onFailure {
                    it.printStackTrace()
                    errorMsg = it.message
                }.getOrElse { false }
                if (result) {
                    onBluetoothListener?.onSendMsg(msg)
                } else {
                    onBluetoothListener?.onFailed(errorMsg)
                }
            }
        }

        fun cancel() {
            runCatching {
                bluetoothSocket?.close()
            }.onFailure {
                it.printStackTrace()
            }
            runCatching {
                mOutputStream?.close()
            }.onFailure {
                it.printStackTrace()
            }
        }
    }
}

interface OnBluetoothListener {
    fun onReadyConnect() {}
    fun onConnected(bluetoothDevice: BluetoothDevice?) {}
    fun onSendMsg(msg: String?) {}
    fun onReceiveMsg(bluetoothDevice: BluetoothDevice?, msg: String?) {}
    fun onFailed(errorMsg: String?) {}
}