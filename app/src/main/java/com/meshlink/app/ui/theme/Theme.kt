package com.meshlink.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// ── Light scheme — Figma design system ───────────────────────────────────────
private val MeshLinkColorScheme = lightColorScheme(
    primary             = Primary,
    onPrimary           = TextOnPrimary,
    primaryContainer    = PrimaryContainer,
    onPrimaryContainer  = PrimaryDark,

    secondary           = Secondary,
    onSecondary         = TextOnPrimary,
    secondaryContainer  = SecondaryContainer,
    onSecondaryContainer= SecondaryDark,

    tertiary            = Tertiary,
    onTertiary          = TextOnPrimary,
    tertiaryContainer   = TertiaryContainer,
    onTertiaryContainer = TertiaryDark,

    background          = AppBackground,
    onBackground        = TextPrimary,
    surface             = AppBackground,
    onSurface           = TextPrimary,
    surfaceVariant      = CardSurface,
    onSurfaceVariant    = TextSecondary,

    outline             = Outline,
    outlineVariant      = OutlineVariant,

    error               = Primary,
    onError             = TextOnPrimary,
    errorContainer      = PrimaryLight,
    onErrorContainer    = PrimaryDark,

    scrim               = Neutral.copy(alpha = 0.32f)
)

// ── App-level theme (light, white background) ───────────────────────────────
@Composable
fun MeshLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MeshLinkColorScheme,
        typography  = MeshTypography,
        content     = content
    )
}

// ── Alias for Medical Profile (same as main theme now — both are light) ─────
@Composable
fun MeshLinkLightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MeshLinkColorScheme,
        typography  = MeshTypography,
        content     = content
    )
}
