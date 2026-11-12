package com.divinedeck.launcher.feature.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.EqualizerMode
import com.music.bitchord.playback.EqLayout
import com.music.bitchord.playback.EqualizerPreset
import kotlin.math.abs
import kotlin.math.roundToInt

// Paleta Oficial de Divine Deck (Automotive Luxury)
private val ColorObsidian = Color(0xFF080B12)
private val ColorMidnightNavy = Color(0xFF101827)
private val ColorGraphite = Color(0xFF171D27)
private val ColorWarmAmber = Color(0xFFFFB547)
private val ColorChampagneGold = Color(0xFFE6B86A)
private val ColorStatusGreen = Color(0xFF76C893)
private val ColorAlertRed = Color(0xFFE45B5B)
private val ColorMutedText = Color(0xFF9EABB8)
private val ColorSubtleBorder = Color.White.copy(alpha = 0.08f)

/**
 * Pantalla completa de Ajustes de Audio y Ecualizador DSP de BitChord en DIVINE DECK.
 *
 * Diseñada para el Samsung Galaxy Tab A11 SM-X133 (1340 × 800, Landscape estricto).
 * Distribución de 3 columnas horizontales sin necesidad de scroll vertical en la vista principal:
 * - Columna Izquierda (Modos & Presets)
 * - Columna Central (Tone Pad 2D o Ecualizador Gráfico de 7 bandas)
 * - Columna Derecha (Audio Espacial Haas, Balance Estéreo y Telemetría DSP)
 */
@Composable
fun BitChordAudioSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isEmbedded: Boolean = false,
) {
    // 1. Estado reactivo observado directamente desde AppSettings
    val equalizerEnabled by AppSettings.equalizerEnabled.collectAsState()
    val equalizerMode by AppSettings.equalizerMode.collectAsState()
    val toneX by AppSettings.equalizerToneX.collectAsState()
    val toneY by AppSettings.equalizerToneY.collectAsState()
    val focused by AppSettings.equalizerFocused.collectAsState()
    val balance by AppSettings.equalizerBalance.collectAsState()
    val bands by AppSettings.equalizerBands.collectAsState()
    val preset by AppSettings.equalizerPreset.collectAsState()
    val spatialAudio by AppSettings.spatialAudio.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorObsidian)
    ) {
        // ---- BARRA SUPERIOR ----
        if (!isEmbedded) {
            AudioSettingsTopBar(
                equalizerEnabled = equalizerEnabled,
                onToggleEqualizer = { AppSettings.setEqualizerEnabled(it) },
                onResetDefaults = {
                    AppSettings.setEqualizerPreset(EqualizerPreset.FLAT)
                    AppSettings.setEqualizerTone(0, 0)
                    AppSettings.setEqualizerBalance(0f)
                    AppSettings.setEqualizerFocused(false)
                },
                onBack = onBack
            )
        } else {
            // Cabecera embebida limpia (sin botón duplicado de VOLVER)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = ColorWarmAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "ECUALIZADOR & PROCESAMIENTO DSP",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = Color.White
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (equalizerEnabled) "ACTIVO" else "BYPASS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp
                            ),
                            color = if (equalizerEnabled) ColorStatusGreen else ColorMutedText
                        )
                        Switch(
                            checked = equalizerEnabled,
                            onCheckedChange = { AppSettings.setEqualizerEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ColorStatusGreen,
                                uncheckedThumbColor = ColorMutedText,
                                uncheckedTrackColor = ColorMidnightNavy
                            )
                        )
                    }

                    Surface(
                        onClick = {
                            AppSettings.setEqualizerPreset(EqualizerPreset.FLAT)
                            AppSettings.setEqualizerTone(0, 0)
                            AppSettings.setEqualizerBalance(0f)
                            AppSettings.setEqualizerFocused(false)
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, ColorSubtleBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "RESTABLECER",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }

        // ---- CUERPO DE 3 COLUMNAS HORIZONTALES (Aprovechamiento 1340x800) ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // COLUMNA 1: Selector de Modo & Presets (Ancho: 300 dp)
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModeSelectorCard(
                    currentMode = equalizerMode,
                    enabled = equalizerEnabled,
                    onModeChange = { AppSettings.setEqualizerMode(it) }
                )

                if (equalizerMode == EqualizerMode.TONE_PAD) {
                    TonePadOptionsCard(
                        focused = focused,
                        enabled = equalizerEnabled,
                        onFocusedChange = { AppSettings.setEqualizerFocused(it) }
                    )
                } else {
                    PresetsSelectorCard(
                        activePreset = preset,
                        enabled = equalizerEnabled,
                        onSelectPreset = { AppSettings.setEqualizerPreset(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // COLUMNA 2: Zona Central de Esculpido Acústico (Tone Pad o 7-Bandas)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                color = ColorMidnightNavy,
                border = BorderStroke(1.dp, ColorSubtleBorder)
            ) {
                if (equalizerMode == EqualizerMode.TONE_PAD) {
                    TonePadCenterPane(
                        toneX = toneX,
                        toneY = toneY,
                        enabled = equalizerEnabled,
                        onToneChange = { newX, newY -> AppSettings.setEqualizerTone(newX, newY) },
                        onResetTone = { AppSettings.setEqualizerTone(0, 0) }
                    )
                } else {
                    ManualSevenBandsPane(
                        bands = bands,
                        enabled = equalizerEnabled,
                        onBandsChange = { AppSettings.setEqualizerBands(it) },
                        onResetFlat = { AppSettings.setEqualizerPreset(EqualizerPreset.FLAT) }
                    )
                }
            }

            // COLUMNA 3: Audio Espacial, Balance Estéreo & Telemetría (Ancho: 320 dp)
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SpatialAudioCard(
                    spatialAudio = spatialAudio,
                    onToggleSpatial = { AppSettings.setSpatialAudio(it) }
                )

                StereoBalanceCard(
                    balance = balance,
                    enabled = equalizerEnabled,
                    onBalanceChange = { AppSettings.setEqualizerBalance(it) },
                    onCenterBalance = { AppSettings.setEqualizerBalance(0f) }
                )

                DspTelemetryCard(
                    equalizerEnabled = equalizerEnabled,
                    spatialAudio = spatialAudio,
                    mode = equalizerMode
                )
            }
        }
    }
}

