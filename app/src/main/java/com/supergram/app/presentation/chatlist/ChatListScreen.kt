package com.supergram.app.presentation.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import com.supergram.app.domain.model.Chat
import com.supergram.app.domain.model.ChatCategory
import com.supergram.app.domain.model.filterByCategory
import com.supergram.app.presentation.common.SearchResultRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Material 3 chat list screen: active chats with last message snippets and
 * unread counters, updated live through the ViewModel state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onChatClick: (Long, Long?) -> Unit,
) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val searchActive by viewModel.searchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    // Category filter tabs (All / Direct / Groups / Channels / Bots).
    val categories = listOf(
        null,                       // All
        ChatCategory.DIRECT,
        ChatCategory.GROUP,
        ChatCategory.CHANNEL,
        ChatCategory.BOT,
    )
    val labels = listOf("All", "Direct", "Groups", "Channels", "Bots")
    var selectedTab by remember { mutableIntStateOf(0) }

    // Reactive: re-evaluates on every live chats update.
    val visibleChats = remember(chats, selectedTab) {
        chats.filterByCategory(categories.getOrNull(selectedTab))
    }

    Scaffold(
        topBar = {
            if (searchActive) {
                // Global search mode: query field replaces the app bar.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, start = 4.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = viewModel::closeSearch) {
                        Icon(Icons.Default.Close, contentDescription = "Close search")
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text("Search messages") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                TopAppBar(
                    title = { Text("SuperGram", fontWeight = FontWeight.Bold) },
                    actions = {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).padding(end = 16.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        IconButton(onClick = viewModel::toggleSearch) {
                            Icon(Icons.Default.Search, contentDescription = "Search messages")
                        }
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    },
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (searchActive) {
                // ---- Global search results ----
                if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (searchQuery.isBlank()) "Type to search" else "No matches",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                    ) {
                        items(searchResults, key = { it.chatId.toString() + "_" + it.messageId }) { result ->
                            SearchResultRow(
                                result = result,
                                query = searchQuery,
                                showChatTitle = true,
                                onClick = {
                                    viewModel.closeSearch()
                                    onChatClick(result.chatId, result.messageId)
                                },
                            )
                        }
                    }
                }
            }
            if (!searchActive) {
                ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
            ) {
                labels.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            val count = categories.getOrNull(index)
                                ?.let { c -> chats.count { it.category == c } }
                                ?: chats.size
                            Text("$label ($count)")
                        },
                    )
                }
            }

            error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (visibleChats.isEmpty() && !loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No chats yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visibleChats, key = { it.id }) { chat ->
                        ChatRow(chat = chat, onClick = { onChatClick(chat.id, null) })
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: Chat, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar placeholder with initial
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = chat.title.take(1).ifBlank { "?" },
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = chat.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = chat.lastMessageSnippet ?: "No messages",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            chat.lastMessageDate?.let { seconds ->
                Text(
                    text = formatTime(seconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (chat.unreadCount > 0) {
                Badge { Text(chat.unreadCount.toString()) }
            }
        }
    }
}

private fun formatTime(epochSeconds: Long): String {
    val format = SimpleDateFormat("HH:mm", Locale.getDefault())
    return format.format(Date(epochSeconds * 1000))
}
