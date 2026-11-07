# 0.2.0 — guided setup and missing-wallpaper recovery

The original capture screen accepted a valid widget rectangle and offered its 45-second test even when the live wallpaper was not active. The button returned to the launcher, where no lab wallpaper existed to draw the box. The user reported this exact sequence and explained that they had never applied the live wallpaper.

This revision adds a numbered setup flow: choose a background, apply the wallpaper, add one widget, show a pink box, and report what appeared. Applying the wallpaper is checked using Android's wallpaper identity, not inferred from a completed preview intent or from receiving widget bounds. The manifest adds a scoped wallpaper-service visibility query, without QUERY_ALL_PACKAGES. Earlier Android releases use a recent real home-engine observation only as a conservative fallback when identity access is restricted.

Capture and advanced alignment buttons now share prerequisite checks. Missing wallpaper, removed widgets, invalid bounds, unavailable surfaces and stale calibration get an explanation before leaving the Activity. The renderer records a submitted frame only after posting the canvas successfully. That is telemetry, not a visual pass. Engine changes, rejection and drawing failures are recorded separately. The 45-second viewing period begins with the first posted box frame.

The default screen no longer dumps JSON, offsets or a menu of six page labels. It includes a labelled visual example and asks the user whether the box was aligned, misplaced or absent. Missing/misplaced feedback offers report export immediately. Advanced controls remain available from Help. A separate page-check walkthrough presents the six visits in order and requires an explicit user observation for swipe isolation.

Version code increases from 1 to 2 with the same package and debug signer, allowing an update over the earlier APK. Existing widgets, saved backgrounds and logs are retained. Video remains deferred pending target-device alignment and isolation.

A previous alignment confirmation is only reused when the same installed widget still has valid bounds, the same recorded position, and the same wallpaper/display environment. Replacing, moving or invalidating it requires another check; the old observation remains in the report as history.

Reference: Android's [WallpaperManager.getWallpaperInfo](https://developer.android.com/reference/android/app/WallpaperManager#getWallpaperInfo()) documents home-wallpaper identity and the scoped service query on Android 14+.
