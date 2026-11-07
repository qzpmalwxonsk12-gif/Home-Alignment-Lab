package dev.animatedhome.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import org.json.JSONObject

object SwipeFallback {
    fun count(s:State)=s.prefs.getInt("swipeFallbackPages",3).coerceIn(2,10)
    fun label(page:Int)="Page ${('A'.code+page).toChar()}"
    private fun profile(context:Context,env:String?=null)=(env ?: environment(context)).split('|').take(7).joinToString("|")
    fun enabled(context:Context,env:String?=null):Boolean {
        val s=State(context)
        return s.prefs.getBoolean("swipeFallbackEnabled",false) && s.prefs.getString("swipeFallbackProfile","")==profile(context,env)
    }
    fun assigned(s:State,id:Int):Int?=s.prefs.getInt(WidgetAnimations.key("fallbackPage",id),-1).takeIf {it in 0 until count(s)}
    fun active(context:Context,env:String?=null)=enabled(context,env) && Live.offset?.let {it.xStep<=0f && it.px==0 && !Live.offsetsChangedAcrossPages}==true
    fun configure(context:Context){Live.swipeTracker.configure(count(State(context)))}
    fun setEnabled(context:Context,value:Boolean) {
        val s=State(context)
        s.prefs.edit().putBoolean("swipeFallbackEnabled",value).putString("swipeFallbackProfile",profile(context)).apply()
        configure(context);Live.swipeTracker.reset()
        DiagnosticLog.event(context,"SWIPE_FALLBACK",if(value)"Enabled by user; estimated pages, not A/B/C evidence" else "Disabled by user")
    }
    fun anchor(context:Context,id:Int):Boolean {
        if(!active(context))return false
        configure(context)
        val index=assigned(State(context),id) ?: return false
        val ok=Live.swipeTracker.anchor(index)
        if(ok)DiagnosticLog.event(context,"SWIPE_ANCHOR","widget=$id ${label(index)} estimated=true")
        return ok
    }
    fun snapshot(context:Context):JSONObject {
        val s=State(context);val bindings=JSONObject()
        s.ids().forEach {id->assigned(s,id)?.let {bindings.put(id.toString(),label(it))}}
        return JSONObject().put("enabled",enabled(context)).put("active",active(context))
            .put("pageCount",count(s)).put("estimatedPage",Live.swipeTracker.page?.let {label(it)} ?: "Unknown: tap an assigned widget")
            .put("gesturesReceived",Live.swipeTracker.gestures).put("estimatedPageChanges",Live.swipeTracker.swipes)
            .put("bindings",bindings).put("evidence","Gesture estimate only; A/B/C uses raw launcher readings")
    }
}

class SwipeFallbackActivity:LabActivity() {
    private val state by lazy {State(this)}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)}
    override fun onResume(){super.onResume();render()}
    private fun refreshKeepingPlace() {
        val position=(body.parent as? android.widget.ScrollView)?.scrollY ?: 0
        render()
        (body.parent as? android.widget.ScrollView)?.let {view->view.post {view.scrollTo(0,position)}}
    }
    private fun render() {
        page("Swipe fallback","Optional · Experimental page estimates",{openSetup(true)})
        card {
            pill(if(SwipeFallback.enabled(this))"Enabled · Estimated pages" else "Off",SwipeFallback.enabled(this))
            text("For launchers with fixed page readings",21f)
            text("This tries to count left/right swipes when your launcher does not report page changes. It cannot guarantee the right page: missed gestures, cancelled swipes, Home-button jumps and looping pages can put it out of sync. It never makes A/B/C testing pass.",14f,MUTED)
        }
        section("1. Count your home pages")
        text("Count all pages from left to right, including pages with no widgets. Turn off looping/infinite pages in your launcher before using this mode.",14f,MUTED)
        button("Home pages: ${SwipeFallback.count(state)}") {
            android.app.AlertDialog.Builder(this).setTitle("Number of home pages")
                .setItems((2..10).map {"$it pages"}.toTypedArray()){_,n->
                    state.prefs.edit().putInt("swipeFallbackPages",n+2).apply();SwipeFallback.configure(this);Live.swipeTracker.reset();refreshKeepingPlace()
                }.show()
        }
        section("2. Assign each widget to its page")
        text("A is the leftmost page, B the next, and so on. After moving a widget, update its assignment here.",14f,MUTED)
        if(state.ids().isEmpty())notice("Add a widget from Home first.")
        state.ids().forEach {id->
            actionRow(WidgetAnimations.name(state,id),SwipeFallback.assigned(state,id)?.let {SwipeFallback.label(it)} ?: "Not assigned","pages") {
                val options=arrayOf("Not assigned")+(0 until SwipeFallback.count(state)).map {SwipeFallback.label(it)}
                android.app.AlertDialog.Builder(this).setTitle(WidgetAnimations.name(state,id)).setItems(options){_,n->
                    state.prefs.edit().putInt(WidgetAnimations.key("fallbackPage",id),n-1).apply();Live.swipeTracker.reset();refreshKeepingPlace()
                }.show()
            }
        }
        section("3. Enable, then sync on your home screen")
        text("Tap any assigned widget once on its actual home page, then return to that same page. Swipe on empty space to try it. If it drifts, tap an assigned widget again. Repeat after a wallpaper restart. Unassigned widgets keep their usual behavior; pinned animations stay on all pages.",14f,MUTED)
        if(SwipeFallback.enabled(this)) {
            primary("Go home and tap an assigned widget") {home()}
            button("Turn off swipe fallback") {SwipeFallback.setEnabled(this,false);refreshKeepingPlace()}
        } else primary("Enable experimental swipe fallback") {
            if(state.ids().none {SwipeFallback.assigned(state,it)!=null})message("Assign at least one widget to its home page first.")
            else {SwipeFallback.setEnabled(this,true);refreshKeepingPlace()}
        }
        text("If swipes do nothing, your launcher may not forward wallpaper touch events. Use the per-widget Pin to all home pages option or turn this fallback off.",13f,MUTED)
        button("Refresh status") {refreshKeepingPlace()}
        val tracker=Live.swipeTracker
        notice("Estimated page: ${tracker.page?.let {SwipeFallback.label(it)} ?: "Unknown"}\nGestures received: ${tracker.gestures} · Estimated page changes: ${tracker.swipes}\n${if(SwipeFallback.active(this))"Fixed launcher readings detected." else "Fallback is inactive; native page readings take priority when available."}")
        button("Download diagnostic report") {saveReport()}
        link("Back to Diagnostics") {openSetup(true)}
    }
    @Deprecated("Legacy back support") override fun onBackPressed(){openSetup(true)}
}
