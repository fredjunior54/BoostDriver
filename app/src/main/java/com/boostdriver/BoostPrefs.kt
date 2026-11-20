package com.boostdriver

import android.content.Context
import android.content.SharedPreferences

internal object BoostPrefs {
    const val LIMIT = "limit"
    const val ACCEPTED = "accepted"
    const val BOOT = "boot"
    const val SELECTION = "selected_gain"
    const val DEFAULT_GAIN = 20
    const val RESUME = "resume"
    const val VOLUME = "show_volume"
    const val WALLPAPER = "wallpaper_colors"

    fun get(context: Context): SharedPreferences = context.getSharedPreferences("boost", Context.MODE_PRIVATE)

    fun limit(prefs: SharedPreferences): Int = prefs.getInt(LIMIT, 40).coerceIn(10, 60)

    fun selected(prefs: SharedPreferences): Int {
        val saved = if (prefs.contains(SELECTION)) prefs.getInt(SELECTION, DEFAULT_GAIN) else null
        return resolveSelection(saved, prefs.getInt(RESUME, 0), limit(prefs)).also {
            if (saved != it) prefs.edit().putInt(SELECTION, it).apply()
        }
    }

    fun resolveSelection(saved: Int?, legacy: Int, limit: Int): Int =
        (saved ?: legacy.takeIf { it in 1..60 } ?: DEFAULT_GAIN).coerceIn(0, limit)

    fun bootGain(accepted: Boolean, boot: Boolean, gain: Int, limit: Int): Int =
        if (accepted && boot) gain.coerceIn(0, limit) else 0
}
