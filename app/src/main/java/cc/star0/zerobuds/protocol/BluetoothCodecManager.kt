package cc.star0.zerobuds.protocol

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * 蓝牙音频编解码器管理器
 * 通过 CompanionDeviceManager 关联设备获取权限，
 * 再通过反射调用 BluetoothA2dp 隐藏 API 实现编解码器配置
 */
@SuppressLint("MissingPermission")
class BluetoothCodecManager {

    companion object {
        private const val TAG = "zerOBuds-Codec"

        // 编解码器类型 (BluetoothCodecConfig.SOURCE_CODEC_TYPE_*)
        const val CODEC_TYPE_SBC = 0
        const val CODEC_TYPE_AAC = 1
        const val CODEC_TYPE_APTX = 2
        const val CODEC_TYPE_APTX_HD = 3
        const val CODEC_TYPE_LDAC = 4
        const val CODEC_TYPE_LC3 = 5
        const val CODEC_TYPE_APTX_ADAPTIVE = 6
        const val CODEC_TYPE_LHDC = 8
        const val CODEC_TYPE_LHDC_V5 = 19

        // 采样率 (位掩码)
        const val SAMPLE_RATE_NONE = 0
        const val SAMPLE_RATE_44100 = 1 shl 0
        const val SAMPLE_RATE_48000 = 1 shl 1
        const val SAMPLE_RATE_88200 = 1 shl 2
        const val SAMPLE_RATE_96000 = 1 shl 3
        const val SAMPLE_RATE_176400 = 1 shl 4
        const val SAMPLE_RATE_192000 = 1 shl 5

        // 每样本位数 (位掩码)
        const val BITS_PER_SAMPLE_NONE = 0
        const val BITS_PER_SAMPLE_16 = 1 shl 0
        const val BITS_PER_SAMPLE_24 = 1 shl 1
        const val BITS_PER_SAMPLE_32 = 1 shl 2

        // 声道模式 (位掩码)
        const val CHANNEL_MODE_NONE = 0
        const val CHANNEL_MODE_MONO = 1 shl 0
        const val CHANNEL_MODE_STEREO = 1 shl 1

        // 优先级
        const val CODEC_PRIORITY_HIGHEST = 1000 * 1000

        // LDAC 质量
        const val LDAC_QUALITY_ABR = 1001

        // 编解码器名称映射
        private val CODEC_NAMES = mapOf(
            CODEC_TYPE_SBC to "SBC",
            CODEC_TYPE_AAC to "AAC",
            CODEC_TYPE_APTX to "aptX",
            CODEC_TYPE_APTX_HD to "aptX HD",
            CODEC_TYPE_LDAC to "LDAC",
            CODEC_TYPE_LC3 to "LC3",
            CODEC_TYPE_APTX_ADAPTIVE to "aptX Adaptive",
            CODEC_TYPE_LHDC to "LHDC",
            CODEC_TYPE_LHDC_V5 to "LHDC V5"
        )

        // 采样率映射 (位掩码 → 显示名称)
        private val SAMPLE_RATE_NAMES = linkedMapOf(
            SAMPLE_RATE_44100 to "44.1 kHz",
            SAMPLE_RATE_48000 to "48.0 kHz",
            SAMPLE_RATE_88200 to "88.2 kHz",
            SAMPLE_RATE_96000 to "96.0 kHz",
            SAMPLE_RATE_176400 to "176.4 kHz",
            SAMPLE_RATE_192000 to "192.0 kHz"
        )

        // 位深映射
        private val BPS_NAMES = linkedMapOf(
            BITS_PER_SAMPLE_16 to "16 bit",
            BITS_PER_SAMPLE_24 to "24 bit",
            BITS_PER_SAMPLE_32 to "32 bit"
        )

        // 声道模式映射
        private val CHANNEL_MODE_NAMES = linkedMapOf(
            CHANNEL_MODE_MONO to "Mono",
            CHANNEL_MODE_STEREO to "Stereo"
        )

        fun getCodecTypeName(type: Int): String =
            CODEC_NAMES[type] ?: "Unknown($type)"

        /** 从位掩码中提取第一个匹配的标志位 */
        private fun firstFlag(mask: Int, vararg flags: Int): Int {
            for (flag in flags) {
                if (mask and flag != 0) return flag
            }
            return 0
        }

        /** 从位掩码生成可选列表 */
        private fun <T> selectableList(mask: Int, mapping: LinkedHashMap<Int, T>): List<Pair<Int, T>> {
            return mapping.entries
                .filter { (flag, _) -> mask and flag != 0 }
                .map { (flag, name) -> flag to name }
        }
    }

