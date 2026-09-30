package com.meshlink.app.ui.discovery

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.DiscoveredDevice
import com.meshlink.app.ui.theme.BadgeFar
import com.meshlink.app.ui.theme.BadgeNear
import com.meshlink.app.ui.theme.BadgeVeryFar
import com.meshlink.app.ui.theme.DarkBackground
import com.meshlink.app.ui.theme.DarkSurface
import com.meshlink.app.ui.theme.DarkSurfaceElevated
import com.meshlink.app.ui.theme.EmergencyGreen
import com.meshlink.app.ui.theme.EmergencyRed
import com.meshlink.app.ui.theme.LightBackground
import com.meshlink.app.ui.theme.LightBorder
import com.meshlink.app.ui.theme.LightSurface
import com.meshlink.app.ui.theme.TextOnDark
import com.meshlink.app.ui.theme.TextOnDarkDim
import com.meshlink.app.ui.theme.TextOnDarkMuted
import com.meshlink.app.ui.theme.TextPrimary
import com.meshlink.app.ui.theme.TextSecondary

// Mocked distance helper (Nearby API doesn't expose distance)
private fun mockDistance(endpointId: String): String {
    val distances = listOf("42M", "110M", "250M", "85M", "320M", "67M")
    return distances[Math.abs(endpointId.hashCode()) % distances.size]
}

private fun distanceBadge(endpointId: String): Pair<String, Color> {
    val d = mockDistance(endpointId).removeSuffix("M").toIntOrNull() ?: 100
    return when {
        d <= 80  -> "NEAR"      to BadgeNear
        d <= 180 -> "FAR"       to BadgeFar
        else     -> "VERY FAR" to BadgeVeryFar
    }
}

private fun deviceIcon(index: Int): ImageVector = when (index % 3) {
    0    -> Icons.Default.Person
    1    -> Icons.Default.CellTower
    else -> Icons.Default.SignalCellularAlt
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun DiscoveryScreen(
    onDeviceClick: (endpointId: String, deviceName: String) -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val devices          by viewModel.devices.collectAsStateWithLifecycle()
    val connectionStates by viewModel.connectionStates.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigateToChat.collect { (endpointId, deviceName) ->
            onDeviceClick(endpointId, deviceName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // ── Header (dark) ──────────────────────────────────────────────────────
        DeviceListHeader(onSettingsClick = onSettingsClick)

        // ── Network status card ────────────────────────────────────────────────
        NetworkStatusCard(deviceCount = devices.size)

        // ── Peers in range section ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text          = "PEERS IN RANGE",
                style         = MaterialTheme.typography.labelLarge,
                color         = TextPrimary,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            ScanningBadge()
        }

        // ── Device list ────────────────────────────────────────────────────────
        if (devices.isEmpty()) {
            Box(
                modifier         = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        color       = EmergencyRed,
                        strokeWidth = 2.dp,
                        modifier    = Modifier.size(32.dp)
                    )
                    Text(
                        text  = "Scanning for mesh nodes…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextOnDarkDim
                    )
                }
            }
        } else {
            LazyColumn(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(devices.mapIndexed { i, d -> i to d }, key = { it.second.endpointId }) { (idx, device) ->
                    val state        = connectionStates[device.endpointId] ?: ConnectionState.IDLE
                    val isConnecting = state == ConnectionState.CONNECTING ||
                                       state == ConnectionState.HANDSHAKING
                    val isConnected  = state == ConnectionState.CONNECTED
                    DeviceCard(
                        device       = device,
                        icon         = deviceIcon(idx),
                        distance     = mockDistance(device.endpointId),
                        badge        = distanceBadge(device.endpointId),
                        isConnecting = isConnecting,
                        isConnected  = isConnected,
                        onClick      = { if (!isConnecting) viewModel.onDeviceClick(device) },
                        onDisconnect = { viewModel.onDisconnectClick(device.endpointId) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun DeviceListHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.WifiTethering,
                contentDescription = null,
                tint     = EmergencyRed,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text       = "MESH ACTIVE",
                style      = MaterialTheme.typography.titleMedium,
                color      = EmergencyRed,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }
        IconButton(onClick = onSettingsClick) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit profile",
                tint     = TextOnDark,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Network status card ───────────────────────────────────────────────────────

@Composable
private fun NetworkStatusCard(deviceCount: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = LightSurface
    ) {
        Row {
            // Red left accent bar
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(100.dp)
                    .background(EmergencyRed, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text          = "NETWORK STATUS",
                    style         = MaterialTheme.typography.labelMedium,
                    color         = TextSecondary,
                    letterSpacing = 1.sp
                )
                Text(
                    text       = if (deviceCount == 0) "NO PEOPLE\nFOUND"
                                 else "$deviceCount PEOPLE\nNEARBY",
                    style      = MaterialTheme.typography.displayMedium,
                    color      = TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 40.sp
                )
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(EmergencyGreen)
                    )
                    Text(
                        text       = "MESH SECURED",
                        style      = MaterialTheme.typography.labelMedium,
                        color      = EmergencyGreen,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
    }
}

// ── Scanning badge ────────────────────────────────────────────────────────────

@Composable
private fun ScanningBadge() {
    val transition = rememberInfiniteTransition(label = "scan")
    val alpha by transition.animateFloat(
        initialValue  = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = DarkSurfaceElevated
    ) {
        Text(
            text     = "SCANNING…",
            style    = MaterialTheme.typography.labelSmall,
            color    = TextOnDarkDim.copy(alpha = alpha),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            letterSpacing = 0.8.sp
        )
    }
}

// ── Device card ───────────────────────────────────────────────────────────────

@Composable
private fun DeviceCard(
    device:       DiscoveredDevice,
    icon:         ImageVector,
    distance:     String,
    badge:        Pair<String, Color>,
    isConnecting: Boolean,
    isConnected:  Boolean,
    onClick:      () -> Unit,
    onDisconnect: () -> Unit
) {
    val (badgeLabel, badgeColor) = badge

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = LightSurface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Device icon in gray circle
                Box(
                    modifier         = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE2E8F0)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = icon,
                        contentDescription = null,
                        tint               = Color(0xFF64748B),
                        modifier           = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = device.name,
                        style      = MaterialTheme.typography.titleSmall,
                        color      = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text  = distance + " AWAY",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                // Distance badge (top-right)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.12f),
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = badgeColor,
                        shape = RoundedCornerShape(6.dp)
                    )
                ) {
                    Text(
                        text     = badgeLabel,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = badgeColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // CONNECT / DISCONNECT button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            isConnected  -> EmergencyGreen
                            isConnecting -> Color(0xFF374151)
                            else         -> TextPrimary
                        }
                    )
                    .clickable(enabled = !isConnecting) {
                        if (isConnected) onDisconnect() else onClick()
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isConnecting) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color       = Color.White,
                            strokeWidth = 2.dp,
                            modifier    = Modifier.size(14.dp)
                        )
                        Text(
                            text       = "CONNECTING…",
                            style      = MaterialTheme.typography.labelLarge,
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    Text(
                        text       = if (isConnected) "CONNECTED ✓" else "CONNECT",
                        style      = MaterialTheme.typography.labelLarge,
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }
    }
}
