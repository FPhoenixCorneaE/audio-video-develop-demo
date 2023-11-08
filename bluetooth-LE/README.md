# Android BLE(低功耗)蓝牙开发

### **一、简介**

#### **1、基本概念**

##### 1.1 Generic Access Profile(GAP)

用来控制设备连接和广播，GAP 使你的设备被其他设备可见，并决定了你的设备是否可以或者怎样与合同设备进行交互。

##### 1.2. Generic Attribute Profile(GATT)

通过 BLE 连接，读写属性类数据的 Profile 通用规范，现在所有的 BLE 应用 Profile 都是基于 GATT 的。

##### 1.3. Attribute Protocol (ATT)

GATT 是基于 ATT Protocol 的，ATT 针对 BLE 设备做了专门的优化，具体就是在传输过程中使用尽量少的数据，每个属性都有一个唯一的 UUID，属性将以 characteristics and services 的形式传输。

##### 1.4. Characteristic

Characteristic 可以理解为一个数据类型，它包括一个 value 和 0~n 个对此 value 的描述（Descriptor）。

##### 1.5. Descriptor

对 Characteristic 的描述，例如范围、计量单位等。

##### 1.6. Service

Characteristic 的集合。例如一个 service 叫做“Heart Rate Monitor”，它可能包含多个 Characteristics，其中可能包含一个叫做“heart rate measurement”的 Characteristic。

##### 1.7. UUID

唯一标示符，每个 Service，Characteristic，Descriptor，都是由一个 UUID 定义。

`<br>`

#### **2、Android BLE Api**

##### 2.1. BluetoothManager

通过 `BluetoothManager` 来获取 `BluetoothAdapter`。

##### 2.2. BluetoothAdapter

代表了移动设备的本地的蓝牙适配器, 通过该蓝牙适配器可以对蓝牙进行基本操作，一个 Android 系统只有一个 `BluetoothAdapter`，通过 `BluetoothManager` 获取。

##### 2.3. BluetoothDevice

扫描后发现可连接的设备，获取已经连接的设备，通过它可以获取到 `BluetoothGatt`。

##### 2.4. BluetoothGatt

继承 `BluetoothProfile`，通过 `BluetoothGatt` 可以连接设备 `connect()`,发现服务 `discoverServices()`，并把相应地属性返回到 `BluetoothGattCallback`，可以看成蓝牙设备从连接到断开的生命周期。

##### 2.5. BluetoothGattService

服务，`Characteristic` 的集合。

##### 2.6. BluetoothGattCharacteristic

相当于一个数据类型，可以看成一个特征或能力，它包括一个 value 和 0~n 个 value的描述 `BluetoothGattDescriptor`。

##### 2.7. BluetoothGattDescriptor

描述符，对 `Characteristic` 的描述，包括范围、计量单位等。

##### 2.8. BluetoothProfile

一个通用的规范，按照这个规范来收发数据。

##### 2.9. BluetoothGattCallback

已经连接上设备，对设备的某些操作后返回的结果。

`<br>`

#### **3、总结**

&#8194;&#8194;**当我们扫描后发现多个设备 `BluetoothDevice`，每个设备下会有很多服务 `BluetoothGattService`，这些服务通过 `service_uuid`（唯一标识符）来区分，每个服务下又会有很多特征 `BluetoothGattCharacteristic`，这些特征通过uuid来区分的，它是手机与BLE终端设备交换数据的关键。而 `BluetoothGatt` 可以看成手机与 BLE 终端设备建立通信的一个管道，只有有了这个管道，才有了通信的前提。**

`<br>`

### **二、开发流程**

> 和经典蓝牙一样都需要以下几个步骤：
>
> 开启蓝牙 --》扫描蓝牙 --》绑定蓝牙 --》连接蓝牙 --》通信

#### **1. 开启蓝牙**

##### 1.1. 蓝牙权限申请

###### * 在 AndroidManifest 里面添加权限

```xml

<manifest>
    <!-- 使用蓝牙的权限 -->
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <!-- 扫描蓝牙设备或者操作蓝牙设置 -->
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <!-- 与已配对的蓝牙设备通信 -->
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <!-- 查找蓝牙设备 -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <!--模糊定位权限 -->
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <!--精准定位权限 -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
    <!-- 使当前设备可被其他蓝牙设备检测到 -->
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
    <!-- 指明您的应用需要使用低能耗蓝牙 -->
    <uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
</manifest>
```

