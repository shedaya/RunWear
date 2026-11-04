package com.runwear.app.makeitme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-3 (Y-1 parity, iOS census 2026-11-04): the reference-epoch
 * guard. The iOS recording's central defect: a first look
 * commissioned from a freshly uploaded selfie resolved to a BANKED
 * photo generated from a superseded reference set — another
 * person's photo — presented under "You're in the photo now."
 * Retention of the bank is settled; serving a superseded-epoch
 * photo as the user's current representation is the defect.
 *
 * Android's bank lookup is not epoch-keyed (get-personal-hero is
 * keyed by combination_id; the payload carries no reference epoch),
 * so the guard works from the client's own knowledge: the epoch
 * counter it advances at each reference registration, the per-cell
 * accepted record, and whether a generation lifecycle was witnessed
 * in the current epoch. These tests pin every rule.
 */
class ReferenceEpochPolicyTest {

    private fun mayServe(
        hitHeroId: String? = "hero-new",
        hitVersion: Int = 2,
        acceptedHeroId: String? = null,
        acceptedVersion: Int? = null,
        acceptedEpoch: Int? = null,
        currentEpoch: Int = 0,
        witnessed: Boolean = false
    ): Boolean = ReferenceEpochPolicy.mayServe(
        hitHeroId = hitHeroId,
        hitVersion = hitVersion,
        acceptedHeroId = acceptedHeroId,
        acceptedVersion = acceptedVersion,
        acceptedEpoch = acceptedEpoch,
        currentEpoch = currentEpoch,
        generationWitnessedThisEpoch = witnessed
    )

    @Test
    fun `rule 1 - the standing photo accepted under the current epoch serves`() {
        // The returning-home case (Y-2 second half): a personal
        // photo exists for the home cell and serves — place switches
        // and relaunches move neither the epoch nor the acceptance,
        // so the serve is undisturbed.
        assertTrue(
            mayServe(
                hitHeroId = "hero-1", hitVersion = 3,
                acceptedHeroId = "hero-1", acceptedVersion = 3,
                acceptedEpoch = 1, currentEpoch = 1
            )
        )
        assertTrue(
            mayServe(
                hitHeroId = "hero-1", hitVersion = 3,
                acceptedHeroId = "hero-1", acceptedVersion = 3,
                acceptedEpoch = 0, currentEpoch = 0
            )
        )
    }

    @Test
    fun `rule 2 - the same banked photo carried over a reference replacement is superseded and blocked`() {
        // THE Y-1 pin: the photo this client accepted under the old
        // references is still the bank's answer after new references
        // were registered. Same hero, older epoch — it must not
        // serve as the user's current representation, however it is
        // keyed, and a witnessed flag cannot launder it.
        assertFalse(
            mayServe(
                hitHeroId = "hero-1", hitVersion = 3,
                acceptedHeroId = "hero-1", acceptedVersion = 3,
                acceptedEpoch = 0, currentEpoch = 1
            )
        )
        assertFalse(
            mayServe(
                hitHeroId = "hero-1", hitVersion = 3,
                acceptedHeroId = "hero-1", acceptedVersion = 3,
                acceptedEpoch = 1, currentEpoch = 2,
                witnessed = true
            )
        )
    }

    @Test
    fun `rule 3c - a never-accepted bank hit in a post-boundary epoch is blocked (the fresh-install Y-1 replay)`() {
        // Fresh install: the user registers his own selfie (epoch
        // 0 -> 1) and commissions his first look. The server answers
        // with a banked photo this device has NEVER accepted — in
        // the recording, another person's photo from the old
        // reference set. No generation was witnessed (the answer was
        // "already available", not a queued job): blocked. The
        // truthful stock serves and the look stays un-finished.
        assertFalse(
            mayServe(
                hitHeroId = "friend-hero", hitVersion = 1,
                acceptedHeroId = null, acceptedVersion = null,
                acceptedEpoch = null, currentEpoch = 1,
                witnessed = false
            )
        )
        // A different, newer bank hero is equally unproven in a
        // post-boundary epoch without a witnessed generation.
        assertFalse(
            mayServe(
                hitHeroId = "hero-9", hitVersion = 5,
                acceptedHeroId = "hero-1", acceptedVersion = 3,
                acceptedEpoch = 0, currentEpoch = 1,
                witnessed = false
            )
        )
    }

