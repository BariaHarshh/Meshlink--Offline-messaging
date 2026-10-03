package com.meshlink.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.meshlink.app.domain.model.VerificationStatus

private data class SecurityBadgeStyle(
    val icon: ImageVector,
    val tint: Color,
    val text: String,
    val bgColor: Color
)

@Composable
private fun securityBadgeStyle(
    verificationStatus: VerificationStatus?
): SecurityBadgeStyle {
    return when (verificationStatus) {
        VerificationStatus.VERIFIED -> SecurityBadgeStyle(
            icon    = Icons.Filled.Verified,
            tint    = MaterialTheme.colorScheme.tertiary,
            text    = "End-to-end encrypted · P-256 verified",
            bgColor = MaterialTheme.colorScheme.tertiaryContainer
        )
        VerificationStatus.REVOKED -> SecurityBadgeStyle(
            icon    = Icons.Filled.Warning,
            tint    = MaterialTheme.colorScheme.error,
            text    = "Warning: Identity Revoked",
            bgColor = MaterialTheme.colorScheme.errorContainer
        )
        else -> SecurityBadgeStyle(
            icon    = Icons.Filled.Lock,
            tint    = MaterialTheme.colorScheme.secondary,
            text    = "End-to-end encrypted",
            bgColor = MaterialTheme.colorScheme.secondaryContainer
        )
    }
}

/**
 * Displays an encryption/verification status badge.
 *
 * - Shows "End-to-end encrypted" always when [isEncrypted] = true.
 * - Shows "P-256 verified" ONLY when [verificationStatus] == VERIFIED.
 * - Shows a warning when [verificationStatus] == REVOKED.
 * - For UNKNOWN/UNVERIFIED: shows encryption badge only, no verification claim.
 */
@Composable
fun SecurityBadge(
    isEncrypted: Boolean,
    verificationStatus: VerificationStatus? = null,
    modifier: Modifier = Modifier
) {
    if (!isEncrypted) return

    val style = securityBadgeStyle(verificationStatus)

    Row(
        modifier = modifier
            .background(style.bgColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = style.icon,
            contentDescription = null,
            tint               = style.tint,
            modifier           = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text  = style.text,
            color = style.tint,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
