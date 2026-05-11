package com.meshlink.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Circular avatar showing the first 1–2 initials of [name] on a deterministic
 * color background tuned for MeshLink's dark theme.
 *
 * Used across Home, Discovery, and Chat screens.
 */
@Composable
fun MeshAvatar(
    name:     String,
    modifier: Modifier = Modifier,
    size:     Dp       = 46.dp
) {
    val initials = remember(name) { extractInitials(name) }
    val bgColor  = remember(name) { avatarColor(name) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
            .semantics { contentDescription = "Avatar for $name" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text  = initials,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize   = (size.value * 0.33f).sp,
                letterSpacing = 0.5.sp
            )
        )
    }
}

private fun extractInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.size >= 2              -> "${words[0][0]}${words[1][0]}".uppercase()
        words.size == 1 && words[0].length >= 2 -> words[0].take(2).uppercase()
        words.size == 1              -> words[0].take(1).uppercase()
        else                         -> "?"
    }
}

// Dark-theme friendly avatar palette — vibrant but not harsh on #080D1A background
private val avatarPalette = listOf(
    Color(0xFF00897B), // Teal
    Color(0xFF1976D2), // Blue
    Color(0xFF7B1FA2), // Purple
    Color(0xFFAD1457), // Pink
    Color(0xFF2E7D32), // Green
    Color(0xFF00838F), // Cyan
    Color(0xFFE65100), // Deep Orange
    Color(0xFF4527A0), // Deep Purple
    Color(0xFF558B2F)  // Light Green
)

private fun avatarColor(name: String): Color {
    val idx = Math.abs(name.hashCode()) % avatarPalette.size
    return avatarPalette[idx]
}
