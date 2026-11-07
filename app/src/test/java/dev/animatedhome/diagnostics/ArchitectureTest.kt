package dev.animatedhome.diagnostics

import org.junit.Assert.*
import org.junit.Test

class ArchitectureTest {
    private fun at(x: Float)=Offset(x,.5f,.5f,0f,0,0)
    private fun survey(xs: List<Float> = listOf(0f,.5f,1f)) = listOf("A","B","C").flatMapIndexed { n,p ->
        (1..2).map { Visit(p,it,"$p$it","same-environment",at(xs[n]),true) }
    }
    @Test fun validBounds() { assertTrue(Box(10f,20f,400f,500f).valid(1080,2400)) }
    @Test fun emptyBoundsRejected() { assertFalse(Box(10f,20f,10f,500f).valid(1080,2400)) }
    @Test fun offscreenBoundsRejected() { assertFalse(Box(-1f,20f,400f,500f).valid(1080,2400)) }
    @Test fun rotationMismatchBoundsRejected() { assertFalse(Box(10f,20f,400f,2200f).valid(2400,1080)) }
    @Test fun nonFiniteBoundsRejected() { assertFalse(Box(Float.NaN,20f,400f,500f).valid(1080,2400)) }
    @Test fun sixIndependentRepeatableVisitsOnlyBecomeCandidate() { assertEquals("CANDIDATE",PageEvidence.assess(survey()).code) }
    @Test fun noSamplesNeverPass() { assertEquals("NOT_YET_TESTED",PageEvidence.assess(emptyList()).code) }
    @Test fun singleSwipeSamplesNeverProveThreePages() { assertEquals("NOT_YET_TESTED",PageEvidence.assess(survey().filter { it.round==1 }).code) }
    @Test fun constantOffsetsUnsupported() { assertEquals("UNSUPPORTED",PageEvidence.assess(survey(listOf(.5f,.5f,.5f))).code) }
    @Test fun twoIndistinguishablePagesUnsupported() { assertEquals("UNSUPPORTED",PageEvidence.assess(survey(listOf(0f,.5f,.5f))).code) }
    @Test fun nullOffsetsUnsupportedAfterCompletedSurvey() { assertEquals("UNSUPPORTED",PageEvidence.assess(survey().map { it.copy(offset=null) }).code) }
    @Test fun aReusedTapIsNotNewEvidence() { assertEquals("INCONCLUSIVE",PageEvidence.assess(survey().map { it.copy(tap="one-tap") }).code) }
    @Test fun staleEngineSurveyRejected() { val s=survey().toMutableList(); s[1]=s[1].copy(environment="restarted-engine"); assertEquals("INCONCLUSIVE",PageEvidence.assess(s).code) }
    @Test fun movingSamplesAreNotPageEvidence() { assertEquals("INCONCLUSIVE",PageEvidence.assess(survey().map { it.copy(stable=false) }).code) }
    @Test fun nonrepeatablePageRejected() { val s=survey().toMutableList(); s[1]=s[1].copy(offset=at(.1f)); assertEquals("INCONCLUSIVE",PageEvidence.assess(s).code) }
    @Test fun zeroStepsCanStillHaveDistinctSignatures() { assertEquals("CANDIDATE",PageEvidence.assess(survey().map { it.copy(offset=it.offset!!.copy(xStep=0f)) }).code) }
    @Test fun pixelsCanDistinguishPages() { val s=survey(listOf(.5f,.5f,.5f)).map { it.copy(offset=it.offset!!.copy(px=when(it.page){ "A"->0; "B"-> -100; else-> -200 })) }; assertEquals("CANDIDATE",PageEvidence.assess(s).code) }
    @Test fun nanOffsetRejected() { assertFalse(at(Float.NaN).valid()) }
    @Test fun pixelDistanceDoesNotOverflow() { assertFalse(at(0f).copy(px=Int.MAX_VALUE).near(at(0f).copy(px=Int.MIN_VALUE))) }
    @Test fun fullDisplayCoordinatesPreserveStatusBarOffset() { assertEquals(Box(20f,100f,200f,400f),Mapping.toSurface(Box(20f,100f,200f,400f),1080,2400,1080,2400,null,"CENTER",0f,0f,1f)) }
    @Test fun centerCropAddsHalfExtraSurface() { assertEquals(120f,Mapping.toSurface(Box(20f,100f,200f,400f),1080,2400,1280,2600,null,"CENTER",0f,0f,1f).left,0f) }
    @Test fun offsetCropIsExplicitModel() { assertEquals(220f,Mapping.toSurface(Box(20f,100f,200f,400f),1080,2400,1280,2400,at(1f),"OFFSET",0f,0f,1f).left,0f) }
    @Test fun manualTranslationAppliedOnce() { assertEquals(25f,Mapping.toSurface(Box(20f,100f,200f,400f),1080,2400,1080,2400,null,"ORIGIN",5f,-5f,1f).left,0f) }
    @Test fun inverseZoomScalesAroundScreenCentre() { assertEquals(25f,Mapping.toSurface(Box(0f,0f,100f,100f),100,100,100,100,null,"ORIGIN",0f,0f,2f).left,0f) }
    @Test(expected=IllegalArgumentException::class) fun invalidScaleRejected() { Mapping.toSurface(Box(0f,0f,100f,100f),100,100,100,100,null,"CENTER",0f,0f,0f) }
    private fun gate(verdict: String="CANDIDATE",same: Boolean=true,stable: Long=500,visible: Boolean=true,
                     preview: Boolean=false,locked: Boolean=false,zoom: Float=0f,current: Offset?=at(.5f),target: Offset?=at(.5f))=
        RenderGate.pageAllowed(verdict,same,stable,visible,preview,locked,zoom,0f,current,target)
    @Test fun matchingSettledPageAllowsDiagnostic() { assertTrue(gate()) }
    @Test fun anotherPageHidesRectangle() { assertFalse(gate(current=at(1f))) }
    @Test fun transitionHidesRectangle() { assertFalse(gate(stable=499)) }
    @Test fun unsupportedLauncherHidesRectangle() { assertFalse(gate(verdict="UNSUPPORTED")) }
    @Test fun previewNeverUsesHomeCalibration() { assertFalse(gate(preview=true)) }
    @Test fun hiddenWallpaperDoesNotRenderRectangle() { assertFalse(gate(visible=false)) }
    @Test fun keyguardNeverRendersRectangle() { assertFalse(gate(locked=true)) }
    @Test fun staleEnvironmentHidesRectangle() { assertFalse(gate(same=false)) }
    @Test fun changedZoomHidesRectangle() { assertFalse(gate(zoom=.5f)) }
    @Test fun missingTelemetryHidesRectangle() { assertFalse(gate(current=null)) }
}
