# Executed validation — 0.4.0-video

25 October 2026. Package `dev.animatedhome.diagnostics`, version code 4. Minimum API 26, target/compile API 35. APK SHA-256: `a30884a6539f3154b6ae4162aeb8308617321c7eb891fe90193cc213880050e3`.

## Physical prerequisite result

The user's 0.3.0 report and statement confirm observed widget alignment and A/B/C isolation on the tested OnePlus CPH2585 / Android 16 / OxygenOS launcher 16.4.28 configuration. The six A/B/C/C/B/A visits have repeatable distinct offsets; the report separately records the user's no-leakage observation. See [the detailed result](ONEPLUS-0.3.0-RESULT.md). This is sufficient to begin the requested video experiment. **0.4.0 video has not yet been tested on that phone.** The supplied personal screenshot and full phone report are not redistributed.

## Build and package checks

- Final debug APK and separate test APK built successfully.
- **84 JVM tests passed**, zero failures/errors/skips: 35 architecture, 18 setup policy, 16 widget measurement, 15 video policy.
- Lint: **0 errors, 14 warnings**. Existing diagnostic strings, target compatibility and deliberate physical left/right placement account for warnings; this is not a store-readiness claim.
- APK v2 signature and ZIP alignment verified. Certificate SHA-256 `f8f158a8d7fd86744f12e15a45ec396bcdd25c3cfd0df2f61669261d6cbf5156` matches prior deliveries. Update installation succeeded; installed APK SHA-256 matches the delivered APK.
- Bundled local clip: H.264, silent, 640 × 360, 300 frames / 30 FPS / 10 seconds. Full file decode succeeded. Asset SHA-256 `835365dffbf697b940afb8d195b4db0dfd5478300a22c94cd8bea3a162a4abdb`.
- Source and bundle archives were CRC-checked; current aliases were hash-checked against the versioned artifacts. Older versioned deliveries are preserved.

## Final-APK runtime checks

Runtime: disposable Android 15/API 35 x86_64 emulator, Launcher3 15, 1080 × 1920 / 420 dpi. The final run used the host AMD Radeon graphics backend. No physical phone was connected. A clearly labelled **test-APK-only synthetic page fixture** unlocked isolated renderer testing. Actual launcher widget taps supplied the fresh two-corner measurement; synthetic A/B/C evidence is not a physical page-test pass. The helper is excluded from the user APK.

| Check | Observed result | Status |
|---|---|---|
| Guided continuation | Saved-check entry, refresh page-A → tap 1 → instruction → tap 2 → Play video test | PASS in emulator |
| Video positioning/composition | Moving diagnostic clip wholly fitted inside measured `[39,107,628,663]`; upright text, black bars preserve proportions, cyan border retained | PASS in observed frames |
| Background preservation | Grid outside widget remained visible; selected wallpaper regions matched pixel-for-pixel between two playing screenshots | PASS in sampled regions |
| Looping | At 25.29 active seconds, 188 rendered frames and two EOS loops recorded | PASS for two loops |
| Other-page pause | Swiped to the other emulator page: no video rectangle visible. Two snapshots three seconds apart held decoded=721, rendered=196, loops=2, activeSeconds=27.6966386 | PASS in executed sequence |
| Return to target page | Playback resumed; decoded=776 and rendered=223, status PLAYING | PASS |
| Invisible pause | Opened Android Settings. Two snapshots three seconds apart held decoded=835, rendered=233, loops=2, activeSeconds=33.0468289, status PAUSED_HIDDEN | PASS |
| Home return | Decoded=847, rendered=236, status PLAYING | PASS |
| Existing static alignment | After video, requested the pink rectangle through the normal advanced control; all straight rectangle edges still matched the visible widget | PASS in observed emulator position |
| Report export | Selected the stutter answer, used Android's Save dialog, read back the saved report with attempt, telemetry, observation and synthetic-fixture label | PASS |
| Smooth 30 FPS | Host graphics run averaged 7.43 rendered FPS at the two-loop snapshot; 467 late frames discarded, p95 gap 488 ms, max gap 1766 ms | **FAIL in emulator; phone not yet tested** |

The earlier final-APK SwiftShader run was worse: approximately 2.47 rendered FPS, despite completing seven loops. Switching graphics backends improved the result but did not establish smooth playback. The goldfish decoder reports `hardwareAccelerated=true`; that emulator flag is not proof of physical OnePlus decoding or performance. The app reports measured FPS and frame gaps separately from the 30 FPS source file.

The emulator displayed a System UI-not-responding dialog around startup; choosing Wait recovered it. No app crash was observed during the recorded final walkthrough. Evidence is in `verification-0.4.0/`, including screenshots, preferences snapshots, exported emulator report, test/lint output and package checks.

## Limits and next step

Install the update on the phone and follow [the ordered video guide](VIDEO-GUIDE.md). Check motion, two loops, alignment, slow/fast swipes and hidden/resume behavior, then save the result. Do not mark smoothness or transition isolation passed before observing it. Thermal behavior, prolonged playback, arbitrary navigation/zoom transitions, other launchers, rotation/display changes and physical codec fallback remain unverified. Photo import and folder shortcuts were not rerun in this revision; earlier versioned validation describes prior coverage. Renderer counters cover its lifetime, including retries, rather than just one attempt. The visible 10 Hz gate check remains, but no decode pump runs while hidden.
