package cc.star0.zerobuds.protocol

/**
 * zerOBuds RFCOMM 协议包定义
 *
 * 数据包格式 (小端序):
 * Header(AA) + TotalLen(LEB128) + Res(0000) + Cmd(2B LE) + Seq(1B) + PayLen(2B LE) + Payload
 */

object ZerOBudsPackets {

    /** LEB128 编码 TotalLen */
    fun encodeLeb128(value: Int): ByteArray {
        if (value < 128) {
            return byteArrayOf(value.toByte())
        }
        // >= 128: 先减1，再标准 LEB128 编码
        var v = value - 1
        val result = mutableListOf<Byte>()
        do {
            var b = (v and 0x7F).toByte()
            v = v ushr 7
            if (v != 0) {
                b = (b.toInt() or 0x80).toByte()
            }
            result.add(b)
        } while (v != 0)
        return result.toByteArray()
    }

    /** LEB128 解码 TotalLen */
    fun decodeLeb128(data: ByteArray, offset: Int): Pair<Int, Int> {
        val firstByte = data[offset].toInt() and 0xFF
        if (firstByte < 128) {
            return Pair(firstByte, 1)
        }
        // 多字节: 拼接低7位，最后加1
        var result = 0
        var shift = 0
        var i = offset
        while (true) {
            val b = data[i].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
            i++
        }
        return Pair(result + 1, i - offset + 1)
    }

    /** 构建完整协议包 */
    fun buildPacket(cmd: Int, seq: Int = 0x01, payload: ByteArray = byteArrayOf()): ByteArray {
        val payLen = payload.size
        val totalLen = 7 + payLen // Res(2) + Cmd(2) + Seq(1) + PayLen(2) + Payload
        val totalLenEncoded = encodeLeb128(totalLen)

        val packet = ByteArray(1 + totalLenEncoded.size + totalLen)
        var pos = 0
        packet[pos++] = 0xAA.toByte() // Header
        totalLenEncoded.copyInto(packet, pos); pos += totalLenEncoded.size
        packet[pos++] = 0x00 // Res byte 1
        packet[pos++] = 0x00 // Res byte 2
        packet[pos++] = (cmd and 0xFF).toByte()         // Cmd low byte
        packet[pos++] = ((cmd shr 8) and 0xFF).toByte()  // Cmd high byte
        packet[pos++] = seq.toByte()                      // Seq
        packet[pos++] = (payLen and 0xFF).toByte()        // PayLen low byte
        packet[pos++] = ((payLen shr 8) and 0xFF).toByte() // PayLen high byte
        payload.copyInto(packet, pos)
        return packet
    }

    /** 解析数据包头部，返回 (cmd, seq, payload) 或 null */
    fun parsePacket(data: ByteArray): ParsedPacket? {
        if (data.size < 9) return null
        if (data[0] != 0xAA.toByte()) return null

        val (totalLen, lebLen) = decodeLeb128(data, 1)
        val headerEnd = 1 + lebLen
        if (data.size < headerEnd + 6) return null // Res(2) + Cmd(2) + Seq(1) + PayLen(2) = 6

        val cmd = (data[headerEnd + 2].toInt() and 0xFF) or
                ((data[headerEnd + 3].toInt() and 0xFF) shl 8)
        val seq = data[headerEnd + 4].toInt() and 0xFF
        val payLen = (data[headerEnd + 5].toInt() and 0xFF) or
                ((data[headerEnd + 6].toInt() and 0xFF) shl 8)

        val payloadStart = headerEnd + 7
        if (data.size < payloadStart + payLen) return null

        val payload = data.copyOfRange(payloadStart, payloadStart + payLen)
        return ParsedPacket(cmd, seq, payLen, payload)
    }
}

data class ParsedPacket(
    val cmd: Int,
    val seq: Int,
    val payLen: Int,
    val payload: ByteArray
)

