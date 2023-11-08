package com.fphoenixcorneae.bluetooth.le

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.UUID

object LEBluetoothManager {

    private val TAG = javaClass.simpleName
    private val UUID_SERVICE: UUID = UUID.fromString("0000b81d-0000-1000-8000-00805f9b34fb")
    private val UUID_CLIENT: UUID = UUID.fromString("7db3e235-3608-41f3-a03c-955fcbd2ea4b")
    private val UUID_READ: UUID = UUID.fromString("36d4dc5c-814b-4097-a5a6-b93b39085928")
    private val UUID_WRITE: UUID = UUID.fromString("ed10665e-260f-4050-9734-702beaa5c6ae")

    /** 蓝牙适配器 */
    private var mBluetoothAdapter: BluetoothAdapter? = null

    /** 活动结果启动器 */
    private var mBluetoothPermissionsLauncher: ActivityResultLauncher<Array<String>>? = null
    private var mOpenLocationSourceSettingsLauncher: ActivityResultLauncher<Intent>? = null
    private var mOpenBluetoothLauncher: ActivityResultLauncher<Intent>? = null

    /** 延时处理器 */
    private val mHandler by lazy { Handler(Looper.getMainLooper()) }

    /** 扫描回调 */
    private lateinit var mOnScanCallback: ScanCallback
    private var isScanning = false

    /** 蓝牙通用属性协议 */
    private var mBluetoothGatt: BluetoothGatt? = null
    private var isConnected = false

    /** Gatt 服务器 */
    private var mBluetoothGattServer: BluetoothGattServer? = null

    private lateinit var mAdvertiseCallback: AdvertiseCallback

