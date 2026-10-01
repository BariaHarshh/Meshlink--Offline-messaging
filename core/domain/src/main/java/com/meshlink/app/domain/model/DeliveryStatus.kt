package com.meshlink.app.domain.model

/**
 * Phase 4A: Message Delivery Lifecycle Status.
 *
 * Tracks the progression of an outgoing or incoming message across the mesh network.
 *
 * Lifecycle:
 * - [PENDING]: Initial state for a newly created message awaiting initial routing or transmission.
 * - [QUEUED]: Stored in store-and-forward queue (pending_messages) awaiting a reachable peer.
 * - [SENT]: Successfully transferred over the local link to the direct peer / next hop.
 * - [DELIVERED]: Confirmed end-to-end receipt (ACK received from destination) or received locally.
 * - [FAILED]: Unrecoverable failure (e.g., TTL expired, max retries reached).
 */
enum class DeliveryStatus {
    PENDING,
    QUEUED,
    SENT,
    DELIVERED,
    FAILED
}
