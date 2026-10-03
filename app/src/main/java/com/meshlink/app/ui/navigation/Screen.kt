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
    /** Full-screen: Individual chat conversation */
    data object Chat : Screen("chat/{deviceId}/{deviceName}") {
        fun createRoute(deviceId: String, deviceName: String): String {
            val encodedId   = URLEncoder.encode(deviceId,   "UTF-8")
            val encodedName = URLEncoder.encode(deviceName, "UTF-8")
            return "chat/$encodedId/$encodedName"
        }
    }

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
