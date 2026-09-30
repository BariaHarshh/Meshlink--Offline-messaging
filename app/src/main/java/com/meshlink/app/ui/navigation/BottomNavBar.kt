package com.meshlink.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BottomNavItem(
    val route:          String,
    val label:          String,
    val selectedIcon:   ImageVector,
    val unselectedIcon: ImageVector,
    val isSos:          Boolean = false
)

val bottomNavItems = listOf(
    BottomNavItem(
        route          = Screen.Home.route,
        label          = "CHATS",
        selectedIcon   = Icons.Filled.Forum,
        unselectedIcon = Icons.Outlined.Forum
    ),
    BottomNavItem(
        route          = Screen.Discovery.route,
        label          = "DISCOVER",
        selectedIcon   = Icons.Filled.WifiTethering,
        unselectedIcon = Icons.Outlined.WifiTethering
    ),
    BottomNavItem(
        route          = Screen.Sos.route,
        label          = "SOS",
        selectedIcon   = Icons.Filled.Warning,
        unselectedIcon = Icons.Filled.Warning,
        isSos          = true
    )
)

@Composable
fun MeshBottomNavBar(
    currentRoute: String?,
    onNavigate:   (String) -> Unit,
    modifier:     Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outline)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                NavItem(
                    item     = item,
                    selected = currentRoute == item.route,
                    onClick  = { onNavigate(item.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    item:     BottomNavItem,
    selected: Boolean,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor by animateColorAsState(
        targetValue   = when {
            item.isSos -> MaterialTheme.colorScheme.primary
            selected   -> MaterialTheme.colorScheme.primary
            else       -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "navIconColor"
    )
    val labelColor by animateColorAsState(
        targetValue = when {
            item.isSos -> MaterialTheme.colorScheme.primary
            selected   -> MaterialTheme.colorScheme.primary
            else       -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "navLabelColor"
    )

    Column(
        modifier            = modifier
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier         = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    when {
                        item.isSos && selected -> MaterialTheme.colorScheme.primaryContainer
                        selected               -> MaterialTheme.colorScheme.primaryContainer
                        else                   -> Color.Transparent
                    }
                )
                .padding(horizontal = 18.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = if (selected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.label,
                tint               = iconColor,
                modifier           = Modifier.size(22.dp)
            )
        }

        Text(
            text       = item.label,
            color      = labelColor,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = if (item.isSos || selected) FontWeight.Bold else FontWeight.Medium,
            fontSize   = 9.sp
        )
    }
}
