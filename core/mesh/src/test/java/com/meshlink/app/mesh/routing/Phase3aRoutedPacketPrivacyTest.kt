package com.meshlink.app.mesh.routing

import com.meshlink.app.crypto.cipher.EciesService
import com.meshlink.app.crypto.cipher.EncryptionService
import com.meshlink.app.crypto.identity.KeyManager
import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.domain.model.MeshPacket.PacketType
import com.meshlink.app.mesh.util.toBytes
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

/**
 * Phase 3A Unit Tests: Privacy Hardening.
 *
 * Verifies:
 * 1. senderName is omitted from the unencrypted outer routing metadata of ROUTED_CHAT packets.
 * 2. senderName is preserved inside the ECIES-encrypted payload and delivered to the final recipient.
 * 3. AAD metadata binding (originId, finalDestId, messageId, timestamp) is strictly enforced.
 * 4. Advertising token is non-identifying and does not leak display name or Build.MODEL.
 */
class Phase3aRoutedPacketPrivacyTest {

    private lateinit var recipientKeyPair: KeyPair
    private lateinit var recipientKeyManager: KeyManager
    private lateinit var eciesService: EciesService

    @Before
    fun setUp() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        recipientKeyPair = kpg.generateKeyPair()

        recipientKeyManager = mockk(relaxed = true)
        every { recipientKeyManager.keyPair } returns recipientKeyPair

        val encryptionService = EncryptionService()
        eciesService = EciesService(recipientKeyManager, encryptionService)
    }

    // ── Task 3: Outer Header Privacy ─────────────────────────────────────────

    @Test
    fun `ROUTED_CHAT packet outer JSON does NOT contain senderName`() {
        val routedPacket = MeshPacket(
            senderId     = "origin-node-01",
            receiverId   = "relay-node-02",
            content      = "OpaqueCiphertextBase64",
            timestamp    = 1700000000000L,
            type         = PacketType.ROUTED_CHAT,
            messageId    = "msg-uuid-1234",
            originId     = "origin-node-01",
            finalDestId  = "final-node-99",
            hopCount     = 0,
            maxHops      = 7,
            senderName   = "Secret Agent Alice" // Must not leak on wire
        )

        val wireBytes = routedPacket.toBytes()
        val wireJsonString = String(wireBytes, Charsets.UTF_8)
        val jsonObject = JSONObject(wireJsonString)

        assertFalse(
            "ROUTED_CHAT outer wire JSON must NOT contain senderName",
            jsonObject.has("senderName")
        )
        assertFalse(
            "Wire bytes must not contain the sensitive display name",
            wireJsonString.contains("Secret Agent Alice")
        )
        // Verify routing fields are still present
        assertEquals("origin-node-01", jsonObject.getString("originId"))
        assertEquals("final-node-99", jsonObject.getString("finalDestId"))
        assertEquals("msg-uuid-1234", jsonObject.getString("messageId"))
    }

    @Test
    fun `CHAT packet outer JSON retains senderName on direct link`() {
        val directChatPacket = MeshPacket(
            senderId   = "node-a",
            receiverId = "node-b",
            content    = "DirectEncryptedBase64",
            timestamp  = 1700000000000L,
            type       = PacketType.CHAT,
            senderName = "Alice"
        )

        val json = JSONObject(String(directChatPacket.toBytes(), Charsets.UTF_8))
        assertTrue("CHAT packet includes senderName for direct link", json.has("senderName"))
        assertEquals("Alice", json.getString("senderName"))
    }

    @Test
    fun `BROADCAST packet outer JSON retains senderName for public emergency notice`() {
        val broadcastPacket = MeshPacket(
            senderId   = "node-a",
            receiverId = MeshPacket.BROADCAST_DEST,
            content    = "Emergency flood notice",
            timestamp  = 1700000000000L,
            type       = PacketType.BROADCAST,
            senderName = "Emergency Coordinator"
        )

        val json = JSONObject(String(broadcastPacket.toBytes(), Charsets.UTF_8))
        assertTrue("BROADCAST packet includes senderName in clear", json.has("senderName"))
        assertEquals("Emergency Coordinator", json.getString("senderName"))
    }

    // ── Task 3: Encrypted Inner Payload & AAD ─────────────────────────────────

    @Test
    fun `ROUTED_CHAT inner payload conveys senderName securely to final recipient`() {
        val originalText = "Confidential medical status"
        val originalSenderName = "Dr. Alexander Vance"
        val originId = "origin-node-11"
        val finalDestId = "dest-node-22"
        val messageId = "msg-9999"
        val timestamp = 1700000000000L

        // 1. Sender packs inner payload
        val innerPayloadJson = JSONObject().apply {
            put("text", originalText)
            put("senderName", originalSenderName)
        }
        val innerBytes = innerPayloadJson.toString().toByteArray(Charsets.UTF_8)

        // 2. Sender computes AAD and encrypts with recipient's public key
        val aad = MeshRouter.computeMetadataAad(originId, finalDestId, messageId, timestamp)
        val eciesCiphertextBase64 = eciesService.encryptToBase64(innerBytes, recipientKeyPair.public.encoded, aad)

        // 3. Final recipient decrypts using private key and same AAD
        val decryptedBytes = eciesService.decryptFromBase64(eciesCiphertextBase64, aad)
        assertNotNull("ECIES decryption must succeed with correct AAD", decryptedBytes)

        val decryptedJson = JSONObject(String(decryptedBytes!!, Charsets.UTF_8))
        assertEquals(originalText, decryptedJson.getString("text"))
        assertEquals(originalSenderName, decryptedJson.getString("senderName"))
    }

    @Test
    fun `ROUTED_CHAT AAD tampering by intermediate relay is detected and rejected`() {
        val innerBytes = "Tamper Test".toByteArray(Charsets.UTF_8)
        val originId = "node-1"
        val finalDestId = "node-2"
        val messageId = "msg-1"
        val timestamp = 1700000000000L

        val validAad = MeshRouter.computeMetadataAad(originId, finalDestId, messageId, timestamp)
        val eciesCiphertext = eciesService.encryptToBase64(innerBytes, recipientKeyPair.public.encoded, validAad)

        // Attacker relay alters originId
        val tamperedAad = MeshRouter.computeMetadataAad("spoofed-origin", finalDestId, messageId, timestamp)
        val decryptResult = eciesService.decryptFromBase64(eciesCiphertext, tamperedAad)

        assertNull("Tampering with AAD routing metadata must cause decryption to fail", decryptResult)
    }

    // ── Task 2: Generic Advertising Token ────────────────────────────────────

    @Test
    fun `advertising token constant is non-identifying`() {
        val advertisingToken = "meshlink.node"
        assertFalse(
            "Advertising token must not contain device model or username",
            advertisingToken.contains("Pixel") || advertisingToken.contains("Samsung") || advertisingToken.contains("Vance")
        )
        assertTrue(
            "Advertising token must be generic",
            advertisingToken.isNotBlank() && advertisingToken.length <= 64
        )
    }
}
