package com.runwear.app.feedback

import com.runwear.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telemetry contract pins.
 *
 * Provenance: payload shapes are asserted against the DEPLOYED
 * intake's contract — feedback-intake/src/schemas.js allowlists and
 * src/worker.js validation, summarized in the intake README. The
 * Worker rejects unknown top-level fields, unknown bundle keys, and
 * unknown event names or prop keys with 422, so every payload the
 * builders produce is pinned here, key for key. (JetLagKiller's
 * telemetry tests are the shape this file mirrors.)
 */
class TelemetryContractTest {

    // ── The allowlists, copied from feedback-intake/src/schemas.js ──

    private val envelopeKeys = setOf(
        "schema", "app", "platform", "appVersion", "installId", "turnstileToken"
    )
    private val feedbackKeys = envelopeKeys +
        setOf("category", "text", "replyEmail", "entryPoint", "bundle")
    private val eventsPayloadKeys = envelopeKeys + setOf("events")
    private val eventKeys = setOf("name", "ts", "props")
    private val eventNames = setOf(
        "outfit_generated", "item_swapped", "item_removed",
        "now_vs_explicit", "affiliate_tapped"
    )
    private val eventPropKeys = setOf(
        "engineVersion", "tempBand", "comfortClass", "itemCount",
        "slot", "itemName", "replacementName", "mode", "category"
    )
    private val bundleKeys = setOf(
        "engineVersion", "cityLabel", "effectiveTemp", "tempUnits",
        "feelsLike", "humidity", "windSpeed", "windGusts",
        "precipProbability", "weatherDescription", "uvIndex",
        "timeFrame", "localDate", "localHour", "outfitItems",
        "swappedItems", "removedItems", "comfortPreference",
        "fitPreference", "tipsShownCount"
    )
    private val crashKeys = envelopeKeys + setOf(
        "osVersion", "deviceModel", "stackTrace", "recurring", "crashTimestamp"
    )

    /**
     * Field names from the pre-fix client that the Worker rejects or
     * drops. (The bundle-level bans — "platform", "appVersion", "city",
     * and friends riding inside the bundle — are covered by the bundle
     * allowlist pin: those keys simply are not in [bundleKeys].)
     */
    private val forbiddenKeys = setOf(
        "message", "sentAt", "appVersionCode", "at", "crash",
        "city", "frame", "localDateTime", "outfit", "tipsShown",
        "sessionItemChanges", "temperatureUnits", "precipitationProbability",
        "totalItems", "itemCounts", "countedGeneration",
        "from", "to", "item"
    )

    private val installId = "11111111-1111-4111-8111-111111111111"
    private val appVersion = BuildConfig.VERSION_NAME

    private fun keysOf(json: JSONObject): Set<String> =
        mutableSetOf<String>().also { set -> json.keys().forEach { set += it } }

    private fun allKeysDeep(value: Any?, into: MutableSet<String>) {
        when (value) {
            is JSONObject -> value.keys().forEach { key ->
                into += key
                allKeysDeep(value.get(key), into)
            }
            is JSONArray -> for (i in 0 until value.length()) allKeysDeep(value.get(i), into)
        }
    }

    // ── Config ──────────────────────────────────────────────────────

    @Test
    fun config_endpointsCarryTheAppSegment() {
        assertEquals(
            "https://api.uptimeventuresllc.com/v1/feedback/runwear",
            TelemetryConfig.FEEDBACK_ENDPOINT
        )
        assertEquals(
            "https://api.uptimeventuresllc.com/v1/events/runwear",
            TelemetryConfig.EVENTS_ENDPOINT
        )
        assertEquals(
            "https://api.uptimeventuresllc.com/v1/crash/runwear",
            TelemetryConfig.CRASH_ENDPOINT
        )
        assertEquals("runwear-feedback/1", TelemetryConfig.FEEDBACK_SCHEMA)
        assertEquals("runwear-events/1", TelemetryConfig.EVENTS_SCHEMA)
        assertEquals("runwear-crash/1", TelemetryConfig.CRASH_SCHEMA)
    }

    // ── Envelope ────────────────────────────────────────────────────

    @Test
    fun envelope_hasExactlyTheAllowlistedKeys() {
        val envelope = TelemetryPayloads.envelope(
            TelemetryConfig.FEEDBACK_SCHEMA, appVersion, installId
        )
        assertEquals(
            setOf("schema", "app", "platform", "appVersion", "installId"),
            keysOf(envelope)
        )
        assertEquals("runwear-feedback/1", envelope.getString("schema"))
        assertEquals("runwear", envelope.getString("app"))
        assertEquals("android", envelope.getString("platform"))
        assertEquals(appVersion, envelope.getString("appVersion"))
        assertEquals(installId, envelope.getString("installId"))
    }

    // ── Feedback ────────────────────────────────────────────────────

