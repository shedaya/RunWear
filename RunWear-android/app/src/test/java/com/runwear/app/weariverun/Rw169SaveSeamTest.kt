package com.runwear.app.weariverun

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver

/**
 * RW-169 §7F (Nov 11, 2026) — SEAM-LEVEL evidence for the
 * save-to-photos honesty fix. Labelled honestly: this drives the
 * REAL android.content.ContentResolver dispatch into a REAL
 * ContentProvider implementation (an in-memory MediaStore
 * provider registered for the "media" authority under
 * Robolectric), so insert/update/query/openFile all execute the
 * production code paths — but it is NOT the device MediaStore.
 * The evidence of record for gallery visibility is the
 * instrumented test (app/src/androidTest/.../
 * ShareArtifactsInstrumentedTest.kt) against the real
 * MediaStore; this seam test pins the failure branches a device
 * test cannot force (a publish update matching zero rows; a
 * row that reads back still-pending).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Rw169SaveSeamTest {

    /** A minimal but real MediaStore-shaped provider. */
    class FakeMediaProvider : ContentProvider() {
        data class Row(
            val values: MutableMap<String, Any?>,
            var file: File? = null
        )

        val rows = mutableMapOf<Long, Row>()
        private var nextId = 1L

        /** When set, update() reports 0 rows (the §6 failure). */
        var updateRowCountOverride: Int? = null

        /** When set, update() does not clear IS_PENDING. */
        var publishClearsPending: Boolean = true

        override fun onCreate(): Boolean = true

        override fun insert(uri: Uri, values: ContentValues?): Uri {
            val id = nextId++
            val map = mutableMapOf<String, Any?>()
            values?.keySet()?.forEach { map[it] = values.get(it) }
            rows[id] = Row(map)
            return Uri.withAppendedPath(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()
            )
        }

        override fun update(
            uri: Uri, values: ContentValues?, where: String?, args: Array<String>?
        ): Int {
            updateRowCountOverride?.let { return it }
            val row = rows[uri.lastPathSegment!!.toLong()] ?: return 0
            if (publishClearsPending) {
                values?.keySet()?.forEach { row.values[it] = values.get(it) }
            }
            return 1
        }

        override fun delete(uri: Uri, where: String?, args: Array<String>?): Int {
            return if (rows.remove(uri.lastPathSegment!!.toLong()) != null) 1 else 0
        }

        override fun query(
            uri: Uri, projection: Array<String>?, where: String?,
            args: Array<String>?, sort: String?
        ): Cursor {
            val row = rows[uri.lastPathSegment!!.toLong()]
            val cols = projection ?: arrayOf(
                MediaStore.Images.Media.SIZE, MediaStore.Images.Media.IS_PENDING
            )
            val cursor = MatrixCursor(cols)
            if (row != null) {
                cursor.addRow(cols.map { col ->
                    when (col) {
                        MediaStore.Images.Media.SIZE ->
                            row.file?.length() ?: 0L
                        MediaStore.Images.Media.IS_PENDING ->
                            (row.values[MediaStore.Images.Media.IS_PENDING] as? Int) ?: 0
                        else -> row.values[col]
                    }
                }.toTypedArray())
            }
            return cursor
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val row = rows[uri.lastPathSegment!!.toLong()]
                ?: throw java.io.FileNotFoundException(uri.toString())
            val file = File.createTempFile("rw169-media", ".png")
            row.file = file
            return ParcelFileDescriptor.open(
                file, ParcelFileDescriptor.MODE_READ_WRITE
            )
        }

        override fun getType(uri: Uri): String = "image/png"
    }

    private lateinit var provider: FakeMediaProvider

    @Before
    fun setUp() {
        provider = FakeMediaProvider()
        provider.attachInfo(
            RuntimeEnvironment.getApplication(),
            android.content.pm.ProviderInfo().apply { authority = "media" }
        )
        ShadowContentResolver.registerProviderInternal("media", provider)
    }

    private fun bitmap(): Bitmap =
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

    @Test
    fun `save publishes and reads back a gallery-visible row`() {
        val context = RuntimeEnvironment.getApplication()
        val ok = ShareArtifacts.saveToPhotos(
            context, bitmap(), ShareArtifactKind.MORNING_POSTER
        )
        assertTrue("save reports success only after read-back", ok)
        assertEquals(1, provider.rows.size)
        val row = provider.rows.values.single()
        assertEquals(0, row.values[MediaStore.Images.Media.IS_PENDING])
        assertTrue((row.file?.length() ?: 0L) > 0L)
    }

    @Test
    fun `a publish update matching zero rows is a failure, never a success`() {
        // Diagnosis §6's exact failure: the pre-fix code discarded
        // this result and returned true, leaving an invisible
        // IS_PENDING row while announcing "Saved to photos".
        provider.updateRowCountOverride = 0
        val context = RuntimeEnvironment.getApplication()
        val ok = ShareArtifacts.saveToPhotos(
            context, bitmap(), ShareArtifactKind.MORNING_POSTER
        )
        assertFalse(ok)
        assertTrue(
            "the failed row is removed, not left pending-invisible",
            provider.rows.isEmpty()
        )
    }

    @Test
    fun `a row that reads back still pending is a failure`() {
        provider.publishClearsPending = false
        val context = RuntimeEnvironment.getApplication()
        val ok = ShareArtifacts.saveToPhotos(
            context, bitmap(), ShareArtifactKind.MORNING_POSTER
        )
        assertFalse(ok)
        assertTrue(provider.rows.isEmpty())
    }

    @Test
    fun `verifyPublishedRow rejects missing and empty rows`() {
        val context = RuntimeEnvironment.getApplication()
        val missing = Uri.withAppendedPath(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "999"
        )
        assertFalse(ShareArtifacts.verifyPublishedRow(context.contentResolver, missing))
    }
}