    /**
     * 获取 BluetoothAdapter 对象
     */
    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val bluetoothManager = ContextCompat.getSystemService(context, BluetoothManager::class.java)
        return bluetoothManager?.adapter.also { mBluetoothAdapter = it }
    }

    /**
     * 判断是否有蓝牙功能模块
     */
    fun hasBLEFeature(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    /**
     * 设备是否支持蓝牙
     * @return true 为支持
     */
    fun isSupportBluetooth(context: Context): Boolean {
        if (mBluetoothAdapter == null) {
            getBluetoothAdapter(context)
        }
        return mBluetoothAdapter != null
    }

    /**
     * 蓝牙是否打开
     * @return true 为打开
     */
    fun isBluetoothEnable(context: Context): Boolean {
        return isSupportBluetooth(context) && mBluetoothAdapter!!.isEnabled
    }

    /**
     * 开启蓝牙，需在 onResume() 之前调用
     * @param sync true  为同步提示开启蓝牙，打开蓝牙会弹出提示
     *             false 为异步自动开启蓝牙，蓝牙不会立刻就处于开启状态，并且打开蓝牙不会弹出提示，
     */
    @SuppressLint("MissingPermission")
    fun openBluetooth(
        activity: ComponentActivity,
        sync: Boolean = true,
        shouldShowRequestPermissionRationale: () -> Unit = {
            AlertDialog.Builder(activity)
                .setTitle("提示")
                .setMessage("蓝牙功能需要获取此设备的位置信息，请允许！")
                .setNegativeButton(
                    "取消"
                ) { dialog, which -> }
                .setPositiveButton(
                    "确定"
                ) { dialog, which ->
                    openLocationSourceSettings()
                }
                .setCancelable(false)
                .show()
        },
    ) {
        if (mBluetoothPermissionsLauncher == null) {
            mBluetoothPermissionsLauncher =
                activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions: Map<String, Boolean> ->
                    var allGranted = true
                    permissions.entries.forEach {
                        if (!it.value) {
                            allGranted = false
                            // 未同意授权
                            if (!activity.shouldShowRequestPermissionRationale(it.key)) {
                                // 用户拒绝权限并且系统不再弹出请求权限的弹窗
                                // 这时需要我们自己处理，比如自定义弹窗告知用户为何必须要申请这个权限
                                Log.d(
                                    "requestMultiplePermissions",
                                    "${it.key} not granted and should not show rationale"
                                )
                                shouldShowRequestPermissionRationale()
                            }
                        }
                    }
                    if (allGranted) {
                        // 在 Android 10 还需要开启 gps
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (!isGpsOpen(activity)) {
                                Toast.makeText(activity, "请您先开启gps, 否则蓝牙不可用", Toast.LENGTH_SHORT).show()
                                openLocationSourceSettings()
                            }
                        }
                    }
                }
        }
        if (mOpenLocationSourceSettingsLauncher == null) {
            mOpenLocationSourceSettingsLauncher =
                activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
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
                                openLocationSourceSettings()
                            }
                            .setCancelable(false)
                            .show()
                    } else {
                        if (hasBLEFeature(activity)) {
                            if (sync) {
                                mOpenBluetoothLauncher?.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            } else {
                                mBluetoothAdapter?.enable()
                            }
                        } else {
                            Toast.makeText(activity, "您的设备没有低功耗蓝牙驱动！", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        }
        if (mOpenBluetoothLauncher == null) {
            mOpenBluetoothLauncher =
                activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode == Activity.RESULT_CANCELED) {
                        Toast.makeText(activity, "请您不要拒绝开启蓝牙，否则应用无法运行", Toast.LENGTH_SHORT).show()
                        openBluetooth(activity)
                    }
                }
        }
        if (!isBluetoothEnable(activity)) {
            checkPermissions()
        }
    }

    /**
     * 打开位置源设置
     */
    private fun openLocationSourceSettings() {
        mOpenLocationSourceSettingsLauncher?.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    /**
     * 检查权限
     */
    fun checkPermissions() {
        val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        mBluetoothPermissionsLauncher?.launch(bluetoothPermissions)
    }

    /**
     * 检查GPS是否打开
     * @return
     */
    fun isGpsOpen(context: Context): Boolean {
        val locationManager = ContextCompat.getSystemService(context, LocationManager::class.java) ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    /**
     * 开始扫描低能耗蓝牙
     */
    @SuppressLint("MissingPermission", "ObsoleteSdkInt")
    fun startScanBLE(
        onScanResult: (callbackType: Int, result: ScanResult) -> Unit = { _, _ -> },
        onBatchScanResults: (results: MutableList<ScanResult>) -> Unit = {},
        onScanFailed: (errorCode: Int) -> Unit = {},
    ) {
        if (!::mOnScanCallback.isInitialized) {
            mOnScanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    // 不断回调，不建议做复杂的动作
                    result ?: return
                    result.device.name ?: return
                    onScanResult(callbackType, result)
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                    results ?: return
                    onBatchScanResults(results)
                }

                override fun onScanFailed(errorCode: Int) {
                    onScanFailed(errorCode)
                }
            }
        }
        if (isScanning) {
            return
        }
        isScanning = true
        // 扫描设置
        val scanSettings = ScanSettings.Builder()
            .apply {
                // 三种模式
                // - SCAN_MODE_LOW_POWER   低功耗模式，默认此模式，如果应用不在前台，则强制此模式
                // - SCAN_MODE_BALANCED    平衡模式，一定频率下返回结果
                // - SCAN_MODE_LOW_LATENCY 高功耗模式，建议应用在前台才使用此模式
                setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // 三种回调模式
                    // - CALLBACK_TYPE_ALL_MATCHED : 寻找符合过滤条件的广播，如果没有，则返回全部广播
                    // - CALLBACK_TYPE_FIRST_MATCH : 仅筛选匹配第一个广播包出发结果回调的
                    // - CALLBACK_TYPE_MATCH_LOST : 这个看英文文档吧，不满足第一个条件的时候，不好解释
                    setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                }
                // 判断手机蓝牙芯片是否支持批处理扫描
                if (mBluetoothAdapter?.isOffloadedFilteringSupported == true) {
                    setReportDelay(0)
                }
            }.build()
        // 开始扫描
        mBluetoothAdapter?.bluetoothLeScanner?.startScan(null, scanSettings, mOnScanCallback)
        // 扫描是很耗电的，所以，我们不能持续扫描
        mHandler.postDelayed({
            stopScanBLE()
        }, 3000)
    }

    /**
     * 停止扫描低能耗蓝牙
     */
    @SuppressLint("MissingPermission")
    fun stopScanBLE() {
        mBluetoothAdapter?.bluetoothLeScanner?.stopScan(mOnScanCallback)
        isScanning = false
    }

    /**
     * 客户端：连接蓝牙
     */
    @SuppressLint("MissingPermission")
    fun connectGatt(
        context: Context,
        bluetoothDevice: BluetoothDevice?,
        autoConnect: Boolean = false,
        onConnectionStateChanged: (isConnected: Boolean, device: BluetoothDevice?, state: Int) -> Unit = { _, _, _ -> },
        onServicesDiscovered: (gatt: BluetoothGatt?, status: Int) -> Unit = { _, _ -> },
        onCharacteristicRead: (gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, value: ByteArray?, status: Int) -> Unit = { _, _, _, _ -> },
        onCharacteristicWrite: (gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) -> Unit = { _, _, _ -> },
    ) {
        // 连接之前先断开连接
        disconnectGatt()
        val gattClientCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        isConnected = true
                        // 开始发现服务，有个小延时
                        mHandler.postDelayed({
                            gatt?.discoverServices()
                        }, 300)
                        onConnectionStateChanged(true, gatt?.device, status)
                        Log.d(TAG, "onConnectionStateChange: 与蓝牙 ${gatt?.device?.name} 连接成功！")
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "onConnectionStateChange: 与蓝牙 ${gatt?.device?.name} 断开连接！$status")
                        disconnectGatt()
                        onConnectionStateChanged(false, gatt?.device, status)
                    }

                    else -> {}
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "onServicesDiscovered: 已连接上 GATT 服务：$gatt,可以通信！")
                    onServicesDiscovered(gatt, status)
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int,
            ) {
                // 中心设备读数据时回调
                onCharacteristicRead(gatt, characteristic, characteristic?.value, status)
                Log.d(TAG, "onCharacteristicRead1: ${String(characteristic?.value ?: byteArrayOf())}")
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                // 中心设备读数据时回调
                onCharacteristicRead(gatt, characteristic, value, status)
                Log.d(TAG, "onCharacteristicRead2: ${String(value)}")
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int,
            ) {
                // 中心设备写数据时回调
                onCharacteristicWrite(gatt, characteristic, status)
                Log.d(TAG, "onCharacteristicWrite: ${String(characteristic!!.value)}")
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                Log.d(TAG, "onCharacteristicChanged: ${String(characteristic.value)}, ${String(value)}")
            }

            override fun onDescriptorRead(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
                value: ByteArray,
            ) {
                Log.d(TAG, "onDescriptorRead: ${String(descriptor.value)}, ${String(value)}")
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt?,
                descriptor: BluetoothGattDescriptor?,
                status: Int,
            ) {
                Log.d(TAG, "onDescriptorWrite: ${String(descriptor!!.value)}")
            }
        }
        mBluetoothGatt = bluetoothDevice?.connectGatt(
            /* context = */ context,
            /* autoConnect = */ autoConnect,
            /* callback = */ gattClientCallback,
        )
        Log.d(TAG, "connectGatt: 开始与蓝牙 ${bluetoothDevice?.name} 建立连接...$mBluetoothGatt")
    }

    /**
     * 客户端：主动断开连接
     */
    @SuppressLint("MissingPermission")
    fun disconnectGatt() {
        stopScanBLE()
        isConnected = false
        mBluetoothGatt?.disconnect()
        mBluetoothGatt?.close()
    }

    fun isConnected() = isConnected

    /**
     * 客户端：读数据
     */
    @SuppressLint("MissingPermission")
    fun readCharacteristic(
        context: Context,
        serviceUUID: UUID = UUID_SERVICE,
        readUUID: UUID = UUID_READ,
    ) {
        val bluetoothGattService = getBluetoothGattService(context, serviceUUID)
        bluetoothGattService?.let {
            val characteristic = it.getCharacteristic(readUUID)
            mBluetoothGatt?.readCharacteristic(characteristic)
        }
    }

    /**
     * 客户端：写数据
     */
    @SuppressLint("MissingPermission")
    fun writeCharacteristic(
        context: Context,
        value: ByteArray,
        serviceUUID: UUID = UUID_SERVICE,
        writeUUID: UUID = UUID_WRITE,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
    ) {
        val bluetoothGattService = getBluetoothGattService(context, serviceUUID)
        bluetoothGattService?.let {
            val characteristic = it.getCharacteristic(writeUUID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mBluetoothGatt?.writeCharacteristic(characteristic, value, writeType)
            } else {
                characteristic.value = value
                mBluetoothGatt?.writeCharacteristic(characteristic)
            }
        }
    }

    /**
     * 服务端：开启广播
     * 外围设备，会不断地发出广播，让中心设备知道，一旦连接上中心设备，就会停止发出广播
     * Android 5.0 之后，才能充当外围设备
     * @param name 设置本地蓝牙适配器的友好蓝牙名称。此名称对远程蓝牙设备可见。此名称不可过长，否则开启广播失败。
     */
    @SuppressLint("MissingPermission")
    fun startAdvertising(
        context: Context,
        name: String? = null,
        serviceUUID: UUID = UUID_SERVICE,
        clientUUID: UUID = UUID_CLIENT,
        readUUID: UUID = UUID_READ,
        writeUUID: UUID = UUID_WRITE,
        manufacturerId: Int = 0,
        manufacturerSpecificData: ByteArray = byteArrayOf(),
        onAdvertiseCallback: (isSuccessful: Boolean, msg: String) -> Unit = { _, _ -> },
        onConnectionStateChanged: (isConnected: Boolean, device: BluetoothDevice?, state: Int, newState: Int) -> Unit = { _, _, _, _ -> },
        onCharacteristicReadRequest: (device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) -> Unit = { _, _, _, _ -> },
        onCharacteristicWriteRequest: (device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) -> Unit = { _, _, _, _, _, _, _ -> },
    ) {
        val bluetoothManager = ContextCompat.getSystemService(context, BluetoothManager::class.java)
        mBluetoothAdapter = bluetoothManager?.adapter
        name?.let { mBluetoothAdapter?.name = it }

        /**
         * 设置本地GATT服务器，这需要设置其他设备可用的服务和特征可以读取和修改。
         * characteristic 是最小的逻辑单元
         * 一个 characteristic 包含一个单一 value 变量 和 0-n个用来描述 characteristic 变量的
         * Descriptor。与 service 相似，每个 characteristic 用 16bit或者32bit的uuid作为标识
         * 实际的通信中，也是通过 Characteristic 进行读写通信的
         */
        // 开启广播 Gatt service，这样才能通信，包含一个或多个 characteristic ，每个service 都有一个 uuid
        val bluetoothGattService = BluetoothGattService(serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        // 添加读+通知的 GattCharacteristic
        bluetoothGattService.addCharacteristic(
            BluetoothGattCharacteristic(
                /* uuid = */
                readUUID,
                /* properties = */
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                /* permissions = */
                BluetoothGattCharacteristic.PERMISSION_READ,
            )
        )
        // 添加写的 GattCharacteristic
        bluetoothGattService.addCharacteristic(
            BluetoothGattCharacteristic(
                /* uuid = */ writeUUID,
                /* properties = */ BluetoothGattCharacteristic.PROPERTY_WRITE,
                /* permissions = */ BluetoothGattCharacteristic.PERMISSION_WRITE,
            ).apply {
                // 添加 Descriptor 描述符
                addDescriptor(
                    BluetoothGattDescriptor(
                        /* uuid = */ clientUUID,
                        /* permissions = */ BluetoothGattDescriptor.PERMISSION_WRITE
                    )
                )
            }
        )
        // 打开 GATT 服务，方便客户端连接
        val bluetoothGattServerCallback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
                device ?: return
                val isSuccess = status == BluetoothGatt.GATT_SUCCESS
                val isConnected = newState == BluetoothProfile.STATE_CONNECTED
                if (isSuccess && isConnected) {
                    Log.d(TAG, "onConnectionStateChange: 连接到中心设备：${device.name}")
                    onConnectionStateChanged(true, device, status, newState)
                } else {
                    Log.d(TAG, "onConnectionStateChange: 与中心设备：${device.name} 断开连接！")
                    onConnectionStateChanged(false, device, status, newState)
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice?,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic?,
            ) {
                // 中心设备 read 时回调
                onCharacteristicReadRequest(device, requestId, offset, characteristic)
                Log.d(TAG, "onCharacteristicReadRequest: ${device?.name}, ${characteristic?.uuid}")
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?,
            ) {
                // 中心设备 write 时回调
                onCharacteristicWriteRequest(
                    device,
                    requestId,
                    characteristic,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value
                )
                Log.d(TAG, "onCharacteristicWriteRequest: ${device?.name}, ${characteristic?.uuid}")
            }

            override fun onDescriptorReadRequest(
                device: BluetoothDevice?,
                requestId: Int,
                offset: Int,
                descriptor: BluetoothGattDescriptor?,
            ) {
                Log.d(TAG, "onDescriptorReadRequest: ${device?.name}, ${descriptor?.uuid}")
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                descriptor: BluetoothGattDescriptor?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?,
            ) {
                Log.d(TAG, "onDescriptorWriteRequest: ${device?.name}, ${descriptor?.uuid}")
            }
        }
        mBluetoothGattServer = bluetoothManager?.openGattServer(context, bluetoothGattServerCallback)
        mBluetoothGattServer?.addService(bluetoothGattService)

        /**
         * GAP广播数据最长只能31个字节，包含两种： 广播数据和扫描回复
         * - 广播数据是必须的，外设需要不断发送广播，让中心设备知道
         * - 扫描回复是可选的，当中心设备扫描到才会扫描回复
         * 广播间隔越长，越省电
         */
        // 广播设置
        val advertiseSettings = AdvertiseSettings.Builder()
            // 低延时，高功率，不使用后台
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            // 高传输功率
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            // 可连接
            .setConnectable(true)
            // 广播时限。最多180000毫秒。值为0将禁用时间限制。（不设置则为无限广播时长）
            .setTimeout(0)
            .build()
        // 设置广播包，这个是每个外设必须要设置的
        val advertiseData = AdvertiseData.Builder()
            // 显示名字
            .setIncludeDeviceName(true)
            // 设置功率
            .setIncludeTxPowerLevel(true)
            // 设置服务的 uuid
            .addServiceUuid(ParcelUuid(serviceUUID))
            .build()
        // 扫描广播数据（可不写，客户端扫描才发送）
        val scanResponse = AdvertiseData.Builder()
            // 设置特定厂商Id和其数据
            .addManufacturerData(manufacturerId, manufacturerSpecificData)
            .build()

        /**
         * GATT 使用了 ATT 协议，ATT 把 service 和 characteristic 对应的数据保存在一个查询表中，
         * 依次查找每一项的索引
         * BLE 设备通过 Service 和 Characteristic 进行通信
         * 外设只能被一个中心设备连接，一旦连接，就会停止广播，断开又会重新发送
         * 但中心设备同时可以和多个外设连接
         * 他们之间需要双向通信的话，唯一的方式就是建立 GATT 连接
         * 外设作为 GATT(server)，它维持了 ATT 的查找表以及service 和 characteristic 的定义
         */
        if (!::mAdvertiseCallback.isInitialized) {
            mAdvertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.d(TAG, "onStartSuccess: 服务准备就绪，请搜索广播")
                    onAdvertiseCallback(true, "服务准备就绪，请搜索广播")
                }

                override fun onStartFailure(errorCode: Int) {
                    val errorMsg = if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE) {
                        "广播数据超过31个字节了 !"
                    } else {
                        "服务启动失败: $errorCode"
                    }
                    Log.d(TAG, "onStartFailure: $errorMsg")
                    onAdvertiseCallback(false, errorMsg)
                }
            }
        }
        // 开启广播，这个外设就开始发送广播了
        mBluetoothAdapter?.bluetoothLeAdvertiser?.startAdvertising(
            advertiseSettings,
            advertiseData,
            scanResponse,
            mAdvertiseCallback
        )
    }

    /**
     * 服务端：发送回复
     */
    @SuppressLint("MissingPermission")
    fun sendResponse(device: BluetoothDevice?, requestId: Int, offset: Int, value: ByteArray) {
        mBluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
    }

    /**
     * 服务端：停止广播
     */
    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (::mAdvertiseCallback.isInitialized) {
            mBluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(mAdvertiseCallback)
        }
        mBluetoothGattServer?.close()
    }

    /**
     * 获取 Gatt 服务
     */
    private fun getBluetoothGattService(context: Context, uuid: UUID): BluetoothGattService? {
        if (!isConnected) {
            Toast.makeText(context, "没有连接上蓝牙", Toast.LENGTH_SHORT).show()
            return null
        }
        val service = mBluetoothGatt?.getService(uuid)
        if (service == null) {
            Toast.makeText(context, "没有找到服务", Toast.LENGTH_SHORT).show()
        }
        return service
    }
}