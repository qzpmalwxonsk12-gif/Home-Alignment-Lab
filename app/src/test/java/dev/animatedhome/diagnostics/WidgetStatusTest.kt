package dev.animatedhome.diagnostics

import org.junit.Assert.*
import org.junit.Test

class WidgetStatusTest {
    @Test fun enabledIsNotClaimedAsLivePlayback(){assertEquals("Enabled",WidgetStatusPolicy.assess(true,true,true,true,false,false).title)}
    @Test fun failureHasPriority(){assertEquals("Needs attention",WidgetStatusPolicy.assess(true,true,true,true,false,true).title)}
    @Test fun pendingNeedsConfirmation(){assertEquals("Review selection",WidgetStatusPolicy.assess(true,true,false,true,true,false).title)}
    @Test fun wallpaperOffCannotBeReady(){assertEquals("Wallpaper off",WidgetStatusPolicy.assess(false,true,true,true,false,false).title)}
    @Test fun missingPositionNeedsAttention(){assertTrue(WidgetStatusPolicy.assess(true,true,true,false,false,false).attention)}
    @Test fun retainedFileCanBePaused(){assertEquals("Paused",WidgetStatusPolicy.assess(true,true,false,true,false,false).title)}
    @Test fun emptyWidgetAsksForAnimation(){assertEquals("Choose an animation",WidgetStatusPolicy.assess(true,false,false,true,false,false).title)}
}
