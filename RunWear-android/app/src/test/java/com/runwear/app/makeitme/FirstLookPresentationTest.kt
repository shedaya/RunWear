package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-3 A-HERO (WS-B, X-1/X-2): the first-look moment's presentation
 * states. The gen-2 defect: the moment claimed its finished state
 * ("You're in the photo now" + the look counter) off the upload
 * flow's completion alone, over a stock photo or no photo, and the
 * wait itself was a bare greyed-button spinner with no label. The
 * seam's rule: finished claims exist ONLY when the server says the
 * look is ready AND the generated photo is the one displayed.
 */
class FirstLookPresentationTest {

    @Test
    fun `ready and displayed is the only finished state`() {
        assertEquals(
            FirstLookState.READY,
            FirstLookPresentation.resolve(
                PersonalLookStatus.Ready, personalPhotoDisplayed = true
            )
        )
    }

    @Test
    fun `ready reported before the photo lands is still making`() {
        // The server folded state can flip to Ready a beat before
        // the photo is the one on screen: claiming "You're in the
        // photo now" over the fallback would be the X-1 defect.
        assertEquals(
            FirstLookState.MAKING,
            FirstLookPresentation.resolve(
                PersonalLookStatus.Ready, personalPhotoDisplayed = false
            )
        )
    }

    @Test
    fun `making and no-status are making`() {
        assertEquals(
            FirstLookState.MAKING,
            FirstLookPresentation.resolve(
                PersonalLookStatus.Making, personalPhotoDisplayed = false
            )
        )
        assertEquals(
            FirstLookState.MAKING,
            FirstLookPresentation.resolve(
                PersonalLookStatus.None, personalPhotoDisplayed = false
            )
        )
    }

    @Test
    fun `failure is failed regardless of what is displayed`() {
        assertEquals(
            FirstLookState.FAILED,
            FirstLookPresentation.resolve(
                PersonalLookStatus.Failed(PersonalLookStatus.Reason.ERROR),
                personalPhotoDisplayed = false
            )
        )
        assertEquals(
            FirstLookState.FAILED,
            FirstLookPresentation.resolve(
                PersonalLookStatus.Failed(
                    PersonalLookStatus.Reason.QUALITY_REFUSED
                ),
                personalPhotoDisplayed = true
            )
        )
    }

    @Test
    fun `invariant (d) - no stock photo ever sits under the finished claim, even transiently`() {
        // The iOS sequence: claim printed over an empty band, then a
        // STOCK photo faded in under it for ~0.6 s before the user's
        // own photo cut in. Here the claim and the photo are one
        // derivation: whenever the photo on screen is not the user's
        // own delivered photo (personalPhotoDisplayed = false — the
        // serving photo is stock or the fallback), the state is
        // MAKING and no finished claim can render over it, at any
        // instant of the transition.
        val underStock = FirstLookPresentation.resolve(
            PersonalLookStatus.Ready, personalPhotoDisplayed = false
        )
        assertEquals(FirstLookState.MAKING, underStock)
        assertFalse(FirstLookPresentation.showsFinishedClaims(underStock))
        // The personal photo displayed is the ONLY road to READY.
        assertEquals(
            FirstLookState.READY,
            FirstLookPresentation.resolve(
                PersonalLookStatus.Ready, personalPhotoDisplayed = true
            )
        )
    }

    @Test
    fun `finished claims and the premium band hang off ready alone`() {
        assertTrue(FirstLookPresentation.showsFinishedClaims(FirstLookState.READY))
        assertFalse(FirstLookPresentation.showsFinishedClaims(FirstLookState.MAKING))
        assertFalse(FirstLookPresentation.showsFinishedClaims(FirstLookState.FAILED))
        assertTrue(FirstLookPresentation.showsPremiumBand(FirstLookState.READY))
        assertFalse(FirstLookPresentation.showsPremiumBand(FirstLookState.MAKING))
        assertFalse(FirstLookPresentation.showsPremiumBand(FirstLookState.FAILED))
    }
}
