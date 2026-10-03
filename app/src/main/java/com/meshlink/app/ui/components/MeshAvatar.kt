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
 * color background tuned for MeshLink's pastel theme.
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

// Pastel avatar palette
private val avatarPalette = listOf(
    Color(0xFFC4717A), // dusty rose
    Color(0xFFA87BAF), // soft lavender
    Color(0xFF6A9E72), // soft green
    Color(0xFF7B8FC4), // soft blue
    Color(0xFFB87A5E), // warm terracotta
    Color(0xFF9B7AB8), // medium purple
    Color(0xFF5E9E8F), // teal
    Color(0xFFC48A5E), // warm amber
    Color(0xFF8E7AB0)  // dusty purple
)

private fun avatarColor(name: String): Color {
    val idx = Math.abs(name.hashCode()) % avatarPalette.size
    return avatarPalette[idx]
}
