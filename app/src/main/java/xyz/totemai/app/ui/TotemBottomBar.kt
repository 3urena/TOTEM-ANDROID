package xyz.totemai.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.totemai.app.Routes
import xyz.totemai.app.ui.theme.InkElevated
import xyz.totemai.app.ui.theme.TextSecondary
import xyz.totemai.app.ui.theme.Violet

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Home", Icons.Default.Home),
    Tab(Routes.WORKSPACE, "Chat", Icons.Default.Send),
    Tab(Routes.WALLET, "Wallet", Icons.Default.AccountBalanceWallet),
    Tab(Routes.PROFILE, "Profile", Icons.Default.Person)
)

@Composable
fun TotemBottomBar(current: String, onSelect: (String) -> Unit) {
    // Explicitly reserve the Android navigation area. The tab row itself has
    // a fixed, measured height so icons and labels cannot be clipped.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(InkElevated)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .height(58.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TABS.forEach { tab ->
            val selected = current == tab.route
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) Violet.copy(alpha = 0.16f) else Color.Transparent
                    )
                    .clickable { if (!selected) onSelect(tab.route) }
                    .padding(horizontal = 2.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = if (selected) Violet else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = tab.label,
                    color = if (selected) Violet else TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}
