# Physical OnePlus test matrix — current status

Updated 25 October 2026 from the user's 0.3.0 report and confirmation. Device: CPH2585, Android 16/API 36, launcher `com.android.launcher` 16.4.28, 1264 × 2780 / 560 dpi. The phone was operated by the user; no direct phone connection was available. A pass below is scoped to that reported configuration and observation. [Executed emulator checks](VALIDATION.md) are recorded separately.

| Test | Expected behavior | Physical observation | Status | Limitation | Next action |
|---|---|---|---|---|---|
| Transparent widget and multiple instances | Wallpaper visible inside root; independent IDs | User confirmed widget test; report has separate instances 57, 58, 59 | PASS for reported use | Exhaustive padding/corner checks absent | Retain widgets for video test |
| Resize callbacks | Both-axis changes logged | Options recorded, but deliberate resize sequence not established | NOT YET TESTED | Sizing hints do not give coordinates | Test separately after preserving current configuration |
| Source bounds and measured correction | Valid visible rectangle persisted | Two-corner correction applied; user confirmed aligned | PASS for tested positions | Raw OEM bounds differ from visible dimensions | Refresh A's position after update |
| Root coverage | Root taps repeat except the dedicated corner target | Five-point coverage sequence not supplied | NOT YET TESTED | Target 2 intentionally has different bounds | Exclude target 2 from root-coverage taps |
| Invalid/moved bounds | Reject invalid sources and require recapture | No deliberate invalid-source physical sequence established | NOT YET TESTED | Silent same-size moves are possible | Tap again after moves; report rejected sources |
| Static alignment | Four straight edges align | User selected aligned and confirmed in chat on 0.3.0 | PASS, user observed | Other positions/configurations remain unproven | Proceed to bounded video experiment |
| Display/rotation/insets changes | Stale calibration rejected | No changed-display sequence supplied | NOT YET TESTED | Some launcher/grid changes are not detectable | Recalibrate after changes |
| Lifecycle/zoom/lock | No stale or hidden rendering | Not comprehensively observed on phone | NOT YET TESTED | Callback order varies by launcher | Include app-hide/return in video check |
| Offset callback logging | Six offset fields and environment saved | Six labelled stable visits with complete offsets | PASS in report | Callback latency is not established | Retain numeric data with visual result |
| Three-page repeat survey | A/B/C distinguishable across repeat visits | A=.45454547/-689; B=.54545456/-827; C=.6363636/-965, each reproduced | PASS for numeric repeatability | Numeric classifier alone is only CANDIDATE | Pair with human isolation observation |
| Unsupported handling | Missing/constant values do not enable gate | This launcher supplied useful values | NOT YET TESTED for unsupported launcher | No universal page-number inference | Test only if a launcher lacks useful callbacks |
| Page isolation/swipes | Test rectangle only on A | User reported no leakage in performed swipes | PASS, user observed | Does not certify every transition | Repeat slow/fast swipes with video |
| Photo picker/static import | User-selected static background saved locally | Photo background shown previously, full picker/import sequence not documented | NOT YET TESTED comprehensively | Wallpaper reads restricted; another live wallpaper cannot be copied | Separate permission/cancel/persistence checks |
| Video compositor and smoothness | 10-second local loop inside widget; background retained; target 30 FPS | 0.4.0 user-observed success; 29.66 FPS, zero late drops, one logged loop, six pauses | PASS for supplied 0.4.0 observation; 0.4.1 timing pending | Return delay about one second reported; shorter wait now implemented | Follow VIDEO-GUIDE.md and compare return delay |
| Folder shortcuts | Selected app icons launch correct apps | No physical folder sequence supplied | NOT YET TESTED | Only visible launchable apps supported | Separate optional folder test |
| Diagnostic export | Readable evidence and observation report | User supplied readable 0.3.0 report | PASS for supplied export | Other providers untested | Save 0.4.0 video report in Downloads |

Earlier 0.2.0 misalignment remains a failed result; the subsequent 0.3.0 correction passed the user's check. The earlier matrix is preserved in TEST-MATRIX-0.2.0.md. Do not use historical pending statuses as current results, or infer a universal pass from a single device.

0.4.0 video follow-up: the user supplied report (2) and a 9.57-second recording. Summary metrics are in verification-0.4.1/phone-0.4.0-summary.json. The personal recording/report are not redistributed.

## 0.5.0 personal media follow-up

Personal video/GIF import, transparency, switching and saved-copy persistence are **NOT YET TESTED on the physical OnePlus**. Executed emulator evidence is in VALIDATION-0.5.0.md. Follow MY-MEDIA-GUIDE.md to choose and play a personal file in the existing measured page-A widget. Earlier physical passes above remain historical evidence, not a pass for the new importer or GIF path.
