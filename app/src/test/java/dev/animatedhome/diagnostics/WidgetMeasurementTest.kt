package dev.animatedhome.diagnostics

import org.junit.Assert.*
import org.junit.Test

class WidgetMeasurementTest {
    private val root=Box(299f,522f,1263f,1150f)
    private fun corner(sx:Float,sy:Float=sx)=Box(299+796*sx,522+460*sy,299+796*sx+168,522+460*sy+168)
    @Test fun scaledLauncherUsesVisibleSizeInsteadOfLayoutSize() {
        // User's raw 964 x 628 rectangle, with a simulated 0.6944 host scale.
        val b=WidgetMeasurement.measure(root,corner(1/1.44f),1264,2780)!!
        assertEquals(299f,b.left,0f); assertEquals(522f,b.top,0f)
        assertEquals(964/1.44f,b.width,.01f); assertEquals(628/1.44f,b.height,.01f)
    }
    @Test fun standardLauncherKeepsOriginalRectangle() { assertEquals(root,WidgetMeasurement.measure(root,corner(1f),1264,2780)) }
    @Test fun independentAxisScalingIsMeasured() {
        val b=WidgetMeasurement.measure(root,corner(.6f,.8f),1264,2780)!!
        assertEquals(578.4f,b.width,.01f); assertEquals(502.4f,b.height,.01f)
    }
    @Test fun integerScreenRoundingStaysWithinTwoPixels() {
        val b=WidgetMeasurement.measure(root,Box(852f,841f,1020f,1009f),1264,2780)!!
        assertEquals(968.44f,b.right,2f); assertEquals(958.11f,b.bottom,2f)
    }
    @Test fun rawRightCanOverflowWhenVisibleWidgetFits() {
        val b=WidgetMeasurement.measure(Box(700f,500f,1700f,1100f),Box(1116f,716f,1284f,884f),1264,2780)
        assertNotNull(b); assertEquals(1200f,b!!.right,.01f)
    }
    @Test fun correctedOffscreenRectangleIsRejected() { assertNull(WidgetMeasurement.measure(root,corner(1.2f),1264,2780)) }
    @Test fun negativeSourceOriginIsRejected() { assertFalse(WidgetMeasurement.usableSource(Box(-1f,0f,100f,100f),1264,2780)) }
    @Test fun nullSourceIsRejected() { assertFalse(WidgetMeasurement.usableSource(null,1264,2780)) }
    @Test fun emptySourceIsRejected() { assertFalse(WidgetMeasurement.usableSource(Box(1f,1f,1f,1f),1264,2780)) }
    @Test fun nonFiniteSourceIsRejected() { assertNull(WidgetMeasurement.measure(root,Box(Float.NaN,1f,2f,3f),1264,2780)) }
    @Test fun tinyWidgetHasNoReliableBaseline() { assertNull(WidgetMeasurement.measure(Box(0f,0f,180f,180f),Box(12f,12f,180f,180f),1264,2780)) }
    @Test fun cornerBeforeRootIsRejected() { assertNull(WidgetMeasurement.measure(root,Box(280f,500f,448f,668f),1264,2780)) }
    @Test fun implausibleScaleIsRejected() { assertNull(WidgetMeasurement.measure(root,corner(.1f),1264,2780)) }
    @Test fun onePixelRoundingCanReuseButMovementCannot() {
        assertTrue(WidgetMeasurement.same(root,Box(300f,523f,1264f,1151f)))
        assertFalse(WidgetMeasurement.same(root,Box(309f,522f,1273f,1150f)))
        // Xiaomi MIUI/HyperOS press-scale tap jitter (±6–10 px) is absorbed when TAP_JITTER_TOLERANCE is used
        assertTrue(WidgetMeasurement.same(Box(88f,923f,758f,1391f),Box(81f,918f,751f,1386f),WidgetMeasurement.TAP_JITTER_TOLERANCE))
    }
    @Test fun resizeCannotReuseMeasurement() { assertFalse(WidgetMeasurement.same(root,root.copy(right=1200f))) }
    @Test fun reuseKeepsScaleAndTracksOnePixelOriginChange() {
        val b=WidgetMeasurement.measure(root,corner(.7f),1264,2780)!!
        val shifted=WidgetMeasurement.relocate(Box(300f,523f,1264f,1151f),root,b)
        assertEquals(300f,shifted.left,0f); assertEquals(b.width,shifted.width,.01f)
        assertEquals(b.bottom+1,shifted.bottom,.01f)
    }
}
