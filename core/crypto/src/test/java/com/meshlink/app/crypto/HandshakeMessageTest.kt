package com.meshlink.app.crypto

import com.meshlink.app.crypto.session.HandshakeMessage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [HandshakeMessage] — covers serialization / deserialization round-trips
 * and the deterministic transcript-building contract.
 *
 * No Android deps; runs as plain Kotlin JVM tests.
 */
class HandshakeMessageTest {

    private val fakeIdentityKey  = ByteArray(65) { (it + 1).toByte() }   // arbitrary 65-byte DER EC key stub
    private val fakeEphemeralKey = ByteArray(65) { (it + 10).toByte() }
    private val fakeChallenge    = ByteArray(32) { it.toByte() }

    // ── Init serialization / deserialization ──────────────────────────────────

    @Test
    fun `Init toJson and fromJson round-trip preserves all fields`() {
        val init = HandshakeMessage.Init(
            protocolVersion    = HandshakeMessage.PROTOCOL_VERSION,
            deviceId           = "abcdef1234567890",
            identityPublicKey  = fakeIdentityKey,
            ephemeralPublicKey = fakeEphemeralKey,
            challenge          = fakeChallenge,
            timestamp          = 1_700_000_000_000L
        )

        val json   = init.toJson()
        val parsed = HandshakeMessage.fromJsonOrNull(json)

        assertNotNull("Parsed Init must not be null", parsed)
        assertTrue("Parsed result must be Init", parsed is HandshakeMessage.Init)

        val parsedInit = parsed as HandshakeMessage.Init
        assertEquals(HandshakeMessage.PROTOCOL_VERSION, parsedInit.protocolVersion)
        assertEquals("abcdef1234567890", parsedInit.deviceId)
        assertArrayEquals(fakeIdentityKey, parsedInit.identityPublicKey)
        assertArrayEquals(fakeEphemeralKey, parsedInit.ephemeralPublicKey)
        assertArrayEquals(fakeChallenge, parsedInit.challenge)
        assertEquals(1_700_000_000_000L, parsedInit.timestamp)
    }

    // ── Auth serialization / deserialization ──────────────────────────────────

    @Test
    fun `Auth toJson and fromJson round-trip preserves all fields`() {
        val fakeSignature = ByteArray(72) { it.toByte() }

        val auth = HandshakeMessage.Auth(
            protocolVersion = HandshakeMessage.PROTOCOL_VERSION,
            deviceId        = "deadbeef01234567",
            signature       = fakeSignature,
            timestamp       = 1_700_000_500_000L
        )

        val json   = auth.toJson()
        val parsed = HandshakeMessage.fromJsonOrNull(json)

        assertNotNull("Parsed Auth must not be null", parsed)
        assertTrue("Parsed result must be Auth", parsed is HandshakeMessage.Auth)

        val parsedAuth = parsed as HandshakeMessage.Auth
        assertEquals(HandshakeMessage.PROTOCOL_VERSION, parsedAuth.protocolVersion)
        assertEquals("deadbeef01234567", parsedAuth.deviceId)
        assertArrayEquals(fakeSignature, parsedAuth.signature)
        assertEquals(1_700_000_500_000L, parsedAuth.timestamp)
    }

    // ── fromJsonOrNull error cases ─────────────────────────────────────────────

    @Test
    fun `fromJsonOrNull returns null for empty string`() {
        assertNull(HandshakeMessage.fromJsonOrNull(""))
    }

    @Test
    fun `fromJsonOrNull returns null for random garbage`() {
        assertNull(HandshakeMessage.fromJsonOrNull("not json at all!!!"))
    }

    @Test
    fun `fromJsonOrNull returns null for unknown type`() {
        assertNull(HandshakeMessage.fromJsonOrNull("""{"type":"UNKNOWN","version":1}"""))
    }

    @Test
    fun `fromJsonOrNull returns null for INIT with missing identityKey field`() {
        // Strip identityKey to trigger a parse exception
        val broken = """{"type":"INIT","version":1,"deviceId":"abc","ephemeralKey":"AAAA","challenge":"AAAA","timestamp":1000}"""
        assertNull(HandshakeMessage.fromJsonOrNull(broken))
    }

    @Test
    fun `fromJsonOrNull returns null for AUTH with missing signature field`() {
        val broken = """{"type":"AUTH","version":1,"deviceId":"abc","timestamp":1000}"""
        assertNull(HandshakeMessage.fromJsonOrNull(broken))
    }

    @Test
    fun `fromJsonOrNull returns null for malformed Base64 in identityKey`() {
        val broken = """{"type":"INIT","version":1,"deviceId":"abc","identityKey":"!!!NOT_BASE64!!!","ephemeralKey":"AAAA","challenge":"AAAA","timestamp":1000}"""
        assertNull(HandshakeMessage.fromJsonOrNull(broken))
    }

    // ── Transcript determinism ────────────────────────────────────────────────

    @Test
    fun `buildTranscript returns same bytes for identical inputs`() {
        val t1 = HandshakeMessage.buildTranscript(
            protocolVersion      = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId       = "device-A",
            signerIdentityPubKey = fakeIdentityKey,
            signerEphemeralPubKey = fakeEphemeralKey,
            peerChallengeNonce   = fakeChallenge,
            peerDeviceId         = "device-B"
        )
        val t2 = HandshakeMessage.buildTranscript(
            protocolVersion      = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId       = "device-A",
            signerIdentityPubKey = fakeIdentityKey,
            signerEphemeralPubKey = fakeEphemeralKey,
            peerChallengeNonce   = fakeChallenge,
            peerDeviceId         = "device-B"
        )
        assertArrayEquals("Transcript must be deterministic", t1, t2)
    }

