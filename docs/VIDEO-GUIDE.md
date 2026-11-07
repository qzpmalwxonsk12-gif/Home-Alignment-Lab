> Historical diagnostic-clip guide. For your own video or GIF in 0.5.0, start with [MY-MEDIA-GUIDE.md](MY-MEDIA-GUIDE.md).

# Home Alignment Lab 0.4.1 — quicker video return

Your 0.4.0 phone test succeeded. This update shortens the deliberate wait when you return to page A from 500 ms to 150 ms and schedules that check directly. Actual visible return time still includes launcher callbacks, drawing and decoder startup.

1. Install `Home-Alignment-Lab-0.4.1-fast-return.apk` as an **Update** over the existing app. Keep your current widgets and home-screen layout.
2. Open Home Alignment Lab and press **Continue to video test**.
3. If asked to refresh page A, follow the button to go home, tap **1** on the original page-A widget, then follow the instruction to tap **2**. Keep the widget still. This refreshes its position after the update; your earlier A/B/C results stay saved.
4. Press **Play video test**. Watch the clip, swipe to B/C and back slowly and quickly a few times. It should return sooner on A and remain absent on the other pages. The clip still restarts its loop when it returns.
5. Tap **1**, select what you observed, then **Save the video report** in Downloads and attach it here. Mention whether the return feels faster and whether any video appeared on the wrong page.

The test runs for up to 90 seconds from its first displayed frame. The fixed 30 FPS source is unchanged. The report now records the settling interval and the time from starting/resuming decoding to the next submitted video frame.

Your previous report measured 29.66 rendered FPS, no late-frame drops and six pauses on the phone. It recorded one complete loop, so it does not prove two uninterrupted loops. The 0.4.1 return timing still needs this phone check.
