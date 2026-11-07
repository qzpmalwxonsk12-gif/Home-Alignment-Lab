package dev.animatedhome.diagnostics

import org.junit.Assert.*
import org.junit.Test

class VideoPolicyTest {
    private val env="com.android.launcher|16.4.28|1264|2780|560|0|0|engine-one|surface=1264x2780|insets=0,140,0,154"
    private val a=Offset(.45454547f,.5f,.09090909f,1f,-689,0)
    @Test fun wallpaperRestartMayRetainHistoricalProfile(){ assertTrue(VideoPolicy.sameProfile(env,env.replace("engine-one","engine-two"))) }
    @Test fun resolutionChangeRejectsHistory(){ assertFalse(VideoPolicy.sameProfile(env,env.replace("1264","1080"))) }
    @Test fun launcherChangeRejectsHistory(){ assertFalse(VideoPolicy.sameProfile(env,env.replace("16.4.28","17.0"))) }
    @Test fun insetsChangeRejectsHistory(){ assertFalse(VideoPolicy.sameProfile(env,env.replace("140","150"))) }
    @Test fun malformedEnvironmentRejectsHistory(){ assertFalse(VideoPolicy.sameProfile("", "")) }
    @Test fun matchingPageIsAccepted(){ assertTrue(VideoPolicy.samePage(a,a)) }
    @Test fun changedPageStepRejectsHistory(){ assertFalse(VideoPolicy.samePage(a,a.copy(xStep=.1f))) }
    @Test fun differentPageRejectsHistory(){ assertFalse(VideoPolicy.samePage(a,a.copy(x=.54545456f,px=-827))) }
    @Test fun missingPageIsRejected(){ assertFalse(VideoPolicy.samePage(a,null)) }
    @Test fun videoFramesFollowPresentationTimes(){ assertEquals(33L,VideoPolicy.frameDelayMillis(1_000_000_000,33_333,1_000_000_000)) }
    @Test fun overdueFramesDoNotSleep(){ assertEquals(0L,VideoPolicy.frameDelayMillis(1_000_000_000,33_333,1_100_000_000)) }
    @Test fun longStallMustResync(){ assertTrue(VideoPolicy.needsResync(1_000_000_000,33_333,2_000_000_000)) }
    @Test fun smallDelayDoesNotResync(){ assertFalse(VideoPolicy.needsResync(1_000_000_000,33_333,1_100_000_000)) }
    @Test fun landscapeVideoFitsSquareWithoutCropping(){
        val b=VideoPolicy.fit(Box(10f,20f,610f,620f),640,360)
        assertEquals(10f,b.left,0f); assertEquals(610f,b.right,0f)
        assertEquals(337.5f,b.height,.01f); assertEquals(151.25f,b.top,.01f)
    }
    @Test fun portraitVideoFitsWideWidgetWithoutCropping(){
        val b=VideoPolicy.fit(Box(0f,0f,600f,300f),360,640)
        assertEquals(300f,b.height,.01f); assertEquals(168.75f,b.width,.01f)
        assertTrue(b.left>=0 && b.right<=600 && b.top>=0 && b.bottom<=300)
    }
    @Test fun landscapeVideoCenterCropsHorizontallyInFillMode(){
        val uv=VideoPolicy.cropUv(Box(0f,0f,400f,400f),1920,1080)
        assertEquals(0.21875f,uv[0],.001f); assertEquals(0f,uv[1],.001f)
        assertEquals(0.78125f,uv[2],.001f); assertEquals(1f,uv[3],.001f)
    }
    @Test fun portraitVideoCenterCropsVerticallyInFillMode(){
        val uv=VideoPolicy.cropUv(Box(0f,0f,400f,400f),1080,1920)
        assertEquals(0f,uv[0],.001f); assertEquals(0.21875f,uv[1],.001f)
        assertEquals(1f,uv[2],.001f); assertEquals(0.78125f,uv[3],.001f)
    }
}
