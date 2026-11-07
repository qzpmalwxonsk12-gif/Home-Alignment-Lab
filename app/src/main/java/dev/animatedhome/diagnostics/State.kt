package dev.animatedhome.diagnostics

import android.app.Activity
import android.app.KeyguardManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

fun Offset.json() = JSONObject().put("xOffset", x).put("yOffset", y).put("xOffsetStep", xStep)
    .put("yOffsetStep", yStep).put("xPixelOffset", px).put("yPixelOffset", py)
fun JSONObject.offset(): Offset? = try {
    Offset(getDouble("xOffset").toFloat(), getDouble("yOffset").toFloat(), getDouble("xOffsetStep").toFloat(),
        getDouble("yOffsetStep").toFloat(), getInt("xPixelOffset"), getInt("yPixelOffset")).takeIf { it.valid() }
} catch (_: Exception) { null }
fun Box.json() = JSONArray(listOf(left, top, right, bottom))
fun JSONObject.box(key: String = "bounds"): Box? = optJSONArray(key)?.let { a ->
    if (a.length() == 4) Box(a.optDouble(0).toFloat(), a.optDouble(1).toFloat(), a.optDouble(2).toFloat(), a.optDouble(3).toFloat()) else null
}

object DiagnosticLog {
    private val writer = Executors.newSingleThreadExecutor()
    private val recent = ArrayDeque<String>()
    fun event(context: Context, event: String, data: Any = "") {
        val line = "${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())} $event $data"
        Log.i("HomeAlignmentLab", line)
        synchronized(recent) { recent.addLast(line); while (recent.size > 250) recent.removeFirst() }
        val dir = context.applicationContext.filesDir
        writer.execute {
            runCatching {
                val file = File(dir, "diagnostics.log")
                if (file.length() > 2_000_000) file.copyTo(File(dir, "diagnostics.previous.log"), overwrite = true).also { file.writeText("") }
                file.appendText(line + "\n")
            }.onFailure { Log.e("HomeAlignmentLab", "Log write failed", it) }
        }
    }
    fun tail() = synchronized(recent) { recent.toList().takeLast(55).joinToString("\n") }
}

object Live {
    var engine = ""
    var offset: Offset? = null // Raw launcher data only.
    var offsetsChangedAcrossPages=false
    val swipeTracker=SwipePageTracker()
    var stableSince = 0L
    var visible = false
    var hiddenAt = 0L
    var zoom = 0f
    var width = 0
    var height = 0
    var insets = "not reported"
    var insetsKey = "unknown"
    var lastEvent = "Wallpaper has not started in this process."
    var draw = "off"
    fun snapshot() = JSONObject().put("engine", engine).put("offset", offset?.json())
        .put("visible", visible).put("stableMillis", SystemClock.elapsedRealtime() - stableSince)
        .put("zoom", zoom).put("surfaceWidth", width).put("surfaceHeight", height)
        .put("insets", insets).put("lastEvent", lastEvent).put("draw", draw)
}

