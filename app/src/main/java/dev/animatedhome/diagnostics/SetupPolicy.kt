package dev.animatedhome.diagnostics

/** Pure decisions shared by the UI and regression tests. Never infer drawing from valid bounds. */
object SetupPolicy {
    fun step(backgroundChosen: Boolean, wallpaperActive: Boolean, hasWidgets: Boolean) = when {
        !backgroundChosen && !wallpaperActive -> 1
        !wallpaperActive -> 2
        !hasWidgets -> 3
        else -> 4
    }
    fun readiness(active: Boolean, installed: Boolean, valid: Boolean, engineReady: Boolean, sameEnvironment: Boolean) = when {
        !active -> "WALLPAPER_OFF"
        !installed -> "WIDGET_REMOVED"
        !valid -> "BOUNDS_MISSING"
        !engineReady -> "ENGINE_NOT_READY"
        !sameEnvironment -> "CAPTURE_STALE"
        else -> "READY"
    }
    fun confirmationApplies(installed: Boolean, valid: Boolean, sameEnvironment: Boolean, sameBounds: Boolean) = installed && valid && sameEnvironment && sameBounds
}