/** 命令码 */
object Cmd {
    // Query 类
    const val HANDSHAKE = 0x0100
    const val HANDSHAKE_RESPONSE = 0x8100
    const val QUERY_BATTERY = 0x0106
    const val BATTERY_RESPONSE = 0x8106
    const val QUERY_ANC = 0x010C
    const val ANC_RESPONSE = 0x810C
    const val QUERY_STATUS = 0x010D
    const val QUERY_STATUS_RESPONSE = 0x810D
    const val QUERY_VOLUME = 0x0130
    const val VOLUME_RESPONSE = 0x8130

    // Broadcast 类
    const val QUERY_BROADCAST_CODES = 0x0200
    const val BROADCAST_CODES_RESPONSE = 0x8200
    const val NOTIFICATION = 0x0204
    const val SUBSCRIBE_BROADCAST = 0x0205
    const val SUBSCRIBE_BROADCAST_RESPONSE = 0x8205

    // Setting 类
    const val SET_SETTING = 0x0403
    const val SET_SETTING_RESPONSE = 0x8403
    const val SET_ANC = 0x0404
    const val SET_ANC_RESPONSE = 0x8404
    const val SET_SPATIAL_AUDIO = 0x0422
    const val SET_SPATIAL_AUDIO_RESPONSE = 0x8422
    const val SET_VOLUME = 0x0427
    const val SET_VOLUME_RESPONSE = 0x8427

    // 通知类
    const val SPATIAL_AUDIO_NOTIFICATION = 0x0510
}

/** 批量状态查询参数 ID */
object ParamId {
    const val AUTO_PLAY_PAUSE = 0x04
    const val GAME_MODE_1 = 0x06
    const val DUAL_DEVICE = 0x11
    const val HI_RES = 0x18
    const val SPATIAL_SOUND = 0x1B
    const val GAME_MODE_2 = 0x28
    const val WINDOWS_SWIFT_PAIR = 0x37
}

/** 降噪模式 */
enum class AncMode {
    OFF, NOISE_CANCELLATION, TRANSPARENCY, ADAPTIVE
}

/** 降噪等级（仅在 NOISE_CANCELLATION 模式下有效） */
enum class NoiseLevel(val value: Int) {
    SMART(0x80),
    LIGHT(0x40),
    MEDIUM(0x20),
    DEEP(0x10);

    companion object {
        fun fromValue(value: Int): NoiseLevel? = entries.find { it.value == value }
    }
}

/** ANC 解析结果，包含模式和降噪等级/人声增强 */
data class AncParseResult(val mode: AncMode, val noiseLevel: NoiseLevel? = null, val vocalEnhancement: Boolean? = null)

/** 空间音频模式 */
enum class SpatialAudioMode(val value: Int) {
    OFF(0), MODE_1(1), MODE_2(2);

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: OFF
    }
}

/** 通知类型 */
object ReportType {
    const val BATTERY = 0x01
    const val WEAR_STATUS = 0x02
    const val ANC_MODE_CHANGE = 0x03
    const val ANC_MODE = 0x04
    const val GAME_MODE = 0x05
    const val CONNECTED_DEVICES = 0x06
}

/** 佩戴状态 */
enum class WearStatus(val value: Int) {
    DISCONNECTED(0x00),
    IN_CASE(0x04),
    REMOVED(0x05),
    WEARING(0x07);

    companion object {
        fun fromValue(value: Int): WearStatus? = entries.find { it.value == value }
    }
}

/** 佩戴状态结果 */
data class WearStatusResult(val left: WearStatus?, val right: WearStatus?, val case: WearStatus?)

/** 预构建包 */
object PrebuiltPackets {
    private var seqCounter: Int = 0x01
    private fun nextSeq(): Int {
        val seq = seqCounter
        seqCounter = if (seqCounter >= 0xFE) 0x01 else seqCounter + 1
        return seq
    }

    /** 握手: 0x0100 */
    fun handshake(): ByteArray = ZerOBudsPackets.buildPacket(Cmd.HANDSHAKE, nextSeq())

    /** 请求广播码列表: 0x0200 */
    fun queryBroadcastCodes(): ByteArray = ZerOBudsPackets.buildPacket(Cmd.QUERY_BROADCAST_CODES, nextSeq())

