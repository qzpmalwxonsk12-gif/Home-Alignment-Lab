# Executed validation — 0.3.0-measured

25 October 2026. Package `dev.animatedhome.diagnostics`, version code 3. Minimum API 26, target/compile API 35. Final APK SHA-256: `c27715ae522f25c600d965f85a37050c6e1da88ec1e96a3801b7cf148ccd1949`.

## Physical evidence supplied by the user

The 0.2.0 report and screenshot are from a OnePlus CPH2585, Android 16/API 36, launcher `com.android.launcher` 16.4.28, portrait 1264 × 2780 / 560 dpi. Wallpaper activation and frame submission were recorded. The user selected the misplaced-box result; the screenshot confirms the box extends well past the widget's right and bottom edges. This is a **failed physical alignment check for 0.2.0**, not a pass. The corrected 0.3.0 APK has not yet been run on that phone. The personal screenshot and original full report are not redistributed in this bundle.

## Executed checks

- Final debug build succeeded. **69 JVM tests passed**, 0 failures/errors/skips: 35 architecture, 18 setup policy, 16 measurement regressions.
- Lint: **0 errors, 14 warnings**, including diagnostic strings, compatibility and intentional physical left/right target placement. This is not a store-readiness assessment.
- APK signature and ZIP alignment verified. Signing certificate matches the previous app; update installation succeeded on the emulator. Installed APK hash matches the delivered file.
- Runtime: Android 15/API 35 x86_64 emulator, Launcher3 15, 1080 × 1920, density 2.625. No physical phone was connected.
- A test-only fixture inflated production RemoteViews in a View host and invoked their actual PendingIntent clicks. Android supplied sourceBounds; production capture code measured them. At scale 1.0, raw and measured rectangles matched. At simulated scale 0.6944444, raw layout dimensions stayed 900 × 600; measured visible dimensions were 625.58 × 416.46 (expected 625 × 416.67), within 1 px. This reproduces relevant Android geometry behavior, not OxygenOS itself.
- The fixture also checked corner-before-root rejection, movement requiring a new pair, and environment-change rejection for both scales. It ran before the final change that makes the setup/repeat button reset measurements and turn the outline on; measurement/capture code were unchanged by that final change.
- The **final APK** received a complete normal Launcher3 walkthrough: update → open setup → Go home and tap 1 → tap 1 → guided tap-2 instruction → tap 2 → Show pink box → visibly aligned box → tap 1 → answer → First check complete. Screenshots and final state are saved. The widget remained installed throughout.
- Unit cases cover the supplied raw 964 × 628 geometry with simulated scaling, independent axis scales, pixel rounding, raw rectangle overflow with a valid measured rectangle, invalid/off-screen corrected bounds, null/empty/nonfinite sources, short baselines, impossible scales, resize/movement reuse rejection, and reuse within rounding tolerance.

The emulator briefly displayed a System UI-not-responding dialog during concurrent build/test load. Choosing Wait recovered it; the final walkthrough completed. No app crash was observed during that walkthrough.

## Limits and remaining checks

0.3.0 physical OnePlus alignment is **NOT YET TESTED**. The fixture assumes axis-aligned scaling/translation; arbitrary rotation/skew and proprietary click-bounds substitutions are not established. Keep the widget still between taps. Full three-page/six-visit behavior and slow/fast swipe isolation were not rerun. Older APIs, other OEM launchers, reboot/resolution/navigation transitions and video remain unverified or deferred. Report export UI was exercised in 0.2.0; this revision preserves that path and adds measurement fields to its report/state.

Current evidence: `verification-0.3.0/`. Earlier validation remains in the versioned validation documents and older bundles. The instrumentation helper is test-only and excluded from the user APK. Its static-wallpaper fixture must only run in the disposable emulator, never on the user's phone.
