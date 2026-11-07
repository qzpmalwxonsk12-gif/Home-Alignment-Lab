package dev.animatedhome.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SwipePageTrackerTest {
    private fun tracker(page:Int=1)=SwipePageTracker().apply {anchor(page)}
    private fun swipe(t:SwipePageTracker,from:Float,to:Float,duration:Long=500){t.begin(from,500f,1000,1000f,12f);t.move(to,500f);t.finish(to,500f,1000+duration)}
    @Test fun unknownUntilUserAnchor(){assertNull(SwipePageTracker().current())}
    @Test fun unknownDoesNotCountSwipe(){val t=SwipePageTracker();swipe(t,800f,200f);assertNull(t.page)}
    @Test fun swipeLeftAdvancesOnePage(){val t=tracker();swipe(t,800f,200f);assertEquals(2,t.page)}
    @Test fun swipeRightGoesBack(){val t=tracker();swipe(t,200f,800f);assertEquals(0,t.page)}
    @Test fun lastPageDoesNotInventFourthPage(){val t=tracker(2);swipe(t,800f,200f);assertEquals(2,t.page)}
    @Test fun firstPageDoesNotWrap(){val t=tracker(0);swipe(t,200f,800f);assertEquals(0,t.page)}
    @Test fun tinyMovementIsTap(){val t=tracker();swipe(t,500f,490f,50);assertEquals(1,t.page)}
    @Test fun cancelledGestureStaysOnSamePage(){val t=tracker();t.begin(800f,500f,1000,1000f,12f);t.move(200f,500f);assertNull(t.current());t.cancel();assertEquals(1,t.page);assertNotNull(t.current())}
    @Test fun cancelledUpDoesNotMovePage(){val t=tracker();t.begin(800f,500f,1000,1000f,12f);t.cancel();assertFalse(t.finish(100f,500f,1500));assertEquals(1,t.page)}
    @Test fun verticalGestureIsNotPageChange(){val t=tracker();t.begin(700f,300f,1000,1000f,12f);t.move(680f,700f);t.finish(200f,750f,1500);assertEquals(1,t.page)}
    @Test fun edgeBackGestureIgnored(){val t=tracker();swipe(t,10f,800f);assertEquals(1,t.page);assertEquals(0,t.gestures)}
    @Test fun longPressDragIgnored(){val t=tracker();swipe(t,800f,200f,2000);assertEquals(1,t.page)}
    @Test fun quickFlingCanChangePage(){val t=tracker();swipe(t,700f,580f,100);assertEquals(2,t.page)}
    @Test fun shortSlowDragDoesNotChangePage(){val t=tracker();swipe(t,700f,580f,700);assertEquals(1,t.page)}
    @Test fun restartRequiresAnchor(){val t=tracker();t.reset();assertNull(t.current())}
    @Test fun layoutChangeRequiresAnchor(){val t=tracker();t.configure(5);assertNull(t.current());assertTrue(t.anchor(4));assertEquals(1f,t.current()!!.x,0f)}
    @Test fun invalidAnchorDoesNotCorruptPage(){val t=tracker();assertFalse(t.anchor(3));assertEquals(1,t.page)}
    @Test fun gestureMissingDownDoesNotMove(){val t=tracker();t.move(10f,500f);t.finish(10f,500f,500);assertEquals(1,t.page)}
    @Test fun syntheticOffsetsCannotPassDiagnostic(){val t=tracker();val visits=listOf("A","B","C").flatMapIndexed {i,label->(1..2).map {Visit(label,it,"$label$it","env",t.at(i),true,"gesture")}};assertEquals("INCONCLUSIVE",PageEvidence.assess(visits).code)}
    @Test fun fixedXiaomiOffsetsRemainUnsupported(){val raw=Offset(0f,.5f,-1f,-1f,0,0);val v=listOf("A","B","C").flatMap {label->(1..2).map {Visit(label,it,"$label$it","env",raw,true)}};assertEquals("UNSUPPORTED",PageEvidence.assess(v).code)}
    @Test fun oldQuickCopiesAreNotIndependentEvidence(){val v=listOf("A","B","C").flatMapIndexed {i,label->(1..2).map {Visit(label,it,"$label$it","env",Offset(i*.5f,.5f,.5f,0f,0,0),true,"legacy")}};assertEquals("INCONCLUSIVE",PageEvidence.assess(v).code)}
}
