package com.zclei.spinecaremom.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.roundToInt

data class SpineBraceDevice(
    val name: String,
    val address: String,
    val rssi: Int,
)

data class SpineBraceTelemetry(
    val batteryPercent: Int,
    val temperatureRaw: Int,
    val temperatureDiffC: Float,
    val accelerationRaw: Int,
    val accelerationG: Float,
    val pressure1: Int,
    val pressure2: Int,
    val pressure3: Int,
    val pressure4: Int,
    val worn: Boolean,
    val receivedAtMs: Long = System.currentTimeMillis(),
) {
    val batteryText: String = if (batteryPercent in 0..100) "$batteryPercent%" else "--"
    val temperatureText: String = "%.1f°C".format(temperatureDiffC)
    val accelerationText: String = "%.3fg".format(accelerationG)
    val wornText: String = if (worn) "已穿戴" else "未穿戴"
}

data class SpineBraceVersion(
    val deviceType: Int,
    val version: Int,
) {
    val displayText: String = "设备位 %02X · 版本 %02X".format(deviceType, version)
}

data class SpineBraceHistoryHeader(
    val head: Int,
    val count: Int,
    val deviceTimeText: String,
    val nextSaveSeconds: Int,
    val deviceTime: LocalDateTime?,
)

data class SpineBraceWearPoint(
    val recordedAt: LocalDateTime,
    val worn: Boolean,
)

data class SpineBraceHistorySnapshot(
    val header: SpineBraceHistoryHeader?,
    val packetCount: Int,
    val totalBits: Int,
    val wornBits: Int,
    val points: List<SpineBraceWearPoint>,
    val complete: Boolean,
) {
    val summaryText: String =
        if (totalBits == 0) {
            "等待设备返回月度穿戴数据"
        } else {
            val rate = (wornBits * 100f / totalBits).roundToInt()
            "已收 $packetCount 包 · $totalBits 个10分钟点 · 穿戴率 $rate%"
        }
}

interface SpineBraceBluetoothCallback {
    fun onStatus(message: String)
    fun onDevicesChanged(devices: List<SpineBraceDevice>)
    fun onConnected(device: SpineBraceDevice)
    fun onDisconnected()
    fun onTelemetry(telemetry: SpineBraceTelemetry)
    fun onVersion(version: SpineBraceVersion)
    fun onHistory(snapshot: SpineBraceHistorySnapshot)
}

