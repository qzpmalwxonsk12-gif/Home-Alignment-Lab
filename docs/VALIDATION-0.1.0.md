# Executed validation — 25 October 2026

This is a diagnostic APK, not a completed animated home-screen product. No physical OnePlus was connected. Every OnePlus/OxygenOS row in [TEST-MATRIX.md](TEST-MATRIX.md) remains **NOT YET TESTED**. Video is deliberately deferred until target-device alignment and page isolation pass.

## Delivered build

- Package: `dev.animatedhome.diagnostics`, version `0.1.0-validation`, version code 1.
- Minimum API 26; compile/target API 35. JDK 17, Gradle 8.9, AGP 8.7.3, Kotlin 1.9.22.
- Debug APK SHA-256: `1d929373c2a560c26ee0cf34be05c39a655516f3caca1cabfffbdcd4ead85e33`.
- The installed final emulator APK has the same SHA-256 as the delivered APK.
- Final build, JVM tests and lint completed successfully. **35 tests, 0 failures, 0 errors, 0 skipped. Lint: 0 errors, 9 warnings.**
- APK v2 debug signature and ZIP alignment verified. Warnings include the intentional API 35 compatibility target, widget XML attributes used on newer launchers, and untranslated diagnostic strings. These are not a claim of production/store readiness.

Evidence is supplied in the delivery's `verification/` folder. JVM tests exercise geometry, invalid rectangles, mapping arithmetic, the repeated-offset classifier and render guards. They do not run a launcher, wallpaper compositor or physical device.

## Emulator environment

Android 15 / API 35, default x86_64 emulator image `AE3A.240806.019`, Launcher3 version 15; portrait 1080 × 1920, 420 dpi (density 2.625), three-button navigation. This was a workspace-local headless emulator. It is not OxygenOS. Device log timestamps are the emulator's timestamps and need not match the host timezone.

Runtime checks below were executed during the build sequence. The final APK was then installed, its hash matched, and final launch, persisted photo, live-wallpaper import rejection, report export and a fresh widget/folder capture were checked. Earlier screenshots retain their original captures; they are not presented as reruns of every test on the last binary.

| Test | Expected | Observed | Status and limit | Next action |
|---|---|---|---|---|
| Build / unit tests / lint | Compiles, tests pass, no lint errors | Build successful; 35/35 JVM tests; lint 0 errors / 9 warnings | PASS for these automated checks | Run target-phone matrix |
| Install and launch | Native diagnostic UI opens | Final debug APK installed and opened on API 35 | PASS on this emulator | Install on OnePlus |
| Live wallpaper activation | System preview and home wallpaper render | Grid preview and activated wallpaper visible | PASS on this emulator | Test OxygenOS picker/lifecycle |
| Transparent root | Wallpaper visible through widget interior | Widget ID 3 outlined over grid, later imported photo | PASS for one instance | Add independent instances on three pages |
| Five-point root bounds | Four inner corners and centre deliver same root rectangle with fresh captures | Five distinct tap UUIDs; all bounds `[39,107,628,663]`, valid | PASS for this unchanged widget/configuration | Repeat on OnePlus and after movement |
| Horizontal / vertical resize | Options update; stale capture disarms; recapture follows new size | Both axes resized; options changed; invalid calibration with mode OFF; new valid bounds `[39,107,835,957]` | PASS for executed resize sequence | Repeat across launcher grid/display settings |
| Static alignment | Test rectangle's straight edges match widget | CENTER, dx=dy=0, scale=1. Cyan 4 px stroke extent `[37,105,629,664]` around captured edges `[39,107,628,663]`; screenshot inspected | PASS for this single static configuration; host corners are rounded and test rectangle is square | Measure moved/resized positions, zoom, unlock and target-device error |
| Offset / surface telemetry | Raw callback values are recorded | Six offset fields, surface, insets, visibility and zoom logs observed; xOffset changed between 0 and 1 on two pages | PASS for logging; not a page-isolation result | Execute six visits across at least three pages |
| Three-page repeated survey | Distinct repeatable A/B/C evidence | Full six-visit launcher survey was not executed | NOT YET TESTED; classifier only unit-tested | Follow guide section 4 |
| Page-specific rendering / swipe leakage | No rectangle behind other pages' icons | Full settled/transition isolation sequence was not executed | NOT YET TESTED | Follow guide section 5; fail on any leakage |
| Photo import | User-selected image saves privately without broad access | System picker selected 800 × 1200 fixture; private JPEG saved; photo visible as wallpaper and retained after update; All files access not granted | PASS for selected-image path and persistence | Test cancellation, other providers, process restart and phone |
| Existing live wallpaper import | Clear rejection instead of default-image substitution | Active lab live wallpaper rejected by final build, with photo alternative | PASS for this live-wallpaper case | Repeat with another live wallpaper on phone |
| Existing static wallpaper / broad access | Saves static image or reports caught failure | Grant/deny/revoke path with an actual static system wallpaper not executed | NOT YET TESTED | Test optional permission path on phone |
| Folder popup and launch | Dialog with app icons launches correct component | Default Calendar/Camera/Clock/Contacts shown; Clock opened `com.android.deskclock/.DeskClock` | PASS for default popup and one Clock launch; custom selection/removal untested | Choose apps and exercise each shortcut / unavailable app |
| Final capture invalidation | New measurement includes environment and explicitly disarms rendering | Final APK captured resized bounds, stable offsets, surface `1080x1920`, inset signature; mode OFF afterward; folder popup appeared | PASS for this final smoke check | Re-arm only after checking calibration |
| Export | Chosen document contains readable report and full logs | System document picker saved `Home-Alignment-Lab-report.txt`; 83,728 bytes read back with device, widgets, page evidence and full log sections | PASS on this emulator | Export a physical-phone report |
| Video / 30 FPS | Only implemented after critical gates pass | No decoder, MP4 or video compositor included | NOT YET TESTED / DEFERRED | Establish phone alignment and isolation, then implement stage 7 |

## Evidence index

- `build.txt`, `unit-tests.xml`, `lint.txt`: final build results.
- `apk-signature.txt`, `apk-alignment.txt`, `apk-metadata.txt`, `installed-apk-sha256.txt`: final APK checks.
- `five-point-root.json`, `alignment-pixel-check.json`: executed root/edge measurements.
- `resize-state.xml`, `resize-recapture.xml`, `final-recapture.xml`: captured state from resize/final checks.
- `emulator-exported-report.txt`: actual report saved through the app's document picker.
- `emulator-diagnostics.log`: persistent runtime log, including callbacks and captures.
- `photo-import-permission-state.txt`: optional All files access was not granted for the photo test.
- `screenshots/`: original screenshots of alignment, resizing, import, folder launch, rejection and export.

## Remaining architecture decision

Do not infer OnePlus support from these results. Reliable page detection, transition isolation and stable compositor geometry must be established on the actual launcher. Recalibrate after moves, resize, launcher/page/grid changes, navigation/resolution changes or engine restart. Source bounds cannot continuously track silent widget moves. The guide and architecture notes describe failure handling and fallback tradeoffs; no overlay or animated Activity substitutes for the wallpaper.
