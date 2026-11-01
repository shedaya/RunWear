package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RW-16.15 (increment) — the copy contracts for the two states the
 * field recording proved missing:
 *
 * - the save confirmation: after "Save photos" the flow used to
 *   vanish in silence and the user concluded nothing had saved.
 *   C2J is the explicit confirmation, verbatim-identical to the
 *   iOS deck (MakeItMeCopy.saveConfirmed) — both platforms gained
 *   it in this lane; neither had it before.
 * - the Your Photos screen-level load failure: the bounded load
 *   lands on S18 with an S19 retry, never a long spinner into
 *   blank tiles.
 */
class SaveFlowCopyTest {

    @Test
    fun `save confirmation string is the deck C2J`() {
        assertEquals("Your photos are saved.", MakeItMeStrings.C2J_SAVE_CONFIRMED)
    }

    @Test
    fun `photos load failure strings are the deck S18 and S19`() {
        assertEquals("Couldn't load your photos", MakeItMeStrings.S18_PHOTOS_LOAD_FAILED)
        assertEquals("Try again", MakeItMeStrings.S19_TRY_AGAIN)
    }
}
