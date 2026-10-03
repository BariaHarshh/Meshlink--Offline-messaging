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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.DiscoveredDevice
import com.meshlink.app.ui.theme.CardSurfaceWhite
import com.meshlink.app.ui.theme.MeshLinkRadius
import com.meshlink.app.ui.theme.MutedText
import com.meshlink.app.ui.theme.PrimaryAccent
import com.meshlink.app.ui.theme.PrimaryBackground
import com.meshlink.app.ui.theme.PrimaryText
import com.meshlink.app.ui.theme.SecondaryAccent
import com.meshlink.app.ui.theme.SecondaryText
import com.meshlink.app.ui.theme.SoftGreen
import com.meshlink.app.ui.theme.SoftLavender
import com.meshlink.app.ui.theme.WarmWhite
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MeshNetworkScreen(
    onDeviceClick: (endpointId: String, deviceName: String) -> Unit,
    onNavigateToPeerDetails: ((deviceId: String, deviceName: String) -> Unit)? = null,
    viewModel: MeshNetworkViewModel = hiltViewModel()
) {
    val devices            by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val connectionStates   by viewModel.connectionStates.collectAsStateWithLifecycle()
    val connectedPeerCount by viewModel.connectedPeerCount.collectAsStateWithLifecycle()
    val context            = LocalContext.current
    var showMenu           by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.navigateToChat.collect { (endpointId, deviceName) ->
            onDeviceClick(endpointId, deviceName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryBackground)
    ) {
        // ── Top Header matching Screen 6 ──────────────────────────────────────
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
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                color      = PrimaryText
            )

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = PrimaryText,
                        modifier = Modifier.size(24.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Restart Discovery") },
                        onClick = {
                            showMenu = false
                            Toast.makeText(context, "Rescanning for mesh nodes...", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Large Topology Radar Visualization matching Screen 6 ──────────
            item {
                MeshTopologyVisualCard(
                    devices = devices,
                    connectionStates = connectionStates,
                    onDeviceClick = { dev -> viewModel.onDeviceClick(dev) }
                )
            }

            // ── Metrics Row: Nodes | Routes | Hops ────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val nodesCount = devices.size
                    val routesCount = connectedPeerCount
                    val hopsCount = if (connectedPeerCount > 0) 1 else 0

                    MetricCard(
                        value = nodesCount.toString(),
                        label = "Nodes",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        value = routesCount.toString(),
                        label = "Routes",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        value = hopsCount.toString(),
                        label = "Hops",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Button: View Topology → matching Screen 6 ─────────────────────
            item {
                Button(
                    onClick = {
                        Toast.makeText(context, "Interactive mesh topology active (${devices.size} nodes)", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(MeshLinkRadius.Button),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "View Topology",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // ── Peers in Range Section Header ─────────────────────────────────
            item {
                Text(
                    text       = "Peers in Range",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = PrimaryText,
                    modifier   = Modifier.padding(top = 4.dp)
                )
            }

            // ── Peers List ────────────────────────────────────────────────────
            if (devices.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(MeshLinkRadius.Card),
                        color = WarmWhite,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                tint = PrimaryAccent,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "No nearby devices found",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                            Text(
                                text = "Bring other MeshLink devices within Bluetooth/Wi-Fi range to automatically form the mesh.",
                                fontSize = 13.sp,
                                color = SecondaryText,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(devices, key = { it.endpointId }) { device ->
                    val state = connectionStates[device.endpointId] ?: ConnectionState.IDLE
                    val isConnected = state == ConnectionState.CONNECTED

                    Surface(
                        shape = RoundedCornerShape(MeshLinkRadius.Card),
                        color = WarmWhite,
                        shadowElevation = 0.5.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(MeshLinkRadius.Card))
                            .clickable {
                                if (onNavigateToPeerDetails != null) {
                                    onNavigateToPeerDetails(device.endpointId, device.name)
                                } else {
                                    viewModel.onDeviceClick(device)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(SecondaryAccent.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = device.name.take(1).uppercase(),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isConnected) "Connected" else "In range · Direct link",
                                    fontSize = 12.sp,
                                    color = if (isConnected) SoftGreen else SecondaryText
                                )
                            }

                            // Action button: Connect / Chat
                            if (isConnected) {
                                IconButton(
                                    onClick = { onDeviceClick(device.endpointId, device.name) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubbleOutline,
                                        contentDescription = "Chat",
                                        tint = PrimaryAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SoftLavender.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { viewModel.onDeviceClick(device) }
                                ) {
                                    Text(
                                        text = "Connect",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PrimaryText,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MeshTopologyVisualCard(
    devices: List<DiscoveredDevice>,
    connectionStates: Map<String, ConnectionState>,
    onDeviceClick: (DiscoveredDevice) -> Unit
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue  = 0.30f,
        targetValue   = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(MeshLinkRadius.CardLarge),
        color = WarmWhite,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(270.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Concentric circles & connecting radial lines
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2
                val cy = size.height / 2
                val r1 = 50.dp.toPx()
                val r2 = 92.dp.toPx()

                // Concentric background radar rings
                drawCircle(color = SoftLavender.copy(alpha = 0.25f), radius = r2)
                drawCircle(
                    color = PrimaryAccent.copy(alpha = 0.15f * pulseAlpha),
                    radius = r2,
                    style = Stroke(width = 1.5f)
                )
                drawCircle(color = SoftLavender.copy(alpha = 0.35f), radius = r1)
                drawCircle(
                    color = PrimaryAccent.copy(alpha = 0.25f * pulseAlpha),
                    radius = r1,
                    style = Stroke(width = 2.0f)
                )

                // 4 Orthogonal radiating lines matching Screen 6
                drawLine(
                    color = PrimaryAccent.copy(alpha = 0.35f),
                    start = Offset(cx, cy - r2),
                    end   = Offset(cx, cy + r2),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = PrimaryAccent.copy(alpha = 0.35f),
                    start = Offset(cx - r2, cy),
                    end   = Offset(cx + r2, cy),
                    strokeWidth = 1.5f
                )
            }

            if (devices.isEmpty()) {
                // Pulse indicator when scanning
                Text(
                    text = "Scanning nearby mesh frequencies...",
                    fontSize = 12.sp,
                    color = SecondaryText,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                )
            } else {
                // Render discovered nodes dynamically
                val nodeColors = listOf(SoftGreen, SecondaryAccent, PrimaryAccent, SoftLavender)
                devices.take(6).forEachIndexed { index, device ->
                    val angle = (2.0 * Math.PI / devices.size.coerceAtMost(6)) * index - (Math.PI / 2.0)
                    val r = 85.0
                    val offsetX = (r * cos(angle)).dp
                    val offsetY = (r * sin(angle)).dp
                    val color = nodeColors[index % nodeColors.size]

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(
                                start = if (offsetX > 0.dp) offsetX * 2 else 0.dp,
                                end = if (offsetX < 0.dp) -offsetX * 2 else 0.dp,
                                top = if (offsetY < 0.dp) -offsetY * 2 else 0.dp,
                                bottom = if (offsetY > 0.dp) offsetY * 2 else 0.dp
                            )
                            .clickable { onDeviceClick(device) }
                    ) {
                        TopologyNode(
                            label = device.name,
                            initial = device.name.take(1).uppercase(),
                            color = color
                        )
                    }
                }
            }

            // Center: YOU
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PrimaryText)
                        .border(2.5.dp, PrimaryAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "You",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "You",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
            }
        }
    }
}

@Composable
private fun TopologyNode(
    label: String,
    initial: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.45f))
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = PrimaryText
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
        shape = RoundedCornerShape(MeshLinkRadius.Card),
        color = WarmWhite,
        shadowElevation = 0.5.dp,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = SecondaryText
            )
        }
    }
}
