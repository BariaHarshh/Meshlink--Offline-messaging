package com.meshlink.app.ui.security

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun SecurityPrivacyScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeDialogTitle by remember { mutableStateOf<String?>(null) }
    var activeDialogContent by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PrimaryBackground)
            .statusBarsPadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PrimaryText
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Security & Privacy",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ── Hero Security Card matching Screen 10 ─────────────────────────
            Surface(
                shape = RoundedCornerShape(MeshLinkRadius.CardLarge),
                color = WarmWhite,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(PrimaryAccent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = PrimaryAccent,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "End-to-end encrypted",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your messages, files, and identity are protected with modern cryptography.",
                            fontSize = 13.sp,
                            color = SecondaryText,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // ── Security & Privacy Rows ───────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(MeshLinkRadius.CardLarge),
                color = WarmWhite,
                shadowElevation = 0.5.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    SecurityActionRow(
                        icon = Icons.Default.Shield,
                        title = "Identity verification",
                        subtitle = "Manage trusted peers",
                        onClick = {
                            activeDialogTitle = "Identity Verification"
                            activeDialogContent = "Each peer identity is backed by an EC P-256 public key. Cryptographic fingerprints verify peer identities to protect against impersonation."
                        }
                    )
                    SecurityDivider()
                    SecurityActionRow(
                        icon = Icons.Default.Devices,
                        title = "Known devices",
                        subtitle = "View and manage devices",
                        onClick = {
                            activeDialogTitle = "Known Devices"
                            activeDialogContent = "Nearby devices discovered over Bluetooth LE & Wi-Fi are cataloged in your encrypted local database."
                        }
                    )
                    SecurityDivider()
                    SecurityActionRow(
                        icon = Icons.Default.Archive,
                        title = "Backup protection",
                        subtitle = "Encrypted backup settings",
                        onClick = {
                            activeDialogTitle = "Backup Protection"
                            activeDialogContent = "Database backups are encrypted at rest with 256-bit AES-GCM via Android Keystore."
                        }
                    )
                    SecurityDivider()
                    SecurityActionRow(
                        icon = Icons.Default.HourglassBottom,
                        title = "Data retention",
                        subtitle = "Manage local data",
                        onClick = {
                            activeDialogTitle = "Data Retention"
                            activeDialogContent = "Messages are stored locally on your device. Expired undelivered messages are cleaned up automatically via WorkManager."
                        }
                    )
                    SecurityDivider()
                    SecurityActionRow(
                        icon = Icons.Default.Visibility,
                        title = "Privacy settings",
                        subtitle = "Control your visibility",
                        onClick = {
                            activeDialogTitle = "Privacy Settings"
                            activeDialogContent = "MeshLink broadcasts only a non-identifying beacon token (meshlink.node). Your real name is exchanged only through an encrypted ECDH tunnel."
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Informational dialog
    activeDialogTitle?.let { title ->
        AlertDialog(
            onDismissRequest = {
                activeDialogTitle = null
                activeDialogContent = null
            },
            title = {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
            },
            text = {
                Text(
                    text = activeDialogContent ?: "",
                    fontSize = 14.sp,
                    color = SecondaryText,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        activeDialogTitle = null
                        activeDialogContent = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("Understood", color = Color.White)
                }
            },
            containerColor = WarmWhite,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun SecurityDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(Color(0xFFEFE6F0))
    )
}

@Composable
private fun SecurityActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PrimaryText,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryText
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = SecondaryText
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MutedText,
            modifier = Modifier.size(12.dp)
        )
    }
}
