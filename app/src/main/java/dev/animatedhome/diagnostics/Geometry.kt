package dev.animatedhome.diagnostics

import kotlin.math.abs

data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    fun valid(screenWidth: Int, screenHeight: Int) =
        listOf(left, top, right, bottom).all { it.isFinite() } &&
        width > 1 && height > 1 && left >= 0 && top >= 0 &&
        right <= screenWidth && bottom <= screenHeight
}

data class Offset(val x: Float, val y: Float, val xStep: Float, val yStep: Float,
                  val px: Int, val py: Int) {
    fun valid() = x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f &&
        xStep.isFinite() && yStep.isFinite()
    fun near(other: Offset) = valid() && other.valid() && abs(x-other.x) <= .008f &&
        abs(y-other.y) <= .008f && abs(px.toLong()-other.px) <= 4L && abs(py.toLong()-other.py) <= 4L
    fun distinct(other: Offset) = abs(x-other.x) >= .04f || abs(y-other.y) >= .04f ||
        abs(px.toLong()-other.px) >= 16L || abs(py.toLong()-other.py) >= 16L
}

data class Visit(val page: String, val round: Int, val tap: String, val environment: String,
                 val offset: Offset?, val stable: Boolean, val evidenceSource:String="launcher")
data class Verdict(val code: String, val detail: String)

object PageEvidence {
    fun assess(visits: List<Visit>): Verdict {
        val required = listOf("A", "B", "C").flatMap { p -> (1..2).map { p to it } }
        val chosen = required.map { (p, r) -> visits.lastOrNull { it.page == p && it.round == r } }
        if (chosen.any { it == null }) return Verdict("NOT_YET_TESTED", "Need A, B, C in rounds 1 and 2. Use six separate home-screen taps.")
        val six = chosen.filterNotNull()
        if(six.any {it.evidenceSource!="launcher"})return Verdict("INCONCLUSIVE", "Older, copied or estimated readings are not independent launcher evidence. Run six fresh visits.")
        if (six.map { it.tap }.distinct().size != 6) return Verdict("INCONCLUSIVE", "A tap was reused. Repeat six separate page visits.")
        if (six.map { it.environment }.distinct().size != 1) return Verdict("INCONCLUSIVE", "Display, launcher, or wallpaper engine changed. Start a fresh survey.")
        if (six.any { it.offset == null }) return Verdict("UNSUPPORTED", "At least one labelled page supplied no usable wallpaper offsets. Page-specific rendering is disabled.")
        if (six.any { !it.stable || !it.offset!!.valid() }) return Verdict("INCONCLUSIVE", "A sample was moving or invalid. Wait two seconds on each page and repeat.")
        val a = six.map { it.offset!! }
        if (a.all { it.near(a.first()) }) {
            val stepNote = if (a.first().xStep <= 0f) {
                "The launcher reported the same fixed wallpaper position on all three pages. These readings cannot identify the current page. If your launcher offers wallpaper scrolling, enable it and repeat the test. Otherwise try the optional swipe fallback below, or pin animations to all pages."
            } else {
                "All three labelled pages reported the same position. Playback can continue, but these readings cannot keep animations on one page."
            }
            return Verdict("UNSUPPORTED", stepNote)
        }
        for (i in listOf(0, 2, 4)) if (!a[i].near(a[i+1])) return Verdict("INCONCLUSIVE", "A repeat visit did not reproduce its offset. Page gating is disabled.")
        if (!a[0].distinct(a[2]) || !a[0].distinct(a[4]) || !a[2].distinct(a[4]))
            return Verdict("UNSUPPORTED", "Two labelled pages share indistinguishable offsets. Page-specific rendering is disabled.")
        return Verdict("CANDIDATE", "Three repeatable offset signatures found. This is evidence for a page gate, not a physical visual pass. Test slow and rapid swipes next.")
    }
}

/** Empirical viewport models, not an assertion that Android exposes the compositor transform. */
object Mapping {
    fun toSurface(b: Box, sw: Int, sh: Int, width: Int, height: Int, offset: Offset?,
                  model: String, dx: Float, dy: Float, scale: Float): Box {
        require(sw > 0 && sh > 0 && width > 0 && height > 0 && scale.isFinite() && scale in .5f..2f)
        val extraX = (width - sw).coerceAtLeast(0).toFloat()
        val extraY = (height - sh).coerceAtLeast(0).toFloat()
        val cropX = when(model) { "OFFSET" -> extraX * (offset?.x ?: .5f); "PIXEL" -> -(offset?.px?.toFloat() ?: 0f); "ORIGIN" -> 0f; else -> extraX/2 }
        val cropY = when(model) { "OFFSET" -> extraY * (offset?.y ?: .5f); "PIXEL" -> -(offset?.py?.toFloat() ?: 0f); "ORIGIN" -> 0f; else -> extraY/2 }
        fun x(v: Float) = (v - sw/2f) / scale + sw/2f + cropX + dx
        fun y(v: Float) = (v - sh/2f) / scale + sh/2f + cropY + dy
        return Box(x(b.left), y(b.top), x(b.right), y(b.bottom))
    }
}

object RenderGate {
    fun pageAllowed(verdict: String, sameEnvironment: Boolean, stableMillis: Long,
                    visible: Boolean, preview: Boolean, locked: Boolean,
                    zoom: Float, capturedZoom: Float, current: Offset?, target: Offset?, settleMillis:Long=500L) =
        verdict == "CANDIDATE" && sameEnvironment && settleMillis>=0 && stableMillis >= settleMillis && visible &&
        !preview && !locked && zoom.isFinite() && capturedZoom.isFinite() &&
        abs(zoom-capturedZoom) <= .005f && current != null && target != null && current.near(target)
}
