package com.sidequest.app

import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Compatibility overload for modifier-first clickable surfaces used by the V11 UI. */
@Composable
internal fun Surface(
    modifier: Modifier,
    onClick: () -> Unit,
    shape: Shape = RectangleShape,
    color: Color = Color.Unspecified,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    MaterialSurface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = color,
        shadowElevation = shadowElevation,
        content = content
    )
}
