package dev.animatedhome.diagnostics

object VideoPolicy {
    const val SETTLE_MILLIS = 150L
    const val GOOD_ISOLATION = "user-observed no leakage in the swipes performed"
    // The engine UUID changes on APK updates; retain historical evidence only for
    // the identical launcher/display/surface configuration and matching page-A offset.
    fun profile(environment: String): String {
        val pieces=environment.split('|')
        return if(pieces.size>=10) pieces.filterIndexed { index,_ -> index!=7 }.joinToString("|") else ""
    }
    fun sameProfile(a:String,b:String)=profile(a).isNotEmpty() && profile(a)==profile(b)
    fun samePage(a:Offset?,b:Offset?)=a!=null && b!=null && a.near(b) &&
        kotlin.math.abs(a.xStep-b.xStep)<.0001f && kotlin.math.abs(a.yStep-b.yStep)<.0001f
    fun frameDelayMillis(startNanos:Long,ptsUs:Long,nowNanos:Long)=((startNanos+ptsUs*1000-nowNanos)/1_000_000).coerceAtLeast(0)
    fun needsResync(startNanos:Long,ptsUs:Long,nowNanos:Long)=nowNanos-(startNanos+ptsUs*1000)>500_000_000
    fun fit(b:Box,sourceWidth:Int,sourceHeight:Int):Box {
        if(sourceWidth<=0 || sourceHeight<=0) return b
        val scale=minOf(b.width/sourceWidth,b.height/sourceHeight)
        val w=sourceWidth*scale; val h=sourceHeight*scale
        val left=b.left+(b.width-w)/2; val top=b.top+(b.height-h)/2
        return Box(left,top,left+w,top+h)
    }
    fun cropUv(b:Box,sourceWidth:Int,sourceHeight:Int):FloatArray {
        if(sourceWidth<=0 || sourceHeight<=0 || b.width<=0f || b.height<=0f) return floatArrayOf(0f,0f,1f,1f)
        val boxAspect=b.width/b.height
        val srcAspect=sourceWidth.toFloat()/sourceHeight.toFloat()
        return if(srcAspect>boxAspect) {
            val vis=(boxAspect/srcAspect).coerceIn(0.05f,1f)
            val m=(1f-vis)/2f
            floatArrayOf(m,0f,1f-m,1f)
        } else {
            val vis=(srcAspect/boxAspect).coerceIn(0.05f,1f)
            val m=(1f-vis)/2f
            floatArrayOf(0f,m,1f,1f-m)
        }
    }
}
