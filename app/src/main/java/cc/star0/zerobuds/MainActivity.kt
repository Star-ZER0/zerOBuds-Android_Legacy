package cc.star0.zerobuds

import android.content.Context
import android.graphics.Color
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import cc.star0.zerobuds.protocol.BluetoothCodecManager
import cc.star0.zerobuds.protocol.RfcommController
import cc.star0.zerobuds.ui.App
import cc.star0.zerobuds.ui.CompatibilityPrefsManager
import cc.star0.zerobuds.ui.LanguageManager

class MainActivity : ComponentActivity() {
    private val controller = RfcommController()
    private val codecManager = BluetoothCodecManager()
    private lateinit var compatPrefsManager: CompatibilityPrefsManager
    private var predictiveBackCallback: OnBackInvokedCallback? = null

    // CDM 关联请求的 ActivityResultLauncher
    private val associationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // 关联成功，刷新编解码器状态
            codecManager.refreshCodecStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        compatPrefsManager = CompatibilityPrefsManager(this)

        // 设置 CDM 关联回调
        codecManager.onAssociationPending = { intentSender ->
            associationLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }

        setContent {
            val prefs = remember { getSharedPreferences("zerobuds_settings", Context.MODE_PRIVATE) }
            val themeMode = remember { mutableStateOf(prefs.getInt("theme_mode", 0)) }
            val languageMode = remember { mutableStateOf(prefs.getInt("language_mode", 0)) }
            val developerMode = remember { mutableStateOf(prefs.getBoolean("developer_mode", false)) }
            val subscribeBroadcast = remember { mutableStateOf(prefs.getBoolean("subscribe_broadcast", true)) }
            val predictiveBack = remember { mutableStateOf(prefs.getBoolean("predictive_back", true)) }
            val systemDark = isSystemInDarkTheme()
            val darkMode = when (themeMode.value) {
                1 -> false
                2 -> true
                else -> systemDark
            }

            // Initialize language from prefs
            val langFromPrefs = when (languageMode.value) {
                1 -> "eng"
                2 -> "chs"
                else -> null // follow system
            }
            LanguageManager.initFromPrefs(langFromPrefs)

            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkMode },
                )
                window.isNavigationBarContrastEnforced = false
                onDispose {}
            }

            // 预测性返回控制
            DisposableEffect(predictiveBack.value) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    predictiveBackCallback?.let {
                        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
                    }
                    if (!predictiveBack.value) {
                        val callback = OnBackInvokedCallback {
                            onBackPressedDispatcher.onBackPressed()
                        }
                        onBackInvokedDispatcher.registerOnBackInvokedCallback(
                            OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback
                        )
                        predictiveBackCallback = callback
                    } else {
                        predictiveBackCallback = null
                    }
                }
                onDispose {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        predictiveBackCallback?.let {
                            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
                        }
                    }
                }
            }

            App(
                controller = controller,
                codecManager = codecManager,
                compatPrefsManager = compatPrefsManager,
                themeMode = themeMode,
                onThemeModeChange = {
                    themeMode.value = it
                    prefs.edit().putInt("theme_mode", it).apply()
                },
                languageMode = languageMode.value,
                onLanguageModeChange = {
                    languageMode.value = it
                    prefs.edit().putInt("language_mode", it).apply()
                    val lang = when (it) {
                        1 -> "eng"
                        2 -> "chs"
                        else -> null
                    }
                    if (lang != null) {
                        LanguageManager.setLanguage(lang)
                    } else {
                        LanguageManager.setLanguage(
                            if (java.util.Locale.getDefault().language.startsWith("zh")) "chs" else "eng"
                        )
                    }
                },
                developerMode = developerMode.value,
                onDeveloperModeChange = {
                    developerMode.value = it
                    prefs.edit().putBoolean("developer_mode", it).apply()
                },
                subscribeBroadcast = subscribeBroadcast.value,
                onSubscribeBroadcastChange = {
                    subscribeBroadcast.value = it
                    prefs.edit().putBoolean("subscribe_broadcast", it).apply()
                },
                predictiveBack = predictiveBack.value,
                onPredictiveBackChange = {
                    predictiveBack.value = it
                    prefs.edit().putBoolean("predictive_back", it).apply()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (controller.connectionState.value == RfcommController.ConnectionState.CONNECTED) {
            controller.refreshStatus()
            if (codecManager.proxyReady.value) {
                codecManager.refreshCodecStatus()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.disconnect()
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        adapter?.let { codecManager.releaseProxy(it) }
    }

}
