package com.boostdriver;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.widget.*;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable observer = this::render;
    private final Runnable refreshVolume = new Runnable() {
        @Override public void run() { renderVolume(); handler.postDelayed(this, 400); }
    };
    private SharedPreferences prefs;
    private AudioManager audio;
    private TextView volumeLabel, gainLabel, status;
    private SeekBar volume, gain;
    private int requested;
    private boolean dragging;
    private boolean warningOpen;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = BoostPrefs.get(this);
        audio = getSystemService(AudioManager.class);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        getWindow().setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        var scroll = new ScrollView(this);
        var panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        panel.setPadding(padding, padding, padding, padding);
        scroll.addView(panel);
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        setContentView(scroll);
        var title = text(panel, 24);
        title.setText(R.string.app_name);
        title.setPadding(0, 0, 0, dp(12));
        if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        volumeLabel = text(panel, 16);
        volume = slider(panel, volumeLabel);
        gainLabel = text(panel, 16);
        gain = slider(panel, gainLabel);
        status = text(panel, 14);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        Button settings = new Button(this);
        settings.setText(R.string.settings);
        settings.setOnClickListener(v -> settings());
        panel.addView(settings);
        Button stop = new Button(this);
        stop.setText(R.string.stop);
        stop.setOnClickListener(v -> { dragging = false; BoostService.request(this, 0); });
        panel.addView(stop);
        volume.setOnSeekBarChangeListener(new SliderListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user) {
                    try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, minVolume() + value, 0); }
                    catch (SecurityException ignored) { /* volume protegido pelo sistema */ }
                    renderVolume();
                }
            }
        });
        gain.setOnSeekBarChangeListener(new SliderListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { dragging = true; }
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (!user) return;
                requested = value;
                gainLabel.setText(getString(R.string.gain, value));
                if (!dragging) activate(value);
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                dragging = false;
                activate(requested);
            }
        });
        render();
    }

    private void activate(int value) {
        if (value <= 0 || prefs.getBoolean(BoostPrefs.ACCEPTED, false)) {
            BoostService.request(this, value);
            return;
        }
        render();
        if (warningOpen) return;
        warningOpen = true;
        new AlertDialog.Builder(this).setTitle(R.string.warning_title).setMessage(R.string.warning)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.accept, (dialog, which) -> {
                    prefs.edit().putBoolean(BoostPrefs.ACCEPTED, true).apply();
                    BoostService.request(this, value);
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
                    }
                }).setOnDismissListener(d -> warningOpen = false).show();
    }

    private void settings() {
        var options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setPadding(dp(20), dp(8), dp(20), dp(8));
        var showVolume = new Switch(this);
        showVolume.setText(R.string.show_volume);
        showVolume.setChecked(prefs.getBoolean(BoostPrefs.VOLUME, true));
        showVolume.setOnCheckedChangeListener((view, checked) -> {
            prefs.edit().putBoolean(BoostPrefs.VOLUME, checked).apply();
            renderVolume();
        });
        options.addView(showVolume);
        var boot = new Switch(this);
        boot.setText(R.string.boot);
        boot.setChecked(prefs.getBoolean(BoostPrefs.BOOT, false));
        boot.setOnClickListener(view -> {
            if (!boot.isChecked()) { prefs.edit().putBoolean(BoostPrefs.BOOT, false).apply(); return; }
            boot.setChecked(false);
            new AlertDialog.Builder(this).setTitle(R.string.boot).setMessage(R.string.boot_warning)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.accept, (d, w) -> {
                        prefs.edit().putBoolean(BoostPrefs.BOOT, true).apply();
                        boot.setChecked(true);
                    }).show();
        });
        options.addView(boot);
        var limit = new Button(this);
        limit.setText(getString(R.string.limit, BoostPrefs.limit(prefs)));
        limit.setOnClickListener(v -> chooseLimit(limit));
        options.addView(limit);
        var scroll = new ScrollView(this);
        scroll.addView(options);
        new AlertDialog.Builder(this).setTitle(R.string.settings).setView(scroll)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void chooseLimit(Button button) {
        CharSequence[] labels = new CharSequence[6];
        for (int i = 0; i < labels.length; i++) labels[i] = getString(R.string.limit_value, (i + 1) * 10);
        new AlertDialog.Builder(this).setTitle(R.string.choose_limit)
                .setSingleChoiceItems(labels, BoostPrefs.limit(prefs) / 10 - 1, (dialog, which) -> {
                    dialog.dismiss();
                    int selected = (which + 1) * 10;
                    if (selected > 40 && selected > BoostPrefs.limit(prefs)) {
                        new AlertDialog.Builder(this).setTitle(R.string.warning_title)
                                .setMessage(getString(R.string.limit_warning, 40))
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(R.string.accept, (d, w) -> setLimit(selected, button)).show();
                    } else setLimit(selected, button);
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void setLimit(int value, Button button) {
        prefs.edit().putInt(BoostPrefs.LIMIT, value).apply();
        button.setText(getString(R.string.limit, value));
        if (BoostService.applied > value) BoostService.request(this, value);
        render();
    }

    private void render() {
        if (!dragging) {
            gain.setMax(BoostPrefs.limit(prefs));
            gain.setProgress(BoostService.applied);
            requested = BoostService.applied;
            gainLabel.setText(getString(R.string.gain, BoostService.applied));
        }
        String message = BoostService.error != 0 ? getString(BoostService.error) : BoostService.applied == 0
                ? getString(R.string.status_off)
                : getString(R.string.status_on, BoostService.mode == 1 ? "LoudnessEnhancer" : "Equalizer");
        if (BoostService.applied > 0 && Build.VERSION.SDK_INT >= 24 && !getSystemService(NotificationManager.class).areNotificationsEnabled()) {
            message += "\n" + getString(R.string.status_no_notification);
        }
        status.setText(message);
        renderVolume();
    }

    private int minVolume() { return Build.VERSION.SDK_INT >= 28 ? audio.getStreamMinVolume(AudioManager.STREAM_MUSIC) : 0; }

    private void renderVolume() {
        int visibility = prefs.getBoolean(BoostPrefs.VOLUME, true) ? View.VISIBLE : View.GONE;
        volume.setVisibility(visibility);
        volumeLabel.setVisibility(visibility);
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        volume.setMax(Math.max(0, max - minVolume()));
        volume.setProgress(current - minVolume());
        String label = getString(R.string.volume, max <= 0 ? 0 : Math.round(current * 100f / max));
        if (!label.contentEquals(volumeLabel.getText())) volumeLabel.setText(label);
    }

    @Override protected void onResume() {
        super.onResume();
        BoostService.observer = observer;
        render();
        handler.post(refreshVolume);
    }
    @Override protected void onPause() {
        if (BoostService.observer == observer) BoostService.observer = null;
        handler.removeCallbacks(refreshVolume);
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        render();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(LinearLayout parent, int size) {
        var view = new TextView(this);
        view.setId(View.generateViewId());
        view.setTextSize(size);
        parent.addView(view);
        return view;
    }
    private SeekBar slider(LinearLayout parent, TextView label) {
        var bar = new SeekBar(this);
        bar.setId(View.generateViewId());
        label.setLabelFor(bar.getId());
        bar.setMinimumHeight(dp(48));
        parent.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        return bar;
    }

    private abstract static class SliderListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar bar) {}
        @Override public void onStopTrackingTouch(SeekBar bar) {}
    }
}
