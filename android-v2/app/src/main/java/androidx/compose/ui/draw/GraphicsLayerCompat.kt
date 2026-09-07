package androidx.compose.ui.draw

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer as composeGraphicsLayer

fun Modifier.graphicsLayer(block: GraphicsLayerScope.() -> Unit): Modifier =
    this.composeGraphicsLayer(block)
