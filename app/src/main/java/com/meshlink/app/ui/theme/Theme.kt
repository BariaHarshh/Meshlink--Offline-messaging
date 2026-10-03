package com.meshlink.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// ── Light scheme — MeshLink design system ───────────────────────────────────────
private val MeshLinkColorScheme = lightColorScheme(
    primary             = Primary,
    onPrimary           = TextOnPrimary,
    primaryContainer    = PrimaryContainer,
    onPrimaryContainer  = Primary,

    secondary           = Secondary,
    onSecondary         = TextOnPrimary,
    secondaryContainer  = SecondaryContainer,
    onSecondaryContainer= Secondary,

    tertiary            = Tertiary,
    onTertiary          = TextOnPrimary,
    tertiaryContainer   = TertiaryContainer,
    onTertiaryContainer = Tertiary,

    background          = Background,
    onBackground        = TextPrimary,
    surface             = Surface,
    onSurface           = TextPrimary,
    surfaceVariant      = SurfaceVariant,
    onSurfaceVariant    = TextSecondary,

    outline             = Outline,
    outlineVariant      = OutlineVariant,

    error               = Error,
    onError             = TextOnPrimary,
    errorContainer      = ErrorContainer,
    onErrorContainer    = Error,

    scrim               = TextPrimary.copy(alpha = 0.32f)
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
