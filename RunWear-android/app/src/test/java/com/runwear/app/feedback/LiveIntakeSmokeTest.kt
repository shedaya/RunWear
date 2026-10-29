package com.runwear.app.feedback

import com.runwear.app.BuildConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Live end-to-end smoke against the PRODUCTION intake
 * (api.uptimeventuresllc.com), using the exact payload builders the
 * app ships — no hand-rolled JSON. Disabled unless INTAKE_SMOKE=1
 * is in the environment, so normal test runs never touch the
 * network.
 *
 * Every payload carries the marker RW-CONTRACT-FIX-2026-10-17 (in
 * the feedback text, an event prop, and the crash message) so the
 * rows are identifiable in D1 and in the triage inbox. A fresh
 * random installId keeps the smoke out of any real install's rate
 * limits.
 */
class LiveIntakeSmokeTest {

    @Test
    fun liveSmoke_postsFeedbackEventsAndCrash() {
        assumeTrue("live smoke disabled (set INTAKE_SMOKE=1)", System.getenv("INTAKE_SMOKE") == "1")

        val installId = UUID.randomUUID().toString()
        val appVersion = BuildConfig.VERSION_NAME
        val marker = "RW-CONTRACT-FIX-2026-10-17"
        println("SMOKE installId=$installId appVersion=$appVersion")

        // ── Feedback → expect 201 ──────────────────────────────────
        val feedback = TelemetryPayloads.feedback(
            appVersion = appVersion,
            installId = installId,
            category = FeedbackCategory.OTHER,
            text = "$marker — automated contract verification from the RunWear " +
                "client payload builders after the telemetry contract fix. " +
                "Smoke row; please disregard.",
            replyEmail = null,
            entryPoint = "settings",
            bundle = TelemetryFixtures.bundle()
        )
        val (feedbackCode, feedbackBody) = post(TelemetryConfig.FEEDBACK_ENDPOINT, feedback)
        println("SMOKE feedback POST ${TelemetryConfig.FEEDBACK_ENDPOINT} -> $feedbackCode $feedbackBody")
        assertEquals(201, feedbackCode)

        // ── Events batch → expect 202 ──────────────────────────────
        val now = System.currentTimeMillis()
        val generated = TelemetryPayloads.event(
            RunWearEvents.OUTFIT_GENERATED,
            now,
            RunWearEvents.outfitGeneratedProps(
                engineVersion = "4.2",
                tempBand = "32_45",
                comfortClass = "Neutral",
                itemCount = 4
            )
        )
        val swapped = TelemetryPayloads.event(
            RunWearEvents.ITEM_SWAPPED,
            now,
            RunWearEvents.itemSwappedProps(
                slot = "base",
                itemName = marker,
                replacementName = "contract-smoke"
            )
        )
        val batch = TelemetryPayloads.eventsBatch(
            appVersion,
            installId,
            listOf(generated.toString(), swapped.toString())
        )
        val (eventsCode, eventsBody) = post(TelemetryConfig.EVENTS_ENDPOINT, batch)
        println("SMOKE events POST ${TelemetryConfig.EVENTS_ENDPOINT} -> $eventsCode $eventsBody")
        assertEquals(202, eventsCode)

        // ── Crash → expect 201 ─────────────────────────────────────
        val stored = JSONObject().apply {
            put("occurredAt", now)
            put("crashDate", "2026-10-17")
            put("thread", "main")
            put("exceptionClass", "java.lang.IllegalStateException")
            put("message", "$marker — contract smoke; not a real crash.")
            put(
                "stackTrace",
                "com.runwear.app.Smoke.probe(Smoke.kt:17)\n" +
                    "com.runwear.app.Main.main(Main.kt:3)"
            )
            put("causeClass", JSONObject.NULL)
            put("osVersion", "15")
            put("sdkInt", 35)
            put("deviceModel", "Contract Smoke (JVM harness)")
            put("appVersion", appVersion)
            put("appVersionCode", BuildConfig.VERSION_CODE)
        }
        val crash = TelemetryPayloads.crash(appVersion, installId, stored)
        val (crashCode, crashBody) = post(TelemetryConfig.CRASH_ENDPOINT, crash)
        println("SMOKE crash POST ${TelemetryConfig.CRASH_ENDPOINT} -> $crashCode $crashBody")
        assertEquals(201, crashCode)
    }

    /**
     * Same POST the client's TelemetryClient performs (JSON body, 10s
     * timeouts). Transport only: this sandbox VM reaches the internet
     * through the egress proxy named in the HTTPS_PROXY environment
     * variable, so the harness routes through it when present; on a
     * normal network the connection is direct, exactly like the app.
     */
    private fun post(url: String, payload: JSONObject): Pair<Int, String> {
        val target = URL(url)
        val connection = (openThroughSandboxProxy(target) ?: target.openConnection()).let {
            it as HttpURLConnection
        }.apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            connection.outputStream.use { out ->
                out.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            code to body
        } finally {
            connection.disconnect()
        }
    }

    /** Opens [url] through the sandbox egress proxy when HTTPS_PROXY names one. */
    private fun openThroughSandboxProxy(url: URL): java.net.URLConnection? {
        val proxySpec = System.getenv("HTTPS_PROXY") ?: System.getenv("https_proxy")
            ?: return null
        val proxyUrl = runCatching { URL(proxySpec) }.getOrNull() ?: return null
        val userInfo = proxyUrl.userInfo
        if (!userInfo.isNullOrEmpty()) {
            val parts = userInfo.split(":", limit = 2)
            java.net.Authenticator.setDefault(object : java.net.Authenticator() {
                override fun getPasswordAuthentication() =
                    java.net.PasswordAuthentication(parts[0], (parts.getOrNull(1) ?: "").toCharArray())
            })
        }
        val proxy = java.net.Proxy(
            java.net.Proxy.Type.HTTP,
            java.net.InetSocketAddress(proxyUrl.host, proxyUrl.port)
        )
        return url.openConnection(proxy)
    }
}
