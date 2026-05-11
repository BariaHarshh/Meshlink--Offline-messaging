package com.meshlink.app.domain.model

enum class ConnectionState {
    IDLE,
    CONNECTING,
    /** Nearby link established; ECDH handshake in progress. */
    HANDSHAKING,
    /** Handshake complete — AES-256 session key derived, messages can be sent. */
    CONNECTED,
    DISCONNECTED
}
