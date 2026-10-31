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
 * is keyed to the install), so the client obtains an anonymous Supabase
 * Auth session and persists it in the app's DataStore. The anonymous
 * user is a real auth.users row: RLS owner policies apply unchanged.
 *
 * KNOWN SERVER GAP (verified live 2026-10-31): anonymous sign-ins are
 * DISABLED on the project (`anonymous_provider_disabled`), so no
 * session can be established until that one Auth setting is enabled
 * server-side. Every caller degrades to the pool hero / stock state in
 * that case — the feature is inert, never broken, while the setting is
 * off. See the build report.
 *
 * This client talks REST directly (Ktor) rather than through the
 * supabase-kt client in the shared module: that client is built
 * anon-only for the public pool and carries no Auth session; bolting a
 * second, session-carrying client beside it keeps the pool path — the
 * app's critical path — untouched.
 */
@Singleton
class PersonalHeroService @Inject constructor(
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

    suspend fun clearSession() {
        preferencesRepository.remove(KEY_ACCESS_TOKEN)
        preferencesRepository.remove(KEY_REFRESH_TOKEN)
        preferencesRepository.remove(KEY_USER_ID)
    }

    /** The current session's user id, if a session exists (no network). */
    suspend fun currentUserId(): String? = storedSession()?.userId

    /**
     * Ensure a usable session: reuse a non-expired token, refresh an
     * expired one, or sign in anonymously. True when a session is
     * usable; false when none can be established (offline, or anonymous
     * sign-ins disabled server-side) — callers treat false as "personal
     * layer unavailable".
     */
    suspend fun ensureSession(): Boolean = session() != null

    private suspend fun session(): Session? = sessionMutex.withLock {
        val existing = storedSession()
        val nowSec = System.currentTimeMillis() / 1000L
        if (existing != null && jwtExpirySeconds(existing.accessToken) > nowSec + 60L) {
            return@withLock existing
        }
        if (existing != null) {
            val refreshed = refreshSession(existing.refreshToken)
            if (refreshed != null) {
                storeSession(refreshed)
                return@withLock refreshed
            }
            // Refresh failed (revoked/expired): fall through to a fresh
            // anonymous sign-in, replacing the dead session.
            clearSession()
        }
        val fresh = anonymousSignIn()
        if (fresh != null) storeSession(fresh)
        fresh
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

    private suspend fun refreshSession(refreshToken: String): Session? = runCatching {
        val response = http.post("$BASE_URL/auth/v1/token?grant_type=refresh_token") {
            header("apikey", ANON_KEY)
            contentType(ContentType.Application.Json)
            setBody("""{"refresh_token":"$refreshToken"}""")
        }
        parseSession(response)
    }.onFailure { Log.w(TAG, "token refresh failed", it) }.getOrNull()

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
        val heroId: String?
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
            heroId = body["hero_id"]?.jsonPrimitive?.contentOrNull
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

    // ── Subscriber fill / delete ───────────────────────────────────────

    /** Enqueue the 20-cell matrix for the (entitled) caller. */
    suspend fun startPersonalFill(): Boolean =
        callFunction("start-personal-fill", "{}") is FunctionResult.Ok

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

    // ── Profile read (PostgREST, owner RLS) ────────────────────────────

    data class ProfileSnapshot(
        val personalizationOn: Boolean,
        val freeGenerationsUsed: Int,
        val entitled: Boolean,
        val gender: String
    )

    suspend fun fetchProfile(): ProfileSnapshot? {
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

    /** The caller's registered reference-photo storage paths, in order. */
    suspend fun fetchReferencePaths(): List<String> {
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
            if (signed.startsWith("http")) signed else "$BASE_URL$signed"
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
