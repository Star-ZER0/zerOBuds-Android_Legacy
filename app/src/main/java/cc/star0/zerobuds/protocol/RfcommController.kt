package cc.star0.zerobuds.protocol

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 日志条目 */
data class LogEntry(
    val timestamp: Long,
    val direction: LogDirection,
    val hexData: String
)

enum class LogDirection { SEND, RECV }

/**
 * zerOBuds RFCOMM 控制器
 * 负责与耳机建立连接、完成初始化握手、收发命令
 */
@SuppressLint("MissingPermission")
class RfcommController {
    companion object {
        private const val TAG = "zerOBuds-Rfcomm"
        private const val DEFAULT_CHANNEL = 15
    }

    var rfcommChannel: Int = DEFAULT_CHANNEL
    var subscribeBroadcast: Boolean = true

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    private var socket: BluetoothSocket? = null
    private var isConnected = false
    private var currentDevice: BluetoothDevice? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var batteryPollJob: Job? = null // kept for compatibility

    // 状态流
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _batteryInfo = MutableStateFlow(BatteryParser.BatteryResult(null, null, null))
    val batteryInfo: StateFlow<BatteryParser.BatteryResult> = _batteryInfo

    private val _ancMode = MutableStateFlow(AncMode.OFF)
    val ancMode: StateFlow<AncMode> = _ancMode

    private val _noiseLevel = MutableStateFlow(NoiseLevel.DEEP)
    val noiseLevel: StateFlow<NoiseLevel> = _noiseLevel

    private val _vocalEnhancement = MutableStateFlow(false)
    val vocalEnhancement: StateFlow<Boolean> = _vocalEnhancement

    private val _gameMode1 = MutableStateFlow(false)
    val gameMode1: StateFlow<Boolean> = _gameMode1

    private val _gameMode2 = MutableStateFlow(false)
    val gameMode2: StateFlow<Boolean> = _gameMode2

    private val _autoPlayPause = MutableStateFlow(false)
    val autoPlayPause: StateFlow<Boolean> = _autoPlayPause

    private val _spatialSound = MutableStateFlow(false)
    val spatialSound: StateFlow<Boolean> = _spatialSound

    private val _hiRes = MutableStateFlow(false)
    val hiRes: StateFlow<Boolean> = _hiRes

    private val _dualDevice = MutableStateFlow(false)
    val dualDevice: StateFlow<Boolean> = _dualDevice

    private val _windowsSwiftPair = MutableStateFlow(false)
    val windowsSwiftPair: StateFlow<Boolean> = _windowsSwiftPair

    private val _supportedParams = MutableStateFlow<Set<Int>>(emptySet())
    val supportedParams: StateFlow<Set<Int>> = _supportedParams

    private val _spatialAudio = MutableStateFlow(SpatialAudioMode.OFF)
    val spatialAudio: StateFlow<SpatialAudioMode> = _spatialAudio

    private val _promptVolume = MutableStateFlow(-1)
    val promptVolume: StateFlow<Int> = _promptVolume

    private val _wearStatus = MutableStateFlow(WearStatusResult(null, null, null))
    val wearStatus: StateFlow<WearStatusResult> = _wearStatus

    private val _connectedDevices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val connectedDevices: StateFlow<List<ConnectedDevice>> = _connectedDevices

    private val _deviceName = MutableStateFlow("")
    val deviceName: StateFlow<String> = _deviceName

    private val _deviceAddress = MutableStateFlow("")
    val deviceAddress: StateFlow<String> = _deviceAddress

    // 设置失败事件
    private val _settingError = MutableStateFlow<String?>(null)
    val settingError: StateFlow<String?> = _settingError

    // 待确认的设置操作 (paramId, 旧值)
    private var pendingSetting: Pair<Int, Boolean>? = null

    // 日志
    private var _developerMode = false
    val developerMode: Boolean get() = _developerMode

    private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logEntries: StateFlow<List<LogEntry>> = _logEntries

