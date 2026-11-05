package xyz.totemai.app.ui

import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import xyz.totemai.app.Routes
import xyz.totemai.app.ui.theme.InkElevated
import xyz.totemai.app.ui.theme.Violet
import xyz.totemai.app.ui.theme.TextSecondary

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Home", Icons.Default.Home),
    Tab(Routes.WORKSPACE, "Chat", Icons.Default.Send),
    Tab(Routes.WALLET, "Wallet", Icons.Default.AccountBalanceWallet),
    Tab(Routes.PROFILE, "Profile", Icons.Default.Person)
)

@Composable
fun TotemBottomBar(current: String, onSelect: (String) -> Unit) {
    NavigationBar(
        containerColor = InkElevated,
        tonalElevation = 0.dp,
        modifier = Modifier.height(72.dp)
    ) {
        TABS.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = { if (current != tab.route) onSelect(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Violet,
                    selectedTextColor = Violet,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = Violet.copy(alpha = 0.14f)
                )
            )
        }
    }
}
