package com.meshlink.app.ui.sos

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlink.app.ui.theme.DarkBackground
import com.meshlink.app.ui.theme.DarkSurface
import com.meshlink.app.ui.theme.DarkSurfaceElevated
import com.meshlink.app.ui.theme.EmergencyGreen
import com.meshlink.app.ui.theme.EmergencyRed
import com.meshlink.app.ui.theme.EmergencyRedContainer
import com.meshlink.app.ui.theme.EmergencyRedSurface
import com.meshlink.app.ui.theme.TextOnDark
import com.meshlink.app.ui.theme.TextOnDarkDim
import com.meshlink.app.ui.theme.TextOnDarkMuted

@Composable
fun SosScreen(
    onMedicalProfileClick: () -> Unit = {}
) {
    var sosBroadcasting by remember { mutableStateOf(false) }

    val transition = rememberInfiniteTransition(label = "sos-pulse")
    val pulse1 by transition.animateFloat(
        initialValue  = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "p1"
    )
    val pulse2 by transition.animateFloat(
        initialValue  = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, delayMillis = 600, easing = LinearEasing), RepeatMode.Restart),
        label = "p2"
    )
    val pulse3 by transition.animateFloat(
        initialValue  = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, delayMillis = 1200, easing = LinearEasing), RepeatMode.Restart),
        label = "p3"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // ── Header ─────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(24.dp)
        ) {
            Text(
                text       = "SOS",
                style      = MaterialTheme.typography.displaySmall,
                color      = EmergencyRed,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text  = "Emergency broadcast to all mesh nodes",
                style = MaterialTheme.typography.bodyMedium,
                color = TextOnDarkDim
            )
        }

        // ── SOS Button with pulse animation ────────────────────────────────────
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .height(280.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(240.dp)) {
                val maxR = size.minDimension / 2f
                listOf(pulse1 to 0.12f, pulse2 to 0.20f, pulse3 to 0.28f).forEach { (progress, alpha) ->
                    drawCircle(
                        color  = EmergencyRed,
                        radius = maxR * progress,
                        alpha  = (alpha * (1f - progress)).coerceIn(0f, 1f),
                        style  = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color  = EmergencyRed,
                        radius = maxR * progress,
                        alpha  = ((alpha * 0.4f) * (1f - progress)).coerceIn(0f, 0.15f)
                    )
                }
            }

            // Center SOS button
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(if (sosBroadcasting) EmergencyRedSurface else EmergencyRed)
                    .clickable { sosBroadcasting = !sosBroadcasting },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "SOS",
                        tint     = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text       = "SOS",
                        color      = Color.White,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }

        // ── Status ─────────────────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (sosBroadcasting) EmergencyRedContainer else DarkSurface
        ) {
            Row(
                modifier              = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (sosBroadcasting) EmergencyRed else TextOnDarkMuted)
                )
                Text(
                    text  = if (sosBroadcasting)
                                "SOS BROADCASTING — all nearby nodes alerted"
                            else
                                "Tap the SOS button to broadcast emergency alert",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (sosBroadcasting) EmergencyRed else TextOnDarkDim
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Quick actions ──────────────────────────────────────────────────────
        Text(
            text     = "QUICK ACTIONS",
            style    = MaterialTheme.typography.labelLarge,
            color    = TextOnDarkMuted,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            letterSpacing = 1.sp
        )

        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                icon    = Icons.Default.MedicalServices,
                label   = "Medical Profile",
                color   = EmergencyGreen,
                onClick = onMedicalProfileClick,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                icon    = Icons.Default.Person,
                label   = "I Am Safe",
                color   = EmergencyGreen,
                onClick = { sosBroadcasting = false },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text      = "SOS will alert all reachable mesh nodes within range.\nYour Medical Profile will be shared with responders.",
            style     = MaterialTheme.typography.bodySmall,
            color     = TextOnDarkMuted,
            textAlign = TextAlign.Center,
            modifier  = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun QuickActionCard(
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
    label:    String,
    color:    Color,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = DarkSurfaceElevated
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = label,
                tint               = color,
                modifier           = Modifier.size(28.dp)
            )
            Text(
                text      = label,
                style     = MaterialTheme.typography.labelLarge,
                color     = TextOnDark,
                textAlign = TextAlign.Center
            )
        }
    }
}
