package com.runwear.app.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 1.2.0 upgrade-freshness decision (field regression, Oct 27 2026):
 * a widget render restored across an app upgrade belongs to the old
 * build until a recompose is forced — force it exactly when the running
 * version is strictly newer than the last one that forced it.
 */
class WidgetUpgradeTest {

    @Test
    fun `fresh install stamps and forces`() {
        assertTrue(WidgetUpgrade.shouldForceRefresh(0, 14))
    }

    @Test
    fun `upgrade forces`() {
        assertTrue(WidgetUpgrade.shouldForceRefresh(13, 14))
    }

    @Test
    fun `same version does not force again`() {
        assertFalse(WidgetUpgrade.shouldForceRefresh(14, 14))
    }

    @Test
    fun `older build never forces`() {
        assertFalse(WidgetUpgrade.shouldForceRefresh(14, 13))
    }
}
