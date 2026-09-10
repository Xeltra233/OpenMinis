package com.openminis.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope

/**
 * Zoom / pan state of a fullscreen image viewer, shared by [FullscreenImageViewer]
 * and [ImageGalleryViewer.GalleryPage] so the gesture maths can only ever be
 * fixed in one place.
 *
 * The maths is not obvious, and both viewers shipped the same bug:
 *
 *  - The `pan` delta from `detectTransformGestures` is delivered in the
 *    element's LOCAL pixel space. The `Modifier.graphicsLayer(scaleX = scale)`
 *    that draws the image sits OUTSIDE the `pointerInput`, so the pointer
 *    pipeline inverts that layer transform before the gesture handler sees it:
 *    at `scale = 2.5`, a 300 px finger travel arrives as 120 px.
 *  - `translationX` / `translationY` inside that same `graphicsLayer` are
 *    applied in the PARENT's pixel space — the scale does not multiply them.
 *
 * Adding the raw local delta therefore moved the image at `1 / scale` of the
 * finger speed: with a 2.5x zoom the image crawled at 40% of the drag, measured
 * on device as 110 px of travel for a 300 px swipe. The delta has to be
 * multiplied by the CURRENT scale to stay glued to the finger.
 */
data class ZoomPanTransform(
    val scale: Float = MIN_SCALE,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    companion object {
        /** Fully zoomed out — the image is fitted and centred. */
        const val MIN_SCALE = 1f

        /** Pinch ceiling, matches the long-standing viewer behaviour. */
        const val MAX_SCALE = 8f

        /** Scale a double tap magnifies to. */
        const val DOUBLE_TAP_SCALE = 2.5f
    }

    /** True while magnified, i.e. while panning is meaningful. */
    val isZoomed: Boolean get() = scale > MIN_SCALE

    /**
     * Apply one `detectTransformGestures` sample.
     *
     * [panX] / [panY] are the LOCAL delta reported by the detector; [zoom] is
     * its relative zoom factor for this sample. Returns the state to draw next.
     */
    fun gestureBy(panX: Float, panY: Float, zoom: Float): ZoomPanTransform {
        val nextScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        if (nextScale <= MIN_SCALE) {
            // Zoomed all the way out: stay centred instead of remembering an
            // offset that would pop back the next time the image is magnified.
            return ZoomPanTransform(scale = nextScale)
        }
        // Compensate with the CURRENT scale: that is the transform the pointer
        // pipeline inverted to produce this delta. (Using `nextScale` would be
        // off by one frame's worth of zoom, which is invisible but wrong.)
        return ZoomPanTransform(
            scale = nextScale,
            offsetX = offsetX + panX * scale,
            offsetY = offsetY + panY * scale,
        )
    }

    /**
     * Double tap: magnify from the fitted state, and reset back to it when the
     * image is already magnified.
     */
    fun toggledByDoubleTap(): ZoomPanTransform =
        if (isZoomed) ZoomPanTransform() else ZoomPanTransform(scale = DOUBLE_TAP_SCALE)
}

/**
 * Transform-gesture detector for the image viewers, with one behaviour that
 * `detectTransformGestures` cannot express: a bare single-finger drag on a
 * FITTED image is not ours to take.
 *
 * The framework detector consumes every sample once it passes touch slop,
 * regardless of what the callback does with it. Nesting it inside a
 * `HorizontalPager` therefore killed swipe-to-next-image completely: measured on
 * device, a fitted page could not be paged at all, and while magnified the pager
 * was blocked by design. Here the sample is only consumed once the viewer
 * actually owns the gesture — magnified panning, or any two-finger gesture
 * (pinch to zoom, which is how a fitted image becomes magnified in the first
 * place). Anything else is left unconsumed so the pager can page.
 *
 * [panX]/[panY] are the raw local deltas described on [ZoomPanTransform], and
 * [zoom] is the relative zoom factor of the sample.
 */
internal suspend fun PointerInputScope.detectViewerGestures(
    isZoomed: () -> Boolean,
    onGesture: (panX: Float, panY: Float, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val touchSlop = viewConfiguration.touchSlop
        var travel = Offset.Zero
        var pastSlop = false
        var claimed = false
        var pressed = true
        while (pressed) {
            val event = awaitPointerEvent()
            // Someone above us (the pager) already owns this gesture.
            if (event.changes.any { it.isConsumed }) return@awaitEachGesture

            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()
            if (!pastSlop) {
                travel += panChange
                pastSlop = travel.getDistance() > touchSlop || zoomChange != 1f
            }
            if (pastSlop) {
                val multiTouch = event.changes.count { it.pressed } > 1
                if (claimed || multiTouch || zoomChange != 1f || isZoomed()) {
                    claimed = true
                    if (panChange != Offset.Zero || zoomChange != 1f) {
                        onGesture(panChange.x, panChange.y, zoomChange)
                    }
                    event.changes.forEach { change ->
                        if (change.position != change.previousPosition) change.consume()
                    }
                } else {
                    // Single finger on a fitted image: give the gesture up
                    // without consuming so the pager can change pages.
                    return@awaitEachGesture
                }
            }
            pressed = event.changes.any { it.pressed }
        }
    }
}
