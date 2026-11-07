# Executed validation — 0.4.1-fast-return

25 October 2026. Package `dev.animatedhome.diagnostics`, version code 5. APK SHA-256 `d03beb199820c4038808e09e0e9dc36976c6b86d8ed912697629adc4410c5d8f`. Minimum API 26; target/compile 35.

## Supplied phone evidence from 0.4.0

OnePlus CPH2585, Android 16/API 36, launcher com.android.launcher 16.4.28. User confirmed successful video test with delayed reappearance after returning to its page. The exported report records c2.qti.avc.decoder, hardware flag true, 468 rendered frames over 15.7768 active seconds (29.6638 FPS), zero late drops, p95 gap 42 ms, max gap 58 ms, one complete loop and six pauses. The user-selected observation is smooth, aligned and isolated. This is a successful observed 0.4.0 phone run, not universal or prolonged-performance certification.

Four log samples took 587–666 ms from the final target-page offset callback to the rectangle draw request. These precede frame submission and include the deliberate 500 ms stability hold plus poll/rasterization delay. The supplied 9.57-second recording was inspected through extracted frames; it shows the empty widget after arriving back on A and subsequent video return. Personal video/report files are not redistributed. Sanitized metrics are in `verification-0.4.1/phone-0.4.0-summary.json`.

## Change and executed checks

- Video settling hold reduced to 150 ms. A cancellable deadline callback re-evaluates current state; every new offset suspends video and replaces the pending check. Hide/surface destruction cancels it. Pink PAGE and calibration evidence keep 500 ms. Decoder flush/restart behavior is unchanged.
- Build succeeded; **92 JVM tests passed**, zero failures/errors/skips. Eight new video-return regressions cover the shortened boundary, repeated motion, stale wake/page mismatch, hidden/locked/preview/stale configuration, missing/unsupported telemetry, zoom mismatch and the unchanged pink PAGE policy.
- Lint: **0 errors, 14 warnings**. Signature and ZIP alignment verified. The existing debug certificate is retained, update installation succeeded, and the installed APK hash equals the delivered APK.
- Runtime: final APK, disposable Android 15/API 35 x86_64 emulator, Launcher3 15, host AMD Radeon graphics, 1080 × 1920 / 420 dpi. Synthetic historical page prerequisites from the test-only fixture remain clearly labelled. Normal launcher taps refreshed the measured widget. No physical phone was connected.
- Guided update flow reached the saved-results screen, required the page-A two-corner refresh, and returned to Play video test. Video appeared within the measured widget with the surrounding grid preserved.
- Three page-return draws evaluated stability at **151 ms**. Target-offset callback to first submitted moving frame was **533, 384 and 415 ms** in these emulator samples, including drawing/decoder overhead. These timings are from this run, not a controlled same-device before/after benchmark or a phone promise.
- Two ordinary away/return sequences paused decoding off-page and resumed on return. A rapid reverse-swipe sequence also ended on the other page with video absent and decoded/rendered counts unchanged across two snapshots.
- Opening Android Settings produced PAUSED_HIDDEN; decoded/rendered/loop/active-time counters remained identical across two seconds. Returning Home resumed rendering. Screenshots were inspected for visible video on target and no video on the other page.
- Frame-return telemetry (`settleMillis`, `lastResumeToFrameMillis`, `VIDEO_RESUME_FRAME`) was read back. The log also records three complete loops after the swipe sequence. Report save UI was verified in 0.4.0 and was not rerun here; its serialization includes the telemetry object without filtering fields.
- Source/bundle ZIP CRC and current alias hash checks completed during packaging. Previous versioned files are preserved.

The emulator encountered a System UI-not-responding dialog during boot; Wait recovered it. No app crash was observed during the final walkthrough. Emulator rendering remained below 30 FPS and is not a proxy for the supplied phone's near-30 FPS result. Screenshots, snapshots, logs, tests, lint and package evidence are in `verification-0.4.1/`.

## Remaining check

The **0.4.1 phone latency and transition isolation are not yet tested**. Follow VIDEO-GUIDE.md; compare return responsiveness and check slow, fast and reversed swipes for leakage. Shortening the hold removes 350 ms of deliberate delay but cannot eliminate launcher notification, surface presentation or decoder latency. A paused swipe near the target, OEM callback timing, lock/overview, display changes and long-term thermal behavior are not exhaustively established. Existing photo/folder functionality was not changed or rerun in this focused update.
