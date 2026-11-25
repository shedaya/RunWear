package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-16.30 (privacy decision 5a) — the Delete my data copy contract.
 *
 * The account-level erasure's strings (S20–S25) must keep the scope
 * discipline the photos deletion set (S12): name what goes (server +
 * phone + the account itself), state the no-undo fact, and never
 * imply that deleting data cancels a store subscription — the one
 * money fact a user could otherwise learn the hard way.
 */
class DeleteMyDataStringsTest {

    @Test
    fun `confirm body names the account and the no-undo fact`() {
        assertTrue(MakeItMeStrings.S22_DELETE_DATA_CONFIRM_BODY.contains("your account itself"))
        assertTrue(MakeItMeStrings.S22_DELETE_DATA_CONFIRM_BODY.contains("This can't be undone."))
        assertTrue(MakeItMeStrings.S22_DELETE_DATA_CONFIRM_BODY.contains("from our server and this phone"))
    }

    @Test
    fun `confirm body states that deleting data does not cancel a subscription`() {
        assertTrue(
            MakeItMeStrings.S22_DELETE_DATA_CONFIRM_BODY
                .contains("Deleting your data does not cancel a subscription")
        )
    }

    @Test
    fun `destructive action is labeled with the act itself, never a bare yes`() {
        assertEquals("Delete my data", MakeItMeStrings.S20_DELETE_DATA_ROW)
        assertEquals("Delete my data", MakeItMeStrings.S23_DELETE_DATA_CONFIRM)
        assertFalse(MakeItMeStrings.S23_DELETE_DATA_CONFIRM.equals("Yes", ignoreCase = true))
    }

    @Test
    fun `post-delete line claims only the confirmed end state`() {
        assertEquals("Your data and account are deleted.", MakeItMeStrings.S25_POST_DELETE_DATA)
    }
}
