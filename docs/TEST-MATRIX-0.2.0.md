# Physical OnePlus 12R test matrix

Environment: **No physical phone connected during this build. OxygenOS version unknown.** Every target-phone row below is **NOT YET TESTED**. JVM and emulator evidence is recorded separately in VALIDATION.md. The diagnostic app does not auto-fill a PASS from source inspection or arithmetic tests.

User feedback on 0.1.0: the phone reported “bounds received,” but no rectangle appeared. The user then confirmed that they had not applied the live wallpaper. This is recorded as a setup-flow defect, not as evidence that OxygenOS cannot draw the rectangle. Version 0.2.0 adds a checked, numbered setup; its physical-phone results are still pending.

| Test name | Expected behavior | Observed behavior on OnePlus | Status | Known platform limitations | Recommended next action |
|---|---|---|---|---|---|
| 1a Transparent widget | Wallpaper visible through root; thin calibration outline | No physical run | NOT YET TESTED | Host can clip corners and pad widget | Place a widget and inspect background/edges |
| 1b Resize callbacks | Both axes resize; complete option bundle logged | No physical run | NOT YET TESTED | Size hints are dp, not coordinates; keys vary by launcher | Resize each direction; compare WIDGET_OPTIONS logs |
| 1c Multiple instances | Independent widget IDs, captures and folder modes | No physical run | NOT YET TESTED | IDs can change after restore | Add three instances, one per page |
| 2a Source bounds | Valid screen rectangle, ID, dimensions, density, resolution, orientation persist | No physical run | NOT YET TESTED | Some hosts can omit source bounds | Tap and export capture report |
| 2b Root coverage | Centre and four inner corners produce the same rectangle | No physical run | NOT YET TESTED | Host padding/rounded corners differ from root | Execute five taps and compare screenshot/records |
| 2c Invalid/moved bounds | Invalid bounds do not render; recapture follows move | No physical run | NOT YET TESTED | Same-size moves can be silent | Move, tap again; log any null/invalid OEM result |
| 3a Static alignment | All four straight rectangle edges align with widget | No physical run | NOT YET TESTED | Surface crop/viewport and zoom are OEM-dependent | Measure edge errors; try manual mapping |
| 3b Insets/resolution/rotation | No stale calibration used across changed display setup | No physical run | NOT YET TESTED | Navigation/grid changes are not all detectable | Repeat after each change; recalibrate explicitly |
| 3c Zoom/visibility/lifecycle | Zoom logged, PAGE hidden at mismatched zoom, no hidden render loop | No physical run | NOT YET TESTED | Callback ordering and launcher transitions vary | Observe overview, another app, unlock, engine restart |
| 4a Callback logging | All six offset fields, visibility, surface, zoom and insets visible in report | No physical run | NOT YET TESTED | Launcher can send constants, omit offsets or skip zoom callbacks | Export after three-page swipe sequence |
| 4b Three-page repeat survey | Six labelled stable captures distinguish A/B/C on repeat visits | No physical run | NOT YET TESTED | Distinct offsets do not automatically imply page numbers | Run A1 B1 C1 C2 B2 A2; inspect verdict |
| 4c Unsupported handling | Constant/missing/inconsistent values never enable a page gate | No physical run | NOT YET TESTED | Cannot infer missing launcher capabilities from code | Record UNSUPPORTED or INCONCLUSIVE where observed |
| 5a Settled page isolation | Only selected page has its calibrated rectangle | No physical run | NOT YET TESTED | Calibration changes when pages are added/reordered | Enable PAGE after CANDIDATE and inspect all three pages |
| 5b Transition leakage | No rectangle behind unrelated icons even while swiping | No physical run | NOT YET TESTED | Delayed callbacks can leak before first motion report | Record slow/fast/reversed swipes; fail on any leakage |
| 6a Photo picker | User-selected image copied privately; no broad permission | No physical run | NOT YET TESTED | Picker/provider and crop differences | Select and cancel; restart and verify saved background |
| 6b Current static import | Explained permission; successful local save before activation or clear caught error | No physical run | NOT YET TESTED | Recent Android restricts wallpaper reads even for ordinary apps | Test allow and deny, then revoke optional access |
| 6c Live-wallpaper rejection | Another live wallpaper is not claimed as a static import | No physical run | NOT YET TESTED | Android can otherwise return a default image | Activate another live wallpaper and attempt import |
| 7 Video compositor | 10-second local MP4 inside aligned region; background preserved; measured 30 FPS | Deliberately not implemented: target alignment/isolation gate not established | NOT YET TESTED | Codec support, timing, thermals and transition isolation require hardware | Complete gates 3–5 before implementing this stage |
| 8 Folder | Small dialog with chosen icons launches correct apps | No physical run | NOT YET TESTED | Only visible launchable activities; removed apps disappear or fail gracefully | Choose apps, launch each, test unavailable app |
| Diagnostics export | Readable report includes evidence, observations and logs | No physical run | NOT YET TESTED | User chooses save location; scoped providers can fail | Export via Logs and review the saved text |

For every executed test, record actual steps, expected versus observed values, pass/fail, timestamp, device configuration and next action. A negative capability result is useful architectural evidence; it is not an implementation success claim for the original design.
