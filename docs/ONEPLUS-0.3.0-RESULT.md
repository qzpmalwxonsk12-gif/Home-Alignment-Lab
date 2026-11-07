# OnePlus result supplied on 25 October 2026

Source: user-supplied Home-Alignment-Lab-report (1).txt and the user's statement that the widget and A/B/C test worked. The original report remains in the user's Downloads; it is not bundled here.

Configuration: OnePlus CPH2585, Android 16/API 36, build CPH2585_16.0.5.1002(EX01), launcher com.android.launcher 16.4.28, portrait 1264 × 2780, density 3.5 / 560 dpi, wallpaper surface 1264 × 2780.

| Check | Evidence | Result |
|---|---|---|
| Wallpaper active | Report says true; submitted alignment frames logged | Observed active |
| Corrected alignment | User selected aligned for widgets 57 and 59 and confirmed success in chat | User-observed pass at tested positions |
| Source-size correction | Widget 59 raw 964 × 628 px became approximately 669.71 × 436.87 px using the two measured origins | Correction applied; visual result confirmed by user |
| A/B/C repeat survey | Six distinct taps, stable observations, same environment; A/B/C/C/B/A | Repeatable, distinguishable offsets |
| Page A | Both visits xOffset 0.45454547, xPixelOffset −689 | Reproduced |
| Page B | Both visits xOffset 0.54545456, xPixelOffset −827 | Reproduced |
| Page C | Both visits xOffset 0.6363636, xPixelOffset −965 | Reproduced |
| Swipe isolation | User selected no leakage in the swipes performed, logged at 15:35:10.749 | User-observed pass for those swipes |

This satisfies the initial alignment/page-isolation prerequisite for implementing the bounded local-video experiment. It does not establish every launcher transition, display setting, restart, or other device. No video performance result was present in the supplied report.

The automatic classifier remains CANDIDATE because numeric offsets alone cannot certify visual isolation. The separate human observation is the additional evidence that advances this build to video testing.
