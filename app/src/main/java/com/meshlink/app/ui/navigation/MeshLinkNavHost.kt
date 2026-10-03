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
import com.meshlink.app.ui.onboarding.OnboardingScreen
import com.meshlink.app.ui.peer.PeerDetailsScreen
import com.meshlink.app.ui.profile.ProfileScreen
import com.meshlink.app.ui.security.SecurityPrivacyScreen
import com.meshlink.app.ui.settings.SettingsScreen
import com.meshlink.app.ui.splash.SplashScreen

@Composable
fun MeshLinkNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Splash.route,
        modifier         = modifier
    ) {
        // ── 1. SPLASH SCREEN (Screen 1) ────────────────────────────────────────
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateNext = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // ── 2. ONBOARDING SCREEN (Screen 2) ────────────────────────────────────
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onGetStarted = {
                    navController.navigate(Screen.HomeTab.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // ── 3. HOME TAB (Screen 3) ─────────────────────────────────────────────
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
                }
            )
        }

        // ── 4. CHATS TAB (Screen 4) ────────────────────────────────────────────
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

        // ── 5. CHAT SCREEN (Screen 5) ──────────────────────────────────────────
        composable(
            route     = Screen.Chat.route,
            arguments = listOf(
                navArgument("deviceId")   { type = NavType.StringType },
                navArgument("deviceName") { type = NavType.StringType }
            )
        ) {
            ChatScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToPeerDetails = { deviceId, deviceName ->
                    navController.navigate(Screen.PeerDetails.createRoute(deviceId, deviceName))
                }
            )
        }

        // ── 6. MESH NETWORK TAB (Screen 6) ─────────────────────────────────────
        composable(Screen.MeshNetwork.route) {
            MeshNetworkScreen(
                onDeviceClick = { endpointId, deviceName ->
                    navController.navigate(Screen.Chat.createRoute(endpointId, deviceName))
                },
                onNavigateToPeerDetails = { deviceId, deviceName ->
                    navController.navigate(Screen.PeerDetails.createRoute(deviceId, deviceName))
                }
            )
        }

        // ── 7. PEER DETAILS SCREEN (Screen 7) ──────────────────────────────────
        composable(
            route     = Screen.PeerDetails.route,
            arguments = listOf(
                navArgument("deviceId")   { type = NavType.StringType },
                navArgument("deviceName") { type = NavType.StringType }
            )
        ) {
            PeerDetailsScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToChat = { deviceId, deviceName ->
                    navController.navigate(Screen.Chat.createRoute(deviceId, deviceName))
                }
            )
        }

        // ── 8. PROFILE TAB (Screen 8) ──────────────────────────────────────────
        composable(Screen.ProfileTab.route) {
            ProfileScreen(
                onNavigateToSecurity = {
                    navController.navigate(Screen.SecurityPrivacy.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route)
                }
            )
        }

        // ── 9. SETTINGS SCREEN (Screen 9) ──────────────────────────────────────
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToSecurity = {
                    navController.navigate(Screen.SecurityPrivacy.route)
                }
            )
        }

        // ── 10. SECURITY & PRIVACY SCREEN (Screen 10) ──────────────────────────
        composable(Screen.SecurityPrivacy.route) {
            SecurityPrivacyScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // ── Medical Profile ────────────────────────────────────────────────────
        composable(Screen.MedicalProfile.route) {
            MedicalProfileScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // ── Broadcast ──────────────────────────────────────────────────────────
        composable(Screen.Broadcast.route) {
            BroadcastScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
