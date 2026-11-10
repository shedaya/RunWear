package com.runwear.app.weariverun

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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
 * RW-152 §3: resolve an entry's hero to a loadable display URL at
 * render — re-signing its Storage path through [resign] (the
 * production caller passes PersonalHeroService.signedUrlFor via
 * the Make It Me view model). Stable URLs resolve synchronously;
 * a path resolves when its re-sign lands.
 */
@Composable
fun rememberResolvedHeroUrl(
    entry: WearIveRunDayEntry,
    resign: (suspend (String) -> String?)? = null
): String? {
    val stored = remember(entry) { HeroRefResolver.storedRef(entry) }
    val path = remember(stored) { HeroRefResolver.storagePathOf(stored) }
    var resigned by remember(stored) { mutableStateOf<String?>(null) }
    LaunchedEffect(stored) {
        if (path != null && resign != null) {
            resigned = resign(path)
        }
    }
    return HeroRefResolver.resolveDisplay(stored, resigned)
}
