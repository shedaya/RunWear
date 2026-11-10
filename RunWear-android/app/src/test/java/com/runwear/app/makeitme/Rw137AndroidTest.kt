package com.runwear.app.makeitme

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.runwear.shared.data.repository.PreferencesRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

private typealias ReconcileOutcome = PersonalHeroService.ReconcileOutcome

/**
 * RW-137 Android client (Nov 10, 2026; DESIGN.md §4.4/§4.8/§4.9,
 * §7 S4 + S4b + S4c client half):
 *
 *  - S4: after reconcile (the bootstrap / restore / entitlement-
 *    change sync), a server-reported `adoption_available` source is
 *    passed to request-identity-adoption and the profile + bank
 *    refresh renders the restored state. Adoption is AUTOMATIC —
 *    no opt-in, no new flow — and a hint naming the caller's own
 *    uid, or no hint, is a no-op.
 *  - S4b: both manifests wire dataExtractionRules +
 *    fullBackupContent resources that exclude the
 *    runwear_preferences session DataStore from cloud backup AND
 *    device-to-device transfer, while allowBackup stays true.
 *  - S4c (client half): a confirmed delete-my-data discards the
 *    local session material and the identity-scoped fill state, so
 *    the deleted identity cannot re-authenticate and the next
 *    bootstrap mints a genuinely fresh anonymous session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Rw137AndroidTest {

    // ── S4: the reconcile contract (pure) ──────────────────────────

    @Test
    fun `adoption_available parses as a nullable string - absent means no adoption`() {
        assertEquals(
            "source-uid",
            parseAdoptionAvailable(
                JsonObject(mapOf("adoption_available" to JsonPrimitive("source-uid")))
            )
        )
        // Pre-RW-137 payloads carry no field at all.
        assertNull(parseAdoptionAvailable(JsonObject(emptyMap())))
        assertNull(
            parseAdoptionAvailable(
                JsonObject(mapOf("adoption_available" to JsonPrimitive("")))
            )
        )
        // A non-string hint is not a uid; never acted on.
        assertNull(
            parseAdoptionAvailable(
                JsonObject(mapOf("adoption_available" to JsonPrimitive(42)))
            )
        )
    }

    @Test
    fun `adoption policy acts only on a source different from the caller`() {
        assertEquals(
            "source-uid",
            IdentityAdoptionPolicy.sourceToAdopt("source-uid", "dest-uid")
        )
        // The data already lives under the caller's uid.
        assertNull(IdentityAdoptionPolicy.sourceToAdopt("dest-uid", "dest-uid"))
        assertNull(IdentityAdoptionPolicy.sourceToAdopt(null, "dest-uid"))
        assertNull(IdentityAdoptionPolicy.sourceToAdopt("  ", "dest-uid"))
    }

    // ── Fakes ──────────────────────────────────────────────────────

    private class FakeService(prefs: PreferencesRepository) :
        PersonalHeroService(prefs) {
        var fakeUid: String? = null
        var reconcileOutcome = ReconcileOutcome(completed = true)
        var deleteOutcome: DataDeleteOutcome? =
            DataDeleteOutcome(storageRemoved = 0, totalRowsRemoved = 0, accountDeleted = true)
        val adoptionRequests = mutableListOf<String>()

        override suspend fun ensureSession(): Boolean = true
        override suspend fun currentUserId(): String? =
            fakeUid ?: super.currentUserId()

        override suspend fun reconcileEntitlement(): ReconcileOutcome = reconcileOutcome

        override suspend fun requestIdentityAdoption(sourceUserId: String): Boolean {
            adoptionRequests += sourceUserId
            return true
        }

        override suspend fun fetchProfile(): ProfileSnapshot = ProfileSnapshot(
            personalizationOn = false, freeGenerationsUsed = 0,
            entitled = true, gender = "UNISEX"
        )

        override suspend fun fetchReferencePaths(): List<String> = emptyList()

        override suspend fun deleteMyData(): DataDeleteOutcome? = deleteOutcome
    }

    private fun fixture(): Triple<PreferencesRepository, FakeService, PersonalHeroRepository> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesRepository(context)
        val service = FakeService(prefs)
        val repository = PersonalHeroRepository(service, prefs, BillingManager(context))
        return Triple(prefs, service, repository)
    }

    // ── S4: the sync hook ──────────────────────────────────────────

    @Test
    fun `sync adopts a reported source then refreshes the restored state`() = runBlocking {
        val (_, service, repository) = fixture()
        service.fakeUid = "dest-uid"
        service.reconcileOutcome = ReconcileOutcome(
            completed = true, adoptionAvailable = "source-uid"
        )
        repository.syncBillingIdentity()
        assertEquals(listOf("source-uid"), service.adoptionRequests)
        // The refresh after adoption is what renders the restored
        // state on the existing surfaces.
        assertTrue(repository.state.value.synced)
        assertTrue(repository.state.value.entitled)
    }

    @Test
    fun `sync without an adoption hint never calls the adoption endpoint`() = runBlocking {
        val (_, service, repository) = fixture()
        service.fakeUid = "dest-uid"
        service.reconcileOutcome = ReconcileOutcome(completed = true)
        repository.syncBillingIdentity()
        assertTrue(service.adoptionRequests.isEmpty())
        assertTrue(repository.state.value.synced)
    }

    @Test
    fun `sync with a self-naming hint is a no-op adoption-wise`() = runBlocking {
        val (_, service, repository) = fixture()
        service.fakeUid = "dest-uid"
        service.reconcileOutcome = ReconcileOutcome(
            completed = true, adoptionAvailable = "dest-uid"
        )
        repository.syncBillingIdentity()
        assertTrue(service.adoptionRequests.isEmpty())
    }

    @Test
    fun `a failed adoption request still refreshes - silent no-op, never an error state`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesRepository(context)
        val service = object : PersonalHeroService(prefs) {
            override suspend fun ensureSession(): Boolean = true
            override suspend fun currentUserId(): String = "dest-uid"
            override suspend fun reconcileEntitlement(): ReconcileOutcome =
                ReconcileOutcome(completed = true, adoptionAvailable = "source-uid")

            override suspend fun requestIdentityAdoption(sourceUserId: String): Boolean =
                throw RuntimeException("endpoint unavailable")

            override suspend fun fetchProfile(): ProfileSnapshot = ProfileSnapshot(
                personalizationOn = false, freeGenerationsUsed = 0,
                entitled = true, gender = "UNISEX"
            )

            override suspend fun fetchReferencePaths(): List<String> = emptyList()
        }
        val repository = PersonalHeroRepository(service, prefs, BillingManager(context))
        repository.syncBillingIdentity() // must not throw
        assertTrue(repository.state.value.synced)
    }

    // ── S4c: deletion closure (client half) ────────────────────────

    private val keyAccess = stringPreferencesKey("mim_access_token")
    private val keyRefresh = stringPreferencesKey("mim_refresh_token")
    private val keyUserId = stringPreferencesKey("mim_user_id")
    private val keyFillTarget = stringPreferencesKey("mim_fill_target_cells")

    @Test
    fun `confirmed delete-my-data discards the session and the identity fill state`() = runBlocking {
        val (prefs, service, repository) = fixture()
        // A live session + identity-scoped fill state on this phone.
        prefs.setString(keyAccess, "access-token")
        prefs.setString(keyRefresh, "refresh-token")
        prefs.setString(keyUserId, "deleted-uid")
        prefs.setString(keyFillTarget, "CELL_A,CELL_B")
        repository.noteFillDerivation(
            wireCells = listOf("CELL_A"), targetIds = setOf("CELL_A"),
            fillableIds = setOf("CELL_A")
        )
        assertEquals("deleted-uid", service.currentUserId())

        assertTrue(repository.deleteAllData())

        // Session material is gone: the deleted identity cannot
        // silently re-authenticate.
        assertNull(prefs.getString(keyAccess))
        assertNull(prefs.getString(keyRefresh))
        assertNull(prefs.getString(keyUserId))
        assertNull(service.currentUserId())
        // The persisted + in-memory fill picture went with it, so
        // nothing re-applies the deleted identity's state to the
        // next uid.
        assertNull(prefs.getString(keyFillTarget))
        assertTrue(repository.fillTargetCells.isEmpty())
        assertTrue(repository.fillWireCells.isEmpty())
        // In-memory identity state is reset.
        assertFalse(repository.state.value.entitled)
        assertFalse(repository.state.value.personalizationOn)
        // With no stored session, the bootstrap decision is the
        // fresh-mint branch: the next session is a genuinely new
        // anonymous uid.
        assertEquals(
            SessionGuardPolicy.Action.MINT_FRESH,
            SessionGuardPolicy.decide(false, false, null)
        )
    }

    @Test
    fun `an unconfirmed delete-my-data leaves the session untouched`() = runBlocking {
        val (prefs, service, repository) = fixture()
        prefs.setString(keyAccess, "access-token")
        prefs.setString(keyRefresh, "refresh-token")
        prefs.setString(keyUserId, "live-uid")
        service.deleteOutcome = null // server did not confirm

        assertFalse(repository.deleteAllData())
        assertEquals("live-uid", service.currentUserId())
        assertEquals("access-token", prefs.getString(keyAccess))
    }

    // ── S4b: OS-backup exclusion (manifest / resource pins) ────────

    private fun sourceFile(androidRootRelative: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "RunWear-android/", "app/")) {
                val file = File(dir, prefix + androidRootRelative)
                if (file.exists()) return file
            }
            dir = dir.parentFile
        }
        error("source file not found: $androidRootRelative")
    }

    @Test
    fun `the excluded file is exactly the session DataStore PreferencesRepository creates`() {
        val prefsSource = sourceFile(
            "shared/src/main/java/com/runwear/shared/data/repository/PreferencesRepository.kt"
        ).readText()
        assertTrue(prefsSource.contains("name = \"runwear_preferences\""))
        // Sessions persist through this repository's generic string
        // storage (PersonalHeroService.session()).
        val serviceSource = sourceFile(
            "app/src/main/java/com/runwear/app/makeitme/PersonalHeroService.kt"
        ).readText()
        assertTrue(serviceSource.contains("stringPreferencesKey(\"mim_access_token\")"))
        assertTrue(serviceSource.contains("stringPreferencesKey(\"mim_refresh_token\")"))
        assertTrue(serviceSource.contains("stringPreferencesKey(\"mim_user_id\")"))
    }

    @Test
    fun `both manifests wire backup rules that exclude the session DataStore from backup and transfer`() {
        val modules = listOf(
            "app" to "app/src/main/AndroidManifest.xml",
            "wear" to "wear/src/main/AndroidManifest.xml"
        )
        for ((module, manifestPath) in modules) {
            val manifest = sourceFile(manifestPath).readText()
            // allowBackup stays true — everything else remains
            // backup-eligible per DESIGN §4.9; only the session
            // store is excluded.
            assertTrue("$module manifest lost allowBackup", manifest.contains("android:allowBackup=\"true\""))
            assertTrue(
                "$module manifest does not wire dataExtractionRules",
                manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\"")
            )
            assertTrue(
                "$module manifest does not wire fullBackupContent",
                manifest.contains("android:fullBackupContent=\"@xml/backup_rules\"")
            )

            val extraction = sourceFile(
                "$module/src/main/res/xml/data_extraction_rules.xml"
            ).readText()
            val cloud = extraction.substringAfter("<cloud-backup>")
                .substringBefore("</cloud-backup>")
            val transfer = extraction.substringAfter("<device-transfer>")
                .substringBefore("</device-transfer>")
            for ((domainName, section) in listOf("cloud-backup" to cloud, "device-transfer" to transfer)) {
                assertTrue(
                    "$module $domainName does not exclude the session DataStore",
                    section.contains(
                        "<exclude domain=\"file\" " +
                            "path=\"datastore/runwear_preferences.preferences_pb\" />"
                    )
                )
            }

            val legacy = sourceFile("$module/src/main/res/xml/backup_rules.xml").readText()
            assertTrue(legacy.contains("<full-backup-content>"))
            assertTrue(
                "$module legacy rules do not exclude the session DataStore",
                legacy.contains(
                    "<exclude domain=\"file\" " +
                        "path=\"datastore/runwear_preferences.preferences_pb\" />"
                )
            )
        }
    }
}
