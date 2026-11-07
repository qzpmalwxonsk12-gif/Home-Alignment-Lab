package dev.animatedhome.diagnostics

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import java.util.UUID

object VideoFlow {
    fun recorded(state:State)=(state.prefs.getString("alignmentAnswer","")=="aligned" &&
        state.prefs.getString("isolationObservation","")==VideoPolicy.GOOD_ISOLATION && PageEvidence.assess(state.visits()).code=="CANDIDATE")
    fun target(state:State)=state.prefs.getInt("surveyWidgetA", state.ids().firstOrNull() ?: -1)
    fun evidenceMatches(context:Context):Boolean {
        val s=State(context); val c=s.record(target(s)); val visits=s.visits()
        val a=visits.lastOrNull { it.page=="A" } ?: return false
        return recorded(s) && visits.all { VideoPolicy.sameProfile(it.environment,environment(context)) } &&
            c.optBoolean("stable") && VideoPolicy.samePage(c.optJSONObject("offset")?.offset(),a.offset)
    }
    fun restoreSaved(context:Context,offset:Offset?,stable:Boolean,zoom:Float) {
        val s=State(context);val id=target(s);val c=s.record(id);val env=environment(context)
        if(c.optString("environment")==env)return
        val d=device(context)
        val restore=SavedSetupPolicy.canRestore(recorded(s),s.ids().contains(id),
            c.optBoolean("valid") && c.optInt("measurementVersion")==WidgetMeasurement.VERSION && c.box()?.valid(d.getInt("width"),d.getInt("height"))==true,
            VideoPolicy.sameProfile(c.optString("environment"),env) && s.visits().all {VideoPolicy.sameProfile(it.environment,env)},
            VideoPolicy.samePage(offset,c.optJSONObject("offset")?.offset()),stable,
            kotlin.math.abs(zoom-c.optDouble("zoom",-1.0).toFloat())<.001f)
        if(restore) {
            c.put("environment",env).put("stable",true).put("restoredAt",System.currentTimeMillis())
            s.put("widget_$id",c);DiagnosticLog.event(context,"SAVED_SETUP_RESTORED","widget=$id; same launcher, display, surface, insets, zoom and page; saved measured rectangle retained")
        }
    }
    fun ready(context:Context)=recorded(State(context)) && evidenceMatches(context) && AlignmentFlow.readiness(context,target(State(context)))=="READY"
    fun open(a:LabActivity) { a.launch(Intent(a,VideoActivity::class.java)); a.finish() }
    fun start(a:LabActivity,personal:Boolean=false) {
        if(MediaStore.busy.get()){a.message("Wait for the import to finish.");return}
        if(personal && !MediaStore.canPlay(a)){a.message("Choose and confirm the animation preview first.");return}
        if(!ready(a)) { a.message("Refresh the widget on page A first. If your home-screen layout changed, repeat the page check."); return }
        val s=State(a); val id=target(s); val c=s.record(id)
        s.put("videoAttempt",JSONObject().put("id",UUID.randomUUID().toString()).put("widgetId",id)
            .put("requestedAt",System.currentTimeMillis()).put("environment",environment(a)).put("bounds",c.optJSONArray("bounds"))
            .put("firstFramePosted",false))
        s.prefs.edit().putInt("selected",id).putBoolean("videoSetupPending",false).putBoolean("videoObservationPending",!personal)
            .putBoolean("personalPlayback",personal).putString("playbackMedia",if(personal) MediaStore.selected(a).toString() else "{}").putString("mediaError","")
            .putBoolean("outline",!personal).putBoolean("folder_$id",false)
            .putString("mode","VIDEO").putLong("videoUntil",if(personal) Long.MAX_VALUE else SystemClock.elapsedRealtime()+120_000).apply()
        CalibrationWidget.update(a,id)
        DiagnosticLog.event(a,"VIDEO_REQUEST",s.obj("videoAttempt")); a.home()
    }
    fun framePosted(context:Context) {
        val s=State(context); val a=s.obj("videoAttempt")
        if(s.prefs.getString("mode","")!="VIDEO" || a.length()==0 || a.optBoolean("firstFramePosted")) return
        a.put("firstFramePosted",true).put("firstFrameAt",System.currentTimeMillis()); s.put("videoAttempt",a)
        if(!s.prefs.getBoolean("personalPlayback",false))s.prefs.edit().putLong("videoUntil",SystemClock.elapsedRealtime()+90_000).apply()
        DiagnosticLog.event(context,"VIDEO_FIRST_FRAME",a)
    }
}

