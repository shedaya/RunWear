package com.runwear.shared.data.repository

import com.runwear.shared.domain.model.HeroFlagReason
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Insert model for `public.hero_flags` (migration 20261029190000).
 * Mirrors the table column-for-column.
 */
@Serializable
data class HeroFlagInsert(
    @SerialName("image_row_id") val imageRowId: String? = null,
    @SerialName("image_url") val imageUrl: String,
    @SerialName("combination_id") val combinationId: String? = null,
    @SerialName("image_source") val imageSource: String,
    @SerialName("reason") val reason: String,
    @SerialName("platform") val platform: String,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("created_at") val createdAt: String
)

/**
 * Builds [HeroFlagInsert] rows from the hero currently on screen.
 *
 * The flag must identify the EXACT photo the user saw:
 * - Pool photo → the `generated_images` row id is carried through, and
 *   the combination recorded is the served row's own cell (which can
 *   differ from the requested combination when the cascade fell back).
 * - Fallback/curated photo (Unsplash) → no row id exists; the URL is
 *   the identifier and [SOURCE_FALLBACK] marks it as non-pool.
 *
 * Pure and synchronous so the mapping is unit-testable without a
 * Supabase client.
 */
object HeroFlagPayload {

    const val PLATFORM_ANDROID = "Android"
    const val SOURCE_POOL = "pool"
    const val SOURCE_FALLBACK = "fallback"

    /** Total send attempts per flag (initial try + one retry). */
    const val MAX_SEND_ATTEMPTS = 2

    /**
     * @param imageUrl the hero URL on screen; null/blank → no flag.
     * @param imageRowId pool row id when [imageUrl] came from the pool.
     * @param servedCombinationId the pool row's own combination_id, when known.
     * @param requestedCombinationId the combination the app asked for;
     *   used when there is no served row (fallback) or its cell is unknown.
     * @param createdAtIso client timestamp, ISO-8601 (stored as timestamptz).
     * @return the insert row, or null when there is no photo to flag.
     */
    fun build(
        imageUrl: String?,
        imageRowId: String?,
        servedCombinationId: String?,
        requestedCombinationId: String?,
        reason: HeroFlagReason,
        appVersion: String?,
        createdAtIso: String
    ): HeroFlagInsert? {
        val url = imageUrl?.takeIf { it.isNotBlank() } ?: return null
        val fromPool = !imageRowId.isNullOrBlank()
        return HeroFlagInsert(
            imageRowId = if (fromPool) imageRowId else null,
            imageUrl = url,
            combinationId = servedCombinationId ?: requestedCombinationId,
            imageSource = if (fromPool) SOURCE_POOL else SOURCE_FALLBACK,
            reason = reason.code,
            platform = PLATFORM_ANDROID,
            appVersion = appVersion?.takeIf { it.isNotBlank() },
            createdAt = createdAtIso
        )
    }

    /**
     * Fire-and-forget submission: tries [sender] up to
     * [MAX_SEND_ATTEMPTS] times and swallows every failure. A flag is
     * best-effort user feedback — it must never surface an error or
     * disturb the screen it was sent from.
     */
    suspend fun submit(payload: HeroFlagInsert, sender: suspend (HeroFlagInsert) -> Unit) {
        repeat(MAX_SEND_ATTEMPTS) {
            try {
                sender(payload)
                return
            } catch (e: Exception) {
                // Swallowed by design — see class doc. The caller logs.
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }
}
