package com.meshlink.app.domain.model

import java.util.UUID

/**
 * An emergency contact stored on the user's Medical Profile.
 * Shared with nearby responders during an active SOS signal.
 */
data class EmergencyContact(
    val id:       String = UUID.randomUUID().toString(),
    val name:     String,
    val relation: String,   // e.g. "Spouse", "Father", "Team Lead"
    val phone:    String
)