class VideoActivity:LabActivity() {
    private val state by lazy { State(this) }
    private val handler=Handler(Looper.getMainLooper())
    private var pickingKind="gif"
    private var widgetId=-1
    private var showingTroubleshoot=false
    private val importCheck=object:Runnable {override fun run(){if(MediaStore.busy.get())handler.postDelayed(this,300) else render()}}
    private fun k(base:String)=WidgetAnimations.key(base,widgetId)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        pickingKind=savedInstanceState?.getString("pickingKind") ?: "gif"
        showingTroubleshoot=savedInstanceState?.getBoolean("troubleshoot") ?: intent.getBooleanExtra("troubleshoot",false)
        widgetId=savedInstanceState?.getInt("widgetId") ?: intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,WidgetAnimations.current(this))
    }
    override fun onSaveInstanceState(out:Bundle){out.putBoolean("troubleshoot",showingTroubleshoot);out.putString("pickingKind",pickingKind);out.putInt("widgetId",widgetId);super.onSaveInstanceState(out)}
    override fun onResume(){super.onResume();if(showingTroubleshoot)troubleshoot() else render()}
    override fun onPause(){handler.removeCallbacks(importCheck);super.onPause()}
    private fun render() {
        showingTroubleshoot=false
        if(!state.ids().contains(widgetId) && state.ids().isNotEmpty()) {
            widgetId=state.ids().first()
        }
        val role=WidgetAnimations.role(state,widgetId)
        val subtitle=when(role) {
            "YOUTUBE" -> "Designated YouTube Stream Widget"
            "CHROME" -> "Designated Chrome Video Widget"
            else -> "Your animation, your way."
        }
        page(WidgetAnimations.name(state,widgetId), subtitle, {openSetup()})
        if(!state.ids().contains(widgetId)) {
            notice("This widget is no longer on your home screen.")
            primary("My widgets") {openSetup()};return
        }
        if(MediaStore.busy.get()) {
            notice("Saving and checking your animation…")
            text("Your other widgets keep their own files. Please wait until this copy finishes.")
            handler.removeCallbacks(importCheck);handler.postDelayed(importCheck,300);return
        }
        if(!AlignmentFlow.wallpaperActive(this)) {
            notice("Apply the wallpaper first, then return to this widget.")
            primary("Continue wallpaper setup") {openSetup()};return
        }
        val candidate=MediaStore.pending(this,widgetId)
        val media=MediaStore.selected(this,widgetId)
        val isStream=MediaStore.isStream(media)
        val selected=MediaStore.isPlayableSource(this,media)
        val failed=state.prefs.getBoolean(k("mediaImportFailed"),false)
        val c=state.record(widgetId)
        val activeNow=WidgetAnimations.enabled(state,widgetId) && selected && !failed
        if(candidate.length()>0 && MediaStore.file(this,candidate)!=null) {
            if(candidate.optBoolean("kindMismatch",false)) {
                notice("Note: You tapped Choose a ${candidate.optString("requestedKind")}, and this file is actually a ${candidate.optString("kind").uppercase()}. Check the preview below before confirming.")
            } else {
                notice("Check this is the file you chose")
            }
            preview(candidate)
            text("This preview reads the exact saved copy that will play in widget #$widgetId.",13f)
            primary("Use this ${candidate.optString("kind")}") {
                if(MediaStore.accept(this,widgetId)) {if(!WidgetAnimations.start(this,widgetId))render()}
                else message("The saved copy could not be confirmed. Choose the file again.")
            }
            button("Choose a different GIF") {choose("gif")}
            button("Choose a different video") {choose("video")}
            button("Cancel this selection") {MediaStore.keepPrevious(this,widgetId);render()}
        } else {
            if(failed) {
                notice(state.prefs.getString(k("mediaImportMessage"),"Could not use this file.")!!)
                text("This widget is stopped. Its previous file has not been substituted for your choice.")
            } else if(activeNow && c.optBoolean("valid")) {
                pill(if(isStream) "Streaming live on home screen" else "Playing on home screen")
            }
            if(selected) {
                text(if(failed) "Previously saved source" else if(isStream) "Active stream source" else "Saved animation",13f)
                preview(media)
                if(!failed) {
                    if(activeNow && c.optBoolean("valid")) {
                        primary("Done · Return to home screen") {home()}
                        button("Restart / play this widget") {WidgetAnimations.start(this,widgetId)}
                    } else {
                        primary("Play this widget") {WidgetAnimations.start(this,widgetId)}
                    }
                }
            }

            section("Stream & Role Designation")
            when(role) {
                "YOUTUBE" -> {
                    card {
                        pill("Designated YouTube Widget")
                        text("Exclusive YouTube Stream Receiver",19f)
                        text("Whenever you tap Share → Stream to Home Widget in the YouTube app, the video streams directly into this widget. You can also browse or paste a YouTube link below.",13f,MUTED)
                        primary("Open YouTube Streamer / Paste link") {openStreamer("youtube")}
                        val muted=WidgetAnimations.streamMuted(state,widgetId)
                        button(if(muted) "Stream audio: Muted (Tap to enable sound)" else "Stream audio: Sound ON (Tap to mute)") {
                            WidgetAnimations.setStreamMuted(this,widgetId,!muted);render()
                        }
                        button("Revert role to standard widget") {
                            WidgetAnimations.setRole(this,widgetId,"STANDARD");render()
                        }
                    }
                }
                "CHROME" -> {
                    card {
                        pill("Designated Chrome Widget")
                        text("Exclusive Chrome / Web Video Receiver",19f)
                        text("Whenever you tap Share → Stream to Home Widget in Google Chrome (or use the built-in browser below), the background video streams directly into this widget.",13f,MUTED)
                        primary("Open Chrome Video Streamer / Browser") {openStreamer("chrome")}
                        val muted=WidgetAnimations.streamMuted(state,widgetId)
                        button(if(muted) "Stream audio: Muted (Tap to enable sound)" else "Stream audio: Sound ON (Tap to mute)") {
                            WidgetAnimations.setStreamMuted(this,widgetId,!muted);render()
                        }
                        button("Revert role to standard widget") {
                            WidgetAnimations.setRole(this,widgetId,"STANDARD");render()
                        }
                    }
                }
                else -> {
                    val ytId=WidgetAnimations.youtubeWidget(state)
                    val chId=WidgetAnimations.chromeWidget(state)
                    val ytNote=if(ytId!=-1 && ytId!=widgetId) " (Currently ${WidgetAnimations.name(state,ytId)})" else ""
                    val chNote=if(chId!=-1 && chId!=widgetId) " (Currently ${WidgetAnimations.name(state,chId)})" else ""
                    button("Designate as YouTube Widget$ytNote") {
                        WidgetAnimations.setRole(this,widgetId,"YOUTUBE");render()
                    }
                    button("Designate as Chrome Widget$chNote") {
                        WidgetAnimations.setRole(this,widgetId,"CHROME");render()
                    }
                }
            }

            section("Local GIF or Video File")
            primary("Choose a GIF") {choose("gif")}
            button("Choose a video") {choose("video")}
            if(failed && selected)button("Keep previous animation") {MediaStore.keepPrevious(this,widgetId);render()}

            section("Page offset & widget behavior")
            val ignoringOffset=WidgetAnimations.ignorePageOffset(state,widgetId)
            card {
                if(ignoringOffset) {
                    pill("Visible on all home pages · Offset ignored")
                    text("Pinned across all home pages",18f)
                    text("This widget ignores home-screen / A-B-C page offsets and stays visible on every home page when you swipe.",13f,MUTED)
                    primary("Revert to single-page offset (Use page offset)") {
                        WidgetAnimations.setIgnorePageOffset(this,widgetId,false)
                        render()
                    }
                } else {
                    eyebrow("Multi-page visibility")
                    text("Stay on one page or show on all pages",18f)
                    text("By default, this widget moves with its home-screen page. Tap below to ignore the page/A-B-C offset so it stays visible on all home pages.",13f,MUTED)
                    button("Pin to all home pages (Ignore A/B/C page offset)") {
                        WidgetAnimations.setIgnorePageOffset(this,widgetId,true)
                        render()
                    }
                }
            }
            actionRow("Display fit",WidgetAnimations.scaleModeLabel(WidgetAnimations.scaleMode(state,widgetId)),"appearance") {
                val modes=arrayOf("FIT","FILL","FIT_BLACK")
                android.app.AlertDialog.Builder(this).setTitle("Display fit").setSingleChoiceItems(modes.map {WidgetAnimations.scaleModeLabel(it)}.toTypedArray(),modes.indexOf(WidgetAnimations.scaleMode(state,widgetId))) {dialog,index->
                    state.prefs.edit().putString(k("scaleMode"),modes[index]).apply();dialog.dismiss();render()
                }.setNegativeButton("Close",null).show()
            }
            actionRow("Swipe behavior",WidgetAnimations.swipeModeLabel(WidgetAnimations.swipeMode(state,widgetId)),"pages") {
                val modes=arrayOf("SLIDE","INSTANT","SETTLED","ALWAYS")
                android.app.AlertDialog.Builder(this).setTitle("Swipe behavior").setSingleChoiceItems(modes.map {WidgetAnimations.swipeModeLabel(it)}.toTypedArray(),modes.indexOf(WidgetAnimations.swipeMode(state,widgetId))) {dialog,index->
                    val chosen=modes[index]
                    WidgetAnimations.setIgnorePageOffset(this,widgetId,chosen=="ALWAYS")
                    state.prefs.edit().putString(k("swipeMode"),chosen).apply()
                    dialog.dismiss();render()
                }.setNegativeButton("Close",null).show()
            }
            val folderMode=state.prefs.getBoolean("folder_$widgetId",false)
            actionRow("Tap action",if(folderMode) "Open shortcut folder" else "Open animation controls","widgets") {
                android.app.AlertDialog.Builder(this).setTitle("When you tap this widget").setSingleChoiceItems(arrayOf("Open animation controls","Open shortcut folder"),if(folderMode)1 else 0) {dialog,index->
                    state.prefs.edit().putBoolean("folder_$widgetId",index==1).apply();CalibrationWidget.update(this,widgetId);dialog.dismiss();render()
                }.setNegativeButton("Close",null).show()
            }
            if(folderMode)button("Choose folder shortcuts") {FolderActivity.choose(this,widgetId)}
            if(selected) {
                button("↻  Reset & sync all widgets to start together") {WidgetAnimations.syncAll(this)}
                button("Stop this widget") {WidgetAnimations.stop(this,widgetId);render()}
            }
            val error=state.prefs.getString(k("mediaError"),"")!!
            if(error.isNotEmpty())notice(error)
        }
        if(!c.optBoolean("valid"))notice("Place this widget, then tap it once on your home screen to save its location. If that does not work, use Troubleshoot this widget below.")
        text("Tap this widget any time to change its animation or stream. Move or resize it, then tap it once to update its position. Unlimited widgets can be enabled across your home screen.",13f)
        section("More")
        actionRow("Rename widget","Give it a name you will recognize.","widgets") {
            val input=android.widget.EditText(this).apply {setSingleLine();setText(WidgetAnimations.name(state,widgetId));filters=arrayOf(android.text.InputFilter.LengthFilter(40));setPadding(dp(20),dp(12),dp(20),dp(12))}
            android.app.AlertDialog.Builder(this).setTitle("Widget name").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save") {_,_->
                val name=input.text.toString().trim();if(name.isNotEmpty())state.prefs.edit().putString(k("widgetName"),name).apply();render()
            }.show()
        }
        actionRow("Troubleshoot & debug this widget","Adjust position, pink alignment box, A/B/C test & reports.","diagnostics") {troubleshoot()}
        actionRow("Battery & performance","Hardware mAh, CPU %, thermals & video optimizer.","diagnostics") {launch(Intent(this,SetupActivity::class.java).putExtra("batteryView",true))}
        link("Back to Home") {openSetup()}
        button("File size, streaming & format help") {message("YouTube & Chrome Streaming: Designate one widget as your YouTube Widget and another as your Chrome Widget. In the YouTube app or Chrome, tap Share → Stream to Home Widget, or use the built-in streamer inside the widget menu.\n\nVideos: up to 100 MB, ten minutes, 4K in either orientation, and 125 FPS. H.264 MP4 is recommended.\n\nGIFs: up to 10 MB, 1920 × 1920 pixels, 600 frames, two minutes. Transparency is preserved.\n\nPage offset pin: Use 'Pin to all home pages' to ignore A/B/C page offsets so a widget stays visible across every home screen page.")}
    }
    private fun openStreamer(kind:String) {
        launch(Intent(this,WebStreamActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId).putExtra("kind",kind))
        finish()
    }
    private fun troubleshoot() {
        showingTroubleshoot=true
        page("Troubleshoot ${WidgetAnimations.name(state,widgetId)}", "Optional debugging & calibration tools.", {render()})
        card {
            eyebrow("Position & alignment")
            text("Adjust or verify widget bounds",20f)
            text("If the animation looks slightly shifted, calibrate this widget's two corners or show a 45-second pink alignment box behind it.",14f,MUTED)
            primary("Adjust position (2-corner or automatic)") {WidgetAnimations.troubleshoot(this,widgetId)}
            button("Show pink alignment box (45s)") {
                WidgetAnimations.ensureLocated(this,widgetId)
                val c=state.record(widgetId)
                if(!c.optBoolean("valid"))message("Adjust this widget's position first.")
                else {
                    state.prefs.edit().putInt("pinkWidget",widgetId).putLong("pinkUntil",SystemClock.elapsedRealtime()+45000).apply()
                    CalibrationWidget.update(this,widgetId)
                    home()
                }
            }
        }
        section("Page & system debugging")
        actionRow("Run A/B/C page test","Test how your launcher reports home-screen pages A, B, and C.","pages") {launch(Intent(this,PageSurveyActivity::class.java))}
        actionRow("Download diagnostic report","Export full widget, page, playback, and battery logs (.txt).","download") {saveReport()}
        actionRow("Battery & performance monitor","Inspect mAh drain, current (mA), CPU % & optimize videos.","diagnostics") {launch(Intent(this,SetupActivity::class.java).putExtra("batteryView",true))}
        button("Animation missing or on another page?") {message("1. Check that Home Alignment is your applied home wallpaper.\n\n2. Return to this widget's page, wait a moment, then tap it once and press Play this widget.\n\n3. If you want this widget to stay visible across all pages without shifting, tap 'Pin to all home pages (Ignore A/B/C page offset)' in the widget menu.")}
        link("Back to ${WidgetAnimations.name(state,widgetId)}") {render()}
    }
    private fun preview(info:JSONObject) {
        card {
            text(info.optString("name","My animation"),19f)
            if(MediaStore.isStream(info)) {
                val streamType=if(info.optString("kind")=="youtube") "YOUTUBE LIVE STREAM" else "CHROME WEB STREAM"
                val audioLabel=if(info.optBoolean("muted",true)) "Muted" else "Sound ON"
                val modeLabel=if(info.optBoolean("isolateVideo",true)) "Isolated video" else "Full webpage"
                text("$streamType · $modeLabel · $audioLabel",13f,ACCENT)
                text(info.optString("url",""),12f,MUTED)
            } else {
                val sec=(info.optLong("durationUs")/100_000L)/10.0
                val frames=info.optInt("frames",0)
                val details=buildString {
                    append("${info.optString("kind").uppercase()} · ${info.optInt("width")} × ${info.optInt("height")}")
                    if(frames>0)append(" · $frames frames")
                    if(sec>0)append(" · ${sec}s")
                }
                text(details,13f)
                MediaStore.file(this,info)?.let {file->body.addView(MediaPreview(this,file,info.optString("kind"),info.optLong("durationUs")),android.widget.LinearLayout.LayoutParams(-1,dp(190)))}
            }
        }
    }
    @Deprecated("Legacy back support") override fun onBackPressed(){if(showingTroubleshoot)render() else openSetup()}
    private fun choose(kind:String) {
        pickingKind=kind
        WidgetAnimations.stop(this,widgetId)
        runCatching {startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType(if(kind=="gif") "image/gif" else "video/*").putExtra(Intent.EXTRA_LOCAL_ONLY,true).putExtra(Intent.EXTRA_TITLE,"Choose a $kind"),72)}
            .onFailure {message("No file picker is available. Enable the phone's Files app, then retry.")}
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==72 && resultCode==RESULT_OK && data?.data!=null){MediaStore.import(this,data.data!!,pickingKind,widgetId);render()}
    }
}
