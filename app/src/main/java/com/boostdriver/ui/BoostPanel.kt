package com.boostdriver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.boostdriver.BoostService
import com.boostdriver.R
import com.boostdriver.ui.theme.spacing
import kotlin.math.roundToInt

internal data class MediaVolume(val min: Int = 0, val max: Int = 0, val current: Int = 0)
private enum class Dialog { None, Warning, Settings, Limit, HigherLimit, Boot }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoostPanel(
    media: MediaVolume,
    status: BoostService.Status,
    limit: Int,
    showVolume: Boolean,
    resumeOnBoot: Boolean,
    wallpaperColors: Boolean,
    supportsWallpaperColors: Boolean,
    notificationsEnabled: Boolean,
    hasAcceptedWarning: () -> Boolean,
    onDismiss: () -> Unit,
    onMediaVolume: (Int) -> Unit,
    onGain: (Int) -> Unit,
    onWarningAccepted: (Int) -> Unit,
    onShowVolume: (Boolean) -> Unit,
    onResumeOnBoot: (Boolean) -> Unit,
    onWallpaperColors: (Boolean) -> Unit,
    onLimit: (Int) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf(Dialog.None) }
    var proposedGain by rememberSaveable { mutableStateOf<Float?>(null) }
    var pendingGain by rememberSaveable { mutableStateOf(0) }
    var pendingLimit by rememberSaveable { mutableStateOf(limit) }
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
            Text(stringResource(R.string.app_name), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            if (showVolume) {
                val percent = if (media.max == 0) 0 else (100f * media.current / media.max).roundToInt()
                ControlLabel(R.string.volume, percent)
                AudioSlider(R.string.volume_label, percent, media.current.toFloat(), media.min..media.max,
                    onValueChange = { if (it.roundToInt() != media.current) onMediaVolume(it.roundToInt()) })
            }
            val currentGain = (proposedGain ?: status.gain.toFloat()).coerceIn(0f, limit.toFloat())
            ControlLabel(R.string.gain, currentGain.roundToInt())
            AudioSlider(R.string.gain_label, currentGain.roundToInt(), currentGain, 0..limit,
                onValueChange = { proposedGain = it },
                onFinished = {
                    val gain = proposedGain?.roundToInt() ?: status.gain
                    proposedGain = null
                    if (gain > 0 && !hasAcceptedWarning()) {
                        pendingGain = gain
                        dialog = Dialog.Warning
                    } else onGain(gain)
                })
            val message = when {
                status.error != 0 -> stringResource(status.error)
                status.gain == 0 -> stringResource(R.string.status_off)
                else -> stringResource(R.string.status_on, if (status.mode == 1) "LoudnessEnhancer" else "Equalizer")
            }
            Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = if (status.error != 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (status.gain > 0 && !notificationsEnabled) Text(stringResource(R.string.status_no_notification))
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { dialog = Dialog.Settings }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings))
                }
                Button(onClick = { proposedGain = null; onGain(0) }, enabled = status.gain > 0 || status.error != 0,
                    modifier = Modifier.weight(1f)) { Text(stringResource(R.string.stop)) }
            }
        }

        when (dialog) {
            Dialog.None -> Unit
            Dialog.Warning -> ConfirmDialog(R.string.warning_title, stringResource(R.string.warning),
                onCancel = { dialog = Dialog.None }, onConfirm = { dialog = Dialog.None; onWarningAccepted(pendingGain) })
            Dialog.Boot -> ConfirmDialog(R.string.boot, stringResource(R.string.boot_warning),
                onCancel = { dialog = Dialog.Settings }, onConfirm = { onResumeOnBoot(true); dialog = Dialog.Settings })
            Dialog.HigherLimit -> ConfirmDialog(R.string.warning_title, stringResource(R.string.limit_warning, 40),
                onCancel = { dialog = Dialog.Settings }, onConfirm = { onLimit(pendingLimit); dialog = Dialog.Settings })
            Dialog.Settings -> AlertDialog(
                onDismissRequest = { dialog = Dialog.None },
                title = { Text(stringResource(R.string.settings)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        SettingSwitch(R.string.show_volume, showVolume, onShowVolume)
                        SettingSwitch(R.string.boot, resumeOnBoot) { if (it) dialog = Dialog.Boot else onResumeOnBoot(false) }
                        if (supportsWallpaperColors) SettingSwitch(R.string.wallpaper_colors, wallpaperColors, onWallpaperColors)
                        TextButton(onClick = { dialog = Dialog.Limit }) { Text(stringResource(R.string.limit, limit)) }
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = Dialog.None }) { Text(stringResource(R.string.done)) } },
            )
            Dialog.Limit -> AlertDialog(
                onDismissRequest = { dialog = Dialog.Settings },
                title = { Text(stringResource(R.string.choose_limit)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                        for (choice in 10..60 step 10) {
                            RadioButtonRow(choice, limit) {
                                if (choice > 40 && choice > limit) {
                                    pendingLimit = choice
                                    dialog = Dialog.HigherLimit
                                } else {
                                    onLimit(choice)
                                    dialog = Dialog.Settings
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = Dialog.Settings }) { Text(stringResource(R.string.cancel)) } },
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
    val displayedValue = stringResource(R.string.limit_value, percent)
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

@Composable
private fun RadioButtonRow(choice: Int, limit: Int, onSelect: () -> Unit) {
    val selected = choice == limit
    Row(
        Modifier.fillMaxWidth().minimumInteractiveComponentSize().selectable(selected, role = Role.RadioButton, onClick = onSelect),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium), verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(R.string.limit_value, choice))
    }
}
