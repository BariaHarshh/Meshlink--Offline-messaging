package com.meshlink.app.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════════
//  Figma Design System — MeshLink
// ═══════════════════════════════════════════════════════════════════════════════

// ── Core Palette ─────────────────────────────────────────────────────────────
val Primary            = Color(0xFFD32F2F)   // Red — critical actions, alerts, buttons
val PrimaryDark        = Color(0xFFB71C1C)   // Pressed / darker red
val PrimaryLight       = Color(0xFFFDECEC)   // Light red tint for containers
val PrimaryContainer   = Color(0xFFFFCDD2)   // Red container (chips, badges)

val Secondary          = Color(0xFFFF6D00)   // Orange — highlights, secondary actions
val SecondaryDark      = Color(0xFFE65100)   // Pressed orange
val SecondaryLight     = Color(0xFFFFF3E0)   // Light orange tint
val SecondaryContainer = Color(0xFFFFE0B2)   // Orange container

val Tertiary           = Color(0xFF2E7D32)   // Green — success, connected, safe
val TertiaryDark       = Color(0xFF1B5E20)   // Pressed green
val TertiaryLight      = Color(0xFFE8F5E9)   // Light green tint
val TertiaryContainer  = Color(0xFFC8E6C9)   // Green container

val Neutral            = Color(0xFF1A1C1E)   // Near-black — primary text, dark elements

// ── Backgrounds & Surfaces ───────────────────────────────────────────────────
val AppBackground      = Color(0xFFFFFFFF)   // Main app background — WHITE
val CardSurface        = Color(0xFFF8F9FA)   // Slightly off-white for cards
val SurfaceVariant     = Color(0xFFF0F0F0)   // Input fields, secondary surfaces
val SurfaceBright      = Color(0xFFFFFFFF)   // Elevated white surface

// ── Text Colors ──────────────────────────────────────────────────────────────
val TextPrimary        = Color(0xFF1A1C1E)   // Primary text (Neutral)
val TextSecondary      = Color(0xFF5F6368)   // Secondary / body text
val TextMuted          = Color(0xFF9AA0A6)   // Placeholder, disabled text
val TextOnPrimary      = Color(0xFFFFFFFF)   // White text on colored buttons

// ── Borders & Dividers ───────────────────────────────────────────────────────
val Outline            = Color(0xFFE0E0E0)   // Dividers, card borders
val OutlineVariant     = Color(0xFFEEEEEE)   // Subtle dividers

// ── Status Colors ────────────────────────────────────────────────────────────
val StatusConnected    = Tertiary             // Green — connected
val StatusConnecting   = Secondary            // Orange — connecting
val StatusOffline      = Color(0xFFBDBDBD)    // Gray — offline
val StatusError        = Primary              // Red — error / disconnected

// ── Distance Badges ──────────────────────────────────────────────────────────
val BadgeNear          = Tertiary             // Green
val BadgeFar           = Secondary            // Orange
val BadgeVeryFar       = Color(0xFF9E9E9E)   // Gray

// ── Chat Bubbles ─────────────────────────────────────────────────────────────
val BubbleSent         = Primary              // Red sent bubble
val BubbleReceived     = Color(0xFFF0F0F0)   // Light gray received bubble

// ── Legacy Aliases (backward compat — maps old names to Figma system) ────────
val EmergencyRed          = Primary
val EmergencyRedDark      = PrimaryDark
val EmergencyRedSurface   = Color(0xFFD32F2F)
val EmergencyRedContainer = PrimaryLight
val EmergencyRedLight     = PrimaryLight
val EmergencyGreen        = Tertiary
val EmergencyGreenDark    = TertiaryDark
val EmergencyGreenLight   = TertiaryLight
val EmergencyAmber        = Secondary
val EmergencyAmberLight   = SecondaryLight
val EmergencyAmberDark    = SecondaryDark
val DarkBackground        = AppBackground
val DarkSurface           = CardSurface
val DarkSurfaceElevated   = SurfaceVariant
val DarkBorder            = Outline
val LightBackground       = AppBackground
val LightSurface          = Color(0xFFFFFFFF)
val LightSurfaceVariant   = SurfaceVariant
val LightBorder           = Outline
val TextOnDark            = TextPrimary
val TextOnDarkDim         = TextSecondary
val TextOnDarkMuted       = TextMuted
val BadgeOffline          = StatusOffline
val MeshPrimary           = Primary
val MeshBackground        = AppBackground
val MeshSurface           = CardSurface
val MeshSurfaceVariant    = SurfaceVariant
val MeshSurfaceBright     = SurfaceBright
val MeshPrimaryDim        = PrimaryDark
val MeshPrimaryContainer  = PrimaryContainer
val MeshOnPrimary         = TextOnPrimary
val MeshSecondary         = Secondary
val MeshSecondaryContainer= SecondaryContainer
val MeshConnected         = StatusConnected
val MeshConnecting        = StatusConnecting
val MeshHandshaking       = Color(0xFF7C4DFF)
val MeshDisconnected      = StatusError
val MeshOnBackground      = TextPrimary
val MeshOnBackgroundDim   = TextSecondary
val MeshOnBackgroundMuted = TextMuted
val MeshOutline           = Outline
val NavBarBackground      = AppBackground
val NavItemActive         = Primary
val NavItemInactive       = TextMuted
val NavIndicator          = PrimaryLight
val BloodGroupSelected    = Primary
val BloodGroupUnselected  = SurfaceVariant
val MeshReadyGreen        = Tertiary
val ContactAvatarSalmon   = Color(0xFFEF5350)
val RadarRing1            = Color(0x33D32F2F)
val RadarRing2            = Color(0x55D32F2F)
val RadarRing3            = Color(0x88D32F2F)
