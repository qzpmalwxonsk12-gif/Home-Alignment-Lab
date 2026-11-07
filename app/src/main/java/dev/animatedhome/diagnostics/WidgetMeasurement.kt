package dev.animatedhome.diagnostics

import kotlin.math.abs

/** RemoteViews sourceBounds combines a transformed origin with layout dimensions.
 * A bottom-right child supplies a second transformed origin. Its layout size lets
 * us recover the parent's scale independently on each axis, without assuming dp
 * options describe screen coordinates. Requires no movement between the two taps.
 */
object WidgetMeasurement {
    const val VERSION = 1
    fun usableSource(b: Box?, sw: Int, sh: Int): Boolean = b != null &&
        listOf(b.left,b.top,b.right,b.bottom).all { it.isFinite() } &&
        b.width > 1 && b.height > 1 && b.left >= 0 && b.top >= 0 && b.left < sw && b.top < sh

    fun measure(root: Box, corner: Box, sw: Int, sh: Int): Box? {
        if (!usableSource(root,sw,sh) || !usableSource(corner,sw,sh)) return null
        val distanceX = root.width - corner.width
        val distanceY = root.height - corner.height
        if (distanceX < 24 || distanceY < 24) return null
        val sx = (corner.left-root.left)/distanceX
        val sy = (corner.top-root.top)/distanceY
        if (!sx.isFinite() || !sy.isFinite() || sx !in .25f..2f || sy !in .25f..2f) return null
        return Box(root.left,root.top,root.left+root.width*sx,root.top+root.height*sy)
            .takeIf { it.valid(sw,sh) }
    }

    // Launchers such as Xiaomi HyperOS/MIUI animate widget scale on press, shifting
    // sourceBounds by ±3–12 px per tap even when the widget has not moved on the grid.
    const val TAP_JITTER_TOLERANCE = 16f
    fun same(a: Box?, b: Box?, tolerance: Float = 2f): Boolean = a != null && b != null &&
        listOf(abs(a.left-b.left),abs(a.top-b.top),abs(a.right-b.right),abs(a.bottom-b.bottom)).all { it <= tolerance } &&
        abs(a.width - b.width) <= maxOf(tolerance, 12f) && abs(a.height - b.height) <= maxOf(tolerance, 12f)

    fun relocate(raw: Box, previousRaw: Box, measured: Box) = Box(raw.left,raw.top,
        raw.left+raw.width*measured.width/previousRaw.width,
        raw.top+raw.height*measured.height/previousRaw.height)
}
