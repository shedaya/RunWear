package com.runwear.app.weariverun

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * GEN-6 Wave B (RW-31 §7.3 — DA-16): the finished raster is
 * inspected BEFORE the system share sheet opens. Ported from the
 * iOS lane's ShareRasterVerifier (ShareCardRenderStore.swift) with
 * the Android lane's luminance frame (Rec. 709: 0.2126R + 0.7152G +
 * 0.0722B — the same weights the app's photo-quality luminance
 * reads use).
 *
 * The raster must exist, match the export size EXACTLY, carry real
 * image data (sane global brightness/variance), and contain no
 * uniform black region — checked in a grid over the canvas and
 * specifically at the caller's sample points (the face anchor's
 * canvas position, each Kit Card garment tile). A uniform region
 * is the exact signature of the black squares in Sol's gen-5
 * recording and of a photo that never drew. Any failure means the
 * share sheet NEVER opens; the Studio shows "This card didn't
 * render cleanly — try again" and offers a force-render retry.
 */

/** A canvas-fraction sample point with the label its failure
 * reason carries ("face", "kit tile 1", …). */
data class ShareLabeledSamplePoint(
    val point: ShareSamplePoint,
    val label: String
)

sealed interface ShareRasterFailure {
    data object MissingImage : ShareRasterFailure
    data class WrongSize(
        val expectedWidth: Int,
        val expectedHeight: Int,
        val actualWidth: Int,
        val actualHeight: Int
    ) : ShareRasterFailure

    data class DegenerateRaster(
        val mean: Double,
        val standardDeviation: Double
    ) : ShareRasterFailure

    /** [label] is the failing region's name; the user-facing
     * reason string is "{label} sample". */
    data class UniformRegion(val label: String) : ShareRasterFailure {
        val reason: String get() = "$label sample"
    }
}

sealed interface ShareRasterResult {
    data object Valid : ShareRasterResult
    data class Invalid(val failure: ShareRasterFailure) : ShareRasterResult
}

object ShareRasterVerifier {

    /** The working grid's width; its height follows the raster's
     * aspect, so grid statistics read the whole canvas cheaply. */
    const val GRID_WIDTH = 48

    /** Sane-bounds constants (0–255 luminance frame). */
    const val MINIMUM_GLOBAL_DEVIATION = 3.0
    const val UNIFORM_MEAN_CEILING = 10.0
    const val UNIFORM_MEAN_FLOOR = 245.0
    const val UNIFORM_DEVIATION_CEILING = 2.0

    /** Mean + standard deviation of one sampled region. */
    data class RegionStats(val mean: Double, val standardDeviation: Double)

    private fun luminanceOf(pixel: Int): Double =
        0.2126 * Color.red(pixel) + 0.7152 * Color.green(pixel) + 0.0722 * Color.blue(pixel)

    private fun statsOf(values: List<Double>): RegionStats {
        if (values.isEmpty()) return RegionStats(0.0, 0.0)
        val mean = values.sum() / values.size
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return RegionStats(mean, sqrt(variance))
    }

    /**
     * Sample [bitmap] at pixel centre ([x], [y]): the mean and
     * deviation of luminance over a centred square patch whose
     * edge is max(2, min(w, h) / 90), read at a step of edge / 6
     * (at least 1 px). Edge pixels clamp into the bitmap.
     */
    fun sample(bitmap: Bitmap, x: Int, y: Int): RegionStats {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return RegionStats(0.0, 0.0)
        val edge = max(2, min(w, h) / 90)
        val step = max(1, edge / 6)
        val half = edge / 2
        val values = ArrayList<Double>()
        var py = y - half
        while (py <= y + half) {
            var px = x - half
            while (px <= x + half) {
                val cx = px.coerceIn(0, w - 1)
                val cy = py.coerceIn(0, h - 1)
                values += luminanceOf(bitmap.getPixel(cx, cy))
                px += step
            }
            py += step
        }
        return statsOf(values)
    }

