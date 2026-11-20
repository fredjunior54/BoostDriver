package com.boostdriver

import android.content.Context
import android.content.SharedPreferences

internal object BoostPrefs {
    const val SELECTION = "selected_gain"
    const val DEFAULT_GAIN = 20
    const val MAX_GAIN = 100
    const val NOTIFICATIONS_ASKED = "notifications_asked"
    private const val SCHEMA = "gain_scale"
    private const val CURRENT_SCHEMA = 2

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences("boost", Context.MODE_PRIVATE).also(::migrate)

    private fun migrate(prefs: SharedPreferences) {
        if (prefs.getInt(SCHEMA, 0) >= CURRENT_SCHEMA) return
        val selection = resolveSelection(prefs.all[SELECTION] as? Int, prefs.getInt("resume", 0))
        prefs.edit().putInt(SELECTION, selection).putInt(SCHEMA, CURRENT_SCHEMA)
            .remove("resume").remove("limit").remove("accepted")
            .remove("boot").remove("show_volume").remove("wallpaper_colors").apply()
    }

    fun selected(prefs: SharedPreferences): Int = resolveSelection(prefs.getInt(SELECTION, DEFAULT_GAIN), 0)

    fun resolveSelection(saved: Int?, legacy: Int): Int =
        (saved ?: legacy.takeIf { it in 1..MAX_GAIN } ?: DEFAULT_GAIN).coerceIn(0, MAX_GAIN)
}
