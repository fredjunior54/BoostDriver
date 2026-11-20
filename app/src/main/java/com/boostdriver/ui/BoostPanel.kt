package com.boostdriver.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.boostdriver.BoostService
import com.boostdriver.BoostPrefs
import com.boostdriver.R
import com.boostdriver.ui.theme.spacing
import com.boostdriver.audio.BoostEngine
import kotlin.math.roundToInt

internal data class MediaVolume(val min: Int = 0, val max: Int = 0, val current: Int = 0)
private enum class Dialog { None, Settings, Boot }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoostPanel(
    media: MediaVolume,
    status: BoostService.Status,
    selectedGain: Int,
    showVolume: Boolean,
    resumeOnBoot: Boolean,
    wallpaperColors: Boolean,
    supportsWallpaperColors: Boolean,
    notificationsEnabled: Boolean,
    onDismiss: () -> Unit,
    onMediaVolume: (Int) -> Unit,
    onGain: (Int) -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onShowVolume: (Boolean) -> Unit,
    onResumeOnBoot: (Boolean) -> Unit,
    onWallpaperColors: (Boolean) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf(Dialog.None) }
    var proposedGain by rememberSaveable { mutableStateOf<Float?>(null) }
    val spacing = MaterialTheme.spacing
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(
                start = spacing.extraLarge, end = spacing.extraLarge, bottom = spacing.large,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.boost_logo), contentDescription = null, modifier = Modifier.size(spacing.section))
                Text(stringResource(R.string.app_name), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            }
            if (showVolume) {
                val percent = if (media.max == 0) 0 else (100f * media.current / media.max).roundToInt()
                ControlLabel(R.string.volume, percent)
                AudioSlider(R.string.volume_label, percent, media.current.toFloat(), media.min..media.max,
                    onValueChange = { if (it.roundToInt() != media.current) onMediaVolume(it.roundToInt()) })
            }
            val currentGain = (proposedGain ?: selectedGain.toFloat()).coerceIn(0f, BoostPrefs.MAX_GAIN.toFloat())
            val db = BoostEngine.millibels(currentGain.roundToInt()) / 100f
            Text(stringResource(R.string.gain_target, currentGain.roundToInt(), db), style = MaterialTheme.typography.titleMedium)
            AudioSlider(R.string.gain_label, currentGain.roundToInt(), currentGain, 0..BoostPrefs.MAX_GAIN,
                onValueChange = { proposedGain = it },
                onFinished = {
                    val gain = proposedGain?.roundToInt() ?: selectedGain
                    proposedGain = null
                    onGain(gain)
                })
            val message = when {
                status.error != 0 -> stringResource(status.error)
                status.starting -> stringResource(R.string.status_starting)
                !status.enabled -> stringResource(R.string.status_off)
                status.gain == 0 -> stringResource(R.string.status_bypass)
                else -> stringResource(if (status.scoped) R.string.status_session else R.string.status_on,
                    if (status.mode == 1) "LoudnessEnhancer" else "Equalizer")
            }
            Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = if (status.error != 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (status.enabled && !notificationsEnabled) Text(stringResource(R.string.status_no_notification))
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { dialog = Dialog.Settings }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings))
                }
                val canDisable = status.enabled || status.starting
                Button(
                    onClick = { if (canDisable) onDisable() else onEnable() },
                    enabled = canDisable || selectedGain > 0,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(if (canDisable) R.string.disable else R.string.enable)) }
            }
        }

        when (dialog) {
            Dialog.None -> Unit
            Dialog.Boot -> ConfirmDialog(R.string.boot, stringResource(R.string.boot_warning),
                onCancel = { dialog = Dialog.Settings }, onConfirm = { onResumeOnBoot(true); dialog = Dialog.Settings })
            Dialog.Settings -> AlertDialog(
                onDismissRequest = { dialog = Dialog.None },
                title = { Text(stringResource(R.string.settings)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        SettingSwitch(R.string.show_volume, showVolume, onShowVolume)
                        SettingSwitch(R.string.boot, resumeOnBoot) { if (it) dialog = Dialog.Boot else onResumeOnBoot(false) }
                        if (supportsWallpaperColors) SettingSwitch(R.string.wallpaper_colors, wallpaperColors, onWallpaperColors)
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = Dialog.None }) { Text(stringResource(R.string.done)) } },
            )
        }
    }
}

@Composable
private fun ControlLabel(resource: Int, percent: Int) {
    Text(stringResource(resource, percent), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun AudioSlider(
    label: Int, percent: Int, value: Float, range: IntRange,
    onValueChange: (Float) -> Unit, onFinished: (() -> Unit)? = null,
) {
    val name = stringResource(label)
    val displayedValue = stringResource(R.string.gain_percent, percent)
    Slider(
        value = value, onValueChange = onValueChange, onValueChangeFinished = onFinished,
        valueRange = range.first.toFloat()..maxOf(range.first + 1, range.last).toFloat(),
        enabled = range.first != range.last,
        modifier = Modifier.minimumInteractiveComponentSize().semantics {
            contentDescription = name
            stateDescription = displayedValue
        },
    )
}

@Composable
private fun ConfirmDialog(title: Int, message: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel, title = { Text(stringResource(title)) }, text = { Text(message, Modifier.verticalScroll(rememberScrollState())) },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.accept)) } },
    )
}

@Composable
private fun SettingSwitch(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().minimumInteractiveComponentSize().toggleable(checked, role = Role.Switch, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
