package com.supergram.app.presentation.chatdetail

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.supergram.app.R
import com.supergram.app.core.audio.VoiceNotePlayer
import com.supergram.app.domain.model.MediaFile
import com.supergram.app.domain.model.MediaKind
import com.supergram.app.domain.model.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Material 3 chat detail screen: live message stream, media previews with a
 * download flow, voice note playback, and a full-screen photo viewer.
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
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Full-screen photo viewer target.
    var viewerMedia by remember { mutableStateOf<MediaFile?>(null) }

    // Newest at the bottom; auto-scroll when a message arrives.
    val displayList = remember(messages) { messages.asReversed() }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Box(Modifier.fillMaxSize()) {
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
                            voiceState = voiceState,
                            onDownload = viewModel::downloadFile,
                            onToggleVoice = viewModel::toggleVoice,
                            onOpenPhoto = { viewerMedia = it },
                        )
                    }
                }
            }
        }

        // Full-screen photo viewer overlay.
        viewerMedia?.let { media ->
            PhotoViewerOverlay(
                media = media,
                onDismiss = { viewerMedia = null },
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    voiceState: VoiceNotePlayer.PlaybackState,
    onDownload: (Int) -> Unit,
    onToggleVoice: (MediaFile) -> Unit,
    onOpenPhoto: (MediaFile) -> Unit,
) {
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
                message.media?.let { media ->
                    MediaPreview(
                        media = media,
                        isOutgoing = message.isOutgoing,
                        voiceState = voiceState,
                        onDownload = onDownload,
                        onToggleVoice = onToggleVoice,
                        onOpenPhoto = onOpenPhoto,
                    )
                }
                // Voice notes render their own bubble; skip the snippet text.
                if (message.text.isNotBlank() && message.media?.kind != MediaKind.VOICE) {
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

/** Photo / document / voice preview dispatch with download-state placeholders. */
@Composable
private fun MediaPreview(
    media: MediaFile,
    isOutgoing: Boolean,
    voiceState: VoiceNotePlayer.PlaybackState,
    onDownload: (Int) -> Unit,
    onToggleVoice: (MediaFile) -> Unit,
    onOpenPhoto: (MediaFile) -> Unit,
) {
    when (media.kind) {
        MediaKind.PHOTO -> PhotoPreview(media, onDownload, onOpenPhoto)
        MediaKind.DOCUMENT -> DocumentPreview(media, onDownload, isOutgoing = isOutgoing)
        MediaKind.VOICE -> VoiceBubble(media, voiceState, isOutgoing = isOutgoing, onToggle = onToggleVoice)
    }
}

@Composable
private fun PhotoPreview(
    media: MediaFile,
    onDownload: (Int) -> Unit,
    onOpenPhoto: (MediaFile) -> Unit,
) {
    val bitmap = remember(media.localPath) {
        media.localPath?.let { BitmapFactory.decodeFile(it) }?.asImageBitmap()
    }
    if (bitmap != null && media.isDownloaded) {
        // Downloaded photo: tap opens the full-screen zoomable viewer.
        Image(
            bitmap = bitmap,
            contentDescription = "Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(vertical = 2.dp)
                .fillMaxWidth()
                .height(200.dp)
                .clickable { onOpenPhoto(media) },
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

/** Custom voice note bubble: play/pause toggle, duration label, progress. */
@Composable
private fun VoiceBubble(
    media: MediaFile,
    voiceState: VoiceNotePlayer.PlaybackState,
    isOutgoing: Boolean,
    onToggle: (MediaFile) -> Unit,
) {
    val isThisPlaying = voiceState.fileId == media.fileId && voiceState.isPlaying
    val durationMs = media.durationSeconds?.times(1000) ?: voiceState.durationMs
    val positionMs = if (voiceState.fileId == media.fileId) voiceState.positionMs else 0
    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f

    Row(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .fillMaxWidth()
            .clickable { onToggle(media) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Play / pause toggle
        Surface(
            shape = CircleShape,
            color = if (isOutgoing) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = if (isThisPlaying) {
                    painterResource(R.drawable.ic_pause)
                } else {
                    painterResource(R.drawable.ic_play)
                },
                contentDescription = if (isThisPlaying) "Pause" else "Play",
                tint = if (isOutgoing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
                modifier = Modifier.size(24.dp),
            )
        }

        // Duration + waveform progress
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = formatDuration(
                    if (isThisPlaying) (positionMs / 1000) else (media.durationSeconds ?: durationMs / 1000)
                ),
                style = MaterialTheme.typography.labelMedium,
            )
            val playedColor = if (isOutgoing) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.primary
            }
            val unplayedColor = if (isOutgoing) {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            val bars = media.waveform
            if (bars != null) {
                WaveformProgress(
                    bars = bars,
                    progress = progress,
                    playedColor = playedColor,
                    unplayedColor = unplayedColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                )
            } else {
                // Fallback when the waveform payload is unavailable.
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Custom waveform visualizer: dynamic amplitude bars split into
 * played (position-progress) and unplayed portions.
 */
@Composable
private fun WaveformProgress(
    bars: List<Float>,
    progress: Float,
    playedColor: Color,
    unplayedColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (bars.isEmpty()) return@Canvas
        val gap = 2.dp.toPx()
        val barWidth = ((size.width - gap * (bars.size - 1)) / bars.size)
            .coerceAtLeast(1f)
        val playedX = size.width * progress
        bars.forEachIndexed { index, amplitude ->
            val x = index * (barWidth + gap)
            val barHeight = size.height * amplitude.coerceIn(0.05f, 1f)
            val color = if (x + barWidth / 2f <= playedX) playedColor else unplayedColor
            drawRoundRect(
                color = color,
                topLeft = Offset(x, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
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
