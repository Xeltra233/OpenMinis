package com.openminis.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the image-viewer drag speed bug: with the image zoomed,
 * the pan delta the gesture detector reports is in the layer's local (pre-scale)
 * space, so it must be multiplied by the current scale before it is added to
 * `translationX/Y` — otherwise the image drags at 1/scale of the finger speed
 * (measured on device: 110 px of travel for a 300 px swipe at 2.5x).
 */
class ZoomPanTransformTest {

    @Test
    fun fittedImage_ignoresDragAndStaysCentred() {
        val result = ZoomPanTransform().gestureBy(panX = 120f, panY = -40f, zoom = 1f)

        assertEquals(1f, result.scale, 0f)
        assertEquals(0f, result.offsetX, 0f)
        assertEquals(0f, result.offsetY, 0f)
        assertFalse(result.isZoomed)
    }

    @Test
    fun dragAtNativeScale_isOneToOne() {
        // Pinch out to 2x first, then drag there.
        val zoomed = ZoomPanTransform().gestureBy(0f, 0f, 2f)
        val dragged = zoomed.gestureBy(panX = 100f, panY = 50f, zoom = 1f)

        // Local delta 100 px at scale 2 == 200 px of screen travel.
        assertEquals(200f, dragged.offsetX, 0.001f)
        assertEquals(100f, dragged.offsetY, 0.001f)
    }

    @Test
    fun dragAtDoubleTapScale_matchesTheMeasuredBugScenario() {
        val zoomed = ZoomPanTransform().toggledByDoubleTap()
        assertEquals(2.5f, zoomed.scale, 0f)

        // The device scenario: a 300 px swipe arrives as 120 px of local delta.
        val dragged = zoomed.gestureBy(panX = 120f, panY = 0f, zoom = 1f)

        // The fix restores the full 300 px of finger travel.
        assertEquals(300f, dragged.offsetX, 0.001f)
        assertEquals(2.5f, dragged.scale, 0f)
    }

    @Test
    fun dragAtMaxScale_keepsOneToOneCompensation() {
        val maxed = ZoomPanTransform(scale = ZoomPanTransform.MAX_SCALE)
        val dragged = maxed.gestureBy(panX = 10f, panY = 10f, zoom = 1.5f)

        assertEquals(ZoomPanTransform.MAX_SCALE, dragged.scale, 0f)
        assertEquals(80f, dragged.offsetX, 0.001f)
        assertEquals(80f, dragged.offsetY, 0.001f)
    }

    @Test
    fun zoomingBackOut_recentresTheImage() {
        val zoomed = ZoomPanTransform().gestureBy(0f, 0f, 4f).gestureBy(200f, 200f, 1f)
        assertTrue(zoomed.isZoomed)

        val zoomedOut = zoomed.gestureBy(0f, 0f, 0.1f)

        assertEquals(1f, zoomedOut.scale, 0f)
        assertEquals(0f, zoomedOut.offsetX, 0f)
        assertEquals(0f, zoomedOut.offsetY, 0f)
    }

    @Test
    fun pinchAndDragInTheSameSample_appliesBoth() {
        val result = ZoomPanTransform().gestureBy(panX = 30f, panY = 0f, zoom = 2f)

        assertEquals(2f, result.scale, 0f)
        // Compensated with the scale in effect when the delta was measured.
        assertEquals(30f, result.offsetX, 0.001f)
    }

    @Test
    fun doubleTap_togglesBetweenFittedAndMagnified() {
        val magnified = ZoomPanTransform().toggledByDoubleTap()
        assertEquals(ZoomPanTransform.DOUBLE_TAP_SCALE, magnified.scale, 0f)
        assertTrue(magnified.isZoomed)

        val withOffset = magnified.gestureBy(100f, 100f, 1f)
        val reset = withOffset.toggledByDoubleTap()
        assertEquals(1f, reset.scale, 0f)
        assertEquals(0f, reset.offsetX, 0f)
        assertEquals(0f, reset.offsetY, 0f)
        assertFalse(reset.isZoomed)
    }
}
