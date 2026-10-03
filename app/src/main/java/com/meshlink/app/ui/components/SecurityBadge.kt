package com.meshlink.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshlink.app.domain.model.VerificationStatus

@Composable
fun SecurityBadge(
    isEncrypted: Boolean,
    verificationStatus: VerificationStatus? = null,
    modifier: Modifier = Modifier
) {
    if (!isEncrypted) return

    val (icon, tint, text, bgColor) = when (verificationStatus) {
        VerificationStatus.VERIFIED -> {
            listOf(
                Icons.Filled.Verified,
                MaterialTheme.colorScheme.tertiary,
                "🔒 End-to-end encrypted · P-256 verified",
                MaterialTheme.colorScheme.tertiaryContainer
            )
        }
        VerificationStatus.REVOKED -> {
            listOf(
                Icons.Filled.Warning,
                MaterialTheme.colorScheme.error,
                "Warning: Identity Revoked",
                MaterialTheme.colorScheme.errorContainer
            )
        }
        else -> {
            listOf(
                Icons.Filled.Lock,
                MaterialTheme.colorScheme.secondary,
                "🔒 End-to-end encrypted",
                MaterialTheme.colorScheme.secondaryContainer
            )
        }
    }

    Row(
        modifier = modifier
            .background(bgColor as androidx.compose.ui.graphics.Color, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon as androidx.compose.ui.graphics.vector.ImageVector,
            contentDescription = null,
            tint = tint as androidx.compose.ui.graphics.Color,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text as String,
            color = tint,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
