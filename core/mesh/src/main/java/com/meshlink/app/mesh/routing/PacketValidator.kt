package com.meshlink.app.mesh.routing

import com.meshlink.app.domain.model.MeshPacket
import timber.log.Timber

/**
 * Strict packet validation engine.
 *
 * Rejects malformed, oversized, replayed, or invalid packets before they
 * enter the routing pipeline or crypto decoders.
 */
object PacketValidator {

    /** Maximum allowed wire bytes for a raw packet (64 KB). */
    const val MAX_PACKET_SIZE = 64 * 1024

    /** Maximum allowed content character count (32 KB). */
    const val MAX_MESSAGE_SIZE = 32 * 1024

    /** Maximum number of intermediate relays in route history. */
    const val MAX_ROUTE_HISTORY = 10

    /** Absolute ceiling on packet hops. */
    const val MAX_HOPS = 10

    /** Maximum sender display name length. */
    const val MAX_SENDER_NAME_LENGTH = 64

    /** Maximum identifier length (deviceId, messageId, etc.). */
    const val MAX_ID_LENGTH = 64

    /** Clock-skew tolerance for packets with timestamps in the future (10 minutes). */
    const val MAX_CLOCK_SKEW_MS = 10 * 60 * 1000L

    /** Maximum packet age before automatic expiration (48 hours). */
    const val MAX_PACKET_AGE_MS = 48 * 60 * 60 * 1000L

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult() {
            init {
                Timber.w("PacketValidator REJECT: $reason")
            }
        }
    }

    /**
     * Validates raw incoming byte payload before deserialization.
     */
    fun validateRawBytes(bytes: ByteArray): ValidationResult {
        if (bytes.isEmpty()) {
            return ValidationResult.Invalid("Packet payload is empty")
        }
        if (bytes.size > MAX_PACKET_SIZE) {
            return ValidationResult.Invalid("Packet size (${bytes.size} bytes) exceeds MAX_PACKET_SIZE ($MAX_PACKET_SIZE bytes)")
        }
        return ValidationResult.Valid
    }

    /**
     * Validates all structural and semantic fields of a deserialized [MeshPacket].
     */
    fun validatePacket(
        packet: MeshPacket,
        now: Long = System.currentTimeMillis()
    ): ValidationResult {
        // 1. Required identifier fields
        if (packet.messageId.isBlank() || packet.messageId.length > MAX_ID_LENGTH) {
            return ValidationResult.Invalid("Invalid messageId: blank or exceeds $MAX_ID_LENGTH chars")
        }
        if (packet.senderId.isBlank() || packet.senderId.length > MAX_ID_LENGTH) {
            return ValidationResult.Invalid("Invalid senderId: blank or exceeds $MAX_ID_LENGTH chars")
        }
        if (packet.receiverId.isBlank() || packet.receiverId.length > MAX_ID_LENGTH) {
            return ValidationResult.Invalid("Invalid receiverId: blank or exceeds $MAX_ID_LENGTH chars")
        }
        if (packet.originId.isBlank() || packet.originId.length > MAX_ID_LENGTH) {
            return ValidationResult.Invalid("Invalid originId: blank or exceeds $MAX_ID_LENGTH chars")
        }
        if (packet.finalDestId.isBlank() || packet.finalDestId.length > MAX_ID_LENGTH) {
            return ValidationResult.Invalid("Invalid finalDestId: blank or exceeds $MAX_ID_LENGTH chars")
        }

        // 2. Payload size
        if (packet.content.length > MAX_MESSAGE_SIZE) {
            return ValidationResult.Invalid("Content length (${packet.content.length}) exceeds MAX_MESSAGE_SIZE ($MAX_MESSAGE_SIZE)")
        }

        // 3. Hop count and limits
        if (packet.hopCount < 0) {
            return ValidationResult.Invalid("Negative hopCount: ${packet.hopCount}")
        }
        if (packet.maxHops <= 0 || packet.maxHops > MAX_HOPS) {
            return ValidationResult.Invalid("Invalid maxHops: ${packet.maxHops} (allowed: 1..$MAX_HOPS)")
        }
        if (packet.hopCount > packet.maxHops) {
            return ValidationResult.Invalid("hopCount (${packet.hopCount}) exceeds maxHops (${packet.maxHops})")
        }

        // 4. Route history limits and cycle checks
        if (packet.routeHistory.size > MAX_ROUTE_HISTORY) {
            return ValidationResult.Invalid("Route history size (${packet.routeHistory.size}) exceeds MAX_ROUTE_HISTORY ($MAX_ROUTE_HISTORY)")
        }
        val seenNodes = HashSet<String>(packet.routeHistory.size)
        for (node in packet.routeHistory) {
            if (node.isBlank() || node.length > MAX_ID_LENGTH) {
                return ValidationResult.Invalid("Invalid node in routeHistory: blank or exceeds $MAX_ID_LENGTH chars")
            }
            if (!seenNodes.add(node)) {
                return ValidationResult.Invalid("Routing loop detected in routeHistory: duplicate node $node")
            }
        }

        // 5. Display name length
        if (packet.senderName.length > MAX_SENDER_NAME_LENGTH) {
            return ValidationResult.Invalid("senderName length (${packet.senderName.length}) exceeds MAX_SENDER_NAME_LENGTH ($MAX_SENDER_NAME_LENGTH)")
        }

        // 6. Timestamp validation (clock skew & expiration)
        if (packet.timestamp > now + MAX_CLOCK_SKEW_MS) {
            return ValidationResult.Invalid("Packet timestamp is too far in future (${packet.timestamp - now} ms ahead)")
        }
        if (packet.timestamp < now - MAX_PACKET_AGE_MS) {
            return ValidationResult.Invalid("Packet timestamp is expired (${now - packet.timestamp} ms old)")
        }

        return ValidationResult.Valid
    }
}
