package cc.star0.zerobuds.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import cc.star0.zerobuds.protocol.BluetoothCodecManager
import cc.star0.zerobuds.protocol.RfcommController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun App(
    controller: RfcommController = RfcommController(),
    codecManager: BluetoothCodecManager = BluetoothCodecManager(),
    compatPrefsManager: CompatibilityPrefsManager = CompatibilityPrefsManager(android.app.Application()),
    themeMode: MutableState<Int> = mutableStateOf(0),
    onThemeModeChange: (Int) -> Unit = {},
    languageMode: Int = 0,
    onLanguageModeChange: (Int) -> Unit = {},
    developerMode: Boolean = false,
    onDeveloperModeChange: (Boolean) -> Unit = {},
    subscribeBroadcast: Boolean = true,
    onSubscribeBroadcastChange: (Boolean) -> Unit = {},
    predictiveBack: Boolean = true,
    onPredictiveBackChange: (Boolean) -> Unit = {}
) {
    val colorSchemeMode = when (themeMode.value) {
        1 -> ColorSchemeMode.Light
        2 -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }
    AppTheme(colorSchemeMode = colorSchemeMode) {
        MainUI(
            controller = controller,
            codecManager = codecManager,
            compatPrefsManager = compatPrefsManager,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            languageMode = languageMode,
            onLanguageModeChange = onLanguageModeChange,
            developerMode = developerMode,
            onDeveloperModeChange = onDeveloperModeChange,
            subscribeBroadcast = subscribeBroadcast,
            onSubscribeBroadcastChange = onSubscribeBroadcastChange,
            predictiveBack = predictiveBack,
            onPredictiveBackChange = onPredictiveBackChange
        )
    }
}

@Composable
fun AppTheme(
    colorSchemeMode: ColorSchemeMode = ColorSchemeMode.System,
    content: @Composable () -> Unit
) {
    val controller = androidx.compose.runtime.remember(colorSchemeMode) { ThemeController(colorSchemeMode) }
    MiuixTheme(controller = controller, content = content)
}
