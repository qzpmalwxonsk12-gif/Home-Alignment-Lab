> Historical detailed 0.2.0 procedure. For the current next step, use [VIDEO-GUIDE.md](VIDEO-GUIDE.md). Current physical results are in [TEST-MATRIX.md](TEST-MATRIX.md); statements below about pending alignment/video reflect the older build.

# OnePlus 12R / OxygenOS manual test guide

**Version 0.2.0:** Start with [the numbered setup guide](GUIDED-SETUP.md). The app now checks wallpaper activation first and asks whether the pink box appeared. The detailed controls described below are available under **Help → Advanced tools**. On a widget capture screen, old page-label buttons are under **More tests & technical details → Record a home-screen page visit**; the guided page walkthrough handles these labels in order for you.

## Record the environment first

Write down OxygenOS and Android version, launcher name/version, grid size, screen resolution, font/display scale, navigation mode, home-screen page count and orientation. The diagnostic report captures the available Android values, but OxygenOS/grid settings also need a manual note. This build was not run on the physical OnePlus.

Use three genuinely different home-screen pages. Name them A, B and C yourself; these are test labels, not page numbers inferred from Android. Disable folder mode during capture/page tests.

## 1. Transparent widget and resizing

Add one lab widget per page. Confirm each shows a different ID. Check that the static grid or photo shows through the interior. Resize horizontally and vertically, then tap it. Export logs and confirm `WIDGET_OPTIONS` changes, with `appWidgetMinWidth/Height`, `appWidgetMaxWidth/Height`, and any `appWidgetSizes` values the host supplies. All option keys are logged; absent keys remain absent.

Pass only if the launcher actually places/resizes independent widgets and the callbacks are observed. Options are in dp and do not prove position. Rounded corners and host padding may mean the reserved grid area exceeds the clickable root.

## 2. Source bounds and root coverage

Wait two seconds after the launcher settles. Tap near each **inner** corner and the centre, returning home between taps. For one unchanged widget, all five source rectangles must match. Verify the rectangle against a screenshot, including its top offset from the actual display edge. Check widget ID, screen dimensions, density and orientation in every capture.

Move the widget without resizing. Tap again; the new rectangle should follow its new position. Resize and repeat. Null, empty or off-screen bounds must show an invalid result and disable rendering. The JVM tests cover invalid rectangle handling, but a real OEM null-bound delivery has not been reproduced. Do not call that phone scenario passed until executed.

## 3. Wallpaper alignment — critical gate

Activate the wallpaper before capturing. Tap the widget, choose **Use this widget for alignment (45 seconds)**. The pink rectangle with cyan edges and crossed diagonals must lie directly under the yellow outline. During this brief ALIGN test it intentionally renders on any page; remain on the target page.

Measure left/top/right/bottom edge errors in a screenshot. Start with CENTER and zero corrections. If needed, open **Align** and try ORIGIN, OFFSET, or PIXEL, then adjust dx/dy in surface pixels and the inverse compositor scale. Models are experimental: do not assume `xPixelOffset` is a physical surface translation. Test all four edges, not only the centre. A square-cornered test rectangle may extend into host-rounded corners; record this as a mask requirement for production.

Suggested acceptance criterion: at most 3 physical pixels of error on each straight edge for the tested configuration, repeated after unlock and returning from another app. Record the actual measurements and choose your own stricter tolerance if needed. Check different widget locations/sizes, gesture and button navigation, display scale, portrait/landscape where supported, and launcher overview zoom. If corrections cannot stay stable, fail this gate.

## 4. Three-page offset survey — critical gate

On A, wait two seconds, tap its widget and choose **Record page A · round 1**. Return home. Repeat for B1, then C1. Visit in reverse order for C2, B2 and A2, always waiting before tapping. One capture cannot be reused under multiple labels. The latest observation for each label/round is compared.

Open **Pages** and inspect all six raw samples. Distinct, repeatable signatures yield **CANDIDATE**, never an automatic pass. No callbacks/missing samples or identical signatures across different pages yield **UNSUPPORTED** after a completed survey. Unstable samples or changed environments yield **INCONCLUSIVE**. Offset step is logged but does not establish page count.

Repeat after adding/removing/reordering pages and after restarting the wallpaper. Use **Restart survey / invalidate calibration** first. Changing pages often changes normalized offsets, so old data is not a permanent page identity.

## 5. Isolation test

With a CANDIDATE survey, select widget A and enable the page gate. The rectangle should appear only at A's calibrated position after the page settles. Visit B and C, swipe slowly/rapidly and repeatedly, stop halfway then reverse, enter launcher overview, open app drawer, open another app, lock/unlock and rotate. Record screen video if possible. Repeat with widgets B and C.

Any visible rectangle behind unrelated icons is a failure, including a single transition frame. The 500 ms settle delay hides the rectangle after observed movement; it cannot predict a launcher gesture before a delayed offset callback. A supported-looking survey does not guarantee flicker-free isolation. On unavailable/inconsistent offsets, keep PAGE disabled and use the documented fallback decision.

## 6. Wallpaper import

Before switching wallpaper, choose an image using **Choose photo**. Verify cancel, a selected image, local saved-image persistence after process restart, and center-crop appearance. No gallery-wide permission should be needed. Android 13+ uses the native photo picker; older versions use the document picker.

For optional **Import current static wallpaper**, ensure the existing wallpaper is static. On Android 13+ follow the explanation to All files access; enable it, return and press import again. This broad access is optional. Deny it to test the fallback. Revoke it after a successful import. Android 8–12 uses the legacy read permission. OEM restrictions may still cause a caught failure. Confirm the saved image before activation; the original launcher's crop is not preserved automatically.

Try import while another live wallpaper is active: the app must reject it as unavailable, rather than silently substituting the default wallpaper. Re-activate the lab afterwards and recalibrate.

## 7. Video — deferred

No video player is present in this APK. Leave this test NOT YET TESTED. Only proceed to the separate implementation stage after actual target-device alignment and isolation results are acceptable. Then test a local 10-second H.264 MP4 at 30 FPS in the calibrated region, background preservation, looping seams, decoder selection, dropped-frame counts, lock/unlock and zero unnecessary invisible rendering.

## 8. Folder

In **Align**, select a widget, choose up to eight installed launchable apps, and turn on folder mode. Tap the widget: a small dialog-themed Activity should appear with icons. Launch each shortcut and verify the correct app. Return home and try another. Disable/uninstall a selected app to test graceful absence/failure. Turn folder mode off to resume page-survey capture.

## Save the result

Use **Logs → Record a manually executed test result** to record status and observations with the exact device/setup. Enter measurements, reproduction steps and the recommended next action. Merely installing, compiling, or seeing a rectangle once does not pass all eight tests. Use **Save diagnostic report and full logs** to write a text report to a location you choose. The logs use the device's own timestamp/timezone; test reports should state that timezone.
