package xyz.totemai.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import xyz.totemai.app.ui.components.ActionRow
import xyz.totemai.app.ui.components.Gyroscope
import xyz.totemai.app.ui.components.StatusChip
import xyz.totemai.app.ui.components.TotemLogo
import xyz.totemai.app.ui.theme.Ink
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
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TotemLogo(Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = "TOTEM",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }

        Spacer(Modifier.height(34.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .background(Surface1, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Gyroscope(Modifier.size(190.dp))
            TotemLogo(Modifier.size(64.dp))
        }

        Spacer(Modifier.height(26.dp))

        Text(
            text = "Your workspace,\none tap away.",
            style = MaterialTheme.typography.displayLarge,
            color = TextPrimary
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "TOTEM MOBILE connects to the live hosted platform. Your account, models, credits, payments and tools remain on TOTEM servers while this app provides native navigation, secure session storage and a mobile-first shell.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(text = if (online) "ONLINE" else "OFFLINE", active = online)
        }

        if (!displayName.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Welcome back, $displayName",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onOpenWorkspace,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Lime,
                contentColor = Ink
            )
        ) {
            Text(
                text = if (signedIn) "OPEN CHAT" else "SIGN IN",
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(Modifier.height(26.dp))

        ActionRow(
            icon = Icons.Default.Send,
            title = "Chat",
            subtitle = "Start a new chat or open your saved conversations"
        ) { onOpenWorkspace() }

        Spacer(Modifier.height(10.dp))

        ActionRow(
            icon = Icons.Default.Lock,
            title = "Wallet connections",
            subtitle = "Connect an external wallet through the hosted flow"
        ) { onOpenWallet() }

        Spacer(Modifier.height(10.dp))

        ActionRow(
            icon = Icons.Default.Person,
            title = if (displayName.isNullOrBlank()) "Profile" else displayName,
            subtitle = "Session, storage and account"
        ) { onOpenProfile() }

        Spacer(Modifier.height(28.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Outline)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Hosted at beta.totemai.xyz — no local AI backend.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(32.dp))
    }
}
