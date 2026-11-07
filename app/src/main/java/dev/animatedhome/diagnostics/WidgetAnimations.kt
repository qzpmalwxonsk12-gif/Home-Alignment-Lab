package dev.animatedhome.diagnostics

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.util.UUID

object WidgetAnimations {
    const val LIMIT=Int.MAX_VALUE
    val mediaKeys=listOf("selectedMedia","pendingMedia","playbackMedia","mediaImportFailed","mediaImportMessage","mediaError","enabled","adjust","adjustBackup","videoTelemetry","scaleMode","swipeMode","previousSwipeMode","ignorePageOffset","streamMuted","widgetName","fallbackPage")
    fun name(s:State,id:Int)=s.prefs.getString(key("widgetName",id),"Widget $id") ?: "Widget $id"
    fun current(context:Context)=State(context).prefs.getInt("selected",-1)
    fun key(base:String,id:Int)="${base}_$id"

    fun youtubeWidget(s:State):Int=s.prefs.getInt("youtubeWidgetId",-1).takeIf {s.ids().contains(it)} ?: -1
    fun chromeWidget(s:State):Int=s.prefs.getInt("chromeWidgetId",-1).takeIf {s.ids().contains(it)} ?: -1
    fun role(s:State,id:Int):String=when(id) {
        youtubeWidget(s) -> "YOUTUBE"
        chromeWidget(s) -> "CHROME"
        else -> "STANDARD"
    }
    fun roleLabel(role:String):String=when(role) {
        "YOUTUBE" -> "Designated YouTube Widget"
        "CHROME" -> "Designated Chrome Widget"
        else -> "Standard Widget (GIF / Video)"
    }
    fun setRole(context:Context,id:Int,newRole:String) {
        val s=State(context)
        val e=s.prefs.edit()
        val prevYt=s.prefs.getInt("youtubeWidgetId",-1)
        val prevCh=s.prefs.getInt("chromeWidgetId",-1)
        when(newRole) {
            "YOUTUBE" -> {
                e.putInt("youtubeWidgetId",id)
                if(prevCh==id) e.remove("chromeWidgetId")
            }
            "CHROME" -> {
                e.putInt("chromeWidgetId",id)
                if(prevYt==id) e.remove("youtubeWidgetId")
            }
            else -> {
                if(prevYt==id) e.remove("youtubeWidgetId")
                if(prevCh==id) e.remove("chromeWidgetId")
            }
        }
        e.apply()
        s.ids().forEach {CalibrationWidget.update(context,it)}
    }

    fun ignorePageOffset(s:State,id:Int):Boolean=s.prefs.getBoolean(key("ignorePageOffset",id),false) || swipeMode(s,id)=="ALWAYS"
    fun setIgnorePageOffset(context:Context,id:Int,ignore:Boolean) {
        val s=State(context)
        val e=s.prefs.edit()
        if(ignore) {
            val cur=swipeMode(s,id)
            if(cur!="ALWAYS") e.putString(key("previousSwipeMode",id),cur)
            e.putBoolean(key("ignorePageOffset",id),true).putString(key("swipeMode",id),"ALWAYS")
        } else {
            val prev=s.prefs.getString(key("previousSwipeMode",id),"SLIDE") ?: "SLIDE"
            val restored=if(prev=="ALWAYS") "SLIDE" else prev
            e.putBoolean(key("ignorePageOffset",id),false).putString(key("swipeMode",id),restored)
        }
        e.apply()
        CalibrationWidget.update(context,id)
    }

    fun streamMuted(s:State,id:Int):Boolean=s.prefs.getBoolean(key("streamMuted",id),true)
    fun setStreamMuted(context:Context,id:Int,muted:Boolean) {
        val s=State(context)
        val e=s.prefs.edit().putBoolean(key("streamMuted",id),muted)
        for(base in listOf("selectedMedia","playbackMedia")) {
            val obj=s.obj(key(base,id))
            if(MediaStore.isStream(obj)) {
                val updated=StreamUrlHelper.buildStreamSpec(
                    kind=obj.optString("kind","chrome"),
                    url=obj.optString("url",""),
                    title=obj.optString("name",""),
                    isolateVideo=obj.optBoolean("isolateVideo",true),
                    muted=muted,
                    directVideoUrl=obj.optString("directVideoUrl","")
                )
                e.putString(key(base,id),updated.toString())
            }
        }
        e.apply()
    }

