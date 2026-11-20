# BoostDriver

Audio booster for Android 6.0 and later, inspired by Volume Booster GOODEV. Built with Kotlin, Jetpack Compose, and Material 3. **No ads, telemetry, or internet access.**

## 🎚️ How to use

- **Media volume** changes the Android media volume. **Gain** applies extra amplification where the Android audio output supports it.
- Start at a low volume. Tap **Stop** in the panel or notification to turn off amplification without changing media volume.
- The default gain limit is 40%. In **Settings**, you can change the limit, hide the media volume control, enable wallpaper colors on Android 12 and later, or opt in to resuming an active boost after restart.

**Warning:** Extra gain can distort audio, damage speakers, and harm your hearing. No setting is guaranteed safe.

## 🔊 Compatibility and permissions

BoostDriver tries `LoudnessEnhancer` and then `Equalizer` on the global output mix. This is a legacy audio session. Some devices, players, and outputs may reject or ignore the effect. An active status means Android accepted the effect, not that every app is audibly louder. BoostDriver does not target calls or alarms.

A foreground service keeps the boost active when you close the panel. On Android 13 and later, notification permission allows the status notification and its **Stop** action to appear in the notification shade. The boot permission is used for restoration only if you enable **Resume boost after restart**.

## 🛠️ Build

Use JDK 21 and Android SDK 36.

```sh
./gradlew detekt lint :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## License

GPL-3.0
