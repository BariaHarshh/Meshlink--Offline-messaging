package com.meshlink.app.crypto.cipher

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Unit tests for [EncryptionService] (AES-256-GCM AEAD).
 */
class EncryptionServiceTest {

    private lateinit var encryptionService: EncryptionService
    private lateinit var secretKey: SecretKey
    private val secureRandom = SecureRandom()

    @Before
    fun setUp() {
        encryptionService = EncryptionService()
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        secretKey = keyGen.generateKey()
    }

    @Test
    fun `encrypt and decrypt succeeds with valid key`() {
        val plaintext = "Hello, MeshLink secure mesh networking!".toByteArray(Charsets.UTF_8)
        val encrypted = encryptionService.encrypt(plaintext, secretKey)

        assertNotNull(encrypted)
        assertTrue(encrypted.size > plaintext.size)

        val decrypted = encryptionService.decrypt(encrypted, secretKey)
        assertNotNull(decrypted)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt and decrypt succeeds with AAD metadata bound`() {
        val plaintext = "Authenticated with metadata".toByteArray(Charsets.UTF_8)
        val aad = "origin:dest:msg123:1700000000".toByteArray(Charsets.UTF_8)

        val encrypted = encryptionService.encrypt(plaintext, secretKey, aad)
        val decrypted = encryptionService.decrypt(encrypted, secretKey, aad)

        assertNotNull(decrypted)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt fails when AAD metadata is modified`() {
        val plaintext = "Secret message".toByteArray(Charsets.UTF_8)
        val originalAad = "originA:destB:msg1:1000".toByteArray(Charsets.UTF_8)
        val tamperedAad = "originA:destB:msg1:2000".toByteArray(Charsets.UTF_8)

        val encrypted = encryptionService.encrypt(plaintext, secretKey, originalAad)
        val decrypted = encryptionService.decrypt(encrypted, secretKey, tamperedAad)

        assertNull("Decryption must fail when AAD is tampered", decrypted)
    }

    @Test
    fun `decrypt fails when AAD is missing on decryption`() {
        val plaintext = "Secret message".toByteArray(Charsets.UTF_8)
        val aad = "originA:destB:msg1:1000".toByteArray(Charsets.UTF_8)

        val encrypted = encryptionService.encrypt(plaintext, secretKey, aad)
        val decrypted = encryptionService.decrypt(encrypted, secretKey, null)

        assertNull("Decryption must fail when expected AAD is missing", decrypted)
    }

    @Test
    fun `decrypt fails when ciphertext body is modified`() {
        val plaintext = "Confidential data payload".toByteArray(Charsets.UTF_8)
        val encrypted = encryptionService.encrypt(plaintext, secretKey)

        // Modify ciphertext byte (between nonce offset 12 and tag offset length - 16)
        val tampered = encrypted.copyOf()
        tampered[14] = (tampered[14].toInt() xor 0xFF).toByte()

        val decrypted = encryptionService.decrypt(tampered, secretKey)
        assertNull("Decryption must fail on corrupted ciphertext", decrypted)
    }

    @Test
    fun `decrypt fails when GCM authentication tag is modified`() {
        val plaintext = "Confidential data payload".toByteArray(Charsets.UTF_8)
        val encrypted = encryptionService.encrypt(plaintext, secretKey)

        // Modify last byte (part of the 16-byte GCM tag)
        val tampered = encrypted.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0x01).toByte()

        val decrypted = encryptionService.decrypt(tampered, secretKey)
        assertNull("Decryption must fail on corrupted GCM authentication tag", decrypted)
    }

    @Test
    fun `decrypt fails with wrong key`() {
        val plaintext = "Secret message".toByteArray(Charsets.UTF_8)
        val encrypted = encryptionService.encrypt(plaintext, secretKey)

        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val wrongKey = keyGen.generateKey()

        val decrypted = encryptionService.decrypt(encrypted, wrongKey)
        assertNull("Decryption must fail with wrong key", decrypted)
    }

    @Test
    fun `nonce handling generates unique nonces for identical plaintexts`() {
        val plaintext = "Identical message content".toByteArray(Charsets.UTF_8)

        val enc1 = encryptionService.encrypt(plaintext, secretKey)
        val enc2 = encryptionService.encrypt(plaintext, secretKey)

        // Nonce is the first 12 bytes
        val nonce1 = enc1.copyOfRange(0, 12)
        val nonce2 = enc2.copyOfRange(0, 12)

        assertFalse("Nonces must be unique across encryptions", nonce1.contentEquals(nonce2))
        assertFalse("Ciphertexts must differ due to unique nonces", enc1.contentEquals(enc2))
    }

    @Test
    fun `decrypt returns null for payload shorter than nonce length`() {
        val shortPayload = ByteArray(10) { 0x01 }
        assertNull(encryptionService.decrypt(shortPayload, secretKey))
    }

    @Test
    fun `decrypt returns null for empty payload`() {
        assertNull(encryptionService.decrypt(ByteArray(0), secretKey))
    }
}
