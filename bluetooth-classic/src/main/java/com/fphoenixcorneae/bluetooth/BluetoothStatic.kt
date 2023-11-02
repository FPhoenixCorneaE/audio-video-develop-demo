package com.fphoenixcorneae.bluetooth

import android.bluetooth.BluetoothDevice
import java.util.UUID

object BluetoothStatic {
    val BLUETOOTH_UUID = UUID.fromString("ed10665e-260f-4050-9734-702beaa5c6ae")
    var bluetoothDevice: BluetoothDevice? = null
}