// ============================================================================
// COMPONENTES DE LA BARRA SUPERIOR
// ============================================================================

@Composable
private fun AudioSettingsTopBar(
    equalizerEnabled: Boolean,
    onToggleEqualizer: (Boolean) -> Unit,
    onResetDefaults: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        color = ColorMidnightNavy,
        border = BorderStroke(1.dp, ColorSubtleBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Lado izquierdo: Botón Volver + Título
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    onClick = onBack,
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, ColorSubtleBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "VOLVER",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Ecualizador",
                        tint = ColorWarmAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "AJUSTES DE AUDIO & DSP",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            color = Color.White
                        )
                        Text(
                            text = "MOTOR BITCHORD 1.8.1 · MEDIA3 EXOPLAYER",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ColorChampagneGold
                        )
                    }
                }
            }

            // Lado derecho: Interruptor Maestro ON/OFF + Botón Restablecer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Botón Restablecer
                Surface(
                    onClick = onResetDefaults,
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, ColorSubtleBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Restablecer",
                            tint = ColorMutedText,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "RESTABLECER",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ColorMutedText
                        )
                    }
                }

                // Switch Maestro
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (equalizerEnabled) ColorStatusGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (equalizerEnabled) "DSP ACTIVO" else "BYPASS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = if (equalizerEnabled) ColorStatusGreen else ColorMutedText
                    )
                    Switch(
                        checked = equalizerEnabled,
                        onCheckedChange = onToggleEqualizer,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ColorStatusGreen,
                            uncheckedThumbColor = ColorMutedText,
                            uncheckedTrackColor = ColorGraphite,
                            uncheckedBorderColor = ColorSubtleBorder
                        )
                    )
                }
            }
        }
    }
}

// ============================================================================
// COLUMNA 1: SELECTOR DE MODO Y PRESETS
// ============================================================================

