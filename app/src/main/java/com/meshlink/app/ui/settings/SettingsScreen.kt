package com.meshlink.app.ui.settings

import android.content.Context
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlink.app.ui.theme.MeshLinkRadius
import com.meshlink.app.ui.theme.MutedText
import com.meshlink.app.ui.theme.PrimaryAccent
import com.meshlink.app.ui.theme.PrimaryBackground
import com.meshlink.app.ui.theme.PrimaryText
import com.meshlink.app.ui.theme.SecondaryAccent
import com.meshlink.app.ui.theme.SecondaryText
import com.meshlink.app.ui.theme.SoftLavender
import com.meshlink.app.ui.theme.WarmWhite

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("meshlink_settings", Context.MODE_PRIVATE) }

    var autoDiscovery by remember {
        mutableStateOf(prefs.getBoolean("auto_discovery", true))
    }
    var backgroundScanning by remember {
        mutableStateOf(prefs.getBoolean("bg_scanning", true))
    }
    var showNetworkStatus by remember {
        mutableStateOf(prefs.getBoolean("show_status", true))
    }

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
                text = "Settings",
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Section 1: GENERAL ────────────────────────────────────────────
            SettingsSectionHeader(title = "General")

            Surface(
                shape = RoundedCornerShape(MeshLinkRadius.CardLarge),
                color = WarmWhite,
                shadowElevation = 0.5.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SettingsNavRow(
                        icon = Icons.Default.Palette,
                        title = "Appearance",
                        value = "System default",
                        onClick = {
                            Toast.makeText(context, "Appearance: System default", Toast.LENGTH_SHORT).show()
                        }
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        icon = Icons.Default.Notifications,
                        title = "Notifications",
                        value = "",
                        onClick = {
                            Toast.makeText(context, "Notifications enabled for mesh events", Toast.LENGTH_SHORT).show()
                        }
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        icon = Icons.Default.Language,
                        title = "Language",
                        value = "English",
                        onClick = {
                            Toast.makeText(context, "Language: English", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // ── Section 2: MESH NETWORK ───────────────────────────────────────
            SettingsSectionHeader(title = "Mesh Network")

            Surface(
                shape = RoundedCornerShape(MeshLinkRadius.CardLarge),
                color = WarmWhite,
                shadowElevation = 0.5.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SettingsToggleRow(
                        icon = Icons.Default.Radar,
                        title = "Auto discovery",
                        checked = autoDiscovery,
                        onCheckedChange = { checked ->
                            autoDiscovery = checked
                            prefs.edit().putBoolean("auto_discovery", checked).apply()
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Default.Sync,
                        title = "Background scanning",
                        checked = backgroundScanning,
                        onCheckedChange = { checked ->
                            backgroundScanning = checked
                            prefs.edit().putBoolean("bg_scanning", checked).apply()
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Default.Sensors,
                        title = "Show network status",
                        checked = showNetworkStatus,
                        onCheckedChange = { checked ->
                            showNetworkStatus = checked
                            prefs.edit().putBoolean("show_status", checked).apply()
                        }
                    )
                }
            }

            // ── Section 3: PRIVACY & SECURITY ─────────────────────────────────
            SettingsSectionHeader(title = "Privacy & Security")

            Surface(
                shape = RoundedCornerShape(MeshLinkRadius.CardLarge),
                color = WarmWhite,
                shadowElevation = 0.5.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SettingsNavRow(
                        icon = Icons.Default.Lock,
                        title = "Identity key management",
                        value = "",
                        onClick = onNavigateToSecurity
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        icon = Icons.Default.FolderSpecial,
                        title = "Data protection",
                        value = "",
                        onClick = onNavigateToSecurity
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = SecondaryText,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(Color(0xFFEFE6F0))
    )
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PrimaryText,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = PrimaryText,
            modifier = Modifier.weight(1f)
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                fontSize = 13.sp,
                color = SecondaryText,
                modifier = Modifier.padding(end = 4.dp)
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

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PrimaryText,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = PrimaryText,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryAccent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MutedText.copy(alpha = 0.5f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
