package com.meshlink.app.ui.peer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.VerificationStatus
import com.meshlink.app.ui.components.MeshAvatar
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

@Composable
fun PeerDetailsScreen(
    onBackClick: () -> Unit,
    onNavigateToChat: (deviceId: String, deviceName: String) -> Unit,
    viewModel: PeerDetailsViewModel = hiltViewModel()
) {
    val peerDevice      by viewModel.peerDevice.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val context         = LocalContext.current
    var showMenu        by remember { mutableStateOf(false) }

    val isConnected = connectionState == ConnectionState.CONNECTED
    val formattedKey = viewModel.formattedPublicKey(peerDevice)
    val isVerified = peerDevice?.verificationStatus == VerificationStatus.VERIFIED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryBackground)
            .statusBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PrimaryText
                )
            }

            Text(
                text = viewModel.deviceName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = PrimaryText
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Verify Identity Key") },
                        onClick = {
                            showMenu = false
                            peerDevice?.let { viewModel.verifyPeer(it) }
                            Toast.makeText(context, "Identity verified", Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Disconnect") },
                        onClick = {
                            showMenu = false
                            viewModel.disconnect()
                            Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Center Profile Avatar and Name matching Screen 7
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(SecondaryAccent.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = viewModel.deviceName.take(1).uppercase(),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = viewModel.deviceName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) SoftGreen else MutedText)
                )
                Text(
                    text = if (isConnected) "Connected" else "Available",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isConnected) SoftGreen else SecondaryText
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Quick Action Circles: Chat, Info, Block/Disconnect
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PeerActionButton(
                icon = Icons.Default.ChatBubbleOutline,
                label = "Chat",
                onClick = { onNavigateToChat(viewModel.deviceId, viewModel.deviceName) }
            )
            PeerActionButton(
                icon = Icons.Default.Info,
                label = "Info",
                onClick = {
                    peerDevice?.let { viewModel.verifyPeer(it) }
                    Toast.makeText(context, "Peer trust status verified", Toast.LENGTH_SHORT).show()
                }
            )
            PeerActionButton(
                icon = Icons.Default.Block,
                label = "Block",
                onClick = {
                    viewModel.disconnect()
                    Toast.makeText(context, "Peer connection removed", Toast.LENGTH_SHORT).show()
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Connection Details Card matching Screen 7
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(MeshLinkRadius.CardLarge),
            color = WarmWhite,
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Connection Details",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )

                // Row: Connection Type
                ConnectionDetailRow(
                    label = "Connection type",
                    value = "Nearby (BLE/Wi-Fi)"
                )

                // Row: Last seen
                ConnectionDetailRow(
                    label = "Last seen",
                    value = "2 minutes ago"
                )

                // Row: Public key
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Public key",
                            fontSize = 13.sp,
                            color = SecondaryText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formattedKey,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryText
                        )
                    }

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Public Key", formattedKey))
                            Toast.makeText(context, "Key copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy key",
                            tint = SecondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Row: Trust status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Trust status",
                            fontSize = 13.sp,
                            color = SecondaryText
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isVerified) SoftGreenContainer else SoftLavender.copy(alpha = 0.4f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isVerified) "Verified" else "Unverified",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isVerified) SoftGreen else PrimaryText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(WarmWhite)
                .border(1.dp, Color(0xFFE2D6E3), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = PrimaryText,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = SecondaryText
        )
    }
}

@Composable
private fun ConnectionDetailRow(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            fontSize = 13.sp,
            color = SecondaryText
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = PrimaryText
        )
    }
}
