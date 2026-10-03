package com.meshlink.app.ui.mesh

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.DiscoveredDevice
import com.meshlink.app.ui.components.MeshAvatar
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun MeshNetworkScreen(
    onDeviceClick: (endpointId: String, deviceName: String) -> Unit,
    viewModel: MeshNetworkViewModel = hiltViewModel()
) {
    val devices            by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val connectionStates   by viewModel.connectionStates.collectAsStateWithLifecycle()
    val connectedPeerCount by viewModel.connectedPeerCount.collectAsStateWithLifecycle()
    val context            = LocalContext.current
    var showMoreMenu       by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.navigateToChat.collect { (endpointId, deviceName) ->
            onDeviceClick(endpointId, deviceName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Top Header ────────────────────────────────────────────────────────
        MeshNetworkHeader(
            onMoreClick = { showMoreMenu = true }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Visual Topology Card (Reference #6) ───────────────────────────
            item {
                MeshTopologyCard(
                    devices          = devices,
                    connectionStates = connectionStates,
                    onDeviceClick    = { dev -> viewModel.onDeviceClick(dev) }
                )
            }

            // ── Metrics Row (Nodes, Routes, State) ────────────────────────────
            item {
                NetworkMetricsRow(
                    totalNodes     = devices.size,
                    connectedNodes = connectedPeerCount
                )
            }

            // ── Primary Action Button (View Topology / Fast Scan) ─────────────
            item {
                Button(
                    onClick = {
                        Toast.makeText(context, "Mesh network topology active", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Hub,
                            contentDescription = null,
                            tint               = Color.White,
                            modifier           = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text       = "View Topology",
                            style      = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint               = Color.White,
                            modifier           = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // ── Peers in Range Section Header ─────────────────────────────────
            item {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = "Peers in Range",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onBackground
                    )
                    ScanningStatusBadge()
                }
            }

            // ── Peers List ────────────────────────────────────────────────────
            if (devices.isEmpty()) {
                item {
                    EmptyPeersCard()
                }
            } else {
                items(devices, key = { it.endpointId }) { device ->
                    val state = connectionStates[device.endpointId] ?: ConnectionState.IDLE
                    PeerDeviceCard(
                        device       = device,
                        state        = state,
                        onClick      = { viewModel.onDeviceClick(device) },
                        onDisconnect = { viewModel.onDisconnectClick(device.endpointId) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun MeshNetworkHeader(onMoreClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text       = "Mesh Network",
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color      = MaterialTheme.colorScheme.onBackground
        )

        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector        = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun MeshTopologyCard(
    devices: List<DiscoveredDevice>,
    connectionStates: Map<String, ConnectionState>,
    onDeviceClick: (DiscoveredDevice) -> Unit
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue  = 0.25f,
        targetValue   = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
    ) {
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val primaryColor  = MaterialTheme.colorScheme.primary
            val tertiaryColor = MaterialTheme.colorScheme.tertiary
            val outlineColor  = MaterialTheme.colorScheme.outline

            // Background concentric rings & connection lines
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.36f

                // Inner radar circle
                drawCircle(
                    color = primaryColor.copy(alpha = 0.05f * pulseAlpha),
                    radius = radius * 0.6f,
                    center = center
                )

                // Middle orbit ring
                drawCircle(
                    color  = outlineColor,
                    radius = radius,
                    center = center,
                    style  = Stroke(width = 1.dp.toPx())
                )

                // Outer ambient ring
                drawCircle(
                    color  = primaryColor.copy(alpha = 0.08f * pulseAlpha),
                    radius = radius * 1.25f,
                    center = center
                )

                // Connection lines
                val nodeCount = devices.size
                if (nodeCount > 0) {
                    val angleStep = (2 * Math.PI) / nodeCount
                    devices.forEachIndexed { index, dev ->
                        val angle = index * angleStep - (Math.PI / 2)
                        val x = center.x + (radius * cos(angle)).toFloat()
                        val y = center.y + (radius * sin(angle)).toFloat()

                        val isConnected = connectionStates[dev.endpointId] == ConnectionState.CONNECTED
                        drawLine(
                            color       = if (isConnected) tertiaryColor else outlineColor,
                            start       = center,
                            end         = Offset(x, y),
                            strokeWidth = if (isConnected) 2.5.dp.toPx() else 1.dp.toPx()
                        )
                    }
                }
            }

            // Center Node: "You"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(3.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Default.Person,
                        contentDescription = "You",
                        tint               = Color.White,
                        modifier           = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text       = "You",
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                    fontSize   = 11.sp
                )
            }

            // Orbiting Peer Nodes
            val nodeCount = devices.size
            if (nodeCount > 0) {
                val angleStep = (2 * Math.PI) / nodeCount
                val orbitRadiusDp = 96.dp

                devices.forEachIndexed { index, dev ->
                    val angle = index * angleStep - (Math.PI / 2)
                    val offsetX = (orbitRadiusDp.value * cos(angle)).roundToInt().dp
                    val offsetY = (orbitRadiusDp.value * sin(angle)).roundToInt().dp
                    val isConnected = connectionStates[dev.endpointId] == ConnectionState.CONNECTED

                    Box(
                        modifier = Modifier
                            .offset(x = offsetX, y = offsetY)
                            .clip(CircleShape)
                            .clickable { onDeviceClick(dev) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box {
                                MeshAvatar(name = dev.name, size = 38.dp)
                                if (isConnected) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.tertiary)
                                            .border(1.5.dp, Color.White, CircleShape)
                                            .align(Alignment.BottomEnd)
                                    )
                                }
                            }
                            Text(
                                text      = dev.name.take(6),
                                style     = MaterialTheme.typography.labelSmall,
                                fontSize  = 9.sp,
                                maxLines  = 1,
                                color     = MaterialTheme.colorScheme.onSurface,
                                modifier  = Modifier.widthIn(max = 52.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                Text(
                    text     = "Listening for nearby nodes…",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun NetworkMetricsRow(
    totalNodes: Int,
    connectedNodes: Int
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricCard(
            value    = "$totalNodes",
            label    = "Nodes",
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            value    = "$connectedNodes",
            label    = "Routes",
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            value    = if (connectedNodes > 0) "1 Hop" else "0 Hops",
            label    = "Hops",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape    = RoundedCornerShape(18.dp),
        color    = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier            = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text       = value,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text     = label,
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ScanningStatusBadge() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.Sensors,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(13.dp)
            )
            Text(
                text          = "SCANNING",
                style         = MaterialTheme.typography.labelSmall,
                color         = MaterialTheme.colorScheme.primary,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                fontSize      = 10.sp
            )
        }
    }
}

@Composable
private fun PeerDeviceCard(
    device: DiscoveredDevice,
    state: ConnectionState,
    onClick: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isConnected  = state == ConnectionState.CONNECTED
    val isConnecting = state == ConnectionState.CONNECTING || state == ConnectionState.HANDSHAKING

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier              = Modifier.padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            MeshAvatar(name = device.name, size = 48.dp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = device.name,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isConnected  -> MaterialTheme.colorScheme.tertiary
                                    isConnecting -> MaterialTheme.colorScheme.secondary
                                    else         -> MaterialTheme.colorScheme.outline
                                }
                            )
                    )
                    Text(
                        text = when {
                            isConnected  -> "Connected"
                            isConnecting -> "Connecting…"
                            else         -> "In range"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isConnected  -> MaterialTheme.colorScheme.tertiary
                            isConnecting -> MaterialTheme.colorScheme.secondary
                            else         -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Action button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    isConnected  -> MaterialTheme.colorScheme.tertiaryContainer
                    isConnecting -> MaterialTheme.colorScheme.secondaryContainer
                    else         -> MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !isConnecting) {
                        if (isConnected) onDisconnect() else onClick()
                    }
            ) {
                Text(
                    text = when {
                        isConnected  -> "Disconnect"
                        isConnecting -> "..."
                        else         -> "Connect"
                    },
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = when {
                        isConnected  -> MaterialTheme.colorScheme.tertiary
                        isConnecting -> MaterialTheme.colorScheme.secondary
                        else         -> MaterialTheme.colorScheme.primary
                    },
                    modifier   = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyPeersCard() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                color       = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.5.dp,
                modifier    = Modifier.size(28.dp)
            )
            Text(
                text       = "Scanning for mesh nodes…",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text       = "Make sure nearby devices have MeshLink open with Bluetooth and Wi-Fi enabled.",
                style      = MaterialTheme.typography.bodySmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize   = 12.sp,
                textAlign  = TextAlign.Center
            )
        }
    }
}