    @Test
    fun feedback_payloadMatchesTheContractExactly() {
        val payload = TelemetryPayloads.feedback(
            appVersion = appVersion,
            installId = installId,
            category = FeedbackCategory.RECOMMENDATION_OFF,
            text = "At 39F and calm it suggested three torso layers.",
            replyEmail = "  runner@example.com ",
            entryPoint = "contextual",
            bundle = TelemetryFixtures.bundle()
        )
        assertEquals(feedbackKeys - "turnstileToken", keysOf(payload))
        assertEquals("recommendation_off", payload.getString("category"))
        // The user's words ride in "text" — never "message".
        assertEquals(
            "At 39F and calm it suggested three torso layers.",
            payload.getString("text")
        )
        assertEquals("runner@example.com", payload.getString("replyEmail"))
        assertEquals("contextual", payload.getString("entryPoint"))
        val deep = mutableSetOf<String>().also { allKeysDeep(payload, it) }
        assertTrue(
            "forbidden keys present: ${deep intersect forbiddenKeys}",
            (deep intersect forbiddenKeys).isEmpty()
        )
    }

    @Test
    fun feedback_omitsOptionalFieldsWhenAbsent() {
        val payload = TelemetryPayloads.feedback(
            appVersion = appVersion,
            installId = installId,
            category = FeedbackCategory.OTHER,
            text = "A note with enough characters.",
            replyEmail = null,
            entryPoint = null,
            bundle = TelemetryFixtures.bundle()
        )
        assertEquals(
            setOf(
                "schema", "app", "platform", "appVersion", "installId",
                "category", "text", "bundle"
            ),
            keysOf(payload)
        )
    }

    // ── Bundle ──────────────────────────────────────────────────────

    @Test
    fun bundle_keysStayInsideTheAllowlist() {
        val bundle = TelemetryFixtures.bundle()
        val keys = keysOf(bundle)
        assertTrue("bundle keys outside allowlist: ${keys - bundleKeys}", keys.all { it in bundleKeys })
        // engineVersion is the one required bundle field.
        assertEquals("4.3", bundle.getString("engineVersion"))
        assertEquals("Hong Kong", bundle.getString("cityLabel"))
        assertEquals(39.0, bundle.getDouble("feelsLike"), 0.001)
        assertEquals(39.0, bundle.getDouble("effectiveTemp"), 0.001)
        assertEquals("F", bundle.getString("tempUnits"))
        assertEquals(62, bundle.getInt("humidity"))
        assertEquals(10, bundle.getInt("precipProbability"))
        assertEquals("now", bundle.getString("timeFrame"))
        assertEquals("2026-10-17", bundle.getString("localDate"))
        assertEquals(7, bundle.getInt("localHour"))
        assertEquals(2, bundle.getInt("tipsShownCount"))
        assertEquals("Neutral", bundle.getString("comfortPreference"))
        assertEquals("Male", bundle.getString("fitPreference"))
        // Outfit + session changes are flat string arrays — no nested objects.
        val items = bundle.getJSONArray("outfitItems")
        assertTrue(items.length() in 1..16)
        for (i in 0 until items.length()) assertTrue(items.get(i) is String)
        assertEquals("Quarter-Zip Pullover", bundle.getJSONArray("swappedItems").getString(0))
        assertEquals("Light Gloves", bundle.getJSONArray("removedItems").getString(0))
        val deep = mutableSetOf<String>().also { allKeysDeep(bundle, it) }
        assertTrue((deep intersect forbiddenKeys).isEmpty())
    }

    @Test
    fun bundle_withoutWeatherOrOutfit_keepsOnlyTheBasics() {
        val bundle = FeedbackBundleFactory.build(
            com.runwear.app.ui.viewmodel.MainUiState(locationName = "Nowhere"),
            emptyList(), emptyList(), emptyList()
        ).json
        val keys = keysOf(bundle)
        assertTrue(keys.all { it in bundleKeys })
        assertTrue("engineVersion" in keys)
        assertFalse(bundle.has("outfitItems"))
        assertFalse(bundle.has("feelsLike"))
    }

    // ── Events ──────────────────────────────────────────────────────

    @Test
    fun eventNames_matchTheAllowlistExactly() {
        assertEquals(
            eventNames,
            setOf(
                RunWearEvents.OUTFIT_GENERATED,
                RunWearEvents.ITEM_SWAPPED,
                RunWearEvents.ITEM_REMOVED,
                RunWearEvents.NOW_VS_EXPLICIT,
                RunWearEvents.AFFILIATE_TAPPED
            )
        )
    }

    @Test
    fun eventPropBuilders_stayInsideTheAllowlist() {
        val propSets = listOf(
            RunWearEvents.outfitGeneratedProps("4.2", "32_45", "Neutral", 4),
            RunWearEvents.itemSwappedProps("base", "Fleece Pullover", "Quarter-Zip Pullover"),
            RunWearEvents.itemRemovedProps("hands", "Light Gloves"),
            RunWearEvents.nowVsExplicitProps("explicit"),
            RunWearEvents.affiliateTappedProps("top_base")
        )
        propSets.forEach { props ->
            assertTrue(
                "prop keys outside allowlist: ${props.keys - eventPropKeys}",
                props.keys.all { it in eventPropKeys }
            )
            // Props are flat scalars only — never nested objects.
            assertTrue(props.values.all { it is String || it is Number || it is Boolean })
        }
    }

