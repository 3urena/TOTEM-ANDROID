package xyz.totemai.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import xyz.totemai.app.Routes
import xyz.totemai.app.ui.theme.Ink
import xyz.totemai.app.ui.theme.Lime
import xyz.totemai.app.ui.theme.Outline
import xyz.totemai.app.ui.theme.TextSecondary

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val featured: Boolean = false
)

private val TABS = listOf(
    Tab(Routes.HOME, "Home", Icons.Default.Home),
    Tab(Routes.WORKSPACE, "Chat", Icons.Default.SmartToy, featured = true),
    Tab(Routes.WALLET, "Wallet", Icons.Default.Lock),
    Tab(Routes.PROFILE, "Profile", Icons.Default.AccountCircle)
)

@Composable
fun TotemBottomBar(current: String, onSelect: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(30.dp))
                .background(Color(0xEE111815), RoundedCornerShape(30.dp))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(30.dp))
                .padding(horizontal = 7.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TABS.forEach { tab ->
                val selected = current == tab.route
                val tint by animateColorAsState(
                    targetValue = if (selected && !tab.featured) Lime else TextSecondary,
                    label = "tab-tint"
                )
                val iconSize by animateDpAsState(
                    targetValue = if (selected && tab.featured) 25.dp else 21.dp,
                    label = "tab-icon-size"
                )

                if (tab.featured) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { if (!selected) onSelect(tab.route) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(49.dp)
                                .shadow(if (selected) 10.dp else 3.dp, CircleShape)
                                .background(if (selected) Lime else Lime.copy(alpha = 0.16f), CircleShape)
                                .border(1.dp, Lime.copy(alpha = 0.55f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) Ink else Lime,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                        Text(
                            tab.label,
                            color = if (selected) Lime else TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { if (!selected) onSelect(tab.route) }
                            .padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(iconSize)
                        )
                        Text(
                            tab.label,
                            color = tint,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
