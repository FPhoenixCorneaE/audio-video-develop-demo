package com.fphoenixcorneae.bluetooth.le

import android.annotation.SuppressLint
import android.util.Log
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
import androidx.compose.runtime.LaunchedEffect
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
fun LEBluetoothServerScreen() {
    val context = LocalContext.current
    var messageSend by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<String>() }
    var connectedBluetoothPrompt by remember { mutableStateOf("正在发出广播...") }
    var advertiseSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = Unit) {
        LEBluetoothManager.startAdvertising(
            context = context,
            onAdvertiseCallback = { isSuccessful, msg ->
                advertiseSuccess = isSuccessful
                connectedBluetoothPrompt = msg
            },
            onConnectionStateChanged = { isConnected, device, state, newState ->
                if (isConnected) {
                    messages.add("已连接上中心设备：${device?.name}")
                } else {
                    messages.add("与中心设备：${device?.name} 断开连接！")
                }
            },
            onCharacteristicReadRequest = { device, requestId, offset, characteristic ->
                messages.add("客户端：读取回复：$messageSend")
                LEBluetoothManager.sendResponse(device, requestId, offset, "客户端：读取回复：$messageSend".toByteArray())
            },
            onCharacteristicWriteRequest = { device, requestId, characteristic, preparedWrite, responseNeeded, offset, value ->
                LEBluetoothManager.sendResponse(device, requestId, offset, value ?: byteArrayOf())
                messages.add("客户端：写入：${String(value ?: byteArrayOf())}")
            }
        )
    }
    Column {
        Text(
            text = connectedBluetoothPrompt,
            color = if (advertiseSuccess) Color.Black else Color.Gray,
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp)
        )
        OutlinedTextField(
            value = messageSend,
            label = { Text(text = "请输入回复数据") },
            onValueChange = { messageSend = it },
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        )

        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(messages.size) {
                Log.d("messages", "LEBluetoothServerScreen: ${messages.toList()}")
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
                Lifecycle.Event.ON_DESTROY -> {
                    LEBluetoothManager.stopAdvertising()
                }

                Lifecycle.Event.ON_ANY -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer = observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}