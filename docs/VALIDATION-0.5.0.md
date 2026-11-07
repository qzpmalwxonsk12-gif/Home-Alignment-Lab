# Executed validation — 0.5.0-my-media

25 October 2026. Package `dev.animatedhome.diagnostics`, version code 6. APK SHA-256 `e78dc44ee4589bdb3c2cdaa097e01995c5fca4e54ef2625662513e73fc9751ce`. Minimum API 26; target/compile 35.

## Build and automated checks

- Build succeeded; **110 JVM tests passed**, zero errors/failures/skips. Eighteen new tests cover bounded/empty imports, GIF structure and dimensions, excessive frames, and video dimensions/duration/frame-rate/rotation limits.
- Lint: **0 errors, 14 warnings**. Existing debug certificate retained; APK signature and ZIP alignment verified. Update installation succeeded, and the installed APK hash equals the delivered APK.
- Android instrumentation checked actual Movie frame changes and alpha, rejected truncated/oversized GIFs, verified MP4 decoder availability and rotated MP4 metadata. The first rotated test asset lacked its intended rotation metadata; its generator was corrected, and the final native run passed. Fixtures live only in androidTest assets and are excluded from the user APK.

## Executed UI and wallpaper checks

Final APK on a disposable Android 15/API 35 x86_64 emulator, Launcher3 15, host AMD Radeon graphics, 1080 × 1920 / 420 dpi. Historical page prerequisites are explicitly synthetic fixture data. Normal launcher taps refreshed the widget's two corners. **No physical phone was connected.**

- Opened the saved-results entry, exited the older diagnostic observation screen through “Choose my own video or GIF”, refreshed the two corners, and reached the personal animation controls.
- Used Android's real document picker to import a 320 × 180 H.264 MP4. It rendered, looped and stayed inside the measured [39,107,628,663] rectangle with aspect-preserving black bars. Numbered guides disappeared.
- Switched through the same picker to a 160 × 100 transparent GIF. Different screenshots show different moving frames; clear pixels expose the wallpaper grid. Native tests also verify frame changes and transparent pixels. GIF loops were logged.
- A page swipe produced PAUSED_OFF_PAGE with decoded/rendered/loop/active-time counters identical across two snapshots; the other page screenshot contains no GIF. Returning resumed animation. Opening Settings produced PAUSED_HIDDEN with identical counters across two snapshots, then Home resumed rendering. These checks establish sampled states, not exhaustive transition coverage.
- Importing a deliberately truncated GIF displayed an error and kept the previous selected GIF; pressing Play successfully resumed it. Canceling the picker retained the selection with mode OFF. Stop cleared active mode and displayed confirmation.
- A video carrying 90-degree rotation metadata rendered in portrait orientation, with reported output dimensions 180 × 320. Personal playback remained PLAYING more than 90 seconds after its first frame, with no diagnostic deadline.
- App-private copies hash-match the selected fixture files. The synthetic original MP4 was moved after import and playback continued. Selected metadata and its private file survived a forced app process restart after playback had been stopped. Android removed the live wallpaper after force-stop, and the app correctly showed the wallpaper setup step. Original user media was never accessed or modified in these checks.
- Saved a report through Android's document UI and read it back; it contains selected media metadata and playback telemetry.
- Screenshots were visually inspected; runtime assertions, native output, raw emulator snapshots/logs and build evidence are in `verification-0.5.0/`. Source and bundle ZIP CRC checks and current-alias hashes are checked during packaging. Earlier versioned outputs remain available.

## Scope and remaining checks

This version plays one selected file in the established page-A widget, silently. It does not transcode, play multiple independent animations, preserve arbitrary aspect ratios by stretching, or silently restore stale coordinates after a wallpaper restart. A restart/reboot can require opening the app, refreshing the position and pressing Play again; the imported file stays saved. Video codecs depend on the phone. GIF limits include the additional 16-million canvas-pixel-frame work budget. See MY-MEDIA-GUIDE.md for all limits.

The user's earlier **0.4.0 OnePlus CPH2585 / Android 16 video test passed** for the supplied observation: about 29.66 FPS, zero late drops, and correct alignment/isolation, with a delayed page return. That phone evidence is preserved in VALIDATION-0.4.1.md and is distinct from this release. The 150 ms video settling policy from 0.4.1 is retained.

**Personal media import/playback on the physical OnePlus is still awaiting the user's check.** Emulator evidence does not prove arbitrary files/codecs, every GIF disposal mode, API-26 runtime behavior, low-memory conditions, all OEM transitions, reboot recovery, long-term thermal/battery performance or DRM playback. Photo/folder features were not changed or comprehensively rerun in this update. The report contains the imported file name; nothing is uploaded automatically.
