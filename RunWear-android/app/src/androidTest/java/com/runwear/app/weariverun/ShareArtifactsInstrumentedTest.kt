package com.runwear.app.weariverun

import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RW-169 §7F (Nov 11, 2026): the save-to-photos evidence of
 * record — an INSTRUMENTED test against the REAL MediaStore
 * (real ContentResolver via InstrumentationRegistry, no fake
 * resolver). Saves a card through ShareArtifacts.saveToPhotos
 * and asserts a gallery-visible, published row exists afterwards
 * (IS_PENDING cleared, size > 0, discoverable by a MediaStore
 * query over Pictures/RunWear), then removes it.
 *
 * This repo's CI (android-tests.yml) runs unit tests only — it
 * has no emulator/instrumented path — so this test executes on a
 * device/emulator via connectedDebugAndroidTest. Its existence
 * and that execution limitation are recorded in the RW-169 lane
 * report; the seam-level Robolectric companion is
 * Rw169SaveSeamTest (labelled seam-level, not this evidence).
 */
@RunWith(AndroidJUnit4::class)
class ShareArtifactsInstrumentedTest {

    @Test
    fun saveToPhotos_publishesAGalleryVisibleRow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val before = queryRunWearRows(context)
        val ok = ShareArtifacts.saveToPhotos(
            context, bitmap, ShareArtifactKind.MORNING_POSTER
        )
        assertTrue("saveToPhotos must report success", ok)
        val after = queryRunWearRows(context)
        assertEquals(
            "exactly one new gallery-visible row",
            before + 1, after.size
        )
        val row = after.maxByOrNull { it.second }!!
        // Read the row back directly: published and non-empty.
        context.contentResolver.query(
            row.first,
            arrayOf(
                MediaStore.Images.Media.IS_PENDING,
                MediaStore.Images.Media.SIZE
            ),
            null, null, null
        )?.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertTrue(cursor.getLong(1) > 0L)
        } ?: throw AssertionError("saved row did not read back")
        assertNotNull(row.first)
        // Cleanup: remove the test row from the device's gallery.
        context.contentResolver.delete(row.first, null, null)
    }

    /** All published rows under Pictures/RunWear as (uri, dateAdded). */
    private fun queryRunWearRows(
        context: android.content.Context
    ): List<Pair<android.net.Uri, Long>> {
        val out = mutableListOf<Pair<android.net.Uri, Long>>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.IS_PENDING
        )
        val selection = if (android.os.Build.VERSION.SDK_INT >= 29) {
            "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        } else {
            null
        }
        val args = if (android.os.Build.VERSION.SDK_INT >= 29) {
            arrayOf("Pictures/RunWear/")
        } else {
            null
        }
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, selection, args, null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val pendingIdx = cursor.getColumnIndex(MediaStore.Images.Media.IS_PENDING)
            while (cursor.moveToNext()) {
                val pending = if (pendingIdx >= 0) cursor.getInt(pendingIdx) else 0
                if (pending == 0) {
                    out += android.content.ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idIdx)
                    ) to cursor.getLong(dateIdx)
                }
            }
        }
        return out
    }
}
