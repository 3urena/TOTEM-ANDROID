package xyz.totemai.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.totemai.app.data.ApiMessage
import xyz.totemai.app.data.ApiWorkspace
import xyz.totemai.app.ui.theme.Ink
import xyz.totemai.app.ui.theme.Lime
import xyz.totemai.app.ui.theme.Outline
import xyz.totemai.app.ui.theme.Surface1
import xyz.totemai.app.ui.theme.TextPrimary
import xyz.totemai.app.ui.theme.TextSecondary

@Composable
fun WorkspaceScreen(
    workspaces: List<ApiWorkspace>,
    selectedWorkspace: ApiWorkspace?,
    messages: List<ApiMessage>,
    draft: String,
    loading: Boolean,
    sending: Boolean,
    error: String?,
    onSelectWorkspace: (ApiWorkspace) -> Unit,
    onRefresh: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onNewChat: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("WORKSPACE", style = MaterialTheme.typography.labelSmall, color = Lime)
                Text(selectedWorkspace?.name ?: "Your conversations", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary)
            }
            IconButton(onClick = onNewChat, enabled = selectedWorkspace != null) {
                Icon(Icons.Default.Add, contentDescription = "New chat", tint = Lime)
            }
        }

        if (workspaces.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(54.dp),
                horizontalAlignment = Alignment.Start
            ) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        workspaces.forEach { workspace ->
                            val selected = workspace.slug == selectedWorkspace?.slug
                            Box(
                                modifier = Modifier
                                    .background(if (selected) Lime.copy(alpha = 0.14f) else Surface1, RoundedCornerShape(18.dp))
                                    .border(1.dp, if (selected) Lime.copy(alpha = 0.55f) else Outline, RoundedCornerShape(18.dp))
                                    .clickable { onSelectWorkspace(workspace) }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            ) {
                                Text(workspace.name, color = if (selected) Lime else TextSecondary, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Lime)
            }
        } else if (selectedWorkspace == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No workspace is available for this account.", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 42.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("TOTEM", style = MaterialTheme.typography.headlineLarge, color = Lime)
                            Spacer(Modifier.height(8.dp))
                            Text("What would you like to work on?", color = TextSecondary)
                        }
                    }
                }
                items(messages, key = { it.id }) { message ->
                    val user = message.role == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .background(if (user) Lime.copy(alpha = 0.12f) else Surface1, RoundedCornerShape(18.dp))
                                .border(1.dp, if (user) Lime.copy(alpha = 0.24f) else Outline, RoundedCornerShape(18.dp))
                                .padding(14.dp)
                        ) {
                            Text(if (user) "YOU" else "TOTEM", style = MaterialTheme.typography.labelSmall, color = if (user) Lime else TextSecondary)
                            Spacer(Modifier.height(6.dp))
                            Text(message.content, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        }
                    }
                }
                if (!error.isNullOrBlank()) {
                    item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message TOTEM…", color = TextSecondary) },
                    shape = RoundedCornerShape(22.dp),
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Lime,
                        unfocusedBorderColor = Outline,
                        cursorColor = Lime
                    )
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onSend,
                    enabled = draft.isNotBlank() && !sending,
                    modifier = Modifier.size(52.dp).background(Lime, CircleShape)
                ) {
                    if (sending) CircularProgressIndicator(Modifier.size(20.dp), color = Ink, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Send, contentDescription = "Send", tint = Ink)
                }
            }
        }
    }
}
