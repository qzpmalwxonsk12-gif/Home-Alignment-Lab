package dev.animatedhome.diagnostics

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

object BatteryTelemetry {
    private const val MAX_SAMPLES = 360 // Up to 6 hours of 1-minute samples
    private var countersLoaded = false
    private var lastPeriodicElapsed = 0L
    private var lastSampleElapsed = 0L
    private var lastSampleCpuMs = 0L
    private var lastVisibleState = false
    private var lastVisibleChangeElapsed = 0L
    private var accumulatedVisibleMs = 0L
    private var accumulatedHiddenMs = 0L
    private var visibleCpuMs = 0L
    private var hiddenCpuMs = 0L

    // Some OEM kernels (e.g. Xiaomi HyperOS/MIUI) report BATTERY_PROPERTY_CHARGE_COUNTER
    // in mAh (1..25,000) rather than standard Android microamp-hours (µAh).
    private fun normalizeChargeUah(rawCounter: Long): Long = when {
        rawCounter <= 0L -> -1L
        rawCounter in 1L..25_000L -> rawCounter * 1_000L
        else -> rawCounter
    }

    fun ensureStarted(context: Context) {
        val s = State(context)
        val session = s.obj("batterySession")
        if (session.length() == 0) {
            resetSession(context, "auto_init")
        } else if (!countersLoaded) {
            countersLoaded = true
            accumulatedVisibleMs = session.optLong("visibleMs", 0L)
            accumulatedHiddenMs = session.optLong("hiddenMs", 0L)
            visibleCpuMs = session.optLong("visibleCpuMs", 0L)
            hiddenCpuMs = session.optLong("hiddenCpuMs", 0L)
            lastSampleElapsed = SystemClock.elapsedRealtime()
            lastSampleCpuMs = Process.getElapsedCpuTime()
            lastVisibleState = Live.visible
        }
    }

    fun resetSession(context: Context, reason: String = "manual_reset") {
        val nowElapsed = SystemClock.elapsedRealtime()
        val cpuNow = Process.getElapsedCpuTime()
        countersLoaded = true
        lastPeriodicElapsed = nowElapsed
        lastSampleElapsed = nowElapsed
        lastSampleCpuMs = cpuNow
        lastVisibleState = Live.visible
        lastVisibleChangeElapsed = nowElapsed
        accumulatedVisibleMs = 0L
        accumulatedHiddenMs = 0L
        visibleCpuMs = 0L
        hiddenCpuMs = 0L

        val initial = readInstant(context, "SESSION_START ($reason)")
        val session = JSONObject()
            .put("startedAtWallMs", System.currentTimeMillis())
            .put("startedAtIso", isoNow())
            .put("startedElapsedMs", nowElapsed)
            .put("startCpuMs", cpuNow)
            .put("startLevelPct", initial.optDouble("levelPct", -1.0))
            .put("startChargeUah", initial.optLong("chargeUah", -1L))
            .put("startTempC", initial.optDouble("tempC", 0.0))
            .put("peakTempC", initial.optDouble("tempC", 0.0))
            .put("minTempC", initial.optDouble("tempC", 0.0))
            .put("peakThermalStatus", initial.optString("thermalStatus", "NONE"))
        val samples = JSONArray().put(initial)
        State(context).prefs.edit()
            .putString("batterySession", session.toString())
            .putString("batterySamples", samples.toString())
            .apply()
        DiagnosticLog.event(context, "BATTERY_SESSION_RESET", initial)
    }

    fun onVisibilityChanged(context: Context, visible: Boolean) {
        ensureStarted(context)
        updateCounters(visible)
        recordSample(context, if (visible) "HOME_VISIBLE" else "HOME_HIDDEN", force = true)
    }

    fun periodicTick(context: Context) {
        ensureStarted(context)
        val now = SystemClock.elapsedRealtime()
        updateCounters(Live.visible)
        if (now - lastPeriodicElapsed >= 60_000L) {
            lastPeriodicElapsed = now
            recordSample(context, "PERIODIC_60S", force = true)
        }
    }

