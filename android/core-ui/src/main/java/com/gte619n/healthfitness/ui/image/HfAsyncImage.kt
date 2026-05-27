package com.gte619n.healthfitness.ui.image

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.gte619n.healthfitness.ui.theme.Hf

/**
 * Thin wrapper around Coil's `AsyncImage` that paints a themed
 * placeholder color while loading and inherits the singleton `ImageLoader`
 * built by `HealthFitnessApp` (so crossfade, disk cache size, and the
 * shared OkHttp client are configured once).
 */
@Composable
fun HfAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color = Hf.colors.canvasMuted,
) {
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.background(placeholderColor),
        contentScale = contentScale,
    )
}
