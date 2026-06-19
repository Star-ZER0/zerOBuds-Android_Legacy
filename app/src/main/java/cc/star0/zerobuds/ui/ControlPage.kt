package cc.star0.zerobuds.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import cc.star0.zerobuds.protocol.AncMode
import cc.star0.zerobuds.protocol.BatteryParser
import cc.star0.zerobuds.protocol.BluetoothCodecManager
import cc.star0.zerobuds.protocol.ConnectedDevice
import cc.star0.zerobuds.protocol.NoiseLevel
import cc.star0.zerobuds.protocol.ParamId
import cc.star0.zerobuds.protocol.SpatialAudioMode
import cc.star0.zerobuds.protocol.WearStatus
import cc.star0.zerobuds.protocol.WearStatusResult
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ControlPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    batteryInfo: BatteryParser.BatteryResult,
    wearStatus: WearStatusResult,
    connectedDevices: List<ConnectedDevice>,
    deviceName: String,
    deviceAddress: String,
    onDeviceClick: () -> Unit,
    ancMode: AncMode,
    onAncModeChange: (AncMode) -> Unit,
    noiseLevel: NoiseLevel,
    onNoiseLevelChange: (NoiseLevel) -> Unit,
    vocalEnhancement: Boolean,
    onVocalEnhancementChange: (Boolean) -> Unit,
    gameMode1: Boolean,
    onGameMode1Change: (Boolean) -> Unit,
    gameMode2: Boolean,
    onGameMode2Change: (Boolean) -> Unit,
    autoPlayPause: Boolean,
    onAutoPlayPauseChange: (Boolean) -> Unit,
    spatialSound: Boolean,
    onSpatialSoundChange: (Boolean) -> Unit,
    hiRes: Boolean,
    onHiResChange: (Boolean) -> Unit,
    dualDevice: Boolean,
    onDualDeviceChange: (Boolean) -> Unit,
    windowsSwiftPair: Boolean,
    onWindowsSwiftPairChange: (Boolean) -> Unit,
    supportedParams: Set<Int>,
    spatialAudio: SpatialAudioMode,
    onSpatialAudioChange: (SpatialAudioMode) -> Unit,
    promptVolume: Int,
    onPromptVolumeChange: (Int) -> Unit,
    compatPrefs: CompatibilityPrefs = CompatibilityPrefs(),
    codecManager: BluetoothCodecManager = BluetoothCodecManager(),
    onRequestCodecPermission: () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 设备名称卡片
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                ArrowPreference(
                    title = deviceName,
                    summary = deviceAddress,
                    onClick = onDeviceClick
                )
            }
        }

        item { Spacer(Modifier.height(6.dp)) }

        // 状态卡片（电量 + 佩戴状态）
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                StatusDisplay(
                    batteryInfo = batteryInfo,
                    wearStatus = wearStatus,
                    connectedDevices = connectedDevices,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // 降噪切换
        item {
            SmallTitle(
                str("noise_control"),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column {
                    RadioButtonPreference(
                        title = str("anc_off"),
                        selected = ancMode == AncMode.OFF,
                        onClick = { onAncModeChange(AncMode.OFF) }
                    )
                    RadioButtonPreference(
                        title = str("noise_cancellation"),
                        summary = str("noise_cancellation_summary"),
                        selected = ancMode == AncMode.NOISE_CANCELLATION,
                        onClick = { onAncModeChange(AncMode.NOISE_CANCELLATION) }
                    )
                    RadioButtonPreference(
                        title = str("transparency"),
                        summary = str("transparency_summary"),
                        selected = ancMode == AncMode.TRANSPARENCY,
                        onClick = { onAncModeChange(AncMode.TRANSPARENCY) }
                    )
                    if (!compatPrefs.hideAdaptive) {
                        RadioButtonPreference(
                            title = str("adaptive"),
                            summary = str("adaptive_summary"),
                            selected = ancMode == AncMode.ADAPTIVE,
                            onClick = { onAncModeChange(AncMode.ADAPTIVE) }
                        )
                    }
                }
            }
        }

        // 降噪等级（仅在降噪模式下显示）
        if (ancMode == AncMode.NOISE_CANCELLATION) {
            item { Spacer(Modifier.height(6.dp)) }
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    val noiseLevels = listOf(
                        str("noise_level_smart"),
                        str("noise_level_light"),
                        str("noise_level_medium"),
                        str("noise_level_deep")
                    )
                    val levelEntries = NoiseLevel.entries
                    val currentIndex = levelEntries.indexOf(noiseLevel).coerceAtLeast(0)
                    OverlayDropdownPreference(
                        title = str("noise_level_title"),
                        items = noiseLevels,
                        selectedIndex = currentIndex,
                        onSelectedIndexChange = { index ->
                            onNoiseLevelChange(levelEntries[index])
                        }
                    )
                }
            }
        }

        // 人声增强（仅在通透模式下且未隐藏时显示）
        if (ancMode == AncMode.TRANSPARENCY && !compatPrefs.hideVocalEnhancement) {
            item { Spacer(Modifier.height(6.dp)) }
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    SwitchPreference(
                        title = str("vocal_enhancement"),
                        summary = str("vocal_enhancement_summary"),
                        checked = vocalEnhancement,
                        onCheckedChange = onVocalEnhancementChange
                    )
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // 空间音频 & 空间音效
        item {
            SmallTitle(
                str("spatial_title"),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column {
                    if (!(compatPrefs.hideDisabled && !supportedParams.contains(ParamId.SPATIAL_SOUND))) {
                        SwitchPreference(
                            title = str("spatial_sound"),
                            summary = str("spatial_sound_summary"),
                            checked = spatialSound,
                            onCheckedChange = onSpatialSoundChange,
                            enabled = supportedParams.contains(ParamId.SPATIAL_SOUND)
                        )
                    }
                    if (!compatPrefs.hideSpatialAudio) {
                        val spatialAudioItems = listOf(
                            str("spatial_audio_off"),
                            str("spatial_audio_mode1"),
                            str("spatial_audio_mode2")
                        )
                        val spatialAudioEntries = SpatialAudioMode.entries
                        val currentSpatialAudioIndex = spatialAudioEntries.indexOf(spatialAudio).coerceAtLeast(0)
                        OverlayDropdownPreference(
                            title = str("spatial_audio"),
                            items = spatialAudioItems,
                            selectedIndex = currentSpatialAudioIndex,
                            onSelectedIndexChange = { index ->
                                onSpatialAudioChange(spatialAudioEntries[index])
                            }
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // 系统 - 蓝牙音频编解码器
        item {
            SmallTitle(
                str("system_title"),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                CodecSection(
                    codecManager = codecManager,
                    onRequestCodecPermission = onRequestCodecPermission
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // 游戏模式
        item {
            SmallTitle(
                str("other_title"),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column {
                    val showGameMode1 = !(compatPrefs.hideDisabled && !supportedParams.contains(ParamId.GAME_MODE_1)) && !compatPrefs.hideGameMode1
                    val showGameMode2 = !(compatPrefs.hideDisabled && !supportedParams.contains(ParamId.GAME_MODE_2)) && !compatPrefs.hideGameMode2
                    if (showGameMode1) {
                        SwitchPreference(
                            title = str("game_mode_1"),
                            summary = str("game_mode_1_summary"),
                            checked = gameMode1,
                            onCheckedChange = onGameMode1Change,
                            enabled = supportedParams.contains(ParamId.GAME_MODE_1)
                        )
                    }
                    if (showGameMode2) {
                        SwitchPreference(
                            title = str("game_mode_2"),
                            summary = str("game_mode_2_summary"),
                            checked = gameMode2,
                            onCheckedChange = onGameMode2Change,
                            enabled = supportedParams.contains(ParamId.GAME_MODE_2)
                        )
                    }
                    if (!(compatPrefs.hideDisabled && !supportedParams.contains(ParamId.AUTO_PLAY_PAUSE))) {
                        SwitchPreference(
                            title = str("auto_play_pause"),
                            summary = str("auto_play_pause_summary"),
                            checked = autoPlayPause,
                            onCheckedChange = onAutoPlayPauseChange,
                            enabled = supportedParams.contains(ParamId.AUTO_PLAY_PAUSE)
                        )
                    }
                    if (!(compatPrefs.hideDisabled && !supportedParams.contains(ParamId.HI_RES))) {
                        SwitchPreference(
                            title = str("hi_res"),
                            summary = str("hi_res_summary"),
                            checked = hiRes,
                            onCheckedChange = onHiResChange,
                            enabled = supportedParams.contains(ParamId.HI_RES)
                        )
                    }
                    if (!(compatPrefs.hideDisabled && !supportedParams.contains(ParamId.DUAL_DEVICE))) {
                        SwitchPreference(
                            title = str("dual_device"),
                            summary = str("dual_device_summary"),
                            checked = dualDevice,
                            onCheckedChange = onDualDeviceChange,
                            enabled = supportedParams.contains(ParamId.DUAL_DEVICE)
                        )
                    }
                    if (!(compatPrefs.hideDisabled && !supportedParams.contains(ParamId.WINDOWS_SWIFT_PAIR))) {
                        SwitchPreference(
                            title = str("windows_swift_pair"),
                            summary = str("windows_swift_pair_summary"),
                            checked = windowsSwiftPair,
                            onCheckedChange = onWindowsSwiftPairChange,
                            enabled = supportedParams.contains(ParamId.WINDOWS_SWIFT_PAIR)
                        )
                    }
                    if (!compatPrefs.hidePromptVolume) {
                        val volumeItems = listOf(
                            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
                            str("prompt_volume_max")
                        )
                        // volByte: 0x01=0, 0x02=1, ..., 0x0A=9, 0x00=max
                        // index 0-9 → volByte 1-10, index 10 → volByte 0
                        val currentVolumeIndex = when {
                            promptVolume < 0 -> 0
                            promptVolume == 0 -> 10
                            else -> promptVolume - 1
                        }
                        var showMaxVolumeWarning by remember { mutableStateOf(false) }
                        var maxVolumeCountdown by remember { mutableIntStateOf(5) }

                        if (showMaxVolumeWarning) {
                            LaunchedEffect(maxVolumeCountdown) {
                                if (maxVolumeCountdown > 0) {
                                    kotlinx.coroutines.delay(1000)
                                    maxVolumeCountdown--
                                }
                            }
                            OverlayDialog(
                                show = true,
                                title = str("volume_warning_title"),
                                summary = str("volume_warning_summary"),
                                onDismissRequest = {
                                    showMaxVolumeWarning = false
                                    maxVolumeCountdown = 5
                                }
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(
                                        text = str("cancel"),
                                        onClick = {
                                            showMaxVolumeWarning = false
                                            maxVolumeCountdown = 5
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(20.dp))
                                    TextButton(
                                        text = if (maxVolumeCountdown > 0) "${maxVolumeCountdown}s" else str("continue_btn"),
                                        onClick = {
                                            if (maxVolumeCountdown <= 0) {
                                                showMaxVolumeWarning = false
                                                maxVolumeCountdown = 5
                                                onPromptVolumeChange(0)
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = maxVolumeCountdown <= 0,
                                        colors = ButtonDefaults.textButtonColorsPrimary()
                                    )
                                }
                            }
                        }

                        OverlayDropdownPreference(
                            title = str("prompt_volume"),
                            items = volumeItems,
                            selectedIndex = currentVolumeIndex,
                            onSelectedIndexChange = { index ->
                                if (index == 10) {
                                    showMaxVolumeWarning = true
                                    maxVolumeCountdown = 5
                                } else {
                                    onPromptVolumeChange(index + 1)
                                }
                            }
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatusDisplay(
    batteryInfo: BatteryParser.BatteryResult,
    wearStatus: WearStatusResult,
    connectedDevices: List<ConnectedDevice>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 佩戴状态行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusColumn(
                label = str("batt_left"),
                wearStatus = wearStatus.left,
                modifier = Modifier.weight(1f)
            )
            StatusDivider()
            StatusColumn(
                label = str("batt_case"),
                wearStatus = wearStatus.case,
                modifier = Modifier.weight(1f)
            )
            StatusDivider()
            StatusColumn(
                label = str("batt_right"),
                wearStatus = wearStatus.right,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        // 分隔线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(if (isSystemInDarkTheme()) Color(0xFF333333) else Color(0xFFEEEEEE))
        )

        Spacer(Modifier.height(8.dp))

        // 电量行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BatteryColumn(
                label = str("batt_left"),
                info = batteryInfo.left,
                modifier = Modifier.weight(1f)
            )
            StatusDivider()
            BatteryColumn(
                label = str("batt_case"),
                info = batteryInfo.case,
                modifier = Modifier.weight(1f)
            )
            StatusDivider()
            BatteryColumn(
                label = str("batt_right"),
                info = batteryInfo.right,
                modifier = Modifier.weight(1f)
            )
        }

        // 连接设备列表
        if (connectedDevices.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(if (isSystemInDarkTheme()) Color(0xFF333333) else Color(0xFFEEEEEE))
            )
            Spacer(Modifier.height(8.dp))
            ConnectedDevicesDisplay(connectedDevices)
        }
    }
}

@Composable
private fun ConnectedDevicesDisplay(devices: List<ConnectedDevice>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = str("connected_devices"),
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        devices.forEach { device ->
            val statusText = when {
                device.active -> str("device_active")
                device.connected -> str("device_connected")
                else -> str("device_disconnected")
            }
            val statusColor = when {
                device.active -> MiuixTheme.colorScheme.primary
                device.connected -> MiuixTheme.colorScheme.onBackground
                else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (device.name.isNotEmpty()) {
                    Text(
                        text = buildAnnotatedString {
                            append(device.name)
                            withStyle(SpanStyle(color = MiuixTheme.colorScheme.onSurfaceVariantSummary)) {
                                append(" (${device.mac})")
                            }
                        },
                        style = MiuixTheme.textStyles.body1,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = device.mac,
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = statusText,
                    style = MiuixTheme.textStyles.body2,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun StatusColumn(
    label: String,
    wearStatus: WearStatus?,
    modifier: Modifier = Modifier
) {
    val statusText = when (wearStatus) {
        WearStatus.WEARING -> str("wear_wearing")
        WearStatus.IN_CASE -> str("wear_in_case")
        WearStatus.REMOVED -> str("wear_removed")
        WearStatus.DISCONNECTED -> str("wear_disconnected")
        null -> "--"
    }

    val statusColor = when (wearStatus) {
        WearStatus.WEARING -> MiuixTheme.colorScheme.primary
        WearStatus.IN_CASE -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        WearStatus.REMOVED -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        WearStatus.DISCONNECTED -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        null -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = statusText,
            style = MiuixTheme.textStyles.body1,
            color = statusColor
        )
    }
}

@Composable
private fun BatteryColumn(
    label: String,
    info: BatteryParser.BatteryInfo?,
    modifier: Modifier = Modifier
) {
    val isConnected = info != null
    val level = info?.level ?: 0
    val isCharging = info?.isCharging == true
    val displayLevel = if (isConnected) {
        val chargeIndicator = if (isCharging) "+" else ""
        "$level%$chargeIndicator"
    } else "--"

    val levelColor = if (isConnected && level <= 20) {
        MiuixTheme.colorScheme.error
    } else {
        MiuixTheme.colorScheme.onBackground
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = displayLevel,
            style = MiuixTheme.textStyles.title1,
            color = levelColor
        )
    }
}

@Composable
private fun StatusDivider() {
    val dividerColor = if (isSystemInDarkTheme()) {
        Color(0xFF333333)
    } else {
        Color(0xFFEEEEEE)
    }
    Box(
        modifier = Modifier
            .width(0.5.dp)
            .height(30.dp)
            .background(dividerColor)
    )
}

@Composable
private fun CodecSection(
    codecManager: BluetoothCodecManager,
    onRequestCodecPermission: () -> Unit
) {
    val isAssociated by codecManager.isAssociated.collectAsState()
    val proxyReady by codecManager.proxyReady.collectAsState()
    val selectableCodecs by codecManager.selectableCodecs.collectAsState()
    val codecError by codecManager.codecError.collectAsState()
    val currentCodecType by codecManager.codecType.collectAsState()
    val currentSampleRate by codecManager.sampleRate.collectAsState()
    val currentBitsPerSample by codecManager.bitsPerSample.collectAsState()
    val currentChannelMode by codecManager.channelMode.collectAsState()

    val codecAvailable = proxyReady && isAssociated && selectableCodecs.isNotEmpty()
    val defaultLabel = str("bluetooth_codec_default")

    Column {
        if (!isAssociated) {
            ArrowPreference(
                title = str("bluetooth_codec_permission"),
                summary = str("bluetooth_codec_permission_summary"),
                onClick = onRequestCodecPermission
            )
        } else if (codecError != null) {
            ArrowPreference(
                title = str("bluetooth_codec_error"),
                summary = codecError ?: "",
                onClick = { codecManager.refreshCodecStatus() }
            )
        }

        // 编解码器类型
        val codecItems = selectableCodecs.map { BluetoothCodecManager.getCodecTypeName(it.codecType) }
        val currentCodecIndex = selectableCodecs.indexOfFirst { it.codecType == currentCodecType }.coerceAtLeast(0)

        OverlayDropdownPreference(
            title = str("bluetooth_codec"),
            items = codecItems.ifEmpty { listOf(defaultLabel) },
            selectedIndex = if (codecItems.isNotEmpty()) currentCodecIndex else 0,
            onSelectedIndexChange = { index ->
                if (codecItems.isNotEmpty()) {
                    val selectedType = selectableCodecs[index].codecType
                    val (sr, bps, cm) = codecManager.getDefaultParamsForCodec(selectedType)
                    codecManager.setCodecConfig(selectedType, sr, bps, cm)
                }
            },
            enabled = codecAvailable
        )

        // 采样率
        val srEntries = codecManager.getSelectableSampleRates()
        val srItems = srEntries.map { it.second }
        val currentSrIndex = srEntries.indexOfFirst { it.first == currentSampleRate }.coerceAtLeast(0)

        OverlayDropdownPreference(
            title = str("bluetooth_sample_rate"),
            items = srItems.ifEmpty { listOf(defaultLabel) },
            selectedIndex = if (srItems.isNotEmpty()) currentSrIndex else 0,
            onSelectedIndexChange = { index ->
                if (srEntries.isNotEmpty()) {
                    codecManager.setCodecConfig(currentCodecType, srEntries[index].first, currentBitsPerSample, currentChannelMode)
                }
            },
            enabled = codecAvailable
        )

        // 每样本位数
        val bpsEntries = codecManager.getSelectableBitsPerSample()
        val bpsItems = bpsEntries.map { it.second }
        val currentBpsIndex = bpsEntries.indexOfFirst { it.first == currentBitsPerSample }.coerceAtLeast(0)

        OverlayDropdownPreference(
            title = str("bluetooth_bits_per_sample"),
            items = bpsItems.ifEmpty { listOf(defaultLabel) },
            selectedIndex = if (bpsItems.isNotEmpty()) currentBpsIndex else 0,
            onSelectedIndexChange = { index ->
                if (bpsEntries.isNotEmpty()) {
                    codecManager.setCodecConfig(currentCodecType, currentSampleRate, bpsEntries[index].first, currentChannelMode)
                }
            },
            enabled = codecAvailable
        )

        // 声道模式
        val cmEntries = codecManager.getSelectableChannelModes()
        val cmItems = cmEntries.map { it.second }
        val currentCmIndex = cmEntries.indexOfFirst { it.first == currentChannelMode }.coerceAtLeast(0)

        OverlayDropdownPreference(
            title = str("bluetooth_channel_mode"),
            items = cmItems.ifEmpty { listOf(defaultLabel) },
            selectedIndex = if (cmItems.isNotEmpty()) currentCmIndex else 0,
            onSelectedIndexChange = { index ->
                if (cmEntries.isNotEmpty()) {
                    codecManager.setCodecConfig(currentCodecType, currentSampleRate, currentBitsPerSample, cmEntries[index].first)
                }
            },
            enabled = codecAvailable
        )
    }
}
