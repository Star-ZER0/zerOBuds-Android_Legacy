package cc.star0.zerobuds.ui

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 兼容性设置，按设备 MAC 地址绑定
 */
data class CompatibilityPrefs(
    val hideDisabled: Boolean = false,
    val hideAdaptive: Boolean = false,
    val hideVocalEnhancement: Boolean = false,
    val hideSpatialAudio: Boolean = false,
    val hideGameMode1: Boolean = false,
    val hideGameMode2: Boolean = false,
    val hidePromptVolume: Boolean = false,
    val rfcommChannel: Int = 15
)

/**
 * 兼容性设置持久化管理器
 * 使用 SharedPreferences 按 MAC 地址存储
 */
class CompatibilityPrefsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("zerobuds_compatibility", Context.MODE_PRIVATE)

    private val _prefsFlow = MutableStateFlow(CompatibilityPrefs())
    val prefsFlow: StateFlow<CompatibilityPrefs> = _prefsFlow

    private var currentMac: String = ""

    /** 切换到指定 MAC 地址的设备配置 */
    fun loadForDevice(mac: String) {
        currentMac = mac
        _prefsFlow.value = loadPrefs(mac)
    }

    /** 清除当前设备绑定（断开连接时） */
    fun clearDevice() {
        currentMac = ""
        _prefsFlow.value = CompatibilityPrefs()
    }

    /** 更新设置 */
    fun update(transform: (CompatibilityPrefs) -> CompatibilityPrefs) {
        val newPrefs = transform(_prefsFlow.value)
        _prefsFlow.value = newPrefs
        savePrefs(currentMac, newPrefs)
    }

    private fun loadPrefs(mac: String): CompatibilityPrefs {
        if (mac.isEmpty()) return CompatibilityPrefs()
        return CompatibilityPrefs(
            hideDisabled = prefs.getBoolean("${mac}_hideDisabled", false),
            hideAdaptive = prefs.getBoolean("${mac}_hideAdaptive", false),
            hideVocalEnhancement = prefs.getBoolean("${mac}_hideVocalEnhancement", false),
            hideSpatialAudio = prefs.getBoolean("${mac}_hideSpatialAudio", false),
            hideGameMode1 = prefs.getBoolean("${mac}_hideGameMode1", false),
            hideGameMode2 = prefs.getBoolean("${mac}_hideGameMode2", false),
            hidePromptVolume = prefs.getBoolean("${mac}_hidePromptVolume", false),
            rfcommChannel = prefs.getInt("${mac}_rfcommChannel", 15)
        )
    }

    private fun savePrefs(mac: String, p: CompatibilityPrefs) {
        if (mac.isEmpty()) return
        prefs.edit().apply {
            putBoolean("${mac}_hideDisabled", p.hideDisabled)
            putBoolean("${mac}_hideAdaptive", p.hideAdaptive)
            putBoolean("${mac}_hideVocalEnhancement", p.hideVocalEnhancement)
            putBoolean("${mac}_hideSpatialAudio", p.hideSpatialAudio)
            putBoolean("${mac}_hideGameMode1", p.hideGameMode1)
            putBoolean("${mac}_hideGameMode2", p.hideGameMode2)
            putBoolean("${mac}_hidePromptVolume", p.hidePromptVolume)
            putInt("${mac}_rfcommChannel", p.rfcommChannel)
            apply()
        }
    }
}
