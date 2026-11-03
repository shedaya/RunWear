package com.runwear.app.feedback

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * RW-18 (RW-26 client half): automated submissions file under their
 * OWN categories — diagnostics / crash_report — never the human
 * something_wrong; the human categories keep their ids untouched.
 */
class FeedbackRoutingTest {

    @Test
    fun `automated category ids are diagnostics and crash_report`() {
        assertEquals("diagnostics", AutomatedReportCategory.DIAGNOSTICS.id)
        assertEquals("crash_report", AutomatedReportCategory.CRASH_REPORT.id)
        assertEquals(
            "diagnostics",
            FeedbackRouting.automatedCategoryId(AutomatedReportCategory.DIAGNOSTICS)
        )
    }

    @Test
    fun `human categories are unchanged and exclude the automated ids`() {
        val humanIds = FeedbackCategory.entries.map { it.id }
        assertEquals(
            listOf("something_wrong", "recommendation_off", "confusing", "idea", "other"),
            humanIds
        )
        assertFalse("diagnostics" in humanIds)
        assertFalse("crash_report" in humanIds)
    }

    @Test
    fun `an automated payload carries the diagnostics category, not something_wrong`() {
        val payload = TelemetryPayloads.feedback(
            appVersion = "1.2.15",
            installId = "test-install",
            categoryId = FeedbackRouting.automatedCategoryId(AutomatedReportCategory.DIAGNOSTICS),
            text = "[SAFE-BOOT DIAGNOSTIC] trace",
            replyEmail = null,
            entryPoint = null,
            bundle = JSONObject()
        )
        assertEquals("diagnostics", payload.getString("category"))
    }

    @Test
    fun `a human payload still carries its human category`() {
        val payload = TelemetryPayloads.feedback(
            appVersion = "1.2.15",
            installId = "test-install",
            category = FeedbackCategory.SOMETHING_WRONG,
            text = "the outfit was wrong",
            replyEmail = null,
            entryPoint = "settings",
            bundle = JSONObject()
        )
        assertEquals("something_wrong", payload.getString("category"))
    }
}
