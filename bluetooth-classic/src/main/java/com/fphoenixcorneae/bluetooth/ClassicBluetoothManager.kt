package com.fphoenixcorneae.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.util.UUID


object ClassicBluetoothManager {

    private const val TAG = "BluetoothManager"
    private const val REQUEST_CODE_OPEN_BLUETOOTH = 123
    private const val REQUEST_CODE_PERMISSION = 456
    private const val REQUEST_CODE_OPEN_GPS = 789

    /** 蓝牙适配器 */
    private val mBluetoothAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }
    private var mBluetoothSocket: BluetoothSocket? = null

    /**
     * 设备是否支持蓝牙  true为支持
     * @return
     */
    fun isSupportBlue(): Boolean {
        return mBluetoothAdapter != null
    }

    /**
     * 蓝牙是否打开   true为打开
     * @return
     */
    fun isBlueEnable(): Boolean {
        return isSupportBlue() && mBluetoothAdapter.isEnabled
    }

    /**
     * 自动打开蓝牙（异步：蓝牙不会立刻就处于开启状态）
     * 这个方法打开蓝牙不会弹出提示
     */
    @SuppressLint("MissingPermission")
    fun openBlueAsync(activity: Activity) {
        if (!isBlueEnable() && checkPermissions(activity)) {
            mBluetoothAdapter.enable()
        }
    }

    /**
     * 自动打开蓝牙（同步）
     * 这个方法打开蓝牙会弹出提示
     * 需要在onActivityResult 方法中判断resultCode == RESULT_OK  true为成功
     */
    @SuppressLint("MissingPermission")
    fun openBlueSync(activity: Activity) {
        if (!isBlueEnable() && checkPermissions(activity)) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivityForResult(intent, REQUEST_CODE_OPEN_BLUETOOTH)
        }
    }

    /**
     * 检查权限
     */
    fun checkPermissions(activity: Activity): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        val permissionDeniedList: MutableList<String> = ArrayList()
        for (permission in permissions) {
            val permissionCheck = ContextCompat.checkSelfPermission(activity, permission)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                permissionDeniedList.add(permission)
            }
        }
        if (permissionDeniedList.isNotEmpty()) {
            val deniedPermissions = permissionDeniedList.toTypedArray()
            ActivityCompat.requestPermissions(activity, deniedPermissions, REQUEST_CODE_PERMISSION)
            return false
        }
        // 在 Android 10 还需要开启 gps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!isGpsOpen(activity)) {
                Toast.makeText(activity, "请您先开启gps, 否则蓝牙不可用", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                activity.startActivityForResult(intent, REQUEST_CODE_OPEN_GPS)
                return false
            }
        }
        return true
    }

    /**
     * 权限回调
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    fun onRequestPermissionsResult(
        activity: Activity,
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray,
    ) {
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (permissions.isNotEmpty()) {
                Toast.makeText(activity, "需要开启权限才能运行应用", Toast.LENGTH_SHORT).show()
                // 构建跳转意图
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", activity.packageName, null)
                intent.data = uri
                // 启动权限设置页面
                activity.startActivityForResult(intent, REQUEST_CODE_PERMISSION)
            } else {
                openBlueSync(activity)
            }
        }
    }


    fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_OPEN_BLUETOOTH && resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(activity, "请您不要拒绝开启蓝牙，否则应用无法运行", Toast.LENGTH_SHORT).show()
            openBlueSync(activity)
        } else if (requestCode == REQUEST_CODE_OPEN_GPS) {
            if (!isGpsOpen(activity)) {
                // 开启 GPS
                AlertDialog.Builder(activity)
                    .setTitle("提示")
                    .setMessage("当前手机扫描蓝牙需要打开定位功能。")
                    .setNegativeButton(
                        "取消"
                    ) { dialog, which -> }
                    .setPositiveButton(
                        "前往设置"
                    ) { dialog, which ->
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        activity.startActivityForResult(intent, REQUEST_CODE_OPEN_GPS)
                    }
                    .setCancelable(false)
                    .show()
            } else {
                openBlueSync(activity)
            }
        }
    }

    /**
     * 检查GPS是否打开
     * @return
     */
    fun isGpsOpen(context: Context): Boolean {
        val locationManager = ContextCompat.getSystemService(context, LocationManager::class.java) ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    fun registerBluetoothScanBroadcastReceiver(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        onScanStarted: () -> Unit = {},
        onScanning: (BluetoothDevice?) -> Unit,
        onScanFinished: () -> Unit = {},
    ) = apply {
        val receiver = BluetoothScanBroadcastReceiver(onScanStarted, onScanning, onScanFinished)
        val lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }.also {
                    context.registerReceiver(receiver, it)
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                context.unregisterReceiver(receiver)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                lifecycleOwner.lifecycle.removeObserver(this)
            }
        }

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    }

    /**
     * 扫描的方法 返回true 扫描成功
     * 通过接收广播获取扫描到的设备
     * @return
     */
    @SuppressLint("MissingPermission")
    fun scanBluetooth(): Boolean {
        if (!isBlueEnable()) {
            Log.e(TAG, "Bluetooth not enable!")
            return false
        }

        // 当前是否在扫描，如果是就取消当前的扫描，重新扫描
        cancelScanBluetooth()

        // 此方法是个异步操作，一般搜索12秒
        return mBluetoothAdapter.startDiscovery()
    }

    /**
     * 取消扫描蓝牙
     * @return  true 为取消成功
     */
    @SuppressLint("MissingPermission")
    fun cancelScanBluetooth(): Boolean {
        return if (isSupportBlue() && mBluetoothAdapter.isDiscovering) {
            mBluetoothAdapter.cancelDiscovery()
        } else {
            true
        }
    }

    /**
     * 获取已经配对的设备
     */
    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDevice>? {
        return mBluetoothAdapter.bondedDevices?.toList()
    }

    /**
     * 配对（配对成功与失败通过广播返回）
     * @param device
     */
    @SuppressLint("MissingPermission")
    fun bondBluetooth(device: BluetoothDevice?) {
        if (device == null) {
            Log.e(TAG, "bond device null")
            return
        }
        if (!isBlueEnable()) {
            Log.e(TAG, "Bluetooth not enable!")
            return
        }
        // 配对之前把扫描关闭
        cancelScanBluetooth()

        // 判断设备是否配对，没有配对再配，配对了就不需要配了
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            Log.d(TAG, "attempt to bond:" + device.name)
            runCatching {
                val createBondMethod = device.javaClass.getMethod("createBond")
                createBondMethod.invoke(device)
            }.onFailure {
                it.printStackTrace()
                Log.e(TAG, "attempt to bond fail!")
            }
        }
    }

    /**
     * 取消配对（取消配对成功与失败通过广播返回 也就是配对失败）
     * @param device
     */
    @SuppressLint("MissingPermission")
    fun cancelBondBluetooth(device: BluetoothDevice?) {
        if (device == null) {
            Log.d(TAG, "cancel bond device null")
            return
        }
        if (!isBlueEnable()) {
            Log.e(TAG, "Bluetooth not enable!")
            return
        }
        //判断设备是否配对，没有配对就不用取消了
        if (device.bondState != BluetoothDevice.BOND_NONE) {
            Log.d(TAG, "attempt to cancel bond:" + device.name)
            runCatching {
                val removeBondMethod = device.javaClass.getMethod("removeBond")
                removeBondMethod.invoke(device)
            }.onFailure {
                it.printStackTrace()
                Log.e(TAG, "attempt to cancel bond fail!")
            }
        }
    }

    fun registerBluetoothBondBroadcastReceiver(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        onBondRequest: () -> Unit = {},
        onBondFail: () -> Unit = {},
        onBonding: (BluetoothDevice?) -> Unit = {},
        onBondSuccess: (BluetoothDevice?) -> Unit = {},
    ) = apply {
        val receiver = BluetoothBondBroadcastReceiver(onBondRequest, onBondFail, onBonding, onBondSuccess)
        val lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
                    addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                }.also {
                    context.registerReceiver(receiver, it)
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                context.unregisterReceiver(receiver)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                lifecycleOwner.lifecycle.removeObserver(this)
            }
        }

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    }

    /**
     * 连接 （在配对之后调用）
     * 经典蓝牙连接相当于 socket 连接，是个非常耗时的操作，所以应该放到子线程中去完成。
     * @param device
     */
    @SuppressLint("MissingPermission")
    fun connectBluetooth(
        device: BluetoothDevice?,
        uuid: UUID = UUID.fromString("ed10665e-260f-4050-9734-702beaa5c6ae"),
        onConnectStart: () -> Unit = {},
        onConnectSuccess: (
            @ParameterName("bluetoothDevice") BluetoothDevice?,
            @ParameterName("bluetoothSocket") BluetoothSocket?,
        ) -> Unit = { _, _ -> },
        onConnectFailed: (
            @ParameterName("bluetoothDevice") BluetoothDevice?,
            @ParameterName("errorMsg") String?,
        ) -> Unit = { _, _ -> },
    ) {
        if (device == null) {
            Log.d(TAG, "bond device null")
            return
        }
        if (!isBlueEnable()) {
            Log.e(TAG, "Bluetooth not enable!")
            return
        }
        // 连接之前把扫描关闭
        cancelScanBluetooth()

        Log.d(TAG, "开始连接蓝牙：${device.name}, uuid: $uuid")
        onConnectStart()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.java)
                // 通过反射机制将 BluetoothSocket 的端口号为2时蓝牙连接成功
                mBluetoothSocket = method.invoke(device, 2) as? BluetoothSocket
                if (!isConnectBluetooth()) {
                    // connect() 方法为阻塞调用
                    mBluetoothSocket?.connect()
                }
                withContext(Dispatchers.Main) {
                    if (isConnectBluetooth()) {
                        Log.d(TAG, "蓝牙连接成功：${device.name}")
                        onConnectSuccess(device, mBluetoothSocket)
                    } else {
                        Log.d(TAG, "蓝牙连接失败：${device.name}")
                        onConnectFailed(device, "连接失败")
                    }
                }
            }.onFailure {
                Log.d(TAG, "蓝牙socket连接失败")
                withContext(Dispatchers.Main) {
                    onConnectFailed(device, it.message)
                }
                it.printStackTrace()
                runCatching {
                    mBluetoothSocket?.close()
                }.onFailure {
                    it.printStackTrace()
                    Log.d(TAG, "蓝牙socket关闭失败")
                }
            }
        }
    }

    /**
     * 输入mac地址进行自动配对
     * 前提是系统保存了该地址的对象
     * @param address
     */
    fun connectBluetooth(
        address: String?,
        uuid: UUID,
        onConnectStart: () -> Unit = {},
        onConnectSuccess: (
            @ParameterName("bluetoothDevice") BluetoothDevice?,
            @ParameterName("bluetoothSocket") BluetoothSocket?,
        ) -> Unit = { _, _ -> },
        onConnectFailed: (
            @ParameterName("bluetoothDevice") BluetoothDevice?,
            @ParameterName("errorMsg") String?,
        ) -> Unit = { _, _ -> },
    ) {
        if (!isBlueEnable()) {
            return
        }
        val device = mBluetoothAdapter.getRemoteDevice(address)
        connectBluetooth(device, uuid, onConnectStart, onConnectSuccess, onConnectFailed)
    }

    fun disconnectBluetooth() {
        if (isConnectBluetooth()) {
            runCatching {
                mBluetoothSocket?.close()
            }.onFailure {
                it.printStackTrace()
                Log.d(TAG, "蓝牙socket关闭失败")
            }
        }
        mBluetoothSocket = null
    }

    /**
     * 获取已连接的蓝牙设备
     */
    @SuppressLint("MissingPermission")
    fun getConnectedBluetoothDevice(): BluetoothDevice? = runCatching {
        // 得到已配对的蓝牙设备列表
        val bondedDevices = mBluetoothAdapter?.bondedDevices
        var connectedBluetoothDevice: BluetoothDevice? = null
        bondedDevices?.run {
            forEach {
                // 使用反射调用被隐藏的方法
                val isConnectedMethod = BluetoothDevice::class.java.getDeclaredMethod("isConnected")
                isConnectedMethod.isAccessible = true
                val isConnected = isConnectedMethod.invoke(it) as Boolean
                if (isConnected) {
                    connectedBluetoothDevice = it
                    return@run
                }
            }
        }
        connectedBluetoothDevice
    }.onFailure {
        it.printStackTrace()
    }.getOrNull()

    /**
     * 蓝牙是否连接
     * @return
     */
    fun isConnectBluetooth(): Boolean = mBluetoothSocket?.isConnected == true

    /**
     * 发送消息
     */
    fun sendMessage(message: String): Boolean {
        val result = if (isConnectBluetooth()) {
            runCatching {
                mBluetoothSocket?.outputStream?.use {
                    it.write(message.toByteArray())
                    true
                } ?: false
            }.onFailure {
                it.printStackTrace()
            }.getOrElse { false }
        } else false
        Log.d(TAG, if (result) "消息发送成功" else "消息发送失败")
        return result
    }

    suspend fun receiveMessage() = flow<String?> {
        runCatching {
            if (isConnectBluetooth()) {
                mBluetoothSocket?.let {
                    BufferedInputStream(it.inputStream).use { bis ->
                        withContext(Dispatchers.IO) {
                            val stringBuilder = StringBuilder()
                            val buf = ByteArray(1024)
                            var len: Int
                            while (bis.read().also { len = it } != -1) {
                                stringBuilder.append(String(buf, 0, len))
                            }
                            Log.d(TAG, "消息接收成功：$stringBuilder")
                            emit(stringBuilder.toString())
                        }
                    }
                }
            }
        }.onFailure {
            it.printStackTrace()
        }
    }
}
