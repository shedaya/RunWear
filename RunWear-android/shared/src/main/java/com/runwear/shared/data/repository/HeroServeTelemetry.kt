package com.runwear.shared.data.repository

import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.HeroImageResult
import com.runwear.shared.domain.model.HeroServeRecord
import com.runwear.shared.domain.model.HeroServeSource
import com.runwear.shared.domain.model.HeroWeatherCondition
import com.runwear.shared.domain.model.OutfitCombination
import java.util.concurrent.CopyOnWriteArrayList

/**
 * WS-7 / RW-26(c) — serve-source telemetry (D-18's attribution fix).
 *
 * Every hero serve emits exactly one [HeroServeRecord] here, at serve
 * time, naming its source (pool / bundled-fallback / personal /
 * scaffold), the cell and gender where applicable, the combination
 * served, and — for a bundled serve — the asset file and its manifest
 * provenance (combination_id + source row + rung, D-22). Before WS-7
 * the app recorded no serve source at all: Sol's walkthrough hero was
 * the bundled snow asset on a clear morning and no trigger could be
 * attributed after the fact. That failure is what this closes.
 *
 * Transport (the app's existing diagnostics channel patterns):
 * 1. The record's single-line [HeroServeRecord.summary] is written to
 *    the `HeroImage` log tag — the same structured log channel the
 *    hero cascade already narrates itself on (`[Hero] Trying: …`,
 *    `[Hero] ✓ Found via …`) — so a serve is attributable in any
 *    captured device log.
 * 2. Records are retained in a bounded in-memory history ([recent]),
 *    which tests assert against and a diagnostics capture can read.
 * 3. The app layer additionally files every BUNDLED-FALLBACK serve
 *    remotely under the automated `diagnostics` category (RW-18's
 *    client half: automated filings file under `diagnostics`, never
 *    a human category) via TelemetryPayloads — see MainViewModel's
 *    hero-serve filing. Pool and personal serves are already
 *    attributable server-side by row id / hero id, so they are not
 *    re-filed; the bundled serve is the one that previously left no
 *    trace anywhere, and it is the one that now files.
 *
 * The sink is replaceable ([installSink]) so tests capture records
 * without touching the log; production uses [DEFAULT_SINK].
 */
object HeroServeTelemetry {

    /** Receives one serve record. Must never throw into the serve path. */
    fun interface Sink {
        fun onServe(record: HeroServeRecord)
    }

    /** The production sink: the structured `HeroImage` log channel. */
    val DEFAULT_SINK: Sink = Sink { record ->
        android.util.Log.d("HeroImage", "[HeroServe] ${record.summary()}")
    }

    @Volatile
    private var sink: Sink = DEFAULT_SINK

    private val history = CopyOnWriteArrayList<HeroServeRecord>()

    /** Install a sink (tests). Pass [DEFAULT_SINK] to restore. */
    fun installSink(newSink: Sink) {
        sink = newSink
    }

    /** Emit one serve record: retained in history, then handed to the sink. */
    fun emit(record: HeroServeRecord) {
        history.add(record)
        while (history.size > MAX_HISTORY) history.removeAt(0)
        runCatching { sink.onServe(record) }
    }

    /** The retained serve records, oldest first (bounded, see [emit]). */
    fun recent(): List<HeroServeRecord> = history.toList()

    /** Clear retained history (tests). */
    fun clear() {
        history.clear()
    }

    /**
     * Emit the record for a pool-layer [HeroImageResult] — the
     * repository's serves and the synchronous selector fallback the
     * view model substitutes on a resolve timeout. [trigger] names
     * the path that produced the result (e.g. "pool-cascade",
     * "bundled-fallback", "rotation-floor", "resolve-timeout").
     * A result whose image is a bundled asset is a bundled serve
     * whatever the caller labelled it — the URL is the ground truth.
     */
    fun emitForResult(result: HeroImageResult, combination: OutfitCombination?, trigger: String) {
        val url = result.imageUrl ?: return
        val isBundled = url.startsWith(BUNDLED_ASSET_PREFIX)
        // GEN-4: a SCAFFOLD serve keeps its label — a bundled FLOOR
        // scaffold is a bundled URL, but relabelling it
        // bundled-fallback would record a non-exact serve as an
        // ordinary fallback and hide the chain tier that served it.
        val source = if (result.serveSource == HeroServeSource.SCAFFOLD) {
            HeroServeSource.SCAFFOLD
        } else if (isBundled) {
            HeroServeSource.BUNDLED_FALLBACK
        } else {
            result.serveSource ?: HeroServeSource.POOL
        }
        emit(
            HeroServeRecord(
                source = source,
                requestedCombinationId = result.combinationId,
                servedCombinationId = result.servedCombinationId,
                imageRowId = result.imageRowId,
                tempBracket = combination?.tempBracket,
                weatherCondition = combination?.let {
                    HeroWeatherCondition.fromWeatherCode(it.weatherCode)
                },
                gender = combination?.genderPreference,
                fallbackAssetFile = if (isBundled) {
                    result.fallbackAssetFile
                        ?: com.runwear.shared.domain.model.HeroImageSelector.fallbackAssetFile(url)
                } else {
                    result.fallbackAssetFile
                },
                fallbackProvenance = if (isBundled) {
                    result.fallbackProvenance
                        ?: com.runwear.shared.domain.model.HeroImageSelector.fallbackProvenance(url)
                } else {
                    result.fallbackProvenance
                },
                trigger = trigger
            )
        )
    }

    /**
     * WS-9 fix (telemetry-test authenticity): build the serve record
     * for a PERSONAL-layer serve — the one place the personal vs
     * scaffold classification is made. MainViewModel's two personal
     * serve sites (the resolve-time overlay and the poll delivery)
     * both build their record here and emit it through [emit] (via
     * the view model's recordHeroServe), so a test that drives this
     * factory + [emit] consumes a record the production code
     * produced — the pre-fix test hand-built its records and proved
     * only that the channel stores what it is handed.
     *
     * [isScaffold] is the serve kind the personal layer reported:
     * a scaffold serve is the user's OWN banked photo carrying the
     * prescription under the nearest conditions (the convergence
     * scaffold); anything else is the exact personal photo.
     */
    fun personalServeRecord(
        isScaffold: Boolean,
        requestedCombinationId: String,
        servedCombinationId: String? = null,
        gender: GenderPreference?,
        trigger: String
    ): HeroServeRecord = HeroServeRecord(
        source = if (isScaffold) {
            HeroServeSource.SCAFFOLD
        } else {
            HeroServeSource.PERSONAL
        },
        requestedCombinationId = requestedCombinationId,
        servedCombinationId = servedCombinationId,
        gender = gender,
        trigger = trigger
    )

    private const val MAX_HISTORY = 64
    private const val BUNDLED_ASSET_PREFIX = "file:///android_asset/hero_fallback/"
}
