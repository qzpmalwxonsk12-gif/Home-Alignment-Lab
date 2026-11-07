# Executed validation — 0.2.0-guided

Date: 25 October 2026. Target package: `dev.animatedhome.diagnostics`, version code 2. Android API 26 minimum / API 35 target. The physical OnePlus was not connected; the new APK has not been verified on OxygenOS.

Final APK SHA-256: `8c3599c9e97bd9dcc3b45c0bb0fa5bac84804659ee7033e3ffbc51f4ebcf969d`.

## Build checks

- Debug build succeeded; **53 JVM tests passed, 0 failed, 0 errors, 0 skipped**.
- Lint: **0 errors, 10 warnings**. These include diagnostic strings, target compatibility, newer widget attributes and existing platform API deprecations; not a store-readiness claim.
- Debug signing and ZIP alignment verified. Same debug signing identity as 0.1.0; update installation over 0.1.0 succeeded.
- Installed final APK hash matches the delivered APK.
- ZIP CRC checks and artifact hashes are recorded in the delivery's package-check files.

## Runtime environment and scope

Android 15 / API 35, default x86_64 emulator, Launcher3 version 15, portrait 1080 × 1920, density 2.625 / 420 dpi. A test-only instrumentation fixture set a static home wallpaper to reproduce the missing prerequisite. Launcher data was later reset only in this disposable emulator to exercise adding a fresh widget. The helper is not included in the user APK.

Screens were operated using Android input and inspected using screenshots and UI hierarchy. Most walkthrough checks used an intermediate 0.2.0 build. The final APK additionally received an installed-hash check, fresh-widget capture, visible pink-box drawing with the corrected label, and confirmation-flow checks. No emulator result is presented as OnePlus evidence.

| Case | Observed | Status / scope |
|---|---|---|
| Reproduce old silent failure | On 0.1.0, static home wallpaper + valid widget bounds still offered the 45-second button; it returned home with no rectangle; saved mode was ALIGN with no active home engine | Reproduced in emulator; matches the user's reported setup sequence |
| Update compatibility | Installed 0.2.0 over 0.1.0; existing widget and saved app state remained available | PASS on emulator |
| Widget tapped before wallpaper applied | New capture screen said “First, turn on the wallpaper” and offered Continue wallpaper setup; no pink-box button | PASS on emulator |
| Choose background | Test grid selection advanced from step 1 to step 2 | PASS on emulator; new photo-selection branch not rerun in this revision |
| Cancel wallpaper preview | Returned to step 2 with explicit “not applied yet” guidance | PASS on emulator |
| Apply wallpaper | Android Set wallpaper → Home screen activated the grid; guided setup verified it and advanced | PASS on emulator |
| Existing widget path | Kept existing widget; setup skipped adding another and instructed user to tap it | PASS on emulator |
| Fresh widget path | With no launcher widgets, step 3 opened the native pin prompt; Add to home screen placed a new widget | PASS on emulator |
| Fresh position does not inherit completion | New widget required a fresh check instead of retaining the earlier widget's completion screen | PASS on final emulator build plus regression unit tests |
| Show pink box | Valid widget capture → Show pink box → visible pink rectangle with cyan edges behind the yellow widget; final screenshot uses widget 6 | PASS for this emulator configuration |
| Drawing acknowledgement | Persistent attempt recorded DRAWN/submitted only after canvas posting; 45-second expiry was set from that first posted box | Observed telemetry; does not auto-pass visibility or alignment |
| Return and answer | Tapping the widget again opened step 5 with aligned, misplaced and missing choices; aligned answer completed setup | PASS on emulator |
| Missing-box recovery | Selected “No pink box appeared” deliberately to exercise recovery; app offered report export and retry immediately | PASS for UI branch handling; the box actually rendered during this test, so this synthetic answer is not an observed rendering failure |
| Report export | Native file picker saved `Home-Alignment-Lab-report (1).txt`; 125,484 bytes pulled and read back with version, wallpaper status, attempt and full logs | PASS on emulator; contains the deliberate missing-answer branch check |
| Retry | Retry produced another box; subsequent actual visual alignment confirmation was saved | PASS on emulator |
| Guided page preparation | Next check explained A/B/C setup; attempting to proceed with one widget produced a clear “add enough” message | PASS for preparation and prerequisite guard |
| Full six-visit guided survey / swipe isolation | Not executed across three real pages in this revision | NOT YET TESTED; existing classifier tests do not prove launcher behavior |
| Physical OnePlus / OxygenOS | No attached phone, no new-build device run | NOT YET TESTED |
| Video | Still absent by design pending critical device gates | DEFERRED |

Evidence is in `verification-0.2.0/`: final build/unit/lint outputs, signing/alignment/metadata, installed hash, raw final state and logs, report read-back, and screenshots. Baseline 0.1.0 evidence remains in `verification/` and is described by `VALIDATION-0.1.0.md`.

## Limits

This revision addresses the confirmed setup-flow defect. It does not establish reliable page-specific rendering on OxygenOS, guarantee every navigation/zoom configuration, or reproduce every OEM callback failure. Older-Android wallpaper identity restrictions use a conservative fallback and have not been exercised here. Current-static-wallpaper permission paths, folder app selection, cancellation in every media provider, and all advanced controls were not comprehensively rerun. Video remains gated as required by the original brief.
