package com.comai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comai.ui.navigation.Routes
import com.comai.ui.theme.DarkSurface
import com.comai.ui.theme.ElectricTeal
import com.comai.ui.theme.TextPrimary
import com.comai.ui.theme.TextSecondary

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val MainNavItems = listOf(
    NavItem(Routes.HOME, "Home", Icons.Outlined.Home),
    NavItem(Routes.CHAT, "Chat", Icons.Outlined.ChatBubbleOutline),
    NavItem(Routes.ROUTINE, "Routine", Icons.Outlined.Schedule),
    NavItem(Routes.MEMORY, "Memory", Icons.Outlined.Lightbulb),
    NavItem(Routes.PROFILE, "Profile", Icons.Outlined.Person)
)

/**
 * Unified bottom navigation dock designed with a modern floating glass aesthetic.
 */
@Composable
fun ComaiBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkSurface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFF202635)),
        tonalElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainNavItems.forEach { item ->
                val isSelected = currentRoute == item.route
                Surface(
                    color = if (isSelected) Color(0xFF14242F) else Color.Transparent,
                    shape = RoundedCornerShape(16.dp),
                    onClick = {
                        if (!isSelected) {
                            onNavigate(item.route)
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (isSelected) ElectricTeal else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.label,
                                color = ElectricTeal,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
