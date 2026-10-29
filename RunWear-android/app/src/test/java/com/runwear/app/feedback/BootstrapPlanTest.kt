package com.runwear.app.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bootstrap pass-order pins (1.1.5) — the regression test for the
 * silenced 1.1.4 diagnostic. In 1.1.4 the pass's items ran inside a
 * single try in an order that let newer code (the handler-marker
 * snapshot) execute ahead of the crash upload, and one escaping Error
 * anywhere silenced every item at once: zero intake rows of any kind
 * from a build whose predecessor had reported from the same path on
 * every launch.
 *
 * The order is therefore a contract, pinned here: the crash upload
 * and the startup trace — the two items that already existed in the
 * last field-proven build (1.1.3) — lead; everything added since
 * rides behind them; the exit-history read (the only step that talks
 * to a system service and reads system trace streams) goes last.
 */
class BootstrapPlanTest {

    @Test
    fun stepOrder_isExactlyThePinnedOrder() {
        assertEquals(
            listOf("crash", "trace", "watchdog", "handlerMarker", "exitHistory"),
            BootstrapPlan.STEP_ORDER
        )
    }

    @Test
    fun stepOrder_crashAndTraceLead_exitHistoryLast() {
        val order = BootstrapPlan.STEP_ORDER
        assertEquals("crash", order.first())
        assertEquals("trace", order[1])
        assertEquals("exitHistory", order.last())
        // The two proven items precede every 1.1.4 addition.
        assertTrue(order.indexOf("crash") < order.indexOf("handlerMarker"))
        assertTrue(order.indexOf("trace") < order.indexOf("handlerMarker"))
        assertTrue(order.indexOf("crash") < order.indexOf("exitHistory"))
        assertTrue(order.indexOf("trace") < order.indexOf("exitHistory"))
    }
}
