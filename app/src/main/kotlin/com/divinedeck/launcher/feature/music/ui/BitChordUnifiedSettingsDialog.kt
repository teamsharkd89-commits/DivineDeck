package com.divinedeck.launcher.feature.music.ui

import android.widget.Toast
import java.util.Locale
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurLinear
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LowPriority
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NetworkCell
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.divinedeck.launcher.feature.music.ui.settings.Badge
import com.divinedeck.launcher.feature.music.ui.settings.ColorAmberAccent
import com.divinedeck.launcher.feature.music.ui.settings.ColorAmoledCard
import com.divinedeck.launcher.feature.music.ui.settings.ColorCardBorder
import com.divinedeck.launcher.feature.music.ui.settings.ColorCyanAccent
import com.divinedeck.launcher.feature.music.ui.settings.ColorDestructiveRed
import com.divinedeck.launcher.feature.music.ui.settings.ColorTextMuted
import com.divinedeck.launcher.feature.music.ui.settings.ColorTextPrimary
import com.divinedeck.launcher.feature.music.ui.settings.ColorTextSecondary
import com.divinedeck.launcher.feature.music.ui.settings.DestructiveRow
import com.divinedeck.launcher.feature.music.ui.settings.GROUP_INSET
import com.divinedeck.launcher.feature.music.ui.settings.GroupShape
import com.divinedeck.launcher.feature.music.ui.settings.HardwareSpecRow
import com.divinedeck.launcher.feature.music.ui.settings.PaneHeader
import com.divinedeck.launcher.feature.music.ui.settings.ROW_INSET
import com.divinedeck.launcher.feature.music.ui.settings.RowDivider
import com.divinedeck.launcher.feature.music.ui.settings.SegmentedControl
import com.divinedeck.launcher.feature.music.ui.settings.SettingsActionRow
import com.divinedeck.launcher.feature.music.ui.settings.SettingsCard
import com.divinedeck.launcher.feature.music.ui.settings.SettingsRow
import com.divinedeck.launcher.feature.music.ui.settings.SettingsSubRow
import com.divinedeck.launcher.feature.music.ui.settings.SliderRow
import com.divinedeck.launcher.feature.music.ui.settings.StorageStatPill
import com.divinedeck.launcher.feature.music.ui.settings.TEXT_INSET
import com.music.bitchord.data.lyrics.LyricsEffectStyle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.settings.BitChordBackup
import com.music.bitchord.data.settings.BitChordLanguage
import com.music.bitchord.data.settings.BitChordStorageManager
import com.music.bitchord.data.settings.DownloadQuality
import com.music.bitchord.data.settings.LocalMusicSort
import com.music.bitchord.data.settings.OutputPcmMode
import com.music.bitchord.data.settings.SongSort
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.download.DownloadStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 12 Categorías oficiales del Centro Unificado de Ajustes de BitChord.
 */
enum class BitChordSettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    REPRODUCCION("Reproducción", "Comportamiento, cola, velocidad y controles", Icons.Rounded.PlayCircle),
    AUDIO_CALIDAD("Calidad de Audio", "Streaming Wi-Fi/móvil y resolución", Icons.Rounded.GraphicEq),
    ECUALIZADOR_DSP("Ecualizador & DSP", "Tone Pad 2D, 7 bandas y Audio Espacial Haas", Icons.Rounded.Tune),
    DESCARGAS("Descargas Offline", "Calidad de descarga y restricciones", Icons.Rounded.CloudDownload),
    APARIENCIA("Apariencia", "Temas, Liquid Glass, carátulas y efectos", Icons.Rounded.Palette),
    RENDIMIENTO("Rendimiento", "Caché, tasa de refresco y optimización", Icons.Rounded.Speed),
    BIBLIOTECA("Biblioteca & Archivos", "Ordenación, filtros y pistas locales", Icons.Rounded.LibraryMusic),
    ALMACENAMIENTO("Almacenamiento & Caché", "Desglose de espacio y limpieza segura", Icons.Rounded.Storage),
    LETRAS("Fuentes de Letras", "LRCLIB, KuGou, Netease y prioridad", Icons.Rounded.Subtitles),
    IDIOMA("Idioma", "Selección de idioma de la interfaz", Icons.Rounded.Language),
    COPIAS_SEGURIDAD("Copias de Seguridad", "Exportar, importar y restablecer", Icons.Rounded.Save),
    AVANZADO("Avanzado & Diagnóstico", "Estadísticas Nerd y ficha técnica", Icons.Rounded.Info);

    companion object {
        val AUDIO_DSP: BitChordSettingsCategory get() = ECUALIZADOR_DSP
        val LYRICS_SOURCES: BitChordSettingsCategory get() = LETRAS
        val STORAGE_DOWNLOADS: BitChordSettingsCategory get() = ALMACENAMIENTO
        val LOCAL_FILES: BitChordSettingsCategory get() = BIBLIOTECA
    }
}

/**
 * Centro Unificado de Ajustes de BitChord — Rediseño BitChord 1.8.1 (Fase 06.9).
 *
 * Diseñado específicamente para pantalla horizontal de 1340 × 800 px (Samsung Galaxy Tab A11 SM-X133).
 * Recupera la identidad visual, tokens exactos (GroupShape 14dp, ROW_INSET 16dp, ICON_SIZE 22dp,
 * ICON_GAP 14dp, TEXT_INSET 52dp) y componentes clave de BitChord 1.8.1 SettingsSheet:
 * - SettingsCard / SettingsGroup AMOLED
 * - SettingsRow con glifo líder de 22dp
 * - SettingsSubRow para ajustes dependientes sin icono
 * - SliderRow con pista limpia
 * - SegmentedControl estilo píldora deslizante con hápticos
 * - Preserva el 100% de las 12 categorías conectadas a AppSettings
 * - Conecta los controles DSP exclusivamente en Ajustes (Requisito R5)
 */
