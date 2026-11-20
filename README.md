<p align="center">
  <img src="assets/branding/logo-readme.png" alt="BoostDriver logo" width="360">
</p>

<h1 align="center">BoostDriver</h1>

<!-- app-version -->
<p align="center"><strong>Version:</strong> v1.0.4</p>
<!-- /app-version -->

<p align="center">
  <a href="#-compatibility-and-permissions"><img src="https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android" alt="Requires Android 6.0 or later"></a>
  <a href="app/src/main/java/com/boostdriver"><img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin" alt="Written in Kotlin"></a>
  <a href="app/src/main/java/com/boostdriver/ui"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose" alt="UI built with Jetpack Compose"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/fredjunior54/BoostDriver" alt="MIT License"></a>
  <a href="app/src/main/AndroidManifest.xml"><img src="https://img.shields.io/badge/Internet%20permission-none-2E7D32" alt="No internet permission"></a>
</p>

## 🎚️ How to use

Audio booster for Android 6.0 and later, inspired by Volume Booster GOODEV. Built with Kotlin, Jetpack Compose, and Material 3. **No ads, telemetry, or internet access.**

The full-screen interface keeps both controls visible and follows the system light or dark theme.

- **Media volume** changes the Android media volume. **Gain** applies extra amplification where the Android audio output supports it.
- Start at a low volume. Tap **Enable** to apply your selected gain. Tap **Disable** in the panel or notification to turn off amplification without changing the saved gain or media volume.
- The first launch shows 20% gain with boost off. The displayed dB value is the requested target, not a measurement. The slider ranges from 0% to 100%; the target levels off at +25.87 dB from 80% onward. The app remembers your selection, including 0%, and the boost does not start automatically after a restart.

## 📦 Download

Download the signed `BoostDriver-vX.Y.Z.apk` from the [latest GitHub release](https://github.com/fredjunior54/BoostDriver/releases/latest). Select the APK under **Assets**, not the source archive. A SHA-256 checksum is available alongside the APK.

If you installed a debug build, uninstall it before installing the release APK. Android cannot update an app signed with a different key, and uninstalling clears its saved preferences.

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
