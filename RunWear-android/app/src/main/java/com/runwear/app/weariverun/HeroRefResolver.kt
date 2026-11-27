package com.runwear.app.weariverun

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter

/**
 * RW-152 §3 (Nov 10, 2026): durable hero references and their
 * render-time resolution.
 *
 * THE DEFECT this closes: an entry's heroRef was the DISPLAY URL
 * captured at settle time. Personal heroes are served as signed
 * Storage URLs with a one-hour TTL, so every surface that rendered
 * a stored ref more than an hour after settle loaded a dead URL —
 * the scrapbook (the surface that exists to render old refs) read
 * as text-only cards while fresh surfaces masked the failure.
 *
 * THE RULE: entries persist the hero's DURABLE identity —
 * the Storage path for personal heroes (bucket personal-heroes),
 * the stable URL for pool/curated heroes — alongside the display
 * URL (see WearIveRunDayEntry.heroDurableRef), and every
 * collection surface resolves at render through here:
 *
 * - a stable URL passes through untouched;
 * - a Storage path (or an expired signed URL for the
 *   personal-heroes bucket, whose path is extracted) is re-signed
 *   at render via [PersonalHeroService.signedUrlFor] (the caller
 *   supplies that suspend call as `resign`);
 * - an empty ref stays honestly empty — no placeholder photo is
 *   ever fabricated.
 *
 * Old entries whose stored ref IS an expired signed URL are
 * recoverable by the same path-extraction rule.
 */
object HeroRefResolver {

    private const val SIGNED_MARKER = "/storage/v1/object/sign/personal-heroes/"
    private const val PUBLIC_MARKER = "/storage/v1/object/public/personal-heroes/"

    /** The durable identity to persist for a display ref captured
     * at settle time: the Storage path for a signed personal hero,
     * the ref itself when it already is a path or a stable URL. */
    fun durableRefFor(displayRef: String?): String? {
        val ref = displayRef?.takeIf { it.isNotBlank() } ?: return null
        storagePathOf(ref)?.let { return it }
        return ref
    }

    /**
     * The personal-heroes Storage path a ref names, or null when
     * the ref is not a personal Storage reference: a signed URL
     * for the bucket (path before the query), or a bare path
     * (no scheme, contains a path separator). Public bucket URLs
     * are stable and are NOT paths — they pass through as URLs.
     */
    fun storagePathOf(ref: String?): String? {
        val r = ref?.takeIf { it.isNotBlank() } ?: return null
        val signedAt = r.indexOf(SIGNED_MARKER)
        if (signedAt >= 0) {
            return r.substring(signedAt + SIGNED_MARKER.length)
                .substringBefore('?')
                .takeIf { it.isNotBlank() }
        }
        if (!r.contains("://") && !r.startsWith("file:") && r.contains('/')) {
            return r
        }
        return null
    }

    /** True when the ref is a signed personal-heroes URL — the
     * expiring form (one-hour TTL). */
    fun isSignedPersonalUrl(ref: String?): Boolean =
        ref?.contains(SIGNED_MARKER) == true

    /** The stored durable ref for an entry: the persisted durable
     * identity when present, else the identity derived from the
     * legacy display ref (the recovery path for old entries). */
    fun storedRef(entry: WearIveRunDayEntry): String? =
        entry.heroDurableRef?.takeIf { it.isNotBlank() }
            ?: durableRefFor(entry.heroRef)

    /** Whether an entry carries a reference a surface can load —
     * the cover-selection rule's "loadable reference". */
    fun hasLoadableReference(entry: WearIveRunDayEntry): Boolean =
        storedRef(entry) != null

    /**
     * Pure resolution once a re-sign result is known: a stored ref
     * that names a Storage path resolves to the freshly signed
     * URL (null while/if the re-sign has not landed — the surface
     * shows its honest empty slot, never the dead URL); a stable
     * URL resolves to itself.
     */
    fun resolveDisplay(storedRef: String?, resignedUrl: String?): String? {
        val ref = storedRef?.takeIf { it.isNotBlank() } ?: return null
        return if (storagePathOf(ref) != null) resignedUrl else ref
    }
}

/**
 * RW-166 (Nov 10, 2026): the bank re-link recovery seam — the
 * production caller supplies the user's bank as combination_id →
 * storage_path (PersonalHeroService.fetchBankHeroPaths via the
 * Make It Me view model) plus the gender segment the user's
 * cells are keyed under. A surface granted this seam resolves an
 * entry with NO loadable reference through [HeroRecovery];
 * surfaces without it resolve exactly as RW-152 left them.
 */
data class HeroRecoverySeam(
    val genderSegment: String,
    val bankPaths: suspend () -> Map<String, String>?
)

/**
 * RW-166: one entry's render resolution. [url] is the loadable
 * display URL when one exists NOW (null while any async leg is
 * still in flight, and null for good when the entry is honestly
 * photo-less); [pending] is true exactly while a re-sign or a
 * recovery attempt has not concluded — the Share Studio's hero
 * gate reads it so an empty-ref entry whose recovery is still
 * running waits instead of failing its poster on the spot.
 */
data class HeroUrlResolution(
    val url: String?,
    val pending: Boolean
)

/**
 * RW-152 §3 + RW-166: resolve an entry's hero at render.
 *
 * - The entry's own reference resolves first, exactly as RW-152
 *   built it: a stable URL passes through untouched; a Storage
 *   path (or an expired signed URL's extracted path) re-signs
 *   through [resign].
 * - RW-166: only when the entry carries NO loadable reference
 *   (the empty-settle shape) and a [recovery] seam is present
 *   does the bank re-link run: the entry's own outfit cell is
 *   looked up in the bank ([HeroRecovery]); a hit is reported
 *   through [onHeroRecovered] (the host persists it on the
 *   entry, so the recovery happens ONCE) and re-signed through
 *   the same [resign]. No hit → the entry stays honestly empty.
 */
