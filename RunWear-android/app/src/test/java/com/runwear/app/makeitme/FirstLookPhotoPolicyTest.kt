package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-177 (2026-11-25): the first-look moment's photo resolution.
 * While the personal photo is pending, the moment paints the
 * first-look CELL'S OWN approved generic image — not whatever the
 * home scene happens to hold (in the incident, nothing: the
 * moment was raised from the onboarding upload flow before any
 * scene resolution landed, and the screen stood blank).
 */
class FirstLookPhotoPolicyTest {

    @Test
    fun `the personal photo wins when it is the displayed one`() {
        assertEquals(
            "personal",
            FirstLookPhotoPolicy.photoToShow("personal", "generic", "scene")
        )
    }

    @Test
    fun `while pending, the cell's generic beats the scene resolution`() {
        assertEquals(
            "generic",
            FirstLookPhotoPolicy.photoToShow(null, "generic", "scene")
        )
    }

    @Test
    fun `the scene resolution is the last resort`() {
        assertEquals(
            "scene",
            FirstLookPhotoPolicy.photoToShow(null, null, "scene")
        )
    }

    @Test
    fun `no photo at any tier is null`() {
        assertNull(FirstLookPhotoPolicy.photoToShow(null, null, null))
    }

    @Test
    fun `a generic resolution is owed while pending with none held for this cell`() {
        assertTrue(
            FirstLookPhotoPolicy.shouldResolveGeneric(
                personalPhotoDisplayed = false,
                genericPhotoCell = null,
                currentCell = "MALE_CLOUDY_COOL_DUSK_84c10525"
            )
        )
        // A generic held for a DIFFERENT cell does not discharge
        // this cell's resolution.
        assertTrue(
            FirstLookPhotoPolicy.shouldResolveGeneric(
                personalPhotoDisplayed = false,
                genericPhotoCell = "MALE_CLEAR_MILD_DAWN_491df101",
                currentCell = "MALE_CLOUDY_COOL_DUSK_84c10525"
            )
        )
    }

    @Test
    fun `no generic resolution is owed once this cell's generic is held`() {
        assertFalse(
            FirstLookPhotoPolicy.shouldResolveGeneric(
                personalPhotoDisplayed = false,
                genericPhotoCell = "MALE_CLOUDY_COOL_DUSK_84c10525",
                currentCell = "MALE_CLOUDY_COOL_DUSK_84c10525"
            )
        )
    }

    @Test
    fun `no generic resolution is owed when the personal photo is displayed`() {
        assertFalse(
            FirstLookPhotoPolicy.shouldResolveGeneric(
                personalPhotoDisplayed = true,
                genericPhotoCell = null,
                currentCell = "MALE_CLOUDY_COOL_DUSK_84c10525"
            )
        )
    }
}
