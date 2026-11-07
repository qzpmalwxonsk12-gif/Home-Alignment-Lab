package dev.animatedhome.diagnostics

/** Ordinary taps locate widgets. Measured launcher scale is reused when available. */
object WidgetPlacement {
    fun automatic(raw:Box?,sw:Int,sh:Int,sx:Float=1f,sy:Float=1f):Box? {
        if(!WidgetMeasurement.usableSource(raw,sw,sh) || !sx.isFinite() || !sy.isFinite() || sx !in .25f..2f || sy !in .25f..2f)return null
        val b=raw!!
        return Box(b.left,b.top,b.left+b.width*sx,b.top+b.height*sy).takeIf {it.valid(sw,sh)}
    }
    fun pageAllowed(sameProfile:Boolean,stableMillis:Long,visible:Boolean,home:Boolean,locked:Boolean,
        zoom:Float,capturedZoom:Float,current:Offset?,target:Offset?,settleMillis:Long=VideoPolicy.SETTLE_MILLIS):Boolean =
        sameProfile && stableMillis>=settleMillis && visible && home && !locked &&
        zoom.isFinite() && capturedZoom.isFinite() && kotlin.math.abs(zoom-capturedZoom)<.005f && VideoPolicy.samePage(current,target)

    fun liveBox(
        box:Box?,
        sw:Int,
        sh:Int,
        sameProfile:Boolean,
        stableMillis:Long,
        visible:Boolean,
        home:Boolean,
        locked:Boolean,
        zoom:Float,
        capturedZoom:Float,
        current:Offset?,
        target:Offset?,
        swipeMode:String="SLIDE"
    ):Box? {
        if(box==null || !box.valid(sw,sh) || !sameProfile || !visible || !home || locked)return null
        if(!zoom.isFinite() || !capturedZoom.isFinite() || kotlin.math.abs(zoom-capturedZoom)>=.01f)return null
        return when(swipeMode) {
            "ALWAYS" -> box
            "INSTANT" -> if(VideoPolicy.samePage(current,target)) box else null
            "SETTLED" -> if(stableMillis>=VideoPolicy.SETTLE_MILLIS && VideoPolicy.samePage(current,target)) box else null
            else -> {
                if(current==null || target==null || !current.valid() || !target.valid())return null
                if(VideoPolicy.samePage(current,target))return box
                val step=target.xStep
                if(step>0.001f && kotlin.math.abs(current.xStep-step)<0.001f) {
                    val pages=(current.x-target.x)/step
                    val dx=-pages*sw
                    val shifted=Box(box.left+dx,box.top,box.right+dx,box.bottom)
                    if(shifted.right>0f && shifted.left<sw.toFloat()) shifted else null
                } else null
            }
        }
    }
}