@Composable
fun rememberHeroResolution(
    entry: WearIveRunDayEntry,
    resign: (suspend (String) -> String?)? = null,
    recovery: HeroRecoverySeam? = null,
    onHeroRecovered: ((String) -> Unit)? = null
): HeroUrlResolution {
    val stored = remember(entry) { HeroRefResolver.storedRef(entry) }
    val path = remember(stored) { HeroRefResolver.storagePathOf(stored) }
    var resigned by remember(stored) { mutableStateOf<String?>(null) }
    var resignSettled by remember(stored) {
        mutableStateOf(path == null || resign == null)
    }
    LaunchedEffect(stored) {
        if (path != null && resign != null) {
            resigned = resign(path)
            resignSettled = true
        }
    }
    val ownUrl = HeroRefResolver.resolveDisplay(stored, resigned)

    // RW-166 recovery leg: attempted only for an entry with no
    // loadable reference of its own, only with a seam present.
    val recoveryApplies = stored == null && recovery != null
    var recoveredUrl by remember(entry) { mutableStateOf<String?>(null) }
    var recoverySettled by remember(entry) {
        mutableStateOf(!recoveryApplies)
    }
    LaunchedEffect(entry, recoveryApplies) {
        if (!recoveryApplies) return@LaunchedEffect
        val seam = recovery ?: return@LaunchedEffect
        val bank = runCatching { seam.bankPaths() }.getOrNull()
        val recoveredPath = bank?.let {
            HeroRecovery.recoveredDurableReference(
                entry, seam.genderSegment, it
            )
        }
        if (recoveredPath != null) {
            onHeroRecovered?.invoke(recoveredPath)
            recoveredUrl = if (resign != null) {
                resign(recoveredPath)
            } else {
                // No re-sign seam (render proofs): the recovered
                // durable path cannot be displayed raw — it is
                // persisted by the heal and resolves on the next
                // composition that carries the seam.
                null
            }
        }
        recoverySettled = true
    }

    val url = ownUrl ?: recoveredUrl
    val pending = when {
        url != null -> false
        stored != null -> !resignSettled
        recoveryApplies -> !recoverySettled
        else -> false
    }
    return HeroUrlResolution(url, pending)
}

/**
 * RW-152 §3: resolve an entry's hero to a loadable display URL at
 * render — re-signing its Storage path through [resign] (the
 * production caller passes PersonalHeroService.signedUrlFor via
 * the Make It Me view model). Stable URLs resolve synchronously;
 * a path resolves when its re-sign lands. RW-166: the recovering
 * form — see [rememberHeroResolution].
 */
@Composable
fun rememberResolvedHeroUrl(
    entry: WearIveRunDayEntry,
    resign: (suspend (String) -> String?)? = null,
    recovery: HeroRecoverySeam? = null,
    onHeroRecovered: ((String) -> Unit)? = null
): String? = rememberHeroResolution(entry, resign, recovery, onHeroRecovered).url

/**
 * RW-169 §7E.4 (Nov 11, 2026): the HELD form of the resolver —
 * thumbnail binds at promotion. The pre-fix resolver state was
 * keyed on the stored reference, so when a re-settle rewrote the
 * reference the resolved state reset to null and the decode-gated
 * image returned to alpha 0 until the network re-signed the new
 * reference — the footer's thumbnail washing out and vanishing at
 * ~14.3 s of the owner's recording.
 *
 * This holder keeps the last URL THIS surface resolved for THIS
 * entry's identity: while a successor reference resolves, the
 * current decoded image stays ([ThumbnailHold]); the display
 * only moves forward to a new non-null URL. A DIFFERENT entry
 * (identity change) starts honestly empty — the hold never leaks
 * one entry's photo onto another.
 */
@Composable
fun rememberHeldResolvedHeroUrl(
    entry: WearIveRunDayEntry,
    resign: (suspend (String) -> String?)? = null,
    recovery: HeroRecoverySeam? = null,
    onHeroRecovered: ((String) -> Unit)? = null
): String? {
    val resolved = rememberResolvedHeroUrl(entry, resign, recovery, onHeroRecovered)
    val identity = remember(entry) {
        WearIveRunCollectionLogic.identityOf(entry)
    }
    // Keyed on the entry's identity: a different entry starts
    // honestly empty; the same entry keeps its last shown URL.
    var held by remember(identity) { mutableStateOf<String?>(null) }
    LaunchedEffect(resolved) {
        if (resolved != null) held = resolved
    }
    return ThumbnailHold.displayed(resolved, held)
}

/**
 * RW-169 §7E.4: a thumbnail slot that never unloads. Renders the
 * last URL that DECODED successfully for [identityKey] as the
 * base layer; a successor [url] loads invisibly above it and
 * replaces the base only once its own decode succeeds. Until
 * anything has decoded, the slot stays empty (the caller's
 * tinted ground shows) — honest for a photo-less entry.
 */
@Composable
fun HeldHeroThumbnail(
    url: String?,
    identityKey: String,
    modifier: Modifier = Modifier
) {
    var shownUrl by remember(identityKey) { mutableStateOf<String?>(null) }
    Box(modifier = modifier) {
        shownUrl?.let { shown ->
            AsyncImage(
                model = shown,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (url != null && url != shownUrl) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Success) {
                        shownUrl = url
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0f)
            )
        }
    }
}