###### * 动态检查权限

```kotlin
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
```

```kotlin
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
```

###### * Android 10 以后还需要开启 GPS

```kotlin
/**
 * 检查GPS是否打开
 * @return
 */
fun isGpsOpen(context: Context): Boolean {
    val locationManager = ContextCompat.getSystemService(context, LocationManager::class.java) ?: return false
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
}
```

```kotlin
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
```

```kotlin
/**
 * 打开位置源设置
 */
private fun openLocationSourceSettings() {
    mOpenLocationSourceSettingsLauncher?.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
}
```

##### 1.2. 获取 BluetoothAdapter 对象

```kotlin
/**
 * 获取 BluetoothAdapter 对象
 */
fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
    val bluetoothManager = ContextCompat.getSystemService(context, BluetoothManager::class.java)
    return bluetoothManager?.adapter.also { mBluetoothAdapter = it }
}
```

##### 1.3. 判断是否有蓝牙功能模块

```kotlin
/**
 * 判断是否有蓝牙功能模块
 */
fun hasBLEFeature(context: Context): Boolean {
    return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
}
```

##### 1.4. 设备是否支持蓝牙

```kotlin
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
```

##### 1.5. 蓝牙是否打开

```kotlin
/**
 * 蓝牙是否打开
 * @return true 为打开
 */
fun isBluetoothEnable(context: Context): Boolean {
    return isSupportBluetooth(context) && mBluetoothAdapter!!.isEnabled
}
```

##### 1.6. 开启蓝牙

```kotlin
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
```

#### **2. 扫描 LE 蓝牙**

##### 2.1. 在 5.0 之前，使用 `BluetoothAdapter` 的 `startLeScan()` 方法，它会返回当前设备和外设的广播数据。不过在 5.0 之后，通过 `BluetoothLeScanner` 的 `startScan()` 方法扫描，为了方便手机充当外围设备，统一使用 5.0 之后的方法。而扫描是耗时的，应该在扫描到想要的设备后就立即停止或者在规定时间内停止。

```kotlin
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
```

##### 2.2. `ScanCallback` 回调

```kotlin
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
```

##### 2.3. 停止扫描蓝牙设备

```kotlin
/**
 * 停止扫描低能耗蓝牙
 */
@SuppressLint("MissingPermission")
fun stopScanBLE() {
    mBluetoothAdapter?.bluetoothLeScanner?.stopScan(mOnScanCallback)
    isScanning = false
}
```

#### **3. 绑定蓝牙**

绑定蓝牙是在调用连接方法时绑定的。

#### **4. 连接蓝牙**

##### 4.1. 通过调用 `BluetoothDevice` 的 `connectGatt()` 方法开始连接蓝牙，返回 `BluetoothGatt` 对象。

```kotlin
mBluetoothGatt = bluetoothDevice?.connectGatt(
    /* context = */ context, /* autoConnect = */
    autoConnect, /* callback = */
    gattClientCallback,
)
```

##### 4.2. `BluetoothGattCallback` 回调

```kotlin
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
```

##### 4.3. 断开连接

```kotlin
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
```

#### **5. 通信**

| 中心设备(客户端)                               | 外围设备(服务端)                               |
| ---------------------------------------------- | ---------------------------------------------- |
| ![中心设备(客户端)](images/中心设备(客户端).png) | ![外围设备(服务端)](images/外围设备(服务端).png) |

##### 5.1. 外围设备(服务端)

> 要充当一个外围设备，需要完成一下步骤：
>
> 1. 构造广播设置，比如广播模式，发送功率等。
> 2. 配置广播数据(必须)，需要设置 service 的 uuid，或者显示名字等。
> 3. 配置扫描广播数据(可选)，这个是当中心设备在扫描时，能够显示出来的数据，通常我们会在这里配置一些厂商数据，服务数据等。
> 4. 添加 `BluetoothGattService` ，用来跟中心设备通信。

###### 5.1.1. 广播设置

```kotlin
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
```

可以看到，这里设置成可连接广播，且广播模式设置为 ADVERTISE_MODE_LOW_LATENCY 高功耗模式 ，它共有三种模式：

**ADVERTISE_MODE_LOW_POWER**   ：低功耗模式，默认此模式，如果应用不在前台，则强制此模式
**ADVERTISE_MODE_BALANCED**    ：平衡模式，一定频率下返回结果
**ADVERTISE_MODE_LOW_LATENCY** ：高功耗模式，建议应用在前台才使用此模式

