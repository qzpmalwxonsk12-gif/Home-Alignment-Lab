# Home Alignment 0.7.1 — Xiaomi page fallback

## Install

Copy **Home-Alignment-Lab-0.7.1-page-fallback.apk** to the phone and install it over the existing app. Retain the existing installation to keep saved app data. This is a debug-signed sideload build, not a store release.

## Try the Xiaomi workaround

1. Open **Home Alignment → Diagnostics → Swipe fallback (experimental)**.
2. Set the total number of home pages, counted from left to right. Include empty pages. Turn off infinite/looping pages in the launcher.
3. Assign widgets to their actual pages: **A = leftmost**, B = next, and so on. Multiple widgets can share a page.
4. Tap **Enable experimental swipe fallback**.
5. Go home and tap an assigned widget once to sync the estimate. Return to that same page, then swipe on empty home-screen space.
6. If an animation appears on the wrong page, tap an assigned widget on its real page again. Repeat after a wallpaper restart. Update assignments after moving widgets.

Home-button jumps, missed/cancelled gestures and launcher behavior can put the estimate out of sync. If swipes do nothing, open the fallback screen and press **Refresh status**. A gesture count that stays at zero after syncing and swiping suggests the launcher is not forwarding usable touch events. Turn the fallback off if it does not help. Per-widget **Pin to all home pages** remains an alternative.

Fallback is off by default. Native launcher readings take priority. Assigned widgets wait for an anchor after a restart; unassigned widgets keep their usual behavior. Turning it off retains files, widget positions and native readings.

## Optional A/B/C test

Open **Diagnostics → Run A/B/C page test**. Place one stationary widget on each of three different pages and follow **A → B → C → C → B → A**. Wait two seconds, tap the page's widget, then save that single visit. The second C visit needs another tap. After six repeatable real readings, the app offers the pink-box visual check.

In the Xiaomi report reviewed for this patch, all six visits had the same raw position. **UNSUPPORTED** remains the correct result for that native-offset test even if swipe fallback helps playback. Estimated positions never count as evidence. Older readings are archived in reports because previous builds could copy rounds or invent missing positions.

Changing a GIF or video requires none of these tests. Use Home or tap the widget to choose and preview another file.

## Troubleshooting

After trying the fallback, use **Diagnostics → Download diagnostic report**. It includes raw launcher readings, assignments, estimated page and gesture counters. Nothing is sent automatically. Reports can include private filenames and stream URLs.
