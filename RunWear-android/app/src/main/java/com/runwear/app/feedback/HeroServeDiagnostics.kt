package com.runwear.app.feedback

import com.runwear.shared.domain.model.HeroServeRecord
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import org.json.JSONObject

/**
 * WS-7 / RW-26(c): the remote half of serve-source telemetry.
 *
 * A bundled-fallback serve is the one hero serve that leaves no trace
 * anywhere else — no pool row, no server hero record — and it is the
 * serve D-18 could not attribute after the fact (Sol's walkthrough
 * hero was the bundled snow asset on a clear morning, trigger
 * unknown). So when the final paint is a bundled serve, the app files
 * its [HeroServeRecord] as an AUTOMATED report under the
 * `diagnostics` category — RW-18's routing for automated filings,
 * never a human feedback category — through the same
 * [TelemetryPayloads] shape and [TelemetryClient] door as every other
 * automated filing. Pool / personal / scaffold serves are NOT filed:
 * they are attributable server-side by row id / hero id, and their
 * serve records ride the on-device HeroServe channel
 * (HeroServeTelemetry) like every serve.
 *
 * The filing is best-effort and fire-and-forget: a failed send is
 * dropped, never queued — a stale bundled-serve report retried days
 * later would misattribute, which is worse than a missing one.
 */
object HeroServeDiagnostics {

    /** The diagnostics payload for one bundled serve record. */
    fun payload(record: HeroServeRecord, appVersion: String, installId: String): JSONObject {
        // The bundle rides the intake's strict allowlist
        // (runwear-feedback/1: unknown bundle keys are rejected with
        // 422 — the same constraint RawCrashBootstrap's diagnostics
        // filings observe), so the bundle carries only the allowlisted
        // engineVersion and EVERY serve fact rides in the text: the
        // record's summary names source, cell, gender, combinations,
        // asset, provenance combination + row, rung, and trigger.
        val bundle = JSONObject().apply {
            put("engineVersion", GetOutfitRecommendationUseCase.ENGINE_VERSION)
        }
        return TelemetryPayloads.feedback(
            appVersion = appVersion,
            installId = installId,
            categoryId = FeedbackRouting.automatedCategoryId(AutomatedReportCategory.DIAGNOSTICS),
            text = record.summary(),
            replyEmail = null,
            entryPoint = "hero_serve",
            bundle = bundle
        )
    }
}
