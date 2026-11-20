package com.boostdriver

import android.content.Context
import android.content.SharedPreferences

internal object BoostPrefs {
    const val BOOT = "boot"
    const val SELECTION = "selected_gain"
    const val DEFAULT_GAIN = 20
    const val MAX_GAIN = 100
    const val RESUME = "resume"
    const val VOLUME = "show_volume"
    const val WALLPAPER = "wallpaper_colors"
    const val NOTIFICATIONS_ASKED = "notifications_asked"
    private const val SCALE = "gain_scale"
    private const val CURRENT_SCALE = 1

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences("boost", Context.MODE_PRIVATE).also(::migrate)

    private fun migrate(prefs: SharedPreferences) {
        if (prefs.getInt(SCALE, 0) == CURRENT_SCALE) return
        val selection = resolveSelection(prefs.all[SELECTION] as? Int, prefs.getInt(RESUME, 0))
        prefs.edit().putInt(SELECTION, selection).putInt(SCALE, CURRENT_SCALE)
            .remove(RESUME).remove("limit").remove("accepted").apply()
    }

    fun selected(prefs: SharedPreferences): Int = resolveSelection(prefs.getInt(SELECTION, DEFAULT_GAIN), 0)

    fun resolveSelection(saved: Int?, legacy: Int): Int =
        (saved ?: legacy.takeIf { it in 1..MAX_GAIN } ?: DEFAULT_GAIN).coerceIn(0, MAX_GAIN)

    fun bootGain(boot: Boolean, armed: Int, selected: Int): Int =
        if (boot && armed > 0) selected.coerceIn(0, MAX_GAIN) else 0
}
