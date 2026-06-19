package cc.star0.zerobuds.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    themeMode: MutableState<Int>,
    onThemeModeChange: (Int) -> Unit,
    developerMode: Boolean = false,
    onDeveloperModeChange: (Boolean) -> Unit = {},
    languageMode: Int = 0,
    onLanguageModeChange: (Int) -> Unit = {},
    subscribeBroadcast: Boolean = true,
    onSubscribeBroadcastChange: (Boolean) -> Unit = {},
    hasDevice: Boolean = false,
    onCompatibilityClick: () -> Unit = {},
    predictiveBack: Boolean = true,
    onPredictiveBackChange: (Boolean) -> Unit = {}
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                ArrowPreference(
                    title = str("compatibility"),
                    summary = if (hasDevice) str("compatibility_summary") else str("compatibility_summary_disconnected"),
                    onClick = onCompatibilityClick,
                    enabled = hasDevice
                )
            }
        }
        item {
            SmallTitle(
                str("settings"),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column {
                    OverlayDropdownPreference(
                        title = str("language_title"),
                        items = listOf(
                            str("language_follow_system"),
                            str("language_english"),
                            str("language_chinese")
                        ),
                        selectedIndex = languageMode,
                        onSelectedIndexChange = onLanguageModeChange
                    )
                    OverlayDropdownPreference(
                        title = str("theme_title"),
                        items = listOf(
                            str("theme_follow_system"),
                            str("theme_light"),
                            str("theme_dark")
                        ),
                        selectedIndex = themeMode.value,
                        onSelectedIndexChange = onThemeModeChange
                    )
                    SwitchPreference(
                        title = str("predictive_back"),
                        summary = str("predictive_back_summary"),
                        checked = predictiveBack,
                        onCheckedChange = onPredictiveBackChange
                    )
                }
            }
        }

        item {
            SmallTitle(
                str("dev_title"),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column {
                    SwitchPreference(
                        title = str("developer_mode"),
                        summary = str("developer_mode_summary"),
                        checked = developerMode,
                        onCheckedChange = onDeveloperModeChange
                    )
                    SwitchPreference(
                        title = str("subscribe_broadcast"),
                        summary = str("subscribe_broadcast_summary"),
                        checked = subscribeBroadcast,
                        onCheckedChange = onSubscribeBroadcastChange
                    )
                }
            }
        }
    }
}
