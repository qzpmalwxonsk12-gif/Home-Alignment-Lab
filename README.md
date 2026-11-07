# Home Alignment Lab

An Android live-wallpaper-backed widget project for independently positioned GIF/video playback, with media selection, streaming/synchronisation options and launcher diagnostics. The current source declares **0.7.1-page-fallback**, version code 9.

Normal media use is separate from calibration. You do not need to complete an A/B/C launcher test merely to choose another GIF or video.

## Main capabilities

- Independent local media layers behind home-screen widgets.
- File selection/preview and per-widget page/playback options.
- Live-wallpaper rendering, positioning and media lifecycle handling.
- Streaming/synchronisation work retained in this Antigravity source variant.
- Exportable diagnostics for launcher offsets and widget/page assignments.
- An optional, user-anchored swipe estimate for launchers that do not expose useful native page offsets.

## Requirements and build

- Android **8.0/API 26 or later**; compile/target SDK 35.
- JDK 17 and an Android SDK installation.
- A launcher/device combination that supports the required live-wallpaper and widget behaviour.

Configure the SDK path locally, then build from the repository root:

```powershell
.\gradlew.bat :app:assembleDebug
```

The local APK is generated under `app/build/outputs/apk/debug/`. APKs and machine-specific `local.properties` are excluded from this source snapshot. Install your locally built APK, select the app's wallpaper/widget setup, and use the in-app media controls. The [media guide](docs/MY-MEDIA-GUIDE.md) describes normal file selection and playback.

## Xiaomi swipe fallback

The fallback is **experimental and off by default**. In Diagnostics, enter the real page count, assign widgets to their actual pages, enable the fallback, then tap a widget on its real page to anchor the estimate. Re-anchor after a wallpaper restart or when the estimate drifts.

Home-button jumps, missed/cancelled gestures, looping pages and launcher touch routing can desynchronise the estimate. Native launcher readings retain priority. **Estimated swipe positions do not validate native A/B/C calibration and do not change wallpaper crop mapping.** If the raw launcher readings remain fixed, an unsupported native-offset result remains unsupported even if estimated playback is helpful.

See the [0.7.1 guide](releases/START-HERE-0.7.1.md) for the exact procedure, limitations and optional six-visit A/B/C sequence.

## Source layout

| Path | Purpose |
|---|---|
| `app/` | Android UI, wallpaper/widget services and media/diagnostic logic |
| `docs/` | Media use, architecture, device test instructions and historical results |
| `releases/` | Source-distribution setup notes; generated APKs are omitted |
| `gradle/` and root Gradle files | Build configuration |

## Checks and troubleshooting

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

Instrumentation checks require a suitable connected device/emulator. Use the in-app diagnostic report to distinguish native readings from estimates. Reports can contain filenames and stream URLs; review them before sharing.

Historical test/device evidence is in [VALIDATION-0.7.1.md](docs/VALIDATION-0.7.1.md) and the [device test guide](docs/DEVICE-TEST-GUIDE.md). These are scoped development observations, not proof of every physical launcher's behaviour. This README update did not perform new Android or physical-Xiaomi testing.

See [architecture](docs/ARCHITECTURE.md) and [backup notes](BACKUP_NOTES.md) for implementation detail and omitted artifacts.