    /** 订阅广播码: 0x0205 */
    fun subscribeBroadcast(codes: List<Int>): ByteArray {
        val payload = byteArrayOf(codes.size.toByte()) + codes.map { it.toByte() }.toByteArray()
        return ZerOBudsPackets.buildPacket(Cmd.SUBSCRIBE_BROADCAST, nextSeq(), payload)
    }

    /** 查询电量: 0x0106 */
    fun queryBattery(): ByteArray = ZerOBudsPackets.buildPacket(Cmd.QUERY_BATTERY, nextSeq())

    /** 查询 ANC: 0x010C */
    fun queryAnc(): ByteArray = ZerOBudsPackets.buildPacket(Cmd.QUERY_ANC, nextSeq(), byteArrayOf(0x01, 0x01))

    /** 查询批量状态: 0x010D — 查询控制页面关联的参数 */
    fun queryStatus(): ByteArray = ZerOBudsPackets.buildPacket(
        Cmd.QUERY_STATUS, nextSeq(),
        byteArrayOf(0x07, ParamId.AUTO_PLAY_PAUSE.toByte(), ParamId.GAME_MODE_1.toByte(), ParamId.DUAL_DEVICE.toByte(), ParamId.HI_RES.toByte(), ParamId.SPATIAL_SOUND.toByte(), ParamId.GAME_MODE_2.toByte(), ParamId.WINDOWS_SWIFT_PAIR.toByte())
    )

    /** 查询提示音音量: 0x0130 */
    fun queryVolume(): ByteArray = ZerOBudsPackets.buildPacket(
        Cmd.QUERY_VOLUME, nextSeq(), byteArrayOf(0x00, 0x00)
    )

    /** 设置提示音音量: 0x0427 */
    fun setVolume(volByte: Int): ByteArray = ZerOBudsPackets.buildPacket(
        Cmd.SET_VOLUME, nextSeq(), byteArrayOf(volByte.toByte())
    )

    /** 设置 ANC 模式: 0x0404 */
    fun setAnc(mode: AncMode): ByteArray {
        val payload = when (mode) {
            AncMode.OFF -> byteArrayOf(0x01, 0x01, 0x01)
            AncMode.NOISE_CANCELLATION -> byteArrayOf(0x01, 0x01, 0x02)
            AncMode.TRANSPARENCY -> byteArrayOf(0x01, 0x01, 0x04)
            AncMode.ADAPTIVE -> byteArrayOf(0x01, 0x01, 0x00, 0x08)
        }
        return ZerOBudsPackets.buildPacket(Cmd.SET_ANC, nextSeq(), payload)
    }

    /** 设置降噪等级: 0x0404 */
    fun setNoiseLevel(level: NoiseLevel): ByteArray {
        return ZerOBudsPackets.buildPacket(
            Cmd.SET_ANC, nextSeq(),
            byteArrayOf(0x01, 0x01, level.value.toByte())
        )
    }

    /** 查询降噪等级: 0x010C */
    fun queryNoiseLevel(): ByteArray = ZerOBudsPackets.buildPacket(
        Cmd.QUERY_ANC, nextSeq(), byteArrayOf(0x02, 0x00, 0x01, 0x01)
    )

    /** 设置人声增强: 0x0404 */
    fun setVocalEnhancement(enabled: Boolean): ByteArray {
        val payload = if (enabled) byteArrayOf(0x01, 0x01, 0x00, 0x02) else byteArrayOf(0x01, 0x01, 0x00, 0x01)
        return ZerOBudsPackets.buildPacket(Cmd.SET_ANC, nextSeq(), payload)
    }

    /** 设置开关项: 0x0403 */
    fun setSetting(paramId: Int, value: Boolean): ByteArray {
        return ZerOBudsPackets.buildPacket(
            Cmd.SET_SETTING, nextSeq(),
            byteArrayOf(paramId.toByte(), if (value) 0x01 else 0x00)
        )
    }

