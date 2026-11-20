package com.boostdriver;

import android.content.Context;
import android.content.SharedPreferences;

final class BoostPrefs {
    static final String LIMIT = "limit", ACCEPTED = "accepted", BOOT = "boot",
            RESUME = "resume", VOLUME = "show_volume";

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences("boost", Context.MODE_PRIVATE);
    }

    static int limit(SharedPreferences prefs) {
        return Math.max(10, Math.min(60, prefs.getInt(LIMIT, 40)));
    }

    static int bootGain(boolean accepted, boolean boot, int gain, int limit) {
        return accepted && boot ? Math.max(0, Math.min(gain, limit)) : 0;
    }
}
