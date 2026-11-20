package com.boostdriver;

import android.content.*;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        var prefs = BoostPrefs.get(context);
        int gain = BoostPrefs.bootGain(prefs.getBoolean(BoostPrefs.ACCEPTED, false),
                prefs.getBoolean(BoostPrefs.BOOT, false), prefs.getInt(BoostPrefs.RESUME, 0), BoostPrefs.limit(prefs));
        if (gain > 0) BoostService.request(context, gain);
    }
}
