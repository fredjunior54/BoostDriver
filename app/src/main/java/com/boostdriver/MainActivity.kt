package com.boostdriver

import android.Manifest
import android.app.NotificationManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.boostdriver.ui.BoostPanel
import com.boostdriver.ui.MediaVolume
import com.boostdriver.ui.theme.BoostDriverTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val prefs by lazy { BoostPrefs.get(this) }
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private var media by mutableStateOf(MediaVolume())
    private var notificationsEnabled by mutableStateOf(true)
    private var showVolume by mutableStateOf(true)
    private var resumeOnBoot by mutableStateOf(false)
    private var wallpaperColors by mutableStateOf(false)
    private var limit by mutableStateOf(40)
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshNotifications()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        volumeControlStream = AudioManager.STREAM_MUSIC
        showVolume = prefs.getBoolean(BoostPrefs.VOLUME, true)
        resumeOnBoot = prefs.getBoolean(BoostPrefs.BOOT, false)
        wallpaperColors = prefs.getBoolean(BoostPrefs.WALLPAPER, false)
        limit = BoostPrefs.limit(prefs)
        refreshVolume()
        refreshNotifications()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    refreshVolume()
                    delay(400)
                }
            }
        }
        setContent {
            val status by BoostService.status.collectAsStateWithLifecycle()
            BoostDriverTheme(useWallpaperColors = wallpaperColors) {
                BoostPanel(
                    media = media, status = status, limit = limit, showVolume = showVolume,
                    resumeOnBoot = resumeOnBoot, wallpaperColors = wallpaperColors,
                    supportsWallpaperColors = Build.VERSION.SDK_INT >= 31,
                    notificationsEnabled = notificationsEnabled,
                    hasAcceptedWarning = { prefs.getBoolean(BoostPrefs.ACCEPTED, false) },
                    onDismiss = ::finish,
                    onMediaVolume = ::setMediaVolume,
                    onGain = { BoostService.request(this, it) },
                    onWarningAccepted = { gain ->
                        prefs.edit().putBoolean(BoostPrefs.ACCEPTED, true).apply()
                        BoostService.request(this, gain)
                        if (Build.VERSION.SDK_INT >= 33 && !notificationsEnabled) {
                            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onShowVolume = { showVolume = it; save(BoostPrefs.VOLUME, it) },
                    onResumeOnBoot = { resumeOnBoot = it; save(BoostPrefs.BOOT, it) },
                    onWallpaperColors = { wallpaperColors = it; save(BoostPrefs.WALLPAPER, it) },
                    onLimit = ::updateLimit,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshNotifications()
    }

    private fun save(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    private fun updateLimit(value: Int) {
        prefs.edit().putInt(BoostPrefs.LIMIT, value).apply()
        limit = value
        if (BoostService.status.value.gain > value) BoostService.request(this, value)
    }

    private fun setMediaVolume(value: Int) {
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
        } catch (_: SecurityException) {
            // The system may protect volume changes on the current route.
        }
        refreshVolume()
    }

    private fun refreshVolume() {
        val stream = AudioManager.STREAM_MUSIC
        val min = if (Build.VERSION.SDK_INT >= 28) audio.getStreamMinVolume(stream) else 0
        val max = audio.getStreamMaxVolume(stream).coerceAtLeast(min)
        media = MediaVolume(min, max, audio.getStreamVolume(stream).coerceIn(min, max))
    }

    private fun refreshNotifications() {
        notificationsEnabled = Build.VERSION.SDK_INT < 24 ||
            getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }
}
