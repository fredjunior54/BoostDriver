package com.boostdriver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.BadParcelableException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.boostdriver.audio.BoostEngine
import com.boostdriver.audio.PlaybackSessions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BoostService : Service() {
    internal data class Status(
        val gain: Int = 0,
        val mode: Int = 0,
        val error: Int = 0,
        val starting: Boolean = false,
        val enabled: Boolean = false,
        val scoped: Boolean = false,
    )

    private val effects = mutableMapOf<Int, BoostEngine>()
    private val sessions = PlaybackSessions()
    private val main = Handler(Looper.getMainLooper())
    private var foreground = false
    private var listening = false
    private var appliedGeneration = 0L
    private val notifications get() = getSystemService(NotificationManager::class.java)
    private val audio get() = getSystemService(AudioManager::class.java)
    private var outputDevices = emptySet<Int>()
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refreshOutputs()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refreshOutputs()
    }
    private val announcements = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!state.value.enabled) return
            val (session, owner) = try {
                intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0) to
                    intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
            } catch (_: BadParcelableException) {
                return
            }
            val changed = when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> sessions.open(session, owner)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> sessions.close(session, owner)
                else -> false
            }
            if (changed && state.value.scoped && state.value.gain > 0 && requests.accepts(appliedGeneration)) applyGain(state.value.gain)
        }
    }

    override fun onCreate() {
        super.onCreate()
        active = this
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, getString(R.string.channel), NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            notifications.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == DISABLE) {
            disable(this)
            if (!foreground) stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!enterForeground()) {
            finishBoost(R.string.status_start_error)
            return START_NOT_STICKY
        }
        if (!requests.wanted) {
            finishBoost(state.value.error)
        } else if (intent != null && requests.accepts(intent.getLongExtra(REQUEST, -1))) {
            appliedGeneration = requests.generation
            listen()
            applyGain(intent.getIntExtra(GAIN, 0).coerceIn(0, BoostPrefs.MAX_GAIN))
        }
        return START_NOT_STICKY
    }

    private fun enterForeground(): Boolean = try {
        if (!foreground) {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else startForeground(ID, notification())
            foreground = true
        }
        true
    } catch (_: IllegalStateException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun listen() {
        if (listening) return
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(announcements, filter, RECEIVER_EXPORTED)
        else registerReceiver(announcements, filter)
        outputDevices = outputIds()
        audio.registerAudioDeviceCallback(devices, main)
        listening = true
    }

    private fun outputIds() = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.id }.toSet()

    private fun refreshOutputs() {
        val current = outputIds()
        if (current == outputDevices) return
        outputDevices = current
        if (state.value.enabled && state.value.gain > 0 && requests.accepts(appliedGeneration)) {
            releaseEffects()
            applyGain(state.value.gain)
        }
    }

    private fun effectFor(session: Int): BoostEngine = effects.getOrPut(session) {
        lateinit var engine: BoostEngine
        val diagnostic: ((String) -> Unit)? =
            if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                { message -> Log.d(TAG, "session=$session $message") }
            } else null
        engine = BoostEngine(diagnostic) { kind ->
            BoostEngine.platformEffect(kind, session, diagnostic) {
                main.post {
                    if (effects[session] === engine && requests.accepts(appliedGeneration)) {
                        finishBoost(R.string.status_error)
                    }
                }
            }
        }
        engine
    }

    private fun applyGain(gain: Int) {
        var mode = 0
        var scoped = false
        if (gain == 0) {
            releaseEffects()
        } else {
            val targets = sessions.ids
            effects.keys.toList().filter { it != 0 && it !in targets }.forEach { effects.remove(it)?.close() }
            if (effects.keys.none { it != 0 }) {
                mode = effectFor(0).apply(gain)
                if (mode == 0) effects.remove(0)?.close()
            }
            if (mode == 0) {
                for (session in targets) {
                    val result = effectFor(session).apply(gain)
                    if (result != 0) {
                        if (mode == 0 || result == 1) mode = result
                        scoped = true
                    } else effects.remove(session)?.close()
                }
            }
            if (mode == 0) {
                finishBoost(R.string.status_error)
                return
            }
        }
        // This confirms configuration, not that a particular stream passed through the effect.
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d(TAG, "Configured gain=${BoostEngine.millibels(gain)} mB backend=$mode scope=$scoped")
        }
        val prefs = BoostPrefs.get(this)
        if (gain > 0) prefs.edit().putInt(BoostPrefs.RESUME, gain).apply()
        else prefs.edit().remove(BoostPrefs.RESUME).apply()
        state.value = Status(gain = gain, mode = mode, enabled = true, scoped = scoped)
        notifications.notify(ID, notification())
    }

    @Suppress("DEPRECATION")
    private fun notification(): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val disable = PendingIntent.getService(this, 0, Intent(this, BoostService::class.java).setAction(DISABLE), flags)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val current = state.value
        val message = when {
            current.starting -> getString(R.string.status_starting)
            current.gain == 0 -> getString(R.string.status_bypass)
            else -> getString(R.string.gain_target, current.gain, BoostEngine.millibels(current.gain) / 100f)
        }
        return builder.setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(getString(R.string.app_name)).setContentText(message)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.disable), disable).build()).build()
    }

    private fun releaseEffects() {
        effects.values.forEach { it.close() }
        effects.clear()
    }

    private fun finishBoost(error: Int = 0) {
        requests.disable()
        BoostPrefs.get(this).edit().remove(BoostPrefs.RESUME).apply()
        release()
        state.value = Status(error = error)
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun release() {
        releaseEffects()
        sessions.clear()
        if (listening) {
            unregisterReceiver(announcements)
            audio.unregisterAudioDeviceCallback(devices)
            listening = false
        }
        if (foreground) {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
            foreground = false
        }
    }

    override fun onDestroy() {
        release()
        if (active === this) active = null
        if (requests.accepts(appliedGeneration) && state.value.enabled) {
            requests.disable()
            state.value = Status()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    internal companion object {
        private const val TAG = "BoostDriver"
        private const val DISABLE = "com.boostdriver.DISABLE"
        private const val GAIN = "gain"
        private const val REQUEST = "request"
        private const val CHANNEL = "boost"
        private const val ID = 1
        private val state = MutableStateFlow(Status())
        val status = state.asStateFlow()
        private val requests = BoostRequests()
        private var active: BoostService? = null

        fun enable(context: Context, gain: Int) {
            val request = requests.enable(gain) ?: return
            state.value = Status(starting = true)
            send(context, gain, request)
        }

        fun update(context: Context, gain: Int) {
            requests.update()?.let { send(context, gain, it) }
        }

        fun disable(context: Context) {
            requests.disable()
            BoostPrefs.get(context).edit().remove(BoostPrefs.RESUME).apply()
            // Let a queued foreground start promote and stop itself if not yet promoted.
            active?.takeIf { it.foreground }?.finishBoost()
            state.value = Status()
        }

        private fun send(context: Context, gain: Int, request: Long) {
            val intent = Intent(context, BoostService::class.java).putExtra(GAIN, gain).putExtra(REQUEST, request)
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            } catch (_: IllegalStateException) {
                failStart(context)
            } catch (_: SecurityException) {
                failStart(context)
            }
        }

        private fun failStart(context: Context) {
            disable(context)
            state.value = Status(error = R.string.status_start_error)
        }
    }
}
