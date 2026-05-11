package com.meshlink.app.domain.model

/**
 * A MeshPacket queued for store-and-forward delivery.
 *
 * Created when [NearbyRepositoryImpl.routeToDevice] cannot find any connected peer that
 * can forward the packet to [targetDeviceId]. The message is held until:
 *   • A peer who can reach [targetDeviceId] connects  → delivery attempted
 *   • [expiresAt] is reached                          → [MeshCleanupWorker] deletes it
 *
 * @param id           Unique queue entry id (separate from the inner packet's messageId).
 * @param packetJson   Serialized MeshPacket (JSON). Re-deserialized when delivery is retried.
 * @param targetDeviceId Stable crypto deviceId of the final recipient.
 * @param enqueuedAt   Epoch-millis when the entry was created.
 * @param expiresAt    Epoch-millis after which this entry must be discarded (default 48h).
 */
data class PendingMessage(
    val id: String,
    val packetJson: String,
    val targetDeviceId: String,
    val enqueuedAt: Long,
    val expiresAt: Long
)