    fun setDeveloperMode(enabled: Boolean) {
        _developerMode = enabled
        if (!enabled) _logEntries.value = emptyList()
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun createRfcommSocket(device: BluetoothDevice): BluetoothSocket {
        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        return method.invoke(device, rfcommChannel) as BluetoothSocket
    }

    fun connect(device: BluetoothDevice) {
        connectWithRetry(device, remainingRetries = 3)
    }

    private fun connectWithRetry(device: BluetoothDevice, remainingRetries: Int) {
        if (_connectionState.value == ConnectionState.CONNECTING && remainingRetries == 3) return

        currentDevice = device
        _deviceName.value = device.name ?: device.address
        _deviceAddress.value = device.address
        _connectionState.value = ConnectionState.CONNECTING

        scope.launch {
            try {
                delay(300)
                socket = createRfcommSocket(device)
                socket!!.connect()
                Log.d(TAG, "RFCOMM connected to ${device.name}")
                isConnected = true
                _connectionState.value = ConnectionState.CONNECTED

                startPacketReader(socket!!.inputStream)

                // 初始化握手序列
                delay(300)
                sendPacket(PrebuiltPackets.handshake())
                delay(200)
                if (subscribeBroadcast) {
                    sendPacket(PrebuiltPackets.queryBroadcastCodes())
                }

                delay(300)
                queryAllStatus()
            } catch (e: IOException) {
                Log.e(TAG, "RFCOMM connect failed (${4 - remainingRetries}/3)", e)
                isConnected = false
                try { socket?.close() } catch (_: IOException) {}
                socket = null
                if (remainingRetries > 1) {
                    Log.d(TAG, "Retrying connection (${4 - remainingRetries}/3)...")
                    connectWithRetry(device, remainingRetries - 1)
                } else {
                    _connectionState.value = ConnectionState.ERROR
                }
            }
        }
    }

    private var pendingBroadcastCodes: List<Int>? = null

    private fun startPacketReader(inputStream: InputStream) {
        scope.launch {
            val buffer = ByteArray(1024)
            try {
                while (isConnected) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        handleRawData(buffer.copyOfRange(0, bytesRead))
                    } else if (bytesRead == -1) {
                        break
                    }
                }
            } catch (e: IOException) {
                if (isConnected) Log.e(TAG, "Read error", e)
            }
            if (isConnected) disconnect()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun handleRawData(data: ByteArray) {
        Log.v(TAG, "Received: ${data.toHexString()}")
        addLog(LogDirection.RECV, data.toHexString().uppercase())

        val packet = ZerOBudsPackets.parsePacket(data) ?: run {
            Log.w(TAG, "Failed to parse packet")
            return
        }

        when (packet.cmd) {
            // 握手响应
            Cmd.HANDSHAKE_RESPONSE -> {
                Log.d(TAG, "Handshake response received")
            }

            // 广播码列表响应
            Cmd.BROADCAST_CODES_RESPONSE -> {
                if (packet.payload.size >= 2) {
                    val count = packet.payload[1].toInt() and 0xFF
                    val codes = (0 until count).mapNotNull { i ->
                        if (2 + i < packet.payload.size) packet.payload[2 + i].toInt() and 0xFF else null
                    }
                    Log.d(TAG, "Broadcast codes: $codes")
                    pendingBroadcastCodes = codes
                    // 自动订阅所有广播码
                    scope.launch {
                        delay(100)
                        sendPacket(PrebuiltPackets.subscribeBroadcast(codes))
                    }
                }
            }

            // 订阅响应
            Cmd.SUBSCRIBE_BROADCAST_RESPONSE -> {
                Log.d(TAG, "Subscribe broadcast response received")
            }

            // 电量响应
            Cmd.BATTERY_RESPONSE -> {
                BatteryParser.parseResponse(packet)?.let {
                    _batteryInfo.value = it
                }
            }

            // ANC 响应
            Cmd.ANC_RESPONSE -> {
                AncModeParser.parse(packet)?.let {
                    Log.d(TAG, "ANC mode: $it")
                    _ancMode.value = it.mode
                    it.noiseLevel?.let { level -> _noiseLevel.value = level }
                }
            }

            // 批量状态响应
            Cmd.QUERY_STATUS_RESPONSE -> {
                StatusParser.parse(packet)?.let { (status, presentParams) ->
                    _supportedParams.value = presentParams
                    status.gameMode1?.let { _gameMode1.value = it }
                    status.gameMode2?.let { _gameMode2.value = it }
                    status.autoPlayPause?.let { _autoPlayPause.value = it }
                    status.spatialSound?.let { _spatialSound.value = it }
                    status.hiRes?.let { _hiRes.value = it }
                    status.dualDevice?.let { _dualDevice.value = it }
                    status.windowsSwiftPair?.let { _windowsSwiftPair.value = it }
                }
            }

            // 设置响应
            Cmd.SET_SETTING_RESPONSE -> {
                if (packet.payload.size == 1) {
                    // 状态码响应: 0x00=成功, 0x02=失败
                    val status = packet.payload[0].toInt() and 0xFF
                    if (status == 0x02) {
                        // 设置失败，回滚到旧值
                        pendingSetting?.let { (paramId, oldValue) ->
                            val paramName = when (paramId) {
                                ParamId.GAME_MODE_1 -> "GameMode1"
                                ParamId.GAME_MODE_2 -> "GameMode2"
                                ParamId.AUTO_PLAY_PAUSE -> "AutoPlayPause"
                                ParamId.SPATIAL_SOUND -> "SpatialSound"
                                ParamId.HI_RES -> "HiRes"
                                ParamId.DUAL_DEVICE -> "DualDevice"
                                ParamId.WINDOWS_SWIFT_PAIR -> "WindowsSwiftPair"
                                else -> "Unknown(0x${paramId.toString(16)})"
                            }
                            when (paramId) {
                                ParamId.GAME_MODE_1 -> _gameMode1.value = oldValue
                                ParamId.GAME_MODE_2 -> _gameMode2.value = oldValue
                                ParamId.AUTO_PLAY_PAUSE -> _autoPlayPause.value = oldValue
                                ParamId.SPATIAL_SOUND -> _spatialSound.value = oldValue
                                ParamId.HI_RES -> _hiRes.value = oldValue
                                ParamId.DUAL_DEVICE -> _dualDevice.value = oldValue
                                ParamId.WINDOWS_SWIFT_PAIR -> _windowsSwiftPair.value = oldValue
                            }
                            _settingError.value = paramName
                            Log.w(TAG, "Setting $paramName failed, rolled back to $oldValue")
                        }
                    }
                    pendingSetting = null
                } else if (packet.payload.size >= 2) {
                    // 兼容旧格式: [ParamId] [Value]
                    val paramId = packet.payload[0].toInt() and 0xFF
                    val value = packet.payload[1].toInt() and 0xFF == 0x01
                    when (paramId) {
                        ParamId.GAME_MODE_1 -> _gameMode1.value = value
                        ParamId.GAME_MODE_2 -> _gameMode2.value = value
                        ParamId.AUTO_PLAY_PAUSE -> _autoPlayPause.value = value
                        ParamId.SPATIAL_SOUND -> _spatialSound.value = value
                        ParamId.HI_RES -> _hiRes.value = value
                        ParamId.DUAL_DEVICE -> _dualDevice.value = value
                        ParamId.WINDOWS_SWIFT_PAIR -> _windowsSwiftPair.value = value
                    }
                }
            }

            // ANC 设置响应
            Cmd.SET_ANC_RESPONSE -> {
                AncModeParser.parse(packet)?.let {
                    _ancMode.value = it.mode
                    it.noiseLevel?.let { level -> _noiseLevel.value = level }
                }
            }

            // 空间音频设置响应
            Cmd.SET_SPATIAL_AUDIO_RESPONSE -> {
                SpatialAudioParser.parse(packet)?.let {
                    _spatialAudio.value = it
                }
            }

            // 空间音频通知
            Cmd.SPATIAL_AUDIO_NOTIFICATION -> {
                SpatialAudioParser.parseNotification(packet)?.let {
                    Log.d(TAG, "Spatial audio notification: $it")
                    _spatialAudio.value = it
                }
            }

            // 提示音音量查询响应
            Cmd.VOLUME_RESPONSE -> {
                VolumeParser.parseResponse(packet)?.let {
                    _promptVolume.value = it
                }
            }

            // 提示音音量设置响应
            Cmd.SET_VOLUME_RESPONSE -> {
                VolumeParser.parseSetResponse(packet)?.let {
                    _promptVolume.value = it
                }
            }

            // 耳机主动通知
            Cmd.NOTIFICATION -> {
                handleNotification(packet)
            }

            else -> {
                Log.d(TAG, "Unhandled cmd: 0x${packet.cmd.toString(16)}")
            }
        }
    }

    private fun handleNotification(packet: ParsedPacket) {
        if (packet.payload.isEmpty()) return
        val reportType = packet.payload[0].toInt() and 0xFF

        when (reportType) {
            ReportType.BATTERY -> {
                BatteryParser.parseNotification(packet)?.let {
                    _batteryInfo.value = it
                }
            }
            ReportType.WEAR_STATUS -> {
                WearStatusParser.parse(packet)?.let {
                    _wearStatus.value = it
                }
            }
            ReportType.ANC_MODE, ReportType.ANC_MODE_CHANGE -> {
                AncModeParser.parse(packet)?.let {
                    Log.d(TAG, "ANC notification: $it")
                    _ancMode.value = it.mode
                    it.noiseLevel?.let { level -> _noiseLevel.value = level }
                    it.vocalEnhancement?.let { ve -> _vocalEnhancement.value = ve }
                }
            }
            ReportType.GAME_MODE -> {
                GameModeNotificationParser.parse(packet)?.let {
                    Log.d(TAG, "Game mode notification: $it")
                    _gameMode1.value = it
                }
            }
            ReportType.CONNECTED_DEVICES -> {
                ConnectedDevicesParser.parse(packet)?.let {
                    Log.d(TAG, "Connected devices notification: $it")
                    _connectedDevices.value = it
                }
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun sendPacket(packet: ByteArray) {
        try {
            socket?.outputStream?.write(packet)
            socket?.outputStream?.flush()
            addLog(LogDirection.SEND, packet.toHexString().uppercase())
        } catch (e: IOException) {
            Log.e(TAG, "Send failed", e)
        }
    }

    /** 查询全部状态 */
    private fun queryAllStatus() {
        scope.launch {
            sendPacket(PrebuiltPackets.queryStatus())
            delay(50)
            sendPacket(PrebuiltPackets.queryBattery())
            delay(50)
            sendPacket(PrebuiltPackets.queryAnc())
            delay(50)
            sendPacket(PrebuiltPackets.queryVolume())
        }
    }

    /** 刷新状态（供 UI 调用） */
    fun refreshStatus() {
        if (!isConnected) return
        queryAllStatus()
    }

    /** 设置 ANC 模式 */
    fun setAncMode(mode: AncMode) {
        _ancMode.value = mode
        scope.launch {
            sendPacket(PrebuiltPackets.setAnc(mode))
            if (mode == AncMode.NOISE_CANCELLATION) {
                delay(100)
                sendPacket(PrebuiltPackets.queryNoiseLevel())
            }
        }
    }

    /** 设置降噪等级（仅在降噪模式下有效） */
    fun setNoiseLevel(level: NoiseLevel) {
        _noiseLevel.value = level
        scope.launch { sendPacket(PrebuiltPackets.setNoiseLevel(level)) }
    }

    /** 设置人声增强 */
    fun setVocalEnhancement(enabled: Boolean) {
        _vocalEnhancement.value = enabled
        scope.launch { sendPacket(PrebuiltPackets.setVocalEnhancement(enabled)) }
    }

    /** 设置游戏模式1 */
    fun setGameMode1(enabled: Boolean) {
        pendingSetting = Pair(ParamId.GAME_MODE_1, _gameMode1.value)
        _gameMode1.value = enabled
        scope.launch { sendPacket(PrebuiltPackets.setSetting(ParamId.GAME_MODE_1, enabled)) }
    }

    /** 设置游戏模式2 */
    fun setGameMode2(enabled: Boolean) {
        pendingSetting = Pair(ParamId.GAME_MODE_2, _gameMode2.value)
        _gameMode2.value = enabled
        scope.launch { sendPacket(PrebuiltPackets.setSetting(ParamId.GAME_MODE_2, enabled)) }
    }

    /** 设置佩戴自动播放暂停 */
    fun setAutoPlayPause(enabled: Boolean) {
        pendingSetting = Pair(ParamId.AUTO_PLAY_PAUSE, _autoPlayPause.value)
        _autoPlayPause.value = enabled
        scope.launch { sendPacket(PrebuiltPackets.setSetting(ParamId.AUTO_PLAY_PAUSE, enabled)) }
    }

    /** 设置提示音音量 */
    fun setPromptVolume(volByte: Int) {
        _promptVolume.value = volByte
        scope.launch { sendPacket(PrebuiltPackets.setVolume(volByte)) }
    }

    /** 设置空间音效 */
    fun setSpatialSound(enabled: Boolean) {
        pendingSetting = Pair(ParamId.SPATIAL_SOUND, _spatialSound.value)
        _spatialSound.value = enabled
        scope.launch { sendPacket(PrebuiltPackets.setSetting(ParamId.SPATIAL_SOUND, enabled)) }
    }

    /** 设置高清音频 */
    fun setHiRes(enabled: Boolean) {
        pendingSetting = Pair(ParamId.HI_RES, _hiRes.value)
        _hiRes.value = enabled
        scope.launch { sendPacket(PrebuiltPackets.setSetting(ParamId.HI_RES, enabled)) }
    }

    /** 设置双设备连接 */
    fun setDualDevice(enabled: Boolean) {
        pendingSetting = Pair(ParamId.DUAL_DEVICE, _dualDevice.value)
        _dualDevice.value = enabled
        scope.launch { sendPacket(PrebuiltPackets.setSetting(ParamId.DUAL_DEVICE, enabled)) }
    }

    /** 设置 Windows Swift Pair */
    fun setWindowsSwiftPair(enabled: Boolean) {
        pendingSetting = Pair(ParamId.WINDOWS_SWIFT_PAIR, _windowsSwiftPair.value)
        _windowsSwiftPair.value = enabled
        scope.launch { sendPacket(PrebuiltPackets.setSetting(ParamId.WINDOWS_SWIFT_PAIR, enabled)) }
    }

    /** 设置空间音频模式 */
    fun setSpatialAudio(mode: SpatialAudioMode) {
        _spatialAudio.value = mode
        scope.launch { sendPacket(PrebuiltPackets.setSpatialAudio(mode)) }
    }

    /** 手动发送 hex 数据 */
    fun sendRawHex(hex: String) {
        val bytes = hexToByteArray(hex) ?: return
        scope.launch { sendPacket(bytes) }
    }

    /** 清空日志 */
    fun clearLog() {
        _logEntries.value = emptyList()
    }

    /** 清除设置错误状态 */
    fun clearSettingError() {
        _settingError.value = null
    }

    private fun addLog(direction: LogDirection, hexData: String) {
        if (!_developerMode) return
        val entry = LogEntry(System.currentTimeMillis(), direction, hexData)
        _logEntries.value = _logEntries.value + entry
    }

    /** 将 "AA 07 00" 格式的 hex 字符串转为 ByteArray */
    private fun hexToByteArray(hex: String): ByteArray? {
        return try {
            val cleaned = hex.trim().replace("\\s+".toRegex(), "")
            if (cleaned.length % 2 != 0) return null
            cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** 重新连接当前设备 */
    fun reconnect() {
        val device = currentDevice ?: return
        if (_connectionState.value == ConnectionState.CONNECTING) return
        disconnect()
        connect(device)
    }

    /** 断开连接 */
    fun disconnect() {
        isConnected = false
        batteryPollJob?.cancel()
        try { socket?.close() } catch (_: IOException) {}
        socket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _batteryInfo.value = BatteryParser.BatteryResult(null, null, null)
        _ancMode.value = AncMode.OFF
        _noiseLevel.value = NoiseLevel.DEEP
        _gameMode1.value = false
        _gameMode2.value = false
        _autoPlayPause.value = false
        _spatialSound.value = false
        _hiRes.value = false
        _dualDevice.value = false
        _windowsSwiftPair.value = false
        _spatialAudio.value = SpatialAudioMode.OFF
        _promptVolume.value = -1
        _wearStatus.value = WearStatusResult(null, null, null)
        _connectedDevices.value = emptyList()
        _deviceName.value = ""
        _deviceAddress.value = ""
    }
}
