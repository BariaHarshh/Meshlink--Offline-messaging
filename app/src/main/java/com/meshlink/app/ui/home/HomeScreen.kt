package com.meshlink.app.ui.home

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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlink.app.ui.components.DeliveryStatusIndicator
import com.meshlink.app.ui.components.MeshAvatar
import com.meshlink.app.ui.components.MeshLinkCard
import java.util.Calendar

@Composable
fun HomeScreen(
    onConversationClick: (deviceId: String, deviceName: String) -> Unit,
    onSeeAllChats: () -> Unit = {},
    onSeeMesh: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onBroadcastClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val peerCount     by viewModel.peerCount.collectAsStateWithLifecycle()
    val userName      by viewModel.userName.collectAsStateWithLifecycle()

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning,"
            in 12..16 -> "Good afternoon,"
            in 17..22 -> "Good evening,"
            else -> "Good night,"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Header: Greeting + User Avatar ────────────────────────────────────
        HomeHeader(
            greeting      = greeting,
            userName      = userName,
            onAvatarClick = onProfileClick
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Mesh Network Status Card (Visual reference #3) ────────────────
            item {
                MeshNetworkStatusCard(
                    peerCount = peerCount,
                    onClick   = onSeeMesh
                )
            }

            // ── Quick Broadcast Action Card ───────────────────────────────────
            item {
                BroadcastActionCard(
                    peerCount = peerCount,
                    onClick   = onBroadcastClick
                )
            }

            // ── Recent Conversations Section Header ───────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = "Recent Conversations",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onBackground
                    )
                    if (conversations.isNotEmpty()) {
                        Text(
                            text       = "See all",
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.primary,
                            modifier   = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onSeeAllChats)
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // ── Recent Conversations List ─────────────────────────────────────
            if (conversations.isEmpty()) {
                item {
                    EmptyConversationsCard(onDiscoverPeers = onSeeMesh)
                }
            } else {
                val recent = conversations.take(4)
                items(recent, key = { it.deviceId }) { conversation ->
                    RecentConversationCard(
                        conversation = conversation,
                        onClick      = { onConversationClick(conversation.deviceId, conversation.deviceName) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HomeHeader(
    greeting: String,
    userName: String,
    onAvatarClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text     = greeting,
                style    = MaterialTheme.typography.titleMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp
            )
            Text(
                text       = userName.ifEmpty { "MeshLink User" },
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color      = MaterialTheme.colorScheme.onBackground
            )
        }

        MeshAvatar(
            name     = userName.ifEmpty { "ML" },
            size     = 50.dp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onAvatarClick)
        )
    }
}

@Composable
private fun MeshNetworkStatusCard(
    peerCount: Int,
    onClick: () -> Unit
) {
    val isOnline = peerCount > 0

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOnline) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                    Text(
                        text       = if (isOnline) "Mesh Network Active" else "Mesh Network Idle",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Signal indicator icon
                Icon(
                    imageVector        = Icons.Default.SignalCellularAlt,
                    contentDescription = null,
                    tint               = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier           = Modifier.size(22.dp)
                )
            }

            // Stats info
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text       = if (isOnline) "$peerCount nearby ${if (peerCount == 1) "peer" else "peers"}" else "0 nearby peers",
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text       = if (isOnline) "Direct multi-hop routing active" else "Waiting for nearby peers…",
                    style      = MaterialTheme.typography.bodySmall,
                    color      = MaterialTheme.colorScheme.outline
                )
            }

            // Bottom chip row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint               = if (isOnline) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                        modifier           = Modifier.size(15.dp)
                    )
                    Text(
                        text       = if (isOnline) "Network healthy" else "Idle mode",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = if (isOnline) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "View Mesh",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun BroadcastActionCard(
    peerCount: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.WifiTethering,
                    contentDescription = "Broadcast",
                    tint               = Color.White,
                    modifier           = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = "Broadcast Channel",
                    style      = MaterialTheme.typography.titleSmall,
                    color      = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text       = if (peerCount > 0) "Reach all $peerCount nodes in range" else "Alert all nodes in range",
                    style      = MaterialTheme.typography.bodySmall,
                    color      = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RecentConversationCard(
    conversation: Conversation,
    onClick: () -> Unit
) {
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MeshAvatar(name = conversation.deviceName, size = 48.dp)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = conversation.deviceName,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text     = conversation.formattedTime,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = conversation.lastMessage,
                        style      = MaterialTheme.typography.bodySmall,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    DeliveryStatusIndicator(
                        status    = conversation.deliveryStatus,
                        compact   = true,
                        showLabel = false
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyConversationsCard(onDiscoverPeers: () -> Unit) {
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
            Icon(
                imageVector        = Icons.Default.CellTower,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.secondary,
                modifier           = Modifier.size(36.dp)
            )
            Text(
                text       = "No conversations yet",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text       = "Connect with a nearby device to start chatting.",
                style      = MaterialTheme.typography.bodySmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize   = 12.sp
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onDiscoverPeers)
            ) {
                Text(
                    text       = "Discover Nearby Peers",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                    modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
