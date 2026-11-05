package xyz.totemai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import xyz.totemai.app.Intents
import xyz.totemai.app.SessionStore
import xyz.totemai.app.TotemConfig
import xyz.totemai.app.ui.components.ActionRow
import xyz.totemai.app.ui.components.SectionCard
import xyz.totemai.app.ui.components.TotemLogo
import xyz.totemai.app.ui.theme.Danger
import xyz.totemai.app.ui.theme.Ink
import xyz.totemai.app.ui.theme.Lime
import xyz.totemai.app.ui.theme.TextPrimary
import xyz.totemai.app.ui.theme.TextSecondary

@Composable
fun ProfileScreen(
    store: SessionStore,
    signedIn: Boolean,
    displayName: String?,
    onOpenWorkspace: () -> Unit,
    onOpenWallet: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(22.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TotemLogo(Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = "PROFILE",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }

        Spacer(Modifier.height(24.dp))

        SectionCard(title = "Account") {
            Text(
                text = if (signedIn) (displayName ?: "Signed in") else "Not signed in",
                style = MaterialTheme.typography.headlineMedium,
                color = if (signedIn) TextPrimary else TextSecondary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (signedIn) {
                    "The app mirrors the hosted session into encrypted native storage. The hosted WebView also keeps the website's own localStorage session copy."
                } else {
                    "Sign in through the hosted login screen to use the workspace."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onOpenWorkspace,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Lime,
                    contentColor = Ink
                )
            ) {
                Text(
                    text = if (signedIn) "OPEN WORKSPACE" else "SIGN IN",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Session storage") {
            Text(
                text = if (store.persisted) {
                    "Encrypted with an AES-256 keystore key. Survives app restarts."
                } else {
                    "Encrypted storage is unavailable, so this session is memory-only and will not survive an app restart."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (store.persisted) TextSecondary else Danger
            )
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Shell") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Version", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text(TotemConfig.APP_VERSION, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Host", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text("beta.totemai.xyz", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }

        Spacer(Modifier.height(12.dp))

        ActionRow(
            icon = Icons.Default.Info,
            title = "TOTEM website",
            subtitle = "totemai.xyz"
        ) { Intents.openUrl(context, TotemConfig.SITE_URL) }

        Spacer(Modifier.height(10.dp))

        ActionRow(
            icon = Icons.Default.Lock,
            title = "Wallet connections",
            subtitle = "View credits and connected wallet"
        ) { onOpenWallet() }

        Spacer(Modifier.height(10.dp))

        ActionRow(
            icon = Icons.Default.Send,
            title = "Source repository",
            subtitle = "github.com/fred-terzi/totem-llm"
        ) { Intents.openUrl(context, TotemConfig.REPO_URL) }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onSignOut,
            enabled = signedIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Danger.copy(alpha = 0.16f),
                contentColor = Danger,
                disabledContainerColor = Danger.copy(alpha = 0.08f),
                disabledContentColor = Danger.copy(alpha = 0.5f)
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text("SIGN OUT", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
