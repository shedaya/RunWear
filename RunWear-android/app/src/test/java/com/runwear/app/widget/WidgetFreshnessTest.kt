package com.runwear.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Freshness copy is the widget's honesty contract (spec §B.3.5): stale data
 * must SAY it's stale, in the exact forms the layouts render.
 */
class WidgetFreshnessTest {

    private val savedEpoch = 1_000_000_000_000L

    @Test
    fun `age never goes negative`() {
        assertEquals(0L, WidgetFreshness.ageMs(savedEpoch, savedEpoch - 5_000))
    }

    @Test
    fun `stale threshold is the 60 minute cadence target`() {
        assertFalse(WidgetFreshness.isStale(savedEpoch, savedEpoch + 59 * 60_000))
        assertTrue(WidgetFreshness.isStale(savedEpoch, savedEpoch + 61 * 60_000))
    }

    @Test
    fun `eyebrow is clean while fresh and gains the age when stale`() {
        assertEquals("FOR NOW", WidgetFreshness.forNowEyebrow(savedEpoch, savedEpoch + 10 * 60_000))
        assertEquals(
            "FOR NOW · 2H AGO",
            WidgetFreshness.forNowEyebrow(savedEpoch, savedEpoch + 125 * 60_000)
        )
        assertEquals(
            "FOR NOW · 3D AGO",
            WidgetFreshness.forNowEyebrow(savedEpoch, savedEpoch + 3 * 24 * 60 * 60_000L)
        )
    }

    @Test
    fun `large label is a clock time while recent, an age past 90 minutes`() {
        val savedLocal = LocalDateTime.of(2026, 10, 22, 7, 5)
        assertEquals(
            "Updated 7:05 AM",
            WidgetFreshness.updatedLabel(savedLocal, savedEpoch, savedEpoch + 45 * 60_000)
        )
        assertEquals(
            "Updated 3h ago",
            WidgetFreshness.updatedLabel(savedLocal, savedEpoch, savedEpoch + 190 * 60_000)
        )
    }

    @Test
    fun `age words cover the rendered forms`() {
        assertEquals("just now", WidgetFreshness.ageWords(30_000))
        assertEquals("25 min ago", WidgetFreshness.ageWords(25 * 60_000))
        assertEquals("1h ago", WidgetFreshness.ageWords(115 * 60_000))
        assertEquals("2d ago", WidgetFreshness.ageWords(2 * 24 * 60 * 60_000L))
    }

    @Test
    fun `refresh flag expires so a dead process cannot wedge the spinner`() {
        val now = savedEpoch + 10_000
        assertTrue(WidgetFreshness.isRefreshActive(now - 5_000, now))
        assertFalse(WidgetFreshness.isRefreshActive(now - 91_000, now))
        assertFalse(WidgetFreshness.isRefreshActive(null, now))
        assertFalse(WidgetFreshness.isRefreshActive(0L, now))
    }

    @Test
    fun `error categories carry plain-words reasons`() {
        assertEquals("Location isn't available to the widget", WidgetErrorCategory.LOCATION.reason)
        assertEquals("Couldn't reach the weather service", WidgetErrorCategory.WEATHER.reason)
    }
}
