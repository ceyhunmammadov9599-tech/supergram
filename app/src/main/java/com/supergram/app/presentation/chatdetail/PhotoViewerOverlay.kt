package com.supergram.app.presentation.chatdetail

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import com.supergram.app.R
import com.supergram.app.domain.model.MediaFile
import kotlinx.coroutines.launch

/**
 * Full-screen photo viewer overlay with pinch-zoom/pan,
 * double-tap zoom (1x <-> 2.5x), swipe-to-dismiss at 1x zoom,
 * and a save-to-gallery action (MediaStore, permission-free on API 29+).
 */
@Composable
fun PhotoViewerOverlay(
    media: MediaFile,
    onDismiss: () -> Unit,
) {
    // Back gesture closes the viewer.
    BackHandler(onBack = onDismiss)

    val context = androidx.compose.ui.platform.LocalContext.current

    val scope = rememberCoroutineScope()

    // Animated zoom / pan state
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    val bitmap = remember(media.localPath) {
        media.localPath?.let { BitmapFactory.decodeFile(it) }?.asImageBitmap()
    }

    fun resetZoom() {
        scope.launch {
            scale.animateTo(1f)
            offsetX.animateTo(0f)
            offsetY.animateTo(0f)
        }
    }

    Surface(
        color = Color.Black,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 1) Pinch-zoom + pan
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale.value * zoom).coerceIn(1f, 6f)
                        scope.launch {
                            scale.snapTo(newScale)
                            if (newScale > 1f) {
                                offsetX.snapTo(offsetX.value + pan.x)
                                offsetY.snapTo(offsetY.value + pan.y)
                            }
                        }
                    }
                }
                // 2) Double-tap zoom toggle: 1x <-> 2.5x
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale.value > 1.01f) {
                                resetZoom()
                            } else {
                                scope.launch { scale.animateTo(2.5f) }
                            }
                        },
                    )
                }
                // 3) Swipe-to-dismiss (only at 1x zoom)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { _, drag ->
                            if (scale.value <= 1.01f) {
                                scope.launch { offsetY.snapTo(offsetY.value + drag.y) }
                            }
                        },
                        onDragEnd = {
                            if (scale.value <= 1.01f &&
                                (offsetY.value > 350f || offsetY.value < -350f)
                            ) {
                                onDismiss()
                            } else if (scale.value <= 1.01f) {
                                scope.launch { offsetY.animateTo(0f) }
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale.value,
                            scaleY = scale.value,
                            translationX = offsetX.value,
                            translationY = offsetY.value,
                        ),
                )
            } else {
                CircularProgressIndicator()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                )
            }
            IconButton(
                onClick = {
                    val path = media.localPath
                    val result = if (path != null) {
                        MediaSaver.saveToGallery(context, path)
                    } else {
                        "Photo not downloaded"
                    }
                    android.widget.Toast.makeText(
                        context,
                        result ?: "Saved to gallery",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_save),
                    contentDescription = "Save to gallery",
                    tint = Color.White,
                )
            }
        }
    }
}
