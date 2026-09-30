package com.meshlink.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.Message
import com.meshlink.app.ui.components.MeshAvatar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val SentBubbleShape = RoundedCornerShape(
    topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp
)
private val ReceivedBubbleShape = RoundedCornerShape(
    topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp
)

@Composable
fun ChatScreen(
    onBackClick: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages        by viewModel.messages.collectAsStateWithLifecycle()
    val inputText       by viewModel.inputText.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val peerCount       by viewModel.peerCount.collectAsStateWithLifecycle()
    val haptic          = LocalHapticFeedback.current
    val listState       = rememberLazyListState()
    var showQuickActions by rememberSaveable { mutableStateOf(false) }

    val isConnected  = connectionState == ConnectionState.CONNECTED
    val isConnecting = connectionState == ConnectionState.CONNECTING ||
                       connectionState == ConnectionState.HANDSHAKING
    val canSendText  = inputText.isNotBlank()  // always allow sending; queue if offline

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        ChatTopBar(
            deviceName   = viewModel.deviceName,
            peerCount    = peerCount,
            isConnected  = isConnected,
            isConnecting = isConnecting,
            onBackClick  = onBackClick
        )

        MeshLinkStatusBar(deviceName = viewModel.deviceName, isConnected = isConnected)

        val grouped = remember(messages) { groupMessagesByDate(messages) }

        LazyColumn(
            state               = listState,
            modifier            = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            reverseLayout       = true,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            grouped.forEach { (dateLabel, dayMessages) ->
                items(dayMessages.reversed(), key = { it.id }) { message ->
                    val isOutgoing = message.senderId == viewModel.localDeviceId
                    val bodyText   = remember(message.id) {
                        String(message.ciphertext, Charsets.UTF_8)
                    }
                    AnimatedVisibility(
                        visible = true,
                        enter   = fadeIn() + slideInVertically { it / 2 }
                    ) {
                        when {
                            !isOutgoing && bodyText.startsWith("CRITICAL:") ->
                                CriticalAlertCard(
                                    text      = bodyText.removePrefix("CRITICAL:").trim(),
                                    timestamp = message.timestamp
                                )
                            bodyText == "\uD83D\uDFE2 I AM SAFE" ->
                                SafeSignalBubble(isOutgoing = isOutgoing, timestamp = message.timestamp)
                            else ->
                                MessageBubble(message = message, isOutgoing = isOutgoing)
                        }
                    }
                }
                item(key = "sep_$dateLabel") { DateSeparator(label = dateLabel) }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        // Quick-action panel (slides up when '+' is tapped)
        AnimatedVisibility(
            visible = showQuickActions,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut()
        ) {
            QuickActionsPanel(
                onSafeSignal = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onSendSafeSignal()
                    showQuickActions = false
                },
                onNeedHelp = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onInputChanged("CRITICAL: I NEED HELP — please respond!")
                    showQuickActions = false
                },
                onSendLocation = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onInputChanged("\uD83D\uDCCD Sharing my current location")
                    showQuickActions = false
                }
            )
        }

        ChatInputBar(
            text              = inputText,
            onTextChange      = viewModel::onInputChanged,
            onSend            = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.onSendClick()
            },
            canSend           = canSendText,
            enabled           = true,          // always allow typing
            isConnected       = isConnected,
            showQuickActions  = showQuickActions,
            onToggleActions   = { showQuickActions = !showQuickActions }
        )
    }
}

@Composable
private fun ChatTopBar(
    deviceName: String, peerCount: Int,
    isConnected: Boolean, isConnecting: Boolean,
    onBackClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint     = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }

        MeshAvatar(name = deviceName, size = 40.dp)

        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text       = deviceName,
                    style      = MaterialTheme.typography.titleSmall,
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.semantics { contentDescription = "Chat with $deviceName" }
                )
                if (peerCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text     = "$peerCount PEERS",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 9.sp
                        )
                    }
                }
            }
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.secondary,
                        strokeWidth = 1.5.dp, modifier = Modifier.size(8.dp)
                    )
                    Text("Connecting…", style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.secondary)
                } else {
                    Box(
                        modifier = Modifier.size(7.dp).clip(CircleShape)
                            .background(if (isConnected) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.outline)
                    )
                    Text(
                        text  = if (isConnected) "Direct mesh link" else "Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isConnected) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        IconButton(onClick = {}) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options",
                 tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }

    Box(Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.outline))
}

