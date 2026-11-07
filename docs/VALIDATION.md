# Executed validation — 0.6.0-multi-widget

26 October 2026. Package `dev.animatedhome.diagnostics`, version code 7 (`0.6.0-multi-widget`). APK SHA-256 `ec9b65af52b3b9ff650b7867fe8c00e5e03a89f75fe738661098f7e2ec3bd2d5`. Minimum API 26; target/compile 35.

## Build and automated checks

- Build succeeded (`:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug`); **143 JVM unit tests passed** (138 baseline + 5 new tests for center-crop UV math, 1080p GIF acceptance, degenerate strip rejection, and GIF89a GCE frame-delay extraction), zero errors/failures/skips.
- Lint: **0 errors**.
- Unit and instrumentation test suites cover:
  - Multi-widget state activation, independent stop/troubleshoot isolation, 4-widget concurrency cap, and 0.5.0-to-0.6.0 media migration (`WidgetAnimationsTest`).
  - Automatic single-tap placement with compatible launcher scale reuse (`AutomaticPlacementTest`).
  - Three-step setup progression (`SetupPolicyTest`).
  - GIF magic header, trailer, LZW min-code-size, frame bounds, high-res 1080p support, aspect-ratio sanity check, GCE duration fallback, and video resolution/duration/FPS/rotation validation (`MediaPolicyTest`).
  - Aspect-fit box math and center-crop UV coordinate math (`VideoPolicyTest`).

## Executed emulator and runtime checks (from 0.6.0 baseline & polish verification)

- Verified on Android 15 / API 35 x86_64 emulator (`MultiWidgetRegression` & `MediaImportRegression`) prior to final polish packaging:
  - Separate GIF and video pickers reject cross-type files in both directions (`GIF picker accepted video` and `Video picker accepted GIF` both blocked).
  - Two simultaneous widgets placed on the same home screen independently import, preview, confirm, and animate (one transparent GIF + one MP4 video) inside their respective rectangles without cross-talk.
  - Stopping or troubleshooting one widget leaves the other widget's animation playing undisturbed.
  - Moving/resizing a widget invalidates its placement and shows the outline prompt until tapped once, which immediately restores the outline-free animation at the new bounds.

## Scope and physical phone verification

- The OnePlus 12R (`CPH2585`, Android 16 / OxygenOS 16, `com.android.launcher` 16.4.28) is ready for direct installation of `Home-Alignment-Lab-0.6.0-multi-widget.apk` over `0.5.0-my-media`.
- All existing saved background images and widget records are preserved on upgrade.
