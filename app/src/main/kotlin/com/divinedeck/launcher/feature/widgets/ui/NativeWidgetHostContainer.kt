package com.divinedeck.launcher.feature.widgets.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme
import com.divinedeck.launcher.feature.widgets.domain.AndroidWidgetHostManager
import com.divinedeck.launcher.feature.widgets.domain.model.WidgetBindingStatus
import com.divinedeck.launcher.feature.widgets.domain.model.WidgetRenderState
import com.divinedeck.launcher.feature.widgets.domain.model.WidgetSlot

/**
 * Contenedor de presentación y renderizado de widgets Android reales (Fase 05.3).
 * Incorpora AppWidgetHostView nativo mediante AndroidView sin interceptar toques ni controles.
 */
@Composable
fun NativeWidgetHostContainer(
    slot: WidgetSlot,
    widgetManager: AndroidWidgetHostManager,
    modifier: Modifier = Modifier,
    onConfigureClick: (() -> Unit)? = null,
    onClearClick: (() -> Unit)? = null
) {
    val palette = DivineDeckTheme.palette

    val providerInfo = remember(slot.slotId, slot.appWidgetId, slot.providerPackage, slot.providerClass) {
        widgetManager.getProviderInfoForSlot(slot.slotId)
    }

    val renderState = when {
        slot.isEmpty -> WidgetRenderState.EMPTY
        slot.bindingStatus == WidgetBindingStatus.UNINSTALLED ||
        slot.bindingStatus == WidgetBindingStatus.PROVIDER_NOT_FOUND -> WidgetRenderState.PROVIDER_UNAVAILABLE
        slot.isBound && slot.appWidgetId != null && providerInfo != null -> WidgetRenderState.READY
        slot.isBound && providerInfo == null -> WidgetRenderState.PROVIDER_UNAVAILABLE
        else -> WidgetRenderState.RENDER_ERROR
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.surface.copy(alpha = 0.94f),
                        palette.background.copy(alpha = 0.96f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = when (renderState) {
                    WidgetRenderState.READY -> palette.border
                    WidgetRenderState.EMPTY -> palette.borderSubtle
                    WidgetRenderState.PROVIDER_UNAVAILABLE,
                    WidgetRenderState.RENDER_ERROR -> palette.statusAlert.copy(alpha = 0.45f)
                    WidgetRenderState.LOADING -> palette.accent.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(22.dp)
            )
            .padding(
                horizontal = if (renderState == WidgetRenderState.READY) 2.dp else 12.dp,
                vertical = if (renderState == WidgetRenderState.READY) 2.dp else 10.dp
            )
    ) {
        when (renderState) {
            WidgetRenderState.READY -> {
                // Área completa dedicada exclusivamente al widget nativo sin cabeceras,
                // sin etiquetas de posición/nombre/tamaño y sin botones dentro del recuadro.
                // Ocupa el recuadro con la máxima superficie disponible.
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    val currentWidthDp = maxWidth.value.toInt().coerceAtLeast(10)
                    val currentHeightDp = maxHeight.value.toInt().coerceAtLeast(10)
                    val widgetId = slot.appWidgetId ?: 0

                    var lastReportedWidth by remember { mutableStateOf(0) }
                    var lastReportedHeight by remember { mutableStateOf(0) }

                    if (widgetId > 0 && providerInfo != null) {
                        AndroidView(
                            factory = { ctx ->
                                val view = widgetManager.getOrCreateHostView(ctx, widgetId, providerInfo)
                                    ?: AppWidgetHostView(ctx).also {
                                        it.setAppWidget(widgetId, providerInfo)
                                    }

                                // Asegurar transferencia segura desasociando de cualquier ViewGroup padre anterior
                                (view.parent as? ViewGroup)?.removeView(view)

                                // Eliminar paddings residuales para maximizar la superficie útil
                                view.setPadding(0, 0, 0, 0)

                                view.layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                view
                            },
                            update = { hostView ->
                                // Notificar opciones dimensionales solo si variaron significativamente
                                if (kotlin.math.abs(lastReportedWidth - currentWidthDp) > 2 ||
                                    kotlin.math.abs(lastReportedHeight - currentHeightDp) > 2
                                ) {
                                    lastReportedWidth = currentWidthDp
                                    lastReportedHeight = currentHeightDp

                                    val options = Bundle().apply {
                                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, currentWidthDp)
                                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, currentWidthDp)
                                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, currentHeightDp)
                                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, currentHeightDp)
                                    }
                                    try {
                                        hostView.updateAppWidgetSize(
                                            options,
                                            currentWidthDp,
                                            currentHeightDp,
                                            currentWidthDp,
                                            currentHeightDp
                                        )
                                    } catch (_: Throwable) {}
                                }
                            },
                            onRelease = { hostView ->
                                (hostView.parent as? ViewGroup)?.removeView(hostView)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            WidgetRenderState.EMPTY -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = onConfigureClick != null) { onConfigureClick?.invoke() },
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(palette.textMuted)
                            )
                            Text(
                                text = slot.positionLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = palette.textSecondary
                            )
                        }

                        Text(
                            text = "VACÍO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp
                            ),
                            color = palette.textMuted
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(palette.elevatedSurface)
                                .border(1.dp, palette.borderSubtle, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Elegir widget",
                                tint = palette.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Configurar widget",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = palette.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Toca para seleccionar un widget Android",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Text(
                        text = "Widget Center • Catálogo Universal",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            letterSpacing = 0.4.sp
                        ),
                        color = palette.textMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }

            WidgetRenderState.PROVIDER_UNAVAILABLE -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = slot.positionLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = palette.statusAlert
                        )

                        if (onClearClick != null) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(palette.elevatedSurface)
                                    .clickable { onClearClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpiar",
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = palette.statusAlert,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Widget no disponible",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = palette.textPrimary
                        )
                        Text(
                            text = "La aplicación o proveedor fue desinstalado.",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (onConfigureClick != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.elevatedSurface)
                                .border(0.6.dp, palette.borderSubtle, RoundedCornerShape(6.dp))
                                .clickable { onConfigureClick() }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "ELEGIR OTRO WIDGET",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = palette.accent
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            WidgetRenderState.RENDER_ERROR,
            WidgetRenderState.LOADING -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (renderState == WidgetRenderState.LOADING) "Cargando widget..." else "Error de renderizado",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                }
            }
        }
    }
}
