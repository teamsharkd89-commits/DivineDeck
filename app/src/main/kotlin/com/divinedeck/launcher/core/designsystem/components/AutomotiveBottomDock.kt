package com.divinedeck.launcher.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme
import com.divinedeck.launcher.core.navigation.DivineDeckRoute

/**
 * Dock de navegación minimalista (Zona 4 - Fase 02.1).
 * Contiene únicamente iconos para los 7 apartados de DIVINE DECK, sin etiquetas de texto
 * ni botones rectangulares voluminosos, con indicador discreto y transiciones suaves.
 *
 * Dos orientaciones:
 * - Horizontal (por defecto): dock inferior centrado, como en la distribución Original.
 * - Vertical (`vertical = true`): columna lateral a la derecha del modelo 3D, usada por
 *   la Distribución Panorámica para liberar el alto inferior y que las tarjetas de
 *   información bajen. Mismos 7 apartados, misma navegación y mismo indicador.
 */
@Composable
fun AutomotiveBottomDock(
    currentRoute: DivineDeckRoute,
    onNavigate: (DivineDeckRoute) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false
) {
    val palette = DivineDeckTheme.palette

    if (vertical) {
        // Dock lateral: columna vertical a toda la altura del contenido,
        // a la derecha del modelo 3D y de las tarjetas inferiores.
        Box(
            modifier = modifier.fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(palette.surface.copy(alpha = 0.92f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                palette.border,
                                palette.borderSubtle
                            )
                        ),
                        shape = RoundedCornerShape(26.dp)
                    )
                    .padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DivineDeckRoute.entries.forEach { route ->
                    DockIconItem(
                        route = route,
                        isSelected = route == currentRoute,
                        onNavigate = onNavigate,
                        vertical = true
                    )
                }
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp, top = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(palette.surface.copy(alpha = 0.92f))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            palette.border,
                            palette.borderSubtle
                        )
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DivineDeckRoute.entries.forEach { route ->
                DockIconItem(
                    route = route,
                    isSelected = route == currentRoute,
                    onNavigate = onNavigate,
                    vertical = false
                )
            }
        }
    }
}

/** Un icono del dock con su indicador de apartado activo. */
@Composable
private fun DockIconItem(
    route: DivineDeckRoute,
    isSelected: Boolean,
    onNavigate: (DivineDeckRoute) -> Unit,
    vertical: Boolean
) {
    val palette = DivineDeckTheme.palette

    val iconTint by animateColorAsState(
        targetValue = if (isSelected) palette.accent else palette.textSecondary.copy(alpha = 0.72f),
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "DockIconTint"
    )

    val indicatorWidth by animateDpAsState(
        targetValue = if (isSelected) 16.dp else 0.dp,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "DockIndicatorWidth"
    )

    val indicatorAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "DockIndicatorAlpha"
    )

    // Área táctil amplia (56x48 dp en horizontal, 48x48 dp en vertical)
    // sin caja rectangular visible
    Box(
        modifier = Modifier
            .size(
                width = if (vertical) 48.dp else 56.dp,
                height = 48.dp
            )
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onNavigate(route) }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Resplandor ambiental discreto detrás del icono activo
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                palette.accentGlow,
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = route.icon,
                contentDescription = route.title,
                tint = iconTint,
                modifier = Modifier.size(23.dp)
            )

            Spacer(modifier = Modifier.height(5.dp))

            // Indicador discreto inferior para el apartado activo
            Box(
                modifier = Modifier
                    .width(indicatorWidth)
                    .height(2.5.dp)
                    .alpha(indicatorAlpha)
                    .background(palette.accent, CircleShape)
            )
        }
    }
}
