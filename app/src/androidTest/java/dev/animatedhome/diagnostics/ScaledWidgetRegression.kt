package dev.animatedhome.diagnostics

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import kotlin.math.abs

/** Emulator-only integration fixture: real RemoteViews PendingIntent clicks,
 * Android-computed sourceBounds, and production capture/measurement code.
 * A scaled View host simulates launcher shrinking; it is not an OxygenOS test.
 */
class ScaledWidgetRegression(private val test: Instrumentation) {
    fun run(): String {
        val context=test.targetContext
        val state=State(context)
        val id=state.ids().firstOrNull() ?: error("Add one test widget to the disposable emulator first")
        val report=StringBuilder()
        for(scale in listOf(1f,1/1.44f)) {
            state.prefs.edit().putBoolean("folder_$id",false).putBoolean("outline",true).commit()
            val activity=test.startActivitySync(Intent(context,FolderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            lateinit var root: View
            lateinit var corner: View
            val position=IntArray(2)
            test.runOnMainSync {
                val frame=FrameLayout(activity)
                activity.setContentView(frame)
                activity.window.setLayout(-1,-1)
                root=CalibrationWidget.views(context,id).apply(context,frame)
                root.pivotX=0f; root.pivotY=0f; root.scaleX=scale; root.scaleY=scale
                frame.addView(root,FrameLayout.LayoutParams(900,600).apply { leftMargin=40; topMargin=200 })
                corner=root.findViewById(R.id.widget_corner)
                state.put("widget_$id",state.record(id).put("valid",false).put("measurementPending",false))
            }
            test.waitForIdleSync(); SystemClock.sleep(700)
            test.runOnMainSync { root.getLocationOnScreen(position) }
            fun click(view:View): CaptureActivity {
                val monitor=test.addMonitor(CaptureActivity::class.java.name,null,false)
                test.runOnMainSync { check(view.performClick()) }
                val opened=test.waitForMonitorWithTimeout(monitor,10000) as? CaptureActivity ?: error("Click did not open CaptureActivity")
                test.removeMonitor(monitor); test.waitForIdleSync()
                return opened
            }
            fun close(a:CaptureActivity) { test.runOnMainSync { a.finish() }; test.waitForIdleSync(); SystemClock.sleep(300) }
            // Tapping 2 before 1 must never produce a valid measurement.
            close(click(corner)); check(!state.record(id).optBoolean("valid"))
            close(click(root))
            val first=state.record(id)
            check(first.optBoolean("measurementPending") && !first.optBoolean("valid")) { first.toString() }
            val raw=first.box("sourceBounds")!!
            check(abs(raw.width-900)<2 && abs(raw.height-600)<2) { "Android did not expose the expected unscaled layout size: $raw" }
            close(click(corner))
            val measured=state.record(id)
            val b=measured.box() ?: error(measured.toString())
            check(measured.optBoolean("valid")) { measured.toString() }
            check(abs(b.left-position[0])<=2 && abs(b.top-position[1])<=2)
            check(abs(b.width-900*scale)<=2 && abs(b.height-600*scale)<=2) { "Measured $b, expected scale=$scale" }
            close(click(root)); check(state.record(id).optBoolean("valid")) { "Unmoved root tap lost its measurement" }
            report.append("PASS real RemoteViews clicks scale=$scale raw=$raw measured=$b\n")
            test.runOnMainSync { root.translationX=20f }
            close(click(root)); check(state.record(id).optBoolean("measurementPending") && !state.record(id).optBoolean("valid"))
            report.append("PASS movement requires new corner tap; corner-first rejected\n")
            // A wallpaper/rotation/environment change between taps invalidates the pair.
            test.runOnMainSync { state.put("widget_$id",state.record(id).put("environment","old-environment")) }
            close(click(corner)); check(!state.record(id).optBoolean("valid"))
            report.append("PASS changed environment rejected\n")
            test.runOnMainSync { activity.finish() }
        }
        return report.toString()
    }
}
