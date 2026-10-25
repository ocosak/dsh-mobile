package com.dshmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.dshmobile.data.ChatMessage
import com.dshmobile.ui.ChatViewModel
import com.dshmobile.ui.theme.IosGlass

/**
 * Chat — modern iOS messaging look: bubbles aligned by role,
 * grouped messages (consecutive messages from the same role sit
 * closer, with a flattened joining corner), tool-call cards in
 * the same glass language, and approval as a bottom sheet-style
 * glass card (not a raw Android dialog).
 */
@Composable
fun ChatScreen(
    sessionId: String,
    sessionTitle: String,
    viewModel: ChatViewModel,
) {
    val state by viewModel.state.collectAsState()
    var input by remember { mutableStateOf("") }
    val dark = isSystemInDarkTheme()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = sessionTitle,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        GlassCard {
            IosTextField(
                value = state.model, onValueChange = viewModel::setModel,
                placeholder = "Model (optional, e.g. deepseek-chat)",
            )
            Text(
                "Model names are host-controlled. If the host rejects a name, the error is shown below.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        LazyColumn(modifier = Modifier.weight(1f).padding(top = 12.dp, bottom = 8.dp)) {
            itemsIndexed(state.messages, key = { _, m -> m.id }) { index, msg ->
                val isUser = msg.role == ChatMessage.Role.USER
                val groupedWithPrevious = index > 0 && state.messages[index - 1].role == msg.role
                val bubbleShape = when {
                    isUser && groupedWithPrevious -> RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
                    isUser -> RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
                    groupedWithPrevious -> RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
                    else -> RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
                }
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .padding(
                            start = if (isUser) 64.dp else 16.dp,
                            end = if (isUser) 16.dp else 64.dp,
                            top = if (groupedWithPrevious) 2.dp else 8.dp,
                            bottom = 2.dp,
                        ),
                    contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
                        Surface(
                            shape = bubbleShape,
                            color = if (isUser) MaterialTheme.colorScheme.primary
                            else IosGlass.surfaceStrong(dark),
                            shadowElevation = 2.dp,
                        ) {
                            Text(
                                text = msg.text.ifEmpty { if (state.streaming) "…" else "" },
                                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                        // Tool-call cards in the same glass language.
                        msg.toolCalls.forEach { tc ->
                            Surface(
                                modifier = Modifier.padding(top = 6.dp).widthIn(max = 280.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = IosGlass.surface(dark),
                                border = androidx.compose.foundation.BorderStroke(1.dp, IosGlass.border(dark)),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Tool: ${tc.name}", style = MaterialTheme.typography.labelLarge)
                                    Text(tc.status.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            if (state.messages.isEmpty()) {
                item {
                    EmptyState(
                        title = "Start the conversation",
                        body = "Send a message below. The host agent replies here, with tool calls shown as cards.",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        // Approval as an iOS-style bottom sheet/card, not a dialog.
        state.pendingApproval?.let { approval ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = IosGlass.surfaceStrong(dark),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(0.36f).heightIn(min = 5.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)))
                    }
                    Text("Approval required", style = MaterialTheme.typography.titleMedium)
                    Text("Tool: ${approval.toolName}", modifier = Modifier.padding(top = 4.dp))
                    Text(approval.description, style = MaterialTheme.typography.bodyMedium)
                    Row(modifier = Modifier.padding(top = 12.dp)) {
                        IosButton(text = "Approve", onClick = { viewModel.answerApproval(sessionId, true) })
                        OutlinedButton(
                            onClick = { viewModel.answerApproval(sessionId, false) },
                            modifier = Modifier.padding(start = 8.dp).heightIn(min = 44.dp),
                        ) { Text("Deny") }
                    }
                    Text(
                        "Approving lets the host agent run this action (it may read/edit files or run commands). Deny if you do not recognise it.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        state.message?.let { Text(text = it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
        // Floating glass composer bar.
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .shadow(12.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = IosGlass.surfaceStrong(dark),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { viewModel.attachNote(sessionId, "attachment") }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Attach") }
                IosTextField(value = input, onValueChange = { input = it }, placeholder = "Message", modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                IosButton(text = "Send", onClick = { viewModel.send(sessionId, input); input = "" }, enabled = !state.streaming)
            }
        }
    }
}
