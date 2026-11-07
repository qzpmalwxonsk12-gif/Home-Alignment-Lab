# Home Alignment — 0.7.1 page fallback

Android live-wallpaper-backed widgets with independent GIF/video playback and optional web streaming. Version **0.7.1-page-fallback**, code 9, min API 26, target/compile 35. This patch completes the interrupted Xiaomi fixed-offset work in the Antigravity copy while preserving its existing streaming, sync and widget features.

Start with [the current release guide](releases/START-HERE-0.7.1.md). Normal playback and file switching require no diagnostic tests. Diagnostics contains the optional A/B/C test and experimental swipe fallback. The fallback estimates page changes from gestures; it never makes the native-offset test pass.

Build with JDK 17 and Android SDK 35: `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=1 --no-watch-fs`.

See [current validation](docs/VALIDATION-0.7.1.md) and [patch details](docs/CHANGES-0.7.1.md). Physical Xiaomi fallback behavior is unverified. Earlier versioned documents describe earlier builds. The existing optional streaming features use the network; diagnostic reports are not uploaded automatically.
