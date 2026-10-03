package com.meshlink.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.meshlink.app.domain.model.DeliveryStatus

@Composable
fun DeliveryStatusIndicator(
    status: DeliveryStatus,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    compact: Boolean = true
) {
    val (icon, color, label) = when (status) {
        DeliveryStatus.PENDING -> Triple(Icons.Filled.Schedule, MaterialTheme.colorScheme.onSurfaceVariant, "Preparing...")
        DeliveryStatus.QUEUED -> Triple(Icons.Filled.HourglassEmpty, Color(0xFFC48A5E), "Waiting for nearby device")
        DeliveryStatus.SENT -> Triple(Icons.Filled.Check, MaterialTheme.colorScheme.primary, "Sent")
        DeliveryStatus.DELIVERED -> Triple(Icons.Filled.DoneAll, MaterialTheme.colorScheme.tertiary, "Delivered")
        DeliveryStatus.FAILED -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.error, "Couldn't deliver")
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(if (compact) 16.dp else 24.dp)
        )
        if (showLabel) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = color,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
            )
        }
    }
}
