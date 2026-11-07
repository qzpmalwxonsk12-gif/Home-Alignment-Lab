# 0.7.1-page-fallback — 2026-10-28

Version code 9. Continues Gemini's interrupted Xiaomi static-offset patch in the provided Antigravity copy.

The latest Xiaomi report (2312DRAABI, Android 15, com.miui.home RELEASE-7.50.06.2592-09011644) records six distinct visits with identical xOffset=0, yOffset=0.5, xOffsetStep=-1, yOffsetStep=-1 and pixel offsets=0. These readings cannot distinguish pages. The report does not establish why they are fixed or whether a launcher setting will help.

Incoming source failed compilation because it called unfinished Live virtual-page functions. It also substituted label-based positions for missing/fixed diagnostic offsets and could copy one tap into both rounds with different UUIDs.

## Changes

- Added a separately enabled, user-anchored swipe estimator: 2–10 pages, per-widget assignment, enable/disable, resync and gesture status.
- Estimates start unknown and reset on engine/configuration changes. A tap on an assigned widget anchors the estimate. Horizontal gestures stay within the configured pages. Taps, edge gestures, vertical gestures, long drags, cancellations and gestures without a down event do not advance it. Assigned animations hide during horizontal dragging and return using the settled estimate; this is not pixel-perfect finger tracking.
- Raw launcher offsets stay raw. Changing native offsets take priority; all-pages pinning still bypasses filtering. Estimates affect only assigned widgets and never wallpaper crop mapping or A/B/C evidence.
- A/B/C saves one fresh stable capture per visit. Missing/stale/moving/reused taps and inconsistent widget assignments are rejected. Removed duplicated-round quick-save and guessed-position migrations.
- Older readings are archived once in reports and excluded from active results. Widget media and geometry remain intact. Reports distinguish raw readings from estimates.
- Preserved earlier jitter tolerance, battery fixes, streaming, sync and UI features. Updated release naming and instructions.

## Limits

This fallback is experimental and unverified on the physical Xiaomi phone. Gesture motion does not prove the launcher changed pages. Home-button jumps, missed gestures and cancelled gestures can desynchronize estimates. Resync or disable it when needed. No accessibility service, overlay permission or background polling service was added.

API reference: [Android wallpaper touch events](https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine#setTouchEventsEnabled(boolean)).
