package dev.animatedhome.diagnostics

import org.junit.Assert.*
import org.junit.Test

class WidgetPlacementTest {
    private val page=Offset(1f,0f,1f,0f,-1000,0)
    @Test fun ordinaryTapUsesReportedRectangle(){assertEquals(Box(10f,20f,410f,220f),WidgetPlacement.automatic(Box(10f,20f,410f,220f),1080,1920))}
    @Test fun savedScaleCorrectsOversizedOemBounds(){assertEquals(Box(100f,200f,600f,500f),WidgetPlacement.automatic(Box(100f,200f,1100f,800f),1080,1920,.5f,.5f))}
    @Test fun missingBoundsCannotInventPosition(){assertNull(WidgetPlacement.automatic(null,1080,1920))}
    @Test fun emptyBoundsRejected(){assertNull(WidgetPlacement.automatic(Box(10f,10f,10f,10f),1080,1920))}
    @Test fun offscreenBoundsRejected(){assertNull(WidgetPlacement.automatic(Box(100f,200f,1100f,800f),1080,1920))}
    @Test fun invalidScaleRejected(){assertNull(WidgetPlacement.automatic(Box(0f,0f,200f,200f),1080,1920,Float.NaN,1f))}
    @Test fun samePageNeedsNoSurvey(){assertTrue(WidgetPlacement.pageAllowed(true,150,true,true,false,0f,0f,page,page))}
    @Test fun differentPageHidden(){assertFalse(WidgetPlacement.pageAllowed(true,150,true,true,false,0f,0f,Offset(0f,0f,1f,0f,0,0),page))}
    @Test fun changedPageCountHidden(){assertFalse(WidgetPlacement.pageAllowed(true,150,true,true,false,0f,0f,page.copy(xStep=.5f),page))}
    @Test fun lockedHidden(){assertFalse(WidgetPlacement.pageAllowed(true,150,true,true,true,0f,0f,page,page))}
    @Test fun previewHidden(){assertFalse(WidgetPlacement.pageAllowed(true,150,true,false,false,0f,0f,page,page))}
    @Test fun transitionHidden(){assertFalse(WidgetPlacement.pageAllowed(true,149,true,true,false,0f,0f,page,page))}
    @Test fun changedProfileHidden(){assertFalse(WidgetPlacement.pageAllowed(false,150,true,true,false,0f,0f,page,page))}
    @Test fun zoomHidden(){assertFalse(WidgetPlacement.pageAllowed(true,150,true,true,false,.1f,0f,page,page))}
    @Test fun unavailablePageHidden(){assertFalse(WidgetPlacement.pageAllowed(true,150,true,true,false,0f,0f,null,page))}
    @Test fun slideModeShiftsBoxHorizontallyDuringSwipeWithZeroSettleDelay(){
        val target=Offset(0.5f,0f,0.1f,0f,-500,0)
        val midSwipe=Offset(0.55f,0f,0.1f,0f,-550,0) // 0.5 page right -> widget shifts -500px left
        val shifted=WidgetPlacement.liveBox(Box(200f,100f,800f,500f),1000,2000,true,0L,true,true,false,0f,0f,midSwipe,target,"SLIDE")
        assertNotNull(shifted)
        assertEquals(-300f,shifted!!.left,1f)
        assertEquals(300f,shifted.right,1f)
    }
    @Test fun instantModeAppearsImmediatelyWithZeroSettleDelay(){
        val target=Offset(0.5f,0f,0.1f,0f,-500,0)
        val box=Box(200f,100f,800f,500f)
        assertEquals(box,WidgetPlacement.liveBox(box,1000,2000,true,0L,true,true,false,0f,0f,target,target,"INSTANT"))
    }
    @Test fun alwaysModeRemainsVisibleAcrossPages(){
        val target=Offset(0.5f,0f,0.1f,0f,-500,0)
        val otherPage=Offset(0.9f,0f,0.1f,0f,-900,0)
        val box=Box(200f,100f,800f,500f)
        assertEquals(box,WidgetPlacement.liveBox(box,1000,2000,true,0L,true,true,false,0f,0f,otherPage,target,"ALWAYS"))
    }
}
