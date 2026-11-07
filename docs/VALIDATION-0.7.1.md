# Validation — 0.7.1-page-fallback

Date: 2026-10-28. Project: HomeAlignmentLab-Antigravity. Version code 9.

## Confirmed in this run

- Read the latest relevant Gemini conversation and the Xiaomi report received on October 28. The unfinished task was a follow-up patch for unchanged page detection after the prior Xiaomi patch, including a gesture-based fallback.
- Reproduced incoming Kotlin compilation failure: unresolved virtual-page helpers in Live referenced by LabWallpaper, SurveyFlow and WidgetAnimations.
- Final Gradle build completed successfully: assembleDebug, assembleDebugAndroidTest, testDebugUnitTest and lintDebug. **189 unit tests, 0 failures, 0 errors, 0 skipped. Lint: 0 errors, 25 warnings.** Warnings include existing target/API, translation, custom-view and allocation warnings; they were not suppressed for this patch.
- Added 21 focused unit tests covering page bounds, anchor/reset, cancellation, vertical/tiny/edge/long gestures, missing down events, flings, fixed Xiaomi offsets and rejection of synthetic/legacy evidence.
- Android instrumentation on a disposable API 35 emulator passed. Synthetic state fixtures checked migration preserves widget geometry/media; rejects missing, unstable, stale, reused and wrong-widget captures; saves one visit per tap; leaves six fixed-offset visits UNSUPPORTED; does not auto-pass when leaving; keeps raw offsets unchanged when the estimator moves; prioritizes native page data. Preferences restored after the fixture.
- UI smoke check: dashboard → Diagnostics → Swipe fallback, per-widget page assignment, enable and disable controls; correct experimental status and retained scroll position. Visually inspected the fallback screens.
- Real launcher tap-through: applied the live wallpaper via the system picker, opened the guided A/B/C flow, tapped a launcher widget, saved exactly one A1 visit and reached B1. Skip returned Home. Preferences confirmed one saved visit, surveyCompleted=false, guidedSurvey=false and no isolation pass.
- Package metadata: dev.animatedhome.diagnostics, 0.7.1-page-fallback, versionCode 9, minSdk 26, targetSdk 35.
- APK signature verified; certificate matches the existing 0.7.0 APK. Certificate SHA-256: f8f158a8d7fd86744f12e15a45ec396bcdd25c3cfd0df2f61669261d6cbf5156.
- Gemini's StreamEngine source stayed byte-for-byte unchanged. Its streaming functionality was not revalidated by these page-fallback tests.

## Boundaries

No physical Xiaomi, OnePlus or Samsung device was connected. The emulator uses Launcher3, which supplies native offsets; it does not reproduce Xiaomi's touch delivery. Gesture fallback behavior is covered by unit/state tests, not physical-device proof. No claim of corrected Xiaomi page isolation, universal launcher support, measured FPS improvement or battery improvement is made.

A full six-visit, three-page visual isolation run was not completed through the launcher UI in this patch. The six-visit save/assessment pipeline was exercised with synthetic Android state fixtures, while the actual UI path was checked for one independent visit and cancellation.

The persisted emulator produced a System UI ANR during boot. Its Close app dialog was dismissed before testing continued; this is recorded separately from app results.

## Evidence

Logs, regression result and screenshots are in the versioned validation archive in releases. SHA256SUMS.txt identifies the deliverable APK and archives. The pre-edit project backup is retained in the task workspace under work/xiaomi-patch-20261028/before-patch.zip.

For a physical check, follow releases/START-HERE-0.7.1.md, then export a report after swiping. Compare raw readings with the separate fallback gesture counters and estimated page. A/B/C should still report UNSUPPORTED if the launcher continues to supply identical native offsets.
