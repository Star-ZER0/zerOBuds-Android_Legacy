package cc.star0.zerobuds.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun CompatibilityPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    prefs: CompatibilityPrefs,
    onPrefsChange: ((CompatibilityPrefs) -> CompatibilityPrefs) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            SmallTitle(
                str("compatibility_connection"),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                val channelOptions = listOf("OPPO 方案1", "OPPO 方案2")
                val channelValues = listOf(15, 5)
                val currentIndex = channelValues.indexOf(prefs.rfcommChannel).coerceAtLeast(0)

                OverlayDropdownPreference(
                    title = str("compatibility_scheme"),
                    items = channelOptions,
                    selectedIndex = currentIndex,
                    onSelectedIndexChange = { index ->
                        onPrefsChange { it.copy(rfcommChannel = channelValues[index]) }
                    }
                )
            }
        }

        item {
            SmallTitle(
                str("compatibility_display"),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                SwitchPreference(
                    title = str("compatibility_hide_disabled"),
                    summary = str("compatibility_hide_disabled_summary"),
                    checked = prefs.hideDisabled,
                    onCheckedChange = { onPrefsChange { it.copy(hideDisabled = it.hideDisabled.not()) } }
                )
            }
        }

        item {
            SmallTitle(
                str("compatibility_manual_hide"),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column {
                    SwitchPreference(
                        title = str("compatibility_hide_adaptive"),
                        summary = str("compatibility_hide_adaptive_summary"),
                        checked = prefs.hideAdaptive,
                        onCheckedChange = { onPrefsChange { it.copy(hideAdaptive = it.hideAdaptive.not()) } }
                    )
                    SwitchPreference(
                        title = str("compatibility_hide_vocal_enhancement"),
                        summary = str("compatibility_hide_vocal_enhancement_summary"),
                        checked = prefs.hideVocalEnhancement,
                        onCheckedChange = { onPrefsChange { it.copy(hideVocalEnhancement = it.hideVocalEnhancement.not()) } }
                    )
                    SwitchPreference(
                        title = str("compatibility_hide_spatial_audio"),
                        summary = str("compatibility_hide_spatial_audio_summary"),
                        checked = prefs.hideSpatialAudio,
                        onCheckedChange = { onPrefsChange { it.copy(hideSpatialAudio = it.hideSpatialAudio.not()) } }
                    )
                    SwitchPreference(
                        title = str("compatibility_hide_game_mode_1"),
                        summary = str("compatibility_hide_game_mode_1_summary"),
                        checked = prefs.hideGameMode1,
                        onCheckedChange = { onPrefsChange { it.copy(hideGameMode1 = it.hideGameMode1.not()) } }
                    )
                    SwitchPreference(
                        title = str("compatibility_hide_game_mode_2"),
                        summary = str("compatibility_hide_game_mode_2_summary"),
                        checked = prefs.hideGameMode2,
                        onCheckedChange = { onPrefsChange { it.copy(hideGameMode2 = it.hideGameMode2.not()) } }
                    )
                    SwitchPreference(
                        title = str("compatibility_hide_prompt_volume"),
                        summary = str("compatibility_hide_prompt_volume_summary"),
                        checked = prefs.hidePromptVolume,
                        onCheckedChange = { onPrefsChange { it.copy(hidePromptVolume = it.hidePromptVolume.not()) } }
                    )
                }
            }
        }
    }
}
