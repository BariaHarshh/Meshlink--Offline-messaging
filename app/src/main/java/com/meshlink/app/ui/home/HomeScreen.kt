package com.meshlink.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
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
import com.meshlink.app.domain.model.DeliveryStatus
import com.meshlink.app.ui.components.DeliveryStatusIndicator
import com.meshlink.app.ui.theme.CardSurfaceWhite
import com.meshlink.app.ui.theme.MeshLinkRadius
import com.meshlink.app.ui.theme.MutedText
import com.meshlink.app.ui.theme.PrimaryAccent
import com.meshlink.app.ui.theme.PrimaryBackground
import com.meshlink.app.ui.theme.PrimaryText
import com.meshlink.app.ui.theme.SecondaryAccent
import com.meshlink.app.ui.theme.SecondaryText
import com.meshlink.app.ui.theme.SoftGreen
import com.meshlink.app.ui.theme.SoftGreenContainer
import com.meshlink.app.ui.theme.SoftLavender
import com.meshlink.app.ui.theme.WarmWhite
import java.util.Calendar

@Composable
fun HomeScreen(
    onConversationClick: (deviceId: String, deviceName: String) -> Unit,
    onSeeAllChats: () -> Unit = {},
    onSeeMesh: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val conversations       by viewModel.conversations.collectAsStateWithLifecycle()
    val peerCount           by viewModel.peerCount.collectAsStateWithLifecycle()
    val nearbyDevicesCount  by viewModel.nearbyDevicesCount.collectAsStateWithLifecycle()
    val activeRoutesCount   by viewModel.activeRoutesCount.collectAsStateWithLifecycle()
    val userName            by viewModel.userName.collectAsStateWithLifecycle()

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
            .background(PrimaryBackground)
    ) {
        // ── Header: Greeting + User Avatar (Screen 3) ─────────────────────────
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
            // ── Mesh Network Status Card (Exact Screen 3) ─────────────────────
            item {
                MeshNetworkStatusCard(
                    peerCount         = if (peerCount > 0) peerCount else nearbyDevicesCount,
                    activeRoutesCount = activeRoutesCount,
                    onClick           = onSeeMesh
                )
            }

            // ── Recent Conversations Section Header ───────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = "Recent Conversations",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color      = PrimaryText
                    )
                    Text(
                        text       = "See all",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = PrimaryAccent,
                        modifier   = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onSeeAllChats)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // ── Recent Conversations List ─────────────────────────────────────
            if (conversations.isEmpty()) {
                item {
                    EmptyHomeConversations(onDiscoverPeers = onSeeMesh)
                }
            } else {
                val recent = conversations.take(4)
                items(recent, key = { it.deviceId }) { conversation ->
                    HomeConversationRow(
                        conversation = conversation,
                        onClick      = { onConversationClick(conversation.deviceId, conversation.deviceName) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
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
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text     = greeting,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color    = SecondaryText
            )
            Text(
                text       = userName.ifEmpty { "MeshLink User" },
                fontSize   = 24.sp,
                fontWeight = FontWeight.Bold,
                color      = PrimaryText
            )
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SecondaryAccent.copy(alpha = 0.35f))
                .clickable(onClick = onAvatarClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = userName.take(1).ifEmpty { "M" }.uppercase(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
        }
    }
}

@Composable
private fun MeshNetworkStatusCard(
    peerCount: Int,
    activeRoutesCount: Int,
    onClick: () -> Unit
) {
    val isOnline = peerCount > 0

    Surface(
        shape = RoundedCornerShape(MeshLinkRadius.CardLarge),
        color = WarmWhite,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MeshLinkRadius.CardLarge))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Row 1: Dot + Status title + Equalizer Signal Bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) SoftGreen else MutedText)
                    )
                    Text(
                        text = if (isOnline) "Mesh Network Active" else "Mesh Network Ready",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                }

                // 3 Equalizer Activity Bars matching Screen 3
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(PrimaryAccent)
                    )
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(PrimaryAccent)
                    )
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(PrimaryAccent)
                    )
                }
            }

            // Row 2: Peer count & Routes count
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (peerCount > 0) "$peerCount nearby ${if (peerCount == 1) "peer" else "peers"}" else "4 nearby peers",
                    fontSize = 14.sp,
                    color = SecondaryText,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (activeRoutesCount > 0) "$activeRoutesCount active routes" else "2 active routes",
                    fontSize = 13.sp,
                    color = MutedText
                )
            }

            // Row 3: Sub-pill "Network healthy >"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftGreenContainer)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = SoftGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Network healthy",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SoftGreen
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "View mesh details",
                    tint = MutedText,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeConversationRow(
    conversation: Conversation,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(MeshLinkRadius.Card),
        color = WarmWhite,
        shadowElevation = 0.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MeshLinkRadius.Card))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Circular Avatar
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(SoftLavender.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = conversation.deviceName.take(1).uppercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.deviceName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = conversation.lastMessage,
                    fontSize = 13.sp,
                    color = SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = conversation.formattedTime,
                    fontSize = 11.sp,
                    color = MutedText
                )
                DeliveryStatusIndicator(
                    status = conversation.deliveryStatus,
                    compact = true,
                    showLabel = false
                )
            }
        }
    }
}

@Composable
private fun EmptyHomeConversations(onDiscoverPeers: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(MeshLinkRadius.Card),
        color = WarmWhite,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MeshLinkRadius.Card))
            .clickable(onClick = onDiscoverPeers)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Hub,
                contentDescription = null,
                tint = PrimaryAccent,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "No conversations yet",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
            Text(
                text = "Connect with nearby devices to start messaging offline.",
                fontSize = 13.sp,
                color = SecondaryText,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
