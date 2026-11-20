package com.boostdriver.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.boostdriver.BoostService
import com.boostdriver.BuildConfig
import com.boostdriver.BoostPrefs
import com.boostdriver.R
import com.boostdriver.ui.theme.spacing
import com.boostdriver.audio.BoostEngine
import kotlin.math.roundToInt

internal data class MediaVolume(val min: Int = 0, val max: Int = 0, val current: Int = 0)

@Composable
internal fun BoostPanel(
    media: MediaVolume,
    status: BoostService.Status,
    selectedGain: Int,
    notificationsEnabled: Boolean,
    onMediaVolume: (Int) -> Unit,
    onGain: (Int) -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
) {
    var proposedGain by rememberSaveable { mutableStateOf<Float?>(null) }
    val spacing = MaterialTheme.spacing
    Scaffold(Modifier.fillMaxSize(), contentWindowInsets = WindowInsets.safeDrawing) { insets ->
        Column(
            Modifier.padding(insets).verticalScroll(rememberScrollState()).padding(spacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Image(
                painterResource(R.drawable.boost_logo), contentDescription = null,
                modifier = Modifier.align(Alignment.CenterHorizontally).size(spacing.row),
            )
            Text(
                stringResource(R.string.app_title, stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
                Modifier.align(Alignment.CenterHorizontally).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
            val percent = if (media.max == 0) 0 else (100f * media.current / media.max).roundToInt()
            ControlLabel(R.string.volume, percent)
            AudioSlider(R.string.volume_label, percent, media.current.toFloat(), media.min..media.max,
                onValueChange = { if (it.roundToInt() != media.current) onMediaVolume(it.roundToInt()) })
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
            val canDisable = status.enabled || status.starting
            Button(
                onClick = { if (canDisable) onDisable() else onEnable() },
                enabled = canDisable || selectedGain > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(if (canDisable) R.string.disable else R.string.enable)) }
            ProjectCredits(Modifier.padding(top = spacing.large))
        }
    }
}

@Composable
private fun ProjectCredits(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var unavailable by rememberSaveable { mutableStateOf(false) }
    val repository = "https://github.com/fredjunior54/BoostDriver"
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        for ((label, url) in listOf(
            R.string.repository to repository,
            R.string.author to "https://github.com/fredjunior54",
            R.string.license to "$repository/blob/main/LICENSE",
        )) {
            TextButton(onClick = {
                unavailable = try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    false
                } catch (_: ActivityNotFoundException) {
                    true
                }
            }) { Text(stringResource(label)) }
        }
        if (unavailable) Text(stringResource(R.string.no_browser), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
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
