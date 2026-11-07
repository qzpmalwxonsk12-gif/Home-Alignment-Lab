# Architecture assessment — 0.5.0

## Current decision

The 0.3.0 OnePlus CPH2585 / Android 16 / launcher 16.4.28 report contains successful user-observed alignment, six distinguishable repeatable A/B/C readings, and a user observation of no leakage in the swipes performed. This clears the prerequisite for the bounded video experiment. It does not validate all layouts, transitions or devices. See ONEPLUS-0.3.0-RESULT.md.

## Position and page evidence

CalibrationWidget uses a transparent full-root RemoteViews click and a fixed-size bottom-right child click, each with an explicit widget-specific mutable PendingIntent. CaptureActivity reads Android-supplied sourceBounds. Because sourceBounds can combine a transformed origin with unscaled layout dimensions, WidgetMeasurement uses both origins and the child layout size to recover visible width/height. No OEM multiplier or dp-size-as-position assumption is used. Raw, corner and measured rectangles remain separate in reports. Null/invalid measurements, short baselines and off-screen measured rectangles fail closed. Keep the widget still between taps; a subsequent root tap detects movement beyond rounding tolerance, and resize callbacks invalidate the pair.

PageEvidence compares six distinct labelled A/B/C visits across two rounds. It rejects missing, moving, changed-environment, indistinguishable or inconsistent observations. Offset steps are not converted to a page count. CANDIDATE means numeric evidence only; a separate human observation checks swipe leakage.

Video start requires the recorded alignment/isolation observations, a valid fresh page-A measurement, and a matching page-A offset including step values. Historical visit evidence may survive an engine-only restart if the launcher version, display dimensions/density/rotation, surface and insets still match. Changed layout/configuration requires repeating the survey. Runtime visibility, keyguard, current offset, settling and zoom gates remain active. Callback latency can still cause transition leakage, so the video stage repeats visual swipe testing.

## Rendering and lifecycle

LabWallpaper owns a native WallpaperService.Engine. One WallpaperCompositor owns the EGL producer for the lifetime of each Android surface; it never alternates SurfaceHolder.lockCanvas with EGL on that surface. Existing grid/photo/pink-box scenes are drawn on an offscreen Canvas only when state changes, coalesced, uploaded as a static texture, and recycled. The surrounding static wallpaper remains present while video is composited into a clipped inner rectangle. Aspect-fit preserves the complete clip with black bars as needed.

MediaExtractor reads the bundled MP4. MediaCodec prefers platform-identified hardware AVC decoders on API 29+, falling through when configuration fails; older APIs identify hardware status as unknown. The decoder writes into a SurfaceTexture/external OES texture. A dedicated handler thread owns decoder, texture updates and EGL. Presentation timestamps pace frames. EOS flushes/seeks to the clip start; moderately late frames are discarded and long stalls resynchronize the clock, with both recorded in diagnostics.

Hidden/off-page gates stop the pump and flush queued data; resume restarts the loop. Surface loss destroys decoder, SurfaceTexture and EGL resources. Static drawing has no hidden timer; a visible 10 Hz policy check handles timeout, keyguard and settling. Pending scenes carry a gate generation so a stale upload cannot re-enable video after a newer page/visibility event. First-frame acknowledgement occurs after a posted video frame, not merely after selecting a decoder.

The report records decoder metadata, source dimensions/duration/FPS, rendered/decoded/dropped frames, loops, pauses, active-time FPS, frame-gap statistics and clock recoveries. Counters cover the current renderer lifetime. They cannot establish thermal safety, universal 30 FPS, or phone performance from emulator results.

## Other components

BackgroundImport retains private local JPEGs, the optional current-static-wallpaper route and the permission-free photo picker. FolderActivity uses MAIN/LAUNCHER visibility and explicit launch intents for user-selected shortcuts. No new permissions, Internet access, overlay, accessibility service or QUERY_ALL_PACKAGES are added. The media library and broader customization system are outside this architecture-validation build.

## Limitations and fallbacks

Android does not expose the full wallpaper compositor transform or reliable notifications for every widget/page move. Manual viewport crop, translation and scale controls remain diagnostic fallbacks. If offsets cease to be useful, page-specific playback stays disabled. A custom launcher owning both layout and page state is the stronger architectural fallback for persistent geometry/transition failures; it must be a separately chosen scope. No overlay or animated Activity is silently substituted.

References: [RemoteViews](https://developer.android.com/reference/android/widget/RemoteViews), [WallpaperService.Engine](https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine), [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec), [SurfaceTexture](https://developer.android.com/reference/android/graphics/SurfaceTexture), [MediaCodecInfo](https://developer.android.com/reference/android/media/MediaCodecInfo).

## 0.4.1 return responsiveness

Video requires 150 ms of unchanged offsets and uses a cancellable deadline callback to re-evaluate all current gates. New motion immediately suspends decoding and resets the deadline. PAGE diagnostics and capture evidence retain their 500 ms stability requirement. No decoder prewarming or cached-frame display was added. See CHANGES-0.4.1.md and VALIDATION.md.

## Personal media in 0.5.0

One selected local file feeds the existing page-A compositor. Atomic import, media policy, direct GIF alpha rendering, private-copy persistence and restart limitations are described in CHANGES-0.5.0.md. MY-MEDIA-GUIDE.md is the current user entry point. Older video diagnostic procedures describe their respective versions.


## 0.6.0 update

See CHANGES-0.6.0.md for staged media confirmation and source-bound rendering, saved calibration restoration constraints, and the guided system-wallpaper option. These supersede earlier descriptions requiring every engine restart to remeasure. Full layout/profile changes still require recalibration; file changes do not.


## 0.6 multiple widgets

WidgetAnimations keys personal settings by Android widget ID. Ordinary sourceBounds taps establish automatic geometry and an offset signature, reusing compatible measured scale when available; no survey evidence is fabricated. WidgetPlacement gates by profile, offset plus page-step, zoom, visibility, home/lock and 150 ms stability. It cannot distinguish pages whose launcher reports identical offsets. Optional measurement corrects only the selected widget.

The compositor retains one EGL producer and one static background texture. Each enabled LayerSpec supplies an immutable source snapshot and a nullable current-page box. Separate MediaLayer objects own GIF/codec/SurfaceTexture state. Off-page layers pause and retain resources for quick return; stopped/replaced/deleted layers release resources. Up to four enabled sources bound decoder/GIF resource growth. Actual hardware decoder limits can be lower. Per-layer decode failure reports to that widget; EGL/surface failure remains renderer-wide.

Typed import copies to a unique app-private path, checks content/limits, hashes it and stages it per widget. Confirmation publishes selectedMedia; Play copies it to that widget's playbackMedia snapshot. Preview and rendering read the same copy. Application startup migration precedes cleanup; all widgets' selected, pending and playback paths are retained. Providers can still return incorrect same-type content, so the visible filename/preview confirmation remains essential.
