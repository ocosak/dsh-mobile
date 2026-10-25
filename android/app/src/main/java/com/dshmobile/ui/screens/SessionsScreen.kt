package com.dshmobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dshmobile.ui.SessionsViewModel

/**
 * Sessions — iOS inset grouped list in glass cards.
 * Swipe a row to delete (iOS-style gesture, with a visible Delete
 * action as well, so the gesture is discoverable and reversible
 * in intent: deletion is confirmed by the swipe completing).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    viewModel: SessionsViewModel,
    onOpenSession: (String, String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var newTitle by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize()) {
        LargeTitle("Sessions", subtitle = "Your agent conversations")
        GlassCard {
            IosTextField(value = newTitle, onValueChange = { newTitle = it }, placeholder = "New session title")
            IosButton(text = "Create session", onClick = { viewModel.create(newTitle); newTitle = "" }, modifier = Modifier.padding(top = 12.dp))
        }
        if (state.busy && state.sessions.isEmpty()) {
            LoadingState("Loading sessions…")
        }
        state.message?.let { ErrorState(it) }
        if (!state.busy && state.sessions.isEmpty() && state.message == null) {
            EmptyState(
                title = "No sessions yet",
                body = "Create one above, then open it to chat with your dsh host.",
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 12.dp, bottom = 8.dp)) {
            items(state.sessions, key = { it.id }) { session ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            viewModel.delete(session.id); true
                        } else false
                    }
                )
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                            Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                        }
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    GlassCard {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.weight(1f)
                                    .clickable { onOpenSession(session.id, session.title) }
                                    .padding(vertical = 4.dp),
                            ) {
                                Text(session.title, style = MaterialTheme.typography.bodyLarge)
                                session.model?.let {
                                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("Swipe left to delete  ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
