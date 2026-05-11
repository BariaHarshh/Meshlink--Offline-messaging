package com.meshlink.app.domain.model

data class KnownDevice(
    val deviceId: String,
    val displayName: String,
    val publicKey: ByteArray,
    val lastSeen: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KnownDevice) return false
        return deviceId == other.deviceId
    }

    override fun hashCode(): Int = deviceId.hashCode()
}
