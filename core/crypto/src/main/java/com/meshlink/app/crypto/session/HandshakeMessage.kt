package com.meshlink.app.crypto.session

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64

/**
 * Handshake message exchange models and deterministic transcript generation.
 */
sealed class HandshakeMessage {

    /**
     * Initial message sent by each peer upon link establishment.
     * Contains long-term identity public key, ephemeral public key, and fresh challenge nonce.
     */
    data class Init(
        val protocolVersion: Int = PROTOCOL_VERSION,
        val deviceId: String,
        val identityPublicKey: ByteArray,
        val ephemeralPublicKey: ByteArray,
        val challenge: ByteArray,
        val timestamp: Long
    ) : HandshakeMessage() {
        fun toJson(): String {
            val encoder = Base64.getEncoder()
            val json = JSONObject()
            json.put("type", "INIT")
            json.put("version", protocolVersion)
            json.put("deviceId", deviceId)
            json.put("identityKey", encoder.encodeToString(identityPublicKey))
            json.put("ephemeralKey", encoder.encodeToString(ephemeralPublicKey))
            json.put("challenge", encoder.encodeToString(challenge))
            json.put("timestamp", timestamp)
            return json.toString()
        }
    }

    /**
     * Authentication message responding to peer's Init.
     * Contains digital signature proving possession of identity private key over the handshake transcript.
     */
    data class Auth(
        val protocolVersion: Int = PROTOCOL_VERSION,
        val deviceId: String,
        val signature: ByteArray,
        val timestamp: Long
    ) : HandshakeMessage() {
        fun toJson(): String {
            val json = JSONObject()
            json.put("type", "AUTH")
            json.put("version", protocolVersion)
            json.put("deviceId", deviceId)
            json.put("signature", Base64.getEncoder().encodeToString(signature))
            json.put("timestamp", timestamp)
            return json.toString()
        }
    }

    companion object {
        const val PROTOCOL_VERSION = 1
        private const val TRANSCRIPT_DOMAIN = "MeshLink-Handshake-v1"

        /**
         * Parses a handshake message from packet content string.
         * Fails closed by returning null on any parsing error or unknown type.
         */
        fun fromJsonOrNull(content: String): HandshakeMessage? = try {
            val decoder = Base64.getDecoder()
            val json = JSONObject(content)
            when (json.optString("type")) {
                "INIT" -> {
                    val version = json.getInt("version")
                    val deviceId = json.getString("deviceId")
                    val identityKey = decoder.decode(json.getString("identityKey"))
                    val ephemeralKey = decoder.decode(json.getString("ephemeralKey"))
                    val challenge = decoder.decode(json.getString("challenge"))
                    val timestamp = json.getLong("timestamp")
                    Init(version, deviceId, identityKey, ephemeralKey, challenge, timestamp)
                }
                "AUTH" -> {
                    val version = json.getInt("version")
                    val deviceId = json.getString("deviceId")
                    val signature = decoder.decode(json.getString("signature"))
                    val timestamp = json.getLong("timestamp")
                    Auth(version, deviceId, signature, timestamp)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }

        /**
         * Builds a deterministic binary transcript for ECDSA signing and verification.
         *
         * Transcript layout:
         *   1. DOMAIN: "MeshLink-Handshake-v1" UTF-8 bytes
         *   2. PROTOCOL_VERSION: 32-bit big-endian int
         *   3. SIGNER_DEVICE_ID: 16-bit length prefix + UTF-8 bytes
         *   4. SIGNER_IDENTITY_PUBKEY: 16-bit length prefix + X.509 bytes
         *   5. SIGNER_EPHEMERAL_PUBKEY: 16-bit length prefix + X.509 bytes
         *   6. PEER_CHALLENGE_NONCE: 16-bit length prefix + 32 nonce bytes
         *   7. PEER_DEVICE_ID: 16-bit length prefix + UTF-8 bytes
         *
         * Length prefixes and strict typing prevent canonicalization and concatenation attacks.
         */
        fun buildTranscript(
            protocolVersion: Int,
            signerDeviceId: String,
            signerIdentityPubKey: ByteArray,
            signerEphemeralPubKey: ByteArray,
            peerChallengeNonce: ByteArray,
            peerDeviceId: String
        ): ByteArray {
            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)

            dos.write(TRANSCRIPT_DOMAIN.toByteArray(Charsets.UTF_8))
            dos.writeInt(protocolVersion)

            val signerDevBytes = signerDeviceId.toByteArray(Charsets.UTF_8)
            dos.writeShort(signerDevBytes.size)
            dos.write(signerDevBytes)

            dos.writeShort(signerIdentityPubKey.size)
            dos.write(signerIdentityPubKey)

            dos.writeShort(signerEphemeralPubKey.size)
            dos.write(signerEphemeralPubKey)

            dos.writeShort(peerChallengeNonce.size)
            dos.write(peerChallengeNonce)

            val peerDevBytes = peerDeviceId.toByteArray(Charsets.UTF_8)
            dos.writeShort(peerDevBytes.size)
            dos.write(peerDevBytes)

            dos.flush()
            return baos.toByteArray()
        }
    }
}
