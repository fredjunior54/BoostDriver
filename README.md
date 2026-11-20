# BoostDriver

Audio booster for Android 6.0 and later, inspired by Volume Booster GOODEV. Built with Kotlin, Jetpack Compose, and Material 3. **No ads, telemetry, or internet access.**

## 🎚️ How to use

- **Media volume** changes the Android media volume. **Gain** applies extra amplification where the Android audio output supports it.
- Start at a low volume. Tap **Enable** to apply your selected gain. Tap **Disable** in the panel or notification to turn off amplification without changing the saved gain or media volume.
- The first launch shows 20% gain with boost off. The displayed dB value is the requested target, not a measurement. The slider ranges from 0% to 100%; the target levels off at +25.87 dB from 80% onward. In **Settings**, you can hide the media volume control, enable wallpaper colors on Android 12 and later, or opt in to resuming an active boost after restart.

## 🔊 Compatibility and permissions

BoostDriver tries `LoudnessEnhancer` and then `Equalizer` on the global audio mix. When the mix cannot be configured, announced player sessions may provide a fallback. Some devices, players, and outputs bypass that mix even when Android accepts the effect. A configured status means Android accepted the effect, not that every app is audibly louder. BoostDriver does not target calls or alarms.

A foreground service keeps the boost active when you close the panel. On Android 13 and later, notification permission allows the status notification and its **Disable** action to appear in the notification shade. The boot permission is used for restoration only if you enable **Resume boost after restart**.

## 🛠️ Build

Use JDK 21 and Android SDK 36.

```sh
./gradlew detekt lint :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## License

GPL-3.0