    fun scaleMode(s:State,id:Int)=s.prefs.getString(key("scaleMode",id),"FIT") ?: "FIT"
    fun scaleModeLabel(mode:String)=when(mode) {
        "FILL" -> "Fill widget (center crop)"
        "FIT_BLACK" -> "Fit inside (black bars)"
        else -> "Fit inside (wallpaper edges)"
    }
    fun cycleScaleMode(context:Context,id:Int):String {
        val s=State(context)
        val next=when(scaleMode(s,id)) {
            "FIT" -> "FILL"
            "FILL" -> "FIT_BLACK"
            else -> "FIT"
        }
        s.prefs.edit().putString(key("scaleMode",id),next).apply()
        return next
    }
    fun swipeMode(s:State,id:Int):String {
        if(s.prefs.getBoolean(key("ignorePageOffset",id),false)) return "ALWAYS"
        return s.prefs.getString(key("swipeMode",id),"SLIDE") ?: "SLIDE"
    }
    fun swipeModeLabel(mode:String)=when(mode) {
        "INSTANT" -> "Show when page matches"
        "ALWAYS" -> "Always visible on all pages (No page offset)"
        "SETTLED" -> "Wait 150ms after swipe"
        else -> "Slide with home page"
    }
    fun cycleSwipeMode(context:Context,id:Int):String {
        val s=State(context)
        val next=when(swipeMode(s,id)) {
            "SLIDE" -> "INSTANT"
            "INSTANT" -> "ALWAYS"
            "ALWAYS" -> "SETTLED"
            else -> "SLIDE"
        }
        s.prefs.edit().putBoolean(key("ignorePageOffset",id),next=="ALWAYS").putString(key("swipeMode",id),next).apply()
        return next
    }
    fun migrate(context:Context) {
        val s=State(context)
        if(s.prefs.getBoolean("multipleWidgets",false))return
        val id=s.prefs.getInt("surveyWidgetA",s.prefs.getInt("selected",-1))
        val e=s.prefs.edit()
        if(s.ids().contains(id)) {
            for(k in listOf("selectedMedia","pendingMedia","playbackMedia")) if(s.obj(k).length()>0)e.putString(key(k,id),s.obj(k).toString())
            e.putBoolean(key("enabled",id),s.prefs.getBoolean("personalPlayback",false) && s.prefs.getString("mode","")=="VIDEO")
        }
        e.putBoolean("multipleWidgets",true).putBoolean("personalPlayback",false).putString("mode","OFF")
            .putBoolean("guidedSurvey",false).putBoolean("isolationPending",false).putBoolean("videoObservationPending",false).commit()
    }
    fun open(a:LabActivity,id:Int) {a.launch(Intent(a,VideoActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id));a.finish()}
    fun enabled(s:State,id:Int)=s.prefs.getBoolean(key("enabled",id),false)
    fun stop(context:Context,id:Int) {
        State(context).prefs.edit().putBoolean(key("enabled",id),false).apply();CalibrationWidget.update(context,id)
    }
    fun ensureLocated(context:Context,id:Int):Boolean {
        val s=State(context)
        val c=s.record(id)
        val d=device(context)
        val sw=d.getInt("width")
        val sh=d.getInt("height")
        val env=environment(context)
        val fallbackOff=(Live.offset ?: c.optJSONObject("offset")?.offset() ?: Offset(0.5f,0.5f,0f,0f,0,0)).json()
        val existingBox=c.box()?.takeIf {it.valid(sw,sh)} ?: c.box("sourceBounds")?.takeIf {it.valid(sw,sh)}
        if(existingBox!=null) {
            c.put("widgetId",id).put("bounds",existingBox.json()).put("valid",true)
                .put("environment",env).put("offset",c.optJSONObject("offset") ?: fallbackOff)
                .put("zoom",if(Live.zoom.isFinite()) Live.zoom else 0f).put("stable",true)
                .put("measurementPending",false)
            if(!c.has("measurementVersion") || c.optInt("measurementVersion")==0) c.put("measurementVersion",WidgetMeasurement.VERSION)
            s.put("widget_$id",c)
            return true
        }
        val density=d.optDouble("density",3.0).toFloat().coerceAtLeast(1f)
        val opts=runCatching {AppWidgetManager.getInstance(context).getAppWidgetOptions(id)}.getOrNull()
        val wDp=(opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,0)?.takeIf {it>0}
            ?: opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,0)?.takeIf {it>0} ?: 220)
        val hDp=(opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,0)?.takeIf {it>0}
            ?: opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,0)?.takeIf {it>0} ?: 220)
        val bw=(wDp*density).coerceIn(120f,(sw*0.9f).coerceAtLeast(160f))
        val bh=(hDp*density).coerceIn(120f,(sh*0.6f).coerceAtLeast(160f))
        val left=((sw-bw)/2f).coerceAtLeast(16f)
        val top=((sh-bh)*0.32f).coerceAtLeast(48f)
        val est=Box(left,top,(left+bw).coerceAtMost(sw-16f),(top+bh).coerceAtMost(sh-16f))
        if(!est.valid(sw,sh))return false
        c.put("widgetId",id).put("bounds",est.json()).put("sourceBounds",est.json()).put("valid",true)
            .put("environment",env).put("offset",fallbackOff).put("zoom",if(Live.zoom.isFinite()) Live.zoom else 0f)
            .put("stable",true).put("tap",UUID.randomUUID().toString()).put("capturedAt",System.currentTimeMillis())
            .put("measurementPending",false).put("measurementVersion",WidgetMeasurement.VERSION)
            .put("placement","estimated")
        s.put("widget_$id",c)
        return true
    }
    fun startStream(a:LabActivity,id:Int,streamSpec:JSONObject,goHome:Boolean=true):Boolean {
        val s=State(a)
        val streamKind=streamSpec.optString("kind","chrome")
        val targetId=when(streamKind) {
            "youtube" -> {
                val yt=youtubeWidget(s)
                if(yt!=-1) yt else { setRole(a,id,"YOUTUBE"); id }
            }
            "chrome" -> {
                val ch=chromeWidget(s)
                if(ch!=-1) ch else { setRole(a,id,"CHROME"); id }
            }
            else -> id
        }
        if(!s.ids().contains(targetId)){a.message("Add a widget to your home screen first.");return false}
        if(!AlignmentFlow.wallpaperActive(a)){a.message("Apply the Home Alignment wallpaper first, then share or stream to your widget.");return false}
        ensureLocated(a,targetId)
        s.prefs.edit()
            .putString(key("selectedMedia",targetId),streamSpec.toString())
            .putString(key("playbackMedia",targetId),streamSpec.toString())
            .remove(key("pendingMedia",targetId))
            .putBoolean(key("mediaImportFailed",targetId),false)
            .putString(key("mediaImportMessage",targetId),"")
            .putString(key("mediaError",targetId),"")
            .putBoolean(key("enabled",targetId),true)
            .putBoolean(key("adjust",targetId),false)
            .putString("mode","OFF")
            .apply()
        CalibrationWidget.update(a,targetId)
        DiagnosticLog.event(a,"WIDGET_STREAM_START","widget=$targetId kind=$streamKind url=${streamSpec.optString("url")}")
        if(goHome) a.home()
        return true
    }
    fun start(a:LabActivity,id:Int):Boolean {
        val s=State(a)
        if(!s.ids().contains(id)){a.message("This widget was removed. Add a widget from My widgets.");return false}
        if(!AlignmentFlow.wallpaperActive(a)){a.message("Apply the wallpaper from My widgets first, then tap this widget on your home screen.");return false}
        if(!MediaStore.canPlay(a,id)){a.message("Choose and confirm this widget's animation or stream first.");return false}
        ensureLocated(a,id)
        val c=s.record(id);val d=device(a)
        if(!c.optBoolean("valid") || c.box()?.valid(d.getInt("width"),d.getInt("height"))!=true) {
            a.message("Go home and tap this widget once to save its location. If your launcher cannot provide it, open Troubleshoot this widget → Adjust position.");return false
        }
        s.prefs.edit().putString(key("playbackMedia",id),MediaStore.selected(a,id).toString()).putBoolean(key("enabled",id),true)
            .putString(key("mediaError",id),"").putBoolean(key("adjust",id),false)
            .putString("mode","OFF").apply()
        CalibrationWidget.update(a,id);a.home();return true
    }
    fun syncAll(a:LabActivity) {
        val s=State(a)
        if(!AlignmentFlow.wallpaperActive(a)){a.message("Apply the wallpaper first, then sync your widgets.");return}
        val readyIds=s.ids().filter {MediaStore.isPlayableSource(a,MediaStore.selected(a,it))}
        if(readyIds.isEmpty()){a.message("Choose a GIF, video, or stream for at least one widget first.");return}
        val edit=s.prefs.edit()
        var startedCount=0
        for(id in readyIds) {
            ensureLocated(a,id)
            edit.putString(key("playbackMedia",id),MediaStore.selected(a,id).toString())
                .putBoolean(key("enabled",id),true)
                .putString(key("mediaError",id),"")
                .putBoolean(key("adjust",id),false)
            startedCount++
        }
        edit.putLong("syncGeneration",System.currentTimeMillis()).putString("mode","OFF").apply()
        readyIds.forEach {CalibrationWidget.update(a,it)}
        DiagnosticLog.event(a,"WIDGETS_SYNC_ALL","count=$startedCount ids=${readyIds.joinToString()}")
        android.widget.Toast.makeText(a,"Resetting $startedCount animation(s) to 0:00…",android.widget.Toast.LENGTH_SHORT).show()
        a.home()
    }
    fun locate(a:LabActivity,id:Int) {
        val s=State(a);val old=s.record(id);val d=device(a);val env=environment(a)
        if(s.prefs.getInt("pinkWidget",-1)==id) {
            s.prefs.edit().putLong("pinkUntil",0).apply()
        }
        val raw=a.intent.sourceBounds?.let {Box(it.left.toFloat(),it.top.toFloat(),it.right.toFloat(),it.bottom.toFloat())}
        // A widget tap is a user anchor only for the separately enabled gesture fallback.
        if(raw!=null && Live.engine.isNotEmpty() && (Live.visible || android.os.SystemClock.elapsedRealtime()-Live.hiddenAt in 0..3000))SwipeFallback.anchor(a,id)
        val fallbackOff=Live.offset ?: old.optJSONObject("offset")?.offset() ?: Offset(0.5f,0.5f,0f,0f,0,0)
        // Always capture a valid widget tap immediately without requiring a 150ms settle wait or non-null Live.offset.
        if(raw!=null) {
            val measured=(listOf(old)+s.ids().filter {it!=id}.map {s.record(it)}).firstOrNull {
                it.optInt("measurementVersion")==WidgetMeasurement.VERSION && it.has("measuredScaleX") &&
                    VideoPolicy.sameProfile(it.optString("environment"),env)
            }
            val box=WidgetPlacement.automatic(raw,d.getInt("width"),d.getInt("height"),
                measured?.optDouble("measuredScaleX",1.0)?.toFloat() ?: 1f,measured?.optDouble("measuredScaleY",1.0)?.toFloat() ?: 1f)
            if(box!=null) {
                val exact=old.optInt("measurementVersion")==WidgetMeasurement.VERSION && WidgetMeasurement.same(raw,old.box("sourceBounds"),WidgetMeasurement.TAP_JITTER_TOLERANCE) &&
                    VideoPolicy.sameProfile(old.optString("environment"),env) && old.box()?.valid(d.getInt("width"),d.getInt("height"))==true
                val stableRaw=if(exact) (old.box("sourceBounds") ?: raw) else raw
                old.put("widgetId",id).put("bounds",(if(exact)old.box()!! else box).json()).put("sourceBounds",stableRaw.json()).put("valid",true)
                    .put("environment",env).put("offset",fallbackOff.json()).put("zoom",Live.zoom).put("stable",true)
                    .put("tap",UUID.randomUUID().toString()).put("capturedAt",System.currentTimeMillis()).put("measurementPending",false)
                    .put("measurementVersion",WidgetMeasurement.VERSION)
                    .put("placement",if(exact)"measured" else if(measured!=null)"saved launcher scale" else "automatic")
                s.put("widget_$id",old)
            } else if(!old.optBoolean("valid") || !VideoPolicy.sameProfile(old.optString("environment"),env)) {
                old.put("valid",false).put("invalidReason","Your launcher did not supply a usable position. Tap again outside edit mode, or use Adjust position in Troubleshoot this widget.")
                s.put("widget_$id",old)
            }
        }
        s.prefs.edit().putInt("selected",id).apply()
        CalibrationWidget.update(a,id)
        DiagnosticLog.event(a,"WIDGET_CONTROL_TAP","widget=$id placement=${s.record(id).optString("placement")} bounds=${s.record(id).box()}")
    }
    fun troubleshoot(a:LabActivity,id:Int) {
        val s=State(a);stop(a,id)
        s.put(key("adjustBackup",id),s.record(id))
        s.put("widget_$id",s.record(id).put("valid",false).put("measurementPending",false))
        s.prefs.edit().putBoolean(key("adjust",id),true).putInt("selected",id).putString("mode","OFF").apply()
        CalibrationWidget.update(a,id);a.home()
    }
}