    private fun updateCounters(currentVisible: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val cpuNow = Process.getElapsedCpuTime()
        if (lastSampleElapsed > 0L && now > lastSampleElapsed) {
            val dt = now - lastSampleElapsed
            val dCpu = (cpuNow - lastSampleCpuMs).coerceAtLeast(0L)
            if (lastVisibleState) {
                accumulatedVisibleMs += dt
                visibleCpuMs += dCpu
            } else {
                accumulatedHiddenMs += dt
                hiddenCpuMs += dCpu
            }
        }
        lastSampleElapsed = now
        lastSampleCpuMs = cpuNow
        lastVisibleState = currentVisible
    }

    fun recordSample(context: Context, trigger: String, force: Boolean = false): JSONObject {
        ensureStarted(context)
        updateCounters(Live.visible)
        val s = State(context)
        val snap = readInstant(context, trigger)
        val session = s.obj("batterySession")
        val temp = snap.optDouble("tempC", 0.0)
        if (temp > 0.0) {
            val prevPeak = session.optDouble("peakTempC", temp)
            val prevMin = session.optDouble("minTempC", temp)
            session.put("peakTempC", maxOf(prevPeak, temp))
            session.put("minTempC", if (prevMin <= 0.0) temp else minOf(prevMin, temp))
        }
        session.put("visibleMs", accumulatedVisibleMs)
            .put("hiddenMs", accumulatedHiddenMs)
            .put("visibleCpuMs", visibleCpuMs)
            .put("hiddenCpuMs", hiddenCpuMs)
            .put("lastUpdatedIso", isoNow())

        val rawSamples = runCatching { JSONArray(s.prefs.getString("batterySamples", "[]")!!) }.getOrDefault(JSONArray())
        val kept = JSONArray()
        val startIdx = max(0, rawSamples.length() - (MAX_SAMPLES - 1))
        for (i in startIdx until rawSamples.length()) kept.put(rawSamples.get(i))
        kept.put(snap)

        s.prefs.edit()
            .putString("batterySession", session.toString())
            .putString("batterySamples", kept.toString())
            .apply()
        if (force) DiagnosticLog.event(context, "BATTERY_SAMPLE", snap)
        return snap
    }

    fun readInstant(context: Context, trigger: String = "SNAPSHOT"): JSONObject {
        val app = context.applicationContext
        val bm = app.getSystemService(BatteryManager::class.java)
        val pm = app.getSystemService(PowerManager::class.java)
        val sticky = runCatching { app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()

        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
        val levelPct = if (level >= 0) (level * 1000 / scale) / 10.0 else -1.0
        val tempTenths = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenths / 10.0
        val voltageMv = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val statusInt = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pluggedInt = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

        val statusStr = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
            else -> "UNKNOWN"
        }
        val pluggedStr = when (pluggedInt) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
            0 -> "UNPLUGGED"
            else -> "PLUGGED_$pluggedInt"
        }

