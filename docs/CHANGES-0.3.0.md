# 0.3.0: measure the visible widget

The supplied OnePlus CPH2585 / Android 16 / launcher 16.4.28 report records a 964 × 628 px source rectangle at approximately (299, 522). The screenshot shows the yellow widget ending around (967, 956), while the pink rectangle ends near (1263, 1150). The starting corner agrees, but the rendered box is about 44% too large in both dimensions. Wallpaper and screen surfaces are both 1264 × 2780; saved mapping adjustments were all at their defaults.

This is consistent with launcher scaling of the widget. Android's [RemoteViews.getSourceBounds implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/core/java/android/widget/RemoteViews.java) uses the transformed on-screen origin and the layout width/height. The proprietary OxygenOS implementation has not been inspected, so that exact internal cause remains an inference.

The widget now has two ordered tap targets. Tap 1 captures the root; tap 2 captures a fixed-size child placed at the bottom-right. For each axis, visible scale = distance between the two on-screen origins / (root layout size − child layout size). The resulting rectangle keeps the root's screen origin and uses its measured visible size. No device-specific multiplier is hard-coded.

Raw root and child source rectangles remain in the report alongside corrected bounds and measured scales. Rendering requires a valid two-tap measurement. Existing 0.2.0 measurements and confirmations cannot silently pass this requirement. Null/empty/nonfinite inputs, unusable origins, impossible scales, tiny measurement baselines, expired pairs, changed environments, and off-screen corrected boxes fail closed. Raw layout rectangles may extend beyond the screen only if a valid measured rectangle fits on screen.

The user must keep the widget in the same position between taps. Resize callbacks invalidate the pair; a subsequent root tap detects movement over the 2 px rounding allowance. Rotation, wallpaper engine changes, and display changes also require measurement again. This method assumes an axis-aligned scale/translation, not arbitrary rotation, skew, or perspective. A visual check is still required; it never automatically marks physical alignment as passed.

Setup remains sequential: wallpaper → widget → tap 1 → tap 2 → Show pink box → report what happened. Technical details remain optional. The video stage remains deferred until real target-device alignment and page isolation are confirmed.
