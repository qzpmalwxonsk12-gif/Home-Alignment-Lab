# 0.4.1 — faster return to the video page

The supplied 0.4.0 OnePlus report recorded a successful human video observation and 29.66 rendered FPS with zero late drops. Four return samples spent 587, 594, 616 and 666 ms between the final page-A offset callback and the background/rectangle draw request, before decoder/frame presentation. The supplied recording also shows the empty widget after arriving back on A.

The video page gate now requires 150 ms without an offset change, down from 500 ms. A cancellable callback requests a fresh gate evaluation at that deadline instead of relying solely on the visible 100 ms policy poll. Every new offset change cancels/replaces that callback and suspends video immediately. The callback checks the current state; it cannot authorize playback using old offset/visibility information. Hiding/destroying the surface cancels it.

All other validity, page, preview, zoom, keyguard, configuration and timeout checks remain. The pink-box page experiment and evidence-capture stability threshold remain 500 ms. Decoder pause/flush/loop restart behavior is unchanged. No cached frame is displayed on a mismatched page and there is no hidden decoding to hide startup cost.

Added `settleMillis`, `lastResumeToFrameMillis` and a `VIDEO_RESUME_FRAME` log event. These distinguish the deliberate settling delay from decoder/frame submission time; they do not measure the physical panel's display time. Added eight gate regressions, bringing the JVM suite to 92 tests. Version code 5, version name 0.4.1-fast-return. No new permissions, dependency or media asset.

The shorter hold may expose launcher-specific callback timing, so slow/fast/reversed swipes still require a visual phone check. This change removes 350 ms of intentional waiting; it does not promise an exact end-to-end latency. See VALIDATION.md for actual executed checks.
