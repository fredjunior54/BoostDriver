package com.boostdriver;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import com.boostdriver.audio.BoostEngine;

public final class BoostService extends Service {
    static final String STOP = "com.boostdriver.STOP";
    static final String GAIN = "gain";
    private static final String CHANNEL = "boost";
    private static final int ID = 1;
    static int applied, mode, error;
    static Runnable observer;
    private BoostEngine engine;
    private boolean foreground;
    private static boolean cancelled;
    private int effectGeneration;

    static void publish() { if (observer != null) observer.run(); }

    static void request(Context context, int gain) {
        if (gain <= 0) {
            BoostPrefs.get(context).edit().remove(BoostPrefs.RESUME).apply();
            cancelled = true;
            if (applied > 0) context.stopService(new Intent(context, BoostService.class));
            error = 0;
            publish();
            return;
        }
        cancelled = false;
        Intent intent = new Intent(context, BoostService.class).putExtra(GAIN, gain);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (IllegalStateException | SecurityException failure) {
            error = R.string.status_start_error;
            publish();
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        engine = new BoostEngine(kind -> {
            int generation = ++effectGeneration;
            return BoostEngine.platformEffect(kind, () -> {
                if (generation == effectGeneration && applied > 0) finishBoost(R.string.status_error);
            });
        });
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.channel), NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            finishBoost(0);
            return START_NOT_STICKY;
        }
        var prefs = BoostPrefs.get(this);
        int gain = BoostPrefs.bootGain(prefs.getBoolean(BoostPrefs.ACCEPTED, false), true,
                intent.getIntExtra(GAIN, 0), BoostPrefs.limit(prefs));
        try {
            if (!foreground) {
                if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification(0), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                else startForeground(ID, notification(0));
                foreground = true;
            }
        } catch (IllegalStateException | SecurityException failure) {
            finishBoost(R.string.status_start_error);
            return START_NOT_STICKY;
        }
        if (gain == 0 || cancelled) { finishBoost(0); return START_NOT_STICKY; }
        mode = engine.apply(gain);
        if (mode == 0) { finishBoost(R.string.status_error); return START_NOT_STICKY; }
        applied = gain;
        error = 0;
        prefs.edit().putInt(BoostPrefs.RESUME, gain).apply();
        getSystemService(NotificationManager.class).notify(ID, notification(gain));
        publish();
        return START_NOT_STICKY;
    }

    @SuppressWarnings("deprecation")
    private Notification notification(int gain) {
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        var open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), flags);
        var stop = PendingIntent.getService(this, 0, new Intent(this, BoostService.class).setAction(STOP), flags);
        var builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle(getString(R.string.app_name)).setContentText(getString(R.string.gain, gain))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, getString(R.string.stop), stop).build()).build();
    }

    private void finishBoost(int message) {
        BoostPrefs.get(this).edit().remove(BoostPrefs.RESUME).apply();
        error = message;
        releaseEffect();
        stopSelf();
    }

    private void releaseEffect() {
        ++effectGeneration;
        engine.close();
        applied = mode = 0;
        if (foreground) {
            removeForeground();
            foreground = false;
        }
        publish();
    }

    @SuppressWarnings("deprecation")
    private void removeForeground() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
    }

    @Override public void onDestroy() { releaseEffect(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
