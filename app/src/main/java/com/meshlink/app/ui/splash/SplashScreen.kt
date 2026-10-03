package com.meshlink.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlink.app.ui.components.MeshLinkLogo
import com.meshlink.app.ui.theme.PrimaryAccent
import com.meshlink.app.ui.theme.PrimaryBackground
import com.meshlink.app.ui.theme.PrimaryText
import com.meshlink.app.ui.theme.SecondaryText
import com.meshlink.app.ui.theme.SoftLavender
import com.meshlink.app.ui.theme.WarmWhite
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(0.85f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(700, easing = FastOutSlowInEasing)
        )
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(600)
        )
        delay(2200)
        onNavigateNext()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        WarmWhite,
                        PrimaryBackground,
                        SoftLavender.copy(alpha = 0.5f),
                        PrimaryBackground
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Decorative flowing mesh curved background lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val arcPath1 = Path().apply {
                moveTo(-w * 0.2f, h * 0.45f)
                cubicTo(w * 0.3f, h * 0.35f, w * 0.7f, h * 0.65f, w * 1.2f, h * 0.55f)
            }
            drawPath(
                path = arcPath1,
                color = PrimaryAccent.copy(alpha = 0.12f),
                style = Stroke(width = 2.5f)
            )

            val arcPath2 = Path().apply {
                moveTo(-w * 0.1f, h * 0.55f)
                cubicTo(w * 0.4f, h * 0.75f, w * 0.8f, h * 0.35f, w * 1.3f, h * 0.70f)
            }
            drawPath(
                path = arcPath2,
                color = SoftLavender.copy(alpha = 0.35f),
                style = Stroke(width = 3.5f)
            )

            val arcPath3 = Path().apply {
                moveTo(w * 0.1f, h * 0.85f)
                cubicTo(w * 0.5f, h * 0.95f, w * 0.8f, h * 0.80f, w * 1.2f, h * 0.90f)
            }
            drawPath(
                path = arcPath3,
                color = PrimaryAccent.copy(alpha = 0.08f),
                style = Stroke(width = 2.0f)
            )
        }

        // Center Brand Identity
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(scale.value)
                .padding(horizontal = 32.dp)
        ) {
            MeshLinkLogo(
                size = 110.dp,
                animated = true
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "MeshLink",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Secure. Offline. Together.",
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = SecondaryText
            )
        }

        // Bottom CTA forward button matching Screen 1 reference
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 56.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(PrimaryAccent)
                    .clickable(onClick = onNavigateNext),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Proceed to MeshLink",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
