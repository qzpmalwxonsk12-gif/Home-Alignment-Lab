package dev.animatedhome.diagnostics

/** Reuse spatial setup only while the launcher/display profile and measured widget are intact. */
object SavedSetupPolicy {
    fun canRestore(confirmed:Boolean, exists:Boolean, measured:Boolean, sameProfile:Boolean,
        targetPage:Boolean, stable:Boolean, zoomMatches:Boolean)=
        confirmed && exists && measured && sameProfile && targetPage && stable && zoomMatches
    fun canPlay(hasFile:Boolean,busy:Boolean,pending:Boolean,failed:Boolean)=hasFile && !busy && !pending && !failed
}
