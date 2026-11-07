# Current release: 0.7.1-page-fallback

Use [START-HERE-0.7.1.md](START-HERE-0.7.1.md) and **Home-Alignment-Lab-0.7.1-page-fallback.apk** for the current patch. The older 0.6.0 instructions below are retained as historical reference only.

# Historical instructions — 0.6.0-multi-widget

## Install on your phone

1. Copy **[Home-Alignment-Lab-0.6.0-multi-widget.apk](Home-Alignment-Lab-0.6.0-multi-widget.apk)** to your OnePlus 12R and install it over your existing Home Alignment Lab app (do not uninstall first if you want to keep your saved background and widget setup).
2. Tap your existing widget on the home screen to choose a GIF or video, or open **Home Alignment Lab** → **Add another widget** to place additional animated widgets on your home screen (up to **4 simultaneous animations**).

## What's new & polished in 0.6.0-multi-widget

1. **Multiple Independent Animated Widgets (up to 4 at once)**
   - Add multiple widgets on the same home screen or across pages; each widget independently plays its own GIF or MP4 video, with its own stop/play/troubleshoot controls.
2. **No Setup Tests Needed**
   - Just resize a widget and **tap it once** on the home screen to place it automatically (reusing your phone's measured launcher scale). The 2-corner tap and pink alignment box are now optional tools under **Troubleshoot this widget**.
3. **Fixed GIF Picker Mismatch & High-Res GIF Support**
   - **Choose a GIF** (`image/gif`) and **Choose a video** (`video/*`) are now separate buttons with strict magic-header verification and a mandatory live preview of the exact private copy before it plays.
   - Real-world high-resolution GIFs up to **1920 × 1920** and **600 frames** now import cleanly and are automatically scaled to an efficient 512 px GPU texture (< 1 MB RAM).
   - GIFs where Android's `Movie.duration()` reports `0` now have their frame delays parsed directly from the GIF89a Graphic Control Extension blocks so they animate properly.
4. **Per-Widget Display Modes (`Fit` / `Fill` / `Black bars`)**
   - Inside any widget's screen, tap **Display mode** to switch between:
     - **Fit inside (wallpaper edges)** — no black bars around your video/GIF; empty widget margins show your wallpaper background.
     - **Fill widget (center crop)** — fills the entire widget edge-to-edge without stretching.
     - **Fit inside (black bars)** — letterboxes/pillarboxes with black bars inside the widget frame.
5. **Resize Recovery & Folder Mode Compatibility**
   - If you resize a playing widget, its yellow outline automatically reappears with **"Tap to update size"**—tap it once on the home screen and the outline disappears while the animation updates to the new size.
   - You can also toggle **Tap action** between opening animation settings vs. opening an **app shortcuts folder** popup while the animation continues playing behind it.

See [../docs/MY-MEDIA-GUIDE.md](../docs/MY-MEDIA-GUIDE.md) and [../docs/CHANGES-0.6.0.md](../docs/CHANGES-0.6.0.md) for full details.
