package dev.animatedhome.diagnostics

import android.app.Application

class LabApplication:Application() {
    override fun onCreate() {
        super.onCreate()
        // No engine exists yet. Remove only our abandoned private imports and old copies.
        WidgetAnimations.migrate(this)
        GuidedSurvey.migrate(this)
        val s=State(this)
        val keep=s.ids().flatMap {id->listOf("selectedMedia","pendingMedia","playbackMedia").map {s.obj(WidgetAnimations.key(it,id)).optString("file")} }.toSet()
        filesDir.listFiles()?.filter { it.name !in keep &&
            (it.name.matches(Regex("media-[a-f0-9-]+\\.bin")) || it.name.matches(Regex("import-[a-f0-9-]+\\.tmp"))) }
            ?.forEach { it.delete() }
    }
}
