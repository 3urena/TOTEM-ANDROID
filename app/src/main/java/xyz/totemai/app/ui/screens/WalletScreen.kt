package xyz.totemai.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.totemai.app.SessionStore
import xyz.totemai.app.TotemApi
import xyz.totemai.app.TotemWalletBalance
import xyz.totemai.app.ui.theme.Ink
import xyz.totemai.app.ui.theme.InkElevated
import xyz.totemai.app.ui.theme.Lime
import xyz.totemai.app.ui.theme.Outline
import xyz.totemai.app.ui.theme.Surface1
import xyz.totemai.app.ui.theme.TextPrimary
import xyz.totemai.app.ui.theme.TextSecondary
import xyz.totemai.app.ui.theme.Violet
import java.text.NumberFormat
import java.util.Locale

@Composable
fun WalletScreen(
    store: SessionStore,
    signedIn: Boolean,
    onSignIn: () -> Unit,
    refreshTrigger: Int = 0
) {
    val api = remember(store) { TotemApi(store) }
    val scope = rememberCoroutineScope()
    var credits by remember { mutableStateOf<Long?>(null) }
    var wallet by remember { mutableStateOf<TotemWalletBalance?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showLinkInfo by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(signedIn, refreshKey, refreshTrigger) {
        if (!signedIn) {
            credits = null
            wallet = null
            loading = false
        } else {
            loading = true
            error = null
            try {
                credits = api.credits()
                wallet = api.walletBalance()
            } catch (e: Exception) {
                error = e.message ?: "Could not load wallet information."
            } finally {
                loading = false
            }
        }
    }

    if (showLinkInfo) {
        AlertDialog(
            onDismissRequest = { showLinkInfo = false },
            containerColor = InkElevated,
            title = { Text("Wallet linking is not available yet", color = TextPrimary) },
            text = {
                Text(
                    "The current TOTEM backend can read a wallet already attached to an account, and it can sign in with a wallet. It does not expose a safe endpoint to attach a new wallet to this existing account. Using wallet sign-in here could switch you to a different account, so this button will not do that.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showLinkInfo = false }) {
                    Text("Understood", color = Violet)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("WALLET", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Credits and connected wallet", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(
                onClick = { refreshKey++ },
                enabled = signedIn && !loading
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh wallet", tint = TextSecondary)
            }
        }

        Spacer(Modifier.height(18.dp))

        if (!signedIn) {
            WalletCard {
                Text("Sign in required", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Sign in to view your TOTEM credits and wallet information.", color = TextSecondary)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onSignIn,
                    colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sign in")
                }
            }
        } else {
            WalletCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CreditRing()
                    Column(Modifier.weight(1f)) {
                        Text("AVAILABLE CREDITS", color = TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = credits?.let { NumberFormat.getIntegerInstance(Locale.US).format(it) } ?: if (loading) "Loading…" else "—",
                            color = TextPrimary,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Live balance from your TOTEM account", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(Outline)
                    )
                    Text("CREDITS", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(Outline)
                    )
                }
                Text(
                    "The server returns your live credit count but no maximum quota, so the two-color ring is a visual balance indicator rather than a fabricated percentage.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            WalletCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Violet.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Violet)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("CRYPTO WALLET", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            if (wallet?.available == true) "Connected" else if (loading) "Checking connection…" else "No wallet linked",
                            color = if (wallet?.available == true) Lime else TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(
                        onClick = { showLinkInfo = true },
                        enabled = signedIn && !loading,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = Violet, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Link", color = Violet)
                    }
                }

                Spacer(Modifier.height(16.dp))

                when {
                    loading && wallet == null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Violet, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Loading wallet status…", color = TextSecondary)
                        }
                    }
                    wallet?.available == true -> {
                        Text(
                            text = "${wallet?.amount ?: "—"} ${wallet?.symbol ?: ""}",
                            color = TextPrimary,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = wallet?.chainId?.let { "Network chain ID: $it" } ?: "On-chain balance",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    wallet?.noWallet == true -> {
                        Text("No wallet is linked to this TOTEM account yet.", color = TextSecondary)
                    }
                    wallet?.error != null -> {
                        Text(wallet?.error.orEmpty(), color = Color(0xFFFF8B8B))
                    }
                    else -> {
                        Text("Wallet balance is not available from the hosted server.", color = TextSecondary)
                    }
                }
            }

            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(error.orEmpty(), color = Color(0xFFFF8B8B), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WalletCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .border(1.dp, Outline, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        content()
    }
}

@Composable
private fun CreditRing() {
    Box(
        modifier = Modifier.size(104.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            drawArc(
                color = Outline,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = Lime,
                startAngle = -90f,
                sweepAngle = 205f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = Violet,
                startAngle = 120f,
                sweepAngle = 130f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("USAGE", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            Text(
                text = "—%",
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