@Composable
private fun MeshLinkStatusBar(deviceName: String, isConnected: Boolean) {
    val bgColor   = if (isConnected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    val textColor = if (isConnected) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.error
    val dotColor  = if (isConnected) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.error

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
        if (isConnected) {
            Text(
                text = "DIRECT MESH LINK ACTIVE", style = MaterialTheme.typography.labelSmall,
                color = textColor, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp
            )
            Text("•", style = MaterialTheme.typography.labelSmall,
                 color = textColor.copy(alpha = 0.6f))
            Text(
                text = mockPeerDistance(deviceName) + " AWAY",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.85f)
            )
        } else {
            Text(
                text = "OUT OF RANGE", style = MaterialTheme.typography.labelSmall,
                color = textColor, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp
            )
            Text("•", style = MaterialTheme.typography.labelSmall,
                 color = textColor.copy(alpha = 0.6f))
            Text(
                text = "Messages will be sent when in range",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun CriticalAlertCard(text: String, timestamp: Long) {
    val timeStr = remember(timestamp) { formatTime(timestamp) }

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Critical alert",
                         tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "CRITICAL ALERT", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(text = text, style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurface,
                     fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
                Spacer(Modifier.height(6.dp))
                Text(text = timeStr, style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                     modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun SafeSignalBubble(isOutgoing: Boolean, timestamp: Long) {
    val timeStr = remember(timestamp) { formatTime(timestamp) }

    Box(
        modifier = Modifier.fillMaxWidth().padding(
            start = if (isOutgoing) 56.dp else 0.dp,
            end   = if (isOutgoing) 0.dp else 56.dp,
            top = 4.dp, bottom = 4.dp
        ),
        contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.tertiary) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Safe signal",
                     tint = Color.White, modifier = Modifier.size(18.dp))
                Column {
                    Text("I AM SAFE", style = MaterialTheme.typography.labelLarge,
                         color = Color.White, fontWeight = FontWeight.ExtraBold,
                         letterSpacing = 1.sp)
                    Text(timeStr, style = MaterialTheme.typography.labelSmall,
                         color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, isOutgoing: Boolean) {
    val bubbleBg    = if (isOutgoing) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceVariant
    val textColor   = if (isOutgoing) Color.White
                      else MaterialTheme.colorScheme.onSurface
    val metaColor   = if (isOutgoing) Color.White.copy(alpha = 0.65f)
                      else MaterialTheme.colorScheme.onSurfaceVariant
    val bubbleShape = if (isOutgoing) SentBubbleShape else ReceivedBubbleShape
    val bodyText    = remember(message.id) { String(message.ciphertext, Charsets.UTF_8) }
    val timeStr     = remember(message.timestamp) { formatTime(message.timestamp) }

    Box(
        modifier = Modifier.fillMaxWidth().padding(
            start = if (isOutgoing) 64.dp else 0.dp,
            end   = if (isOutgoing) 0.dp else 64.dp,
            top = 2.dp, bottom = 2.dp
        ),
        contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = bubbleShape, color = bubbleBg,
            modifier = Modifier.widthIn(max = 280.dp).semantics {
                contentDescription = if (isOutgoing) "You: $bodyText at $timeStr"
                                     else "Message: $bodyText at $timeStr"
            }
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(text = bodyText, color = textColor,
                     style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp)
                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(text = timeStr, color = metaColor,
                         style = MaterialTheme.typography.labelSmall,
                         textAlign = TextAlign.End, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun DateSeparator(label: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                text = label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                fontSize = 10.sp, letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun QuickActionsPanel(
    onSafeSignal:   () -> Unit,
    onNeedHelp:     () -> Unit,
    onSendLocation: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionChip(
                label = "I AM SAFE",
                icon  = Icons.Default.CheckCircle,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor   = MaterialTheme.colorScheme.tertiary,
                onClick = onSafeSignal,
                modifier = Modifier.weight(1f)
            )
            QuickActionChip(
                label = "NEED HELP",
                icon  = Icons.Default.Warning,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor   = MaterialTheme.colorScheme.error,
                onClick = onNeedHelp,
                modifier = Modifier.weight(1f)
            )
            QuickActionChip(
                label = "LOCATION",
                icon  = Icons.Default.LocationOn,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor   = MaterialTheme.colorScheme.primary,
                onClick = onSendLocation,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(12.dp),
        color    = containerColor,
        modifier = modifier.border(
            1.dp, contentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon, contentDescription = label,
                tint = contentColor, modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.size(5.dp))
            Text(
                label, style = MaterialTheme.typography.labelSmall,
                color = contentColor, fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp, maxLines = 1
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    text: String, onTextChange: (String) -> Unit,
    onSend: () -> Unit, canSend: Boolean, enabled: Boolean,
    isConnected: Boolean = true,
    showQuickActions: Boolean = false,
    onToggleActions: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outline)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(
                    if (showQuickActions) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onToggleActions, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (showQuickActions) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = if (showQuickActions) "Close actions" else "Quick actions",
                    tint = if (showQuickActions) Color.White
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Box(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 11.dp)
        ) {
            if (text.isEmpty()) {
                Text(
                    text = if (isConnected) "Message…"
                           else "Message… (will send when in range)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = text, onValueChange = onTextChange, enabled = enabled,
                textStyle = MaterialTheme.typography.bodyMedium
                    .copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
                    .semantics { contentDescription = "Message input" }
            )
        }

        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape)
                .background(if (canSend) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onSend, enabled = canSend,
                modifier = Modifier.size(42.dp)
                    .semantics { contentDescription = "Send message" }
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send, contentDescription = null,
                    tint = if (canSend) Color.White
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun mockPeerDistance(name: String): String {
    val distances = listOf("42M", "110M", "250M", "85M", "67M")
    return distances[Math.abs(name.hashCode()) % distances.size]
}

private fun groupMessagesByDate(messages: List<Message>): List<Pair<String, List<Message>>> {
    if (messages.isEmpty()) return emptyList()
    val now       = Calendar.getInstance()
    val yesterday = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, -1) }
    val fmt       = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return messages
        .groupBy { msg ->
            val cal = Calendar.getInstance().also { it.timeInMillis = msg.timestamp }
            when {
                isSameDay(cal, now)       -> "Today"
                isSameDay(cal, yesterday) -> "Yesterday"
                else                      -> fmt.format(Date(msg.timestamp))
            }
        }
        .entries
        .sortedByDescending { (_, msgs) -> msgs.maxOf { it.timestamp } }
        .map { (label, msgs) -> label to msgs.sortedBy { it.timestamp } }
}

private fun isSameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
    a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
