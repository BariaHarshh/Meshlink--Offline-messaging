package com.meshlink.app.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════════
//  MeshLink Design System
// ═══════════════════════════════════════════════════════════════════════════════

// ── Core Palette ─────────────────────────────────────────────────────────────
val Background          = Color(0xFFFBF2FB)   // soft warm lavender/pink-white
val Surface             = Color(0xFFFFFFFF)   // warm white
val SurfaceVariant      = Color(0xFFF5EEF5)   // slightly tinted off-white for cards
val Primary             = Color(0xFFC4717A)   // dusty rose / muted crimson
val PrimaryContainer    = Color(0xFFFADADD)   // light rose container
val Secondary           = Color(0xFFA87BAF)   // soft lavender/purple
val SecondaryContainer  = Color(0xFFEDE0F0)   // soft lavender container
val Tertiary            = Color(0xFF6A9E72)   // soft green — success/connected
val TertiaryContainer   = Color(0xFFD6EDD9)   // soft green container

// ── Text Colors ──────────────────────────────────────────────────────────────
val TextPrimary         = Color(0xFF1A0F1E)   // near-black with warm tone
val TextSecondary       = Color(0xFF6B5D6E)   // muted mauve/gray
val TextMuted           = Color(0xFFB8A9BB)   // very muted
val TextOnPrimary       = Color(0xFFFFFFFF)   // White text on colored buttons

// ── Borders & Dividers ───────────────────────────────────────────────────────
val Outline             = Color(0xFFE8DCE8)   // soft lavender dividers
val OutlineVariant      = Color(0xFFF0E8F0)   // subtle dividers

// ── Status Colors ────────────────────────────────────────────────────────────
val Error               = Color(0xFFB85C5C)   // muted red
val ErrorContainer      = Color(0xFFFADADD)   // error container

// ── Backgrounds & Surfaces ───────────────────────────────────────────────────
val AppBackground       = Background
val CardSurface         = SurfaceVariant

// ── Distance Badges ──────────────────────────────────────────────────────────
val BadgeNear           = Tertiary
val BadgeFar            = Secondary
val BadgeVeryFar        = TextMuted

// ── Legacy Aliases (backward compat) ─────────────────────────────────────────
val StatusConnected     = Tertiary
val StatusConnecting    = Secondary
val StatusOffline       = Color(0xFFBDBDBD)
val StatusError         = Error
val PrimaryDark         = Color(0xFFA0555E)
val PrimaryLight        = PrimaryContainer
val EmergencyRed          = Primary
val EmergencyRedDark      = PrimaryDark
val EmergencyRedSurface   = Primary
val EmergencyRedContainer = PrimaryContainer
val EmergencyRedLight     = PrimaryLight
val EmergencyGreen        = Tertiary
val EmergencyGreenDark    = Tertiary
val EmergencyGreenLight   = TertiaryContainer
val EmergencyAmber        = Secondary
val EmergencyAmberLight   = SecondaryContainer
val EmergencyAmberDark    = Secondary
val DarkBackground        = AppBackground
val DarkSurface           = Surface
val DarkSurfaceElevated   = SurfaceVariant
val DarkBorder            = Outline
val LightBackground       = AppBackground
val LightSurface          = Surface
val LightSurfaceVariant   = SurfaceVariant
val LightBorder           = Outline
val TextOnDark          = TextPrimary
val TextOnDarkDim       = TextSecondary
val TextOnDarkMuted     = TextMuted
val BadgeOffline        = StatusOffline
val MeshPrimary         = Primary
val MeshBackground      = AppBackground
val MeshSurface         = Surface
val MeshSurfaceVariant  = SurfaceVariant
val MeshSurfaceBright   = Surface
val MeshPrimaryDim      = PrimaryDark
val MeshPrimaryContainer= PrimaryContainer
val MeshOnPrimary       = TextOnPrimary
val MeshSecondary       = Secondary
val MeshSecondaryContainer= SecondaryContainer
val MeshConnected       = StatusConnected
val MeshConnecting      = StatusConnecting
val MeshHandshaking     = Secondary
val MeshDisconnected    = StatusError
val MeshOnBackground    = TextPrimary
val MeshOnBackgroundDim = TextSecondary
val MeshOnBackgroundMuted = TextMuted
val MeshOutline         = Outline
val NavBarBackground    = AppBackground
val NavItemActive       = Primary
val NavItemInactive     = TextMuted
val NavIndicator        = PrimaryContainer
val BloodGroupSelected  = Primary
val BloodGroupUnselected= SurfaceVariant
val MeshReadyGreen      = Tertiary
val ContactAvatarSalmon = Primary
val RadarRing1          = Color(0x33C4717A)
val RadarRing2          = Color(0x55C4717A)
val RadarRing3          = Color(0x88C4717A)
val BubbleSent          = Primary
val BubbleReceived      = SurfaceVariant