        val rawChargeCounter = bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it != Long.MIN_VALUE } ?: -1L
        val chargeUah = normalizeChargeUah(rawChargeCounter)
        val currentNowUa = bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.takeIf { it != Long.MIN_VALUE } ?: 0L
        val currentAvgUa = bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)?.takeIf { it != Long.MIN_VALUE } ?: 0L
        val currentMa = currentNowUa / 1000.0
        val powerMw = if (voltageMv > 0 && currentNowUa != 0L) (abs(currentMa) * voltageMv) / 1000.0 else 0.0

        val thermalStr = if (Build.VERSION.SDK_INT >= 29 && pm != null) {
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "NONE"
                PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
                PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
                PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
                PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                else -> "STATUS_${pm.currentThermalStatus}"
            }
        } else "UNSUPPORTED"

        val rt = Runtime.getRuntime()
        val javaHeapMb = ((rt.totalMemory() - rt.freeMemory()) / (1024.0 * 1024.0) * 10).toLong() / 10.0
        val nativeHeapMb = (Debug.getNativeHeapAllocatedSize() / (1024.0 * 1024.0) * 10).toLong() / 10.0

        val s = State(app)
        var activeWidgets = 0
        var activeVideos = 0
        var activeGifs = 0
        var totalFrames = 0L
        for (id in s.ids()) {
            if (WidgetAnimations.enabled(s, id)) {
                activeWidgets++
                val kind = s.obj(WidgetAnimations.key("playbackMedia", id)).optString("kind")
                if (kind == "gif") activeGifs++ else activeVideos++
            }
            totalFrames += s.obj(WidgetAnimations.key("videoTelemetry", id)).optLong("renderedFrames", 0L)
        }

        return JSONObject()
            .put("timeIso", isoNow())
            .put("elapsedMs", SystemClock.elapsedRealtime())
            .put("trigger", trigger)
            .put("homeVisible", Live.visible)
            .put("interactive", pm?.isInteractive ?: true)
            .put("powerSave", pm?.isPowerSaveMode ?: false)
            .put("status", statusStr)
            .put("plugged", pluggedStr)
            .put("levelPct", levelPct)
            .put("chargeUah", chargeUah)
            .put("chargeMah", if (chargeUah > 0) (chargeUah / 100) / 10.0 else -1.0)
            .put("currentMa", (currentMa * 10).toLong() / 10.0)
            .put("currentAvgMa", ((currentAvgUa / 1000.0) * 10).toLong() / 10.0)
            .put("voltageMv", voltageMv)
            .put("estPowerMw", (powerMw * 10).toLong() / 10.0)
            .put("tempC", tempC)
            .put("thermalStatus", thermalStr)
            .put("processCpuMs", Process.getElapsedCpuTime())
            .put("javaHeapMb", javaHeapMb)
            .put("nativeHeapMb", nativeHeapMb)
            .put("activeWidgets", activeWidgets)
            .put("activeVideos", activeVideos)
            .put("activeGifs", activeGifs)
            .put("totalRenderedFrames", totalFrames)
    }

    data class Rates(
        val durationMinutes: Double,
        val visibleMinutes: Double,
        val hiddenMinutes: Double,
        val batteryPctDelta: Double,
        val batteryMahDelta: Double,
        val estPctPerHour: Double,
        val estMahPerHour: Double,
        val visibleCpuPctOfOneCore: Double,
        val hiddenCpuPctOfOneCore: Double
    ) {
        fun json() = JSONObject()
            .put("durationMinutes", durationMinutes)
            .put("visibleMinutes", visibleMinutes)
            .put("hiddenMinutes", hiddenMinutes)
            .put("batteryPctDelta", batteryPctDelta)
            .put("batteryMahDelta", batteryMahDelta)
            .put("estPctPerHour", estPctPerHour)
            .put("estMahPerHour", estMahPerHour)
            .put("visibleCpuPctOfOneCore", visibleCpuPctOfOneCore)
            .put("hiddenCpuPctOfOneCore", hiddenCpuPctOfOneCore)
    }

    fun computeRates(
        durationMs: Long,
        startLevelPct: Double,
        endLevelPct: Double,
        startChargeUah: Long,
        endChargeUah: Long,
        visibleMs: Long,
        hiddenMs: Long,
        visibleCpuMs: Long,
        hiddenCpuMs: Long
    ): Rates {
        val hours = durationMs / 3_600_000.0
        val normStartUah = normalizeChargeUah(startChargeUah)
        val normEndUah = normalizeChargeUah(endChargeUah)
        val pctDrop = if (startLevelPct >= 0 && endLevelPct >= 0) (startLevelPct - endLevelPct) else 0.0
        val mahDrop = if (normStartUah > 0 && normEndUah > 0) (normStartUah - normEndUah) / 1000.0 else 0.0
        val pctPerHour = if (hours >= 0.01 && pctDrop > 0) pctDrop / hours else 0.0
        val mahPerHour = if (hours >= 0.01 && mahDrop > 0) mahDrop / hours else 0.0
        val visibleCpuPct = if (visibleMs > 0) (visibleCpuMs * 100.0) / visibleMs else 0.0
        val hiddenCpuPct = if (hiddenMs > 0) (hiddenCpuMs * 100.0) / hiddenMs else 0.0
        return Rates(
            durationMinutes = ((durationMs / 60_000.0) * 10).toLong() / 10.0,
            visibleMinutes = ((visibleMs / 60_000.0) * 10).toLong() / 10.0,
            hiddenMinutes = ((hiddenMs / 60_000.0) * 10).toLong() / 10.0,
            batteryPctDelta = (pctDrop * 10).toLong() / 10.0,
            batteryMahDelta = (mahDrop * 10).toLong() / 10.0,
            estPctPerHour = (pctPerHour * 10).toLong() / 10.0,
            estMahPerHour = (mahPerHour * 10).toLong() / 10.0,
            visibleCpuPctOfOneCore = (visibleCpuPct * 100).toLong() / 100.0,
            hiddenCpuPctOfOneCore = (hiddenCpuPct * 100).toLong() / 100.0
        )
    }

    fun summary(context: Context, recordNewSample: Boolean = true): JSONObject {
        ensureStarted(context)
        val current = if (recordNewSample) recordSample(context, "SUMMARY_VIEW", force = false) else readInstant(context, "REPORT_SNAPSHOT")
        val s = State(context)
        val session = s.obj("batterySession")
        val visMs = session.optLong("visibleMs", accumulatedVisibleMs)
        val hidMs = session.optLong("hiddenMs", accumulatedHiddenMs)
        val elapsedDelta = (SystemClock.elapsedRealtime() - session.optLong("startedElapsedMs", SystemClock.elapsedRealtime())).coerceAtLeast(0L)
        val wallDelta = (System.currentTimeMillis() - session.optLong("startedAtWallMs", System.currentTimeMillis())).coerceAtLeast(0L)
        val durationMs = maxOf(elapsedDelta, wallDelta, visMs + hidMs)
        val rates = computeRates(
            durationMs = durationMs,
            startLevelPct = session.optDouble("startLevelPct", current.optDouble("levelPct", -1.0)),
            endLevelPct = current.optDouble("levelPct", -1.0),
            startChargeUah = session.optLong("startChargeUah", current.optLong("chargeUah", -1L)),
            endChargeUah = current.optLong("chargeUah", -1L),
            visibleMs = visMs,
            hiddenMs = hidMs,
            visibleCpuMs = session.optLong("visibleCpuMs", visibleCpuMs),
            hiddenCpuMs = session.optLong("hiddenCpuMs", hiddenCpuMs)
        ).json()
        val samples = runCatching { JSONArray(s.prefs.getString("batterySamples", "[]")!!) }.getOrDefault(JSONArray())
        return JSONObject()
            .put("session", session)
            .put("computedRates", rates)
            .put("currentSnapshot", current)
            .put("sampleCount", samples.length())
    }

    fun reportSection(context: Context): String {
        val sum = summary(context, recordNewSample = false)
        val s = State(context)
        val samples = runCatching { JSONArray(s.prefs.getString("batterySamples", "[]")!!) }.getOrDefault(JSONArray())
        val sb = StringBuilder()
        sb.append("BATTERY & PERFORMANCE TELEMETRY\n")
        sb.append(sum.toString(2)).append("\n\n")
        sb.append("BATTERY SAMPLES CSV (timeIso,trigger,homeVisible,status,plugged,levelPct,chargeMah,currentMa,voltageMv,estPowerMw,tempC,thermalStatus,processCpuMs,javaHeapMb,nativeHeapMb,activeWidgets,activeVideos,activeGifs,totalRenderedFrames)\n")
        for (i in 0 until samples.length()) {
            val o = samples.getJSONObject(i)
            sb.append("${o.optString("timeIso")},${o.optString("trigger")},${o.optBoolean("homeVisible")},${o.optString("status")},${o.optString("plugged")},${o.optDouble("levelPct")},${o.optDouble("chargeMah")},${o.optDouble("currentMa")},${o.optInt("voltageMv")},${o.optDouble("estPowerMw")},${o.optDouble("tempC")},${o.optString("thermalStatus")},${o.optLong("processCpuMs")},${o.optDouble("javaHeapMb")},${o.optDouble("nativeHeapMb")},${o.optInt("activeWidgets")},${o.optInt("activeVideos")},${o.optInt("activeGifs")},${o.optLong("totalRenderedFrames")}\n")
        }
        return sb.toString()
    }

    private fun isoNow() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
}