    @Test
    fun `buildTranscript produces different bytes when signerDeviceId changes`() {
        val base = HandshakeMessage.buildTranscript(
            protocolVersion      = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId       = "device-A",
            signerIdentityPubKey = fakeIdentityKey,
            signerEphemeralPubKey = fakeEphemeralKey,
            peerChallengeNonce   = fakeChallenge,
            peerDeviceId         = "device-B"
        )
        val altered = HandshakeMessage.buildTranscript(
            protocolVersion      = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId       = "device-X",  // different
            signerIdentityPubKey = fakeIdentityKey,
            signerEphemeralPubKey = fakeEphemeralKey,
            peerChallengeNonce   = fakeChallenge,
            peerDeviceId         = "device-B"
        )
        assertNotEquals(base.toList(), altered.toList())
    }

    @Test
    fun `buildTranscript produces different bytes when challenge nonce changes`() {
        val base = HandshakeMessage.buildTranscript(
            protocolVersion      = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId       = "device-A",
            signerIdentityPubKey = fakeIdentityKey,
            signerEphemeralPubKey = fakeEphemeralKey,
            peerChallengeNonce   = fakeChallenge,
            peerDeviceId         = "device-B"
        )
        val alteredChallenge = ByteArray(32) { (it + 99).toByte() }  // different nonce
        val altered = HandshakeMessage.buildTranscript(
            protocolVersion      = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId       = "device-A",
            signerIdentityPubKey = fakeIdentityKey,
            signerEphemeralPubKey = fakeEphemeralKey,
            peerChallengeNonce   = alteredChallenge,
            peerDeviceId         = "device-B"
        )
        assertNotEquals(base.toList(), altered.toList())
    }

    @Test
    fun `buildTranscript produces different bytes when peerDeviceId changes`() {
        val base = HandshakeMessage.buildTranscript(
            protocolVersion      = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId       = "device-A",
            signerIdentityPubKey = fakeIdentityKey,
            signerEphemeralPubKey = fakeEphemeralKey,
            peerChallengeNonce   = fakeChallenge,
            peerDeviceId         = "device-B"
        )
        val altered = HandshakeMessage.buildTranscript(
            protocolVersion      = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId       = "device-A",
            signerIdentityPubKey = fakeIdentityKey,
            signerEphemeralPubKey = fakeEphemeralKey,
            peerChallengeNonce   = fakeChallenge,
            peerDeviceId         = "device-Z"  // different peer
        )
        assertNotEquals(base.toList(), altered.toList())
    }

    @Test
    fun `buildTranscript produces different bytes when identityPublicKey changes`() {
        val alteredIdentity = ByteArray(65) { (it + 99).toByte() }
        val base = HandshakeMessage.buildTranscript(
            protocolVersion      = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId       = "device-A",
            signerIdentityPubKey = fakeIdentityKey,
            signerEphemeralPubKey = fakeEphemeralKey,
            peerChallengeNonce   = fakeChallenge,
            peerDeviceId         = "device-B"
        )
        val altered = HandshakeMessage.buildTranscript(
            protocolVersion      = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId       = "device-A",
            signerIdentityPubKey = alteredIdentity,
            signerEphemeralPubKey = fakeEphemeralKey,
            peerChallengeNonce   = fakeChallenge,
            peerDeviceId         = "device-B"
        )
        assertNotEquals(base.toList(), altered.toList())
    }

    @Test
    fun `buildTranscript produces a non-empty byte array`() {
        val transcript = HandshakeMessage.buildTranscript(
            protocolVersion      = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId       = "dev",
            signerIdentityPubKey = fakeIdentityKey,
            signerEphemeralPubKey = fakeEphemeralKey,
            peerChallengeNonce   = fakeChallenge,
            peerDeviceId         = "peer"
        )
        assertTrue("Transcript must not be empty", transcript.isNotEmpty())
    }

    @Test
    fun `buildTranscript is not commutative - swapping signerDeviceId and peerDeviceId gives different result`() {
        val forward = HandshakeMessage.buildTranscript(
            protocolVersion      = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId       = "device-A",
            signerIdentityPubKey = fakeIdentityKey,
            signerEphemeralPubKey = fakeEphemeralKey,
            peerChallengeNonce   = fakeChallenge,
            peerDeviceId         = "device-B"
        )
        val swapped = HandshakeMessage.buildTranscript(
            protocolVersion      = HandshakeMessage.PROTOCOL_VERSION,
            signerDeviceId       = "device-B",  // swapped
            signerIdentityPubKey = fakeIdentityKey,
            signerEphemeralPubKey = fakeEphemeralKey,
            peerChallengeNonce   = fakeChallenge,
            peerDeviceId         = "device-A"   // swapped
        )
        assertNotEquals(forward.toList(), swapped.toList())
    }

    // ── PROTOCOL_VERSION sanity ───────────────────────────────────────────────

    @Test
    fun `PROTOCOL_VERSION is a positive integer`() {
        assertTrue(HandshakeMessage.PROTOCOL_VERSION > 0)
    }
}
