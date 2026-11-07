package dev.animatedhome.diagnostics

import android.app.Instrumentation
import android.content.SharedPreferences
import org.json.JSONObject

/** Synthetic state fixtures on a disposable emulator. Restores preferences; never a launcher pass. */
object PageFallbackRegression {
    fun run(i:Instrumentation):String {
        var failure:Throwable?=null
        i.runOnMainSync {
            val context=i.targetContext;val s=State(context);val before=s.prefs.all.toMap()
            val rawBefore=Live.offset;val nativeBefore=Live.offsetsChangedAcrossPages
            try {
                check(s.ids().size>=3){"Needs three disposable emulator widgets"}
                val ids=s.ids().take(3)
                val widgetBefore=ids.associateWith {s.prefs.getString("widget_$it",null)}
                val mediaBefore=ids.associateWith {MediaStore.selected(context,it).toString()}
                s.prefs.edit().putString("visits","[{\"page\":\"A\",\"round\":1,\"tap\":\"legacy-quick-tap\",\"environment\":\"old\",\"stable\":true}]")
                    .remove("honestSurveyV2").putString("isolationObservation",VideoPolicy.GOOD_ISOLATION).commit()
                GuidedSurvey.migrate(context)
                check(s.visits().isEmpty() && s.prefs.getString("prePatchPageVisits","")!!.contains("legacy-quick-tap"))
                check(!s.prefs.contains("isolationObservation"))
                for(id in ids){check(s.prefs.getString("widget_$id",null)==widgetBefore[id]);check(MediaStore.selected(context,id).toString()==mediaBefore[id])}
                GuidedSurvey.begin(context)
                fun pending(id:Int,offset:Offset?,tap:String,stable:Boolean=true,env:String=environment(context)) {
                    s.put("surveyPendingCapture",JSONObject().put("widgetId",id).put("tap",tap).put("capturedAt",System.currentTimeMillis())
                        .put("recentHome",true).put("stable",stable).put("observedLive",true).put("evidenceSource","launcher")
                        .put("environment",env).put("offset",offset?.json()).put("valid",false))
                }
                val fixed=Offset(0f,.5f,-1f,-1f,0,0)
                pending(ids[0],null,"missing");check(GuidedSurvey.save(context)!=null);check(s.visits().isEmpty())
                pending(ids[0],fixed,"moving",false);check(GuidedSurvey.save(context)!=null)
                pending(ids[0],fixed,"old-engine",env="old-engine");check(GuidedSurvey.save(context)!=null)
                for((n,visit) in SurveyPolicy.order.withIndex()) {
                    val index=visit.first[0]-'A'
                    pending(ids[index],fixed,"independent-fixture-tap-$n")
                    check(GuidedSurvey.save(context)==null)
                    check(s.visits().size==n+1){"A single save duplicated a round"}
                    if(n==0) {
                        pending(ids[0],fixed,"independent-fixture-tap-0");check(GuidedSurvey.save(context)!=null)
                        pending(ids[0],fixed,"wrong-widget-page");check(GuidedSurvey.save(context)!=null)
                    }
                }
                check(PageEvidence.assess(s.visits()).code=="UNSUPPORTED")
                check(s.visits().map {it.tap}.distinct().size==6)
                GuidedSurvey.end(context)
                check(!s.prefs.getBoolean("isolationPending",false) && !s.prefs.contains("isolationObservation"))
                Live.offset=fixed;Live.offsetsChangedAcrossPages=false
                s.prefs.edit().putInt(WidgetAnimations.key("fallbackPage",ids[0]),1).commit()
                SwipeFallback.setEnabled(context,true)
                check(SwipeFallback.anchor(context,ids[0]))
                Live.swipeTracker.begin(800f,500f,1000,1000f,10f)
                Live.swipeTracker.finish(100f,500f,1500)
                check(Live.swipeTracker.page==2 && Live.offset==fixed)
                check(PageEvidence.assess(s.visits()).code=="UNSUPPORTED")
                Live.offset=Offset(.5f,.5f,.5f,0f,-500,0);check(!SwipeFallback.active(context))
                Live.offset=fixed;Live.offsetsChangedAcrossPages=true;check(!SwipeFallback.active(context))
                check(s.report().contains("ESTIMATED, NOT TEST EVIDENCE"))
            } catch(t:Throwable){failure=t}
            finally {
                val edit=s.prefs.edit().clear()
                for((key,value) in before)put(edit,key,value)
                edit.commit();Live.offset=rawBefore;Live.offsetsChangedAcrossPages=nativeBefore;Live.swipeTracker.reset()
                s.ids().forEach {CalibrationWidget.update(context,it)}
            }
        }
        failure?.let {throw it}
        return "PASS: legacy readings archived without widget/media changes; missing/moving/stale/reused/wrong-widget captures rejected; one save per tap; six fixed-offset visits remain UNSUPPORTED; skip never passes; swipe estimates never alter raw readings; native offsets take priority. Synthetic state fixtures, not a Xiaomi device result."
    }
    private fun put(e:SharedPreferences.Editor,k:String,v:Any?) {
        when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v);is Long->e.putLong(k,v);is Float->e.putFloat(k,v);is Set<*>->e.putStringSet(k,v.filterIsInstance<String>().toSet())}
    }
}
