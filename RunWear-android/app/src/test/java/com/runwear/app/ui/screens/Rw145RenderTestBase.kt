package com.runwear.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.app.makeitme.MakeItMeMode
import com.runwear.app.makeitme.MakeItMeUiState
import com.runwear.app.makeitme.PersonalLookStatus
import com.runwear.app.makeitme.PersonalServeKind
import com.runwear.app.ui.viewmodel.MainUiState
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime

/**
 * RW-145/146/147 (Nov 9, 2026): shared fixtures for the fix-pass
 * proof frames. Unlike the RW-144 harness — whose hero fixture was
 * a flat wash (null photo URL), the gap the RW-145 diagnosis
 * names — these fixtures serve a REAL bundled hero photograph
 * (the production fallback serve for the cell, a visible runner
 * subject whose head band is the audited D-23 band) through the
 * production Coil path, from a file the test decodes out of the
 * shared module's own asset pool.
 */
abstract class Rw145RenderTestBase : Rw144RenderTestBase() {

    protected val nov9: LocalDate = LocalDate.of(2026, 11, 9)
    protected val nov9Now: LocalDateTime = LocalDateTime.of(2026, 11, 9, 6, 0)

    /** Decode a bundled hero asset (shared module pool) to a PNG
     * file and return its file:// URL for Coil. */
    protected fun heroPhotoUrl(assetName: String): String {
        val candidates = listOf(
            "../shared/src/main/assets/hero_fallback/$assetName",
            "shared/src/main/assets/hero_fallback/$assetName",
            "../../shared/src/main/assets/hero_fallback/$assetName"
        )
        val src = candidates.map { File(it) }.firstOrNull { it.exists() }
            ?: throw AssertionError("hero asset $assetName not found from ${File(".").absolutePath}")
        val bitmap: Bitmap = BitmapFactory.decodeFile(src.absolutePath)
            ?: throw AssertionError("hero asset $assetName did not decode")
        outDir.mkdirs()
        val png = File(outDir, "fixture-$assetName.png")
        png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "file://" + png.absolutePath
    }

    /** New York, Now, 41° COOL — Sol's device-pass conditions. */
    protected fun nyWeather() = WeatherConditions(
        dateTime = KotlinLocalDateTime(2026, 11, 9, 6, 0),
        temperature = 49.0,
        feelsLike = 41.0,
        humidity = 46,
        windSpeed = 8.0,
        windGusts = 12.0,
        windDirection = 270,
        precipitationProbability = 1,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 20,
        uvIndex = 0.0,
        weatherCode = WeatherCode.MAINLY_CLEAR
    )

    protected fun nyState(photoUrl: String): MainUiState {
        val w = nyWeather()
        return MainUiState(
            isLoading = false,
            error = null,
            outfit = engine.execute(w, ComfortPreference.NEUTRAL),
            weather = w,
            hourlyForecast = hourly(nov9),
            locationName = "New York, NY",
            simplePlace = com.runwear.shared.domain.model.SimplePlace
                .fromRawDisplayString("New York, NY"),
            selectedDateTime = nov9Now,
            locationNow = nov9Now,
            isNowSelected = true,
            heroImageUrl = photoUrl,
            heroFallbackUrl = photoUrl,
            heroIsPersonal = false,
            personalLookStatus = PersonalLookStatus.Ready,
            heroServeKind = PersonalServeKind.EXACT,
            locationSource = "gps",
            hasCompletedOnboarding = true
        )
    }

    protected fun exhaustedMim() = MakeItMeUiState(
        mode = MakeItMeMode.FREE,
        freeGenerationsUsed = 10
    )

    protected data class NodeBox(
        val text: String,
        val top: Float,
        val bottom: Float,
        val left: Float,
        val right: Float
    )

    /** All text nodes with full bounds (dp-space conversion is the
     * caller's, via the host's density). */
    protected fun nodeBoxes(host: Host): List<NodeBox> {
        val out = mutableListOf<NodeBox>()
        fun walk(node: SemanticsNode) {
            if (node.config.contains(SemanticsProperties.Text)) {
                for (t in node.config[SemanticsProperties.Text]) {
                    out += NodeBox(
                        t.text,
                        node.boundsInRoot.top, node.boundsInRoot.bottom,
                        node.boundsInRoot.left, node.boundsInRoot.right
                    )
                }
            }
            node.children.forEach { walk(it) }
        }
        walk(rootNode(host.composeView))
        return out
    }
}
