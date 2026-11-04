package com.runwear.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * GEN-2 A2 (W-6): the layered-hero rules. The walkthrough's switch
 * showed an EMPTY hero band for ~1 s and then the new photo fading in
 * through white — the single painter bound to the URL painted
 * nothing while the new image decoded, over the bare background.
 * The layer policy is what now forbids that: the base photo stays
 * whole until the incoming photo is ready, and the incoming fades
 * over it.
 */
class HeroLayerPolicyTest {

    private val kc = "https://example.test/kc.webp"
    private val ny = "https://example.test/ny.webp"
    private val chi = "https://example.test/chi.webp"

    @Test
    fun `the first photo becomes incoming, then base when ready`() {
        var layers = HeroLayerPolicy.Layers(null, null)
        layers = HeroLayerPolicy.onTarget(layers, kc)
        assertNull(layers.baseUrl)
        assertEquals(kc, layers.incomingUrl)

        layers = HeroLayerPolicy.onIncomingReady(layers)
        assertEquals(kc, layers.baseUrl)
        assertNull(layers.incomingUrl)
    }

    @Test
    fun `a switch keeps the old photo as base until the new one is ready`() {
        var layers = HeroLayerPolicy.Layers(kc, null)
        layers = HeroLayerPolicy.onTarget(layers, ny)
        // The whole point: the band is never empty mid-switch.
        assertEquals(kc, layers.baseUrl)
        assertEquals(ny, layers.incomingUrl)

        layers = HeroLayerPolicy.onIncomingReady(layers)
        assertEquals(ny, layers.baseUrl)
        assertNull(layers.incomingUrl)
    }

    @Test
    fun `re-targeting mid-transition replaces only the incoming layer`() {
        var layers = HeroLayerPolicy.Layers(kc, null)
        layers = HeroLayerPolicy.onTarget(layers, ny)
        layers = HeroLayerPolicy.onTarget(layers, chi)
        assertEquals(kc, layers.baseUrl)
        assertEquals(chi, layers.incomingUrl)
    }

    @Test
    fun `a target equal to the base clears any incoming layer`() {
        val layers = HeroLayerPolicy.onTarget(HeroLayerPolicy.Layers(kc, ny), kc)
        assertEquals(kc, layers.baseUrl)
        assertNull(layers.incomingUrl)
    }

    @Test
    fun `a null target is the resolved no-photo state - gradient, reached only after resolution`() {
        val layers = HeroLayerPolicy.onTarget(HeroLayerPolicy.Layers(kc, ny), null)
        assertNull(layers.baseUrl)
        assertNull(layers.incomingUrl)
    }

    @Test
    fun `readiness with nothing incoming is a no-op`() {
        val layers = HeroLayerPolicy.Layers(kc, null)
        assertEquals(layers, HeroLayerPolicy.onIncomingReady(layers))
    }
}
