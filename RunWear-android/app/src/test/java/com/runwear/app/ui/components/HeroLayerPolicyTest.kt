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

    // ── GEN-3 A-HERO (X-7): scene changes ───────────────────────────
    // The gen-2 layering kept the old place's photo as base through
    // a place switch: Miami's label sat over New York's photo for
    // ~1.3 s. A scene change has no access to the old layers at all
    // (the function takes only the new scene's URLs) — the new base
    // is the new scene's own fallback, and the resolved photo fades
    // in over THAT.

    private val miamiFallback = "file:///android_asset/hero_fallback/hot_clear_female.webp"
    private val miamiPhoto = "https://example.test/miami.webp"

    @Test
    fun `a scene change bases on the new scene's fallback, never the old photo`() {
        val layers = HeroLayerPolicy.onSceneChange(miamiPhoto, miamiFallback)
        assertEquals(miamiFallback, layers.baseUrl)
        assertEquals(miamiPhoto, layers.incomingUrl)
    }

    @Test
    fun `a scene change with no resolved photo yet paints the fallback alone`() {
        val layers = HeroLayerPolicy.onSceneChange(null, miamiFallback)
        assertEquals(miamiFallback, layers.baseUrl)
        assertNull(layers.incomingUrl)
    }

    @Test
    fun `a scene change whose target is the fallback has no incoming layer`() {
        val layers = HeroLayerPolicy.onSceneChange(miamiFallback, miamiFallback)
        assertEquals(miamiFallback, layers.baseUrl)
        assertNull(layers.incomingUrl)
    }

    @Test
    fun `a scene change with no fallback bases on the resolved photo itself`() {
        val layers = HeroLayerPolicy.onSceneChange(miamiPhoto, null)
        assertEquals(miamiPhoto, layers.baseUrl)
        assertNull(layers.incomingUrl)
    }

    @Test
    fun `a scene change with neither photo nor fallback is the gradient truth`() {
        val layers = HeroLayerPolicy.onSceneChange(null, null)
        assertNull(layers.baseUrl)
        assertNull(layers.incomingUrl)
    }

    @Test
    fun `after a scene change the incoming photo promotes over the fallback`() {
        var layers = HeroLayerPolicy.onSceneChange(miamiPhoto, miamiFallback)
        layers = HeroLayerPolicy.onIncomingReady(layers)
        assertEquals(miamiPhoto, layers.baseUrl)
        assertNull(layers.incomingUrl)
    }

    // ── RW-106 (Nov 6, 2026): the bounded hero-load retry —
    // two retries with backoff, then the layer settles on the
    // base for good.

    @Test
    fun `the retry budget is two retries with growing backoff - RW-106`() {
        assertEquals(2, HeroLoadRetryPolicy.MAX_RETRIES)
        assertEquals(1_200L, HeroLoadRetryPolicy.retryDelayMs(0))
        assertEquals(2_400L, HeroLoadRetryPolicy.retryDelayMs(1))
    }

    @Test
    fun `the retry budget ends - the layer settles - RW-106`() {
        assertNull(HeroLoadRetryPolicy.retryDelayMs(2))
        assertNull(HeroLoadRetryPolicy.retryDelayMs(3))
    }
}