    /** 设置空间音频: 0x0422 */
    fun setSpatialAudio(mode: SpatialAudioMode): ByteArray {
        return ZerOBudsPackets.buildPacket(
            Cmd.SET_SPATIAL_AUDIO, nextSeq(),
            byteArrayOf(mode.value.toByte())
        )
    }
}

/** 电量解析器 */
object BatteryParser {

    data class BatteryInfo(val level: Int, val isCharging: Boolean)
    data class BatteryResult(val left: BatteryInfo?, val right: BatteryInfo?, val case: BatteryInfo?)

    /** 解析电量查询响应 (0x8106) */
    fun parseResponse(packet: ParsedPacket): BatteryResult? {
        if (packet.cmd != Cmd.BATTERY_RESPONSE) return null
        return parseBatteryPayload(packet.payload)
    }

    /** 解析电量主动通知 (0x0204, type=0x01) */
    fun parseNotification(packet: ParsedPacket): BatteryResult? {
        if (packet.cmd != Cmd.NOTIFICATION) return null
        if (packet.payload.isEmpty()) return null
        if (packet.payload[0].toInt() and 0xFF != ReportType.BATTERY) return null
        if (packet.payload.size < 2) return null

        val count = packet.payload[1].toInt() and 0xFF
        val pairs = packet.payload.copyOfRange(2, 2 + count * 2)
        return parseBatteryPairs(pairs)
    }

    private fun parseBatteryPayload(payload: ByteArray): BatteryResult {
        return parseBatteryPairs(payload)
    }

    private fun parseBatteryPairs(data: ByteArray): BatteryResult {
        var left: BatteryInfo? = null
        var right: BatteryInfo? = null
        var case: BatteryInfo? = null

        var i = 0
        while (i + 1 < data.size) {
            val index = data[i].toInt() and 0xFF
            val rawValue = data[i + 1].toInt() and 0xFF
            val level = rawValue and 0x7F
            val charging = (rawValue and 0x80) != 0
            val info = BatteryInfo(level, charging)

            when (index) {
                1 -> left = info   // 左耳
                2 -> right = info  // 右耳
                3 -> case = info   // 充电盒
            }
            i += 2
        }
        return BatteryResult(left, right, case)
    }
}

/** ANC 模式解析器 */
object AncModeParser {

    /** 解析 ANC 查询响应 (0x810C) 或 ANC 通知 (0x0204 ReportType=0x04/0x03) */
    fun parse(packet: ParsedPacket): AncParseResult? {
        if (packet.cmd == Cmd.ANC_RESPONSE) {
            return parseAncPayload(packet.payload)
        }
        if (packet.cmd == Cmd.NOTIFICATION) {
            if (packet.payload.isEmpty()) return null
            val reportType = packet.payload[0].toInt() and 0xFF
            // 0x02 = ANC模式/佩戴状态, 0x03 = ANC模式切换通知
            if (reportType == ReportType.ANC_MODE || reportType == ReportType.ANC_MODE_CHANGE) {
                return parseAncPayload(packet.payload)
            }
        }
        return null
    }

    /** 在 payload 中查找 01 01 [Val1] [Val2] 模式 */
    private fun parseAncPayload(payload: ByteArray): AncParseResult? {
        for (i in 0 until payload.size - 3) {
            if (payload[i] == 0x01.toByte() && payload[i + 1] == 0x01.toByte()) {
                val val1 = payload[i + 2].toInt() and 0xFF
                val val2 = payload[i + 3].toInt() and 0xFF
                return when {
                    val1 == 0x08 && val2 == 0x00 -> AncParseResult(AncMode.OFF)
                    val1 == 0x00 && val2 == 0x01 -> AncParseResult(AncMode.TRANSPARENCY, vocalEnhancement = false)
                    val1 == 0x00 && val2 == 0x02 -> AncParseResult(AncMode.TRANSPARENCY, vocalEnhancement = true)
                    val1 == 0x00 && val2 == 0x08 -> AncParseResult(AncMode.ADAPTIVE)
                    // 降噪模式: val2==0x00 且 val1 为降噪等级值
                    val2 == 0x00 && NoiseLevel.fromValue(val1) != null ->
                        AncParseResult(AncMode.NOISE_CANCELLATION, NoiseLevel.fromValue(val1))
                    // 通用降噪 (0x02)
                    val1 == 0x02 && val2 == 0x00 ->
                        AncParseResult(AncMode.NOISE_CANCELLATION)
                    else -> null
                }
            }
        }
        return null
    }
}

