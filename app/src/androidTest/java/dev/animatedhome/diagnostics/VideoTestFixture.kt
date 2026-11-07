package dev.animatedhome.diagnostics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Synthetic gate fixture for the disposable emulator, NOT physical page evidence. */
object VideoTestFixture {
    fun prepare(context:Context):String {
        val s=State(context); val id=s.ids().firstOrNull() ?: error("Add a widget first")
        val c=s.record(id); val a=c.optJSONObject("offset")?.offset() ?: error("Capture widget position first")
        val visits=JSONArray()
        val direction=if(a.x<.7f) 1 else -1
        for((page,round) in GuidedSurvey.order) {
            val index=page[0]-'A'
            val offset=a.copy(x=a.x+direction*.1f*index,px=a.px-direction*138*index)
            visits.put(JSONObject().put("page",page).put("round",round).put("tap",UUID.randomUUID().toString())
                .put("environment",c.getString("environment")).put("offset",offset.json()).put("stable",true))
        }
        s.prefs.edit().putString("visits",visits.toString()).putInt("surveyWidgetA",id)
            .putString("alignmentAnswer","aligned").putString("isolationObservation",VideoPolicy.GOOD_ISOLATION)
            .putBoolean("surveyCompleted",true).putBoolean("guidedSurvey",false).putBoolean("isolationPending",false)
            .putBoolean("videoObservationPending",false).putBoolean("videoSetupPending",false)
            .remove("videoAttempt").remove("videoTelemetry").putString("mode","OFF")
            .putString("results",JSONObject().put("testFixture","SYNTHETIC emulator page prerequisites; not a physical page or alignment pass").toString()).commit()
        DiagnosticLog.event(context,"SYNTHETIC_VIDEO_FIXTURE","widget=$id; source offset=$a; six generated visits only unlock renderer testing")
        return "Prepared synthetic prerequisites for video renderer test; widget=$id. Fresh real corner taps are still required after restart."
    }
}
