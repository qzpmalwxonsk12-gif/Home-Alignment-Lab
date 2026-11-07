package dev.animatedhome.diagnostics

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import org.json.JSONObject
import java.util.UUID

object AlignmentFlow {
    fun wallpaperActive(context: Context): Boolean {
        val info = runCatching { WallpaperManager.getInstance(context).wallpaperInfo }.getOrNull()
        if (info != null) return info.component == ComponentName(context, LabWallpaper::class.java)
        // Earlier releases may restrict wallpaperInfo. A recent real (not preview) home engine
        // in our own process is a conservative fallback; no package inventory permission needed.
        return Build.VERSION.SDK_INT < 34 && Live.engine.isNotEmpty() &&
            (Live.visible || SystemClock.elapsedRealtime() - Live.hiddenAt in 0..3000)
    }
    fun readiness(context: Context, id: Int): String {
        val state = State(context)
        val c = state.record(id)
        val d = device(context)
        return SetupPolicy.readiness(wallpaperActive(context), state.ids().contains(id),
            c.optBoolean("valid") && c.optInt("measurementVersion") == WidgetMeasurement.VERSION && c.box()?.valid(d.getInt("width"), d.getInt("height")) == true,
            Live.engine.isNotEmpty() && Live.width > 0 && Live.height > 0,
            c.optString("environment") == environment(context))
    }
    fun explanation(code: String) = when (code) {
        "WALLPAPER_OFF" -> "Turn on the lab wallpaper first. The widget marks the position; the wallpaper draws the pink box. Tap Set up wallpaper, then press Set wallpaper or Apply on Android's preview screen."
        "WIDGET_REMOVED" -> "That widget was removed. Go home and tap the new widget instead."
        "BOUNDS_MISSING" -> "Measure the widget first: go home, tap 1, then follow the instruction to tap 2. Leave home-screen editing mode and keep the widget in the same position during both taps."
        "ENGINE_NOT_READY" -> "The lab wallpaper is selected, but its drawing surface is not ready. Go home, wait two seconds and tap the widget again. If this repeats, save a report from Help."
        "CAPTURE_STALE" -> "The wallpaper or display changed after this widget was measured. Go home, wait two seconds, then tap the widget again to update its position."
        else -> "Ready to show the pink box."
    }
    fun start(a: LabActivity, id: Int) {
        val code = readiness(a, id)
        DiagnosticLog.event(a, "ALIGNMENT_PRECHECK", "widget=$id result=$code")
        if (code != "READY") {
            if (code == "WALLPAPER_OFF") a.openSetup()
            else a.message(explanation(code))
            return
        }
        val state = State(a)
        val now = SystemClock.elapsedRealtime()
        val attempt = JSONObject().put("id", UUID.randomUUID().toString()).put("widgetId", id)
            .put("status", "WAITING").put("submitted", false).put("requestedAt", System.currentTimeMillis())
            .put("requestedElapsed", now).put("captureTap", state.record(id).optString("tap"))
            .put("environment",state.record(id).optString("environment")).put("bounds",state.record(id).optJSONArray("bounds"))
            .put("measurementVersion",state.record(id).optInt("measurementVersion"))
            .put("sourceBounds",state.record(id).optJSONArray("sourceBounds"))
            .put("cornerSourceBounds",state.record(id).optJSONArray("cornerSourceBounds"))
        state.put("alignmentAttempt", attempt)
        // Allow a short return-home window. The 45 seconds begins after the first submitted frame.
        state.prefs.edit().putInt("selected", id).putString("mode", "ALIGN")
            .putLong("alignUntil", now + 60_000).apply()
        DiagnosticLog.event(a, "ALIGNMENT_REQUEST", attempt)
        a.home()
    }
    fun recordFrame(context: Context) {
        val state = State(context); val a = state.obj("alignmentAttempt")
        if (a.length() == 0 || a.optBoolean("submitted")) return
        a.put("status", "DRAWN").put("submitted", true).put("submittedAt", System.currentTimeMillis())
        state.put("alignmentAttempt", a)
        state.prefs.edit().putLong("alignUntil", SystemClock.elapsedRealtime() + 45_000).apply()
        DiagnosticLog.event(context, "ALIGNMENT_FRAME_SUBMITTED", a)
    }
    fun blocked(context: Context, reason: String) {
        val state = State(context); val a = state.obj("alignmentAttempt")
        if (a.length() == 0 || a.optString("reason") == reason) return
        a.put("status", "BLOCKED").put("reason", reason)
        state.put("alignmentAttempt", a)
        DiagnosticLog.event(context, "ALIGNMENT_BLOCKED", reason)
    }
    fun feedback(context: Context, answer: String) {
        val state = State(context); val a = state.obj("alignmentAttempt")
        a.put("answer", answer).put("answeredAt", System.currentTimeMillis())
        state.put("alignmentAttempt", a)
        state.prefs.edit().putString("alignmentAnswer", answer).putString("mode", "OFF").apply()
        DiagnosticLog.event(context, "USER_ALIGNMENT_OBSERVATION", a)
    }
    fun status(context: Context): String {
        val a = State(context).obj("alignmentAttempt")
        return when {
            a.length() == 0 -> "No pink-box check has been started yet."
            a.optString("status") == "BLOCKED" -> "The check was stopped: ${a.optString("reason")}"
            a.optBoolean("submitted") -> "The app finished drawing a test box. This alone doesn't prove it appeared in the right place on your screen."
            else -> "The app could not confirm that it drew the box. Try once more; if it still fails, save the report below."
        }
    }
}
