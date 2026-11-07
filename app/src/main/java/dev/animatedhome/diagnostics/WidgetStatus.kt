package dev.animatedhome.diagnostics

data class WidgetStatus(val title:String,val detail:String,val attention:Boolean=false)

object WidgetStatusPolicy {
    fun assess(wallpaper:Boolean,file:Boolean,enabled:Boolean,position:Boolean,pending:Boolean,error:Boolean):WidgetStatus = when {
        error -> WidgetStatus("Needs attention","Open this widget to resolve its file or playback issue.",true)
        pending -> WidgetStatus("Review selection","Confirm the saved-file preview to use this animation.",true)
        !file -> WidgetStatus("Choose an animation","Add a GIF or video to make this widget yours.")
        !wallpaper -> WidgetStatus("Wallpaper off","Apply Home Alignment to display this animation.",true)
        !position -> WidgetStatus("Check position","Tap it on your home screen, or adjust its position.",true)
        enabled -> WidgetStatus("Enabled","Ready to animate on its home-screen page.")
        else -> WidgetStatus("Paused","Your animation is saved. Open it to resume.")
    }
}
