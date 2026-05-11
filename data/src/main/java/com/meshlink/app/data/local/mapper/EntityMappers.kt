package com.meshlink.app.data.local.mapper

import com.meshlink.app.data.local.entity.KnownDeviceEntity
import com.meshlink.app.data.local.entity.MessageEntity
import com.meshlink.app.domain.model.KnownDevice
import com.meshlink.app.domain.model.Message

fun MessageEntity.toDomain(): Message = Message(
    id = id,
    senderId = senderId,
    receiverId = receiverId,
    ciphertext = ciphertext,
    timestamp = timestamp,
    delivered = delivered,
    senderName = senderName
)

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    senderId = senderId,
    receiverId = receiverId,
    ciphertext = ciphertext,
    timestamp = timestamp,
    delivered = delivered,
    senderName = senderName
)

fun KnownDeviceEntity.toDomain(): KnownDevice = KnownDevice(
    deviceId = deviceId,
    displayName = displayName,
    publicKey = publicKey,
    lastSeen = lastSeen
)

fun KnownDevice.toEntity(): KnownDeviceEntity = KnownDeviceEntity(
    deviceId = deviceId,
    displayName = displayName,
    publicKey = publicKey,
    lastSeen = lastSeen
)
