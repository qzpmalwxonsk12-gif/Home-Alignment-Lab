package dev.animatedhome.diagnostics

import android.app.AlertDialog
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.os.Bundle
import org.json.JSONObject

class CaptureActivity : LabActivity() {
    private var captured: JSONObject? = null
    private var answeredHere = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(openPersonalControls())return
        captured = savedInstanceState?.getString("capture")?.let { JSONObject(it) } ?: capture(this)
        answeredHere=savedInstanceState?.getBoolean("answeredHere") ?: false
    }
    override fun onResume() { super.onResume(); if(!isFinishing)renderCapture() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); if(openPersonalControls())return; captured=capture(this); answeredHere=false; renderCapture()
    }
    private fun openPersonalControls():Boolean {
        if(GuidedSurvey.capture(this))return true
        val s=State(this);val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1)
        if(!s.ids().contains(id) || s.prefs.getBoolean(WidgetAnimations.key("adjust",id),false) ||
            s.prefs.getBoolean("guidedSurvey",false) || intent.getBooleanExtra("corner",false))return false
        WidgetAnimations.locate(this,id)
        WidgetAnimations.open(this,id);return true
    }
    private fun renderCapture() {
        val c=captured
        val state=State(this)
        val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1)
        if(state.prefs.getBoolean(WidgetAnimations.key("adjust",id),false)) {
            page("Adjust widget #$id", "Optional troubleshooting · Only this widget")
            when {
                c?.optBoolean("measurementPending")==true -> {
                    notice("First corner saved. Go home and tap 2 at this widget's bottom-right, or skip corner 2 below to use automatic sizing.")
                    primary("Go home and tap 2") {home()}
                    button("Use automatic size (Skip corner 2)") {
                        WidgetAnimations.ensureLocated(this,id)
                        state.prefs.edit().putBoolean(WidgetAnimations.key("adjust",id),false).remove(WidgetAnimations.key("adjustBackup",id)).apply()
                        CalibrationWidget.update(this,id);WidgetAnimations.open(this,id)
                    }
                }
                c?.optBoolean("valid")==true -> {
                    notice("Position saved. You can play your animation now, or check the outline from Troubleshoot this widget.")
                    primary("Back to this widget") {
                        state.prefs.edit().putBoolean(WidgetAnimations.key("adjust",id),false).remove(WidgetAnimations.key("adjustBackup",id)).apply()
                        CalibrationWidget.update(this,id);WidgetAnimations.open(this,id)
                    }
                }
                else -> {
                    notice(c?.optString("invalidReason") ?: "Tap 1 on this widget first, or use automatic position below.")
                    primary("Go home and tap 1") {home()}
                    button("Use automatic position (Skip 2-corner tap)") {
                        WidgetAnimations.ensureLocated(this,id)
                        state.prefs.edit().putBoolean(WidgetAnimations.key("adjust",id),false).remove(WidgetAnimations.key("adjustBackup",id)).apply()
                        CalibrationWidget.update(this,id);WidgetAnimations.open(this,id)
                    }
                }
            }
            button("Cancel adjustment") {
                val backup=state.obj(WidgetAnimations.key("adjustBackup",id))
                if(backup.length()>0)state.put("widget_$id",backup)
                state.prefs.edit().putBoolean(WidgetAnimations.key("adjust",id),false).remove(WidgetAnimations.key("adjustBackup",id)).apply()
                CalibrationWidget.update(this,id);WidgetAnimations.open(this,id)
            }
            button("Save report") {saveReport()}
            return
        }
        if(!AlignmentFlow.wallpaperActive(this)) {
            page("First, turn on the wallpaper", "SETUP NEEDED · Your widget is already added")
            notice("The widget marks a location. The lab wallpaper draws the animation behind it.")
            text("We'll guide you through choosing a background and applying it. Then come back and tap this widget again.")
            primary("Continue wallpaper setup") { openSetup() }
        } else if(c?.optBoolean("measurementPending") == true) {
            page("Now tap 2 on the widget", "OPTIONAL · First corner saved ✓")
            text("Tap 2 at the bottom-right of the widget, or skip corner 2 below to go straight to choosing your GIF or video.")
            primary("Skip corner 2 · Choose GIF or video now") {
                WidgetAnimations.ensureLocated(this,id)
                WidgetAnimations.open(this,id)
            }
            button("Go home and tap 2") { home() }
        } else if(c==null || !c.optBoolean("valid")) {
            page("Let's locate the widget", "Wallpaper applied ✓")
            notice("Tap Skip & auto-locate below to choose your GIF or video immediately, or return home and tap the widget once.")
            primary("Skip & auto-locate · Choose GIF or video") {
                val targetId = if(id != -1) id else (state.ids().firstOrNull() ?: -1)
                if(targetId != -1) {
                    WidgetAnimations.ensureLocated(this,targetId)
                    WidgetAnimations.open(this,targetId)
                } else openSetup()
            }
            button("Go home and tap widget") { home() }
            button("Save a report if this keeps happening") { saveReport() }
        } else if(state.prefs.getBoolean("videoSetupPending",false) || state.prefs.getBoolean("videoObservationPending",false) || state.prefs.getBoolean("personalPlayback",false)) {
            VideoFlow.open(this); return
        } else if(state.prefs.getBoolean("isolationPending",false)) {
            launch(Intent(this,PageSurveyActivity::class.java)); finish(); return
        } else if(state.prefs.getBoolean("guidedSurvey",false)) {
            launch(Intent(this,PageSurveyActivity::class.java));finish();return
        } else if(answeredHere) {
            val answer=state.prefs.getString("alignmentAnswer","")
            page(when(answer) { "aligned" -> "First check complete"; "offset" -> "The box needs adjusting"; else -> "Let's find the missing box" }, "Your observation has been saved")
            primary("Choose GIF or video for this widget") { WidgetAnimations.open(this,c.optInt("widgetId")) }
            button("My widgets") { openSetup() }
            if(answer!="aligned") {
                button("Save report for troubleshooting") { saveReport() }
                button("Try showing the pink box again") { AlignmentFlow.start(this,c.optInt("widgetId")) }
            }
        } else if(state.obj("alignmentAttempt").let { it.optInt("measurementVersion")==WidgetMeasurement.VERSION && !it.has("answer") &&
                it.optInt("widgetId")==c.optInt("widgetId") && it.optString("environment")==c.optString("environment") && WidgetMeasurement.same(it.box(),c.box()) }) {
            renderObservation(this) { answeredHere=true; renderCapture() }
        } else {
            page("Widget #${c.optInt("widgetId")} ready", "Wallpaper applied ✓ · Widget located ✓")
            text("Your widget is located and ready. Choose a GIF or video now, or optionally test alignment with the pink box below.")
            primary("Choose GIF or video now") { WidgetAnimations.open(this,c.optInt("widgetId")) }
            button("Optional: Show pink test box") { AlignmentFlow.start(this,c.optInt("widgetId")) }
        }
        button("Help") { openSetup(true) }
        if(c!=null) button("More tests & technical details") {
            AlertDialog.Builder(this).setTitle("Optional tools").setItems(arrayOf("View captured position", "Record a home-screen page visit", "Open advanced controls")) { _,which ->
                when(which) {
                    0 -> details("Widget #${c.optInt("widgetId")}",c.toString(2))
                    1 -> {
                        val labels=listOf("A","B","C").flatMap { p -> (1..2).map { p to it } }
                        AlertDialog.Builder(this).setTitle("Which page visit?").setItems(labels.map { "Page ${it.first} · visit ${it.second}" }.toTypedArray()) { _,index ->
                            message(state.addVisit(c,labels[index].first,labels[index].second))
                        }.show()
                    }
                    2 -> launch(Intent(this,MainActivity::class.java).putExtra("tab","Align"))
                }
            }.show()
        }
    }
    override fun onSaveInstanceState(outState: Bundle) { captured?.let { outState.putString("capture",it.toString()) }; outState.putBoolean("answeredHere",answeredHere); super.onSaveInstanceState(outState) }
}