class State(val context: Context) {
    val prefs = context.getSharedPreferences("lab", Context.MODE_PRIVATE)
    fun obj(key: String) = runCatching { JSONObject(prefs.getString(key, "{}")!!) }.getOrDefault(JSONObject())
    fun put(key: String, value: JSONObject) { prefs.edit().putString(key, value.toString()).apply() }
    fun ids(): IntArray = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, CalibrationWidget::class.java))
    fun record(id: Int) = obj("widget_$id")
    fun options(id: Int, options: Bundle) {
        val all = JSONObject()
        for (key in options.keySet().sorted()) all.put(key, runCatching { options.get(key).toString() }.getOrDefault("unreadable"))
        val record = record(id)
        // Sizing callbacks cannot locate a widget. Invalidate spatial calibration after a resize.
        if (record.has("options") && record.optJSONObject("options")?.toString() != all.toString()) {
            record.put("valid", false).put("invalidReason", "Sizing options changed. Tap the widget again.")
            record.put("measurementPending", false)
            prefs.edit().putString("mode", "OFF").apply()
        }
        record.put("options", all)
        put("widget_$id", record)
        DiagnosticLog.event(context, "WIDGET_OPTIONS id=$id", all)
    }
    fun visits(): List<Visit> {
        val a = runCatching { JSONArray(prefs.getString("visits", "[]")!!) }.getOrDefault(JSONArray())
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            Visit(it.getString("page"), it.getInt("round"), it.getString("tap"), it.getString("environment"),
                it.optJSONObject("offset")?.offset(), it.optBoolean("stable"),it.optString("evidenceSource","legacy"))
        }
    }
    fun addVisit(capture: JSONObject, page: String, round: Int): String {
        val a = JSONArray(prefs.getString("visits", "[]")!!)
        if (visits().any { it.tap == capture.optString("tap") }) return "This tap was already labelled. Return home and tap again."
        a.put(JSONObject().put("page", page).put("round", round).put("tap", capture.getString("tap"))
            .put("widgetId",capture.optInt("widgetId",-1)).put("observedLive",capture.optBoolean("observedLive"))
            .put("environment", capture.getString("environment")).put("offset", capture.optJSONObject("offset"))
            .put("stable", capture.optBoolean("stable")).put("evidenceSource",capture.optString("evidenceSource","legacy")))
        // Keep at most 60 attempts; the last attempt for each label/round is assessed.
        val kept = JSONArray((maxOf(0,a.length()-60) until a.length()).map { a.get(it) })
        prefs.edit().putString("visits", kept.toString()).putString("mode", "OFF").apply()
        DiagnosticLog.event(context, "PAGE_VISIT", a.getJSONObject(a.length()-1))
        return PageEvidence.assess(visits()).detail
    }
    fun invalidate(reason: String) {
        prefs.edit().putString("mode", "OFF").putString("visits", "[]").apply()
        ids().forEach { id -> put("widget_$id", record(id).put("valid", false).put("measurementPending", false).put("invalidReason", reason)) }
        DiagnosticLog.event(context, "INVALIDATE", reason)
    }
    fun report(): String {
        val widgets = JSONObject(); ids().forEach { widgets.put(it.toString(), record(it).put("animation",obj(WidgetAnimations.key("selectedMedia",it))).put("pendingAnimation",obj(WidgetAnimations.key("pendingMedia",it))).put("playback",obj(WidgetAnimations.key("playbackMedia",it))).put("enabled",WidgetAnimations.enabled(this,it)).put("telemetry",obj(WidgetAnimations.key("videoTelemetry",it)))) }
        return "HOME ALIGNMENT ${BuildConfig.VERSION_NAME}\n${device(context).toString(2)}\n\nWALLPAPER ACTIVE: ${AlignmentFlow.wallpaperActive(context)}\nALIGNMENT ATTEMPT\n${obj("alignmentAttempt").toString(2)}\n\nLIVE TELEMETRY\n${Live.snapshot().toString(2)}\n\n" +
            "SWIPE FALLBACK (ESTIMATED, NOT TEST EVIDENCE)\n${SwipeFallback.snapshot(context).toString(2)}\n\nPAGE TEST STATUS: ${if(visits().isEmpty()) "NOT TESTED" else PageEvidence.assess(visits()).code}\n${prefs.getString("surveyMigrationNote", "")}\nARCHIVED PRE-PATCH READINGS: ${prefs.getString("prePatchPageVisits", "[]")}\nARCHIVED UNVERIFIED READINGS: ${prefs.getString("unverifiedLegacyVisits", "[]")}\n\nPAGE EVIDENCE\n${PageEvidence.assess(visits())}\n${prefs.getString("visits", "[]")}\n\nWIDGETS\n${widgets.toString(2)}\n\n" +
            "USER-RECORDED RESULTS (not automatically passed)\n${obj("results").toString(2)}\n\n" +
            "SETTINGS\nmode=${prefs.getString("mode", "OFF")} model=${prefs.getString("mapping", "CENTER")} dx=${prefs.getFloat("dx",0f)} dy=${prefs.getFloat("dy",0f)} scale=${prefs.getFloat("scale",1f)}\nPAGE OBSERVATION: ${prefs.getString("isolationObservation","NOT YET TESTED")}\n\n" +
            "PENDING MEDIA\n${obj("pendingMedia").toString(2)}\n\nPLAYBACK SOURCE\n${obj("playbackMedia").toString(2)}\n\nSELECTED MEDIA\n${obj("selectedMedia").toString(2)}\n\nVIDEO ATTEMPT\n${obj("videoAttempt").toString(2)}\n\nVIDEO TELEMETRY\n${obj("videoTelemetry").toString(2)}\n\nVIDEO OBSERVATION: ${prefs.getString("videoObservation","NOT YET TESTED")}\n\n${BatteryTelemetry.reportSection(context)}\nRECENT LOG\n${DiagnosticLog.tail()}"
    }
}

