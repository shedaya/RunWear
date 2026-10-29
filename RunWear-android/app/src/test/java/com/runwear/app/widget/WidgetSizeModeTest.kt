package com.runwear.app.widget

import androidx.glance.appwidget.SizeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression pin for the Oct 27 2026 field failure's size half: the
 * widget must receive the size the launcher actually reports
 * (SizeMode.Exact), never a host-substituted bucket (SizeMode.Responsive
 * let One UI serve a smaller bucket's RemoteViews for a placement the
 * largest bucket didn't exactly fit). The dispatch and slot budgets in
 * WidgetLayout are written for arbitrary sizes, so Exact loses nothing.
 */
class WidgetSizeModeTest {

    @Test
    fun `widget receives the exact reported size`() {
        assertEquals(SizeMode.Exact, OutfitWidget().sizeMode)
    }
}
