package com.runwear.app.weariverun

/**
 * GEN-5 (RW-28 sharing fix): the subject-aware crop for share
 * surfaces. Sol's field evidence (Nov 5, 2026): the Morning
 * Poster rendered the runner HEADLESS — the entry's portrait
 * photo was CENTER-cropped into the poster's box, and the centre
 * band of a runner portrait is the torso: head above the window,
 * legs below it.
 *
 * The rule: share crops anchor to the TOP of the source (and
 * centre horizontally). A runner's head and face live in the
 * source's top band; the poster's own caption lockup covers the
 * bottom of the frame anyway, so the bottom is what a crop may
 * spend. This is the same anchoring principle the C-2 fork band
 * uses, applied to the poster, the kit surfaces and the small
 * collection thumbnails.
 *
 * The window math is pure so the regression test can prove —
 * against the evidence's exact shape — that the head zone is
 * inside the top-anchored window and outside the centre window.
 */
object ShareCropPolicy {

    /** The source's head zone: the top 30% of the frame. */
    const val HEAD_ZONE_FRACTION = 0.30f

    data class Window(val x: Float, val y: Float, val width: Float, val height: Float) {
        val bottom: Float get() = y + height
    }

    /**
     * The visible source window for a crop fill of a
     * [dstW]×[dstH] target from a [srcW]×[srcH] source, anchored
     * to the top edge and centred horizontally.
     */
    fun visibleSourceWindow(srcW: Float, srcH: Float, dstW: Float, dstH: Float): Window {
        if (srcW <= 0f || srcH <= 0f || dstW <= 0f || dstH <= 0f) {
            return Window(0f, 0f, srcW, srcH)
        }
        val scale = maxOf(dstW / srcW, dstH / srcH)
        val visibleW = dstW / scale
        val visibleH = dstH / scale
        return Window(
            x = (srcW - visibleW) / 2f,
            y = 0f,
            width = visibleW,
            height = visibleH
        )
    }

    /** True when the source's whole head zone is inside [window]. */
    fun headZoneFullyVisible(srcH: Float, window: Window): Boolean {
        val headBottom = srcH * HEAD_ZONE_FRACTION
        return window.y <= 0f && window.bottom >= headBottom
    }
}
