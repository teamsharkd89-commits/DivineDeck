package com.divinedeck.launcher.feature.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.lyrics.LyricsEffectStyle
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla completa de Configuración de Fuentes de Letras BitChord dentro de DIVINE DECK (Fase 06.5E.5).
 *
 * Características:
 * - Diseñada para el Samsung Galaxy Tab A11 SM-X133 (1340 × 800, orientación horizontal).
 * - Estilo Obsidian (#080B12), Graphite (#171D27), Amber (#FFB547) y Electric Cyan (#00E5FF).
 * - Dos columnas optimizadas para ergonomía de tablero automotriz.
 * - Activación y desactivación reactiva de fuentes con guardia de seguridad (mínimo 1 fuente activa).
 * - Ordenación de prioridad en tiempo real que modifica el orden efectivo de consulta en [LyricsRepository].
 * - Control de priorización palabra por palabra ([AppSettings.prioritizeSyllableSync]).
 * - Restauración a valores predeterminados con confirmación previa.
 * - Cero interrupción de la reproducción de música ni del servicio de fondo Media3.
 */
@Composable
fun BitChordLyricsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isEmbedded: Boolean = false,
) {
    val activeSources by AppSettings.lyricsSources.collectAsState()
    val sourceOrder by AppSettings.lyricsSourceOrder.collectAsState()
    val prioritizeSyllableSync by AppSettings.prioritizeSyllableSync.collectAsState()
    val lyricsEffectsEnabled by AppSettings.lyricsEffectsEnabled.collectAsState()
    val lyricsEffectStyle by AppSettings.lyricsEffectStyle.collectAsState()
    val lyricsEffectIntensity by AppSettings.lyricsEffectIntensity.collectAsState()
    val lyricsBeatReaction by AppSettings.lyricsBeatReaction.collectAsState()
    val lyricsLowPowerMode by AppSettings.lyricsLowPowerMode.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }
    var warningMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF080B12))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // ---- BARRA SUPERIOR ------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (!isEmbedded) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.07f)),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Volver",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Fuentes de Letras BitChord",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 20.sp,
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFFB547).copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, Color(0xFFFFB547).copy(alpha = 0.4f)),
                            ) {
                                Text(
                                    text = "DSP & SYNC",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB547),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            text = "Control de prioridad de consulta, proveedores activos y sincronización silábica",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                        )
                    }
                }

                // Badge de estado de fuentes
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF))
                            )
                            Text(
                                text = "${activeSources.size} / ${sourceOrder.size} ACTIVAS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---- CUERPO PRINCIPAL (2 COLUMNAS HORIZONTALES) -------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // COLUMNA IZQUIERDA: LISTA ORDENADA DE PROVEEDORES
                Column(
                    modifier = Modifier
                        .weight(1.55f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF171D27))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "ORDEN DE PRIORIDAD Y PROVEEDORES",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB547),
                                letterSpacing = 0.5.sp,
                            )
                            Text(
                                text = "El motor consulta en paralelo pero asigna la letra según el orden de arriba a abajo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        itemsIndexed(
                            items = sourceOrder,
                            key = { _, source -> source.name },
                        ) { index, source ->
                            val isEnabled = source in activeSources
                            LyricsSourceRowItem(
                                source = source,
                                priority = index + 1,
                                isEnabled = isEnabled,
                                canMoveUp = index > 0,
                                canMoveDown = index < sourceOrder.size - 1,
                                onMoveUp = {
                                    AppSettings.moveLyricsSourceUp(source)
                                },
                                onMoveDown = {
                                    AppSettings.moveLyricsSourceDown(source)
                                },
                                onToggle = {
                                    val success = AppSettings.toggleLyricsSource(source)
                                    if (!success) {
                                        warningMessage = "Debe permanecer al menos un proveedor activo."
                                        scope.launch {
                                            delay(2500)
                                            warningMessage = null
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // COLUMNA DERECHA: AJUSTES DE SINCRONIZACIÓN Y RESTAURACIÓN
                Column(
                    modifier = Modifier
                        .weight(1.0f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // TARJETA 1: SINCRONIZACIÓN POR PALABRA
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF171D27),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Priorizar Sincronización Silábica",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                    )
                                    Text(
                                        text = "Enhanced LRC, Apple Music, TTML, QRC y YRC",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF00E5FF),
                                        fontSize = 11.sp,
                                    )
                                }

                                Switch(
                                    checked = prioritizeSyllableSync,
                                    onCheckedChange = { AppSettings.setPrioritizeSyllableSync(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF00E5FF),
                                        uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                        uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                                    )
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = if (prioritizeSyllableSync) {
                                        "ACTIVO: Si un proveedor de menor prioridad tiene tiempos exactos palabra por palabra, se elegirá antes que una letra de línea completa. Las letras por línea seguirán actuando de respaldo si no hay silábicas."
                                    } else {
                                        "ESTÁNDAR: Se acepta de inmediato el primer proveedor del orden que responda con letras sincronizadas, garantizando máxima velocidad de carga."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }

                    // TARJETA EFECTOS VISUALES & RENDIMIENTO (Milestone 1)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF171D27),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Efectos Visuales de Letras",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                    )
                                    Text(
                                        text = "Pulso, Flujo, Karaoke, Glow y Beat a 60fps",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF00E5FF),
                                        fontSize = 11.sp,
                                    )
                                }

                                Switch(
                                    checked = lyricsEffectsEnabled,
                                    onCheckedChange = { AppSettings.setLyricsEffectsEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF00E5FF),
                                        uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                        uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                                    )
                                )
                            }

                            if (lyricsEffectsEnabled) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    LyricsEffectStyle.entries.forEach { style ->
                                        val isSelected = style == lyricsEffectStyle
                                        Surface(
                                            onClick = { AppSettings.setLyricsEffectStyle(style) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f),
                                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.10f)),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = when (style) {
                                                        LyricsEffectStyle.PULSE -> "Pulso"
                                                        LyricsEffectStyle.FLOW -> "Flujo"
                                                        LyricsEffectStyle.KARAOKE -> "Karaoke"
                                                        LyricsEffectStyle.GLOW -> "Glow"
                                                        LyricsEffectStyle.BEAT_REACTIVE -> "Beat"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.70f),
                                                    fontSize = 11.sp,
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Reacción a Graves / Beat",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                        )
                                        Text(
                                            text = "In-pipeline DSP con fallback rítmico procedural",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.50f),
                                            fontSize = 10.sp,
                                        )
                                    }
                                    Switch(
                                        checked = lyricsBeatReaction,
                                        onCheckedChange = { AppSettings.setLyricsBeatReaction(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFF00E5FF),
                                            uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                            uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                                        )
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Modo de Bajo Consumo",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                        )
                                        Text(
                                            text = "Desactiva desenfoques GPU Mali-G57 para máxima autonomía",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.50f),
                                            fontSize = 10.sp,
                                        )
                                    }
                                    Switch(
                                        checked = lyricsLowPowerMode,
                                        onCheckedChange = { AppSettings.setLyricsLowPowerMode(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFF00E5FF),
                                            uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                            uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // TARJETA 2: RESUMEN DEL SISTEMA Y PROTECCIÓN OFFLINE
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF171D27),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "ARQUITECTURA Y OFFLINE",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB547),
                                letterSpacing = 0.5.sp,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Motor de consulta",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                )
                                Text(
                                    text = "Concurrente + Prioridad",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Persistencia offline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                )
                                Text(
                                    text = "Sidecar .lrc + LyricsStore",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.sp,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Letras incrustadas",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                )
                                Text(
                                    text = "Prioridad 1 (Sin red)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFFB547),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // TARJETA 3: BOTÓN DE RESTAURAR VALORES PREDETERMINADOS
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF171D27),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = { showResetDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFFFB547),
                                ),
                                border = BorderStroke(1.dp, Color(0xFFFFB547).copy(alpha = 0.5f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestartAlt,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB547),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Restaurar Configuración Original",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                )
                            }

                            Text(
                                text = "Reestablece todas las fuentes originales, el orden por defecto y desactiva la prioridad silábica forzada.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                }
            }
        }

        // AVISO FLOTANTE DE ERROR / RESTRICCIÓN (ej: no desactivar última fuente)
        AnimatedVisibility(
            visible = warningMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF26180B),
                border = BorderStroke(1.dp, Color(0xFFFFB547).copy(alpha = 0.8f)),
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFB547),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = warningMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // DIÁLOGO DE CONFIRMACIÓN PARA RESTAURAR
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                containerColor = Color(0xFF171D27),
                shape = RoundedCornerShape(18.dp),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        tint = Color(0xFFFFB547),
                        modifier = Modifier.size(28.dp),
                    )
                },
                title = {
                    Text(
                        text = "¿Restaurar fuentes de letras?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                },
                text = {
                    Text(
                        text = "Se restaurará el orden oficial de BitChord 1.8.1, se activarán todas las fuentes disponibles y la sincronización silábica volverá a su modo equilibrado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            AppSettings.resetLyricsSourceSettings()
                            showResetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFB547),
                            contentColor = Color(0xFF080B12),
                        ),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(text = "Restaurar", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showResetDialog = false },
                    ) {
                        Text(text = "Cancelar", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            )
        }
    }
}

