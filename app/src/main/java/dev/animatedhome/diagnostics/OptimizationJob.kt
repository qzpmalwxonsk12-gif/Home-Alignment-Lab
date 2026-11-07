package dev.animatedhome.diagnostics

import android.content.Context

/** Keep an optional optimization job independent of the dashboard Activity lifecycle. */
object OptimizationJob {
    @Volatile var busy=false;private set
    @Volatile var progress="Preparing saved videos…";private set
    @Volatile var result="";private set
    @Synchronized fun start(context:Context):Boolean {
        if(busy || !MediaStore.busy.compareAndSet(false,true))return false
        busy=true;result="";val app=context.applicationContext
        Thread {
            try {
                val count=VideoOptimizer.optimizeExistingWidgets(app){progress=it}
                result="$count saved video file(s) optimized. Performance depends on the phone and the source media."
            } catch(e:Exception){result="Optimization stopped: ${e.message ?: "Could not process the video"}"}
            finally {MediaStore.busy.set(false);busy=false}
        }.start()
        return true
    }
}
