# Home Alignment Lab 0.6.0 — My widgets

Install `Home-Alignment-Lab-0.6.0-multi-widget.apk` over the existing app. Keep the app installed so its saved background and files remain available.

## If your wallpaper and widget are already set up

1. Tap your widget on the home screen.
2. Choose **Choose a GIF** or **Choose a video**.
3. Check the filename and preview, then press **Use this gif / video**. The preview uses the exact private copy that the wallpaper will play.

No pink-box, numbered-corner or A/B/C test is required to switch files. Normal playback has no test timer.

## Add more widgets

Open the app → **Add another widget**. Place it beside the first, resize it, then tap it once to choose its own animation. Repeat for more widgets. Up to **four animations** can be enabled together. Each widget has separate selected/pending files, playback, location, display mode, tap action, errors and Stop controls. GIFs and videos can be mixed. Video playback is silent. More simultaneous animations use more battery and decoder resources; the phone may support fewer simultaneous videos.

After moving or resizing a widget, tap it once to update its location. Android does not reliably notify wallpaper apps when a launcher moves a widget. When you resize an active widget, its yellow outline reappears with **Tap to update size** so you can tap it once to resume playback at the new size. Keep widgets in non-overlapping home-screen positions. The app pauses animations during page transitions, while hidden, on the lock screen and during launcher zoom; it resumes on their saved pages.

## Per-widget Display Modes & Folder Shortcuts

Inside any widget's settings screen:
- **Display mode**: Cycle between:
  - **Fit inside (wallpaper edges)** — preserves aspect ratio and shows your wallpaper background around non-matching edges.
  - **Fill widget (center crop)** — fills the entire widget rectangle edge-to-edge without stretching by center-cropping the UV coordinates.
  - **Fit inside (black bars)** — preserves aspect ratio with solid black bars inside the widget frame.
- **Tap action**: Choose whether tapping the widget opens **animation settings** or opens an **app shortcuts folder** popup while the animation continues playing behind it.

## First-time setup

1. Choose the background around the animations: current/system wallpaper, a photo, or the simple grid.
2. Open wallpaper preview, press Android's **Set wallpaper / Apply**, and choose **Home screen**. Return to the app.
3. Add a widget, place/resize it, and tap it once. Choose a file, check the preview and confirm it.

The live wallpaper supplies the animated pixels; widgets provide placement and taps. Removing or replacing the live wallpaper stops the animations.

## Troubleshoot only the widget that needs it

Tap that widget → **Troubleshoot this widget**. **Adjust position** enables the numbered corners on that widget only. Tap 1, return home and tap 2 without moving it, then return to its controls. This preserves the selected file. Use **Show pink alignment box** if you want to inspect the edges; it temporarily replaces only that widget's animation for 45 seconds. Other widgets retain their settings.

Ordinary taps reuse your earlier measured launcher scale where compatible, otherwise use the launcher's reported rectangle. This is automatic placement, not a claim that every launcher's coordinates are correct. Missing, empty or unusable coordinates never invent a new location. A valid saved location can be retained; a new widget with no usable location needs another ordinary tap or optional adjustment. Launcher/display/grid changes may require a fresh tap or adjustment. Identical offsets on different pages cannot distinguish those pages; the advanced page survey remains available for diagnosis, never as a normal setup requirement.

## If the picker returns a different file

GIF and video buttons use separate document types (`image/gif` and `video/*` with `EXTRA_LOCAL_ONLY`). The importer checks the **actual file header**, not just its extension. Video returned from the GIF picker is rejected with a clear explanation (many social apps store "GIFs" as silent looping MP4s—use **Choose a video** for those, or open the **Files** app from the `☰` menu if your gallery's Recents view returned the wrong item). Cancel a preview that is not the file you wanted. A failed import stops that widget and retains its old file without silently substituting it. **Keep previous animation** explicitly clears the failure; press Play to use it again. Other widgets keep their own files.

## Current / system wallpaper

This copies an accessible **static** system wallpaper into the app. Recent Android versions may require optional **All files access** and **Photos / Allow all photos** permissions to read it. The app explains these before requesting them. If you prefer not to grant them, choose a photo with the system picker instead; picking individual files needs no broad storage access. Permissions can be revoked after copying.

If this app's live wallpaper is already active, the option keeps its saved background. Android cannot recover a static picture that was already replaced and never copied into the app. Another live wallpaper cannot be imported as a static original; choose its source image instead.

## File limits

- Videos: 100 MB, two minutes, 1080p in either orientation, up to 60 FPS. Use a standard H.264 MP4 for broad compatibility. No transcoding; codec availability depends on the phone.
- GIFs: 10 MB, up to **1920 × 1920** pixels, **600 frames**, two minutes, and **120 million canvas pixels** across all frames. High-resolution GIFs are automatically rendered to an efficient 512 px GPU texture to keep memory usage under 1 MB per widget. GIF89a frame delays are parsed directly from the file when Android's `Movie.duration()` reports `0`. Transparency is supported.
- Files are copied privately; originals are untouched. Old unused private copies are cleaned at the next process start. Active and pending copies for every widget are preserved.

The optional report includes filenames, source hashes, widget geometry and playback logs. It is saved locally; nothing is uploaded automatically.
