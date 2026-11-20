# BoostDriver design rules

## Platform and scope

- Kotlin, Jetpack Compose BOM `2026.01.00`, and Material 3.
- Android 6.0 and later (`minSdk 23`, `targetSdk 36`). Use only dependencies that preserve API 23. Guard newer functionality at runtime; do not raise the minimum SDK for a visual feature.
- A compact phone panel with two independent audio controls. Preserve the existing gain, stopping, notification, and boot behavior.
- Default UI and accessibility copy: American English, sentence case, Android string resources.

## Anchor: Material You

- Color: fixed HCT Tonal Spot palettes generated from `#2355A5` at contrast level `0.25`; follow the system light/dark setting. Wallpaper color is opt-in and available only on Android 12 and later.
- Typography: the system Roboto family, declared in `Type.kt` for API 23 compatibility with no font download. Seven Material 3 sizes across the semantic roles.
- Shapes: the Material You corner scale in `Shape.kt`. Use the native Material button shape and extra-large corners on sheets.
- Spacing: use `MaterialTheme.spacing`; the compact layout follows a 4 dp grid and interactive targets are at least 48 dp.
- Motion: use built-in Material transitions and platform back behavior. Any custom transition must use named tokens and convey state. Do not add ornamental motion.

## Prohibited patterns

No generic gradients, placeholder copy, arbitrary values outside the theme, undeclared font choices, decorative blur, nested cards, generic template layouts, or decorative animation.

## Implementation and verification

Theme source: `app/src/main/java/com/boostdriver/ui/theme/`. Screens use its semantic colors, spacing, shapes, and typography. Use edge-to-edge insets, honor back navigation, and describe icon-only controls for TalkBack.

With JDK 21, run `./gradlew detekt lint :app:testDebugUnitTest :app:assembleDebug`. Verify both themes, large fonts, and the effect and stop flows on the emulator before shipping the Compose UI.