    /** Sample a pre-extracted ARGB pixel array (row-major). */
    private fun samplePixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int
    ): RegionStats {
        if (width <= 0 || height <= 0) return RegionStats(0.0, 0.0)
        val edge = max(2, min(width, height) / 90)
        val step = max(1, edge / 6)
        val half = edge / 2
        val values = ArrayList<Double>()
        var py = y - half
        while (py <= y + half) {
            var px = x - half
            while (px <= x + half) {
                val cx = px.coerceIn(0, width - 1)
                val cy = py.coerceIn(0, height - 1)
                values += luminanceOf(pixels[cy * width + cx])
                px += step
            }
            py += step
        }
        return statsOf(values)
    }

    /** A uniform BLACK region — the black-square signature. The
     * grid scan uses this form only: the Kit Card's cream ground
     * is a designed uniform field, and whole-raster blankness is
     * the global degenerate check's job. */
    private fun isUniformBlack(stats: RegionStats): Boolean =
        stats.standardDeviation < UNIFORM_DEVIATION_CEILING &&
            stats.mean < UNIFORM_MEAN_CEILING

    /** A uniform black OR blank region — used at the specific
     * samples, where real content must sit (the face, a tile). */
    private fun isUniformDead(stats: RegionStats): Boolean =
        stats.standardDeviation < UNIFORM_DEVIATION_CEILING &&
            (stats.mean < UNIFORM_MEAN_CEILING || stats.mean > UNIFORM_MEAN_FLOOR)

    fun verify(
        bitmap: Bitmap?,
        expectedSize: SharePixelSize,
        samplePoints: List<ShareLabeledSamplePoint>
    ): ShareRasterResult {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
            return ShareRasterResult.Invalid(ShareRasterFailure.MissingImage)
        }
        if (bitmap.width != expectedSize.width || bitmap.height != expectedSize.height) {
            return ShareRasterResult.Invalid(
                ShareRasterFailure.WrongSize(
                    expectedWidth = expectedSize.width,
                    expectedHeight = expectedSize.height,
                    actualWidth = bitmap.width,
                    actualHeight = bitmap.height
                )
            )
        }
        // Working grid: the raster scaled to GRID_WIDTH wide.
        val gridHeight = max(
            1,
            (GRID_WIDTH.toDouble() * bitmap.height / bitmap.width).roundToInt()
        )
        val grid = try {
            Bitmap.createScaledBitmap(bitmap, GRID_WIDTH, gridHeight, true)
        } catch (e: Exception) {
            return ShareRasterResult.Invalid(ShareRasterFailure.MissingImage)
        }
        val gridPixels = IntArray(GRID_WIDTH * gridHeight)
        try {
            grid.getPixels(gridPixels, 0, GRID_WIDTH, 0, 0, GRID_WIDTH, gridHeight)
        } catch (e: Exception) {
            return ShareRasterResult.Invalid(ShareRasterFailure.MissingImage)
        }
        val luminance = DoubleArray(gridPixels.size) { luminanceOf(gridPixels[it]) }
        val global = statsOf(luminance.toList())
        if (global.standardDeviation < MINIMUM_GLOBAL_DEVIATION ||
            global.mean <= 3.0 || global.mean >= 252.0
        ) {
            return ShareRasterResult.Invalid(
                ShareRasterFailure.DegenerateRaster(global.mean, global.standardDeviation)
            )
        }

        // Grid scan: 6 columns × proportional rows of cells; a
        // cell of uniform black anywhere is a broken payload.
        val cellColumns = 6
        val cellSize = GRID_WIDTH / cellColumns
        val cellRows = max(1, gridHeight / cellSize)
        for (row in 0 until cellRows) {
            for (column in 0 until cellColumns) {
                val x0 = column * cellSize
                val x1 = min(GRID_WIDTH, x0 + cellSize)
                val y0 = row * cellSize
                val y1 = min(gridHeight, y0 + cellSize)
                if (x1 <= x0 || y1 <= y0) continue
                val cellValues = ArrayList<Double>((x1 - x0) * (y1 - y0))
                for (yy in y0 until y1) {
                    for (xx in x0 until x1) {
                        cellValues += luminance[yy * GRID_WIDTH + xx]
                    }
                }
                if (isUniformBlack(statsOf(cellValues))) {
                    return ShareRasterResult.Invalid(
                        ShareRasterFailure.UniformRegion("grid $column,$row")
                    )
                }
            }
        }

        // Specific samples: the face anchor and each garment tile
        // must hold real image data.
        for (labeled in samplePoints) {
            val cx = (labeled.point.x * GRID_WIDTH).toInt().coerceIn(0, GRID_WIDTH - 1)
            val cy = (labeled.point.y * gridHeight).toInt().coerceIn(0, gridHeight - 1)
            val stats = samplePixels(gridPixels, GRID_WIDTH, gridHeight, cx, cy)
            if (isUniformDead(stats)) {
                return ShareRasterResult.Invalid(
                    ShareRasterFailure.UniformRegion(labeled.label)
                )
            }
        }
        return ShareRasterResult.Valid
    }
}
