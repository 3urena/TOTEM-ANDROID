package xyz.totemai.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.totemai.app.data.CreditsSnapshot
import xyz.totemai.app.data.WalletSnapshot
import xyz.totemai.app.ui.theme.Ink
import xyz.totemai.app.ui.theme.Lime
import xyz.totemai.app.ui.theme.Outline
import xyz.totemai.app.ui.theme.Surface1
import xyz.totemai.app.ui.theme.TextPrimary
import xyz.totemai.app.ui.theme.TextSecondary

@Composable
fun WalletScreen(
    credits: CreditsSnapshot?,
    wallet: WalletSnapshot?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpenWalletConnection: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Ink).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("WALLET", style = MaterialTheme.typography.labelSmall, color = Lime)
                Text("Your assets & credits", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary)
            }
        }

        Column(
            Modifier.fillMaxWidth().background(Surface1, RoundedCornerShape(22.dp))
                .border(1.dp, Outline, RoundedCornerShape(22.dp)).padding(20.dp)
        ) {
            Text("TOTEM CREDITS", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            Text(
                credits?.balance?.let { "%,d".format(it) } ?: if (loading) "Loading…" else "Unavailable",
                style = MaterialTheme.typography.displayMedium,
                color = Lime
            )
            Text("Live balance from your TOTEM account", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }

        Column(
            Modifier.fillMaxWidth().background(Surface1, RoundedCornerShape(22.dp))
                .border(1.dp, Outline, RoundedCornerShape(22.dp)).padding(20.dp)
        ) {
            Text("$" + "TOTEM TOKEN", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            Text(
                wallet?.amount?.let { "$it ${wallet.symbol}" } ?: when {
                    loading -> "Loading…"
                    wallet?.noWallet == true -> "No wallet linked"
                    wallet?.available == false -> "Balance unavailable"
                    else -> "—"
                },
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                wallet?.error ?: "Read-only balance supplied by the TOTEM server. No private keys are stored in this app.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onOpenWalletConnection,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text("  Manage wallet connection")
            }
        }

        if (!error.isNullOrBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Text(
            "Payments and wallet signing are not performed inside this native screen yet. This screen never asks for a seed phrase or private key.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}