@Suppress("DEPRECATION")
fun device(context: Context): JSONObject {
    val wm = context.getSystemService(WindowManager::class.java)
    val display = wm.defaultDisplay
    val metrics = DisplayMetrics(); display.getRealMetrics(metrics)
    val home = context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName ?: "unknown"
    val version = runCatching { context.packageManager.getPackageInfo(home, 0).versionName }.getOrNull() ?: "unknown"
    return JSONObject().put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
        .put("android", Build.VERSION.RELEASE).put("api", Build.VERSION.SDK_INT).put("build", Build.DISPLAY)
        .put("width", metrics.widthPixels).put("height", metrics.heightPixels).put("density", metrics.density)
        .put("densityDpi", metrics.densityDpi).put("displayId", display.displayId).put("rotation", display.rotation)
        .put("orientation", if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait")
        .put("launcher", home).put("launcherVersion", version)
}

fun environment(context: Context): String {
    val d = device(context)
    return listOf("launcher", "launcherVersion", "width", "height", "densityDpi", "displayId", "rotation").joinToString("|") { d.optString(it) } + "|${Live.engine}|surface=${Live.width}x${Live.height}|insets=${Live.insetsKey}"
}

fun capture(activity: Activity): JSONObject? {
    val id = activity.intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
    val state = State(activity)
    if (!state.ids().contains(id)) { DiagnosticLog.event(activity, "CAPTURE_REJECTED", "Unknown widget id $id"); return null }
    val r = activity.intent.sourceBounds
    val d = device(activity)
    val b = r?.let { Box(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
    val now = SystemClock.elapsedRealtime()
    val recentHome = Live.engine.isNotEmpty() && (Live.visible || now-Live.hiddenAt in 0..1500) &&
        !activity.getSystemService(KeyguardManager::class.java).isKeyguardLocked
    val previous = state.record(id)
    val result = JSONObject(previous.toString())
    val sw = d.getInt("width"); val sh = d.getInt("height")
    val env = environment(activity)
    val cornerTap = activity.intent.getBooleanExtra("corner",false)
    if (cornerTap) {
        val root = previous.box("sourceBounds")
        val elapsed = System.currentTimeMillis()-previous.optLong("measurementStartedAt")
        val priorOffset = previous.optJSONObject("offset")?.offset()
        val samePage = (priorOffset == null && Live.offset == null) ||
            (recentHome && priorOffset != null && Live.offset?.near(priorOffset) == true)
        val fresh = previous.optBoolean("measurementPending") && elapsed in 0..300_000 &&
            previous.optString("environment") == env && samePage
        val measured = if(fresh && root != null && b != null) WidgetMeasurement.measure(root,b,sw,sh) else null
        result.put("cornerSourceBounds",b?.json()).put("bounds",measured?.json())
            .put("valid",measured != null).put("measurementPending",false)
            .put("measurementVersion",if(measured != null) WidgetMeasurement.VERSION else 0)
            .put("invalidReason",if(measured != null) "" else "Tap 1 first, then tap 2 on the same widget within five minutes. Keep it in the same position. If it is very small, make it larger first.")
        if(measured != null && root != null) result.put("measuredScaleX",measured.width/root.width)
            .put("measuredScaleY",measured.height/root.height)
    } else {
        val priorRaw = previous.box("sourceBounds")
        val priorBox = previous.box()
        val reuse = previous.optBoolean("valid") && previous.optInt("measurementVersion") == WidgetMeasurement.VERSION &&
            previous.optString("environment") == env && WidgetMeasurement.same(priorRaw,b)
        val measured = if(reuse && b != null && priorRaw != null && priorBox != null)
            WidgetMeasurement.relocate(b,priorRaw,priorBox).takeIf { it.valid(sw,sh) } else null
        val pending = measured == null && WidgetMeasurement.usableSource(b,sw,sh)
        result.put("sourceBounds",b?.json()).put("bounds",measured?.json()).put("valid",measured != null)
            .put("measurementPending",pending).put("measurementStartedAt",System.currentTimeMillis())
            .put("measurementVersion",if(measured != null) WidgetMeasurement.VERSION else 0)
            .put("invalidReason",if(measured != null || pending) "" else "No usable position was received. Leave home-screen editing mode, wait two seconds, then tap 1 again.")
        if(!reuse) { result.remove("cornerSourceBounds"); result.remove("measuredScaleX"); result.remove("measuredScaleY") }
    }
    result.put("widgetId", id).put("captureTarget",if(cornerTap) "corner" else "root")
        .put("capturedAt", System.currentTimeMillis()).put("tap", UUID.randomUUID().toString()).put("device", d)
        .put("environment", env).put("offset", if (recentHome) Live.offset?.json() else null)
        .put("stable", recentHome && Live.offset != null && now-Live.stableSince >= 500)
        .put("zoom", Live.zoom)
    state.put("widget_$id", result)
    // A new measurement must be explicitly armed; never silently move an active page gate.
    state.prefs.edit().putInt("selected", id).putString("mode", "OFF").apply()
    DiagnosticLog.event(activity, "CAPTURE", result)
    return result
}

