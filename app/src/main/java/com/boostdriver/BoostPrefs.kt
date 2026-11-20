package com.boostdriver

import android.content.Context
import android.content.SharedPreferences

internal object BoostPrefs {
    const val LIMIT = "limit"
    const val ACCEPTED = "accepted"
    const val BOOT = "boot"
    const val RESUME = "resume"
    const val VOLUME = "show_volume"
    const val WALLPAPER = "wallpaper_colors"

    fun get(context: Context): SharedPreferences = context.getSharedPreferences("boost", Context.MODE_PRIVATE)

    fun limit(prefs: SharedPreferences): Int = prefs.getInt(LIMIT, 40).coerceIn(10, 60)

    fun bootGain(accepted: Boolean, boot: Boolean, gain: Int, limit: Int): Int =
        if (accepted && boot) gain.coerceIn(0, limit) else 0
}
