package com.supergram.app.presentation.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.supergram.app.domain.model.matchRanges

/**
 * Builds an [AnnotatedString] with the query matches emphasized in bold and
 * tinted with the primary color (search highlighting).
 */
@Composable
fun highlightedText(text: String, query: String): AnnotatedString {
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val highlightForeground = MaterialTheme.colorScheme.primary
    return remember(text, query, highlightColor, highlightForeground) {
        buildAnnotatedString {
            append(text)
            matchRanges(text, query).forEach { range ->
                addStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = highlightForeground,
                        background = highlightColor,
                    ),
                    range.first,
                    range.last + 1,
                )
            }
        }
    }
}
