package com.meshlink.app.ui.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.DeliveryStatus
import com.meshlink.app.domain.model.Message
import com.meshlink.app.domain.model.VerificationStatus
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    onBackClick: () -> Unit,
    onNavigateToPeerDetails: ((deviceId: String, deviceName: String) -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages        by viewModel.messages.collectAsStateWithLifecycle()
    val inputText       by viewModel.inputText.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val peerDevice      by viewModel.peerDevice.collectAsStateWithLifecycle()
    val listState       = rememberLazyListState()
    val context         = LocalContext.current
    var showMenu        by remember { mutableStateOf(false) }

    val isConnected = connectionState == ConnectionState.CONNECTED
    val isVerified = peerDevice?.verificationStatus == VerificationStatus.VERIFIED

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // ── Top Header matching Screen 5 ──────────────────────────────────────
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

            // Peer Avatar Circle with Initial
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SecondaryAccent.copy(alpha = 0.35f))
                    .clickable {
                        onNavigateToPeerDetails?.invoke(viewModel.deviceId, viewModel.deviceName)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = viewModel.deviceName.take(1).uppercase(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onNavigateToPeerDetails?.invoke(viewModel.deviceId, viewModel.deviceName)
                    }
            ) {
                Text(
                    text = viewModel.deviceName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) SoftGreen else MutedText)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isConnected) "Connected through mesh" else "Routing through mesh",
                        fontSize = 12.sp,
                        color = SecondaryText
                    )
                }
            }

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
                        text = { Text("Peer Details") },
                        onClick = {
                            showMenu = false
                            onNavigateToPeerDetails?.invoke(viewModel.deviceId, viewModel.deviceName)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Send Safe Signal") },
                        onClick = {
                            showMenu = false
                            viewModel.onSendSafeSignal()
                            Toast.makeText(context, "Safe signal sent", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // ── Security Banner matching Screen 5 ─────────────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = WarmWhite,
            shadowElevation = 0.5.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(PrimaryAccent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = PrimaryAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "End-to-end encrypted",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Text(
                        text = if (isVerified) "P-256 verified · Secure connection" else "P-256 encrypted · Unverified identity",
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                }
            }
        }

        // ── Messages List ─────────────────────────────────────────────────────
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No messages yet\nSay hello to ${viewModel.deviceName}!",
                    fontSize = 14.sp,
                    color = SecondaryText,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages.reversed(), key = { it.id }) { message ->
                    val isOutgoing = message.senderId == viewModel.localDeviceId
                    val bodyText = remember(message.id) {
                        String(message.ciphertext, Charsets.UTF_8)
                    }

                    ChatBubble(
                        body = bodyText,
                        timestamp = message.timestamp,
                        isOutgoing = isOutgoing,
                        deliveryStatus = message.deliveryStatus
                    )
                }
            }
        }

        // ── Bottom Input Row matching Screen 5 ────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Paperclip Attachment Icon
            IconButton(
                onClick = {
                    Toast.makeText(context, "Attachment sharing active over mesh", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attach file",
                    tint = SecondaryText,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Rounded Text Input
            Surface(
                shape = RoundedCornerShape(MeshLinkRadius.Button),
                color = WarmWhite,
                shadowElevation = 0.5.dp,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = viewModel::onInputChanged,
                        singleLine = true,
                        cursorBrush = SolidColor(PrimaryAccent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    viewModel.onSendClick()
                                }
                            }
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 15.sp,
                            color = PrimaryText
                        ),
                        decorationBox = { innerTextField ->
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "Type a message...",
                                    fontSize = 15.sp,
                                    color = MutedText
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Circular Send Button in Dusty Rose
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(PrimaryAccent)
                    .clickable {
                        if (inputText.isNotBlank()) {
                            viewModel.onSendClick()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(
    body: String,
    timestamp: Long,
    isOutgoing: Boolean,
    deliveryStatus: DeliveryStatus
) {
    val timeFormatted = remember(timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp)
    }

    val bubbleColor = if (isOutgoing) PrimaryAccent else WarmWhite
    val textColor   = if (isOutgoing) Color.White else PrimaryText
    val metaColor   = if (isOutgoing) Color.White.copy(alpha = 0.85f) else MutedText

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            shadowElevation = if (isOutgoing) 1.dp else 0.5.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = body,
                    fontSize = 15.sp,
                    color = textColor,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = timeFormatted,
                        fontSize = 11.sp,
                        color = metaColor
                    )

                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (deliveryStatus) {
                            DeliveryStatus.DELIVERED -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Delivered",
                                    tint = metaColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            DeliveryStatus.SENT -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sent",
                                    tint = metaColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            DeliveryStatus.QUEUED, DeliveryStatus.PENDING -> {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Waiting for node",
                                    tint = metaColor,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            DeliveryStatus.FAILED -> {
                                Text(
                                    text = "!",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = metaColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
