package com.divinedeck.launcher.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme

/**
 * Superficie contenedora automotriz adaptada dinámicamente al tema activo de DIVINE DECK.
 */
@Composable
fun AutomotiveCard(
    modifier: Modifier = Modifier,
    borderColor: Color = DivineDeckTheme.palette.border,
    borderWidth: Dp = 1.dp,
    cornerRadius: Dp = 18.dp,
    backgroundColor: Color = DivineDeckTheme.palette.surface,
    contentPadding: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = backgroundColor,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x09FFFFFF),
                            Color(0x00000000)
                        )
                    )
                )
                .padding(contentPadding)
        ) {
            content()
        }
    }
}