class SpineBraceBluetoothManager(
    context: Context,
    private val callback: SpineBraceBluetoothCallback,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scanner get() = adapter?.bluetoothLeScanner
    private val scanSettings =
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()

    private val devices = linkedMapOf<String, SpineBraceDevice>()
    private val incomingBuffer = ArrayDeque<Byte>()
    private val pendingNotificationDescriptors = ArrayDeque<BluetoothGattDescriptor>()
    private val historyPayloads = sortedMapOf<Int, ByteArray>()
    private var historyHeader: SpineBraceHistoryHeader? = null
    private var gatt: BluetoothGatt? = null
    private var connectingDevice: SpineBraceDevice? = null
    private var connectedDevice: SpineBraceDevice? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var scanSession = 0
    private var scanning = false
    private var bleSetupInProgress = false

    private val scanCallback =
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val name = result.extractDeviceName() ?: return
                if (!isSpineBraceDeviceName(name)) {
                    return
                }
                val item = SpineBraceDevice(name = name, address = device.address, rssi = result.rssi)
                addOrUpdateDevice(item)
            }

            override fun onScanFailed(errorCode: Int) {
                scanning = false
                notifyStatus("扫描失败：$errorCode")
            }
        }

    @SuppressLint("MissingPermission")
    fun startScan(timeoutMs: Long = SCAN_TIMEOUT_MS) {
        if (adapter == null || adapter?.isEnabled != true) {
            notifyStatus("蓝牙未开启")
            return
        }
        if (scanning) {
            stopScan()
        }
        devices.clear()
        notifyDevicesChanged()
        scanning = true
        val activeScanSession = ++scanSession
        scanner?.startScan(null, scanSettings, scanCallback)
        notifyStatus("正在扫描 DR-Z-T0 脊柱侧弯设备...")
        mainHandler.postDelayed(
            {
                if (scanning && activeScanSession == scanSession) {
                    stopScan()
                    notifyStatus("扫描完成，发现 ${devices.size} 个 DR-Z-T0 设备")
                }
            },
            timeoutMs,
        )
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) {
            return
        }
        runCatching { scanner?.stopScan(scanCallback) }
        scanning = false
    }

    @SuppressLint("MissingPermission")
    fun connect(device: SpineBraceDevice) {
        stopScan()
        disconnectInternal(notify = false)
        val remoteDevice =
            try {
                adapter?.getRemoteDevice(device.address)
            } catch (_: IllegalArgumentException) {
                null
            }
        if (remoteDevice == null) {
            notifyStatus("设备地址无效")
            return
        }
        connectingDevice = device
        notifyStatus("正在连接 ${device.name}...")
        gatt = remoteDevice.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        disconnectInternal(notify = true)
    }

    @SuppressLint("MissingPermission")
    fun close() {
        stopScan()
        disconnectInternal(notify = false)
    }

    fun requestHistory(reportStatus: Boolean = true) {
        historyHeader = null
        historyPayloads.clear()
        writeCommand(0x04, successText = "已发送读取一个月穿戴数据指令".takeIf { reportStatus })
    }

    fun zeroTemperature() {
        writeCommand(0x05, successText = "已发送温度差清零指令")
    }

    fun requestVersion() {
        writeCommand(0x06, successText = "已发送获取版本号指令")
    }

    fun clearMonthlyDataWithCurrentTime(reportStatus: Boolean = true) {
        val now = LocalDateTime.now()
        val payload =
            byteArrayOf(
                (now.year % 100).toByte(),
                now.monthValue.toByte(),
                now.dayOfMonth.toByte(),
                now.hour.toByte(),
                now.minute.toByte(),
                now.second.toByte(),
            )
        historyHeader = null
        historyPayloads.clear()
        writeCommand(0x01, payload, successText = "已发送清空并重新开始保存指令".takeIf { reportStatus })
    }

    fun writeUniqueCodeLastSix(code: Int) {
        val value = code.coerceIn(0, 999999)
        val payload =
            byteArrayOf(
                ((value shr 24) and 0xFF).toByte(),
                ((value shr 16) and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte(),
            )
        writeCommand(0xF2, payload, successText = "已发送设备唯一码后6位写入指令，设备将重启")
    }

    @SuppressLint("MissingPermission")
    private fun disconnectInternal(notify: Boolean) {
        val hadConnection = connectedDevice != null || connectingDevice != null || gatt != null
        writeCharacteristic = null
        pendingNotificationDescriptors.clear()
        bleSetupInProgress = false
        incomingBuffer.clear()
        connectingDevice = null
        connectedDevice = null
        val targetGatt = gatt
        gatt = null
        if (targetGatt != null) {
            runCatching {
                targetGatt.disconnect()
                targetGatt.close()
            }
        }
        if (notify && hadConnection) {
            notifyDisconnected()
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeCommand(command: Int, payload: ByteArray = byteArrayOf(), successText: String?) {
        val targetGatt = gatt
        val targetCharacteristic = writeCharacteristic
        if (targetGatt == null || targetCharacteristic == null) {
            notifyStatus("请先连接蓝牙设备")
            return
        }
        val packet = buildCommandPacket(command, payload)
        val ok =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                targetGatt.writeCharacteristic(targetCharacteristic, packet, targetCharacteristic.writeType) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                targetCharacteristic.value = packet
                @Suppress("DEPRECATION")
                targetGatt.writeCharacteristic(targetCharacteristic)
            }
        if (ok) {
            successText?.let(::notifyStatus)
        } else if (successText != null) {
            notifyStatus("蓝牙写入失败，请保持设备连接后重试")
        }
    }

    private val gattCallback =
        object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (this@SpineBraceBluetoothManager.gatt !== gatt) {
                    runCatching { gatt.close() }
                    return
                }
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            handleDisconnected(gatt, status)
                            return
                        }
                        runCatching { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
                        notifyStatus("已连接，正在发现服务...")
                        mainHandler.postDelayed(
                            {
                                if (this@SpineBraceBluetoothManager.gatt === gatt) {
                                    gatt.discoverServices()
                                }
                            },
                            BLE_SERVICE_DISCOVERY_DELAY_MS,
                        )
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> handleDisconnected(gatt, status)
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (this@SpineBraceBluetoothManager.gatt !== gatt) {
                    return
                }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    notifyStatus("服务发现失败：$status")
                    disconnectInternal(notify = true)
                    return
                }
                writeCharacteristic = null
                pendingNotificationDescriptors.clear()
                bleSetupInProgress = true
                val notifyCandidates = mutableListOf<BluetoothGattCharacteristic>()
                val writeCandidates = mutableListOf<BluetoothGattCharacteristic>()
                gatt.services.orEmpty().forEach { service ->
                    service.characteristics.orEmpty().forEach { characteristic ->
                        val props = characteristic.properties
                        if (props.hasAny(BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) {
                            characteristic.writeType =
                                if (props.hasAny(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) {
                                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                                } else {
                                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                                }
                            writeCandidates += characteristic
                        }
                        if (props.hasAny(BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PROPERTY_INDICATE)) {
                            notifyCandidates += characteristic
                        }
                    }
                }
                writeCharacteristic = writeCandidates.maxByOrNull(::writeCharacteristicScore)
                val orderedNotifyCandidates =
                    notifyCandidates
                        .filter(::isBraceNotifyCharacteristic)
                        .ifEmpty { notifyCandidates.filterNot(::isSystemServiceChangedCharacteristic) }
                        .sortedByDescending { characteristic -> characteristic.uuid.toString().contains("ffe4", ignoreCase = true) }
                var notifyCount = 0
                orderedNotifyCandidates.forEach { characteristic ->
                    if (queueNotification(gatt, characteristic)) {
                        notifyCount += 1
                    }
                }
                val item = connectingDevice ?: connectedDevice
                if (writeCharacteristic == null) {
                    notifyStatus("未找到可写入的蓝牙通道")
                }
                if (item != null) {
                    connectedDevice = item
                    connectingDevice = null
                }
                notifyStatus("蓝牙已就绪，通知通道 $notifyCount 个")
                continueBleSetup(gatt)
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (this@SpineBraceBluetoothManager.gatt !== gatt) {
                    return
                }
                @Suppress("DEPRECATION")
                handleIncomingBytes(characteristic.value)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                if (this@SpineBraceBluetoothManager.gatt !== gatt) {
                    return
                }
                handleIncomingBytes(value)
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (this@SpineBraceBluetoothManager.gatt !== gatt) {
                    return
                }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "descriptor write status=$status descriptor=${descriptor.uuid}")
                }
                continueBleSetup(gatt)
            }
        }

    @SuppressLint("MissingPermission")
    private fun handleDisconnected(gatt: BluetoothGatt, status: Int) {
        Log.w(TAG, "BLE disconnected status=$status")
        if (this.gatt === gatt) {
            this.gatt = null
        }
        runCatching { gatt.close() }
        connectingDevice = null
        connectedDevice = null
        writeCharacteristic = null
        pendingNotificationDescriptors.clear()
        bleSetupInProgress = false
        notifyStatus("BLE连接断开 status=$status")
        notifyDisconnected()
    }

    @SuppressLint("MissingPermission")
    private fun queueNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        val enabled = gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_UUID)
        if (descriptor != null) {
            @Suppress("DEPRECATION")
            descriptor.value =
                if (characteristic.properties.hasAny(BluetoothGattCharacteristic.PROPERTY_INDICATE)) {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                }
            pendingNotificationDescriptors.add(descriptor)
        }
        return enabled
    }

    @SuppressLint("MissingPermission")
    private fun continueBleSetup(gatt: BluetoothGatt) {
        val descriptor = pendingNotificationDescriptors.removeFirstOrNull()
        if (descriptor == null) {
            bleSetupInProgress = false
            connectedDevice?.let(::notifyConnected)
            notifyStatus("蓝牙已就绪")
            return
        }
        if (!gatt.writeDescriptor(descriptor)) {
            continueBleSetup(gatt)
        }
    }

    private fun handleIncomingBytes(value: ByteArray?) {
        if (value == null || value.isEmpty()) {
            return
        }
        value.forEach(incomingBuffer::addLast)
        parseBufferedPackets()
    }

    private fun parseBufferedPackets() {
        while (incomingBuffer.size >= MIN_PACKET_SIZE) {
            if ((incomingBuffer.first().toInt() and 0xFF) != 0xD5) {
                incomingBuffer.removeFirst()
                continue
            }
            val snapshot = incomingBuffer.toList()
            if (snapshot.size < 3) {
                return
            }
            if ((snapshot[1].toInt() and 0xFF) != 0x5D) {
                incomingBuffer.removeFirst()
                continue
            }
            val command = snapshot[2].toInt() and 0xFF
            val packetLength =
                when (command) {
                    0x03 -> TELEMETRY_PACKET_SIZE
                    0x04 -> historyPacketLength(snapshot)
                    0x06 -> VERSION_PACKET_SIZE
                    else -> {
                        incomingBuffer.removeFirst()
                        continue
                    }
                }
            if (packetLength == null || incomingBuffer.size < packetLength) {
                return
            }
            val packet = incomingBuffer.take(packetLength).toByteArray()
            val crcOk = hasValidCrc(packet)
            if (!crcOk) {
                Log.w(TAG, "drop packet with invalid crc command=${command.toString(16)} bytes=${packet.toHexString()}")
                incomingBuffer.removeFirst()
                continue
            }
            repeat(packetLength) { incomingBuffer.removeFirst() }
            when (command) {
                0x03 -> parseTelemetry(packet)?.let(::notifyTelemetry)
                0x04 -> parseHistoryPacket(packet)
                0x06 -> parseVersion(packet)?.let(::notifyVersion)
            }
        }
        while (incomingBuffer.size > MAX_BUFFER_SIZE) {
            incomingBuffer.removeFirst()
        }
    }

    private fun historyPacketLength(snapshot: List<Byte>): Int? {
        if (snapshot.size < HISTORY_PACKET_SIZE) {
            return null
        }
        val first = snapshot.take(HISTORY_PACKET_SIZE).toByteArray()
        if (hasValidCrc(first)) {
            return HISTORY_PACKET_SIZE
        }
        if (snapshot.size >= HISTORY_ALT_PACKET_SIZE) {
            val alt = snapshot.take(HISTORY_ALT_PACKET_SIZE).toByteArray()
            if (hasValidCrc(alt)) {
                return HISTORY_ALT_PACKET_SIZE
            }
        }
        return HISTORY_PACKET_SIZE
    }

    private fun parseTelemetry(packet: ByteArray): SpineBraceTelemetry? {
        if (packet.size < TELEMETRY_PACKET_SIZE) {
            return null
        }
        val temperatureRaw = readUInt16LittleEndian(packet, 4)
        val accelerationRaw = readUInt16LittleEndian(packet, 6)
        return SpineBraceTelemetry(
            batteryPercent = packet.u8(3),
            temperatureRaw = temperatureRaw,
            temperatureDiffC = temperatureRaw / 30f,
            accelerationRaw = accelerationRaw,
            accelerationG = accelerationRaw / 1000f,
            pressure1 = packet.u8(8),
            pressure2 = packet.u8(9),
            pressure3 = packet.u8(10),
            pressure4 = packet.u8(11),
            worn = packet.u8(12) == 1,
        )
    }

    private fun parseVersion(packet: ByteArray): SpineBraceVersion? {
        if (packet.size < VERSION_PACKET_SIZE) {
            return null
        }
        return SpineBraceVersion(deviceType = packet.u8(3), version = packet.u8(4))
    }

    private fun parseHistoryPacket(packet: ByteArray) {
        val data = packet.copyOfRange(3, packet.size - 2)
        if (data.isEmpty()) {
            return
        }
        val sequence = data[0].toInt() and 0xFF
        if (sequence == 0) {
            historyHeader = parseHistoryHeader(data)
        } else {
            historyPayloads[sequence] = data.copyOfRange(1, data.size)
        }
        notifyHistory(historySnapshot())
    }

    private fun parseHistoryHeader(data: ByteArray): SpineBraceHistoryHeader {
        val head = if (data.size >= 2) readUInt16BigEndian(data, 0) else 0
        val count = if (data.size >= 4) readUInt16BigEndian(data, 2) else 0
        var deviceTime: LocalDateTime? = null
        val timeText =
            if (data.size >= 11) {
                val year = data.u8(5)
                val month = data.u8(6)
                val day = data.u8(7)
                val hour = data.u8(8)
                val minute = data.u8(9)
                val second = data.u8(10)
                if (listOf(year, month, day, hour, minute, second).all { it == 0xFF }) {
                    "设备未同步时间"
                } else {
                    deviceTime =
                        runCatching {
                            LocalDateTime.of(2000 + year, month, day, hour, minute, second)
                        }.getOrNull()
                    deviceTime?.let {
                        "%04d-%02d-%02d %02d:%02d:%02d".format(it.year, it.monthValue, it.dayOfMonth, it.hour, it.minute, it.second)
                    } ?: "设备时间异常"
                }
            } else {
                "--"
            }
        val nextSeconds = if (data.size >= 13) readUInt16LittleEndian(data, 11) else 0
        return SpineBraceHistoryHeader(head = head, count = count, deviceTimeText = timeText, nextSaveSeconds = nextSeconds, deviceTime = deviceTime)
    }

    private fun historySnapshot(): SpineBraceHistorySnapshot {
        val bits =
            historyPayloads.values.flatMap { payload ->
                payload.flatMap { byte ->
                    (0..7).map { bit -> ((byte.toInt() and 0xFF) shr bit) and 1 == 1 }
                }
            }
        val expectedCount = historyHeader?.count?.takeIf { it > 0 }
        val visibleBits = if (expectedCount != null && expectedCount <= bits.size) bits.takeLast(expectedCount) else bits
        val header = historyHeader
        val nextSeconds = header?.nextSaveSeconds?.coerceIn(0, 600) ?: 0
        val latestPointTime =
            (header?.deviceTime ?: LocalDateTime.now())
                .minusSeconds((600 - nextSeconds).toLong())
                .truncatedTo(ChronoUnit.MINUTES)
        val points =
            visibleBits.mapIndexed { index, worn ->
                val minutesAgo = (visibleBits.size - 1 - index) * 10L
                SpineBraceWearPoint(recordedAt = latestPointTime.minusMinutes(minutesAgo), worn = worn)
            }
        val complete =
            header != null &&
                when {
                    expectedCount != null -> bits.size >= expectedCount
                    header.count == 0 && historyPayloads.isEmpty() -> true
                    else -> historyPayloads.size >= EXPECTED_HISTORY_DATA_PACKETS
                }
        return SpineBraceHistorySnapshot(
            header = header,
            packetCount = historyPayloads.size + if (historyHeader != null) 1 else 0,
            totalBits = visibleBits.size,
            wornBits = visibleBits.count { it },
            points = points,
            complete = complete,
        )
    }

    private fun buildCommandPacket(command: Int, payload: ByteArray = byteArrayOf()): ByteArray {
        val body = ByteArray(3 + payload.size)
        body[0] = 0xC5.toByte()
        body[1] = 0x5C.toByte()
        body[2] = command.toByte()
        payload.copyInto(body, destinationOffset = 3)
        val crc = crc16CcittFalse(body)
        return body + byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())
    }

    private fun hasValidCrc(packet: ByteArray): Boolean {
        if (packet.size < 5) {
            return false
        }
        val body = packet.copyOfRange(0, packet.size - 2)
        val expected = crc16CcittFalse(body)
        val actual = packet.u8(packet.size - 2) or (packet.u8(packet.size - 1) shl 8)
        return expected == actual
    }

    private fun crc16CcittFalse(data: ByteArray): Int {
        var crc = 0xFFFF
        data.forEach { byte ->
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc =
                    if ((crc and 0x8000) != 0) {
                        ((crc shl 1) xor 0x1021) and 0xFFFF
                    } else {
                        (crc shl 1) and 0xFFFF
                    }
            }
        }
        return crc and 0xFFFF
    }

    private fun addOrUpdateDevice(item: SpineBraceDevice) {
        devices[item.address.uppercase()] = item
        notifyDevicesChanged()
    }

    private fun notifyStatus(message: String) {
        mainHandler.post { callback.onStatus(message) }
    }

    private fun notifyDevicesChanged() {
        mainHandler.post { callback.onDevicesChanged(devices.values.sortedByDescending { it.rssi }) }
    }

    private fun notifyConnected(device: SpineBraceDevice) {
        mainHandler.post { callback.onConnected(device) }
    }

    private fun notifyDisconnected() {
        mainHandler.post { callback.onDisconnected() }
    }

    private fun notifyTelemetry(telemetry: SpineBraceTelemetry) {
        mainHandler.post { callback.onTelemetry(telemetry) }
    }

    private fun notifyVersion(version: SpineBraceVersion) {
        mainHandler.post { callback.onVersion(version) }
    }

    private fun notifyHistory(snapshot: SpineBraceHistorySnapshot) {
        mainHandler.post { callback.onHistory(snapshot) }
    }

    private fun ScanResult.extractDeviceName(): String? {
        val advertisedName = scanRecord?.deviceName
        if (!advertisedName.isNullOrBlank()) {
            return advertisedName
        }
        val cachedName =
            try {
                device?.name
            } catch (_: SecurityException) {
                null
            }
        if (!cachedName.isNullOrBlank()) {
            return cachedName
        }
        val rawText = runCatching { scanRecord?.bytes?.toString(Charsets.ISO_8859_1) }.getOrNull()
        return rawText?.let { DEVICE_NAME_REGEX.find(it)?.value }
    }

    private fun isSpineBraceDeviceName(name: String): Boolean =
        name.trim().startsWith(DEVICE_PREFIX, ignoreCase = true)

    private fun writeCharacteristicScore(characteristic: BluetoothGattCharacteristic): Int {
        val uuid = characteristic.uuid.toString()
        val serviceUuid = characteristic.service?.uuid?.toString().orEmpty()
        val props = characteristic.properties
        var score = 0
        if (uuid.contains("ffe9", ignoreCase = true)) score += 80
        if (uuid.contains("ffe1", ignoreCase = true)) score += 40
        if (serviceUuid.contains("ffe0", ignoreCase = true)) score += 20
        if (props.hasAny(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) score += 8
        if (props.hasAny(BluetoothGattCharacteristic.PROPERTY_WRITE)) score += 4
        return score
    }

    private fun isBraceNotifyCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        val uuid = characteristic.uuid.toString()
        val serviceUuid = characteristic.service?.uuid?.toString().orEmpty()
        return uuid.contains("ffe4", ignoreCase = true) ||
            serviceUuid.contains("ffe0", ignoreCase = true) ||
            serviceUuid.contains("ffe5", ignoreCase = true)
    }

    private fun isSystemServiceChangedCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean =
        characteristic.uuid.toString().contains("2a05", ignoreCase = true)

    private fun Int.hasAny(vararg flags: Int): Boolean = flags.any { flag -> this and flag != 0 }

    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

    private fun readUInt16LittleEndian(value: ByteArray, offset: Int): Int =
        value.u8(offset) or (value.u8(offset + 1) shl 8)

    private fun readUInt16BigEndian(value: ByteArray, offset: Int): Int =
        (value.u8(offset) shl 8) or value.u8(offset + 1)

    private fun ByteArray.toHexString(): String =
        joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private companion object {
        const val TAG = "SpineBraceBT"
        const val DEVICE_PREFIX = "DR-Z-T0"
        const val SCAN_TIMEOUT_MS = 10_000L
        const val BLE_SERVICE_DISCOVERY_DELAY_MS = 350L
        const val MIN_PACKET_SIZE = 5
        const val MAX_BUFFER_SIZE = 512
        const val TELEMETRY_PACKET_SIZE = 15
        const val HISTORY_PACKET_SIZE = 18
        const val HISTORY_ALT_PACKET_SIZE = 20
        const val EXPECTED_HISTORY_DATA_PACKETS = 36
        const val VERSION_PACKET_SIZE = 7
        val DEVICE_NAME_REGEX = Regex("DR-Z-T0[A-Za-z0-9_-]*", RegexOption.IGNORE_CASE)
        val CLIENT_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