@Composable
fun BitChordUnifiedSettingsDialog(
    onDismiss: () -> Unit,
    initialCategory: BitChordSettingsCategory = BitChordSettingsCategory.REPRODUCCION,
    onLaunchAudioPicker: (() -> Unit)? = null,
    downloadedCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by rememberSaveable { mutableStateOf(initialCategory) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF000000),
            border = BorderStroke(1.dp, ColorCardBorder),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // =============================================================
                // 1. BARRA SUPERIOR: IDENTIDAD BITCHORD, BUSCADOR EN VIVO Y CERRAR
                // =============================================================
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    color = Color(0xFF0C0C0C),
                    border = BorderStroke(0.5.dp, ColorCardBorder),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Badge de BitChord 1.8.1
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ColorCyanAccent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    tint = ColorCyanAccent,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "BITCHORD AJUSTES",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.1.sp,
                                        ),
                                        color = ColorTextPrimary,
                                    )
                                    Surface(
                                        color = ColorCyanAccent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            text = "v1.8.1",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                            color = ColorCyanAccent,
                                        )
                                    }
                                }
                                Text(
                                    text = "DIVINE DECK · Centro Unificado de Configuración",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = ColorTextSecondary,
                                )
                            }
                        }

                        // Buscador central interactivo
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .width(460.dp)
                                .height(46.dp),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    "Buscar ajustes (calidad, crossfade, caché, letras...)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = ColorTextMuted,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = ColorCyanAccent.copy(alpha = 0.70f),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Clear,
                                            contentDescription = "Limpiar búsqueda",
                                            tint = Color.White.copy(alpha = 0.60f),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = ColorCyanAccent,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedContainerColor = Color.Black.copy(alpha = 0.40f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.25f),
                            ),
                            shape = RoundedCornerShape(12.dp),
                        )

                        // Botón de Volver / Cerrar
                        Surface(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF181818),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Cerrar",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "VOLVER",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                    ),
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }

                // =============================================================
                // 2. CUERPO PRINCIPAL: PANEL LATERAL 270DP + CONTENIDO
                // =============================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    // BARRA LATERAL DE CATEGORÍAS (Ancho 270dp)
                    Surface(
                        modifier = Modifier
                            .width(270.dp)
                            .fillMaxHeight(),
                        color = Color(0xFF080808),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f)),
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(BitChordSettingsCategory.entries) { category ->
                                val isSelected = selectedCategory == category && searchQuery.isEmpty()
                                Surface(
                                    onClick = {
                                        searchQuery = ""
                                        selectedCategory = category
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) ColorCyanAccent.copy(alpha = 0.16f) else Color.Transparent,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) ColorCyanAccent.copy(alpha = 0.60f) else Color.Transparent,
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = category.icon,
                                            contentDescription = category.title,
                                            tint = if (isSelected) ColorCyanAccent else Color.White.copy(alpha = 0.60f),
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = category.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 13.sp,
                                                ),
                                                color = if (isSelected) ColorCyanAccent else ColorTextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = category.subtitle,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = ColorTextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // PANEL CENTRAL DE CONTENIDO
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFF000000)),
                    ) {
                        if (searchQuery.isNotBlank()) {
                            // MODO BÚSQUEDA GLOBAL
                            GlobalSettingsSearchPane(
                                query = searchQuery,
                                onSelectCategory = { cat ->
                                    searchQuery = ""
                                    selectedCategory = cat
                                },
                            )
                        } else {
                            // PESTAÑA SELECCIONADA
                            AnimatedContent(
                                targetState = selectedCategory,
                                transitionSpec = {
                                    fadeIn(tween(140)) togetherWith fadeOut(tween(100))
                                },
                                label = "settingsCategorySwitch",
                            ) { category ->
                                when (category) {
                                    BitChordSettingsCategory.REPRODUCCION -> PlaybackSettingsPane()
                                    BitChordSettingsCategory.AUDIO_CALIDAD -> AudioQualitySettingsPane(
                                        onOpenDsp = { selectedCategory = BitChordSettingsCategory.ECUALIZADOR_DSP },
                                    )
                                    BitChordSettingsCategory.ECUALIZADOR_DSP -> BitChordAudioSettingsScreen(
                                        onBack = { selectedCategory = BitChordSettingsCategory.AUDIO_CALIDAD },
                                        modifier = Modifier.fillMaxSize(),
                                        isEmbedded = true,
                                    )
                                    BitChordSettingsCategory.DESCARGAS -> DownloadsSettingsPane(
                                        downloadedCount = downloadedCount,
                                    )
                                    BitChordSettingsCategory.APARIENCIA -> AppearanceSettingsPane()
                                    BitChordSettingsCategory.RENDIMIENTO -> PerformanceSettingsPane()
                                    BitChordSettingsCategory.BIBLIOTECA -> LibrarySettingsPane(
                                        onLaunchAudioPicker = onLaunchAudioPicker,
                                    )
                                    BitChordSettingsCategory.ALMACENAMIENTO -> StorageAndCachePane(
                                        downloadedCount = downloadedCount,
                                    )
                                    BitChordSettingsCategory.LETRAS -> BitChordLyricsSettingsScreen(
                                        onBack = { selectedCategory = BitChordSettingsCategory.REPRODUCCION },
                                        modifier = Modifier.fillMaxSize(),
                                        isEmbedded = true,
                                    )
                                    BitChordSettingsCategory.IDIOMA -> LanguageSettingsPane()
                                    BitChordSettingsCategory.COPIAS_SEGURIDAD -> BackupAndRestorePane()
                                    BitChordSettingsCategory.AVANZADO -> AdvancedSettingsPane()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// SUB-PANEL 1: REPRODUCCIÓN (Playback) — Identidad BitChord 1.8.1
// =============================================================================
@Composable
private fun PlaybackSettingsPane() {
    val autoplay by AppSettings.autoplay.collectAsStateWithLifecycle()
    val repeatMode by AppSettings.repeatMode.collectAsStateWithLifecycle()
    val shuffleEnabled by AppSettings.shuffleEnabled.collectAsStateWithLifecycle()
    val crossfadeSeconds by AppSettings.crossfadeSeconds.collectAsStateWithLifecycle()
    val smartFade by AppSettings.smartFadeEnabled.collectAsStateWithLifecycle()
    val skipSilence by AppSettings.skipSilence.collectAsStateWithLifecycle()
    val outputPcmMode by AppSettings.outputPcmMode.collectAsStateWithLifecycle()
    val preferUsbDac by AppSettings.preferUsbDac.collectAsStateWithLifecycle()
    val loudnessNorm by AppSettings.loudnessNormalization.collectAsStateWithLifecycle()
    val loudnessOffSpeaker by AppSettings.loudnessOffOnSpeaker.collectAsStateWithLifecycle()
    val preferMusicOnly by AppSettings.preferMusicOnly.collectAsStateWithLifecycle()
    val swipeToPlayNext by AppSettings.swipeToPlayNext.collectAsStateWithLifecycle()
    val dontRepeatSuggestions by AppSettings.dontRepeatSuggestions.collectAsStateWithLifecycle()
    val stopOnTaskRemoved by AppSettings.stopOnTaskRemoved.collectAsStateWithLifecycle()
    val hideVolumeBar by AppSettings.hideVolumeBar.collectAsStateWithLifecycle()
    val playbackSpeed by AppSettings.playbackSpeed.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PaneHeader(
            title = "Ajustes de Reproducción",
            subtitle = "Controla la continuidad, transiciones, cola y respuesta del motor de audio",
        )

        // Comportamiento del Reproductor
        SettingsCard(title = "Comportamiento del Reproductor") {
            SettingsRow(
                icon = Icons.Rounded.PlayArrow,
                title = "Reproducción Automática",
                subtitle = "Reanuda o continúa la reproducción al seleccionar una pista o abrir la app",
                trailing = {
                    Switch(
                        checked = autoplay,
                        onCheckedChange = AppSettings::setAutoplay,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setAutoplay(!autoplay) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Repeat,
                title = "Modo de Repetición",
                subtitle = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repetir canción actual"
                    Player.REPEAT_MODE_ALL -> "Repetir toda la cola de reproducción"
                    else -> "Sin repetición activa"
                },
            )
            SegmentedControl(
                options = listOf("Desactivado", "Repetir Todo", "Repetir 1"),
                selectedIndex = when (repeatMode) {
                    Player.REPEAT_MODE_ALL -> 1
                    Player.REPEAT_MODE_ONE -> 2
                    else -> 0
                },
                onSelect = {
                    AppSettings.setRepeatMode(
                        when (it) {
                            1 -> Player.REPEAT_MODE_ALL
                            2 -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        },
                    )
                },
                modifier = Modifier.padding(start = TEXT_INSET, end = ROW_INSET, bottom = 12.dp),
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Shuffle,
                title = "Modo Aleatorio",
                subtitle = "Mezcla el orden de reproducción de la lista actual sin alterar la cola del usuario",
                trailing = {
                    Switch(
                        checked = shuffleEnabled,
                        onCheckedChange = AppSettings::setShuffleEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setShuffleEnabled(!shuffleEnabled) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.PowerSettingsNew,
                title = "Detener al Cerrar la Aplicación",
                subtitle = "Pausa y libera el servicio multimedia al retirar Divine Deck de tareas recientes",
                trailing = {
                    Switch(
                        checked = stopOnTaskRemoved,
                        onCheckedChange = AppSettings::setStopOnTaskRemoved,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setStopOnTaskRemoved(!stopOnTaskRemoved) },
            )
        }

        // Transiciones y Crossfade
        SettingsCard(title = "Transiciones y Fades") {
            SliderRow(
                icon = Icons.Rounded.Tune,
                title = "Crossfade entre Canciones",
                subtitle = if (crossfadeSeconds == 0) "Desactivado (reproducción estándar)" else "Superposición de audio: $crossfadeSeconds segundos",
                value = "${crossfadeSeconds}s",
                sliderValue = crossfadeSeconds.toFloat(),
                onSliderValue = { AppSettings.setCrossfadeSeconds(it.roundToInt()) },
                valueRange = 0f..12f,
                steps = 11,
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.GraphicEq,
                title = "Smart Fade",
                subtitle = "Atenuación exponencial suave al pausar, reanudar o saltar pistas manualmente",
                trailing = {
                    Switch(
                        checked = smartFade,
                        onCheckedChange = AppSettings::setSmartFadeEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setSmartFadeEnabled(!smartFade) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.FastForward,
                title = "Saltar Silencios",
                subtitle = "Omite fragmentos en silencio absoluto al inicio o final de las pistas",
                trailing = {
                    Switch(
                        checked = skipSilence,
                        onCheckedChange = AppSettings::setSkipSilence,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setSkipSilence(!skipSilence) },
            )
        }

        // Audio y Salida de Hardware
        SettingsCard(title = "Procesamiento y Salida de Hardware") {
            SettingsRow(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                title = "Formato PCM de Salida",
                subtitle = outputPcmMode.label,
            )
            SegmentedControl(
                options = OutputPcmMode.entries.map { it.label },
                selectedIndex = OutputPcmMode.entries.indexOf(outputPcmMode),
                onSelect = { AppSettings.setOutputPcmMode(OutputPcmMode.entries[it]) },
                modifier = Modifier.padding(start = TEXT_INSET, end = ROW_INSET, bottom = 12.dp),
            )
            RowDivider()
            SettingsSubRow(
                title = "Preferir DAC USB Externo",
                subtitle = "Prioriza salida directa sin resampleado al conectar un DAC o dongle USB",
                checked = preferUsbDac,
                onCheckedChange = AppSettings::setPreferUsbDac,
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Hearing,
                title = "Normalización de Volumen",
                subtitle = "Equilibra el nivel percibido entre pistas grabadas a diferente ganancia",
                trailing = {
                    Switch(
                        checked = loudnessNorm,
                        onCheckedChange = AppSettings::setLoudnessNormalization,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setLoudnessNormalization(!loudnessNorm) },
            )
            RowDivider()
            SettingsSubRow(
                title = "Desactivar Normalización en Altavoces",
                subtitle = "Aplica ganancia máxima si la tablet reproduce por sus altavoces integrados",
                checked = loudnessOffSpeaker,
                onCheckedChange = AppSettings::setLoudnessOffOnSpeaker,
            )
            RowDivider()
            SliderRow(
                icon = Icons.Rounded.Speed,
                title = "Velocidad de Reproducción",
                subtitle = "Tasa de velocidad del audio: ${String.format(Locale.US, "%.2f", playbackSpeed)}x",
                value = "${String.format(Locale.US, "%.2f", playbackSpeed)}x",
                sliderValue = playbackSpeed,
                onSliderValue = AppSettings::setPlaybackSpeed,
                valueRange = 0.5f..2.0f,
                steps = 5,
            )
        }

        // Navegación de Pistas y Cola
        SettingsCard(title = "Preferencias de Contenido y Cola") {
            SettingsRow(
                icon = Icons.Rounded.MusicNote,
                title = "Preferir Solo Música",
                subtitle = "Filtra intros no musicales y silencios de vídeos musicales oficiales",
                trailing = {
                    Switch(
                        checked = preferMusicOnly,
                        onCheckedChange = AppSettings::setPreferMusicOnly,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setPreferMusicOnly(!preferMusicOnly) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Swipe,
                title = "Deslizar para Reproducir Siguiente",
                subtitle = "Gesto de swipe en canciones las encola como siguientes en vez de al final",
                trailing = {
                    Switch(
                        checked = swipeToPlayNext,
                        onCheckedChange = AppSettings::setSwipeToPlayNext,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setSwipeToPlayNext(!swipeToPlayNext) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.AutoAwesome,
                title = "No Repetir Sugerencias",
                subtitle = "Evita que el algoritmo recomiende canciones escuchadas recientemente",
                trailing = {
                    Switch(
                        checked = dontRepeatSuggestions,
                        onCheckedChange = AppSettings::setDontRepeatSuggestions,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setDontRepeatSuggestions(!dontRepeatSuggestions) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.AutoMirrored.Rounded.VolumeMute,
                title = "Ocultar Barra de Volumen en Reproductor",
                subtitle = "Oculta el control deslizante de volumen en el reproductor apaisado",
                trailing = {
                    Switch(
                        checked = hideVolumeBar,
                        onCheckedChange = AppSettings::setHideVolumeBar,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setHideVolumeBar(!hideVolumeBar) },
            )
        }
    }
}

// =============================================================================
// SUB-PANEL 2: AUDIO Y CALIDAD (Streaming Quality) — Identidad BitChord 1.8.1
// =============================================================================
@Composable
private fun AudioQualitySettingsPane(
    onOpenDsp: () -> Unit,
) {
    val wifiQuality by AppSettings.audioQualityWifi.collectAsStateWithLifecycle()
    val cellQuality by AppSettings.audioQualityCellular.collectAsStateWithLifecycle()
    val metered by AppSettings.meteredConnection.collectAsStateWithLifecycle()
    val effectiveQuality = AppSettings.effectiveAudioQuality

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PaneHeader(
            title = "Calidad de Audio y Streaming",
            subtitle = "Configura el techo de bitrate y consumo de datos según tu tipo de red",
        )

        // Estado activo en tiempo real
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GROUP_INSET),
            shape = GroupShape,
            color = ColorAmoledCard,
            border = BorderStroke(1.dp, ColorCyanAccent.copy(alpha = 0.35f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(ColorCyanAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = ColorCyanAccent,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Calidad Activa en Vigor: ${effectiveQuality.label.uppercase()}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        ),
                        color = ColorCyanAccent,
                    )
                    Text(
                        text = "${effectiveQuality.detail} · Consumo estimado: ${effectiveQuality.hourly}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = ColorTextSecondary,
                    )
                    Text(
                        text = if (metered == true) "Conexión actual: Red de datos medidos / móvil" else "Conexión actual: Wi-Fi sin límite de datos",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = ColorTextMuted,
                    )
                }
            }
        }

        SettingsCard(title = "Techo de Calidad por Tipo de Red") {
            SettingsRow(
                icon = Icons.Rounded.Wifi,
                title = "Calidad en Redes Wi-Fi",
                subtitle = "${wifiQuality.label} (${wifiQuality.hourly})",
            )
            SegmentedControl(
                options = AudioQuality.entries.map { it.label },
                selectedIndex = AudioQuality.entries.indexOf(wifiQuality),
                onSelect = { AppSettings.setAudioQualityWifi(AudioQuality.entries[it]) },
                modifier = Modifier.padding(start = TEXT_INSET, end = ROW_INSET, bottom = 12.dp),
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.NetworkCell,
                title = "Calidad en Redes Móviles / Datos Medidos",
                subtitle = "${cellQuality.label} (${cellQuality.hourly})",
            )
            SegmentedControl(
                options = AudioQuality.entries.map { it.label },
                selectedIndex = AudioQuality.entries.indexOf(cellQuality),
                onSelect = { AppSettings.setAudioQualityCellular(AudioQuality.entries[it]) },
                modifier = Modifier.padding(start = TEXT_INSET, end = ROW_INSET, bottom = 12.dp),
            )
        }

        // Acceso directo al ecualizador DSP (Requisito R5)
        SettingsCard(title = "Procesamiento Acústico y Ecualización") {
            SettingsRow(
                icon = Icons.Rounded.Tune,
                title = "Ecualizador de 7 Bandas & Tone Pad 2D",
                subtitle = "Ajusta curvas de ecualización manual, presets acústicos y espacialización Haas",
                trailing = {
                    Button(
                        onClick = onOpenDsp,
                        colors = ButtonDefaults.buttonColors(containerColor = ColorAmberAccent),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "ABRIR DSP",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.Black,
                        )
                    }
                },
                onClick = onOpenDsp,
            )
        }
    }
}

// =============================================================================
// SUB-PANEL 3: DESCARGAS OFFLINE (Downloads) — Identidad BitChord 1.8.1
// =============================================================================
@Composable
private fun DownloadsSettingsPane(
    downloadedCount: Int,
) {
    val dlQuality by AppSettings.downloadQuality.collectAsStateWithLifecycle()
    val wifiOnly by AppSettings.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val exportDownloads by AppSettings.exportDownloads.collectAsStateWithLifecycle()
    val maxConcurrent by AppSettings.maxConcurrentDownloads.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val dlDir = remember { runCatching { DownloadStore.downloadsDir(context).path }.getOrDefault("storage/bitchord") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PaneHeader(
            title = "Ajustes de Descargas Offline",
            subtitle = "Gestiona la calidad, límites de red y almacenamiento para escuchar música sin conexión",
        )

        // Resumen de descargas
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GROUP_INSET),
            shape = GroupShape,
            color = ColorAmoledCard,
            border = BorderStroke(1.dp, ColorCyanAccent.copy(alpha = 0.30f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(ColorCyanAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DownloadDone,
                        contentDescription = null,
                        tint = ColorCyanAccent,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column {
                    Text(
                        text = "$downloadedCount Canciones Descargadas y Listas Offline",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ColorTextPrimary,
                    )
                    Text(
                        text = "Almacén local en: $dlDir",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = ColorTextSecondary,
                    )
                }
            }
        }

        SettingsCard(title = "Políticas de Descarga") {
            SettingsRow(
                icon = Icons.Rounded.Download,
                title = "Calidad de Descarga",
                subtitle = "${dlQuality.label} · ${dlQuality.detail} (${dlQuality.perTrack})",
            )
            SegmentedControl(
                options = DownloadQuality.entries.map { it.label },
                selectedIndex = DownloadQuality.entries.indexOf(dlQuality),
                onSelect = { AppSettings.setDownloadQuality(DownloadQuality.entries[it]) },
                modifier = Modifier.padding(start = TEXT_INSET, end = ROW_INSET, bottom = 12.dp),
            )
            RowDivider()
            SettingsSubRow(
                title = "Descargar Únicamente con Wi-Fi",
                subtitle = "Bloquea descargas automáticas o manuales cuando el dispositivo esté con red móvil",
                checked = wifiOnly,
                onCheckedChange = AppSettings::setWifiOnlyDownloads,
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Sync,
                title = "Descargas Simultáneas",
                subtitle = "$maxConcurrent descargas concurrentes en paralelo",
            )
            SegmentedControl(
                options = listOf("1", "2", "3", "4", "5"),
                selectedIndex = (maxConcurrent - 1).coerceIn(0, 4),
                onSelect = { AppSettings.setMaxConcurrentDownloads(it + 1) },
                modifier = Modifier.padding(start = TEXT_INSET, end = ROW_INSET, bottom = 12.dp),
            )
            RowDivider()
            SettingsSubRow(
                title = "Exportar a Carpeta de Música del Sistema",
                subtitle = "Copia el archivo descargado a la carpeta /Music pública de Android",
                checked = exportDownloads,
                onCheckedChange = AppSettings::setExportDownloads,
            )
        }
    }
}

// =============================================================================
// SUB-PANEL 4: APARIENCIA (Appearance) — Identidad BitChord 1.8.1
// =============================================================================
@Composable
private fun AppearanceSettingsPane() {
    val themeMode by AppSettings.themeMode.collectAsStateWithLifecycle()
    val liquidGlass by AppSettings.liquidGlass.collectAsStateWithLifecycle()
    val reduceAnim by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val reduceBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val fullBleedArt by AppSettings.fullBleedArtwork.collectAsStateWithLifecycle()
    val meshGradient by AppSettings.legacyMeshGradient.collectAsStateWithLifecycle()
    val animatedCanvas by AppSettings.animatedCanvas.collectAsStateWithLifecycle()
    val syncedLyrics by AppSettings.syncedLyrics.collectAsStateWithLifecycle()
    val lyricsBlur by AppSettings.lyricsBlur.collectAsStateWithLifecycle()
    val hideStatusPlayer by AppSettings.hideStatusBarInPlayer.collectAsStateWithLifecycle()
    val localViewType by AppSettings.localMusicViewType.collectAsStateWithLifecycle()
    val dlViewType by AppSettings.downloadedMusicViewType.collectAsStateWithLifecycle()
    val lyricsEffectsEnabled by AppSettings.lyricsEffectsEnabled.collectAsStateWithLifecycle()
    val lyricsEffectStyle by AppSettings.lyricsEffectStyle.collectAsStateWithLifecycle()
    val lyricsEffectIntensity by AppSettings.lyricsEffectIntensity.collectAsStateWithLifecycle()
    val lyricsBeatReaction by AppSettings.lyricsBeatReaction.collectAsStateWithLifecycle()
    val lyricsLowPowerMode by AppSettings.lyricsLowPowerMode.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PaneHeader(
            title = "Ajustes de Apariencia e Interfaz",
            subtitle = "Personaliza temas, lenguaje Liquid Glass, presentación de portadas y transiciones",
        )

        SettingsCard(title = "Tema y Superficies") {
            SettingsRow(
                icon = Icons.Rounded.Brightness4,
                title = "Tema de la Aplicación",
                subtitle = themeMode.label,
            )
            SegmentedControl(
                options = ThemeMode.entries.map { it.label },
                selectedIndex = ThemeMode.entries.indexOf(themeMode),
                onSelect = { AppSettings.setThemeMode(ThemeMode.entries[it]) },
                modifier = Modifier.padding(start = TEXT_INSET, end = ROW_INSET, bottom = 12.dp),
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.AutoAwesome,
                title = "Efecto Liquid Glass",
                subtitle = "Bordes traslúcidos y superficies vidriadas con acento automotriz de BitChord",
                trailing = {
                    Switch(
                        checked = liquidGlass,
                        onCheckedChange = AppSettings::setLiquidGlass,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setLiquidGlass(!liquidGlass) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Animation,
                title = "Reducir Animaciones",
                subtitle = "Transiciones instantáneas sin efectos dinámicos para mayor agilidad",
                trailing = {
                    Switch(
                        checked = reduceAnim,
                        onCheckedChange = AppSettings::setReduceAnimation,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setReduceAnimation(!reduceAnim) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.BlurOn,
                title = "Reducir Desenfoque Dinámico",
                subtitle = "Desactiva blur en tiempo real sobre carátulas en fondos para ahorrar GPU",
                trailing = {
                    Switch(
                        checked = reduceBlur,
                        onCheckedChange = AppSettings::setReduceDynamicBlur,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setReduceDynamicBlur(!reduceBlur) },
            )
        }

        SettingsCard(title = "Carátulas y Reproductor") {
            SettingsRow(
                icon = Icons.Rounded.AspectRatio,
                title = "Carátula a Pantalla Completa (Full Bleed)",
                subtitle = "Expande la portada al fondo completo del reproductor apaisado",
                trailing = {
                    Switch(
                        checked = fullBleedArt,
                        onCheckedChange = AppSettings::setFullBleedArtwork,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setFullBleedArtwork(!fullBleedArt) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Gradient,
                title = "Gradiente de Malla Suave",
                subtitle = "Calcula una gradiente ambiental a partir de la paleta de la carátula",
                trailing = {
                    Switch(
                        checked = meshGradient,
                        onCheckedChange = AppSettings::setLegacyMeshGradient,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setLegacyMeshGradient(!meshGradient) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.VideoLibrary,
                title = "Canvas y Portadas Animadas",
                subtitle = "Reproduce bucles de vídeo cortos proporcionados por la pista cuando estén disponibles",
                trailing = {
                    Switch(
                        checked = animatedCanvas,
                        onCheckedChange = AppSettings::setAnimatedCanvas,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setAnimatedCanvas(!animatedCanvas) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Fullscreen,
                title = "Ocultar Barra de Estado en Reproductor",
                subtitle = "Inmersión total sin indicador de hora ni batería durante la reproducción",
                trailing = {
                    Switch(
                        checked = hideStatusPlayer,
                        onCheckedChange = AppSettings::setHideStatusBarInPlayer,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setHideStatusBarInPlayer(!hideStatusPlayer) },
            )
        }

        SettingsCard(title = "Letras y Tipografía") {
            SettingsRow(
                icon = Icons.Rounded.Subtitles,
                title = "Sincronización de Letras",
                subtitle = "Desplazamiento automático de líneas y palabras al ritmo de la música",
                trailing = {
                    Switch(
                        checked = syncedLyrics,
                        onCheckedChange = AppSettings::setSyncedLyrics,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setSyncedLyrics(!syncedLyrics) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.BlurLinear,
                title = "Desenfocar Líneas de Letra Inactivas",
                subtitle = "Aplica desenfoque a las frases pasadas o futuras para resaltar la línea activa",
                trailing = {
                    Switch(
                        checked = lyricsBlur,
                        onCheckedChange = AppSettings::setLyricsBlur,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setLyricsBlur(!lyricsBlur) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.AutoAwesome,
                title = "Efectos Visuales de Letras",
                subtitle = "Animaciones draw-time (Pulso, Flujo, Karaoke, Glow y Beat Reactive)",
                trailing = {
                    Switch(
                        checked = lyricsEffectsEnabled,
                        onCheckedChange = AppSettings::setLyricsEffectsEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setLyricsEffectsEnabled(!lyricsEffectsEnabled) },
            )
            if (lyricsEffectsEnabled) {
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.Animation,
                    title = "Estilo de Efecto Visual",
                    subtitle = lyricsEffectStyle.description,
                )
                SegmentedControl(
                    options = listOf("Pulso", "Flujo", "Karaoke", "Glow", "Beat"),
                    selectedIndex = LyricsEffectStyle.entries.indexOf(lyricsEffectStyle),
                    onSelect = { AppSettings.setLyricsEffectStyle(LyricsEffectStyle.entries[it]) },
                    modifier = Modifier.padding(start = TEXT_INSET, end = ROW_INSET, bottom = 12.dp),
                )
                RowDivider()
                SliderRow(
                    icon = Icons.Rounded.Tune,
                    title = "Intensidad del Efecto",
                    subtitle = "Escala y amplitud de la animación visual",
                    value = String.format(Locale.ROOT, "%.1fx", lyricsEffectIntensity),
                    sliderValue = lyricsEffectIntensity,
                    onSliderValue = AppSettings::setLyricsEffectIntensity,
                    valueRange = 0.2f..2.0f,
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.GraphicEq,
                    title = "Reacción a Graves y Compás",
                    subtitle = "Sincroniza la pulsación con la energía de audio in-pipeline o beat procedural",
                    trailing = {
                        Switch(
                            checked = lyricsBeatReaction,
                            onCheckedChange = AppSettings::setLyricsBeatReaction,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = ColorCyanAccent,
                            ),
                        )
                    },
                    onClick = { AppSettings.setLyricsBeatReaction(!lyricsBeatReaction) },
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.Bolt,
                    title = "Modo de Bajo Consumo (Letras)",
                    subtitle = "Optimización térmica GPU Mali-G57: sustituye desenfoques por trazos nítidos",
                    trailing = {
                        Switch(
                            checked = lyricsLowPowerMode,
                            onCheckedChange = AppSettings::setLyricsLowPowerMode,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = ColorCyanAccent,
                            ),
                        )
                    },
                    onClick = { AppSettings.setLyricsLowPowerMode(!lyricsLowPowerMode) },
                )
            }
        }

        SettingsCard(title = "Disposición de Listas") {
            SettingsRow(
                icon = Icons.AutoMirrored.Rounded.ViewList,
                title = "Vista de Música Local",
                subtitle = if (localViewType == "GRID") "Cuadrícula" else "Lista vertical",
            )
            SegmentedControl(
                options = listOf("Lista", "Cuadrícula"),
                selectedIndex = if (localViewType == "GRID") 1 else 0,
                onSelect = { AppSettings.setLocalMusicViewType(if (it == 1) "GRID" else "LIST") },
                modifier = Modifier.padding(start = TEXT_INSET, end = ROW_INSET, bottom = 12.dp),
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.GridView,
                title = "Vista de Canciones Descargadas",
                subtitle = if (dlViewType == "GRID") "Cuadrícula" else "Lista vertical",
            )
            SegmentedControl(
                options = listOf("Lista", "Cuadrícula"),
                selectedIndex = if (dlViewType == "GRID") 1 else 0,
                onSelect = { AppSettings.setDownloadedMusicViewType(if (it == 1) "GRID" else "LIST") },
                modifier = Modifier.padding(start = TEXT_INSET, end = ROW_INSET, bottom = 12.dp),
            )
        }
    }
}

// =============================================================================
// SUB-PANEL 5: RENDIMIENTO (Performance) — Identidad BitChord 1.8.1
// =============================================================================
@Composable
private fun PerformanceSettingsPane() {
    val highPerf by AppSettings.highPerformanceMode.collectAsStateWithLifecycle()
    val refreshRate by AppSettings.performanceRefreshRate.collectAsStateWithLifecycle()
    val cacheLimit by AppSettings.audioCacheLimitBytes.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PaneHeader(
            title = "Ajustes de Rendimiento y Optimización",
            subtitle = "Ajustes de caché, límites de memoria y tasa de refresco para el SoC Helio G99",
        )

        SettingsCard(title = "Tasa de Cuadros y Pantalla") {
            SettingsRow(
                icon = Icons.Rounded.Bolt,
                title = "Modo de Alto Rendimiento",
                subtitle = "Prioriza suavidad y velocidad de renderizado en Compose desactivando efectos pesados",
                trailing = {
                    Switch(
                        checked = highPerf,
                        onCheckedChange = AppSettings::setHighPerformanceMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setHighPerformanceMode(!highPerf) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Refresh,
                title = "Tasa de Refresco de Pantalla",
                subtitle = when (refreshRate) {
                    60 -> "60 Hz (Estándar)"
                    90 -> "90 Hz (Fluido)"
                    120 -> "120 Hz (Ultra fluido)"
                    else -> "Predeterminada del sistema"
                },
            )
            SegmentedControl(
                options = listOf("Sistema", "60 Hz", "90 Hz", "120 Hz"),
                selectedIndex = when (refreshRate) {
                    60 -> 1
                    90 -> 2
                    120 -> 3
                    else -> 0
                },
                onSelect = {
                    AppSettings.setPerformanceRefreshRate(
                        when (it) {
                            1 -> 60
                            2 -> 90
                            3 -> 120
                            else -> 0
                        },
                    )
                },
                modifier = Modifier.padding(start = TEXT_INSET, end = ROW_INSET, bottom = 12.dp),
            )
        }

        SettingsCard(title = "Caché de Audio en Disco") {
            val cacheMb = (cacheLimit / (1024 * 1024)).toInt()
            SliderRow(
                icon = Icons.Rounded.Storage,
                title = "Límite Máximo de Caché de Audio",
                subtitle = if (cacheLimit == AppSettings.UNLIMITED_CACHE_LIMIT_BYTES) "Ilimitado" else "Límite: $cacheMb MB de canciones cacheadas en búfer",
                value = if (cacheLimit == AppSettings.UNLIMITED_CACHE_LIMIT_BYTES) "Ilimitado" else "$cacheMb MB",
                sliderValue = when {
                    cacheLimit == AppSettings.UNLIMITED_CACHE_LIMIT_BYTES -> 10240f
                    else -> cacheMb.toFloat().coerceIn(512f, 10240f)
                },
                valueRange = 512f..10240f,
                steps = 18,
                onSliderValue = {
                    val mb = it.roundToInt()
                    if (mb >= 10000) {
                        AppSettings.setAudioCacheLimitBytes(AppSettings.UNLIMITED_CACHE_LIMIT_BYTES)
                    } else {
                        AppSettings.setAudioCacheLimitBytes(mb.toLong() * 1024 * 1024)
                    }
                },
            )
        }
    }
}

// =============================================================================
// SUB-PANEL 6: BIBLIOTECA Y ARCHIVOS LOCALES — Identidad BitChord 1.8.1
// =============================================================================
@Composable
private fun LibrarySettingsPane(
    onLaunchAudioPicker: (() -> Unit)?,
) {
    val folderUri by AppSettings.localMusicFolderUri.collectAsStateWithLifecycle()
    val filterNonMusic by AppSettings.filterNonMusicAudio.collectAsStateWithLifecycle()
    val localSort by AppSettings.localMusicSort.collectAsStateWithLifecycle()
    val dlSort by AppSettings.downloadedMusicSort.collectAsStateWithLifecycle()
    val libSort by AppSettings.librarySort.collectAsStateWithLifecycle()
    val showQueueHistory by AppSettings.showQueueHistory.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PaneHeader(
            title = "Biblioteca y Música Local",
            subtitle = "Organiza pistas locales, carpetas examinadas, ordenación y filtros de audio",
        )

        SettingsCard(title = "Exploración de Música Local") {
            SettingsActionRow(
                icon = Icons.Rounded.FolderOpen,
                title = "Carpeta de Música Local",
                subtitle = if (folderUri.isBlank()) "Examinando todas las carpetas públicas de audio del dispositivo" else "Ruta: $folderUri",
                buttonText = "Elegir Archivo",
                onClick = { onLaunchAudioPicker?.invoke() },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.FilterList,
                title = "Filtrar Audios que no sean Música",
                subtitle = "Ignora notas de voz cortas (< 30s) y audios de mensajería (WhatsApp, Telegram)",
                trailing = {
                    Switch(
                        checked = filterNonMusic,
                        onCheckedChange = AppSettings::setFilterNonMusicAudio,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setFilterNonMusicAudio(!filterNonMusic) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.History,
                title = "Mostrar Historial en la Cola",
                subtitle = "Mantiene las pistas reproducidas recientemente en la vista de cola de reproducción",
                trailing = {
                    Switch(
                        checked = showQueueHistory,
                        onCheckedChange = AppSettings::setShowQueueHistory,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setShowQueueHistory(!showQueueHistory) },
            )
        }

        SettingsCard(title = "Criterios de Ordenación") {
            SettingsRow(
                icon = Icons.AutoMirrored.Rounded.Sort,
                title = "Orden de Música Local",
                subtitle = localSort.label,
            )
            SegmentedControl(
                options = LocalMusicSort.entries.map { it.label },
                selectedIndex = LocalMusicSort.entries.indexOf(localSort),
                onSelect = { AppSettings.setLocalMusicSort(LocalMusicSort.entries[it]) },
                modifier = Modifier.padding(start = TEXT_INSET, end = ROW_INSET, bottom = 12.dp),
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.SortByAlpha,
                title = "Orden de Canciones Descargadas",
                subtitle = dlSort.label,
            )
            SegmentedControl(
                options = SongSort.entries.map { it.label },
                selectedIndex = SongSort.entries.indexOf(dlSort),
                onSelect = { AppSettings.setDownloadedMusicSort(SongSort.entries[it]) },
                modifier = Modifier.padding(start = TEXT_INSET, end = ROW_INSET, bottom = 12.dp),
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.LowPriority,
                title = "Orden General de la Biblioteca",
                subtitle = libSort.label,
            )
            SegmentedControl(
                options = SongSort.entries.map { it.label },
                selectedIndex = SongSort.entries.indexOf(libSort),
                onSelect = { AppSettings.setLibrarySort(SongSort.entries[it]) },
                modifier = Modifier.padding(start = TEXT_INSET, end = ROW_INSET, bottom = 12.dp),
            )
        }
    }
}

// =============================================================================
// SUB-PANEL 7: ALMACENAMIENTO Y CACHÉ — Identidad BitChord 1.8.1
// =============================================================================
@Composable
private fun StorageAndCachePane(
    downloadedCount: Int,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloadsSize by remember { mutableStateOf(0L) }
    var audioCacheSize by remember { mutableStateOf(0L) }
    var imageCacheSize by remember { mutableStateOf(0L) }
    var freeSpace by remember { mutableStateOf(0L) }
    var totalSpace by remember { mutableStateOf(0L) }

    var showClearAudioDialog by remember { mutableStateOf(false) }
    var showClearImageDialog by remember { mutableStateOf(false) }

    fun refreshStats() {
        downloadsSize = BitChordStorageManager.getDownloadsSizeBytes(context)
        audioCacheSize = BitChordStorageManager.getAudioCacheSizeBytes(context)
        imageCacheSize = BitChordStorageManager.getImageCacheSizeBytes(context)
        freeSpace = BitChordStorageManager.getAvailableStorageBytes(context)
        totalSpace = BitChordStorageManager.getTotalStorageBytes(context)
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            refreshStats()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PaneHeader(
            title = "Gestión de Almacenamiento y Caché",
            subtitle = "Consulta de espacio en disco y liberación segura de memoria temporal sin borrar canciones offline",
        )

        // Resumen de Almacenamiento
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GROUP_INSET),
            shape = GroupShape,
            color = ColorAmoledCard,
            border = BorderStroke(1.dp, ColorCyanAccent.copy(alpha = 0.25f)),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Espacio Interno del Dispositivo",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ColorTextPrimary,
                    )
                    Text(
                        text = "${BitChordStorageManager.formatBytes(freeSpace)} Libres de ${BitChordStorageManager.formatBytes(totalSpace)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = ColorCyanAccent,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StorageStatPill(
                        label = "Descargas Offline",
                        value = BitChordStorageManager.formatBytes(downloadsSize),
                        detail = "$downloadedCount canciones",
                        color = ColorCyanAccent,
                        modifier = Modifier.weight(1f),
                    )
                    StorageStatPill(
                        label = "Caché de Streaming",
                        value = BitChordStorageManager.formatBytes(audioCacheSize),
                        detail = "Fragmentos temporales",
                        color = ColorAmberAccent,
                        modifier = Modifier.weight(1f),
                    )
                    StorageStatPill(
                        label = "Caché de Imágenes",
                        value = BitChordStorageManager.formatBytes(imageCacheSize),
                        detail = "Carátulas remotas",
                        color = Color(0xFF10B981),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        SettingsCard(title = "Limpieza Segura de Datos Temporales") {
            SettingsActionRow(
                icon = Icons.Rounded.CleaningServices,
                title = "Limpiar Caché Temporal de Streaming",
                subtitle = "Libera ${BitChordStorageManager.formatBytes(audioCacheSize)}. NUNCA elimina canciones descargadas ni listas de reproducción.",
                buttonText = "Limpiar Audio",
                isDestructive = true,
                onClick = { showClearAudioDialog = true },
            )
            RowDivider()
            SettingsActionRow(
                icon = Icons.Rounded.DeleteOutline,
                title = "Limpiar Caché de Portadas en Memoria",
                subtitle = "Libera ${BitChordStorageManager.formatBytes(imageCacheSize)}. Las portadas se descargarán automáticamente al reproducir.",
                buttonText = "Limpiar Imágenes",
                isDestructive = true,
                onClick = { showClearImageDialog = true },
            )
        }
    }

    // Diálogos de confirmación
    if (showClearAudioDialog) {
        AlertDialog(
            onDismissRequest = { showClearAudioDialog = false },
            title = { Text("¿Limpiar Caché de Audio?") },
            text = { Text("Se liberarán los fragmentos de audio temporalmente cacheados en búfer. Tus canciones descargadas offline permanecerán intactas.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            BitChordStorageManager.clearAudioCache(context)
                            refreshStats()
                        }
                        showClearAudioDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDestructiveRed),
                ) {
                    Text("Limpiar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAudioDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (showClearImageDialog) {
        AlertDialog(
            onDismissRequest = { showClearImageDialog = false },
            title = { Text("¿Limpiar Caché de Imágenes?") },
            text = { Text("Se eliminarán las carátulas temporales descargadas por Coil. Las carátulas locales de pistas descargadas no se verán afectadas.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            BitChordStorageManager.clearImageCache(context)
                            refreshStats()
                        }
                        showClearImageDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDestructiveRed),
                ) {
                    Text("Limpiar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearImageDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

// =============================================================================
// SUB-PANEL 8: IDIOMA (Language) — Identidad BitChord 1.8.1
// =============================================================================
@Composable
private fun LanguageSettingsPane() {
    val language by AppSettings.appLanguage.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PaneHeader(
            title = "Idioma de la Aplicación",
            subtitle = "Selecciona el idioma de los textos y pantallas de BitChord",
        )

        SettingsCard(title = "Idioma de la Interfaz") {
            SettingsRow(
                icon = Icons.Rounded.Language,
                title = "Idioma Seleccionado",
                subtitle = language.label,
            )
            SegmentedControl(
                options = BitChordLanguage.entries.map { it.label },
                selectedIndex = BitChordLanguage.entries.indexOf(language),
                onSelect = { AppSettings.setAppLanguage(BitChordLanguage.entries[it]) },
                modifier = Modifier.padding(start = TEXT_INSET, end = ROW_INSET, bottom = 12.dp),
            )
        }
    }
}

// =============================================================================
// SUB-PANEL 9: COPIAS DE SEGURIDAD — Identidad BitChord 1.8.1
// =============================================================================
@Composable
private fun BackupAndRestorePane() {
    val context = LocalContext.current
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PaneHeader(
            title = "Copias de Seguridad y Datos",
            subtitle = "Exporta tus preferencias a un archivo JSON seguro o restaura configuraciones previas",
        )

        SettingsCard(title = "Copia de Seguridad de Preferencias") {
            SettingsActionRow(
                icon = Icons.Rounded.Save,
                title = "Exportar Configuración a JSON",
                subtitle = "Guarda tus ajustes, ecualizaciones y fuentes de letras en una copia de respaldo",
                buttonText = "Exportar",
                isDestructive = false,
                onClick = {
                    val json = BitChordBackup.exportToJson()
                    backupStatus = "Configuración exportada exitosamente (${json.length} caracteres)"
                    Toast.makeText(context, "Copia de seguridad generada correctamente", Toast.LENGTH_SHORT).show()
                },
            )
            RowDivider()
            DestructiveRow(
                label = "Restablecer Todos los Ajustes de Fábrica",
                onClick = { showResetDialog = true },
            )
        }

        if (backupStatus != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GROUP_INSET),
                shape = GroupShape,
                color = Color(0xFF10B981).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFF10B981)),
            ) {
                Text(
                    text = backupStatus.orEmpty(),
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF10B981),
                )
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("¿Restablecer Todas las Preferencias?") },
            text = { Text("Esta acción restablecerá el ecualizador, calidad, apariencia y opciones de reproducción a sus valores de fábrica. Tus descargas y canciones guardadas se conservarán.") },
            confirmButton = {
                Button(
                    onClick = {
                        AppSettings.resetAllDefaults()
                        showResetDialog = false
                        Toast.makeText(context, "Ajustes restablecidos", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDestructiveRed),
                ) {
                    Text("Restablecer Todo", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

// =============================================================================
// SUB-PANEL 10: AVANZADO Y DIAGNÓSTICO — Identidad BitChord 1.8.1
// =============================================================================
@Composable
private fun AdvancedSettingsPane() {
    val nerdStats by AppSettings.showNerdStats.collectAsStateWithLifecycle()
    val smartAlign by AppSettings.smartVersionAlignment.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PaneHeader(
            title = "Ajustes Avanzados y Diagnóstico",
            subtitle = "Opciones para desarrolladores, telemetría técnica y ficha de hardware",
        )

        SettingsCard(title = "Telemetría y Diagnóstico") {
            SettingsRow(
                icon = Icons.Rounded.Analytics,
                title = "Estadísticas Nerd (Nerd Stats)",
                subtitle = "Muestra overlay con bitrate real, códec de audio, búfer y tasa de caída de paquetes",
                trailing = {
                    Switch(
                        checked = nerdStats,
                        onCheckedChange = AppSettings::setShowNerdStats,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setShowNerdStats(!nerdStats) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.SyncAlt,
                title = "Alineación de Versión Inteligente",
                subtitle = "Sincroniza metadatos de pistas idénticas entre diferentes fuentes de catálogo",
                trailing = {
                    Switch(
                        checked = smartAlign,
                        onCheckedChange = AppSettings::setSmartVersionAlignment,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ColorCyanAccent,
                        ),
                    )
                },
                onClick = { AppSettings.setSmartVersionAlignment(!smartAlign) },
            )
        }

        SettingsCard(title = "Ficha Técnica del Sistema") {
            HardwareSpecRow(label = "Motor BitChord", value = "v1.8.1 (GPLv3)")
            RowDivider()
            HardwareSpecRow(label = "Host Integrador", value = "DIVINE DECK Launcher (Fase 06.9)")
            RowDivider()
            HardwareSpecRow(label = "Dispositivo Objetivo", value = "Samsung Galaxy Tab A11 SM-X133")
            RowDivider()
            HardwareSpecRow(label = "Resolución", value = "1340 × 800 píxeles (Horizontal estricto)")
            RowDivider()
            HardwareSpecRow(label = "Plataforma / SoC", value = "MediaTek Helio G99 (MT8781)")
            RowDivider()
            HardwareSpecRow(label = "GPU Aceleradora", value = "ARM Mali-G57 MC2")
            RowDivider()
            HardwareSpecRow(label = "Motor de Audio", value = "Único DivineDeckPlaybackService + ExoPlayer Media3")
        }
    }
}

// =============================================================================
// BÚSQUEDA GLOBAL DE AJUSTES EN TIEMPO REAL
// =============================================================================
@Composable
private fun GlobalSettingsSearchPane(
    query: String,
    onSelectCategory: (BitChordSettingsCategory) -> Unit,
) {
    val q = query.trim().lowercase()

    data class SearchItem(
        val category: BitChordSettingsCategory,
        val title: String,
        val subtitle: String,
        val icon: ImageVector,
    )

    val allItems = remember {
        listOf(
            SearchItem(BitChordSettingsCategory.REPRODUCCION, "Reproducción Automática", "Reanuda al iniciar o seleccionar", Icons.Rounded.PlayArrow),
            SearchItem(BitChordSettingsCategory.REPRODUCCION, "Modo de Repetición", "Repetir una pista o toda la cola", Icons.Rounded.Repeat),
            SearchItem(BitChordSettingsCategory.REPRODUCCION, "Modo Aleatorio", "Reproducción aleatoria de la lista", Icons.Rounded.Shuffle),
            SearchItem(BitChordSettingsCategory.REPRODUCCION, "Crossfade entre Canciones", "Superposición y fundido cruzado", Icons.Rounded.Tune),
            SearchItem(BitChordSettingsCategory.REPRODUCCION, "Smart Fade", "Atenuación suave al pausar o cambiar", Icons.Rounded.GraphicEq),
            SearchItem(BitChordSettingsCategory.REPRODUCCION, "Saltar Silencios", "Elimina silencios iniciales o finales", Icons.Rounded.FastForward),
            SearchItem(BitChordSettingsCategory.REPRODUCCION, "Formato PCM de Salida", "16-bit PCM o 32-bit Float", Icons.AutoMirrored.Rounded.VolumeUp),
            SearchItem(BitChordSettingsCategory.REPRODUCCION, "Normalización de Volumen", "Ajusta ganancia homogénea", Icons.Rounded.Hearing),
            SearchItem(BitChordSettingsCategory.REPRODUCCION, "Velocidad de Reproducción", "Modifica la tasa de velocidad", Icons.Rounded.Speed),
            SearchItem(BitChordSettingsCategory.AUDIO_CALIDAD, "Calidad Wi-Fi", "Calidad de streaming sobre redes Wi-Fi", Icons.Rounded.Wifi),
            SearchItem(BitChordSettingsCategory.AUDIO_CALIDAD, "Calidad Red Móvil", "Calidad de streaming sobre datos móviles", Icons.Rounded.NetworkCell),
            SearchItem(BitChordSettingsCategory.ECUALIZADOR_DSP, "Ecualizador de 7 Bandas", "Ecualizador gráfico manual y presets", Icons.Rounded.Tune),
            SearchItem(BitChordSettingsCategory.ECUALIZADOR_DSP, "Tone Pad 2D", "Control dimensional de brillo y calidez", Icons.Rounded.GraphicEq),
            SearchItem(BitChordSettingsCategory.ECUALIZADOR_DSP, "Audio Espacial Haas", "Efecto de espacialización psicoacústica", Icons.Rounded.Hearing),
            SearchItem(BitChordSettingsCategory.DESCARGAS, "Calidad de Descarga", "Standard, High o Lossless para pistas offline", Icons.Rounded.Download),
            SearchItem(BitChordSettingsCategory.DESCARGAS, "Solo Wi-Fi para Descargas", "Evita consumo de datos móviles", Icons.Rounded.Wifi),
            SearchItem(BitChordSettingsCategory.APARIENCIA, "Tema de la Aplicación", "Sistema, Claro u Oscuro", Icons.Rounded.Brightness4),
            SearchItem(BitChordSettingsCategory.APARIENCIA, "Efecto Liquid Glass", "Superficies de vidrio traslúcidas", Icons.Rounded.AutoAwesome),
            SearchItem(BitChordSettingsCategory.APARIENCIA, "Carátula a Pantalla Completa", "Portada full bleed en reproductor", Icons.Rounded.AspectRatio),
            SearchItem(BitChordSettingsCategory.APARIENCIA, "Letras Sincronizadas", "Desplazamiento interactivo de letras", Icons.Rounded.Subtitles),
            SearchItem(BitChordSettingsCategory.RENDIMIENTO, "Tasa de Refresco", "60Hz, 90Hz o 120Hz para pantalla fluida", Icons.Rounded.Refresh),
            SearchItem(BitChordSettingsCategory.RENDIMIENTO, "Límite de Caché de Audio", "Capacidad de almacenamiento en disco", Icons.Rounded.Storage),
            SearchItem(BitChordSettingsCategory.ALMACENAMIENTO, "Limpiar Caché de Audio", "Libera memoria sin tocar descargas", Icons.Rounded.CleaningServices),
            SearchItem(BitChordSettingsCategory.ALMACENAMIENTO, "Limpiar Caché de Imágenes", "Libera portadas temporales", Icons.Rounded.DeleteOutline),
            SearchItem(BitChordSettingsCategory.LETRAS, "Fuentes de Letras", "LRCLIB, KuGou, Netease, QQ Music, Genius", Icons.Rounded.Subtitles),
            SearchItem(BitChordSettingsCategory.IDIOMA, "Idioma de la Aplicación", "Español, Inglés o Predeterminado", Icons.Rounded.Language),
            SearchItem(BitChordSettingsCategory.COPIAS_SEGURIDAD, "Exportar Configuración", "Genera archivo JSON de respaldo", Icons.Rounded.Save),
            SearchItem(BitChordSettingsCategory.COPIAS_SEGURIDAD, "Restablecer Ajustes", "Devuelve los ajustes a fábrica", Icons.Rounded.DeleteOutline),
            SearchItem(BitChordSettingsCategory.AVANZADO, "Estadísticas Nerd", "Monitor de telemetría de red y decodificación", Icons.Rounded.Analytics),
        )
    }

    val matches = remember(q) {
        allItems.filter {
            it.title.lowercase().contains(q) ||
                it.subtitle.lowercase().contains(q) ||
                it.category.title.lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PaneHeader(
            title = "Resultados de Búsqueda para \"$query\"",
            subtitle = "${matches.size} opciones encontradas",
        )

        if (matches.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(GROUP_INSET),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No se encontraron ajustes que coincidan con la búsqueda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorTextSecondary,
                )
            }
        } else {
            SettingsCard {
                matches.forEachIndexed { index, item ->
                    if (index > 0) RowDivider()
                    SettingsRow(
                        icon = item.icon,
                        title = item.title,
                        subtitle = item.subtitle,
                        badge = item.category.title,
                        onClick = { onSelectCategory(item.category) },
                    )
                }
            }
        }
    }
}
