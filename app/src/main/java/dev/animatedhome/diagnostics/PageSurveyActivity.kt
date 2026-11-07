package dev.animatedhome.diagnostics

import android.os.Bundle
import android.os.SystemClock

class PageSurveyActivity:LabActivity() {
    private val state by lazy {State(this)}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);GuidedSurvey.migrate(this)}
    override fun onResume(){super.onResume();render()}
    private fun render() {
        page("A/B/C page test", "Optional launcher check · Your animations stay configured", {leave()})
        if(!AlignmentFlow.wallpaperActive(this)) {
            notice("Apply the Home Alignment wallpaper before running this check.")
            primary("Open wallpaper setup") {launch(android.content.Intent(this,SetupActivity::class.java).putExtra("setup",true))}
            return
        }
        if(state.prefs.getBoolean("isolationPending",false)) {
            card {
                eyebrow("Final visual check")
                text("Did the box stay on page A?",22f)
                text("Swipe slowly and quickly between A, B and C. The pink box should appear only behind A's widget, with no flashes on B or C. The box is shown for two minutes.")
                primary("Yes, only on page A") {answer(VideoPolicy.GOOD_ISOLATION)}
                button("No, it appeared on another page") {answer("FAIL: user observed leakage onto another page")}
                button("The box did not appear") {answer("INCONCLUSIVE: user reported no test box")}
                button("Go home and check again") {enableIsolation()}
            }
            button("Skip visual check · Return to Home") {GuidedSurvey.skip(this)}
            link("Back to Diagnostics") {leave()};return
        }
        if(state.prefs.getBoolean("guidedSurvey",false)) {
            val visits=state.visits();val next=GuidedSurvey.next(visits)
            if(next==null){state.prefs.edit().putBoolean("guidedSurvey",false).putBoolean("surveyCompleted",true).apply();render();return}
            val step=GuidedSurvey.order.indexOf(next)+1
            pill("Visit $step of 6")
            card {
                eyebrow("A → B → C → C → B → A")
                text("Page ${next.first} · visit ${next.second}",24f)
                text("Swipe to home-screen page ${next.first}, wait two seconds, then tap its widget once. Keep each widget on the same page throughout the test.")
                val pending=state.obj("surveyPendingCapture")
                if(pending.length()>0) {
                    val off=pending.optJSONObject("offset")?.offset()
                    notice("Tap received from widget #${pending.optInt("widgetId")}${if(off!=null) " (xOffset=${String.format(java.util.Locale.US,"%.3f",off.x)}, xStep=${String.format(java.util.Locale.US,"%.3f",off.xStep)})" else ""}.")
                    primary("Save page ${next.first} · visit ${next.second}") {
                        val error=GuidedSurvey.save(this)
                        if(error!=null)message(error) else render()
                    }
                    button("Wrong page — tap again") {state.prefs.edit().remove("surveyPendingCapture").apply();home()}
                } else primary("Go home to page ${next.first}") {home()}
            }
            button("Skip A/B/C test · Return to Home") {GuidedSurvey.skip(this)}
            link("Pause test and return to Diagnostics") {leave()}
            return
        }
        val visits=state.visits();val verdict=PageEvidence.assess(visits)
        if(visits.isNotEmpty()) {
            val completed=GuidedSurvey.next(visits)==null
            val candidate=verdict.code=="CANDIDATE"
            val canCheck=candidate && GuidedSurvey.anchorUsable(this)
            card {
                pill(if(candidate)"Readings repeatable" else if(completed)"Result: ${verdict.code}" else "${visits.size} of 6 visits saved",completed && !candidate)
                text(if(candidate && !canCheck)"Fresh readings needed" else if(candidate)"Readings repeatable · Visual check next" else if(completed)"Launcher page readings recorded" else "Continue where you left off",21f)
                text(if(candidate && !canCheck)"These saved readings belong to an older session. Start a fresh test below before checking the box." else if(candidate)"Three page positions repeated correctly. Show the pink box next to check whether it actually stays on page A." else if(completed)verdict.detail else "Your completed visits are saved.")
                if(canCheck)primary("Show test box on page A") {enableIsolation()}
                if(!completed)primary("Resume A/B/C page test") {
                    state.prefs.edit().putBoolean("guidedSurvey",true).apply();state.ids().forEach {CalibrationWidget.update(this,it)};render()
                }
                val observation=state.prefs.getString("isolationObservation","") ?: ""
                if(observation.isNotEmpty())notice("Your observation: $observation")
            }
        }
        if(visits.isEmpty())card {
            eyebrow("Optional diagnostic tool")
            text("A/B/C Multi-Page Check",23f)
            text("1. Place a different widget on each of three home-screen pages (A, B, and C).\n\n2. Tap Start A/B/C test below, then tap the widget on each page when prompted.\n\n3. Record six fresh visits: A → B → C → C → B → A. Wait two seconds, tap, then save each reading. For the second C visit, return home and tap again.")
            text("Normal widget animations do not require this test—you can skip it at any time.",13f,MUTED)
        }
        primary(if(visits.isEmpty())"Start A/B/C test" else "Start a fresh A/B/C test") {
            if(state.ids().size<3)message("Place a different widget on each of three home pages first. Empty temporary widgets are fine.")
            else if(visits.isEmpty())startTest() else android.app.AlertDialog.Builder(this).setTitle("Start a fresh page test?")
                .setMessage("This replaces only the saved page-test readings. Widget files and settings are kept.")
                .setNegativeButton("Keep readings",null).setPositiveButton("Start fresh") {_,_->startTest()}.show()
        }
        actionRow("Swipe fallback (experimental)","For fixed launcher readings · Manual page assignments","pages") {launch(android.content.Intent(this,SwipeFallbackActivity::class.java))}
        button("Skip A/B/C test · Go to Home") {GuidedSurvey.skip(this)}
        button("Download diagnostic report") {saveReport()}
        if(visits.isNotEmpty())link("View recorded readings") {details("Page readings",verdict.detail+"\n\n"+visits.joinToString("\n"))}
        link("Back to Diagnostics") {leave()}
    }
    private fun startTest(){GuidedSurvey.begin(this);render()}
    private fun enableIsolation() {
        val anchor=state.obj("surveyAnchor")
        if(PageEvidence.assess(state.visits()).code!="CANDIDATE" || !GuidedSurvey.anchorUsable(this)) {
            message("The visual check needs fresh readings and a usable position for page A's widget.");return
        }
        state.prefs.edit().putInt("selected",anchor.optInt("widgetId")).putBoolean("isolationPending",true)
            .putString("mode","PAGE").putLong("surveyBoxUntil",SystemClock.elapsedRealtime()+120000).apply()
        state.ids().forEach {CalibrationWidget.update(this,it)};home()
    }
    private fun answer(value:String) {
        state.prefs.edit().putString("isolationObservation",value).apply()
        GuidedSurvey.end(this);DiagnosticLog.event(this,"USER_PAGE_ISOLATION",value);render()
    }
    private fun leave(){GuidedSurvey.end(this);openSetup(true)}
    @Deprecated("Legacy back support") override fun onBackPressed(){leave()}
}
