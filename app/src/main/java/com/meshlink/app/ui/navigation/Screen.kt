package com.meshlink.app.ui.navigation

import java.net.URLEncoder

sealed class Screen(val route: String) {

    // ── Bottom-tab roots ───────────────────────────────────────────────────────
    /** Bottom-tab: Home dashboard */
    data object HomeTab : Screen("home_tab")

    /** Bottom-tab: Full chat list */
    data object Chats : Screen("chats")

    /** Bottom-tab: Mesh network topology */
    data object MeshNetwork : Screen("mesh_network")

    /** Bottom-tab: Profile (placeholder in Phase 6A) */
    data object ProfileTab : Screen("profile_tab")

    // ── Full-screen destinations (no bottom bar) ───────────────────────────────
    /** Screen 1: Splash screen */
    data object Splash : Screen("splash")

    /** Screen 2: Onboarding screen */
    data object Onboarding : Screen("onboarding")

    /** Screen 5: Individual chat conversation */
    data object Chat : Screen("chat/{deviceId}/{deviceName}") {
        fun createRoute(deviceId: String, deviceName: String): String {
            val encodedId   = URLEncoder.encode(deviceId,   "UTF-8")
            val encodedName = URLEncoder.encode(deviceName, "UTF-8")
            return "chat/$encodedId/$encodedName"
        }
    }

    /** Screen 7: Peer details view */
    data object PeerDetails : Screen("peer_details/{deviceId}/{deviceName}") {
        fun createRoute(deviceId: String, deviceName: String): String {
            val encodedId   = URLEncoder.encode(deviceId,   "UTF-8")
            val encodedName = URLEncoder.encode(deviceName, "UTF-8")
            return "peer_details/$encodedId/$encodedName"
        }
    }

    /** Screen 9: App settings */
    data object Settings : Screen("settings")

    /** Screen 10: Security and privacy center */
    data object SecurityPrivacy : Screen("security_privacy")

    /** Full-screen: Medical / identity profile editor */
    data object MedicalProfile : Screen("medical_profile")

    /** Full-screen: Broadcast message to all peers */
    data object Broadcast : Screen("broadcast")

    // ── Legacy aliases (kept for any remaining references) ────────────────────
    /** @deprecated use HomeTab instead */
    data object Home : Screen("home_tab")

    /** @deprecated use MeshNetwork instead */
    data object Discovery : Screen("mesh_network")

    /** @deprecated removed in Phase 6A */
    data object Sos : Screen("sos_legacy")
}