@Composable
private fun ModeSelectorCard(
    currentMode: EqualizerMode,
    enabled: Boolean,
    onModeChange: (EqualizerMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ColorMidnightNavy,
        border = BorderStroke(1.dp, ColorSubtleBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "MODO DE CONTROL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = ColorChampagneGold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ColorGraphite)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModeTabPill(
                    title = "TONE PAD 2D",
                    isSelected = currentMode == EqualizerMode.TONE_PAD,
                    enabled = enabled,
                    onClick = { onModeChange(EqualizerMode.TONE_PAD) },
                    modifier = Modifier.weight(1f)
                )
                ModeTabPill(
                    title = "7 BANDAS",
                    isSelected = currentMode == EqualizerMode.MANUAL,
                    enabled = enabled,
                    onClick = { onModeChange(EqualizerMode.MANUAL) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ModeTabPill(
    title: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) ColorWarmAmber else Color.Transparent,
        animationSpec = tween(180),
        label = "modeTabBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) ColorObsidian else if (enabled) Color.White else ColorMutedText,
        animationSpec = tween(180),
        label = "modeTabTextColor"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun TonePadOptionsCard(
    focused: Boolean,
    enabled: Boolean,
    onFocusedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ColorMidnightNavy,
        border = BorderStroke(1.dp, ColorSubtleBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "ENFOQUE ESPECTRAL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = ColorChampagneGold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ColorGraphite)
                    .clickable(enabled = enabled) { onFocusedChange(!focused) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (focused) "ENFOCADO (Q = 2.2)" else "AMPLIO (Q = 0.7)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (focused) "Bandas estrechas para voz nítida" else "Transiciones suaves de espectro",
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorMutedText
                    )
                }

                Switch(
                    checked = focused,
                    onCheckedChange = onFocusedChange,
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ColorWarmAmber,
                        uncheckedThumbColor = ColorMutedText,
                        uncheckedTrackColor = ColorMidnightNavy
                    )
                )
            }

            // Explicación didáctica del Tone Pad
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.03f),
                border = BorderStroke(1.dp, ColorSubtleBorder)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "GUÍA DE EJES TONE PAD",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ColorChampagneGold
                    )
                    Text(
                        text = "• Horizontal (X): Cálido (-5) ↔ Brillante (+5)\n• Vertical (Y): Graves/V-Shape (-5) ↔ Claridad (+5)",
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorMutedText,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetsSelectorCard(
    activePreset: EqualizerPreset,
    enabled: Boolean,
    onSelectPreset: (EqualizerPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ColorMidnightNavy,
        border = BorderStroke(1.dp, ColorSubtleBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PRESETS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = ColorChampagneGold
                )
                Text(
                    text = activePreset.name.replace("_", " "),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = ColorWarmAmber
                )
            }

            val presetsList = remember {
                listOf(
                    EqualizerPreset.FLAT,
                    EqualizerPreset.BASS_BOOST,
                    EqualizerPreset.BASS_CUT,
                    EqualizerPreset.VOCAL,
                    EqualizerPreset.TREBLE_BOOST,
                    EqualizerPreset.TREBLE_CUT,
                    EqualizerPreset.ROCK,
                    EqualizerPreset.ELECTRONIC,
                    EqualizerPreset.JAZZ,
                    EqualizerPreset.CLASSICAL,
                    EqualizerPreset.ACOUSTIC,
                    EqualizerPreset.LOUDNESS,
                    EqualizerPreset.HIP_HOP,
                    EqualizerPreset.SPOKEN_WORD,
                    EqualizerPreset.SMALL_SPEAKERS,
                    EqualizerPreset.LATE_NIGHT
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(presetsList) { p ->
                    val isSelected = activePreset == p
                    val pillBg by animateColorAsState(
                        targetValue = if (isSelected) ColorWarmAmber else ColorGraphite,
                        animationSpec = tween(160),
                        label = "presetPillBg"
                    )
                    val pillText by animateColorAsState(
                        targetValue = if (isSelected) ColorObsidian else if (enabled) Color.White else ColorMutedText,
                        animationSpec = tween(160),
                        label = "presetPillText"
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(pillBg)
                            .clickable(enabled = enabled) { onSelectPreset(p) }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = p.name.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            color = pillText,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// COLUMNA 2: ÁREA CENTRAL (TONE PAD O 7 BANDAS)
// ============================================================================

@Composable
private fun TonePadCenterPane(
    toneX: Int,
    toneY: Int,
    enabled: Boolean,
    onToneChange: (Int, Int) -> Unit,
    onResetTone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Título del Tone Pad
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TONE PAD BIDIMENSIONAL",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = Color.White
                )
                Text(
                    text = "Arrastra el punto para esculpir la curva espectral de forma fluida",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorMutedText
                )
            }

            Surface(
                onClick = onResetTone,
                shape = RoundedCornerShape(8.dp),
                color = ColorGraphite,
                border = BorderStroke(1.dp, ColorSubtleBorder)
            ) {
                Text(
                    text = "CENTRAR (0, 0)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ColorChampagneGold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Canvas interactivo del Tone Pad
        TonePadCanvas(
            toneX = toneX,
            toneY = toneY,
            enabled = enabled,
            onToneChange = onToneChange,
            modifier = Modifier
                .size(340.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ColorGraphite)
                .border(1.dp, ColorSubtleBorder, RoundedCornerShape(16.dp))
        )

        // Lectura digital de coordenadas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ColorGraphite,
                border = BorderStroke(1.dp, ColorSubtleBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "TILT (X):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ColorMutedText
                        )
                        Text(
                            text = if (toneX > 0) "+$toneX" else toneX.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = ColorWarmAmber
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(Color.White.copy(alpha = 0.1f))
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "CONTOUR (Y):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ColorMutedText
                        )
                        Text(
                            text = if (toneY > 0) "+$toneY" else toneY.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = ColorWarmAmber
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TonePadCanvas(
    toneX: Int,
    toneY: Int,
    enabled: Boolean,
    onToneChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = EqLayout.TONE_STEPS // 5 pasos (-5 a +5)
    val latestOnChange = rememberUpdatedState(onToneChange)

    Canvas(
        modifier = modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val (x, y) = calculateToneCoordinates(offset, size.width.toFloat(), size.height.toFloat(), steps)
                    latestOnChange.value(x, y)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, _ ->
                    change.consume()
                    val (x, y) = calculateToneCoordinates(change.position, size.width.toFloat(), size.height.toFloat(), steps)
                    latestOnChange.value(x, y)
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val inset = 24.dp.toPx()
        val usableW = w - inset * 2
        val usableH = h - inset * 2
        val stepSpan = steps * 2 // 10 divisiones

        // 1. Líneas de rejilla central (Ejes X e Y en 0)
        val midX = inset + usableW / 2
        val midY = inset + usableH / 2
        drawLine(
            color = Color.White.copy(alpha = 0.12f),
            start = Offset(inset, midY),
            end = Offset(w - inset, midY),
            strokeWidth = 1.5f
        )
        drawLine(
            color = Color.White.copy(alpha = 0.12f),
            start = Offset(midX, inset),
            end = Offset(midX, h - inset),
            strokeWidth = 1.5f
        )

        // 2. Rejilla de puntos (-5 a +5)
        for (col in 0..stepSpan) {
            val px = inset + usableW * col / stepSpan
            for (row in 0..stepSpan) {
                val py = inset + usableH * row / stepSpan
                val isCenter = col == steps && row == steps
                drawCircle(
                    color = if (isCenter) ColorWarmAmber.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.18f),
                    radius = if (isCenter) 3.5f else 1.8f,
                    center = Offset(px, py)
                )
            }
        }

        // 3. Puck del usuario con halos suaves
        val puckX = inset + usableW * (toneX + steps) / stepSpan
        val puckY = inset + usableH * (steps - toneY) / stepSpan
        val puckCenter = Offset(puckX, puckY)

        // Halo exterior
        drawCircle(
            color = ColorWarmAmber.copy(alpha = 0.15f),
            radius = 28.dp.toPx(),
            center = puckCenter
        )
        // Halo medio
        drawCircle(
            color = ColorWarmAmber.copy(alpha = 0.35f),
            radius = 18.dp.toPx(),
            center = puckCenter
        )
        // Pomo sólido central
        drawCircle(
            color = ColorWarmAmber,
            radius = 10.dp.toPx(),
            center = puckCenter
        )
        drawCircle(
            color = ColorObsidian,
            radius = 3.dp.toPx(),
            center = puckCenter
        )
    }
}

private fun calculateToneCoordinates(offset: Offset, width: Float, height: Float, steps: Int): Pair<Int, Int> {
    val inset = 24f * 2.5f // Inset aproximado en px
    val usableW = (width - inset * 2).coerceAtLeast(1f)
    val usableH = (height - inset * 2).coerceAtLeast(1f)

    val fracX = ((offset.x - inset) / usableW).coerceIn(0f, 1f)
    val fracY = ((offset.y - inset) / usableH).coerceIn(0f, 1f)

    val x = ((fracX * steps * 2).roundToInt() - steps).coerceIn(-steps, steps)
    val y = (steps - (fracY * steps * 2).roundToInt()).coerceIn(-steps, steps)
    return Pair(x, y)
}

// ---- ECUALIZADOR MANUAL DE 7 BANDAS ----

@Composable
private fun ManualSevenBandsPane(
    bands: List<Float>,
    enabled: Boolean,
    onBandsChange: (List<Float>) -> Unit,
    onResetFlat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Cabecera del ecualizador manual
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ECUALIZADOR GRÁFICO (7 BANDAS IIR)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = Color.White
                )
                Text(
                    text = "Filtros biquad trapezoidales con compensación automática de ganancia (±12 dB)",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorMutedText
                )
            }

            Surface(
                onClick = onResetFlat,
                shape = RoundedCornerShape(8.dp),
                color = ColorGraphite,
                border = BorderStroke(1.dp, ColorSubtleBorder)
            ) {
                Text(
                    text = "PLANO (0 dB)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ColorChampagneGold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Faders de 7 bandas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EqLayout.MANUAL_BANDS_HZ.forEachIndexed { index, hz ->
                val currentDb = bands.getOrElse(index) { 0f }
                SevenBandVerticalFader(
                    frequencyHz = hz,
                    gainDb = currentDb,
                    enabled = enabled,
                    onGainChange = { newDb ->
                        val updated = bands.toMutableList()
                        while (updated.size < EqLayout.MANUAL_COUNT) updated.add(0f)
                        updated[index] = newDb
                        onBandsChange(updated)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SevenBandVerticalFader(
    frequencyHz: Float,
    gainDb: Float,
    enabled: Boolean,
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val range = EqLayout.MANUAL_RANGE_DB // 12 dB
    val latestOnChange = rememberUpdatedState(onGainChange)

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Etiqueta superior con la ganancia en dB
        val isNonZero = abs(gainDb) >= 0.1f
        Text(
            text = if (gainDb > 0) "+${String.format("%.1f", gainDb)}" else "${String.format("%.1f", gainDb)}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isNonZero) FontWeight.Black else FontWeight.Bold,
            color = if (isNonZero) ColorWarmAmber else ColorMutedText,
            maxLines = 1
        )

        // 2. Slider táctil sobre Canvas
        Canvas(
            modifier = Modifier
                .width(42.dp)
                .weight(1f)
                .padding(vertical = 6.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val newDb = calculateGainFromOffset(offset.y, size.height.toFloat(), range)
                        latestOnChange.value(newDb)
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { change, _ ->
                        change.consume()
                        val newDb = calculateGainFromOffset(change.position.y, size.height.toFloat(), range)
                        latestOnChange.value(newDb)
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val trackW = 6.dp.toPx()
            val knobRadius = 11.dp.toPx()
            val usableH = h - knobRadius * 2
            val midY = knobRadius + usableH / 2f
            val centerX = w / 2f

            // Pista de fondo
            drawRoundRect(
                color = ColorGraphite,
                topLeft = Offset(centerX - trackW / 2f, knobRadius),
                size = Size(trackW, usableH),
                cornerRadius = CornerRadius(trackW / 2f)
            )

            // Línea central de 0 dB
            drawLine(
                color = Color.White.copy(alpha = 0.25f),
                start = Offset(centerX - 12.dp.toPx(), midY),
                end = Offset(centerX + 12.dp.toPx(), midY),
                strokeWidth = 1.5f
            )

            // Cálculo de posición del pomo: +12 dB arriba, -12 dB abajo
            val normalizedFraction = ((gainDb / range) + 1f) / 2f // 0f en -12dB, 0.5f en 0dB, 1f en +12dB
            val knobY = knobRadius + usableH * (1f - normalizedFraction)

            // Barra de relleno activo desde 0 dB hacia el pomo
            if (abs(gainDb) > 0.05f) {
                val fillTop = if (knobY < midY) knobY else midY
                val fillHeight = abs(knobY - midY)
                drawRoundRect(
                    color = ColorWarmAmber,
                    topLeft = Offset(centerX - trackW / 2f, fillTop),
                    size = Size(trackW, fillHeight),
                    cornerRadius = CornerRadius(trackW / 2f)
                )
            }

            // Halo suave del pomo
            drawCircle(
                color = ColorWarmAmber.copy(alpha = 0.25f),
                radius = knobRadius + 4.dp.toPx(),
                center = Offset(centerX, knobY)
            )

            // Pomo del slider
            drawCircle(
                color = ColorWarmAmber,
                radius = knobRadius,
                center = Offset(centerX, knobY)
            )
            drawCircle(
                color = ColorObsidian,
                radius = 4.dp.toPx(),
                center = Offset(centerX, knobY)
            )
        }

        // 3. Etiqueta de frecuencia
        Text(
            text = formatFrequencyLabel(frequencyHz),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
    }
}

private fun calculateGainFromOffset(y: Float, height: Float, range: Float): Float {
    val knobRadius = 11f * 2.5f
    val usableH = (height - knobRadius * 2).coerceAtLeast(1f)
    val fraction = ((y - knobRadius) / usableH).coerceIn(0f, 1f)
    // 0 es arriba (+12 dB), 1 es abajo (-12 dB)
    val db = (1f - fraction * 2f) * range
    // Redondeo suave a 0.5 dB
    val snapped = (db * 2f).roundToInt() / 2f
    return snapped.coerceIn(-range, range)
}

private fun formatFrequencyLabel(hz: Float): String = when {
    hz >= 1000f -> "${(hz / 1000f).toInt()}k"
    else -> "${hz.toInt()}"
}

// ============================================================================
// COLUMNA 3: AUDIO ESPACIAL, BALANCE & TELEMETRÍA
// ============================================================================

@Composable
private fun SpatialAudioCard(
    spatialAudio: Boolean,
    onToggleSpatial: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ColorMidnightNavy,
        border = BorderStroke(1.dp, ColorSubtleBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Headphones,
                        contentDescription = "Audio Espacial",
                        tint = if (spatialAudio) ColorStatusGreen else ColorMutedText,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "AUDIO ESPACIAL HAAS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = ColorChampagneGold
                    )
                }

                Switch(
                    checked = spatialAudio,
                    onCheckedChange = onToggleSpatial,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ColorStatusGreen,
                        uncheckedThumbColor = ColorMutedText,
                        uncheckedTrackColor = ColorGraphite
                    )
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ColorGraphite,
                border = BorderStroke(1.dp, ColorSubtleBorder)
            ) {
                Text(
                    text = "Ampliación mid/side con retardo Haas (15 ms). Activo únicamente en pistas estéreo; las pistas mono se procesan con bypass seguro.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorMutedText,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun StereoBalanceCard(
    balance: Float,
    enabled: Boolean,
    onBalanceChange: (Float) -> Unit,
    onCenterBalance: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ColorMidnightNavy,
        border = BorderStroke(1.dp, ColorSubtleBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BALANCE ESTÉREO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = ColorChampagneGold
                )

                Surface(
                    onClick = onCenterBalance,
                    shape = RoundedCornerShape(6.dp),
                    color = ColorGraphite
                ) {
                    Text(
                        text = "CENTRAR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ColorWarmAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Slider horizontal de balance
            Slider(
                value = balance,
                onValueChange = { onBalanceChange(it) },
                valueRange = -1f..1f,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = ColorWarmAmber,
                    activeTrackColor = ColorWarmAmber,
                    inactiveTrackColor = ColorGraphite
                )
            )

            // Indicadores L - C - R
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "IZQ (L)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (balance < -0.05f) ColorWarmAmber else ColorMutedText
                )
                Text(
                    text = when {
                        abs(balance) < 0.05f -> "CENTRO"
                        balance < 0 -> "L ${(abs(balance) * 100).toInt()}%"
                        else -> "R ${(balance * 100).toInt()}%"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "DER (R)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (balance > 0.05f) ColorWarmAmber else ColorMutedText
                )
            }
        }
    }
}

@Composable
private fun DspTelemetryCard(
    equalizerEnabled: Boolean,
    spatialAudio: Boolean,
    mode: EqualizerMode
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ColorMidnightNavy,
        border = BorderStroke(1.dp, ColorSubtleBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "TELEMETRÍA DEL PIPELINE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = ColorChampagneGold
            )

            TelemetryRow(label = "Sink de audio:", value = "PCM 16-bit LE (Media3)")
            TelemetryRow(
                label = "Ecualizador:",
                value = if (equalizerEnabled) "Activo (${if (mode == EqualizerMode.TONE_PAD) "Tone Pad" else "7 Bandas"})" else "Bypass (0 dB)"
            )
            TelemetryRow(
                label = "Espacialización:",
                value = if (spatialAudio) "Activo (Haas 15ms)" else "Desactivado"
            )
            TelemetryRow(label = "Limitador:", value = "PeakLimiter Activo")
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ColorMutedText
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
