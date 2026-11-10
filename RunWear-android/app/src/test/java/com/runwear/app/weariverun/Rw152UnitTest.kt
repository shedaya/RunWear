package com.runwear.app.weariverun

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.runwear.app.ui.screens.WirOverlayPanelPolicy
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * RW-152 (Nov 10, 2026) — unit gate for §2 (panel sizing), §3
 * (durable refs + resolver + cover selection) and the store's
 * durable-ref round-trip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [35])
class Rw152UnitTest {

    private val signed =
        "https://proj.supabase.co/storage/v1/object/sign/personal-heroes/u1/CLEAR_SKY|COOL.jpg?token=abc123"
    private val path = "u1/CLEAR_SKY|COOL.jpg"

    // ---- §3: durable refs + resolver ----

    @Test
    fun `a signed personal URL persists as its storage path`() {
        assertEquals(path, HeroRefResolver.durableRefFor(signed))
        assertEquals(path, HeroRefResolver.storagePathOf(signed))
        assertTrue(HeroRefResolver.isSignedPersonalUrl(signed))
    }

    @Test
    fun `a stable pool URL passes through unchanged`() {
        val pool = "https://cdn.example.com/heroes/cool.jpg"
        assertEquals(pool, HeroRefResolver.durableRefFor(pool))
        assertNull(HeroRefResolver.storagePathOf(pool))
        assertFalse(HeroRefResolver.isSignedPersonalUrl(pool))
    }

    @Test
    fun `an expired signed URL stored by a legacy entry recovers its path`() {
        val legacy = WearIveRunDayEntry(
            date = LocalDate.of(2026, 11, 1), place = "Miami, FL",
            feelsLikeF = 80.0, conditionLabel = "Sunny",
            isSnowing = false, isRaining = false,
            items = listOf("Singlet"), heroRef = signed
        )
        assertEquals(path, HeroRefResolver.storedRef(legacy))
        assertTrue(HeroRefResolver.hasLoadableReference(legacy))
    }

    @Test
    fun `resolve - a path renders only a fresh signature, never the dead URL`() {
        assertEquals(
            "https://fresh/signed",
            HeroRefResolver.resolveDisplay(path, "https://fresh/signed")
        )
        assertNull(HeroRefResolver.resolveDisplay(path, null))
        assertNull(
            "the expired URL is never the render input",
            HeroRefResolver.resolveDisplay(signed, null)
        )
    }

    @Test
    fun `resolve - stable URLs and empties stay honest`() {
        val pool = "https://cdn.example.com/heroes/cool.jpg"
        assertEquals(pool, HeroRefResolver.resolveDisplay(pool, null))
        assertNull(HeroRefResolver.resolveDisplay(null, "ignored"))
        assertNull(HeroRefResolver.resolveDisplay("", null))
        assertNull(HeroRefResolver.resolveDisplay("   ", null))
    }

    @Test
    fun `an entry with no reference at all has no loadable reference`() {
        val bare = WearIveRunDayEntry(
            date = LocalDate.of(2026, 11, 1), place = "Miami, FL",
            feelsLikeF = 80.0, conditionLabel = "Sunny",
            isSnowing = false, isRaining = false,
            items = listOf("Singlet"), heroRef = null
        )
        assertFalse(HeroRefResolver.hasLoadableReference(bare))
        assertNull(HeroRefResolver.storedRef(bare))
    }

    // ---- §3: store round-trip keeps the durable ref ----

    @Test
    fun `the store round-trips the durable ref alongside the display URL`() =
        runBlocking {
            val context = ApplicationProvider
                .getApplicationContext<Context>()
            val store = WearIveRunStore(context)
            val entry = WearIveRunDayEntry(
                date = LocalDate.of(2026, 11, 8), place = "New York, NY",
                feelsLikeF = 41.0, conditionLabel = "Clear sky",
                isSnowing = false, isRaining = false,
                items = listOf("Light Tights"),
                heroRef = signed,
                heroDurableRef = path,
                sessionId = "rw152-roundtrip",
                settledAtMillis = 42L
            )
            store.recordSettled(entry)
            val read = store.entries().single {
                it.sessionId == "rw152-roundtrip"
            }
            assertEquals(path, read.heroDurableRef)
            assertEquals(signed, read.heroRef)
            assertEquals(path, HeroRefResolver.storedRef(read))
        }

    // ---- §3: cover selection ----

    private fun entry(
        place: String,
        session: String,
        settledAt: Long,
        heroRef: String?,
        durable: String? = null
    ) = WearIveRunDayEntry(
        date = LocalDate.of(2026, 11, 5), place = place,
        feelsLikeF = 70.0, conditionLabel = "Clear sky",
        isSnowing = false, isRaining = false,
        items = listOf("Tee"), heroRef = heroRef,
        heroDurableRef = durable,
        sessionId = session, settledAtMillis = settledAt
    )

    @Test
    fun `cover is the newest entry with a loadable reference`() {
        val chapters = WearIveRunCollection.chapters(
            listOf(
                entry("Miami, FL", "newest-no-photo", 3000, null),
                entry(
                    "Miami, FL", "older-with-photo", 2000,
                    "https://cdn.example.com/m.jpg"
                )
            )
        )
        assertEquals(
            "older-with-photo",
            chapters.single().cover?.sessionId
        )
    }

    @Test
    fun `cover falls back to the newest entry when none has a photo`() {
        val chapters = WearIveRunCollection.chapters(
            listOf(
                entry("Miami, FL", "a", 1000, null),
                entry("Miami, FL", "b", 2000, null)
            )
        )
        assertEquals("b", chapters.single().cover?.sessionId)
    }

    @Test
    fun `an expired signed URL still counts as a loadable reference for cover selection`() {
        val chapters = WearIveRunCollection.chapters(
            listOf(
                entry("Miami, FL", "newest-no-photo", 3000, null),
                entry("Miami, FL", "older-signed", 2000, signed)
            )
        )
        assertEquals("older-signed", chapters.single().cover?.sessionId)
    }

    // ---- §2: panel sizing (the design's gate assertion, unit level) ----

    @Test
    fun `panel height equals content height whenever the cap does not bind`() {
        // Rendered panel height == measured content height at
        // every viewport class: standard, folded, wide.
        for (viewport in listOf(844f, 740f, 660f, 420f)) {
            for (content in listOf(180f, 240f, 260f, 300f)) {
                if (!WirOverlayPanelPolicy.capBinds(content, viewport)) {
                    assertEquals(
                        "viewport $viewport content $content",
                        content,
                        WirOverlayPanelPolicy.panelHeightDp(content, viewport)
                    )
                }
            }
        }
    }

    @Test
    fun `the cap is the viewport minus the top chrome zone`() {
        assertEquals(
            844f - WirOverlayPanelPolicy.TOP_CHROME_DP,
            WirOverlayPanelPolicy.maxPanelHeightDp(844f)
        )
        assertEquals(
            724f,
            WirOverlayPanelPolicy.panelHeightDp(900f, 844f)
        )
        assertTrue(WirOverlayPanelPolicy.capBinds(900f, 844f))
        assertFalse(WirOverlayPanelPolicy.capBinds(300f, 844f))
    }
}
