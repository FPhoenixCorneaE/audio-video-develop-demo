package com.fphoenixcorneae.bluetooth

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fphoenixcorneae.bluetooth.chat.BtClientActivity
import com.fphoenixcorneae.bluetooth.chat.BtServerActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("MissingPermission")
@Composable
fun ClassicBluetoothScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val bondedDeviceStateList = remember { mutableStateListOf<BluetoothDevice?>() }
    val scanBluetoothResult = remember { mutableStateListOf<BluetoothDevice?>() }
    ClassicBluetoothManager.registerBluetoothScanBroadcastReceiver(
        context,
        lifecycleOwner,
        onScanning = {
            Log.d("TAG", "ClassicBluetoothScreen: name: ${it?.name} type: ${it?.bluetoothClass?.deviceClass}")
            if (!it?.name.isNullOrEmpty()
                && it?.bondState == BluetoothDevice.BOND_NONE
                && !scanBluetoothResult.contains(it)
            ) {
                scanBluetoothResult.add(it)
            }
        },
    ).registerBluetoothBondBroadcastReceiver(
        context,
        lifecycleOwner,
        onBondFail = {
            coroutineScope.launch {
                delay(1000)
                // 扫描
                scanBluetooth(bondedDeviceStateList, scanBluetoothResult)
            }
        },
        onBondSuccess = {
            // 扫描
            scanBluetooth(bondedDeviceStateList, scanBluetoothResult)
        }
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(0xFFDDDDDD))
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        // 扫描
                        scanBluetooth(bondedDeviceStateList, scanBluetoothResult)
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
                    Text(text = "扫描", fontSize = 16.sp)
                }
                Button(
                    onClick = {
                        if (BluetoothStatic.bluetoothDevice == null) {
                            Toast.makeText(context, "请先选择一个蓝牙设备", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        context.startActivity(Intent(context, BtClientActivity::class.java))
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
                    Text(text = "传统蓝牙客户端（流模式）", fontSize = 16.sp)
                }
                Button(
                    onClick = {
                        context.startActivity(Intent(context, BtServerActivity::class.java))
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
                    Text(text = "传统蓝牙服务端（流模式）", fontSize = 16.sp)
                }
                Text(
                    text = "已配对的设备",
                    color = Color.DarkGray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(bondedDeviceStateList.size) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .background(
                        Color.White,
                        if (bondedDeviceStateList.size == 1) {
                            RoundedCornerShape(8.dp)
                        } else {
                            when (it) {
                                0 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                bondedDeviceStateList.size - 1 -> RoundedCornerShape(
                                    bottomStart = 8.dp,
                                    bottomEnd = 8.dp
                                )

                                else -> RoundedCornerShape(0.dp)
                            }
                        },
                    )
                    .padding(horizontal = 8.dp)
                    .combinedClickable(onLongClick = {
                        AlertDialog
                            .Builder(context)
                            .setTitle("提示")
                            .setMessage("您要取消与蓝牙 ${bondedDeviceStateList[it]?.name} 配对嘛")
                            .setNegativeButton("取消") { dialog, which -> }
                            .setPositiveButton("确定") { dialog, which ->
                                ClassicBluetoothManager.cancelBondBluetooth(bondedDeviceStateList[it])
                            }
                            .setCancelable(false)
                            .show()
                    }) {
                        BluetoothStatic.bluetoothDevice = bondedDeviceStateList[it]
                        AlertDialog
                            .Builder(context)
                            .setTitle("提示")
                            .setMessage("连接蓝牙 ${bondedDeviceStateList[it]?.name}")
                            .setNegativeButton("取消") { dialog, which -> }
                            .setPositiveButton("确定") { dialog, which ->
                                ClassicBluetoothManager.connectBluetooth(
                                    bondedDeviceStateList[it],
                                    UUID.randomUUID(),
                                    onConnectStart = {
                                        Toast
                                            .makeText(context, "开始连接", Toast.LENGTH_SHORT)
                                            .show()
                                    },
                                    onConnectSuccess = { bluetoothDevice, bluetoothSocket ->
                                        Toast
                                            .makeText(context, "连接成功", Toast.LENGTH_SHORT)
                                            .show()
                                    },
                                    onConnectFailed = { bluetoothDevice, errorMsg ->
                                        Toast
                                            .makeText(context, "连接失败：$errorMsg", Toast.LENGTH_SHORT)
                                            .show()
                                    }
                                )
                            }
                            .setCancelable(false)
                            .show()
                    }
            ) {
                Image(
                    painter = when (bondedDeviceStateList[it]?.bluetoothClass?.deviceClass) {
                        BluetoothClass.Device.PHONE_SMART -> painterResource(id = R.drawable.ic_smartphone_24)
                        BluetoothClass.Device.COMPUTER_LAPTOP -> painterResource(id = R.drawable.ic_computer_24)
                        BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO -> painterResource(id = R.drawable.ic_headset_mic_24)
                        BluetoothClass.Device.WEARABLE_WRIST_WATCH -> painterResource(id = R.drawable.ic_watch_24)
                        else -> painterResource(id = R.drawable.ic_bluetooth_24)
                    }, contentDescription = null
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = bondedDeviceStateList[it]?.name.orEmpty(),
                        color = Color.Black,
                        fontSize = 16.sp,
                    )
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            Text(
                text = "可用设备",
                color = Color.DarkGray,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(scanBluetoothResult.size) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .background(
                        Color.White,
                        if (scanBluetoothResult.size == 1) {
                            RoundedCornerShape(8.dp)
                        } else {
                            when (it) {
                                0 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                scanBluetoothResult.size - 1 -> RoundedCornerShape(
                                    bottomStart = 8.dp,
                                    bottomEnd = 8.dp
                                )

                                else -> RoundedCornerShape(0.dp)
                            }
                        },
                    )
                    .padding(horizontal = 8.dp)
                    .clickable {
                        BluetoothStatic.bluetoothDevice = scanBluetoothResult[it]
                        ClassicBluetoothManager.bondBluetooth(scanBluetoothResult[it])
                    }
            ) {
                Image(
                    painter = when (scanBluetoothResult[it]?.bluetoothClass?.deviceClass) {
                        BluetoothClass.Device.PHONE_SMART -> painterResource(id = R.drawable.ic_smartphone_24)
                        BluetoothClass.Device.COMPUTER_LAPTOP -> painterResource(id = R.drawable.ic_computer_24)
                        BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO -> painterResource(id = R.drawable.ic_headset_mic_24)
                        BluetoothClass.Device.WEARABLE_WRIST_WATCH -> painterResource(id = R.drawable.ic_watch_24)
                        else -> painterResource(id = R.drawable.ic_bluetooth_24)
                    }, contentDescription = null
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = scanBluetoothResult[it]?.name.orEmpty(),
                        color = Color.Black,
                        fontSize = 16.sp,
                    )
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private fun scanBluetooth(
    bondedDeviceStateList: SnapshotStateList<BluetoothDevice?>,
    scanBluetoothResult: SnapshotStateList<BluetoothDevice?>,
) {
    ClassicBluetoothManager.getBondedDevices()?.let {
        bondedDeviceStateList.clear()
        bondedDeviceStateList.addAll(it)
    }
    scanBluetoothResult.clear()
    ClassicBluetoothManager.scanBluetooth()
}