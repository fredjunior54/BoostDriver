# BoostDriver

<!-- app-version --> **Version:** v1.0.2 <!-- /app-version -->

Audio booster for Android 6.0 and later, inspired by Volume Booster GOODEV. Built with Kotlin, Jetpack Compose, and Material 3. **No ads, telemetry, or internet access.**

## 🎚️ How to use

The full-screen interface keeps both controls visible and follows the system light or dark theme.

- **Media volume** changes the Android media volume. **Gain** applies extra amplification where the Android audio output supports it.
- Start at a low volume. Tap **Enable** to apply your selected gain. Tap **Disable** in the panel or notification to turn off amplification without changing the saved gain or media volume.
- The first launch shows 20% gain with boost off. The displayed dB value is the requested target, not a measurement. The slider ranges from 0% to 100%; the target levels off at +25.87 dB from 80% onward. The app remembers your selection, including 0%, and the boost does not start automatically after a restart.

## 🔊 Compatibility and permissions

BoostDriver tries `LoudnessEnhancer` and then `Equalizer` on the global audio mix. When the mix cannot be configured, announced player sessions may provide a fallback. Some devices, players, and outputs bypass that mix even when Android accepts the effect. A configured status means Android accepted the effect, not that every app is audibly louder. BoostDriver does not target calls or alarms.

A foreground service keeps the boost active when you close the panel. On Android 13 and later, notification permission allows the status notification and its **Disable** action to appear in the notification shade.

## 🛠️ Build

Use JDK 21 and Android SDK 36.

```sh
./gradlew detekt lint :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 📄 License

BoostDriver was created by [Fred Junior](https://github.com/fredjunior54). The [source repository](https://github.com/fredjunior54/BoostDriver) is available under the [MIT License](LICENSE). Third-party dependencies retain their respective licenses.
