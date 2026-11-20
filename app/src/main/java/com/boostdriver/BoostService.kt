package com.boostdriver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.boostdriver.audio.BoostEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BoostService : Service() {
    internal data class Status(val gain: Int = 0, val mode: Int = 0, val error: Int = 0)

    private lateinit var engine: BoostEngine
    private var foreground = false
    private var effectGeneration = 0
    private val notifications get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        engine = BoostEngine { kind ->
            val generation = ++effectGeneration
            BoostEngine.platformEffect(kind) {
                if (generation == effectGeneration && state.value.gain > 0) finishBoost(R.string.status_error)
            }
        }
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, getString(R.string.channel), NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            notifications.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == STOP) {
            finishBoost()
            return START_NOT_STICKY
        }
        val prefs = BoostPrefs.get(this)
        val gain = BoostPrefs.bootGain(
            prefs.getBoolean(BoostPrefs.ACCEPTED, false), true,
            intent.getIntExtra(GAIN, 0), BoostPrefs.limit(prefs),
        )
        try {
            if (!foreground) {
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(ID, notification(0), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(ID, notification(0))
                }
                foreground = true
            }
        } catch (_: IllegalStateException) {
            finishBoost(R.string.status_start_error)
            return START_NOT_STICKY
        } catch (_: SecurityException) {
            finishBoost(R.string.status_start_error)
            return START_NOT_STICKY
        }
        val mode = if (cancelled) 0 else engine.apply(gain)
        if (mode == 0) {
            finishBoost(if (gain > 0 && !cancelled) R.string.status_error else 0)
        } else {
            prefs.edit().putInt(BoostPrefs.RESUME, gain).apply()
            notifications.notify(ID, notification(gain))
            state.value = Status(gain, mode)
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun notification(gain: Int): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(this, 0, Intent(this, BoostService::class.java).setAction(STOP), flags)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return builder.setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.gain, gain))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.stop), stop).build()).build()
    }

    private fun finishBoost(error: Int = 0) {
        BoostPrefs.get(this).edit().remove(BoostPrefs.RESUME).apply()
        releaseEffect(error)
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun releaseEffect(error: Int) {
        ++effectGeneration
        engine.close()
        if (foreground) {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
            foreground = false
        }
        state.value = Status(error = error)
    }

    override fun onDestroy() {
        releaseEffect(state.value.error)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    internal companion object {
        private const val STOP = "com.boostdriver.STOP"
        private const val GAIN = "gain"
        private const val CHANNEL = "boost"
        private const val ID = 1
        private val state = MutableStateFlow(Status())
        val status = state.asStateFlow()
        private var cancelled = false

        fun request(context: Context, gain: Int) {
            val intent = Intent(context, BoostService::class.java)
            if (gain <= 0) {
                cancelled = true
                BoostPrefs.get(context).edit().remove(BoostPrefs.RESUME).apply()
                if (state.value.gain > 0) context.stopService(intent)
                state.value = state.value.copy(error = 0)
                return
            }
            cancelled = false
            intent.putExtra(GAIN, gain)
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            } catch (_: IllegalStateException) {
                state.value = state.value.copy(error = R.string.status_start_error)
            } catch (_: SecurityException) {
                state.value = state.value.copy(error = R.string.status_start_error)
            }
        }
    }
}
