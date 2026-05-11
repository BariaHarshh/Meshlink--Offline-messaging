package com.meshlink.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.meshlink.app.service.NearbyService
import com.meshlink.app.ui.navigation.MeshLinkNavHost
import com.meshlink.app.ui.navigation.MeshBottomNavBar
import com.meshlink.app.ui.navigation.Screen
import com.meshlink.app.ui.navigation.bottomNavItems
import com.meshlink.app.ui.theme.MeshLinkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var permissionsGranted by mutableStateOf(false)

    private val requiredPermissions: Array<String>
        get() = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            permissionsGranted = results.values.all { it }
            if (permissionsGranted) NearbyService.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        permissionsGranted = requiredPermissions.all { perm ->
            checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
        }
        if (permissionsGranted) NearbyService.start(this)

        setContent {
            MeshLinkTheme {
                if (permissionsGranted) {
                    val navController = rememberNavController()
                    val navBackStack  by navController.currentBackStackEntryAsState()
                    val currentRoute  = navBackStack?.destination?.route

                    // Bottom bar only on root tabs — not inside Chat or Medical Profile
                    val bottomNavRoutes = bottomNavItems.map { it.route }.toSet()
                    val showBottomBar   = currentRoute in bottomNavRoutes

                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = {
                            AnimatedVisibility(
                                visible = showBottomBar,
                                enter   = slideInVertically { it },
                                exit    = slideOutVertically { it }
                            ) {
                                MeshBottomNavBar(
                                    currentRoute = currentRoute,
                                    onNavigate   = { route ->
                                        navController.navigate(route) {
                                            // Pop up to start destination to avoid huge back stack
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState    = true
                                        }
                                    }
                                )
                            }
                        }
                    ) { innerPadding ->
                        MeshLinkNavHost(
                            navController = navController,
                            modifier      = Modifier.padding(innerPadding)
                        )
                    }
                } else {
                    // ── Permission gate screen ────────────────────────────────
                    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(horizontal = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text  = "📡",
                                style = MaterialTheme.typography.displaySmall
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text      = "MeshLink needs permissions to discover and connect with nearby devices.",
                                style     = MaterialTheme.typography.bodyLarge,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { permissionLauncher.launch(requiredPermissions) },
                                shape   = RoundedCornerShape(14.dp),
                                colors  = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    "Allow Permissions",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
