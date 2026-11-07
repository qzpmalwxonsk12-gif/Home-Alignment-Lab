package dev.animatedhome.diagnostics

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import java.io.File

class SetupActivity:LabActivity(),SharedPreferences.OnSharedPreferenceChangeListener {
    private val state by lazy {State(this)}
    private var screen="Home"
    private var openedPreview=false
    private val handler=Handler(Looper.getMainLooper())
    private val refresh=Runnable {if(!isFinishing)render()}
    private val optimizationTick=object:Runnable {override fun run(){if(screen=="Performance")render();if(OptimizationJob.busy)handler.postDelayed(this,1000)}}
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        screen=savedInstanceState?.getString("screen") ?: when {
            intent.getBooleanExtra("help",false)->"Diagnostics"
            intent.getBooleanExtra("batteryView",false)->"Performance"
            intent.getBooleanExtra("backgroundChoice",false)->"Appearance"
            intent.getBooleanExtra("setup",false)->"Setup"
            else->"Home"
        }
        openedPreview=savedInstanceState?.getBoolean("openedPreview") ?: false
        GuidedSurvey.migrate(this);BatteryTelemetry.ensureStarted(this)
        state.ids().forEach {CalibrationWidget.update(this,it)}
    }
    override fun onResume(){super.onResume();state.prefs.registerOnSharedPreferenceChangeListener(this);render();BackgroundImport.resumeCurrent(this);if(OptimizationJob.busy)handler.post(optimizationTick)}
    override fun onPause(){state.prefs.unregisterOnSharedPreferenceChangeListener(this);handler.removeCallbacksAndMessages(null);super.onPause()}
    override fun onSaveInstanceState(out:Bundle){out.putString("screen",screen);out.putBoolean("openedPreview",openedPreview);super.onSaveInstanceState(out)}
    override fun onSharedPreferenceChanged(p:SharedPreferences?,key:String?) {
        if(key=="backgroundVersion") {if(screen=="Setup")screen="Setup";handler.removeCallbacks(refresh);handler.post(refresh)}
        else if(screen=="Home" && key!=null && (key=="youtubeWidgetId" || key=="chromeWidgetId" || key.startsWith("ignorePageOffset_") || key.startsWith("selectedMedia_") || key.startsWith("pendingMedia_") || key.startsWith("enabled_") || key.startsWith("mediaError_") || key.startsWith("widget_"))) {
            handler.removeCallbacks(refresh);handler.postDelayed(refresh,150)
        }
    }
    private fun go(destination:String){screen=destination;render()}
    private fun render() {
        when(screen){"Appearance"->appearance();"Diagnostics"->diagnostics();"Setup"->setup();"Performance"->performance();else->dashboard()}
        if(screen in listOf("Home","Appearance","Diagnostics"))navigation(screen){go(it)}
    }
    private fun status(id:Int):WidgetStatus {
        val media=MediaStore.selected(this,id);val c=state.record(id);val d=device(this)
        val hasPos=c.optBoolean("valid") && c.box()?.valid(d.getInt("width"),d.getInt("height"))==true
        return WidgetStatusPolicy.assess(
            AlignmentFlow.wallpaperActive(this),
            MediaStore.isPlayableSource(this,media),
            WidgetAnimations.enabled(state,id),
            hasPos,
            MediaStore.pending(this,id).length()>0,
            state.prefs.getBoolean(WidgetAnimations.key("mediaImportFailed",id),false) || state.prefs.getString(WidgetAnimations.key("mediaError",id),"")!!.isNotEmpty()
        )
    }
    private fun dashboard() {
        page("Your home, in motion.","HOME ALIGNMENT STUDIO  ·  ACTIVE WIDGETS")
        val ids=state.ids();val active=AlignmentFlow.wallpaperActive(this)
        val enabled=ids.count {WidgetAnimations.enabled(state,it)}
        val fpsLabel=if(state.prefs.getBoolean("highFpsMode",false))"60 FPS" else "30 FPS"
        metrics(listOf(ids.size.toString() to "Widgets",enabled.toString() to "Active",fpsLabel to "Target rate"))
        if(!active)card {
            pill("Setup needed",true);text("Make room for a little motion.",21f)
            text("Choose a background, apply the live wallpaper, then add your animated widgets.",14f,MUTED)
            primary("${if(state.prefs.getBoolean("backgroundChosen",false)) "Continue" else "Start"} setup") {go("Setup")}
        } else {
            card {
                pill("Wallpaper active · Ready")
                text("Live wallpaper engine connected",17f)
                text("Off-screen widgets automatically freeze at 0% CPU and resume in <2ms when swiped onto screen.",13f,MUTED)
            }
        }
        if(state.prefs.getBoolean("guidedSurvey",false) || state.prefs.getBoolean("isolationPending",false)) {
            actionRow("Continue A/B/C page test","Your optional 3-page check is currently in progress.","pages"){launch(Intent(this,PageSurveyActivity::class.java))}
        }
        if(ids.isNotEmpty()) {
            section("Stream display receivers")
            val ytId=WidgetAnimations.youtubeWidget(state)
            val chId=WidgetAnimations.chromeWidget(state)
            actionRow(
                "Designated YouTube Widget",
                if(ytId!=-1) "${WidgetAnimations.name(state,ytId)} · Receives YouTube Share links" else "Tap to designate which widget plays shared YouTube videos",
                "play"
            ) {chooseRoleWidget("YOUTUBE")}
            actionRow(
                "Designated Chrome Widget",
                if(chId!=-1) "${WidgetAnimations.name(state,chId)} · Plays Chrome background videos" else "Tap to designate which widget plays Chrome web videos",
                "widgets"
            ) {chooseRoleWidget("CHROME")}
        }
        section("Your widgets")
        if(ids.isEmpty())card {
            body.addView(AppGlyph(this,"widgets",ACCENT),LinearLayout.LayoutParams(dp(40),dp(40)))
            text("No widgets placed yet",21f)
            text("Tap Add widget below to pin a transparent animation window to your home screen, resize it, and choose a GIF, video, or live YouTube/Chrome stream.",14f,MUTED)
        }
        for(id in ids)widgetCard(id)
        if(ids.any {MediaStore.isPlayableSource(this,MediaStore.selected(this,it))}) {
            button("↻  Reset & sync all animations to start together") {WidgetAnimations.syncAll(this)}
        }
        primary("+  Add widget to home screen") {if(active)addWidget() else go("Setup")}
        section("Quick access")
        actionRow("Appearance & background","Change wallpaper backdrop or frame rate limit.","appearance") {go("Appearance")}
        actionRow("Diagnostics, A/B/C test & reports","Troubleshoot alignment, run the A/B/C page check, or download battery/diagnostic logs.","diagnostics") {go("Diagnostics")}
        text("Unlimited widgets can be active across your home screen. Tap any widget after moving or resizing it.",12f,MUTED)
    }
    private fun chooseRoleWidget(role:String) {
        val ids=state.ids()
        if(ids.isEmpty()){message("Add a widget to your home screen first.");return}
        val currentId=if(role=="YOUTUBE") WidgetAnimations.youtubeWidget(state) else WidgetAnimations.chromeWidget(state)
        val labels=(ids.map {"${WidgetAnimations.name(state,it)} (#$it)"}+listOf("None (Clear designation)")).toTypedArray()
        val checked=ids.indexOf(currentId).let {if(it>=0) it else ids.size}
        val title=if(role=="YOUTUBE") "Designated YouTube Widget" else "Designated Chrome Widget"
        android.app.AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels,checked) {dialog,index->
            if(index in ids.indices) {
                WidgetAnimations.setRole(this,ids[index],role)
            } else if(currentId!=-1) {
                WidgetAnimations.setRole(this,currentId,"STANDARD")
            }
            dialog.dismiss();render()
        }.setNegativeButton("Close",null).show()
    }
    private fun widgetCard(id:Int) {
        val media=MediaStore.selected(this,id);val status=status(id)
        val isEnabled=WidgetAnimations.enabled(state,id)
        val isStream=MediaStore.isStream(media)
        val hasSource=MediaStore.isPlayableSource(this,media)
        val role=WidgetAnimations.role(state,id)
        val ignoreOffset=WidgetAnimations.ignorePageOffset(state,id)
        card {
            val badge=buildString {
                append(if(isEnabled && status.title=="Enabled") (if(isStream) "Streaming on home screen" else "Playing on home screen") else status.title)
                if(role=="YOUTUBE") append(" · YOUTUBE WIDGET")
                else if(role=="CHROME") append(" · CHROME WIDGET")
                if(ignoreOffset) append(" · ALL PAGES")
            }
            pill(badge,status.attention)
            val row=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL}
            val file=MediaStore.file(this,media)
            val preview=if(file!=null)MediaPreview(this,file,media.optString("kind"),media.optLong("durationUs"),false) else AppGlyph(this,"play",if(isStream) ACCENT else MUTED)
            preview.background=rounded(INK,14);preview.clipToOutline=true
            row.addView(preview,LinearLayout.LayoutParams(dp(80),dp(80)).apply {marginEnd=dp(14)})
            val parent=body;val labels=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL};body=labels
            text(WidgetAnimations.name(state,id),18f).apply {typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)}
            text(media.optString("name",when(role){"YOUTUBE"->"Ready for YouTube Share / Stream";"CHROME"->"Ready for Chrome video stream";else->"No animation selected"}),13f,WHITE).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
            val modeMeta=if(hasSource) "${media.optString("kind","").uppercase()} · ${WidgetAnimations.scaleModeLabel(WidgetAnimations.scaleMode(state,id))} · ${WidgetAnimations.swipeModeLabel(WidgetAnimations.swipeMode(state,id))}" else "Tap below to choose a GIF, video, or stream"
            text(modeMeta,12f,MUTED).apply {maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END}
            body=parent;row.addView(labels,LinearLayout.LayoutParams(0,-2,1f));body.addView(row)
            text(status.detail,13f,MUTED)
            if(!hasSource) {
                primary(when(role){"YOUTUBE"->"Open YouTube Streamer / Settings";"CHROME"->"Open Chrome Streamer / Settings";else->"Choose animation or stream"}) {WidgetAnimations.open(this,id)}
            } else {
                primary("Customize ${WidgetAnimations.name(state,id)}") {WidgetAnimations.open(this,id)}
                if(isEnabled) {
                    button("Pause widget") {WidgetAnimations.stop(this,id);render()}
                } else {
                    button("Resume playback") {WidgetAnimations.start(this,id);render()}
                }
            }
        }
    }
    private fun appearance() {
        page("Appearance","The backdrop to your everyday.")
        card {
            pill(if(AlignmentFlow.wallpaperActive(this))"Wallpaper connected" else "Wallpaper not applied",!AlignmentFlow.wallpaperActive(this))
            text("Your background",22f)
            val saved=File(filesDir,"background.jpg")
            if(saved.exists() && !state.prefs.getBoolean("useGrid",false)) {
                val view=android.widget.ImageView(this).apply {
                    scaleType=android.widget.ImageView.ScaleType.CENTER_CROP
                    val options=android.graphics.BitmapFactory.Options().apply {inSampleSize=4}
                    setImageBitmap(android.graphics.BitmapFactory.decodeFile(saved.path,options));background=rounded(INK,16);clipToOutline=true;contentDescription="Saved wallpaper background"
                }
                body.addView(view,LinearLayout.LayoutParams(-1,dp(160)))
            } else {body.addView(AppGlyph(this,"appearance",ACCENT),LinearLayout.LayoutParams(dp(54),dp(54)));text("Geometric dark grid background",14f,MUTED)}
            if(!AlignmentFlow.wallpaperActive(this))primary("Apply wallpaper") {wallpaperPreview()}
        }
        backgroundChoices()
        section("Playback preference")
        actionRow("Frame rate",if(state.prefs.getBoolean("highFpsMode",false))"Up to 60 FPS · Max smoothness" else "Up to 30 FPS · Battery saver (Recommended)","play") {frameRate()}
        text("Display fit, swipe behavior, and folder shortcuts are available inside each widget's controls.",13f,MUTED)
    }
    private fun backgroundChoices() {
        actionRow("Choose a photo","Pick an image from your phone.","appearance") {BackgroundImport.picker(this)}
        actionRow("Use current / system wallpaper","Copy your static home wallpaper.","appearance") {BackgroundImport.current(this)}
        actionRow("Use geometric background","A clean dark studio grid behind your widgets.","widgets") {
            state.prefs.edit().putBoolean("backgroundChosen",true).putBoolean("useGrid",true).putLong("backgroundVersion",System.currentTimeMillis()).apply();render()
        }
        text("If Home Alignment is already applied, Current wallpaper keeps its saved background. Choosing a photo needs no storage permission.",12f,MUTED)
    }
    private fun setup() {
        val active=AlignmentFlow.wallpaperActive(this)
        val step=SetupPolicy.step(state.prefs.getBoolean("backgroundChosen",false),active,state.ids().isNotEmpty())
        if(step>3){go("Home");return}
        page("Set up your space","Three quick steps, no calibration tests required.",{go("Home")})
        pill("Step $step of 3")
        when(step) {
            1->{card {text("Choose the backdrop",23f);text("Pick the image you want around your animated widgets. Applying Home Alignment in the next step sets your live wallpaper.",14f,MUTED)};backgroundChoices()}
            2->{card {text("Connect the wallpaper",23f);text("On Android's next screen, press Set wallpaper or Apply and choose Home screen. Then return here.");if(openedPreview)notice("The wallpaper is not applied yet. Going back from the preview without pressing Apply does not turn it on.");primary("Open wallpaper preview") {wallpaperPreview()}};link("Change background") {go("Appearance")}}
            else->{card {text("Add your first widget",23f);text("Place it on your home screen and resize it. Tap it once, choose a GIF or video, check the preview, and confirm.");primary("Add widget") {addWidget()};button("Go to home screen") {home()}};text("If your launcher does not place it automatically: long-press an empty home-screen space → Widgets → Home Alignment.",13f,MUTED)}
        }
        link("Open Diagnostics & Help") {go("Diagnostics")}
    }
    private fun wallpaperPreview() {
        openedPreview=true
        launch(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,ComponentName(this,LabWallpaper::class.java)))
    }
    private fun addWidget() {
        val manager=AppWidgetManager.getInstance(this)
        if(manager.isRequestPinAppWidgetSupported)manager.requestPinAppWidget(ComponentName(this,CalibrationWidget::class.java),null,null)
        else message("Long-press an empty home-screen space → Widgets → Home Alignment. Drag it onto the page, resize it, then tap it.")
    }
    private fun diagnostics() {
        page("Diagnostics & Debugging","Download reports, test A/B/C pages, and troubleshoot.")
        card {
            eyebrow("Debugging & calibration suite")
            text("Optional diagnostic tools",21f)
            text("Normal widget playback works automatically without any tests. Use this section whenever you want to run the A/B/C page test, adjust widget corners, inspect battery drain, or export a diagnostic report.",14f,MUTED)
        }
        section("Reports & telemetry")
        actionRow("Download diagnostic report","Export full widget, page, playback, and battery logs (.txt).","download") {saveReport()}
        actionRow("Battery & performance monitor","Live mAh counter, current (mA), temp (°C), CPU % & H.264 optimizer.","diagnostics") {go("Performance")}
        section("Multi-page A/B/C testing")
        val verdict=PageEvidence.assess(state.visits())
        val summary=when {
            state.prefs.getBoolean("guidedSurvey",false) -> "In progress · Tap to continue or finish A/B/C visits"
            state.visits().isEmpty() -> "Optional · Test how your launcher reports pages A, B, and C"
            verdict.code=="CANDIDATE" -> "Repeatable readings · Visual check available"
            else -> "${verdict.code} · ${state.visits().size} visits recorded (Tap to review or re-run)"
        }
        actionRow("Run A/B/C page test",summary,"pages") {launch(Intent(this,PageSurveyActivity::class.java))}
        actionRow("Swipe fallback (experimental)","Try gesture estimates when the launcher reports fixed page positions.","pages") {launch(Intent(this,SwipeFallbackActivity::class.java))}
        section("Troubleshoot a widget")
        if(state.ids().isEmpty())text("Add a widget from Home to see its position and alignment tools here.",14f,MUTED)
        state.ids().forEach {id->actionRow(WidgetAnimations.name(state,id),"2-corner position adjustment, 45s pink alignment box, and status","widgets") {
            GuidedSurvey.end(this)
            launch(Intent(this,VideoActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).putExtra("troubleshoot",true))
        }}
        section("Engineering console")
        actionRow("Advanced diagnostic tools","Raw engine telemetry, manual viewport offsets (dx/dy/scale), and test logs.","diagnostics") {launch(Intent(this,MainActivity::class.java).putExtra("tab","Align"))}
        text("Home Alignment Studio · ${BuildConfig.VERSION_NAME}",12f,MUTED)
    }
    private fun frameRate() {
        android.app.AlertDialog.Builder(this).setTitle("Frame rate preference")
            .setSingleChoiceItems(arrayOf("Up to 30 FPS · Battery saver (Recommended)","Up to 60 FPS · Maximum smoothness"),if(state.prefs.getBoolean("highFpsMode",false))1 else 0) {dialog,index->
                state.prefs.edit().putBoolean("highFpsMode",index==1).apply();dialog.dismiss();render()
            }.setNegativeButton("Close",null).show()
    }
    private fun performance() {
        page("Battery & performance","Hardware mAh, CPU, thermal & frame telemetry.",{go("Diagnostics")})
        if(OptimizationJob.busy) {
            card {
                pill("Optimizing videos",true)
                text(OptimizationJob.progress,16f)
                text("Converting heavy 2K/4K/AV1 videos into hardware-decoded 720p 30 FPS H.264 MP4s.",13f,MUTED)
            }
        }
        val sum=BatteryTelemetry.summary(this,false)
        val sess=sum.getJSONObject("session")
        val snap=sum.getJSONObject("currentSnapshot")
        val rates=sum.getJSONObject("computedRates")
        fun reading(v:Double,unit:String)=if(v.isFinite() && v>=0)String.format(java.util.Locale.US,"%.1f%s",v,unit) else "—"
        metrics(listOf(
            reading(snap.optDouble("levelPct",-1.0),"%") to "Battery level",
            reading(snap.optDouble("tempC",-1.0),"°C") to "Temperature",
            "${snap.optInt("activeWidgets",0)}" to "Active widgets"
        ))
        val heavy=state.ids().count {VideoOptimizer.needsOptimization(MediaStore.selected(this,it)) || VideoOptimizer.needsOptimization(state.obj(WidgetAnimations.key("playbackMedia",it)))}
        if(heavy>0 && !OptimizationJob.busy) {
            card {
                pill("$heavy heavy video(s) detected",true)
                text("Optimize for hardware H.264 decoding",19f)
                text("Converts 2K/4K or AV1/VP9 videos to 720p 30 FPS H.264 so Snapdragon's hardware decoder handles playback at ~80% lower CPU & battery.",13f,MUTED)
                primary("Optimize existing videos ($heavy)") {
                    if(OptimizationJob.start(this)) {render();handler.post(optimizationTick)} else message("Wait for the current media operation to finish.")
                }
            }
        }
        if(!OptimizationJob.busy && OptimizationJob.result.isNotEmpty())notice(OptimizationJob.result)
        card {
            eyebrow("Session & drain summary")
            text("${rates.optDouble("durationMinutes")} min observed",20f)
            val details=buildString {
                append("Started: ${sess.optString("startedAtIso","-")}\n")
                append("Home visible: ${rates.optDouble("visibleMinutes")} min · Hidden/App off: ${rates.optDouble("hiddenMinutes")} min\n\n")
                append("Battery: ${sess.optDouble("startLevelPct")}% → ${snap.optDouble("levelPct")}% (Δ ${rates.optDouble("batteryPctDelta")}%)\n")
                append("Charge counter: ${snap.optDouble("chargeMah")} mAh (Drained: ${rates.optDouble("batteryMahDelta")} mAh)\n")
                append("Est. drain rate: ${rates.optDouble("estPctPerHour")} %/hr · ${rates.optDouble("estMahPerHour")} mAh/hr\n\n")
                append("Live current: ${snap.optDouble("currentMa")} mA @ ${snap.optInt("voltageMv")} mV (~${snap.optDouble("estPowerMw")} mW)\n")
                append("Temp: ${snap.optDouble("tempC")} °C (min ${sess.optDouble("minTempC")} °C · peak ${sess.optDouble("peakTempC")} °C) · Thermal: ${snap.optString("thermalStatus")}\n")
                append("Power state: ${snap.optString("status")} (${snap.optString("plugged")}) · Power Saver: ${snap.optBoolean("powerSave")}\n\n")
                append("App CPU (Home visible): ${rates.optDouble("visibleCpuPctOfOneCore")}% of 1 core\n")
                append("App CPU (Hidden / in other apps): ${rates.optDouble("hiddenCpuPctOfOneCore")}% of 1 core\n")
                append("Memory: Java ${snap.optDouble("javaHeapMb")} MB · Native ${snap.optDouble("nativeHeapMb")} MB\n")
                append("Rendered frames: ${snap.optLong("totalRenderedFrames")} · Timeline samples: ${sum.optInt("sampleCount")}")
            }
            text(details,13f,MUTED)
        }
        actionRow("Frame rate limit",if(state.prefs.getBoolean("highFpsMode",false))"60 FPS (Maximum smoothness)" else "30 FPS (Battery saver · Recommended)","play") {frameRate()}
        primary("Download battery & diagnostic report") {saveReport()}
        button("Refresh live readings") {BatteryTelemetry.recordSample(this,"MANUAL_REFRESH",force=false);render()}
        button("Reset session counters (Start new test)") {
            android.app.AlertDialog.Builder(this).setTitle("Reset session counters?").setMessage("Save a report first if you want to keep the current measurements.")
                .setNegativeButton("Keep session",null).setPositiveButton("Reset") {_,_->BatteryTelemetry.resetSession(this,"user_button");render()}.show()
        }
        link("View raw JSON telemetry") {details("Performance readings",sum.toString(2))}
    }
    @Deprecated("Legacy back support") override fun onBackPressed(){if(screen=="Home")super.onBackPressed() else go(if(screen=="Performance")"Diagnostics" else "Home")}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==BackgroundImport.READ_PERMISSION || requestCode==BackgroundImport.READ_IMAGES_PERMISSION) {
            if(grantResults.firstOrNull()==android.content.pm.PackageManager.PERMISSION_GRANTED)BackgroundImport.current(this)
            else message("Current wallpaper access was not granted. Choose a photo instead, or try again.")
        }
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==BackgroundImport.PICK && resultCode==RESULT_OK && data?.data!=null)BackgroundImport.selected(this,data.data!!)
    }
}

fun renderObservation(a: LabActivity, after: () -> Unit) {
    a.page("Did the pink box appear?", "Alignment check · Tell us what you saw")
    a.boxExample()
    a.text("Look for the pink box with cyan edges behind the yellow widget outline. All four edges should line up. Choose the answer that matches what you actually saw.")
    a.primary("Yes — it lined up") { AlignmentFlow.feedback(a,"aligned"); after() }
    a.button("I saw it, but it was misplaced") { AlignmentFlow.feedback(a,"offset"); after() }
    a.button("No pink box appeared") { AlignmentFlow.feedback(a,"missing"); after() }
    a.button("I haven't checked yet — show it again") {
        val id=State(a).prefs.getInt("selected",-1); AlignmentFlow.start(a,id)
    }
    a.text("The box stays for 45 seconds. A successful draw does not automatically mean it lined up; your answer is the check.",13f)
}
