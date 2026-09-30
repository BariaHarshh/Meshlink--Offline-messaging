package com.meshlink.app.crypto.cipher

import com.meshlink.app.crypto.identity.KeyManager
import io.mockk.every
import io.mockk.mockk
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
 * Unit tests for [EciesService] (Multi-hop ECIES encryption and decryption).
 */
class EciesServiceTest {

    private lateinit var keyManager: KeyManager
    private lateinit var encryptionService: EncryptionService
    private lateinit var eciesService: EciesService

    private lateinit var recipientKeyPair: KeyPair
    private lateinit var otherKeyPair: KeyPair

    @Before
    fun setUp() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        recipientKeyPair = kpg.generateKeyPair()
        otherKeyPair = kpg.generateKeyPair()

        keyManager = mockk(relaxed = true)
        every { keyManager.keyPair } returns recipientKeyPair

        encryptionService = EncryptionService()
        eciesService = EciesService(keyManager, encryptionService)
    }

    @Test
    fun `encrypt and decrypt succeeds with valid recipient key`() {
        val plaintext = "Multi-hop mesh routed message payload".toByteArray(Charsets.UTF_8)
        val encrypted = eciesService.encrypt(plaintext, recipientKeyPair.public.encoded)

        assertNotNull(encrypted)
        // Wire: ephemeralPubKey (91 B) + nonce (12 B) + ciphertext + tag (16 B) = 119 B + plaintext
        assertTrue(encrypted.size >= 119 + plaintext.size)

        val decrypted = eciesService.decrypt(encrypted)
        assertNotNull(decrypted)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt and decrypt succeeds with AAD metadata bound`() {
        val plaintext = "Confidential routed message".toByteArray(Charsets.UTF_8)
        val aad = "originDev1:destDev2:msg-999:1700000000000".toByteArray(Charsets.UTF_8)

        val encrypted = eciesService.encrypt(plaintext, recipientKeyPair.public.encoded, aad)
        val decrypted = eciesService.decrypt(encrypted, aad)

        assertNotNull(decrypted)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `encryptToBase64 and decryptFromBase64 round-trip succeeds with metadata AAD`() {
        val plaintext = "Base64 routed chat message".toByteArray(Charsets.UTF_8)
        val aad = "AAD:data:123".toByteArray(Charsets.UTF_8)

        val base64 = eciesService.encryptToBase64(plaintext, recipientKeyPair.public.encoded, aad)
        assertNotNull(base64)
        assertTrue(base64.isNotBlank())

        val decrypted = eciesService.decryptFromBase64(base64, aad)
        assertNotNull(decrypted)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt fails when AAD metadata is modified by relay`() {
        val plaintext = "Confidential routed message".toByteArray(Charsets.UTF_8)
        val originalAad = "originDev1:destDev2:msg-999:1700000000000".toByteArray(Charsets.UTF_8)
        val tamperedAad = "attackerDev:destDev2:msg-999:1700000000000".toByteArray(Charsets.UTF_8)

        val encrypted = eciesService.encrypt(plaintext, recipientKeyPair.public.encoded, originalAad)
        val decrypted = eciesService.decrypt(encrypted, tamperedAad)

        assertNull("Decryption must fail when relay tampers with metadata AAD", decrypted)
    }

    @Test
    fun `decrypt fails when ciphertext portion is modified`() {
        val plaintext = "Routed payload".toByteArray(Charsets.UTF_8)
        val encrypted = eciesService.encrypt(plaintext, recipientKeyPair.public.encoded)

        val tampered = encrypted.copyOf()
        // Tamper byte inside ciphertext (offset > 91 + 12)
        tampered[105] = (tampered[105].toInt() xor 0xFF).toByte()

        val decrypted = eciesService.decrypt(tampered)
        assertNull("Decryption must fail when ciphertext is modified", decrypted)
    }

    @Test
    fun `decrypt fails when ephemeral public key is tampered`() {
        val plaintext = "Routed payload".toByteArray(Charsets.UTF_8)
        val encrypted = eciesService.encrypt(plaintext, recipientKeyPair.public.encoded)

        val tampered = encrypted.copyOf()
        // Tamper byte inside the 91-byte ephemeral public key header
        tampered[10] = (tampered[10].toInt() xor 0xFF).toByte()

        val decrypted = eciesService.decrypt(tampered)
        assertNull("Decryption must fail when ephemeral key is corrupted", decrypted)
    }

    @Test
    fun `decrypt fails with wrong recipient private key`() {
        val plaintext = "Secret routed message".toByteArray(Charsets.UTF_8)
        val encrypted = eciesService.encrypt(plaintext, recipientKeyPair.public.encoded)

        // Mock keyManager to return another device's keypair
        every { keyManager.keyPair } returns otherKeyPair

        val decrypted = eciesService.decrypt(encrypted)
        assertNull("Decryption must fail when recipient does not have the corresponding private key", decrypted)
    }

    @Test
    fun `decrypt returns null for wire bytes shorter than minimum overhead`() {
        val shortWire = ByteArray(100) { 0x42 } // min overhead is 91 + 12 + 16 = 119
        assertNull(eciesService.decrypt(shortWire))
    }
}
