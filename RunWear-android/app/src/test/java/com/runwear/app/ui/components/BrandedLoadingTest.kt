package com.runwear.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The branded startup loading state must say what the load is actually
 * waiting for — that line is the difference between "the app is working"
 * and the old near-black shimmer that read as a hang. These tests pin the
 * three phases of the first-run chain (permission/fix, naming, forecast).
 */
class BrandedLoadingTest {

    @Test
    fun `no permission yet reads as getting location`() {
        assertEquals(
            "Getting your location…",
            loadingStatusText(hasLocationPermission = false, locationName = "Getting location...")
        )
    }

    @Test
    fun `permission granted but fix not named yet reads as finding location`() {
        assertEquals(
            "Finding your location…",
            loadingStatusText(hasLocationPermission = true, locationName = "Getting location...")
        )
    }

    @Test
    fun `named location reads as loading the forecast`() {
        assertEquals(
            "Loading your forecast…",
            loadingStatusText(hasLocationPermission = true, locationName = "New York, New York")
        )
    }
}
