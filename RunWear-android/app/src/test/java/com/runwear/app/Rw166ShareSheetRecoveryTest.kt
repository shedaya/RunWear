package com.runwear.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import com.runwear.app.weariverun.HeroRecovery
import com.runwear.app.weariverun.HeroRecoverySeam
import com.runwear.app.weariverun.ShareStudioSheet
import com.runwear.app.weariverun.WearIveRunDayEntry
import com.runwear.shared.domain.model.GenderPreference
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * RW-166 (Nov 10, 2026): the Share Studio's poster for an entry
 * that settled with NO hero reference (RW-160's Android form).
 *
 * Pre-fix, the sheet's hero gate short-circuits an empty-ref
 * entry to NoPhoto before any resolution: "Your Postcard
 * couldn't be made" while the Kit Card renders. Post-fix, with
 * the recovery seam present, the entry's own banked hero is
 * re-linked, healed, and loaded — the poster renders and offers
 * the full photo, exactly as the RW-158 expired-ref case proved
 * for the re-sign path (this class reuses its harness).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(qualifiers = "w411dp-h914dp")
class Rw166ShareSheetRecoveryTest : Rw158Harness() {

    @Test
    fun emptyRefEntryPostcardRendersFromItsRecoveredBankHero() {
        val photo = File.createTempFile("rw166-hero", ".png")
        val bitmap = Bitmap.createBitmap(90, 120, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(0x8A, 0x9B, 0x7C))
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val kitKeys = listOf(
            "LONG_SLEEVE_LIGHT",
            "SHORTS",
            "LIGHT_BEANIE",
            "SUNGLASSES"
        )
        val ny = WearIveRunDayEntry(
            date = LocalDate.of(2026, 11, 9),
            place = "New York, NY",
            feelsLikeF = 58.0,
            conditionLabel = "Clear sky",
            items = kitKeys,
            itemKeys = kitKeys,
            heroRef = null,
            heroDurableRef = null,
            momentLabel = "9 a.m.",
            settledAtMillis = 1_762_700_400_000L,
            sessionId = "rw166-share"
        )
        val bankId = HeroRecovery.candidateCombinationIds(
            ny, "MALE", ZoneId.systemDefault()
        ).first()
        val bankPath = "user-1/heroes/$bankId/v1.png"
        val heals = mutableListOf<String>()

        composeRule.setContent {
            MaterialTheme {
                ShareStudioSheet(
                    entry = ny,
                    gender = GenderPreference.MALE,
                    onDismiss = {},
                    heroResign = { path ->
                        if (path == bankPath) photo.absolutePath else null
                    },
                    heroRecovery = HeroRecoverySeam(
                        genderSegment = "MALE",
                        bankPaths = { mapOf(bankId to bankPath) }
                    ),
                    onHeroRecovered = { path -> heals += path },
                    onViewPhoto = {}
                )
            }
        }
        awaitNewDialog()
        val tree = composeRule.onRoot().printToString(maxDepth = 400)
        assertNoFailureText(tree, "rw166-share-sheet")
        // The poster rendered from the recovered photo: the
        // RW-158 preview door ("View full photo") is present.
        composeRule.onNodeWithText("View full photo").assertIsDisplayed()
        // And the recovery healed exactly once, with the bank path.
        assertEquals(listOf(bankPath), heals)
    }
}
