package com.supergram.app.presentation.chatdetail

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.supergram.app.R
import com.supergram.app.domain.model.MediaFile
import com.supergram.app.domain.model.MediaKind
import com.supergram.app.domain.model.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Material 3 chat detail screen: live message stream, media previews with a
 * download flow, and an input bar dispatching plain-text messages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chatTitle: String,
    viewModel: ChatDetailViewModel,
    onBack: () -> Unit,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Newest at the bottom; auto-scroll when a message arrives.
    val displayList = remember(messages) { messages.asReversed() }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(chatTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.imePadding()) {
                error?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Message") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                    )
                    IconButton(
                        onClick = {
                            val text = input
                            input = ""
                            viewModel.send(text)
                        },
                        enabled = input.isNotBlank() && !sending,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (input.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No messages yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(displayList, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onDownload = viewModel::downloadFile,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, onDownload: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isOutgoing) {
            Arrangement.End
        } else {
            Arrangement.Start
        },
    ) {
        val shape = RoundedCornerShape(
            topStart = 12.dp,
            topEnd = 12.dp,
            bottomStart = if (message.isOutgoing) 12.dp else 2.dp,
            bottomEnd = if (message.isOutgoing) 2.dp else 12.dp,
        )
        Surface(
            color = if (message.isOutgoing) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = shape,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                message.media?.let { media -> MediaPreview(media, isOutgoing = message.isOutgoing, onDownload = onDownload) }
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.isOutgoing) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                Text(
                    text = formatTime(message.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isOutgoing) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

/** Photo / document preview with download-state placeholders. */
@Composable
private fun MediaPreview(media: MediaFile, isOutgoing: Boolean, onDownload: (Int) -> Unit) {
    when (media.kind) {
        MediaKind.PHOTO -> PhotoPreview(media, onDownload)
        MediaKind.DOCUMENT -> DocumentPreview(media, onDownload, isOutgoing = isOutgoing)
    }
}

@Composable
private fun PhotoPreview(media: MediaFile, onDownload: (Int) -> Unit) {
    val bitmap = remember(media.localPath) {
        media.localPath?.let { BitmapFactory.decodeFile(it) }?.asImageBitmap()
    }
    if (bitmap != null && media.isDownloaded) {
        // Downloaded photo: render the decoded image.
        Image(
            bitmap = bitmap,
            contentDescription = "Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(vertical = 2.dp)
                .fillMaxWidth()
                .height(200.dp),
        )
    } else {
        // Downloading / not started: placeholder with live progress.
        Box(
            modifier = Modifier
                .padding(vertical = 2.dp)
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                    RoundedCornerShape(8.dp),
                )
                .clickable { onDownload(media.fileId) },
            contentAlignment = Alignment.Center,
        ) {
            if (media.isDownloadingActive) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        progress = { media.progress },
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = "${(media.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_download),
                    contentDescription = "Tap to download photo",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DocumentPreview(media: MediaFile, onDownload: (Int) -> Unit, isOutgoing: Boolean) {
    Row(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .fillMaxWidth()
            .clickable(enabled = !media.isDownloaded) { onDownload(media.fileId) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_document),
            contentDescription = "Document",
            tint = if (isOutgoing) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
        )
        Column(
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
        ) {
            Text(
                text = media.fileName ?: "Document",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (media.isDownloaded) {
                Text(
                    text = formatSize(media.expectedSize),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        when {
            media.isDownloaded -> Unit
            media.isDownloadingActive -> CircularProgressIndicator(
                progress = { media.progress },
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
            else -> Icon(
                painter = painterResource(R.drawable.ic_download),
                contentDescription = "Download document",
                tint = if (isOutgoing) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatTime(epochSeconds: Long): String {
    val format = SimpleDateFormat("HH:mm", Locale.getDefault())
    return format.format(Date(epochSeconds * 1000))
}
