package com.meshlink.app.ui.broadcast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlink.app.ui.theme.EmergencyGreen
import com.meshlink.app.ui.theme.EmergencyRed
import com.meshlink.app.ui.theme.LightBackground
import com.meshlink.app.ui.theme.LightBorder
import com.meshlink.app.ui.theme.LightSurface
import com.meshlink.app.ui.theme.LightSurfaceVariant
import com.meshlink.app.ui.theme.TextMuted
import com.meshlink.app.ui.theme.TextPrimary
import com.meshlink.app.ui.theme.TextSecondary

@Composable
fun BroadcastScreen(
    onBackClick: () -> Unit,
    viewModel: BroadcastViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBackground)
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        BroadcastTopBar(onBackClick = onBackClick)

        // ── Content ──────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp)
        ) {
            // Icon + Title
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(EmergencyRed.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.WifiTethering,
                            contentDescription = null,
                            tint = EmergencyRed,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Broadcast to All Nodes",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Your message will be sent to all connected mesh peers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Message input ────────────────────────────────────────────────
            Text(
                text = "MESSAGE",
                style = MaterialTheme.typography.labelLarge,
                color = EmergencyRed,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = LightSurface,
                shadowElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (state.message.isEmpty()) {
                        Text(
                            text = "Type your broadcast message...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextMuted
                        )
                    }
                    BasicTextField(
                        value = state.message,
                        onValueChange = viewModel::onMessageChanged,
                        enabled = !state.isSending && !state.isSent,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = TextPrimary
                        ),
                        cursorBrush = SolidColor(EmergencyRed),
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Error message ────────────────────────────────────────────────
            state.error?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = EmergencyRed,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Character count ──────────────────────────────────────────────
            Text(
                text = "${state.message.length} characters",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.align(Alignment.End)
            )

            Spacer(Modifier.weight(1f))

            // ── Success feedback ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = state.isSent,
                enter = fadeIn() + scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            ) {
                SuccessCard()
            }

            Spacer(Modifier.height(16.dp))
        }

        // ── Send button ──────────────────────────────────────────────────────
        BroadcastSendButton(
            isSending = state.isSending,
            isSent = state.isSent,
            onClick = viewModel::sendBroadcast
        )
    }
}

// ── Top bar ──────────────────────────────────────────────────────────────────

@Composable
private fun BroadcastTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LightSurface)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = EmergencyRed
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "Broadcast",
            style = MaterialTheme.typography.titleLarge,
            color = EmergencyRed,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        // Invisible spacer to center title
        Box(modifier = Modifier.size(48.dp))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(LightBorder))
}

// ── Success card ─────────────────────────────────────────────────────────────

@Composable
private fun SuccessCard() {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "successScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(16.dp),
        color = EmergencyGreen.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Success",
                tint = EmergencyGreen,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(
                    text = "Broadcast Sent Successfully",
                    style = MaterialTheme.typography.titleSmall,
                    color = EmergencyGreen,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "All connected mesh nodes have been alerted",
                    style = MaterialTheme.typography.bodySmall,
                    color = EmergencyGreen.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ── Send button ──────────────────────────────────────────────────────────────

@Composable
private fun BroadcastSendButton(
    isSending: Boolean,
    isSent: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(LightSurface)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = !isSending && !isSent, onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            color = when {
                isSent    -> EmergencyGreen
                isSending -> EmergencyRed.copy(alpha = 0.7f)
                else      -> EmergencyRed
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    isSending -> {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = "Sending...",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    isSent -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = "Sent Successfully",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    else -> {
                        Icon(
                            Icons.Default.WifiTethering,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = "Send Broadcast",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
