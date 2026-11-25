package com.runwear.app.ui.theme

import android.app.Application
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.runwear.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * RW-25: the bundled Great Vibes font (res/font/greatvibes_regular.ttf)
 * is the genuine article and resolves at runtime.
 *
 * Two proofs:
 *  - The file-parse tests read the bundled TTF's own tables: its name
 *    table must identify it as "Great Vibes Regular" /
 *    "GreatVibes-Regular", and its cmap must cover every character of
 *    the three script wordmarks ("RunWear", "Wear I've Run",
 *    "Make It Me") — including the lowercase "a" the old design-file
 *    subset was missing. A redrawn or lookalike substitute fails here.
 *  - The Robolectric test resolves R.font.greatvibes_regular through
 *    the platform font machinery (native graphics, so the TTF is
 *    really parsed) and shows it measures text differently from the
 *    platform default face — i.e. the resource is a live, distinct
 *    typeface, not a silent fallback.
 */
class GreatVibesBundledFileTest {

    private val bytes: ByteArray by lazy {
        val candidates = listOf(
            File("src/main/res/font/greatvibes_regular.ttf"),
            File("RunWear-android/app/src/main/res/font/greatvibes_regular.ttf"),
            File("app/src/main/res/font/greatvibes_regular.ttf")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("greatvibes_regular.ttf not found from ${File(".").absolutePath}")
        file.readBytes()
    }

    @Test
    fun `bundled file identifies itself as Great Vibes Regular`() {
        assertEquals("Great Vibes Regular", Ttf.name(bytes, 4))
        assertEquals("GreatVibes-Regular", Ttf.name(bytes, 6))
        assertEquals("Great Vibes", Ttf.name(bytes, 1))
    }

    @Test
    fun `bundled file covers every wordmark character including lowercase a`() {
        val needed = ("RunWear" + "Wear I've Run" + "Make It Me").toSet()
        for (ch in needed) {
            assertTrue("missing glyph for '$ch'", Ttf.glyphId(bytes, ch.code) != 0)
        }
        assertTrue("lowercase a must have a glyph", Ttf.glyphId(bytes, 'a'.code) != 0)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [33], qualifiers = "xhdpi")
class GreatVibesFontResolutionTest {

    @Test
    fun `great vibes resource resolves to a live distinct typeface`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val greatVibes = ResourcesCompat.getFont(context, R.font.greatvibes_regular)
        assertNotNull("R.font.greatvibes_regular did not resolve", greatVibes)

        // Native graphics parses the real TTF, so its metrics differ
        // from the platform default face — a fallback would measure
        // identically to Typeface.DEFAULT.
        val probe = "Wear I've Run"
        val scriptPaint = Paint().apply {
            typeface = greatVibes
            textSize = 40f
        }
        val defaultPaint = Paint().apply {
            typeface = Typeface.DEFAULT
            textSize = 40f
        }
        assertTrue(scriptPaint.measureText(probe) > 0f)
        assertNotEquals(
            defaultPaint.measureText(probe),
            scriptPaint.measureText(probe),
            0.01f
        )
    }

}

/** Minimal read-only TTF table reader (name + cmap format 4). */
private object Ttf {
    private fun u16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun i16(b: ByteArray, off: Int): Int {
        val v = u16(b, off)
        return if (v >= 0x8000) v - 0x10000 else v
    }

    private fun u32(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)

    private fun tableOffset(b: ByteArray, tag: String): Int {
        val numTables = u16(b, 4)
        for (i in 0 until numTables) {
            val rec = 12 + i * 16
            val t = String(b, rec, 4, Charsets.US_ASCII)
            if (t == tag) return u32(b, rec + 8)
        }
        error("table $tag not found")
    }

    fun name(b: ByteArray, nameId: Int): String? {
        val off = tableOffset(b, "name")
        val count = u16(b, off + 2)
        val storage = off + u16(b, off + 4)
        var macValue: String? = null
        for (i in 0 until count) {
            val rec = off + 6 + i * 12
            val platform = u16(b, rec)
            val id = u16(b, rec + 6)
            if (id != nameId) continue
            val len = u16(b, rec + 8)
            val strOff = storage + u16(b, rec + 10)
            val raw = b.copyOfRange(strOff, strOff + len)
            val decoded = when (platform) {
                0, 3 -> String(raw, Charsets.UTF_16BE)
                1 -> String(raw, Charsets.ISO_8859_1)
                else -> continue
            }
            if (platform == 3 || platform == 0) return decoded
            macValue = decoded
        }
        return macValue
    }

    fun glyphId(b: ByteArray, codePoint: Int): Int {
        val cmap = tableOffset(b, "cmap")
        val num = u16(b, cmap + 2)
        var sub = -1
        for (i in 0 until num) {
            val rec = cmap + 4 + i * 8
            val platform = u16(b, rec)
            val encoding = u16(b, rec + 2)
            val subOff = cmap + u32(b, rec + 4)
            if (u16(b, subOff) == 4 && (platform == 3 && encoding == 1 || platform == 0)) {
                sub = subOff
                break
            }
        }
        require(sub >= 0) { "no format-4 cmap subtable" }
        val segCount = u16(b, sub + 6) / 2
        val endBase = sub + 14
        val startBase = endBase + 2 * segCount + 2
        val deltaBase = startBase + 2 * segCount
        val rangeBase = deltaBase + 2 * segCount
        for (seg in 0 until segCount) {
            val end = u16(b, endBase + 2 * seg)
            if (codePoint > end) continue
            val start = u16(b, startBase + 2 * seg)
            if (codePoint < start) return 0
            val delta = i16(b, deltaBase + 2 * seg)
            val rangeOffset = u16(b, rangeBase + 2 * seg)
            if (rangeOffset == 0) return (codePoint + delta) and 0xFFFF
            val glyphAddr = rangeBase + 2 * seg + rangeOffset + 2 * (codePoint - start)
            val glyph = u16(b, glyphAddr)
            return if (glyph == 0) 0 else (glyph + delta) and 0xFFFF
        }
        return 0
    }
}