    data class CodecCapability(
        val codecType: Int,
        val sampleRates: Int,
        val bitsPerSample: Int,
        val channelMode: Int,
        val codecSpecific1: Long = 0
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var a2dpProxy: Any? = null
    private var currentDevice: BluetoothDevice? = null
    private var currentDeviceAddress: String = ""

    // 是否已通过 CompanionDeviceManager 关联
    private val _isAssociated = MutableStateFlow(false)
    val isAssociated: StateFlow<Boolean> = _isAssociated

    // 是否已获取 A2DP 代理
    private val _proxyReady = MutableStateFlow(false)
    val proxyReady: StateFlow<Boolean> = _proxyReady

    // 错误信息
    private val _codecError = MutableStateFlow<String?>(null)
    val codecError: StateFlow<String?> = _codecError

    // 当前编解码器配置
    private val _codecType = MutableStateFlow(CODEC_TYPE_SBC)
    val codecType: StateFlow<Int> = _codecType

    private val _sampleRate = MutableStateFlow(SAMPLE_RATE_NONE)
    val sampleRate: StateFlow<Int> = _sampleRate

    private val _bitsPerSample = MutableStateFlow(BITS_PER_SAMPLE_NONE)
    val bitsPerSample: StateFlow<Int> = _bitsPerSample

    private val _channelMode = MutableStateFlow(CHANNEL_MODE_NONE)
    val channelMode: StateFlow<Int> = _channelMode

    // 可选编解码器能力列表
    private val _selectableCodecs = MutableStateFlow<List<CodecCapability>>(emptyList())
    val selectableCodecs: StateFlow<List<CodecCapability>> = _selectableCodecs

    // CDM 关联请求回调
    var onAssociationPending: ((IntentSender) -> Unit)? = null

    // ---- 反射辅助方法 ----

    /** 绕过 Android 隐藏 API 限制 */
    private fun exemptHiddenApis() {
        val result = HiddenApiBypass.setHiddenApiExemptions("")
        Log.d(TAG, "HiddenApiBypass result: $result")
    }

    /** 通过反射调用对象的指定 getter 方法 */
    private fun Any.getInt(name: String): Int =
        javaClass.getMethod(name).invoke(this) as Int

    private fun Any.getLong(name: String): Long =
        javaClass.getMethod(name).invoke(this) as Long

    private fun Any.getObj(name: String): Any? =
        javaClass.getMethod(name).invoke(this)

    private fun Any.getObjList(name: String): List<*>? {
        val result = javaClass.getMethod(name).invoke(this)
        return result as? List<*>
    }

    // ---- 公开方法 ----

    /** 检查设备是否已通过 CompanionDeviceManager 关联 */
    fun checkAssociation(deviceManager: CompanionDeviceManager, deviceAddress: String) {
        currentDeviceAddress = deviceAddress
        _isAssociated.value = deviceManager.myAssociations.any {
            it.deviceMacAddress.toString().equals(deviceAddress, ignoreCase = true)
        }
        Log.d(TAG, "CDM association for $deviceAddress: ${_isAssociated.value}")
    }

    /** 请求通过 CompanionDeviceManager 关联设备 */
    fun requestAssociation(deviceManager: CompanionDeviceManager, deviceAddress: String) {
        currentDeviceAddress = deviceAddress
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(deviceAddress).build())
            .setSingleDevice(true)
            .build()

        deviceManager.associate(request, Runnable::run, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                Log.d(TAG, "CDM association pending")
                onAssociationPending?.invoke(intentSender)
            }

            override fun onAssociationCreated(info: android.companion.AssociationInfo) {
                Log.d(TAG, "CDM association created: ${info.deviceMacAddress}")
                _isAssociated.value = true
                refreshCodecStatus()
            }

            override fun onFailure(errorMessage: CharSequence?) {
                Log.e(TAG, "CDM association failed: $errorMessage")
            }
        })
    }

    /** 初始化 A2DP 代理 */
    fun initProxy(context: Context, adapter: BluetoothAdapter, device: BluetoothDevice) {
        currentDevice = device
        currentDeviceAddress = device.address
        _codecError.value = null
        exemptHiddenApis()
        try {
            val result = adapter.getProfileProxy(
                context,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        a2dpProxy = proxy
                        _proxyReady.value = true
                        Log.d(TAG, "A2DP proxy connected")
                        refreshCodecStatus()
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        a2dpProxy = null
                        _proxyReady.value = false
                        Log.d(TAG, "A2DP proxy disconnected")
                    }
                },
                BluetoothProfile.A2DP
            )
            if (!result) {
                _codecError.value = "getProfileProxy returned false"
                Log.e(TAG, "getProfileProxy returned false")
            }
        } catch (e: SecurityException) {
            _codecError.value = "SecurityException: ${e.message}"
            Log.e(TAG, "SecurityException getting A2DP proxy", e)
        } catch (e: Exception) {
            _codecError.value = "Exception: ${e.message}"
            Log.e(TAG, "Failed to get A2DP proxy", e)
        }
    }

    /** 刷新当前编解码器状态 */
    fun refreshCodecStatus() {
        val proxy = a2dpProxy ?: run {
            Log.w(TAG, "refreshCodecStatus: proxy is null")
            return
        }
        val device = currentDevice ?: run {
            Log.w(TAG, "refreshCodecStatus: device is null")
            return
        }
        scope.launch {
            try {
                val codecStatus = proxy.javaClass.getMethod("getCodecStatus", BluetoothDevice::class.java)
                    .invoke(proxy, device) ?: run {
                    Log.w(TAG, "getCodecStatus returned null")
                    return@launch
                }

                // 当前编解码器配置
                codecStatus.getObj("getCodecConfig")?.let { config ->
                    _codecType.value = config.getInt("getCodecType")
                    _sampleRate.value = config.getInt("getSampleRate")
                    _bitsPerSample.value = config.getInt("getBitsPerSample")
                    _channelMode.value = config.getInt("getChannelMode")
                }

                // 可选编解码器能力
                val capabilities = codecStatus.getObjList("getCodecsSelectableCapabilities")
                    ?.filterNotNull()
                    ?.map { config ->
                        CodecCapability(
                            codecType = config.getInt("getCodecType"),
                            sampleRates = config.getInt("getSampleRate"),
                            bitsPerSample = config.getInt("getBitsPerSample"),
                            channelMode = config.getInt("getChannelMode"),
                            codecSpecific1 = config.getLong("getCodecSpecific1")
                        )
                    } ?: emptyList()
                _selectableCodecs.value = capabilities

                Log.d(TAG, "Codec: type=${_codecType.value}, sr=${_sampleRate.value}, bps=${_bitsPerSample.value}, cm=${_channelMode.value}, selectable=${capabilities.size}")
            } catch (e: SecurityException) {
                _codecError.value = "SecurityException: ${e.message}"
                Log.e(TAG, "SecurityException in refreshCodecStatus", e)
            } catch (e: NoSuchMethodException) {
                _codecError.value = "NoSuchMethodException: ${e.message}"
                Log.e(TAG, "NoSuchMethodException in refreshCodecStatus", e)
            } catch (e: Exception) {
                _codecError.value = "Exception: ${e.message}"
                Log.e(TAG, "Failed to refresh codec status", e)
            }
        }
    }

    /** 设置编解码器配置 */
    fun setCodecConfig(codecType: Int, sampleRate: Int, bitsPerSample: Int, channelMode: Int) {
        val proxy = a2dpProxy ?: return
        val device = currentDevice ?: return
        scope.launch {
            try {
                val codecConfigClass = Class.forName("android.bluetooth.BluetoothCodecConfig")
                val codecConfig = codecConfigClass.getConstructor(
                    Int::class.java, Int::class.java, Int::class.java,
                    Int::class.java, Int::class.java,
                    Long::class.java, Long::class.java, Long::class.java, Long::class.java
                ).newInstance(
                    codecType, CODEC_PRIORITY_HIGHEST, sampleRate, bitsPerSample, channelMode,
                    if (codecType == CODEC_TYPE_LDAC) LDAC_QUALITY_ABR.toLong() else 0L,
                    0L, 0L, 0L
                )

                proxy.javaClass.getMethod("setCodecConfigPreference", BluetoothDevice::class.java, codecConfigClass)
                    .invoke(proxy, device, codecConfig)

                _codecType.value = codecType
                _sampleRate.value = sampleRate
                _bitsPerSample.value = bitsPerSample
                _channelMode.value = channelMode

                Log.d(TAG, "Codec set: type=$codecType, sr=$sampleRate, bps=$bitsPerSample, cm=$channelMode")
                delay(500)
                refreshCodecStatus()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set codec config", e)
            }
        }
    }

    /** 释放 A2DP 代理 */
    fun releaseProxy(adapter: BluetoothAdapter) {
        a2dpProxy?.let {
            try {
                adapter.closeProfileProxy(BluetoothProfile.A2DP, it as BluetoothProfile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close A2DP proxy", e)
            }
        }
        a2dpProxy = null
        _proxyReady.value = false
        currentDevice = null
    }

    /** 重置状态 */
    fun reset() {
        _codecType.value = CODEC_TYPE_SBC
        _sampleRate.value = SAMPLE_RATE_NONE
        _bitsPerSample.value = BITS_PER_SAMPLE_NONE
        _channelMode.value = CHANNEL_MODE_NONE
        _selectableCodecs.value = emptyList()
        _isAssociated.value = false
        _codecError.value = null
    }

    // ---- UI 辅助方法 ----

    /** 当前编解码器支持的可选采样率 */
    fun getSelectableSampleRates(): List<Pair<Int, String>> {
        val mask = _selectableCodecs.value.find { it.codecType == _codecType.value }?.sampleRates ?: 0
        return selectableList(mask, SAMPLE_RATE_NAMES)
    }

    /** 当前编解码器支持的可选位深 */
    fun getSelectableBitsPerSample(): List<Pair<Int, String>> {
        val mask = _selectableCodecs.value.find { it.codecType == _codecType.value }?.bitsPerSample ?: 0
        return selectableList(mask, BPS_NAMES)
    }

    /** 当前编解码器支持的可选声道模式 */
    fun getSelectableChannelModes(): List<Pair<Int, String>> {
        val mask = _selectableCodecs.value.find { it.codecType == _codecType.value }?.channelMode ?: 0
        return selectableList(mask, CHANNEL_MODE_NAMES)
    }

    /** 切换编解码器类型时，获取该编解码器的默认参数 */
    fun getDefaultParamsForCodec(codecType: Int): Triple<Int, Int, Int> {
        val cap = _selectableCodecs.value.find { it.codecType == codecType }
        val sr = firstFlag(
            cap?.sampleRates ?: 0,
            SAMPLE_RATE_44100, SAMPLE_RATE_48000, SAMPLE_RATE_88200,
            SAMPLE_RATE_96000, SAMPLE_RATE_176400, SAMPLE_RATE_192000
        )
        val bps = firstFlag(
            cap?.bitsPerSample ?: 0,
            BITS_PER_SAMPLE_16, BITS_PER_SAMPLE_24, BITS_PER_SAMPLE_32
        )
        val cm = firstFlag(
            cap?.channelMode ?: 0,
            CHANNEL_MODE_STEREO, CHANNEL_MODE_MONO
        )
        return Triple(sr, bps, cm)
    }
}
