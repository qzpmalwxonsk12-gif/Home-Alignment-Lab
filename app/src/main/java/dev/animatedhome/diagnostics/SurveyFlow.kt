package dev.animatedhome.diagnostics

import android.app.KeyguardManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object SurveyPolicy {
    val order=listOf("A" to 1,"B" to 1,"C" to 1,"C" to 2,"B" to 2,"A" to 2)
    fun next(visits:List<Visit>)=order.firstOrNull { (p,r)->visits.none {it.page==p && it.round==r} }
    fun synthetic(tap:String)=tap.startsWith("skip-") || tap.endsWith("-r1") || tap.endsWith("-r2")
    fun canSave(recent:Boolean,stable:Boolean,offset:Offset?,sameEnvironment:Boolean,ageMillis:Long)=
        recent && stable && offset?.valid()==true && sameEnvironment && ageMillis in 0..300_000
}

/** Diagnostic evidence is independent of normal playback and never required for widgets to play. */
object GuidedSurvey {
    val order=SurveyPolicy.order
    fun next(visits:List<Visit>)=SurveyPolicy.next(visits)
    fun migrate(context:Context) {
        val s=State(context)
        if(s.prefs.getBoolean("honestSurveyV2",false))return
        val previous=s.prefs.getString("visits","[]") ?: "[]"
        val hasOld=s.visits().isNotEmpty()
        val edit=s.prefs.edit().putBoolean("honestSurveyV1",true).putBoolean("honestSurveyV2",true).remove("surveySkipped")
        if(hasOld) {
            edit.putString("prePatchPageVisits",previous).putString("visits","[]")
                .putBoolean("surveyCompleted",false).putBoolean("guidedSurvey",false).putBoolean("isolationPending",false)
                .remove("surveyPendingCapture").remove("surveyAnchor").remove("isolationObservation").putString("mode","OFF")
                .putString("surveyMigrationNote","Previous page readings were archived because older versions could copy rounds or estimate offsets. Widget files and positions are kept. A fresh test records six independent launcher readings.")
        }
        edit.apply()
    }
    fun anchorUsable(context:Context):Boolean {
        val s=State(context);val a=s.obj("surveyAnchor");val d=device(context)
        return a.optString("environment")==environment(context) && a.optBoolean("valid") &&
            a.box()?.valid(d.getInt("width"),d.getInt("height"))==true && s.ids().contains(a.optInt("widgetId",-1))
    }
    fun end(context:Context) {
        val s=State(context)
        s.prefs.edit().putBoolean("guidedSurvey",false).putBoolean("isolationPending",false)
            .remove("surveyPendingCapture").putString("mode","OFF").apply()
        s.ids().forEach {CalibrationWidget.update(context,it)}
    }
    fun skip(a:LabActivity) {end(a);a.openSetup()}
    fun begin(context:Context) {
        val s=State(context)
        s.prefs.edit().putString("visits","[]").putBoolean("guidedSurvey",true).putBoolean("surveyCompleted",false)
            .remove("isolationObservation").remove("surveyPendingCapture").remove("surveyAnchor")
            .putBoolean("isolationPending",false).putString("mode","OFF").apply()
        s.ids().forEach {CalibrationWidget.update(context,it)}
    }
    fun capture(a:LabActivity):Boolean {
        val s=State(a)
        if(!s.prefs.getBoolean("guidedSurvey",false) && !s.prefs.getBoolean("isolationPending",false))return false
        val id=a.intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1)
        if(!s.ids().contains(id))return false
        if(s.prefs.getBoolean("guidedSurvey",false)) {
            val now=SystemClock.elapsedRealtime()
            val recent=Live.engine.isNotEmpty() && (Live.visible || now-Live.hiddenAt in 0..3000) &&
                !a.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            val d=device(a);val old=s.record(id);val raw=a.intent.sourceBounds?.let {Box(it.left.toFloat(),it.top.toFloat(),it.right.toFloat(),it.bottom.toFloat())}
            val sameProfile=VideoPolicy.sameProfile(old.optString("environment"),environment(a))
            val b=if(sameProfile && WidgetMeasurement.same(raw,old.box("sourceBounds"),WidgetMeasurement.TAP_JITTER_TOLERANCE))old.box() else
                WidgetPlacement.automatic(raw,d.getInt("width"),d.getInt("height"),
                    if(sameProfile)old.optDouble("measuredScaleX",1.0).toFloat() else 1f,
                    if(sameProfile)old.optDouble("measuredScaleY",1.0).toFloat() else 1f)
            val off=Live.offset
            s.put("surveyPendingCapture",JSONObject().put("widgetId",id).put("tap",UUID.randomUUID().toString())
                .put("capturedAt",System.currentTimeMillis()).put("environment",environment(a)).put("observedLive",recent).put("evidenceSource","launcher")
                .put("recentHome",recent).put("stable",recent && now-Live.stableSince>=500)
                .put("offset",if(recent)off?.json() else null).put("zoom",Live.zoom)
                .put("bounds",b?.json()).put("valid",b?.valid(d.getInt("width"),d.getInt("height"))==true)
                .put("measurementVersion",WidgetMeasurement.VERSION))
        }
        a.launch(Intent(a,PageSurveyActivity::class.java));a.finish();return true
    }
    fun save(context:Context):String? {
        val s=State(context);val next=next(s.visits()) ?: return "All six visits are already saved."
        val c=s.obj("surveyPendingCapture")
        if(!SurveyPolicy.canSave(c.optBoolean("recentHome"),c.optBoolean("stable"),c.optJSONObject("offset")?.offset(),
            c.optString("environment")==environment(context),System.currentTimeMillis()-c.optLong("capturedAt")) || c.optString("evidenceSource")!="launcher")
            return "That tap did not include a stable live launcher reading. Go home, wait two seconds on the requested page, then tap its widget again."
        if(s.visits().any {it.tap==c.optString("tap")})return "That tap was already saved. Make a new home-screen visit."
        val raw=JSONArray(s.prefs.getString("visits","[]"))
        val rows=(0 until raw.length()).map {raw.getJSONObject(it)}
        val previous=rows.firstOrNull {it.optString("page")==next.first}
        if(previous!=null && previous.optInt("widgetId",-1)!=c.optInt("widgetId"))return "Use the same widget you used on page ${next.first} during the first round."
        if(rows.any {it.optString("page")!=next.first && it.optInt("widgetId",-1)==c.optInt("widgetId")})return "Use a different stationary widget on each page. This widget already belongs to another page in this test."
        s.addVisit(c,next.first,next.second)
        if(next.first=="A") {
            s.put("surveyAnchor",c);s.prefs.edit().putInt("surveyWidgetA",c.optInt("widgetId")).apply()
        }
        s.prefs.edit().remove("surveyPendingCapture").apply()
        if(SurveyPolicy.next(s.visits())==null) {
            s.prefs.edit().putBoolean("guidedSurvey",false).putBoolean("surveyCompleted",true).apply()
            s.ids().forEach {CalibrationWidget.update(context,it)}
        }
        return null
    }
}
