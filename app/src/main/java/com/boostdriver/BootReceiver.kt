package com.boostdriver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = BoostPrefs.get(context)
        val gain = BoostPrefs.bootGain(
            prefs.getBoolean(BoostPrefs.ACCEPTED, false),
            prefs.getBoolean(BoostPrefs.BOOT, false),
            prefs.getInt(BoostPrefs.RESUME, 0),
            BoostPrefs.limit(prefs),
        )
        if (gain > 0) BoostService.request(context, gain)
    }
}
