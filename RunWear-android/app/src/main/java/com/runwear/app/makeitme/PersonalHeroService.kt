package com.runwear.app.makeitme

import android.util.Base64
import android.util.Log
import androidx.datastore.preferences.core.stringPreferencesKey
import com.runwear.shared.data.repository.PreferencesRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The RW-14 personal-hero backend's REST client (backend report §2 —
 * the serving contract). The backend is live in Supabase project
 * ebicqznlcjbqcukjfzcf; every personal function derives the user from
 * the verified JWT and accepts no user id from the caller.
 *
 * Identity: RunWear has no account system (spec J6 — per-user identity
 * is keyed to the install for free users; keyed to the subscription
 * for Premium, RW-137 DESIGN §6), so the client obtains an anonymous
 * Supabase Auth session and persists it in the app's DataStore. The
 * anonymous user is a real auth.users row: RLS owner policies apply
 * unchanged. A Premium restore can re-key a prior identity's data
 * into the restored session's uid — server-side, on RevenueCat proof
 * only (see [reconcileEntitlement] / [requestIdentityAdoption]); the
 * client never claims an identity, it only passes the server's own
 * reported source back as a lookup hint.
 *
 * Anonymous sign-ins are ENABLED on the project (verified 2026-10-31:
 * Sol's device runs a live anonymous session on this path). If a
 * session still can't be established (offline, provider regression),
 * every caller degrades to the pool hero / stock state — the personal
 * layer goes inert, never broken.
 *
 * This client talks REST directly (Ktor) rather than through the
 * supabase-kt client in the shared module: that client is built
 * anon-only for the public pool and carries no Auth session; bolting a
 * second, session-carrying client beside it keeps the pool path — the
 * app's critical path — untouched.
 */
@Singleton
open class PersonalHeroService @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val http: HttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HttpClient(Android) {
            install(ContentNegotiation) { json(json) }
            engine { connectTimeout = 15_000; socketTimeout = 30_000 }
        }
    }

    private val sessionMutex = Mutex()

    // ── Session ────────────────────────────────────────────────────────

    private data class Session(
        val accessToken: String,
        val refreshToken: String,
        val userId: String
    )

    private fun jwtExpirySeconds(accessToken: String): Long {
        return try {
            val payload = accessToken.split(".").getOrNull(1) ?: return 0L
            val decoded = Base64.decode(
                payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            val obj = json.parseToJsonElement(String(decoded, Charsets.UTF_8)).jsonObject
            obj["exp"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private suspend fun storedSession(): Session? {
        val access = preferencesRepository.getString(KEY_ACCESS_TOKEN) ?: return null
        val refresh = preferencesRepository.getString(KEY_REFRESH_TOKEN) ?: return null
        val userId = preferencesRepository.getString(KEY_USER_ID) ?: return null
        return Session(access, refresh, userId)
    }

    private suspend fun storeSession(session: Session) {
        preferencesRepository.setString(KEY_ACCESS_TOKEN, session.accessToken)
        preferencesRepository.setString(KEY_REFRESH_TOKEN, session.refreshToken)
        preferencesRepository.setString(KEY_USER_ID, session.userId)
    }

    open suspend fun clearSession() {
        preferencesRepository.remove(KEY_ACCESS_TOKEN)
        preferencesRepository.remove(KEY_REFRESH_TOKEN)
        preferencesRepository.remove(KEY_USER_ID)
    }

    /** The current session's user id, if a session exists (no network). */
    open suspend fun currentUserId(): String? = storedSession()?.userId

    /**
     * Ensure a usable session: reuse a non-expired token, refresh an
     * expired one, or sign in anonymously. True when a session is
     * usable; false when none can be established (offline, or anonymous
     * sign-ins disabled server-side) — callers treat false as "personal
     * layer unavailable".
     */
    open suspend fun ensureSession(): Boolean = session() != null

    private suspend fun session(): Session? = sessionMutex.withLock {
        val existing = storedSession()
        val nowSec = System.currentTimeMillis() / 1000L
        val tokenValid = existing != null &&
            jwtExpirySeconds(existing.accessToken) > nowSec + 60L
        // RW-16.15(iv): every branch is SessionGuardPolicy's decision —
        // a stored identity is replaced ONLY on a definitive server
        // rejection of its refresh token, never on an unproven failure
        // (the pre-guard flow cleared + re-minted on ANY refresh
        // failure, which silently orphans the old uid's photos,
        // profile, and entitlement).
        if (existing == null) {
            check(
                SessionGuardPolicy.decide(false, false, null) ==
                    SessionGuardPolicy.Action.MINT_FRESH
            )
            val fresh = anonymousSignIn()
            if (fresh != null) storeSession(fresh)
            return@withLock fresh
        }
        if (SessionGuardPolicy.decide(true, tokenValid, null) ==
            SessionGuardPolicy.Action.REUSE_STORED
        ) {
            return@withLock existing
        }
        val (outcome, refreshed) = refreshSession(existing.refreshToken)
        when (SessionGuardPolicy.decide(true, false, outcome)) {
            SessionGuardPolicy.Action.USE_REFRESHED -> {
                storeSession(refreshed!!)
                refreshed
            }
            SessionGuardPolicy.Action.CLEAR_AND_MINT -> {
                // The server definitively refused the refresh token:
                // the identity is gone, a fresh start is the recovery.
                clearSession()
                val fresh = anonymousSignIn()
                if (fresh != null) storeSession(fresh)
                fresh
            }
            else -> null // KEEP_STORED_AND_FAIL: stored session left
            // untouched; this call degrades, the next one retries.
        }
    }

    private suspend fun parseSession(response: HttpResponse): Session? {
        if (!response.status.isSuccess()) {
            Log.w(TAG, "auth call failed: ${response.status} ${runCatching { response.bodyAsText() }.getOrNull()?.take(200)}")
            return null
        }
        val obj = runCatching { json.parseToJsonElement(response.bodyAsText()).jsonObject }
            .getOrNull() ?: return null
        val access = obj["access_token"]?.jsonPrimitive?.contentOrNull ?: return null
        val refresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull ?: return null
        val userId = obj["user"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull ?: return null
        return Session(access, refresh, userId)
    }

    private suspend fun anonymousSignIn(): Session? = runCatching {
        val response = http.post("$BASE_URL/auth/v1/signup") {
            header("apikey", ANON_KEY)
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        parseSession(response)
    }.onFailure { Log.w(TAG, "anonymous sign-in failed", it) }.getOrNull()

    /**
     * Refresh, classified for [SessionGuardPolicy]: REJECTED only when
     * the token endpoint definitively refused the token (HTTP
     * 400/401/403, via [SessionGuardPolicy.classifyHttpFailure]); a
     * transport error, a 5xx, a 408/409/425/429 (GoTrue rate-limits
     * this endpoint — a throttled refresh proves nothing about the
     * token), or an unparseable success is UNKNOWN — the stored
     * identity must survive all of them (RW-16.15(iv)).
     */
    private suspend fun refreshSession(
        refreshToken: String
    ): Pair<SessionGuardPolicy.RefreshOutcome, Session?> {
        val response = runCatching {
            http.post("$BASE_URL/auth/v1/token?grant_type=refresh_token") {
                header("apikey", ANON_KEY)
                contentType(ContentType.Application.Json)
                setBody("""{"refresh_token":"$refreshToken"}""")
            }
        }.onFailure { Log.w(TAG, "token refresh failed", it) }.getOrElse {
            return SessionGuardPolicy.RefreshOutcome.UNKNOWN to null
        }
        if (response.status.isSuccess()) {
            val session = parseSession(response)
            return if (session != null) {
                SessionGuardPolicy.RefreshOutcome.SUCCEEDED to session
            } else {
                SessionGuardPolicy.RefreshOutcome.UNKNOWN to null
            }
        }
        return SessionGuardPolicy.classifyHttpFailure(response.status.value) to null
    }

    // ── Edge functions ─────────────────────────────────────────────────

    sealed interface FunctionResult {
        data class Ok(val body: JsonObject) : FunctionResult
        data class HttpError(val status: Int, val errorCode: String?, val body: JsonObject?) : FunctionResult
        data object Unavailable : FunctionResult
    }

    /** POST one edge function with the user's JWT. */
    suspend fun callFunction(slug: String, bodyJson: String): FunctionResult {
        val session = session() ?: return FunctionResult.Unavailable
        return runCatching {
            val response = http.post("$BASE_URL/functions/v1/$slug") {
                header("apikey", ANON_KEY)
                header("Authorization", "Bearer ${session.accessToken}")
                contentType(ContentType.Application.Json)
                setBody(bodyJson)
            }
            val text = response.bodyAsText()
            val parsed = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            if (response.status.isSuccess()) {
                FunctionResult.Ok(parsed ?: JsonObject(emptyMap()))
            } else {
                FunctionResult.HttpError(
                    status = response.status.value,
                    errorCode = parsed?.get("error")?.jsonPrimitive?.contentOrNull,
                    body = parsed
                )
            }
        }.onFailure { Log.w(TAG, "function $slug failed", it) }
            .getOrElse { FunctionResult.Unavailable }
    }

    // ── Serving (get-personal-hero) ────────────────────────────────────

    data class HeroLookup(
        val found: Boolean,
        val imageUrl: String?,
        val version: Int?,
        val heroId: String?,
        /** RW-16.16: the server's per-cell look state (v5+ payloads). */
        val lookState: String? = null,
        /** RW-16.16: the failure reason CLASS for a failed look. */
        val lookReason: String? = null,
        /**
         * RW-26: the convergence serve kind the server reports —
         * "exact" for the cell's own banked photo, "scaffold" when
         * the serve is the user's OWN banked photo carrying the
         * exact prescription under the nearest conditions while the
         * exact photo generates. Absent on older payloads.
         */
        val serveKind: String? = null
    )

    suspend fun getPersonalHero(combinationId: String): HeroLookup? {
        val result = callFunction(
            "get-personal-hero",
            """{"combination_id":"$combinationId"}"""
        )
        val body = (result as? FunctionResult.Ok)?.body ?: return null
        return HeroLookup(
            found = body["found"]?.jsonPrimitive?.booleanOrNull ?: false,
            imageUrl = body["image_url"]?.jsonPrimitive?.contentOrNull,
            version = body["version"]?.jsonPrimitive?.intOrNull,
            heroId = body["hero_id"]?.jsonPrimitive?.contentOrNull,
            lookState = body["look_state"]?.jsonPrimitive?.contentOrNull,
            lookReason = body["look_reason"]?.jsonPrimitive?.contentOrNull,
            // RW-130 contract v2: serve_tier is the single
            // classification (exact | scaffold | similar | none);
            // serve_kind / the legacy scaffold boolean remain as
            // fallbacks for payloads from the older server line.
            serveKind = body["serve_tier"]?.jsonPrimitive?.contentOrNull
                ?: body["serve_kind"]?.jsonPrimitive?.contentOrNull
                ?: if (body["scaffold"]?.jsonPrimitive?.booleanOrNull == true) "scaffold" else null
        )
    }

    // ── Generation requests (request-personal-generation) ──────────────

    sealed interface RequestOutcome {
        data class Queued(val jobId: String?) : RequestOutcome
        data class AlreadyAvailable(val heroId: String?) : RequestOutcome
        data class AlreadyQueued(val jobId: String?) : RequestOutcome
        data object AllowanceExhausted : RequestOutcome
        data object ConsentRequired : RequestOutcome
        data object ReferencesRequired : RequestOutcome
        data class Failed(val reason: String) : RequestOutcome
    }

    suspend fun recordConsent(gender: String): Boolean {
        val body = """{"action":"record_consent","consent_version":"${MakeItMeStrings.CONSENT_VERSION}","attested_own_likeness":true,"attested_adult":true,"gender":"$gender"}"""
        return callFunction("request-personal-generation", body) is FunctionResult.Ok
    }

    suspend fun registerReferences(storagePaths: List<String>): Boolean {
        val pathsJson = storagePaths.joinToString(",") { "\"$it\"" }
        val body = """{"action":"register_references","storage_paths":[$pathsJson]}"""
        return callFunction("request-personal-generation", body) is FunctionResult.Ok
    }

    suspend fun requestGeneration(combinationId: String, outfitItems: List<String>): RequestOutcome {
        val itemsJson = outfitItems.joinToString(",") { "\"$it\"" }
        val body = """{"combination_id":"$combinationId","outfit_items":[$itemsJson]}"""
        return when (val result = callFunction("request-personal-generation", body)) {
            is FunctionResult.Ok -> {
                val status = result.body["status"]?.jsonPrimitive?.contentOrNull
                when (status) {
                    "queued" -> RequestOutcome.Queued(result.body["job_id"]?.jsonPrimitive?.contentOrNull)
                    "already_available" -> RequestOutcome.AlreadyAvailable(result.body["hero_id"]?.jsonPrimitive?.contentOrNull)
                    "already_queued" -> RequestOutcome.AlreadyQueued(result.body["job_id"]?.jsonPrimitive?.contentOrNull)
                    else -> RequestOutcome.Failed("unexpected_status:$status")
                }
            }
            is FunctionResult.HttpError -> when (result.errorCode) {
                "allowance_exhausted" -> RequestOutcome.AllowanceExhausted
                "consent_required" -> RequestOutcome.ConsentRequired
                "references_required" -> RequestOutcome.ReferencesRequired
                else -> RequestOutcome.Failed("http_${result.status}:${result.errorCode}")
            }
            FunctionResult.Unavailable -> RequestOutcome.Failed("unavailable")
        }
    }

    /**
     * RW-16.27: flag one of the caller's own personalized heroes
     * ("This doesn't look like me", spec §4) — the same flag_hero
     * action iOS calls. The hero must belong to the caller (a missing
     * hero and someone else's are the same 404 server-side). On
     * success the hero is retired in the same call (it stops being
     * served on the very next get-personal-hero), a snapshot lands
     * in personal_hero_flags, and a regeneration job is queued for
     * the cell unless an active job already covers it. True only
     * when the server confirmed the flag.
     */
    suspend fun flagPersonalHero(heroId: String, reason: PersonalHeroFlagReason): Boolean {
        val body = """{"action":"flag_hero","hero_id":"$heroId","reason":"${reason.code}"}"""
        return callFunction("request-personal-generation", body) is FunctionResult.Ok
    }

    // ── Subscriber fill / delete ───────────────────────────────────────

    /**
     * GEN-5 (RW-28 Addendum 8, Defect 1 — convergence diagnosis,
     * Nov 5, 2026): ask the deployed start-personal-fill (v13,
     * RW-16.30 convergence model) to enqueue the forecast-imminent
     * cells THIS caller names in [priorityCells] — the owed fill
     * set: the user's 7-day forecast cells first (imminent first),
     * then the 20-cell canonical core matrix (the scaffold
     * substrate), each id derived by the serve path's own
     * derivation (Addendum 8, second follow-up).
     * The deployed function enqueues ONLY named cells (intersected
     * server-side with the canonical launch set); the gen-4 client
     * called it with an empty body, so no fill was ever requested
     * and the bank grew only by cells the user personally opened.
     * Re-calling with the same cells is harmless by design.
     */
    suspend fun startPersonalFill(priorityCells: List<String>): Boolean =
        callFunction("start-personal-fill", fillRequestBody(priorityCells)) is FunctionResult.Ok

    /**
     * Entitlement reconciliation (the RW-16.12 backstop): ask the
     * server to bring the caller's `entitled` flag in line with
     * RevenueCat's record. The client asserts nothing — the function
     * derives the user from this session's JWT and verifies the
     * subscriber state against RevenueCat itself before acting; on a
     * grant it also kicks start-personal-fill server-side — under
     * the deployed convergence contract that kick names no cells
     * and enqueues nothing, so the fill trigger of record is the
     * client's own startPersonalFill call with forecast cells
     * (Defect 1). Silent and idempotent by design (the function
     * writes only on a real transition). True when the call
     * completed; the caller re-reads the profile afterwards to
     * mirror any change.
     */
    /**
     * RW-137 (DESIGN §4.4 trigger 2): what one reconcile call
     * established. [completed] is the pre-RW-137 Boolean contract
     * (the call completed and the caller should re-read the
     * profile). [adoptionAvailable] is the server-reported source
     * uid whose personal layer RevenueCat proof links to this
     * caller — null when the response carries no adoption hint
     * (including every pre-RW-137 server payload: the field is
     * additive and absent means no adoption).
     */
    data class ReconcileOutcome(
        val completed: Boolean,
        val adoptionAvailable: String? = null
    )

    open suspend fun reconcileEntitlement(): ReconcileOutcome {
        // GEN-5 (RW-28 Addendum 8, Item 4a): name this client's
        // platform so the function's RevenueCat lookup queries the
        // matching storefront view instead of a hard-coded one.
        val result = callFunction(
            "reconcile-personal-entitlement", "{\"platform\":\"android\"}"
        )
        val body = (result as? FunctionResult.Ok)?.body
            ?: return ReconcileOutcome(completed = false)
        return ReconcileOutcome(
            completed = true,
            adoptionAvailable = parseAdoptionAvailable(body)
        )
    }

    /**
     * RW-137 (DESIGN §4.4 trigger 2): ask the server to execute the
     * adoption the reconcile response reported. [sourceUserId] is a
     * LOOKUP HINT only — request-identity-adoption re-verifies the
     * RevenueCat record server-side before re-keying anything, so a
     * stale or wrong hint degrades to a silent no-op server-side.
     * Every failure here is likewise a silent no-op for the caller:
     * adoption is a standing Premium property, never a user-facing
     * flow, and there is no error state to render. True only when
     * the endpoint confirmed the adoption executed.
     */
    open suspend fun requestIdentityAdoption(sourceUserId: String): Boolean {
        // GEN-5 Item 4a discipline, same as reconcile: name this
        // client's platform — the endpoint's RevenueCat lookup
        // defaults to the iOS storefront view when it is absent.
        val body = """{"source_user_id":"$sourceUserId","platform":"android"}"""
        return callFunction("request-identity-adoption", body) is FunctionResult.Ok
    }

    data class DeleteOutcome(val storageRemoved: Int, val totalRowsRemoved: Int)

    suspend fun deleteMyPhotos(): DeleteOutcome? {
        val result = callFunction("delete-my-photos", "{}")
        val body = (result as? FunctionResult.Ok)?.body ?: return null
        val rows = body["rows_removed"]?.jsonObject
        val totalRows = rows?.values?.sumOf { it.jsonPrimitive.intOrNull ?: 0 } ?: 0
        return DeleteOutcome(
            storageRemoved = body["storage_objects_removed"]?.jsonPrimitive?.intOrNull ?: 0,
            totalRowsRemoved = totalRows
        )
    }

    data class DataDeleteOutcome(
        val storageRemoved: Int,
        val totalRowsRemoved: Int,
        val accountDeleted: Boolean
    )

    /**
     * Full account-data deletion (delete-my-data, RW-16.30 decision
     * 5a): the photos sweep plus the webhook log and the account
     * itself. The server reports a failed account deletion in the
     * receipt (account_deleted = false) instead of failing the call;
     * that maps to a null outcome here so callers claim nothing —
     * the data is gone but the account is not, and the user's retry
     * completes it.
     */
    open suspend fun deleteMyData(): DataDeleteOutcome? {
        val result = callFunction("delete-my-data", "{}")
        val body = (result as? FunctionResult.Ok)?.body ?: return null
        if (body["account_deleted"]?.jsonPrimitive?.booleanOrNull != true) return null
        val rows = body["rows_removed"]?.jsonObject
        val totalRows = rows?.values?.sumOf { it.jsonPrimitive.intOrNull ?: 0 } ?: 0
        return DataDeleteOutcome(
            storageRemoved = body["storage_objects_removed"]?.jsonPrimitive?.intOrNull ?: 0,
            totalRowsRemoved = totalRows,
            accountDeleted = true
        )
    }

    // ── Profile read (PostgREST, owner RLS) ────────────────────────────

    data class ProfileSnapshot(
        val personalizationOn: Boolean,
        val freeGenerationsUsed: Int,
        val entitled: Boolean,
        val gender: String
    )

    open suspend fun fetchProfile(): ProfileSnapshot? {
        val session = session() ?: return null
        return runCatching {
            val response = http.get(
                "$BASE_URL/rest/v1/personal_profiles?select=personalization_on,free_generations_used,entitled,gender"
            ) {
                header("apikey", ANON_KEY)
                header("Authorization", "Bearer ${session.accessToken}")
            }
            if (!response.status.isSuccess()) return@runCatching null
            val arr = json.parseToJsonElement(response.bodyAsText()).jsonArray
            val row = arr.firstOrNull()?.jsonObject ?: return@runCatching null
            ProfileSnapshot(
                personalizationOn = row["personalization_on"]?.jsonPrimitive?.booleanOrNull ?: false,
                freeGenerationsUsed = row["free_generations_used"]?.jsonPrimitive?.intOrNull ?: 0,
                entitled = row["entitled"]?.jsonPrimitive?.booleanOrNull ?: false,
                gender = row["gender"]?.jsonPrimitive?.contentOrNull ?: "UNISEX"
            )
        }.onFailure { Log.w(TAG, "profile fetch failed", it) }.getOrNull()
    }

    /**
     * GEN-3 (convergence band): the caller's REAL bank coverage.
     * GEN-5 (RW-28 Addendum 8, Defect 2 — convergence diagnosis,
     * Nov 5, 2026): [total] is the BANK'S TARGET SET — the fill
     * set actually owed (the 20-cell core matrix plus the current
     * forecast horizon — the same composed set the client names
     * in priority_cells), never the engaged set. The gen-4
     * denominator (banked ∪ ever-enqueued cells) manufactured
     * ~90% at 8-of-48 true coverage and would have read 100% with
     * 40 target cells still missing. [ready] counts only banked
     * cells that belong to the target. Both tables are owner-read
     * under RLS (the jobs policy exists "for progress UI"). Null
     * when there is no session or either read fails — the band
     * never shows invented counts.
     */
    data class BankCoverage(
        val ready: Int,
        val total: Int,
        /**
         * RW-126 (Nov 7, 2026): target cells whose generation ended
         * terminally FAILED with no delivered hero — recorded
         * failures (including the duplicate-insert races RW-126(b)
         * proved were being miscounted as open work). The band's
         * settle rule counts them as SETTLED, never as outstanding:
         * a bank whose remaining cells all failed terminally is
         * finished, and the band must retire instead of announcing
         * generation that no longer exists.
         */
        val failed: Int = 0,
        /**
         * RW-151 client (Nov 10, 2026): whether the account holds
         * any NON-TERMINAL generation job (QUEUED / PROCESSING) as
         * of this read — the reconcile rule's in-flight input. A
         * band that shows outstanding work while this is false and
         * no fill is outstanding must re-issue the fill once
         * instead of presenting "making" with nothing in flight.
         */
        val hasNonTerminalJob: Boolean = false
    ) {
        /** Target cells neither delivered nor terminally failed. */
        val outstanding: Int get() = (total - ready - failed).coerceAtLeast(0)
    }

    suspend fun fetchBankCoverage(
        targetCells: Set<String>,
        fillableCells: Set<String> = emptySet()
    ): BankCoverage? {
        val session = session() ?: return null
        suspend fun cells(table: String): Set<String>? = runCatching {
            val response = http.get(
                "$BASE_URL/rest/v1/$table?select=combination_id"
            ) {
                header("apikey", ANON_KEY)
                header("Authorization", "Bearer ${session.accessToken}")
            }
            if (!response.status.isSuccess()) return@runCatching null
            json.parseToJsonElement(response.bodyAsText()).jsonArray
                .mapNotNull { it.jsonObject["combination_id"]?.jsonPrimitive?.contentOrNull }
                .toSet()
        }.onFailure { Log.w(TAG, "bank coverage read failed ($table)", it) }.getOrNull()
        val heroCells = cells("personal_heroes") ?: return null
        // RW-126: the jobs read carries STATUS so terminal failures
        // settle the band (see BankCoverage.failed). A cell counts
        // failed only when a FAILED job exists and no hero landed.
        val jobStatuses = runCatching {
            val response = http.get(
                "$BASE_URL/rest/v1/personal_generation_jobs?select=combination_id,status"
            ) {
                header("apikey", ANON_KEY)
                header("Authorization", "Bearer ${session.accessToken}")
            }
            if (!response.status.isSuccess()) return@runCatching null
            json.parseToJsonElement(response.bodyAsText()).jsonArray
                .mapNotNull { row ->
                    val id = row.jsonObject["combination_id"]?.jsonPrimitive?.contentOrNull
                        ?: return@mapNotNull null
                    val status = row.jsonObject["status"]?.jsonPrimitive?.contentOrNull
                        ?: return@mapNotNull null
                    id to status
                }
        }.onFailure { Log.w(TAG, "bank coverage job read failed", it) }.getOrNull()
            ?: return null
        val jobCells = jobStatuses.map { it.first }.toSet()
        val failedCells = jobStatuses
            .filter { it.second == "FAILED" }
            .map { it.first }
            .toSet() - heroCells
        // RW-151 client: QUEUED / PROCESSING are the non-terminal
        // states (COMPLETED / FAILED are terminal) — the reconcile
        // rule reads in-flight truth from this same jobs read.
        val hasNonTerminalJob = jobStatuses.any {
            it.second == "QUEUED" || it.second == "PROCESSING"
        }
        return deriveBankCoverage(
            heroCells, jobCells, targetCells, failedCells,
            fillableCells, hasNonTerminalJob
        )
    }

    /**
     * GEN-6 WAVE C (RW-61): the looks library's bank read — the
     * same two owner-read tables as [fetchBankCoverage], but
     * carrying each job's STATUS so a gate-failed cell (a FAILED
     * job with no banked hero) is visible as itself. Returns
     * (heroCells, jobStatuses); null when either read fails.
     */
    suspend fun fetchBankCells(): Pair<Set<String>, Map<String, String>>? {
        val session = session() ?: return null
        suspend fun read(table: String, select: String): List<kotlinx.serialization.json.JsonObject>? =
            runCatching {
                val response = http.get("$BASE_URL/rest/v1/$table?select=$select") {
                    header("apikey", ANON_KEY)
                    header("Authorization", "Bearer ${session.accessToken}")
                }
                if (!response.status.isSuccess()) return@runCatching null
                json.parseToJsonElement(response.bodyAsText()).jsonArray
                    .map { it.jsonObject }
            }.onFailure { Log.w(TAG, "bank cells read failed ($table)", it) }.getOrNull()
        val heroes = read("personal_heroes", "combination_id") ?: return null
        val jobs = read("personal_generation_jobs", "combination_id,status") ?: return null
        val heroCells = heroes.mapNotNull {
            it["combination_id"]?.jsonPrimitive?.contentOrNull
        }.toSet()
        val jobStatuses = jobs.mapNotNull { row ->
            val id = row["combination_id"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            val status = row["status"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            id to status
        }.toMap()
        return heroCells to jobStatuses
    }

    /**
     * RW-166 (Nov 10, 2026): the bank as combination_id →
     * storage_path, WITHOUT signing — the Wear I've Run re-link
     * recovery's lookup table (HeroRecovery). The same
     * owner-read personal_heroes table [fetchBankCells] reads;
     * ordered by combination_id so the first row per cell wins
     * deterministically, as on iOS. Null when the read fails —
     * recovery then simply does not happen and the entry stays
     * honestly empty.
     */
    suspend fun fetchBankHeroPaths(): Map<String, String>? {
        val session = session() ?: return null
        return runCatching {
            val response = http.get(
                "$BASE_URL/rest/v1/personal_heroes" +
                    "?select=combination_id,storage_path" +
                    "&order=combination_id.asc"
            ) {
                header("apikey", ANON_KEY)
                header("Authorization", "Bearer ${session.accessToken}")
            }
            if (!response.status.isSuccess()) return@runCatching null
            val paths = LinkedHashMap<String, String>()
            json.parseToJsonElement(response.bodyAsText()).jsonArray
                .forEach { row ->
                    val id = row.jsonObject["combination_id"]
                        ?.jsonPrimitive?.contentOrNull
                    val path = row.jsonObject["storage_path"]
                        ?.jsonPrimitive?.contentOrNull
                    if (id != null && !path.isNullOrBlank() &&
                        !paths.containsKey(id)
                    ) {
                        paths[id] = path
                    }
                }
            paths
        }.onFailure { Log.w(TAG, "bank hero paths read failed", it) }
            .getOrNull()
    }

    /** The caller's registered reference-photo storage paths, in order. */
    open suspend fun fetchReferencePaths(): List<String> {
        val session = session() ?: return emptyList()
        return runCatching {
            val response = http.get(
                "$BASE_URL/rest/v1/personal_reference_photos?select=storage_path,position&order=position.asc"
            ) {
                header("apikey", ANON_KEY)
                header("Authorization", "Bearer ${session.accessToken}")
            }
            if (!response.status.isSuccess()) return@runCatching emptyList()
            json.parseToJsonElement(response.bodyAsText()).jsonArray
                .mapNotNull { it.jsonObject["storage_path"]?.jsonPrimitive?.contentOrNull }
        }.getOrElse { emptyList() }
    }

    // ── Storage ────────────────────────────────────────────────────────

    /** Upload one reference photo under the caller's own {uid}/refs/. */
    suspend fun uploadReferencePhoto(path: String, jpegBytes: ByteArray): Boolean {
        val session = session() ?: return false
        return runCatching {
            val response = http.post("$BASE_URL/storage/v1/object/$BUCKET/$path") {
                header("apikey", ANON_KEY)
                header("Authorization", "Bearer ${session.accessToken}")
                header("x-upsert", "true")
                contentType(ContentType.Image.JPEG)
                setBody(jpegBytes)
            }
            response.status.isSuccess()
        }.onFailure { Log.w(TAG, "reference upload failed", it) }.getOrElse { false }
    }

    /** A short-lived signed URL for displaying one of the caller's refs. */
    suspend fun signedUrlFor(path: String, expiresInSeconds: Int = 3600): String? {
        val session = session() ?: return null
        return runCatching {
            val response = http.post("$BASE_URL/storage/v1/object/sign/$BUCKET/$path") {
                header("apikey", ANON_KEY)
                header("Authorization", "Bearer ${session.accessToken}")
                contentType(ContentType.Application.Json)
                setBody("""{"expiresIn":$expiresInSeconds}""")
            }
            if (!response.status.isSuccess()) return@runCatching null
            val signed = json.parseToJsonElement(response.bodyAsText()).jsonObject["signedURL"]
                ?.jsonPrimitive?.contentOrNull ?: return@runCatching null
            // RW-16.15: the API's signedURL is relative to
            // /storage/v1 — SignedUrlPolicy owns the join (the
            // bare-host join 404'd every tile on vc21).
            SignedUrlPolicy.resolve(BASE_URL, signed)
        }.getOrNull()
    }

    companion object {
        private const val TAG = "PersonalHero"
        const val BASE_URL = "https://ebicqznlcjbqcukjfzcf.supabase.co"
        const val BUCKET = "personal-heroes"

        // The project's public anon key — the same key the shared pool
        // client ships with (HeroImageRepository). Safe to ship: all
        // personal data sits behind JWT-scoped RLS.
        private const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWNxem5sY2picWN1a2pmemNmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk1NDA5MzgsImV4cCI6MjA4NTExNjkzOH0.0Zl7DF4y6riHWzNEDqMwtYZerbFVXAlpFGbeJ3S1Bg4"

        private val KEY_ACCESS_TOKEN = stringPreferencesKey("mim_access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("mim_refresh_token")
        private val KEY_USER_ID = stringPreferencesKey("mim_user_id")
    }
}

/**
 * GEN-3 (convergence band): coverage from the two owner-read cell
 * sets. GEN-5 (RW-28 Addendum 8, Defect 2): when the fill's target
 * set is known (the forecast cells the client names — see
 * [fillRequestBody]), total IS that set and ready counts only
 * banked cells inside it, so the bar cannot reach 100% while
 * target cells are missing. Before any target has been derived
 * this session (the transient before the first forecast load),
 * the engaged union is the fallback denominator — the only state
 * in which it can still appear, and it self-corrects on the first
 * derivation. Pure, so the band's counts are unit-pinned without
 * a network.
 */
internal fun deriveBankCoverage(
    heroCells: Set<String>,
    jobCells: Set<String>,
    targetCells: Set<String> = emptySet(),
    failedCells: Set<String> = emptySet(),
    /**
     * RW-151 client (Nov 10, 2026; DESIGN.md §5): the FILLABLE set.
     * Empty (the default, for callers that predate the amendment)
     * means every target cell is treated as fillable — the
     * pre-amendment denominator. When supplied, the denominator is
     * |Target ∩ Fillable| only: a cell the fill contract cannot
     * enqueue can never again hold the band open.
     */
    fillableCells: Set<String> = emptySet(),
    hasNonTerminalJob: Boolean = false
): PersonalHeroService.BankCoverage =
    if (targetCells.isNotEmpty()) {
        val denominator = if (fillableCells.isEmpty()) {
            targetCells
        } else {
            targetCells intersect fillableCells
        }
        PersonalHeroService.BankCoverage(
            ready = heroCells.count { it in targetCells },
            total = denominator.size,
            // RW-126: terminally failed target cells (recorded,
            // no hero) are settled — they never hold the band open.
            failed = failedCells.count { it in targetCells },
            hasNonTerminalJob = hasNonTerminalJob
        )
    } else {
        // RW-FIXWAVE (architecture review §2 row 19): NO engaged-
        // union fallback. Before the session's first forecast
        // derivation the denominator is UNKNOWN, and the retired
        // gen-4 union formula manufactured a wrong fraction for
        // exactly that window of every cold session (worse after
        // process death, when the in-memory target is gone but
        // heroes/jobs persist). Unknown is reported as total 0,
        // which the band policy renders as no band — an honest
        // absence instead of an invented percentage. The
        // repository also persists the target set (see
        // PersonalHeroRepository) so the window is one read long.
        PersonalHeroService.BankCoverage(ready = 0, total = 0, failed = 0)
    }

/**
 * RW-137 (DESIGN §4.4 trigger 2): the reconcile response's
 * adoption hint — `adoption_available` as a nullable string field
 * (additive: absent, null, non-string, or blank all mean "no
 * adoption reported"). Pure, so the client half of the reconcile
 * contract is unit-pinned without a network. Whether the hint is
 * acted on is [IdentityAdoptionPolicy]'s decision; whether it is
 * TRUE is the server's — request-identity-adoption re-verifies
 * against RevenueCat before re-keying anything.
 */
internal fun parseAdoptionAvailable(body: JsonObject): String? {
    // Only a STRING primitive is a uid hint — a numeric/boolean
    // value must not coerce into one (contentOrNull would happily
    // stringify it).
    val primitive = body["adoption_available"]?.jsonPrimitive
    if (primitive == null || !primitive.isString) return null
    return primitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * GEN-5 (RW-28 Addendum 8, Defect 1): the start-personal-fill
 * request body — the forecast-derived priority cells, in forecast
 * order, deduplicated. Pure, so the contract the gate checks
 * (grant → fill requested for forecast cells) is unit-pinned.
 */
internal fun fillRequestBody(priorityCells: List<String>): String {
    val cells = priorityCells.distinct()
    val joined = cells.joinToString(",") { "\"$it\"" }
    return """{"priority_cells":[$joined]}"""
}
