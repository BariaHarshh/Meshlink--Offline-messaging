package com.meshlink.app.mesh.util

import com.meshlink.app.domain.model.MeshPacket
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
        // User identity
        if (senderName.isNotEmpty()) put("senderName", senderName)
    }
    return json.toString().toByteArray(Charsets.UTF_8)
}

fun ByteArray.toMeshPacket(): MeshPacket? = try {
    val json      = JSONObject(toString(Charsets.UTF_8))
    val histArray = json.optJSONArray("routeHistory")
    val history   = buildList {
        if (histArray != null) for (i in 0 until histArray.length()) add(histArray.getString(i))
    }
    val senderId  = json.getString("senderId")
    val receiverId = json.getString("receiverId")
    MeshPacket(
        messageId    = json.optString("messageId", UUID.randomUUID().toString()),
        senderId     = senderId,
        receiverId   = receiverId,
        content      = json.getString("content"),
        timestamp    = json.getLong("timestamp"),
        type         = MeshPacket.PacketType.valueOf(json.optString("type", "CHAT")),
        // Phase 4 — fall back to Phase 3 values for backward compat with older peers
        originId     = json.optString("originId",    senderId),
        finalDestId  = json.optString("finalDestId", receiverId),
        hopCount     = json.optInt   ("hopCount",    0),
        maxHops      = json.optInt   ("maxHops",     7),
        routeHistory = history,
        // User identity — backward compat: older peers won't send this field
        senderName   = json.optString("senderName", "")
    )
} catch (e: Exception) {
    Timber.e(e, "Failed to deserialize MeshPacket")
    null
}
