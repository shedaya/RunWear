package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-16.14 — the reference-photo intake grows a second source
 * (the camera, beside the library) and a guidance line (one
 * full-body photo in plain athletic wear). These tests pin the
 * contracts the lane is built on:
 *
 * - [PhotoIntakePolicy]: the source-routing matrix is total and
 *   honest — the camera when one can be launched, the library
 *   otherwise, WITH the reason carried in the route; no state
 *   routes to a dead end. A capture enters the identical intake
 *   path as a library pick by construction (the TakePicture result
 *   is a PickedPhoto Uri feeding the same PhotoQualityChecker
 *   effect — pinned in code review, exercised on device), so the
 *   routing + copy contracts are what unit tests can prove.
 * - The guidance copy carries Sol's ruled elements verbatim — the
 *   positive lead, the full-body ask, and the solo-frame note that
 *   keeps users out of the multiple-people rejection — identical
 *   to the iOS deck.
 */
class PhotoIntakePolicyTest {

    @Test
    fun `library request always routes to library`() {
        assertEquals(
            PhotoIntakePolicy.Route.Library,
            PhotoIntakePolicy.route(PhotoIntakePolicy.Source.LIBRARY, cameraAvailable = true)
        )
        assertEquals(
            PhotoIntakePolicy.Route.Library,
            PhotoIntakePolicy.route(PhotoIntakePolicy.Source.LIBRARY, cameraAvailable = false)
        )
    }

    @Test
    fun `camera request routes to camera when one can be launched`() {
        assertEquals(
            PhotoIntakePolicy.Route.Camera,
            PhotoIntakePolicy.route(PhotoIntakePolicy.Source.CAMERA, cameraAvailable = true)
        )
    }

    @Test
    fun `camera request falls back to library with the honest note`() {
        assertEquals(
            PhotoIntakePolicy.Route.LibraryWithNote(
                "No camera is available here — choose from your library instead."
            ),
            PhotoIntakePolicy.route(PhotoIntakePolicy.Source.CAMERA, cameraAvailable = false)
        )
    }

    @Test
    fun `full body guidance carries the ruled elements`() {
        val guidance = MakeItMeStrings.C2D_FULL_BODY_GUIDANCE
        assertTrue(
            "the guidance leads with Sol's positive line",
            guidance.startsWith("Photos of you running work best.")
        )
        assertTrue(
            "the structural ask: one full-body photo in plain athletic wear",
            guidance.contains("one full-body photo in plain athletic wear")
        )
        assertTrue(
            "the solo-frame note that keeps race photos out of the C3b rejection",
            guidance.contains("just you in the frame")
        )
        assertTrue(
            "the reason names the full-length outfits it rescues",
            guidance.contains("tights and jackets")
        )
        assertTrue(
            "the multiple-people limit is stated before it can reject anyone",
            guidance.contains("Photos with other people in them can't be used.")
        )
    }
}
