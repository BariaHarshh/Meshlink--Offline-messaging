package com.meshlink.app.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════════
//  MeshLink Reference-Accurate Design System Palette
// ═══════════════════════════════════════════════════════════════════════════════

// ── Core Palette (Exact reference values) ────────────────────────────────────
val PrimaryBackground   = Color(0xFFEBE1EB)   // Main canvas background
val PrimaryAccent       = Color(0xFFC4717A)   // Dusty rose / primary accent
val SecondaryAccent     = Color(0xFFE6ACDC)   // Soft pink accent
val SoftLavender        = Color(0xFFDCC7EA)   // Soft lavender
val WarmWhite           = Color(0xFFFFF8FA)   // Warm white card/bubble surface
val CardSurfaceWhite    = Color(0xFFFFFFFF)   // Pure white for highest contrast cards

// ── Text Palette ─────────────────────────────────────────────────────────────
val PrimaryText         = Color(0xFF070403)   // Deep black text
val SecondaryText       = Color(0xFF6F6570)   // Medium mauve text
val MutedText           = Color(0xFFAFA5B3)   // Muted gray-lavender text
val TextOnPrimary       = Color(0xFFFFFFFF)   // White on primary accent

// ── Status Palette ───────────────────────────────────────────────────────────
val SoftGreen           = Color(0xFF43A047)   // Success / connected
val SoftGreenContainer  = Color(0xFFE8F5E9)   // Green badge container
val SoftAmber           = Color(0xFFE5A038)   // Warning / handshaking
val SoftAmberContainer  = Color(0xFFFFF8E1)   // Amber badge container
val SoftRed             = Color(0xFFD3455B)   // Error / disconnected
val SoftRedContainer    = Color(0xFFFFEBEE)   // Error badge container

// ── Semantic Aliases & Backward Compatibility ────────────────────────────────
val Background          = PrimaryBackground
val Surface             = WarmWhite
val SurfaceVariant      = Color(0xFFF7EFF7)
val Primary             = PrimaryAccent
val PrimaryContainer    = Color(0xFFF7DDE2)
val Secondary           = SecondaryAccent
val SecondaryContainer  = Color(0xFFF4E1F2)
val Tertiary            = SoftGreen
val TertiaryContainer   = SoftGreenContainer
val Outline             = Color(0xFFE2D6E3)
val OutlineVariant      = Color(0xFFECE1EC)
val Error               = SoftRed
val ErrorContainer      = SoftRedContainer

val TextPrimary         = PrimaryText
val TextSecondary       = SecondaryText
val TextMuted           = MutedText

val AppBackground       = PrimaryBackground
val CardSurface         = WarmWhite

val StatusConnected     = SoftGreen
val StatusConnecting    = SoftAmber
val StatusOffline       = MutedText
val StatusError         = SoftRed

val BubbleSent          = PrimaryAccent
val BubbleReceived      = WarmWhite

// Legacy aliases
val PrimaryDark         = Color(0xFFA2535C)
val PrimaryLight        = PrimaryContainer
val MeshPrimary         = PrimaryAccent
val MeshBackground      = PrimaryBackground
val MeshSurface         = WarmWhite
val MeshSurfaceVariant  = SurfaceVariant
val MeshSurfaceBright   = CardSurfaceWhite
val MeshPrimaryDim      = PrimaryDark
val MeshPrimaryContainer= PrimaryContainer
val MeshOnPrimary       = TextOnPrimary
val MeshSecondary       = SecondaryAccent
val MeshSecondaryContainer= SecondaryContainer
val MeshConnected       = SoftGreen
val MeshConnecting      = SoftAmber
val MeshHandshaking     = SoftAmber
val MeshDisconnected    = SoftRed
val MeshOnBackground    = PrimaryText
val MeshOnBackgroundDim = SecondaryText
val MeshOnBackgroundMuted = MutedText
val MeshOutline         = Outline
val NavBarBackground    = PrimaryBackground
val NavItemActive       = PrimaryAccent
val NavItemInactive     = MutedText
val NavIndicator        = PrimaryContainer
val MeshReadyGreen      = SoftGreen
val RadarRing1          = Color(0x22C4717A)
val RadarRing2          = Color(0x33C4717A)
val RadarRing3          = Color(0x44C4717A)

// Legacy screen compatibility aliases
val EmergencyGreen        = SoftGreen
val EmergencyRed          = SoftRed
val EmergencyRedContainer = SoftRedContainer
val EmergencyRedSurface   = SoftRedContainer
val LightBackground       = PrimaryBackground
val LightBorder           = Outline
val LightSurface          = WarmWhite
val LightSurfaceVariant   = SurfaceVariant
val DarkBackground        = Color(0xFF1E1A20)
val DarkSurface           = Color(0xFF2A242D)
val DarkSurfaceElevated   = Color(0xFF38303C)
val TextOnDark            = Color(0xFFFFF8FA)
val TextOnDarkDim         = Color(0xFFDCC7EA)
val TextOnDarkMuted       = Color(0xFFAFA5B3)
val BadgeNear             = SoftGreen
val BadgeFar              = SoftAmber
val BadgeVeryFar          = SoftRed
val BloodGroupSelected    = PrimaryAccent
val BloodGroupUnselected  = WarmWhite
val ContactAvatarSalmon   = SecondaryAccent

