package com.meshlink.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.meshlink.app.ui.broadcast.BroadcastScreen
import com.meshlink.app.ui.chat.ChatScreen
import com.meshlink.app.ui.chats.ChatsListScreen
import com.meshlink.app.ui.home.HomeScreen
import com.meshlink.app.ui.medical.MedicalProfileScreen
import com.meshlink.app.ui.mesh.MeshNetworkScreen
import com.meshlink.app.ui.profile.ProfileScreen

@Composable
fun MeshLinkNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController    = navController,
        startDestination = Screen.HomeTab.route,
        modifier         = modifier
    ) {
        // ── HOME tab ───────────────────────────────────────────────────────────
        composable(Screen.HomeTab.route) {
            HomeScreen(
                onConversationClick = { deviceId, deviceName ->
                    navController.navigate(Screen.Chat.createRoute(deviceId, deviceName))
                },
                onSeeAllChats = {
                    navController.navigate(Screen.Chats.route)
                },
                onSeeMesh = {
                    navController.navigate(Screen.MeshNetwork.route)
                },
                onProfileClick = {
                    navController.navigate(Screen.ProfileTab.route)
                },
                onBroadcastClick = {
                    navController.navigate(Screen.Broadcast.route)
                }
            )
        }

        // ── CHATS tab ──────────────────────────────────────────────────────────
        composable(Screen.Chats.route) {
            ChatsListScreen(
                onConversationClick = { deviceId, deviceName ->
                    navController.navigate(Screen.Chat.createRoute(deviceId, deviceName))
                },
                onDiscoverPeers = {
                    navController.navigate(Screen.MeshNetwork.route)
                }
            )
        }

        // ── MESH NETWORK tab ───────────────────────────────────────────────────
        composable(Screen.MeshNetwork.route) {
            MeshNetworkScreen(
                onDeviceClick = { endpointId, deviceName ->
                    navController.navigate(Screen.Chat.createRoute(endpointId, deviceName))
                }
            )
        }

        // ── PROFILE tab ────────────────────────────────────────────────────────
        composable(Screen.ProfileTab.route) {
            ProfileScreen(
                onNavigateToSecurity = {
                    navController.navigate(Screen.MedicalProfile.route)
                }
            )
        }

        // ── Medical / Security Profile (full-screen, no bottom bar) ───────────
        composable(Screen.MedicalProfile.route) {
            MedicalProfileScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // ── Broadcast (full-screen, no bottom bar) ─────────────────────────────
        composable(Screen.Broadcast.route) {
            BroadcastScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // ── Chat (full-screen, no bottom bar) ──────────────────────────────────
        composable(
            route     = Screen.Chat.route,
            arguments = listOf(
                navArgument("deviceId")   { type = NavType.StringType },
                navArgument("deviceName") { type = NavType.StringType }
            )
        ) {
            ChatScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