    @Test
    fun `rule 3a - a delivery through a witnessed generation in the current epoch serves`() {
        // The commissioned look was QUEUED (or read as MAKING) in
        // this epoch, then delivered: generated from the current
        // reference set by construction.
        assertTrue(
            mayServe(
                hitHeroId = "hero-new", hitVersion = 1,
                acceptedHeroId = null, acceptedVersion = null,
                acceptedEpoch = null, currentEpoch = 1,
                witnessed = true
            )
        )
        // A regeneration replacing a superseded photo: different
        // hero, witnessed lifecycle — serves, and is accepted under
        // the new epoch (rule 1 from then on).
        assertTrue(
            mayServe(
                hitHeroId = "hero-new", hitVersion = 2,
                acceptedHeroId = "hero-old", acceptedVersion = 1,
                acceptedEpoch = 0, currentEpoch = 1,
                witnessed = true
            )
        )
    }

    @Test
    fun `rule 3b - with no boundary ever recorded, the bank belongs to the only epoch this device knows`() {
        // A returning user whose references were never replaced on
        // this device: the existing personal photo for the home cell
        // serves (Y-2 second half) — the guard must not starve the
        // ordinary case it was not built for.
        assertTrue(
            mayServe(
                hitHeroId = "hero-1", hitVersion = 4,
                acceptedHeroId = null, acceptedVersion = null,
                acceptedEpoch = null, currentEpoch = 0,
                witnessed = false
            )
        )
    }

    @Test
    fun `already available is not a witness - only a queued lifecycle is`() {
        // Modeled directly: in a post-boundary epoch, the same hit
        // flips purely on the witness flag — and the witness is set
        // only by Queued/AlreadyQueued outcomes and MAKING reads,
        // never by AlreadyAvailable (the repository pins that
        // wiring; see PersonalHeroRepository.requestGeneration).
        assertFalse(
            mayServe(
                hitHeroId = "hero-x", hitVersion = 1,
                currentEpoch = 3, witnessed = false
            )
        )
        assertTrue(
            mayServe(
                hitHeroId = "hero-x", hitVersion = 1,
                currentEpoch = 3, witnessed = true
            )
        )
    }

    @Test
    fun `hero identity - version decides, a missing id on one side still matches itself`() {
        assertTrue(
            ReferenceEpochPolicy.isSameHero("a", 2, "a", 2)
        )
        assertTrue(
            // A pre-hero-id cache entry re-deriving its id.
            ReferenceEpochPolicy.isSameHero("a", 2, null, 2)
        )
        assertTrue(
            ReferenceEpochPolicy.isSameHero(null, 2, "a", 2)
        )
        assertFalse(
            ReferenceEpochPolicy.isSameHero("a", 2, "b", 2)
        )
        assertFalse(
            ReferenceEpochPolicy.isSameHero("a", 3, "a", 2)
        )
    }

    @Test
    fun `the serving seam consults the guard before any hit is written or returned`() {
        // Source pin (the repository is not unit-hostable — its
        // service performs network calls): resolveHit must apply
        // ReferenceEpochPolicy, and a blocked hit must not reach
        // the cache writer.
        val repo = sourceFile(
            "app/src/main/java/com/runwear/app/makeitme/PersonalHeroRepository.kt"
        ).readText()
        val resolveStart = repo.indexOf("private suspend fun resolveHit")
        val resolveEnd = repo.indexOf("private suspend fun readCachedEntry")
        assertTrue(resolveStart in 0 until resolveEnd)
        val body = repo.substring(resolveStart, resolveEnd)
        assertTrue(body.contains("ReferenceEpochPolicy.mayServe("))
        assertTrue(
            "the guard must run before the cache write",
            body.indexOf("ReferenceEpochPolicy.mayServe(") <
                body.indexOf("writeCachedEntry(")
        )
        assertTrue(
            "registration must advance the epoch",
            repo.contains("advanceReferenceEpoch()")
        )
    }

    private fun sourceFile(androidRootRelative: String): java.io.File {
        var dir: java.io.File? =
            java.io.File(System.getProperty("user.dir")).absoluteFile
        val candidates = listOf(
            androidRootRelative,
            "RunWear-android/$androidRootRelative",
            "../$androidRootRelative"
        )
        while (dir != null) {
            for (candidate in candidates) {
                val file = java.io.File(dir, candidate)
                if (file.isFile) return file
            }
            dir = dir.parentFile
        }
        org.junit.Assert.fail("source file not found: $androidRootRelative")
        throw AssertionError("unreachable")
    }
}
