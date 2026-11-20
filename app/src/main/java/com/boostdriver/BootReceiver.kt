package com.boostdriver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = BoostPrefs.get(context)
        val resume = prefs.getInt(BoostPrefs.RESUME, 0)
        val selected = BoostPrefs.selected(prefs)
        val gain = BoostPrefs.bootGain(
            prefs.getBoolean(BoostPrefs.BOOT, false),
            resume,
            selected,
        )
        if (gain > 0) BoostService.enable(context, gain)
    }
}
