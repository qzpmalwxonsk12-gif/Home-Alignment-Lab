package dev.animatedhome.diagnostics

import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class FolderActivity : LabActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) recordTap()
        renderFolder()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recordTap()
        renderFolder()
    }
    private fun recordTap() {
        val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1)
        if (State(this).prefs.getBoolean("multipleWidgets",false) && !State(this).prefs.getBoolean("guidedSurvey",false)) {
            if (State(this).ids().contains(id)) WidgetAnimations.locate(this,id)
        } else {
            capture(this)
        }
    }
    private fun renderFolder() {
        val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1)
        page("Widget #$id · folder", "Selected launcher shortcuts")
        val state=State(this)
        if (!state.ids().contains(id)) { text("Widget no longer exists."); return }
        val all=apps(this)
        val saved=state.prefs.getStringSet("apps_$id",null)
        val selected=if(saved==null) all.take(4) else all.filter { key(it) in saved }
        if(selected.isEmpty()) text("No selected apps are available. Use Choose shortcuts to select installed launchable apps.")
        selected.forEach { info ->
            val row=LinearLayout(this).apply { gravity=android.view.Gravity.CENTER_VERTICAL; setPadding(dp(8),dp(6),dp(8),dp(6)); minimumHeight=dp(56) }
            row.addView(ImageView(this).apply { setImageDrawable(info.loadIcon(packageManager)); contentDescription=info.loadLabel(packageManager) },LinearLayout.LayoutParams(dp(40),dp(40)))
            row.addView(TextView(this).apply { text=info.loadLabel(packageManager); textSize=17f; setPadding(dp(12),0,0,0); setTextColor(android.graphics.Color.WHITE) })
            row.isClickable=true; row.isFocusable=true
            row.contentDescription="Open ${info.loadLabel(packageManager)}"
            row.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(ComponentName(info.activityInfo.packageName,info.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
                    DiagnosticLog.event(this,"SHORTCUT_LAUNCH",key(info)); finish()
                } catch(e: Exception) { DiagnosticLog.event(this,"SHORTCUT_FAILURE",e.toString()); message("App unavailable: ${e.message}") }
            }
            body.addView(row)
        }
        button("Choose shortcuts") { choose(this,id) { recreate() } }
        button("Animation & widget settings") { WidgetAnimations.open(this,id) }
        button("Close") { finish() }
        window.setLayout((resources.displayMetrics.widthPixels*.9f).toInt(),LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    companion object {
        private fun key(info: ResolveInfo)=ComponentName(info.activityInfo.packageName,info.activityInfo.name).flattenToString()
        @Suppress("DEPRECATION")
        private fun apps(a: LabActivity)=a.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .filter { it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.packageName != a.packageName }
            .distinctBy { key(it) }.sortedBy { it.loadLabel(a.packageManager).toString().lowercase() }
        fun choose(a: LabActivity,id: Int,after: () -> Unit = {}) {
            val list=apps(a); val state=State(a)
            val selected=state.prefs.getStringSet("apps_$id",null) ?: list.take(4).map { key(it) }.toSet()
            val checked=BooleanArray(list.size) { key(list[it]) in selected }
            AlertDialog.Builder(a).setTitle("Select up to 8 launchable apps")
                .setMultiChoiceItems(list.map { it.loadLabel(a.packageManager).toString() }.toTypedArray(),checked) { _,n,value -> checked[n]=value }
                .setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_ ->
                    if(checked.count { it }>8) a.message("Choose at most eight apps. Selection was not saved.")
                    else { state.prefs.edit().putStringSet("apps_$id",list.filterIndexed { n,_ -> checked[n] }.map { key(it) }.toSet()).apply(); after() }
                }.show()
        }
    }
}
