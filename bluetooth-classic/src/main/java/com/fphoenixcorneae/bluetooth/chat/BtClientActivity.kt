package com.fphoenixcorneae.bluetooth.chat

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.fphoenixcorneae.bluetooth.BluetoothStatic
import com.fphoenixcorneae.bluetooth.ui.theme.AudioVideoDevelopDemoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BtClientActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudioVideoDevelopDemoTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BTClientScreen()
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BTClientScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var messageSend by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<String>() }
    var connectedBluetoothDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var connectThread by remember { mutableStateOf<ConnectThread?>(null) }
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
                connectThread = ConnectThread(
                    bluetoothDevice = BluetoothStatic.bluetoothDevice,
                    uuid = BluetoothStatic.BLUETOOTH_UUID,
                    onBluetoothListener = object : OnBluetoothListener {
                        override fun onReadyConnect() {
                            coroutineScope.launch(Dispatchers.Main) {
                                Toast.makeText(context, "开始连接", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onConnected(bluetoothDevice: BluetoothDevice?) {
                            connectedBluetoothDevice = bluetoothDevice
                        }

                        override fun onSendMsg(msg: String?) {
                            messages.add("我：$msg")
                        }

                        override fun onReceiveMsg(bluetoothDevice: BluetoothDevice?, msg: String?) {
                            messages.add("${bluetoothDevice?.name}：$msg")
                        }

                        override fun onFailed(errorMsg: String?) {
                            messages.add(errorMsg.orEmpty())
                        }
                    }
                )
                connectThread?.start()
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
                connectThread?.sendMsg(messageSend)
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
                Lifecycle.Event.ON_DESTROY -> connectThread?.cancel()
                Lifecycle.Event.ON_ANY -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer = observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}