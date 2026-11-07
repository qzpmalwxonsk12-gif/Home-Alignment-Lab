package dev.animatedhome.diagnostics

import org.junit.Assert.*
import org.junit.Test

class VideoReturnGateTest {
    private val target=Offset(.45454547f,.5f,.09090909f,1f,-689,0)
    private fun allowed(stable:Long=150,current:Offset?=target,visible:Boolean=true,
                        locked:Boolean=false,preview:Boolean=false,same:Boolean=true,
                        zoom:Float=1f,verdict:String="CANDIDATE") =
        RenderGate.pageAllowed(verdict,same,stable,visible,preview,locked,zoom,1f,current,target,VideoPolicy.SETTLE_MILLIS)
    @Test fun returnWaitsForFullShortSettle(){ assertFalse(allowed(149)); assertTrue(allowed(150)) }
    @Test fun everyMovingOffsetRestartsWait(){
        for(age in listOf(0L,40L,120L,149L,0L,80L)) assertFalse(allowed(age))
        assertTrue(allowed(150))
    }
    @Test fun delayedWakeRechecksLatestPage(){ assertFalse(allowed(200,current=target.copy(x=.54545456f,px=-827))) }
    @Test fun hiddenAndLockedStillReject(){ assertFalse(allowed(visible=false)); assertFalse(allowed(locked=true)) }
    @Test fun previewAndStaleCalibrationStillReject(){ assertFalse(allowed(preview=true)); assertFalse(allowed(same=false)) }
    @Test fun missingAndUnsupportedTelemetryStillReject(){ assertFalse(allowed(current=null)); assertFalse(allowed(verdict="UNSUPPORTED")) }
    @Test fun zoomMismatchStillRejects(){ assertFalse(allowed(zoom=.8f)); assertFalse(allowed(zoom=Float.NaN)) }
    @Test fun pinkPageExperimentRetainsOriginalSettle(){
        assertFalse(RenderGate.pageAllowed("CANDIDATE",true,150,true,false,false,1f,1f,target,target))
        assertTrue(RenderGate.pageAllowed("CANDIDATE",true,500,true,false,false,1f,1f,target,target))
    }
}
