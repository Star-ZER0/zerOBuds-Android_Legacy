package cc.star0.zerobuds.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import cc.star0.zerobuds.protocol.AncMode
import cc.star0.zerobuds.protocol.BluetoothCodecManager
import cc.star0.zerobuds.protocol.NoiseLevel
import cc.star0.zerobuds.protocol.RfcommController
import cc.star0.zerobuds.protocol.SpatialAudioMode
import cc.star0.zerobuds.protocol.WearStatusResult
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.utils.overScrollVertical

sealed interface Screen : NavKey {
    data object Home : Screen
    data object Settings : Screen
    data object Compatibility : Screen
    data object Log : Screen
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainUI(
    controller: RfcommController,
    codecManager: BluetoothCodecManager,
    compatPrefsManager: CompatibilityPrefsManager,
    themeMode: androidx.compose.runtime.MutableState<Int> = remember { mutableStateOf(0) },
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
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val context = LocalContext.current
    val connState by controller.connectionState.collectAsState()
    val batteryInfo by controller.batteryInfo.collectAsState()
    val ancMode by controller.ancMode.collectAsState()
    val noiseLevel by controller.noiseLevel.collectAsState()
    val vocalEnhancement by controller.vocalEnhancement.collectAsState()
    val gameMode1 by controller.gameMode1.collectAsState()
    val gameMode2 by controller.gameMode2.collectAsState()
    val autoPlayPause by controller.autoPlayPause.collectAsState()
    val spatialSound by controller.spatialSound.collectAsState()
    val hiRes by controller.hiRes.collectAsState()
    val dualDevice by controller.dualDevice.collectAsState()
    val windowsSwiftPair by controller.windowsSwiftPair.collectAsState()
    val supportedParams by controller.supportedParams.collectAsState()
    val spatialAudio by controller.spatialAudio.collectAsState()
    val promptVolume by controller.promptVolume.collectAsState()
    val wearStatus by controller.wearStatus.collectAsState()
    val connectedDevices by controller.connectedDevices.collectAsState()
    val deviceName by controller.deviceName.collectAsState()
    val deviceAddress by controller.deviceAddress.collectAsState()
    val logEntries by controller.logEntries.collectAsState()

    val isConnected = connState == RfcommController.ConnectionState.CONNECTED
    val isConnecting = connState == RfcommController.ConnectionState.CONNECTING
    val isError = connState == RfcommController.ConnectionState.ERROR

    val compatPrefs by compatPrefsManager.prefsFlow.collectAsState()

    // 当设备 MAC 变化时加载对应的兼容性设置
    LaunchedEffect(deviceAddress) {
        if (deviceAddress.isNotEmpty()) {
            compatPrefsManager.loadForDevice(deviceAddress)
        } else {
            compatPrefsManager.clearDevice()
        }
    }

    LaunchedEffect(developerMode) {
        controller.setDeveloperMode(developerMode)
    }

    LaunchedEffect(compatPrefs.rfcommChannel) {
        controller.rfcommChannel = compatPrefs.rfcommChannel
    }

    LaunchedEffect(subscribeBroadcast) {
        controller.subscribeBroadcast = subscribeBroadcast
    }

    // 当设备连接时初始化蓝牙编解码器管理器
    LaunchedEffect(isConnected, deviceAddress) {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager?.adapter
        if (isConnected && adapter != null && deviceAddress.isNotEmpty()) {
            try {
                val device = adapter.getRemoteDevice(deviceAddress)
                codecManager.initProxy(context, adapter, device)
            } catch (_: Exception) {}
            // 检查 CDM 关联状态
            val deviceManager = context.getSystemService(CompanionDeviceManager::class.java)
            if (deviceManager != null) {
                codecManager.checkAssociation(deviceManager, deviceAddress)
            }
        }
        if (!isConnected) {
            adapter?.let { codecManager.releaseProxy(it) }
            codecManager.reset()
        }
    }

    val entryProvider = entryProvider<Screen> {
        entry<Screen.Home> {
            val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = str("app_name"),
                        scrollBehavior = topAppBarScrollBehavior,
                        actions = {
                            if (isConnected) {
                                IconButton(onClick = { controller.reconnect() }) {
                                    Icon(imageVector = MiuixIcons.Refresh, contentDescription = "Reconnect")
                                }
                            }
                            if (developerMode) {
                                IconButton(onClick = { backStack.add(Screen.Log) }) {
                                    Icon(imageVector = MiuixIcons.Playlist, contentDescription = "Log")
                                }
                            }
                            IconButton(onClick = { backStack.add(Screen.Settings) }) {
                                Icon(imageVector = MiuixIcons.Settings, contentDescription = "Settings")
                            }
                        }
                    )
                }
            ) { padding ->
                AnimatedContent(
                    targetState = when {
                        isConnected -> "control"
                        isConnecting -> "connecting"
                        isError -> "error"
                        else -> "picker"
                    },
                    label = "MainPageAnim"
                ) { state ->
                    when (state) {
                        "control" -> ControlPage(
                            modifier = Modifier
                                .overScrollVertical()
                                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                            contentPadding = padding,
                            batteryInfo = batteryInfo,
                            wearStatus = wearStatus,
                            connectedDevices = connectedDevices,
                            deviceName = deviceName,
                            deviceAddress = deviceAddress,
                            onDeviceClick = { controller.disconnect() },
                            ancMode = ancMode,
                            onAncModeChange = { controller.setAncMode(it) },
                            noiseLevel = noiseLevel,
                            onNoiseLevelChange = { controller.setNoiseLevel(it) },
                            vocalEnhancement = vocalEnhancement,
                            onVocalEnhancementChange = { controller.setVocalEnhancement(it) },
                            gameMode1 = gameMode1,
                            onGameMode1Change = { controller.setGameMode1(it) },
                            gameMode2 = gameMode2,
                            onGameMode2Change = { controller.setGameMode2(it) },
                            autoPlayPause = autoPlayPause,
                            onAutoPlayPauseChange = { controller.setAutoPlayPause(it) },
                            spatialSound = spatialSound,
                            onSpatialSoundChange = { controller.setSpatialSound(it) },
                            hiRes = hiRes,
                            onHiResChange = { controller.setHiRes(it) },
                            dualDevice = dualDevice,
                            onDualDeviceChange = { controller.setDualDevice(it) },
                            windowsSwiftPair = windowsSwiftPair,
                            onWindowsSwiftPairChange = { controller.setWindowsSwiftPair(it) },
                            supportedParams = supportedParams,
                            spatialAudio = spatialAudio,
                            onSpatialAudioChange = { controller.setSpatialAudio(it) },
                            promptVolume = promptVolume,
                            onPromptVolumeChange = { controller.setPromptVolume(it) },
                            compatPrefs = compatPrefs,
                            codecManager = codecManager,
                            onRequestCodecPermission = {
                                val deviceManager = context.getSystemService(CompanionDeviceManager::class.java)
                                if (deviceManager != null && deviceAddress.isNotEmpty()) {
                                    codecManager.requestAssociation(deviceManager, deviceAddress)
                                }
                            }
                        )
                        "connecting" -> Box(Modifier.padding(padding).fillMaxSize()) { ConnectingPage() }
                        "error" -> Box(Modifier.padding(padding).fillMaxSize()) {
                            ErrorPage(
                                onRetry = { controller.disconnect() },
                                onSettingsClick = { backStack.add(Screen.Settings) }
                            )
                        }
                        else -> Box(Modifier.padding(padding).fillMaxSize()) {
                            DevicePickerPage(onDeviceSelected = { controller.connect(it) })
                        }
                    }
                }
            }
        }
        entry<Screen.Settings> {
            val settingsScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = str("settings"),
                        largeTitle = str("settings"),
                        scrollBehavior = settingsScrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = { backStack.removeLast() }) {
                                Icon(imageVector = MiuixIcons.Back, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { padding ->
                SettingsPage(
                    modifier = Modifier
                        .overScrollVertical()
                        .nestedScroll(settingsScrollBehavior.nestedScrollConnection),
                    contentPadding = padding,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    developerMode = developerMode,
                    onDeveloperModeChange = onDeveloperModeChange,
                    languageMode = languageMode,
                    onLanguageModeChange = onLanguageModeChange,
                    subscribeBroadcast = subscribeBroadcast,
                    onSubscribeBroadcastChange = onSubscribeBroadcastChange,
                    hasDevice = deviceAddress.isNotEmpty(),
                    onCompatibilityClick = { backStack.add(Screen.Compatibility) },
                    predictiveBack = predictiveBack,
                    onPredictiveBackChange = onPredictiveBackChange
                )
            }
        }
        entry<Screen.Compatibility> {
            val compatScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = str("compatibility"),
                        scrollBehavior = compatScrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = { backStack.removeLast() }) {
                                Icon(imageVector = MiuixIcons.Back, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { padding ->
                CompatibilityPage(
                    modifier = Modifier
                        .overScrollVertical()
                        .nestedScroll(compatScrollBehavior.nestedScrollConnection),
                    contentPadding = padding,
                    prefs = compatPrefs,
                    onPrefsChange = { compatPrefsManager.update(it) }
                )
            }
        }
        entry<Screen.Log> {
            val logScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = str("log_title"),
                        largeTitle = str("log_title"),
                        scrollBehavior = logScrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = { backStack.removeLast() }) {
                                Icon(imageVector = MiuixIcons.Back, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { padding ->
                LogPage(
                    modifier = Modifier
                        .overScrollVertical()
                        .nestedScroll(logScrollBehavior.nestedScrollConnection),
                    contentPadding = padding,
                    logEntries = logEntries,
                    onSendHex = { controller.sendRawHex(it) },
                    onClearLog = { controller.clearLog() }
                )
            }
        }
    }

    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryProvider = entryProvider
    )

    NavDisplay(
        entries = entries,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLast()
            } else {
                (context as? Activity)?.finish()
            }
        }
    )

    // 设置失败弹窗
    val settingError by controller.settingError.collectAsState()
    if (settingError != null) {
        OverlayDialog(
            show = true,
            title = str("setting_failed"),
            summary = str("setting_failed_summary", settingError!!),
            onDismissRequest = { controller.clearSettingError() }
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    text = str("ok"),
                    onClick = { controller.clearSettingError() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
fun ConnectingPage() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            InfiniteProgressIndicator()
            Text(str("connecting"), modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
fun ErrorPage(onRetry: () -> Unit, onSettingsClick: () -> Unit = {}) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(str("connect_failed"), color = Color(0xFFFF3B30))
            TextButton(
                text = str("retry"),
                onClick = onRetry,
                modifier = Modifier.padding(top = 12.dp)
            )
            TextButton(
                text = str("settings"),
                onClick = onSettingsClick,
                modifier = Modifier.padding(top = 4.dp),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}
