package xyz.totemai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.totemai.app.ui.components.ActionRow
import xyz.totemai.app.ui.components.Gyroscope
import xyz.totemai.app.ui.components.StatusChip
import xyz.totemai.app.ui.components.TotemLogo
import xyz.totemai.app.ui.theme.AppBackground
import xyz.totemai.app.ui.theme.Ink
import xyz.totemai.app.ui.theme.Violet
import xyz.totemai.app.ui.theme.Lime
import xyz.totemai.app.ui.theme.Outline
import xyz.totemai.app.ui.theme.Surface1
import xyz.totemai.app.ui.theme.TextPrimary
import xyz.totemai.app.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    online: Boolean,
    signedIn: Boolean,
    displayName: String?,
    onOpenWorkspace: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenProfile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TotemLogo(Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("TOTEM", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("AI WORKSPACE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            StatusChip(text = if (online) "ONLINE" else "OFFLINE", active = online)
        }

        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(224.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Surface1, Violet.copy(alpha = 0.18f), Surface1)))
                .border(1.dp, Outline, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            Gyroscope(Modifier.align(Alignment.CenterEnd).size(190.dp))
            TotemLogo(Modifier.align(Alignment.CenterEnd).padding(end = 58.dp).size(58.dp))
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.64f)
                    .padding(start = 20.dp)
            ) {
                Text("YOUR AI\nWORKSPACE", style = MaterialTheme.typography.labelSmall, color = Lime, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("Think beyond.", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text("One place for your models and conversations.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = if (signedIn) "Welcome back${displayName?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}." else "Your AI workspace starts here.",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Chat with TOTEM, choose your inference mode and keep your account connected to the official service.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onOpenWorkspace,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)
        ) {
            Text(if (signedIn) "OPEN CHAT" else "SIGN IN", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))
        Text("QUICK ACCESS", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        ActionRow(
            icon = Icons.Default.Send,
            title = "Chat",
            subtitle = "Start a new conversation or open chat history"
        ) { onOpenWorkspace() }
        Spacer(Modifier.height(10.dp))
        ActionRow(
            icon = Icons.Default.Lock,
            title = "Wallet",
            subtitle = "View credits and connected wallet"
        ) { onOpenWallet() }
        Spacer(Modifier.height(10.dp))
        ActionRow(
            icon = Icons.Default.Person,
            title = if (displayName.isNullOrBlank()) "Profile" else displayName,
            subtitle = "Appearance, session and account settings"
        ) { onOpenProfile() }

        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Outline))
        Spacer(Modifier.height(14.dp))
        Text("Connected to the live TOTEM service.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(Modifier.height(28.dp))
    }
}
