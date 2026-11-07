package dev.animatedhome.diagnostics

import android.app.AlertDialog
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.*
import java.io.File

class MainActivity : LabActivity() {
    private val state by lazy { State(this) }
    private var tab = "Start"
    private var liveText: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            liveText?.text = if (tab == "Logs") State(this@MainActivity).report() else Live.snapshot().toString(2)
            handler.postDelayed(this,1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state.ids().forEach { CalibrationWidget.update(this,it) }
        tab = savedInstanceState?.getString("tab") ?: intent.getStringExtra("tab") ?: "Align"
        render()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("tab",tab); super.onSaveInstanceState(outState) }
    override fun onResume() { super.onResume(); handler.post(refresh); BackgroundImport.resumeCurrent(this) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    private fun render() {
        liveText = null
        page("Advanced tools", "Detailed readings and manual controls.")
        button("Back to Diagnostics") { openSetup(true) }
        val nav = LinearLayout(this)
        listOf("Start","Align","Pages","Logs").forEach { label -> nav.addView(Button(this).apply {
            text = label; isAllCaps = false; textSize = 13f; setPadding(0,0,0,0)
            setOnClickListener { tab = label; render() }
        },LinearLayout.LayoutParams(0,dp(48),1f)) }
        body.addView(nav)
        when(tab) { "Align" -> alignment(); "Pages" -> pages(); "Logs" -> logs(); else -> start() }
    }
    private fun start() {
        primary("Open My widgets (Skip all diagnostic tests)") { GuidedSurvey.skip(this) }
        section("01 · Preserve the background")
        text(if (File(filesDir,"background.jpg").exists()) "A background image is saved locally." else "No saved image. The wallpaper will use the calibration grid.")
        button("Choose photo · no storage permission") { BackgroundImport.picker(this) }
        button("Import current static wallpaper · optional") { BackgroundImport.current(this) }
        section("02 · Activate the live wallpaper")
        button("Open system wallpaper preview") {
            AlertDialog.Builder(this).setTitle("Before activation")
                .setMessage("Import your static background first if you want to preserve it. Another live wallpaper cannot be copied.\n\nThe preview shows only the background. Tap Set wallpaper in the system screen, then add widgets. Choose Home screen when the launcher offers that option.")
                .setNegativeButton("Cancel",null).setPositiveButton("Open preview") { _,_ ->
                    launch(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,ComponentName(this,LabWallpaper::class.java)))
                }.show()
        }
        section("03 · Add calibration widgets")
        text("Long-press the home screen → Widgets → Home Alignment Lab. Add a widget, resize it, and tap it once to pick a GIF or video.")
        button("Request a widget") {
            val manager = AppWidgetManager.getInstance(this)
            if (manager.isRequestPinAppWidgetSupported) manager.requestPinAppWidget(ComponentName(this,CalibrationWidget::class.java),null,null)
            else message("This launcher does not support pin requests. Add the widget from its home-screen Widgets menu.")
        }
        button("Read the on-device test procedure") { message(PROCEDURE) }
        section("Video experiment")
        text("Open the widget animation picker directly below. No 3-page survey is required.")
        button("Open selected widget controls") { VideoFlow.open(this) }
        text(device(this).toString(2),13f)
    }
    private fun selected() = state.prefs.getInt("selected",-1)
    private fun widgetSelector(): Boolean {
        val ids = state.ids()
        if (ids.isEmpty()) { text("Add a widget on your home screen, then tap it to capture its bounds."); return false }
        if (!ids.contains(selected())) state.prefs.edit().putInt("selected",ids.first()).apply()
        text("Selected widget #${selected()}")
        button("Select widget") {
            AlertDialog.Builder(this).setItems(ids.map { "Widget #$it" }.toTypedArray()) { _,n -> state.prefs.edit().putInt("selected",ids[n]).apply(); render() }.show()
        }
        return true
    }
    private fun alignment() {
        if (!widgetSelector()) return
        val id = selected(); val c = state.record(id)
        section("Capture and root coverage")
        text("#${id}: ${if(c.optBoolean("valid")) "valid bounds" else "needs capture"}\n${c.optJSONArray("bounds") ?: "No bounds"}\n${c.optString("invalidReason")}\nOptions (dp): ${c.optJSONObject("options")}",13f)
        text("Tap the root area away from the numbered 2 target to check repeatability. The 2 target deliberately measures the opposite corner. The host may reserve padding outside that root. Moving a widget without resizing is not reported by Android: tap again after every move.")
        button("Show alignment rectangle for 45 seconds") {
            AlignmentFlow.start(this,id)
        }
        text("ALIGN is an explicit temporary all-pages test. Keep this page visible. Page mode below is the guarded isolation test.",13f)
        button("Hide all test rectangles") { state.prefs.edit().putString("mode","OFF").apply(); message("Test rectangles hidden.") }
        section("Manual alignment fallback")
        text("Screen bounds include status/navigation areas. They are not automatically subtracted. Android does not publish a universal wallpaper crop/zoom transform. Choose a starting model, then adjust against all four outline edges. Corrections apply to this diagnostic display setup.")
        button("Viewport model: ${state.prefs.getString("mapping","CENTER")}") {
            val models = arrayOf("CENTER","ORIGIN","OFFSET","PIXEL")
            AlertDialog.Builder(this).setTitle("Empirical crop model").setItems(arrayOf("CENTER · centre excess surface pixels","ORIGIN · screen origin equals surface origin","OFFSET · excess surface × normalized offset","PIXEL · compensate reported pixel offsets")) { _,n ->
                state.prefs.edit().putString("mapping",models[n]).putString("mode","OFF").apply(); render()
            }.show()
        }
        val dx = number("Horizontal correction (surface px)",state.prefs.getFloat("dx",0f).toString())
        val dy = number("Vertical correction (surface px)",state.prefs.getFloat("dy",0f).toString())
        val scale = number("Observed compositor scale (0.5–2.0; 1 = no correction)",state.prefs.getFloat("scale",1f).toString())
        button("Save manual correction") {
            val x=dx.text.toString().toFloatOrNull(); val y=dy.text.toString().toFloatOrNull(); val z=scale.text.toString().toFloatOrNull()
            if (x == null || y == null || z == null || !x.isFinite() || !y.isFinite() || !z.isFinite() || x !in -4000f..4000f || y !in -4000f..4000f || z !in .5f..2f) message("Enter finite corrections within ±4000 px and a scale from 0.5 to 2.")
            else { state.prefs.edit().putFloat("dx",x).putFloat("dy",y).putFloat("scale",z).putString("mode","OFF").apply(); DiagnosticLog.event(this,"MANUAL_MAPPING","dx=$x dy=$y scale=$z"); message("Saved. Repeat the 45-second alignment check.") }
        }
        val outline = Switch(this).apply {
            text="Yellow calibration outline"; isChecked=state.prefs.getBoolean("outline",true)
            setOnCheckedChangeListener { _,value -> state.prefs.edit().putBoolean("outline",value).apply(); state.ids().forEach { CalibrationWidget.update(this@MainActivity,it) } }
        }; body.addView(outline)
        section("Folder tap mode")
        val folder = Switch(this).apply {
            text="Open folder when widget #$id is tapped"; isChecked=state.prefs.getBoolean("folder_$id",false)
            setOnCheckedChangeListener { _,value -> state.prefs.edit().putBoolean("folder_$id",value).apply(); CalibrationWidget.update(this@MainActivity,id) }
        }; body.addView(folder)
        button("Choose folder shortcuts") { FolderActivity.choose(this,id) }
    }
    private fun pages() {
        section("Three-page survey (Optional)")
        primary("Open guided A/B/C test") { launch(Intent(this,PageSurveyActivity::class.java)) }
        val verdict = PageEvidence.assess(state.visits())
        text("${verdict.code}\n${verdict.detail}",17f)
        text("This survey is optional. You can skip it above and use widgets on any home-screen page immediately.")
        state.visits().forEach { text("${it.page}${it.round} · stable=${it.stable} · ${it.offset ?: "no usable offset"}",12f) }
        button("Manage saved page readings") {launch(Intent(this,PageSurveyActivity::class.java))}
        if (widgetSelector()) button("Enable page gate for widget #${selected()}") {
            val c=state.record(selected())
            if (verdict.code != "CANDIDATE" || !c.optBoolean("valid") || !c.optBoolean("stable") || c.optString("environment") != environment(this)) {
                message("Need a repeatable three-page survey and a fresh stable capture of this widget in the same engine/display/launcher session."); return@button
            }
            val visitEnv=state.visits().lastOrNull()?.environment
            if (visitEnv != environment(this)) { message("Survey belongs to an older environment. Restart it."); return@button }
            state.prefs.edit().putString("mode","PAGE").apply(); home()
        }
        text("PAGE hides the rectangle on mismatched, moving, uncalibrated or locked states, and at a different zoom. It waits 500 ms after offset changes.",13f)
        section("Live engine values")
        liveText = text(Live.snapshot().toString(2),12f)
    }
    private fun logs() {
        button("Save diagnostic report and full logs") {
            saveReport()
        }
        button("Record a manually executed test result") { recordResult() }
        button("Read test procedure") { message(PROCEDURE) }
        liveText = text(state.report(),12f)
    }
    private fun recordResult() {
        val names = arrayOf("1 · Transparent/resizable widget","2 · Bounds and five-point root coverage","3 · Alignment incl. zoom/insets","4 · Three-page repeated offset survey","5 · Page isolation and swipe leakage","6 · Static wallpaper/photo import","7 · Video playback","8 · Folder shortcuts")
        AlertDialog.Builder(this).setTitle("Which test did you execute?").setItems(names) { _,index ->
            val note=EditText(this).apply { hint="Observed behavior, device/launcher, steps and next action"; minLines=3 }
            val statuses=arrayOf("PASS (manually observed)","FAIL (manually observed)","NOT YET TESTED")
            AlertDialog.Builder(this).setTitle(names[index]).setItems(statuses) { _,n ->
                AlertDialog.Builder(this).setTitle("Record ${statuses[n]}").setView(note).setNegativeButton("Cancel",null)
                    .setPositiveButton("Save observation") { _,_ ->
                        if(note.text.isBlank()) { message("An observation is required. No result was saved."); return@setPositiveButton }
                        val results=state.obj("results")
                        results.put(names[index],org.json.JSONObject().put("status",statuses[n]).put("observation",note.text.toString())
                            .put("recordedAt",System.currentTimeMillis()).put("source","user-entered").put("device",device(this)))
                        state.put("results",results)
                    }.show()
            }.show()
        }.show()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri=data.data!!
        when(requestCode) {
            BackgroundImport.PICK -> BackgroundImport.selected(this,uri)
            50 -> {
                val snapshot=state.report()
                Thread {
                    val result=runCatching {
                        contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use { out ->
                            out.write(snapshot)
                            for(name in listOf("diagnostics.previous.log","diagnostics.log")) {
                                val f=File(filesDir,name); if(f.exists()) { out.write("\n\nFULL LOG: $name\n"); f.reader().use { it.copyTo(out) } }
                            }
                        }
                    }
                    runOnUiThread { if(!isDestroyed) message(if(result.isSuccess) "Report saved." else "Report failed: ${result.exceptionOrNull()?.message}") }
                }.start()
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if((requestCode==BackgroundImport.READ_PERMISSION || requestCode==BackgroundImport.READ_IMAGES_PERMISSION)) {
            if(grantResults.firstOrNull()==android.content.pm.PackageManager.PERMISSION_GRANTED) BackgroundImport.current(this)
            else message("Permission denied. Choose photo works without broad storage access.")
        }
    }
    companion object {
        const val PROCEDURE = "1. Note phone model, Android/OxygenOS, launcher version, grid, resolution, display size, gesture/navigation mode. Import a photo or current static wallpaper before activation.\n\n2. Activate the live wallpaper; add one widget on each of three pages. Resize each. Tap all four inner corners and centre; compare captured bounds and widget IDs in logs. The five rectangles should agree.\n\n3. Select widget A; show the 45-second rectangle; return home. Compare its four cyan edges with the yellow outline. Adjust model/translation/scale if needed. Repeat at different sizes, locations, after unlock and overview zoom. Record measured error.\n\n4. Wait 2 seconds on page A, tap, label A1. Repeat B1 and C1. Return in reverse order C2, B2, A2. If values are identical across pages, record UNSUPPORTED. Repeat after page changes.\n\n5. If CANDIDATE, select A and enable the page gate. Swipe slowly and rapidly across all pages, including overview, app drawer, unlock and rotations. Any rectangle behind unrelated icons is a failure. Repeat for B and C. Do not infer success from the survey alone.\n\n6. Test photo picker, cancel/denial, current static wallpaper with optional access, and rejection of a live wallpaper. Verify the saved background after activating and restarting.\n\n7. After alignment and isolation pass, open the guided video test. Watch two loops, check placement and page isolation, then inspect decoder and frame statistics. Source 30 FPS does not establish achieved playback FPS.\n\n8. Choose folder apps; enable folder tap mode; tap the widget and launch each shortcut. Uninstall/disable one to test failure handling.\n\nRecord only executed results in Logs; export the report. Recalibrate after widget moves, launcher/grid/resolution/navigation changes or engine restart."
    }
}
