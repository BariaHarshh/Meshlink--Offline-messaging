package com.meshlink.app.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
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
import com.meshlink.app.ui.theme.SoftLavender
import com.meshlink.app.ui.theme.WarmWhite

@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "meshPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue  = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PrimaryBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar: Skip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onGetStarted) {
                Text(
                    text = "Skip",
                    color = SecondaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Title and description
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Stay connected\neven without\nthe internet",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
                lineHeight = 38.sp,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "MeshLink uses nearby devices to create a secure, decentralized mesh network.",
                fontSize = 15.sp,
                color = SecondaryText,
                lineHeight = 22.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Center Mesh Illustration matching Screen 2
        Box(
            modifier = Modifier
                .size(280.dp),
            contentAlignment = Alignment.Center
        ) {
            // Dashed connection lines forming the mesh triangle
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val topNode   = Offset(w * 0.50f, h * 0.22f)
                val leftNode  = Offset(w * 0.22f, h * 0.72f)
                val rightNode = Offset(w * 0.78f, h * 0.72f)

                val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)

                // Soft outer glowing background circles
                drawCircle(
                    color = SoftLavender.copy(alpha = 0.25f),
                    radius = w * 0.42f * pulseScale,
                    center = Offset(w * 0.5f, h * 0.5f)
                )

                drawLine(
                    color = PrimaryAccent.copy(alpha = 0.6f),
                    start = topNode,
                    end = leftNode,
                    strokeWidth = 2.5f,
                    pathEffect = dashedEffect
                )
                drawLine(
                    color = PrimaryAccent.copy(alpha = 0.6f),
                    start = topNode,
                    end = rightNode,
                    strokeWidth = 2.5f,
                    pathEffect = dashedEffect
                )
                drawLine(
                    color = PrimaryAccent.copy(alpha = 0.6f),
                    start = leftNode,
                    end = rightNode,
                    strokeWidth = 2.5f,
                    pathEffect = dashedEffect
                )
            }

            // Top Avatar (YOU)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp)
            ) {
                PeerAvatarNode(
                    label = "You",
                    backgroundColor = SecondaryAccent.copy(alpha = 0.35f),
                    borderColor = SecondaryAccent
                )
            }

            // Bottom-Left Avatar (Peer A)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 22.dp)
            ) {
                PeerAvatarNode(
                    label = "Alex",
                    backgroundColor = SoftLavender.copy(alpha = 0.45f),
                    borderColor = SoftLavender
                )
            }

            // Bottom-Right Avatar (Peer B)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 22.dp)
            ) {
                PeerAvatarNode(
                    label = "Priya",
                    backgroundColor = PrimaryAccent.copy(alpha = 0.25f),
                    borderColor = PrimaryAccent
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Page Indicator Dots (4 dots, first active)
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 24.dp, height = 7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(PrimaryAccent)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MutedText.copy(alpha = 0.45f))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MutedText.copy(alpha = 0.45f))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MutedText.copy(alpha = 0.45f))
            )
        }

        // Get Started CTA Button
        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(MeshLinkRadius.Button),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryAccent,
                contentColor   = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Get Started",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PeerAvatarNode(
    label: String,
    backgroundColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(WarmWhite)
                .border(3.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = label,
                    tint = PrimaryText,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
