package com.meshlink.app.mesh.routing

import com.meshlink.app.domain.model.MeshPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketValidatorTest {

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun validPacket(
        messageId:    String = "msg-001",
        senderId:     String = "sender-abc",
        receiverId:   String = "receiver-xyz",
        content:      String = "hello",
        timestamp:    Long   = System.currentTimeMillis(),
        hopCount:     Int    = 0,
        maxHops:      Int    = 7,
        routeHistory: List<String> = emptyList(),
        senderName:   String = "Alice",
        originId:     String = "sender-abc",
        finalDestId:  String = "receiver-xyz"
    ) = MeshPacket(
        messageId    = messageId,
        senderId     = senderId,
        receiverId   = receiverId,
        content      = content,
        timestamp    = timestamp,
        hopCount     = hopCount,
        maxHops      = maxHops,
        routeHistory = routeHistory,
        senderName   = senderName,
        originId     = originId,
        finalDestId  = finalDestId
    )

    private fun isValid(result: PacketValidator.ValidationResult) =
        result is PacketValidator.ValidationResult.Valid

    private fun isInvalid(result: PacketValidator.ValidationResult) =
        result is PacketValidator.ValidationResult.Invalid

    // ── Raw byte validation ───────────────────────────────────────────────────

    @Test
    fun `validateRawBytes - empty byte array returns Invalid`() {
        assertTrue(isInvalid(PacketValidator.validateRawBytes(ByteArray(0))))
    }

    @Test
    fun `validateRawBytes - single byte returns Valid`() {
        assertTrue(isValid(PacketValidator.validateRawBytes(ByteArray(1) { 0x42 })))
    }

    @Test
    fun `validateRawBytes - exactly MAX_PACKET_SIZE bytes returns Valid`() {
        assertTrue(isValid(PacketValidator.validateRawBytes(ByteArray(PacketValidator.MAX_PACKET_SIZE))))
    }

    @Test
    fun `validateRawBytes - one byte over MAX_PACKET_SIZE returns Invalid`() {
        assertTrue(isInvalid(PacketValidator.validateRawBytes(ByteArray(PacketValidator.MAX_PACKET_SIZE + 1))))
    }

    // ── Valid packet ──────────────────────────────────────────────────────────

    @Test
    fun `validatePacket - valid packet returns Valid`() {
        assertTrue(isValid(PacketValidator.validatePacket(validPacket())))
    }

    // ── ID field validation ───────────────────────────────────────────────────

    @Test
    fun `validatePacket - blank messageId returns Invalid`() {
        assertTrue(isInvalid(PacketValidator.validatePacket(validPacket(messageId = "  "))))
    }

    @Test
    fun `validatePacket - messageId exceeding MAX_ID_LENGTH returns Invalid`() {
        val longId = "x".repeat(PacketValidator.MAX_ID_LENGTH + 1)
        assertTrue(isInvalid(PacketValidator.validatePacket(validPacket(messageId = longId))))
    }

    @Test
    fun `validatePacket - messageId exactly MAX_ID_LENGTH returns Valid`() {
        val id = "a".repeat(PacketValidator.MAX_ID_LENGTH)
        assertTrue(isValid(PacketValidator.validatePacket(validPacket(messageId = id))))
    }

    @Test
    fun `validatePacket - blank senderId returns Invalid`() {
        assertTrue(isInvalid(PacketValidator.validatePacket(validPacket(senderId = ""))))
    }

    @Test
    fun `validatePacket - blank receiverId returns Invalid`() {
        assertTrue(isInvalid(PacketValidator.validatePacket(validPacket(receiverId = ""))))
    }

    @Test
    fun `validatePacket - blank originId returns Invalid`() {
        assertTrue(isInvalid(PacketValidator.validatePacket(validPacket(originId = ""))))
    }

    @Test
    fun `validatePacket - blank finalDestId returns Invalid`() {
        assertTrue(isInvalid(PacketValidator.validatePacket(validPacket(finalDestId = ""))))
    }

    // ── Content size ─────────────────────────────────────────────────────────

    @Test
    fun `validatePacket - content exactly MAX_MESSAGE_SIZE chars returns Valid`() {
        val bigContent = "a".repeat(PacketValidator.MAX_MESSAGE_SIZE)
        assertTrue(isValid(PacketValidator.validatePacket(validPacket(content = bigContent))))
    }

    @Test
    fun `validatePacket - content over MAX_MESSAGE_SIZE returns Invalid`() {
        val tooBig = "a".repeat(PacketValidator.MAX_MESSAGE_SIZE + 1)
        assertTrue(isInvalid(PacketValidator.validatePacket(validPacket(content = tooBig))))
    }

    // ── Hop count ─────────────────────────────────────────────────────────────

    @Test
    fun `validatePacket - negative hopCount returns Invalid`() {
        assertTrue(isInvalid(PacketValidator.validatePacket(validPacket(hopCount = -1))))
    }

    @Test
    fun `validatePacket - maxHops of 0 returns Invalid`() {
        assertTrue(isInvalid(PacketValidator.validatePacket(validPacket(maxHops = 0))))
    }

    @Test
    fun `validatePacket - maxHops over MAX_HOPS returns Invalid`() {
        assertTrue(isInvalid(PacketValidator.validatePacket(validPacket(maxHops = PacketValidator.MAX_HOPS + 1))))
    }

    @Test
    fun `validatePacket - hopCount exceeds maxHops returns Invalid`() {
        assertTrue(isInvalid(PacketValidator.validatePacket(validPacket(hopCount = 8, maxHops = 7))))
    }

    @Test
    fun `validatePacket - hopCount equals maxHops returns Valid`() {
        assertTrue(isValid(PacketValidator.validatePacket(validPacket(hopCount = 7, maxHops = 7))))
    }

    // ── Route history ─────────────────────────────────────────────────────────

    @Test
    fun `validatePacket - route history over MAX_ROUTE_HISTORY returns Invalid`() {
        val tooManyHops = (1..PacketValidator.MAX_ROUTE_HISTORY + 1).map { "node-$it" }
        assertTrue(isInvalid(PacketValidator.validatePacket(validPacket(routeHistory = tooManyHops))))
    }

    @Test
    fun `validatePacket - exactly MAX_ROUTE_HISTORY hops returns Valid`() {
        val maxHops = (1..PacketValidator.MAX_ROUTE_HISTORY).map { "node-$it" }
        assertTrue(isValid(PacketValidator.validatePacket(validPacket(routeHistory = maxHops, hopCount = maxHops.size))))
    }

    @Test
    fun `validatePacket - routing loop in history returns Invalid`() {
        val loopHistory = listOf("node-A", "node-B", "node-A")  // A appears twice
        assertTrue(isInvalid(PacketValidator.validatePacket(validPacket(routeHistory = loopHistory))))
    }

    @Test
    fun `validatePacket - blank node in route history returns Invalid`() {
        val historyWithBlank = listOf("node-A", "", "node-B")
        assertTrue(isInvalid(PacketValidator.validatePacket(validPacket(routeHistory = historyWithBlank))))
    }

    // ── Sender name ───────────────────────────────────────────────────────────

    @Test
    fun `validatePacket - senderName exactly MAX_SENDER_NAME_LENGTH returns Valid`() {
        val name = "A".repeat(PacketValidator.MAX_SENDER_NAME_LENGTH)
        assertTrue(isValid(PacketValidator.validatePacket(validPacket(senderName = name))))
    }

    @Test
    fun `validatePacket - senderName over MAX_SENDER_NAME_LENGTH returns Invalid`() {
        val name = "A".repeat(PacketValidator.MAX_SENDER_NAME_LENGTH + 1)
        assertTrue(isInvalid(PacketValidator.validatePacket(validPacket(senderName = name))))
    }

    @Test
    fun `validatePacket - empty senderName is Valid`() {
        assertTrue(isValid(PacketValidator.validatePacket(validPacket(senderName = ""))))
    }

    // ── Timestamp / clock skew ────────────────────────────────────────────────

    @Test
    fun `validatePacket - future timestamp within skew is Valid`() {
        val nearFuture = System.currentTimeMillis() + (PacketValidator.MAX_CLOCK_SKEW_MS - 1_000)
        assertTrue(isValid(PacketValidator.validatePacket(validPacket(timestamp = nearFuture))))
    }

    @Test
    fun `validatePacket - timestamp too far in the future returns Invalid`() {
        val farFuture = System.currentTimeMillis() + PacketValidator.MAX_CLOCK_SKEW_MS + 60_000
        assertTrue(isInvalid(PacketValidator.validatePacket(validPacket(timestamp = farFuture))))
    }

    @Test
    fun `validatePacket - recent past timestamp is Valid`() {
        val recentPast = System.currentTimeMillis() - 5 * 60_000L   // 5 minutes ago
        assertTrue(isValid(PacketValidator.validatePacket(validPacket(timestamp = recentPast))))
    }

    @Test
    fun `validatePacket - expired timestamp returns Invalid`() {
        val tooOld = System.currentTimeMillis() - PacketValidator.MAX_PACKET_AGE_MS - 60_000
        assertTrue(isInvalid(PacketValidator.validatePacket(validPacket(timestamp = tooOld))))
    }

    @Test
    fun `validatePacket - timestamp exactly at expiry boundary is Invalid`() {
        val justExpired = System.currentTimeMillis() - PacketValidator.MAX_PACKET_AGE_MS - 1
        assertTrue(isInvalid(PacketValidator.validatePacket(validPacket(timestamp = justExpired))))
    }

    // ── Invalid-reason string ─────────────────────────────────────────────────

    @Test
    fun `Invalid result contains a non-empty reason`() {
        val result = PacketValidator.validatePacket(validPacket(messageId = ""))
        assertTrue(result is PacketValidator.ValidationResult.Invalid)
        val reason = (result as PacketValidator.ValidationResult.Invalid).reason
        assertTrue(reason.isNotBlank())
    }

    // ── Constant sanity checks ─────────────────────────────────────────────────

    @Test
    fun `constants have expected minimum values`() {
        assertTrue(PacketValidator.MAX_PACKET_SIZE  >= 32_000)
        assertTrue(PacketValidator.MAX_MESSAGE_SIZE >= 16_000)
        assertTrue(PacketValidator.MAX_HOPS         >= 5)
        assertTrue(PacketValidator.MAX_ROUTE_HISTORY >= 5)
        assertTrue(PacketValidator.MAX_CLOCK_SKEW_MS > 0)
        assertTrue(PacketValidator.MAX_PACKET_AGE_MS > PacketValidator.MAX_CLOCK_SKEW_MS)
    }
}
