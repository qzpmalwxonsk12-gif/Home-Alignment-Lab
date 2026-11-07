package dev.animatedhome.diagnostics

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews

class CalibrationWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context,intent)
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) State(context).ids().forEach { update(context,it) }
    }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { State(context).options(it, manager.getAppWidgetOptions(it)); update(context, it) }
    }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        State(context).options(id, options)
        update(context, id)
    }
    override fun onDeleted(context: Context, ids: IntArray) {
        val s = State(context)
        val edit = s.prefs.edit().putString("mode", "OFF")
        ids.forEach { id->
            if(s.prefs.getInt("youtubeWidgetId",-1)==id) edit.remove("youtubeWidgetId")
            if(s.prefs.getInt("chromeWidgetId",-1)==id) edit.remove("chromeWidgetId")
            edit.remove("widget_$id").remove("folder_$id").remove("apps_$id")
            WidgetAnimations.mediaKeys.forEach {edit.remove(WidgetAnimations.key(it,id))}
        }
        edit.apply()
        DiagnosticLog.event(context, "WIDGET_DELETED", ids.joinToString())
    }
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        State(context).invalidate("Widget IDs restored. Recalibrate all positions and pages.")
        newWidgetIds.forEach { update(context, it) }
    }
    companion object {
        fun update(context: Context, id: Int) {
            AppWidgetManager.getInstance(context).updateAppWidget(id, views(context,id))
        }
        fun views(context: Context, id: Int): RemoteViews {
            val s = State(context)
            val folder = s.prefs.getBoolean("folder_$id", false)
            val adjust = s.prefs.getBoolean(WidgetAnimations.key("adjust",id),false)
            val survey=s.prefs.getBoolean("guidedSurvey",false) || s.prefs.getBoolean("isolationPending",false)
            val playing = WidgetAnimations.enabled(s,id)
            val valid = s.record(id).optBoolean("valid")
            val intent = Intent(context, if (folder && !adjust && !survey) FolderActivity::class.java else CaptureActivity::class.java)
                .setAction("dev.animatedhome.diagnostics.WIDGET_TAP")
                .setData(Uri.parse("alignmentlab://widget/$id/${if (folder && !adjust && !survey) "folder" else "capture"}"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            // RemoteViews fills in sourceBounds at send time. IMMUTABLE would discard them.
            // Component/action/data are explicit and unique; only our Activity receives this token.
            val flags = PendingIntent.FLAG_CANCEL_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getActivity(context, id, intent, flags)
            val views = RemoteViews(context.packageName, R.layout.calibration_widget)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            val role = WidgetAnimations.role(s, id)
            val idleLabel = when(role) {
                "YOUTUBE" -> "YouTube Widget · Tap"
                "CHROME" -> "Chrome Widget · Tap"
                else -> "Tap to add animation"
            }
            views.setTextViewText(R.id.widget_label, if(survey) "Tap for page test · #$id" else if(adjust) "1 · Tap here · #$id" else if(!playing) idleLabel else "")
            val cornerIntent = Intent(context,CaptureActivity::class.java)
                .setAction("dev.animatedhome.diagnostics.WIDGET_TAP")
                .setData(Uri.parse("alignmentlab://widget/$id/corner"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).putExtra("corner",true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            views.setOnClickPendingIntent(R.id.widget_corner,PendingIntent.getActivity(context,id,cornerIntent,flags))
            val pink = s.prefs.getInt("pinkWidget",-1)==id && android.os.SystemClock.elapsedRealtime()<s.prefs.getLong("pinkUntil",0)
            val outline = adjust || survey || !playing || !valid || pink
            views.setInt(R.id.widget_root, "setBackgroundResource", if (outline) R.drawable.widget_outline else android.R.color.transparent)
            views.setViewVisibility(R.id.widget_label, if (adjust || survey || !playing) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_corner, if (adjust && !survey) View.VISIBLE else View.GONE)
            return views
        }
    }
}
