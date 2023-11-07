package com.fphoenixcorneae.bluetooth.le

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun LEBluetoothClientScreen() {
    val context = LocalContext.current
    var messageSend by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<String>() }
    var connectedBluetoothDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    Column {
        connectedBluetoothDevice?.let {
            Text(
                text = "已连接上服务端：${it.name}",
                color = Color.Gray,
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp)
            )
        } ?: Text(
            text = "请先连接上服务端",
            color = Color.Gray,
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp)
        )

        Button(
            onClick = {
                // 连接 Gatt
                LEBluetoothManager.connectGatt(
                    context = context,
                    bluetoothDevice = BluetoothStatic.bluetoothDevice,
                    onConnectionStateChanged = { isConnected, device, state ->
                        if (isConnected) {
                            connectedBluetoothDevice = device
                            messages.add("与蓝牙 ${device?.name} 连接成功！")
                        } else {
                            connectedBluetoothDevice = null
                            messages.add("与蓝牙 ${device?.name} 断开连接！")
                        }
                    },
                    onServicesDiscovered = { gatt: BluetoothGatt?, status: Int ->
                        messages.add("已连接上 GATT 服务：$gatt,可以通信！")
                    },
                    onCharacteristicRead = { gatt, characteristic, value, status ->
                        messages.add(String(value ?: byteArrayOf()))
                    },
                    onCharacteristicWrite = { gatt, characteristic, status ->
                        messages.add("客户端：写入："+String(characteristic?.value ?: byteArrayOf()))
                    }
                )
            },
            contentPadding = PaddingValues(horizontal = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.LightGray,
                contentColor = Color.Black,
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            Text(text = "连接", fontSize = 16.sp)
        }

        OutlinedTextField(
            value = messageSend,
            label = { Text(text = "请输入发送数据") },
            onValueChange = { messageSend = it },
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        )
        Button(
            onClick = {
                LEBluetoothManager.readCharacteristic(context = context)
            },
            contentPadding = PaddingValues(horizontal = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.LightGray,
                contentColor = Color.Black,
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        ) {
            Text(text = "读取", fontSize = 16.sp)
        }
        Button(
            onClick = {
                LEBluetoothManager.writeCharacteristic(
                    context = context,
                    messageSend.toByteArray(),
                )
                messageSend = ""
            },
            contentPadding = PaddingValues(horizontal = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.LightGray,
                contentColor = Color.Black,
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        ) {
            Text(text = "发送", fontSize = 16.sp)
        }

        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(messages.size) {
                val message = messages[it]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 20.dp), contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = message,
                        color = if (message.startsWith("我")) Color.Black else Color.Gray,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(key1 = lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {}
                Lifecycle.Event.ON_START -> {}
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_PAUSE -> {}
                Lifecycle.Event.ON_STOP -> {}
                Lifecycle.Event.ON_DESTROY -> LEBluetoothManager.disconnectGatt()
                Lifecycle.Event.ON_ANY -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer = observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}