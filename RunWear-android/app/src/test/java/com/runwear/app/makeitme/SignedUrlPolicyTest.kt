package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RW-16.15 — vc21's Your Photos tiles could never render: the
 * Storage API's `signedURL` is relative to the storage endpoint
 * root (`/storage/v1`), and the client joined it to the bare host,
 * so every tile URL returned HTTP 404 (proven live in the RW-16.15
 * trace against the user's real reference photo: host-root join →
 * 404, `/storage/v1` join → 206 image/jpeg). These tests pin the
 * corrected join in [SignedUrlPolicy.resolve] — the exact function
 * `PersonalHeroService.signedUrlFor` ships.
 */
class SignedUrlPolicyTest {

    private val base = "https://ebicqznlcjbqcukjfzcf.supabase.co"

    @Test
    fun `a relative signedURL resolves under the storage root`() {
        // The exact shape the Storage sign API returns.
        val signed = "/object/sign/personal-heroes/u1/refs/ref-1.jpg?token=abc"
        assertEquals(
            "https://ebicqznlcjbqcukjfzcf.supabase.co/storage/v1/object/sign/personal-heroes/u1/refs/ref-1.jpg?token=abc",
            SignedUrlPolicy.resolve(base, signed)
        )
    }

    @Test
    fun `a relative signedURL without a leading slash still resolves`() {
        assertEquals(
            "https://ebicqznlcjbqcukjfzcf.supabase.co/storage/v1/object/sign/personal-heroes/u1/refs/ref-1.jpg?token=abc",
            SignedUrlPolicy.resolve(base, "object/sign/personal-heroes/u1/refs/ref-1.jpg?token=abc")
        )
    }

    @Test
    fun `an absolute signedURL passes through untouched`() {
        val absolute = "https://cdn.example.com/object/sign/x.jpg?token=abc"
        assertEquals(absolute, SignedUrlPolicy.resolve(base, absolute))
    }
}