传输功率共有四种：

**ADVERTISE_TX_POWER_ULTRA_LOW** ：极低传输(TX)功率
**ADVERTISE_TX_POWER_LOW**       ：低传输(TX)功率
**ADVERTISE_TX_POWER_MEDIUM**    ：中等传输(TX)功率
**ADVERTISE_TX_POWER_HIGH**      ：高传输(TX)功率

###### 5.1.2. 配置广播数据

```kotlin
// 设置广播包，这个是每个外设必须要设置的
val advertiseData = AdvertiseData.Builder()
    // 显示名字
    .setIncludeDeviceName(true)
    // 设置功率
    .setIncludeTxPowerLevel(true)
    // 设置服务的 uuid
    .addServiceUuid(ParcelUuid(serviceUUID))
    .build()
```

###### 5.1.3. 配置扫描广播数据

```kotlin
// 扫描广播数据（可不写，客户端扫描才发送）
val scanResponse = AdvertiseData.Builder()
    // 设置特定厂商Id和其数据
    .addManufacturerData(manufacturerId, manufacturerSpecificData)
    .build()
```

###### 5.1.4. BluetoothGattService

> 启动 Service 时，我们需要配置特征 Characteristic 和 描述符 Descriptor。
> Characteristic 是 Gatt 通信最小的逻辑单元，一个 characteristic 包含一个单一 value 变量 和 0-n 个用来描述 characteristic 变量的描述符 Descriptor。与 service 相似，每个 characteristic 用 16bit 或者 32bit 的 uuid 作为标识，实际的通信中，也是通过 Characteristic 进行读写通信的。
> Descriptor 的定义就是描述 GattCharacteristic 值已定义的属性，比如指定可读的属性，可接受范围等。

```kotlin
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
```

###### 5.1.5. 开始发送广播 `startAdvertising()`

```kotlin
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
```

###### 5.1.6. 停止广播 `stopAdvertising()`

```kotlin
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
```

###### 5.1.7. 发送回复 `sendResponse()`

```kotlin
/**
 * 服务端：发送回复
 */
@SuppressLint("MissingPermission")
fun sendResponse(device: BluetoothDevice?, requestId: Int, offset: Int, value: ByteArray) {
    mBluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
}
```

##### 5.2. 中心设备(客户端)

###### 5.2.1. 通过扫描到服务端发出的广播，使用 `BluetoothDevice` 的 `connectGatt()` 方法，来连接 GATT 服务。

###### 5.2.2. `BluetoothGattCallback` 回调

```kotlin
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
}
```

若是服务端的配置没有出错的话，`onConnectionStateChange()` 回调中的 `newState` 为 `BluetoothProfile.STATE_CONNECTED` 时，表示已经连接上了，此时，调用 `BluetoothGatt` 的 `discoverServices()` 方法，能在 `onServicesDiscovered()` 方法中收到 `state` 为 `BluetoothGatt.GATT_SUCCESS`，则证明此时 GATT 服务已经成功建立，可以进行通信了。

###### 5.2.3. 获取 Gatt 服务

```kotlin
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
```

###### 5.2.4. 读数据

* 中心设备调用 `readCharacteristic`

```kotlin
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
```

* 外围设备的 `BluetoothGattServerCallback` 会回调 `onCharacteristicReadRequest()`，提供回复中心设备的数据

```kotlin

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
```

* 中心设备的 `BluetoothGattCallback` 会回调 `onCharacteristicRead()`，在这个方法里拿到外围设备回复的数据

```kotlin
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
```

###### 5.2.5. 写数据

* 中心设备调用 `writeCharacteristic`

```kotlin
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
```

* 外围设备的 `BluetoothGattServerCallback` 会回调 `onCharacteristicWriteRequest()`，提供回复中心设备的数据

```kotlin
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
```

* 中心设备的 `BluetoothGattCallback` 会回调 `onCharacteristicWrite()`，在这个方法里拿到外围设备回复的数据

```kotlin
override fun onCharacteristicWrite(
    gatt: BluetoothGatt?,
    characteristic: BluetoothGattCharacteristic?,
    status: Int,
) {
    // 中心设备写数据时回调
    onCharacteristicWrite(gatt, characteristic, status)
    Log.d(TAG, "onCharacteristicWrite: ${String(characteristic!!.value)}")
}
```
