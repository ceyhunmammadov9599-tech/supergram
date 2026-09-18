package com.supergram.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.supergram.app.core.telegram.TelegramClientManager
import com.supergram.app.domain.model.AuthState
import com.supergram.app.presentation.auth.AuthScreen
import com.supergram.app.presentation.auth.AuthViewModel
import com.supergram.app.presentation.chatdetail.ChatDetailScreen
import com.supergram.app.presentation.chatdetail.ChatDetailViewModel
import com.supergram.app.presentation.chatlist.ChatListScreen
import com.supergram.app.presentation.chatlist.ChatListViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SuperGramTheme {
                SuperGramApp()
            }
        }
    }
}

/**
 * Lightweight manual navigation (no navigation framework): the TDLib auth state
 * decides between the auth flow and the chat list; a selected chat id opens the
 * chat detail screen.
 */
@Composable
fun SuperGramApp() {
    val authState by TelegramClientManager.authState.collectAsState()
    var selectedChatId by remember { mutableStateOf<Long?>(null) }
    val chatListViewModel = remember { ChatListViewModel.create() }

    val currentChat = selectedChatId
    when {
        // ---- Chat detail (Phase 3) ----
        currentChat != null -> {
            val chat = chatListViewModel.chats.value.firstOrNull { it.id == currentChat }
            ChatDetailScreen(
                chatTitle = chat?.title ?: "Chat",
                viewModel = remember(currentChat) { ChatDetailViewModel.create(currentChat) },
                onBack = { selectedChatId = null },
            )
        }
        // ---- Chat list (Phase 2) ----
        authState is AuthState.Ready -> {
            ChatListScreen(
                viewModel = chatListViewModel,
                onChatClick = { chatId -> selectedChatId = chatId },
            )
        }
        // ---- Auth flow (Phase 1) ----
        else -> {
            AuthScreen(AuthViewModel.create())
        }
    }
}

@Composable
fun SuperGramTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content,
    )
}
