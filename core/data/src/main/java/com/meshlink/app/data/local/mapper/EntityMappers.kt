package com.meshlink.app.data.local.mapper

import com.meshlink.app.data.local.entity.KnownDeviceEntity
import com.meshlink.app.data.local.entity.MessageEntity
import com.meshlink.app.domain.model.DeliveryStatus
import com.meshlink.app.domain.model.KnownDevice
import com.meshlink.app.domain.model.Message
import com.meshlink.app.domain.model.VerificationStatus

fun MessageEntity.toDomain(): Message {
    val status = try {
        DeliveryStatus.valueOf(deliveryStatus)
    } catch (_: Exception) {
        if (delivered) DeliveryStatus.DELIVERED else DeliveryStatus.PENDING
    }
    return Message(
        id = id,
        senderId = senderId,
        receiverId = receiverId,
        ciphertext = ciphertext,
        timestamp = timestamp,
        delivered = delivered || status == DeliveryStatus.DELIVERED,
        senderName = senderName,
        deliveryStatus = status
    )
}

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    senderId = senderId,
    receiverId = receiverId,
    ciphertext = ciphertext,
    timestamp = timestamp,
    delivered = delivered || deliveryStatus == DeliveryStatus.DELIVERED,
    senderName = senderName,
    deliveryStatus = deliveryStatus.name
)

fun KnownDeviceEntity.toDomain(): KnownDevice = KnownDevice(
    deviceId = deviceId,
    displayName = displayName,
    publicKey = publicKey,
    lastSeen = lastSeen,
    firstSeen = firstSeen,
    verificationStatus = try {
        VerificationStatus.valueOf(verificationStatus)
    } catch (e: Exception) {
        VerificationStatus.UNVERIFIED
    }
)

fun KnownDevice.toEntity(): KnownDeviceEntity = KnownDeviceEntity(
    deviceId = deviceId,
    displayName = displayName,
    publicKey = publicKey,
    lastSeen = lastSeen,
    firstSeen = firstSeen,
    verificationStatus = verificationStatus.name
)
