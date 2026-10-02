package com.meshlink.app.mesh.util

import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.mesh.routing.PacketValidator
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID

fun MeshPacket.toBytes(): ByteArray {
    val histArray = JSONArray().also { arr -> routeHistory.forEach { arr.put(it) } }
    val json = JSONObject().apply {
        put("messageId",    messageId)
        put("senderId",     senderId)
        put("receiverId",   receiverId)
        put("content",      content)
        put("timestamp",    timestamp)
        put("type",         type.name)
        // Phase 4 routing fields
        put("originId",     originId)
        put("finalDestId",  finalDestId)
        put("hopCount",     hopCount)
        put("maxHops",      maxHops)
        put("routeHistory", histArray)
        // Phase 3A: Outer routing header never exposes senderName for multi-hop ROUTED_CHAT or ACK packets.
        // Intermediate relays only require routing identifiers (originId, finalDestId, hopCount).
        if (type != MeshPacket.PacketType.ROUTED_CHAT && type != MeshPacket.PacketType.ACK && senderName.isNotEmpty()) {
            put("senderName", senderName)
        }
    }
    return json.toString().toByteArray(Charsets.UTF_8)
}

fun ByteArray.toMeshPacket(): MeshPacket? = try {
    // 1. Raw byte size validation
    val rawValidation = PacketValidator.validateRawBytes(this)
    if (rawValidation is PacketValidator.ValidationResult.Invalid) {
        null
    } else {
        val json      = JSONObject(toString(Charsets.UTF_8))
        val histArray = json.optJSONArray("routeHistory")
        val history   = buildList {
            if (histArray != null) for (i in 0 until histArray.length()) add(histArray.getString(i))
        }
        val senderId   = json.getString("senderId")
        val receiverId = json.getString("receiverId")
        val packet = MeshPacket(
            messageId    = json.optString("messageId", UUID.randomUUID().toString()),
            senderId     = senderId,
            receiverId   = receiverId,
            content      = json.getString("content"),
            timestamp    = json.getLong("timestamp"),
            type         = MeshPacket.PacketType.valueOf(json.optString("type", "CHAT")),
            originId     = json.optString("originId",    senderId),
            finalDestId  = json.optString("finalDestId", receiverId),
            hopCount     = json.optInt   ("hopCount",    0),
            maxHops      = json.optInt   ("maxHops",     7),
            routeHistory = history,
            senderName   = json.optString("senderName", "")
        )

        // 2. Semantic field and limit validation
        val packetValidation = PacketValidator.validatePacket(packet)
        if (packetValidation is PacketValidator.ValidationResult.Invalid) {
            null
        } else {
            packet
        }
    }
} catch (e: Exception) {
    Timber.w(e, "Failed to deserialize or validate MeshPacket — packet rejected")
    null
}