/** 批量状态解析器 */
object StatusParser {

    data class StatusResult(
        val gameMode1: Boolean? = null,
        val spatialSound: Boolean? = null,
        val hiRes: Boolean? = null,
        val dualDevice: Boolean? = null,
        val autoPlayPause: Boolean? = null,
        val windowsSwiftPair: Boolean? = null,
        val gameMode2: Boolean? = null
    )

    /** 解析批量状态响应 (0x810D) */
    fun parse(packet: ParsedPacket): Pair<StatusResult, Set<Int>>? {
        if (packet.cmd != Cmd.QUERY_STATUS_RESPONSE) return null
        if (packet.payload.size < 2) return null

        // Payload 格式: [Unknown 1B] [Count] [ParamId1] [Val1] [ParamId2] [Val2] ...
        val count = packet.payload[1].toInt() and 0xFF
        var gameMode1: Boolean? = null
        var spatialSound: Boolean? = null
        var hiRes: Boolean? = null
        var dualDevice: Boolean? = null
        var autoPlayPause: Boolean? = null
        var windowsSwiftPair: Boolean? = null
        var gameMode2: Boolean? = null
        val presentParams = mutableSetOf<Int>()

        var i = 2
        while (i + 1 < packet.payload.size && (i - 2) / 2 < count) {
            val paramId = packet.payload[i].toInt() and 0xFF
            val value = packet.payload[i + 1].toInt() and 0xFF
            presentParams.add(paramId)
            when (paramId) {
                ParamId.GAME_MODE_1 -> gameMode1 = value == 0x01
                ParamId.SPATIAL_SOUND -> spatialSound = value == 0x01
                ParamId.HI_RES -> hiRes = value == 0x01
                ParamId.DUAL_DEVICE -> dualDevice = value == 0x01
                ParamId.AUTO_PLAY_PAUSE -> autoPlayPause = value == 0x01
                ParamId.WINDOWS_SWIFT_PAIR -> windowsSwiftPair = value == 0x01
                ParamId.GAME_MODE_2 -> gameMode2 = value == 0x01
            }
            i += 2
        }
        return Pair(StatusResult(gameMode1, spatialSound, hiRes, dualDevice, autoPlayPause, windowsSwiftPair, gameMode2), presentParams.toSet())
    }
}

/** 游戏模式通知解析器 */
object GameModeNotificationParser {
    /** 解析游戏模式通知 (0x0204, type=0x05) */
    fun parse(packet: ParsedPacket): Boolean? {
        if (packet.cmd != Cmd.NOTIFICATION) return null
        if (packet.payload.isEmpty()) return null
        if (packet.payload[0].toInt() and 0xFF != ReportType.GAME_MODE) return null
        if (packet.payload.size < 2) return null
        return packet.payload[1].toInt() and 0xFF == 0x01
    }
}

/** 空间音频响应解析器 */
object SpatialAudioParser {
    /** 解析空间音频设置响应 (0x8422) */
    fun parse(packet: ParsedPacket): SpatialAudioMode? {
        if (packet.cmd != Cmd.SET_SPATIAL_AUDIO_RESPONSE) return null
        if (packet.payload.isEmpty()) return null
        val mode = packet.payload[0].toInt() and 0xFF
        return SpatialAudioMode.fromValue(mode)
    }

    /** 解析空间音频通知 (0x0510) */
    fun parseNotification(packet: ParsedPacket): SpatialAudioMode? {
        if (packet.cmd != Cmd.SPATIAL_AUDIO_NOTIFICATION) return null
        if (packet.payload.isEmpty()) return null
        val mode = packet.payload[0].toInt() and 0xFF
        return SpatialAudioMode.fromValue(mode)
    }
}

