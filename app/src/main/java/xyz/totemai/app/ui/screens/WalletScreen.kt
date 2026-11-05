package xyz.totemai.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import xyz.totemai.app.SessionStore
import xyz.totemai.app.api.TotemApi
import xyz.totemai.app.ui.theme.Danger
import xyz.totemai.app.ui.theme.Ink
import xyz.totemai.app.ui.theme.InkElevated
import xyz.totemai.app.ui.theme.Lime
import xyz.totemai.app.ui.theme.Outline
import xyz.totemai.app.ui.theme.Surface1
import xyz.totemai.app.ui.theme.TextPrimary
import xyz.totemai.app.ui.theme.TextSecondary
import xyz.totemai.app.ui.theme.Violet
import kotlin.math.abs
import kotlin.math.roundToInt

private data class CreditsActivity(
    val balance: String,
    val added: Double,
    val used: Double,
    val transactionCount: Int
) {
    val totalFlow: Double get() = added + used
    val addedPercent: Int
        get() = if (totalFlow <= 0.0) 0 else (added / totalFlow * 100).roundToInt()
    val usedPercent: Int get() = if (totalFlow <= 0.0) 0 else 100 - addedPercent
}

@Composable
fun WalletScreen(
    store: SessionStore,
    signedIn: Boolean,
    sessionVersion: Int,
    onOpenChat: () -> Unit
) {
    val api = remember(store) { TotemApi(store) }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var credits by remember { mutableStateOf<CreditsActivity?>(null) }
    var wallet by remember { mutableStateOf<JSONObject?>(null) }
    var showLinkInfo by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        error = null
        try {
            val creditJson = api.credits()
            val transactions = creditJson.optJSONArray("transactions")
            var added = 0.0
            var used = 0.0
            if (transactions != null) {
                for (index in 0 until transactions.length()) {
                    val item = transactions.optJSONObject(index) ?: continue
                    val amount = abs(item.optDouble("amount", 0.0))
                    when (item.optString("type").lowercase()) {
                        "credit", "topup", "grant" -> added += amount
                        else -> used += amount
                    }
                }
            }
            val balanceValue = creditJson.opt("balance")
                ?: creditJson.opt("credits")
            val balanceText = when (balanceValue) {
                null, JSONObject.NULL -> "—"
                is Number -> formatCredits(balanceValue.toDouble())
                else -> balanceValue.toString()
            }
            credits = CreditsActivity(
                balance = balanceText,
                added = added,
                used = used,
                transactionCount = transactions?.length() ?: 0
            )
            wallet = api.walletBalance()
        } catch (exception: Exception) {
            error = exception.message ?: "Could not load wallet information."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(signedIn, sessionVersion) {
        if (signedIn) refresh() else {
            credits = null
            wallet = null
            error = null
        }
    }

    if (showLinkInfo) {
        AlertDialog(
            onDismissRequest = { showLinkInfo = false },
            containerColor = InkElevated,
            title = { Text("Wallet linking is not available yet", color = TextPrimary) },
            text = {
                Text(
                    "The current Hosted API supports signing in with a wallet. It does not provide an endpoint to attach a wallet to your existing email/username account. Wallet sign-in can switch to a different account or create a new one, so this button will not do that without your approval.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showLinkInfo = false }) {
                    Text("UNDERSTOOD", color = Violet)
                }
            }
        )
    }

    if (!signedIn) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("WALLET", color = TextPrimary, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))
            Text("Sign in to view your Credits and wallet balance.", color = TextSecondary)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onOpenChat,
                colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color.White)
            ) { Text("SIGN IN") }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("WALLET", color = TextPrimary, style = MaterialTheme.typography.headlineMedium)
                Text("Credits and on-chain balance", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh wallet", tint = Violet)
            }
        }

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Surface1)
        ) {
            if (loading && credits == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Violet)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AVAILABLE CREDITS", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            credits?.balance ?: "—",
                            color = TextPrimary,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text("Credits", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(Lime, RoundedCornerShape(50)))
                            Spacer(Modifier.size(6.dp))
                            Text("Added", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.size(12.dp))
                            Box(Modifier.size(8.dp).background(Violet, RoundedCornerShape(50)))
                            Spacer(Modifier.size(6.dp))
                            Text("Used", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    CreditActivityRing(activity = credits)
                }
            }
        }

        if (credits?.transactionCount ?: 0 > 0) {
            Text(
                "RECENT CREDIT ACTIVITY · LAST ${credits?.transactionCount ?: 0} TRANSACTIONS",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp, top = 9.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Surface1)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("CRYPTO WALLET", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                val balance = wallet
                when {
                    balance == null && loading -> Text("Loading wallet…", color = TextSecondary)
                    balance?.optBoolean("available") == true -> {
                        Text(
                            balance.optString("symbol", "$TOTEM"),
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            balance.optString("amount", "—"),
                            color = TextPrimary,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            if (balance.optInt("chainId") == 8453) "Base network" else "Chain ID ${balance.optInt("chainId")}",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    balance?.optBoolean("noWallet") == true -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("NO WALLET LINKED", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                                Text("Connect a crypto wallet to view its $TOTEM balance.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(
                                onClick = { showLinkInfo = true },
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Violet),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Violet)
                            ) {
                                Text("LINK")
                            }
                        }
                    }
                    balance != null -> {
                        Text(
                            balance.optString("error").ifBlank { "Wallet balance is not available." },
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { scope.launch { refresh() } }) {
                            Text("RETRY", color = Violet)
                        }
                    }
                }
            }
        }

        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Surface(
                color = Danger.copy(alpha = 0.10f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    error.orEmpty(),
                    color = Danger,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun CreditActivityRing(activity: CreditsActivity?) {
    val added = activity?.added ?: 0.0
    val used = activity?.used ?: 0.0
    val total = added + used
    val addedSweep = if (total > 0.0) (added / total * 360f).toFloat() else 0f
    val usedSweep = if (total > 0.0) 360f - addedSweep else 0f

    Box(modifier = Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = Outline,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            if (total > 0.0) {
                drawArc(
                    color = Lime,
                    startAngle = -90f,
                    sweepAngle = addedSweep,
                    useCenter = false,
                    style = stroke
                )
                drawArc(
                    color = Violet,
                    startAngle = -90f + addedSweep,
                    sweepAngle = usedSweep,
                    useCenter = false,
                    style = stroke
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (total > 0.0) "${activity?.addedPercent ?: 0}%" else "—",
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge
            )
            Text("ADDED", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatCredits(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format("%.2f", value)