/**
 * Fila individual para un proveedor de letras dentro de la lista de prioridad.
 */
@Composable
private fun LyricsSourceRowItem(
    source: LyricsSource,
    priority: Int,
    isEnabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isEnabled) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.04f)
    val cardBackground = if (isEnabled) Color(0xFF1E2633) else Color(0xFF131720)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBackground,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // BADGE DE PRIORIDAD #1..#16
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isEnabled) Color(0xFFFFB547).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, if (isEnabled) Color(0xFFFFB547).copy(alpha = 0.35f) else Color.Transparent),
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "#$priority",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) Color(0xFFFFB547) else Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                    )
                }
            }

            // CONTROLES DE REORDENAMIENTO SUBIR / BAJAR
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(24.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White.copy(alpha = 0.85f),
                        disabledContentColor = Color.White.copy(alpha = 0.15f),
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Subir prioridad",
                        modifier = Modifier.size(20.dp),
                    )
                }

                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(24.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White.copy(alpha = 0.85f),
                        disabledContentColor = Color.White.copy(alpha = 0.15f),
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Bajar prioridad",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // NOMBRE Y DETALLES DEL PROVEEDOR
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = source.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) Color.White else Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // CHIP DE TIPO DE SINCRONIZACIÓN
                    if (source.wordSynced) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF00E5FF).copy(alpha = if (isEnabled) 0.16f else 0.06f),
                        ) {
                            Text(
                                text = "Palabra / Sílaba",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isEnabled) Color(0xFF00E5FF) else Color(0xFF00E5FF).copy(alpha = 0.35f),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White.copy(alpha = 0.05f),
                        ) {
                            Text(
                                text = "Línea completa",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Normal,
                                color = Color.White.copy(alpha = if (isEnabled) 0.5f else 0.25f),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Text(
                    text = source.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = if (isEnabled) 0.55f else 0.25f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // SWITCH ON / OFF
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFFFFB547),
                    uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                ),
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}