    @Test
    fun event_shapeUsesTsNeverAt() {
        val event = TelemetryPayloads.event(
            RunWearEvents.OUTFIT_GENERATED,
            1_760_659_200_000L,
            RunWearEvents.outfitGeneratedProps("4.2", "32_45", "Neutral", 4)
        )
        assertEquals(eventKeys, keysOf(event))
        assertEquals("outfit_generated", event.getString("name"))
        assertEquals(1_760_659_200_000L, event.getLong("ts"))
        assertFalse(event.has("at"))
        val props = event.getJSONObject("props")
        assertEquals(4, props.getInt("itemCount"))
        assertEquals("4.2", props.getString("engineVersion"))
    }

    @Test
    fun eventsBatch_topLevelShapeIsExact() {
        val queued = listOf(
            TelemetryPayloads.event(
                RunWearEvents.NOW_VS_EXPLICIT,
                1_760_659_200_000L,
                RunWearEvents.nowVsExplicitProps("now")
            ).toString(),
            TelemetryPayloads.event(
                RunWearEvents.AFFILIATE_TAPPED,
                1_760_659_201_000L,
                RunWearEvents.affiliateTappedProps("top_base")
            ).toString()
        )
        val batch = TelemetryPayloads.eventsBatch(appVersion, installId, queued)
        assertEquals(eventsPayloadKeys - "turnstileToken", keysOf(batch))
        assertEquals("runwear-events/1", batch.getString("schema"))
        val events = batch.getJSONArray("events")
        assertEquals(2, events.length())
        for (i in 0 until events.length()) {
            val event = events.getJSONObject(i)
            assertTrue(keysOf(event).all { it in eventKeys })
            assertTrue(event.getString("name") in eventNames)
        }
        val deep = mutableSetOf<String>().also { allKeysDeep(batch, it) }
        assertTrue((deep intersect forbiddenKeys).isEmpty())
    }

    // ── Crash ───────────────────────────────────────────────────────

    /** The on-device crash file exactly as CrashReporter.writeCrash writes it. */
    private fun storedCrash(
        deviceModel: String = "Google Pixel 8",
        frames: String = "com.runwear.app.Foo.bar(Foo.kt:12)\ncom.runwear.app.Main.main(Main.kt:3)"
    ) = JSONObject().apply {
        put("occurredAt", 1_760_659_200_000L) // 2025-10-17T00:00:00Z
        put("crashDate", "2025-10-17")
        put("thread", "main")
        put("exceptionClass", "java.lang.IllegalStateException")
        put("message", "Boom")
        put("stackTrace", frames)
        put("causeClass", JSONObject.NULL)
        put("osVersion", "15")
        put("sdkInt", 35)
        put("deviceModel", deviceModel)
        put("appVersion", "1.1.0")
        put("appVersionCode", 4)
    }

    @Test
    fun crash_payloadIsFlatAndMatchesTheContract() {
        val payload = TelemetryPayloads.crash(appVersion, installId, storedCrash())
        // Flat: envelope + CRASH_FIELDS, no wrapper object, and none of
        // the stored file's local bookkeeping fields leak onto the wire.
        assertEquals(crashKeys - "turnstileToken", keysOf(payload))
        assertEquals("runwear-crash/1", payload.getString("schema"))
        assertEquals("Android 15", payload.getString("osVersion"))
        assertEquals("Google Pixel 8", payload.getString("deviceModel"))
        assertFalse(payload.getBoolean("recurring"))
        val trace = payload.getString("stackTrace")
        assertTrue(
            trace.startsWith("java.lang.IllegalStateException: Boom (thread: main)\n")
        )
        assertTrue(trace.contains("com.runwear.app.Foo.bar(Foo.kt:12)"))
        val timestamp = payload.getString("crashTimestamp")
        assertTrue(timestamp.length <= 32)
        // ISO-8601 local rendering of the stored crash instant.
        val expectedTimestamp = java.time.LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(1_760_659_200_000L),
            java.time.ZoneId.systemDefault()
        ).toString()
        assertEquals(expectedTimestamp, timestamp)
        val deep = mutableSetOf<String>().also { allKeysDeep(payload, it) }
        assertTrue((deep intersect forbiddenKeys).isEmpty())
    }

    @Test
    fun crash_payloadCapsFieldsAtTheSchemaLengths() {
        val payload = TelemetryPayloads.crash(
            appVersion,
            installId,
            storedCrash(
                deviceModel = "X".repeat(100),
                frames = "a".repeat(20_000)
            )
        )
        assertEquals(64, payload.getString("deviceModel").length)
        assertEquals(16_000, payload.getString("stackTrace").length)
        assertTrue(payload.getString("osVersion").length <= 32)
    }
}