/** 提示音音量解析器 */
object VolumeParser {
    /** 解析音量查询响应 (0x8130) */
    fun parseResponse(packet: ParsedPacket): Int? {
        if (packet.cmd != Cmd.VOLUME_RESPONSE) return null
        if (packet.payload.size < 2) return null
        return packet.payload[1].toInt() and 0xFF
    }

    /** 解析音量设置响应 (0x8427) */
    fun parseSetResponse(packet: ParsedPacket): Int? {
        if (packet.cmd != Cmd.SET_VOLUME_RESPONSE) return null
        if (packet.payload.size < 2) return null
        return packet.payload[1].toInt() and 0xFF
    }
}
object WearStatusParser {
    /**
     * 解析佩戴状态通知 (0x0204, ReportType=0x02)
     */
    fun parse(packet: ParsedPacket): WearStatusResult? {
        if (packet.cmd != Cmd.NOTIFICATION) return null
        if (packet.payload.isEmpty()) return null
        if (packet.payload[0].toInt() and 0xFF != ReportType.WEAR_STATUS) return null
        if (packet.payload.size < 3) return null

        // 佩戴状态格式: [0x02] [Count] [Id1] [Status1] [Id2] [Status2] ...
        val count = packet.payload[1].toInt() and 0xFF
        var left: WearStatus? = null
        var right: WearStatus? = null
        var case: WearStatus? = null

        var i = 2
        while (i + 1 < packet.payload.size && (i - 2) / 2 < count) {
            val id = packet.payload[i].toInt() and 0xFF
            val status = packet.payload[i + 1].toInt() and 0xFF
            val wearStatus = WearStatus.fromValue(status)
            when (id) {
                1 -> left = wearStatus
                2 -> right = wearStatus
                3 -> case = wearStatus
            }
            i += 2
        }
        return WearStatusResult(left, right, case)
    }
}

/** 连接设备信息 */
data class ConnectedDevice(
    val mac: String,
    val connected: Boolean,
    val active: Boolean,
    val name: String
)

/** 连接设备通知解析器 */
object ConnectedDevicesParser {
    /** 解析连接设备通知 (0x0204, ReportType=0x06) */
    fun parse(packet: ParsedPacket): List<ConnectedDevice>? {
        if (packet.cmd != Cmd.NOTIFICATION) return null
        if (packet.payload.isEmpty()) return null
        if (packet.payload[0].toInt() and 0xFF != ReportType.CONNECTED_DEVICES) return null
        if (packet.payload.size < 2) return null

        // 格式: [0x06] [Count] [Device1] [Device2] ...
        // 每个设备: [MAC 6B LE] [ProfileFlags 1B] [ConnectionState 1B] [IsActive 1B] [NameLen 1B] [Name N字节]
        val count = packet.payload[1].toInt() and 0xFF
        val devices = mutableListOf<ConnectedDevice>()
        var i = 2

        for (d in 0 until count) {
            if (i + 11 > packet.payload.size) break // 至少需要 6+1+1+1+1+1=11 字节
            // MAC: 6 字节小端序
            val macBytes = packet.payload.sliceArray(i until i + 6)
            val mac = macBytes.reversed().joinToString(":") { "%02X".format(it) }
            i += 6
            val profileFlags = packet.payload[i].toInt() and 0xFF
            i += 1
            val connectionState = packet.payload[i].toInt() and 0xFF
            i += 1
            val isActive = packet.payload[i].toInt() and 0xFF == 0x01
            i += 1
            val nameLen = packet.payload[i].toInt() and 0xFF
            i += 1
            val name = if (nameLen > 0 && i + nameLen <= packet.payload.size) {
                packet.payload.sliceArray(i until i + nameLen).decodeToString()
            } else ""
            i += nameLen

            devices.add(ConnectedDevice(
                mac = mac,
                connected = connectionState == 0x02,
                active = isActive,
                name = name
            ))
        }
        return devices
    }
}
