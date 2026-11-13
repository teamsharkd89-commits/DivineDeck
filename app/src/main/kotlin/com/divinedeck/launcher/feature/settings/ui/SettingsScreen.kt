package com.divinedeck.launcher.feature.settings.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.divinedeck.launcher.core.designsystem.components.AutomotiveCard
import com.divinedeck.launcher.core.designsystem.components.AutomotivePrimaryButton
import com.divinedeck.launcher.core.designsystem.components.AutomotiveSecondaryButton
import com.divinedeck.launcher.core.designsystem.theme.CockpitLayout
import com.divinedeck.launcher.core.designsystem.theme.CuratedCustomAccents
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme
import com.divinedeck.launcher.core.designsystem.theme.ThemePreset
import com.divinedeck.launcher.core.designsystem.theme.buildPaletteForTheme
import com.divinedeck.launcher.core.designsystem.theme.ensureReadableAccent
import com.divinedeck.launcher.feature.garage3d.domain.model.LowriderBeatEngine
import com.divinedeck.launcher.feature.settings.domain.SettingsRepository
import com.divinedeck.launcher.feature.settings.model.OperatingMode
import com.divinedeck.launcher.feature.voice.data.VoskModelManager
import com.divinedeck.launcher.feature.voice.data.VoskModelState
import kotlinx.coroutines.delay

/**
 * Pantalla de Ajustes y Sistema de Personalización de Temas (Fase 02.1 + 06.11).
 * Permite seleccionar entre los 10 temas (OBSIDIAN, ARCTIC, MONOCHROME, BRONZE,
 * COLOR PERSONALIZADO, MINIMALISTA, ROJO SPORT, ESPECTRO, VAPORWAVE y ÁCIDO LIMA),
 * persistiendo la selección y actualizando toda la interfaz en tiempo real.
 */
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    val palette = DivineDeckTheme.palette
    val context = LocalContext.current
    val operatingMode by settingsRepository.operatingMode.collectAsState()
    val autoNightMode by settingsRepository.isAutoNightModeEnabled.collectAsState()
    val selectedTheme by settingsRepository.selectedTheme.collectAsState()
    val customAccentHex by settingsRepository.customAccentHex.collectAsState()
    val cockpitLayout by settingsRepository.cockpitLayout.collectAsState()
    val isLowriderEnabled by settingsRepository.isLowriderEnabled.collectAsState()

    // Permiso de audio del Modo Lowrider: se pide al activar el interruptor.
    // Si se deniega, el interruptor queda apagado y se muestra la nota con el
    // acceso directo a los ajustes de la app.
    var lowriderPermissionNote by remember { mutableStateOf(false) }
    val lowriderPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            lowriderPermissionNote = false
            settingsRepository.setLowriderEnabled(true)
            // setLowriderEnabled(true) es no-op si el interruptor ya estaba
            // en ON: el arranque directo evita que el motor se quede parado.
            LowriderBeatEngine.start(context.applicationContext)
        } else {
            lowriderPermissionNote = true
            settingsRepository.setLowriderEnabled(false)
        }
    }
    val lowriderAudioGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    // Estado vivo del motor Lowrider: mode y lastError son StateFlow y se
    // observan directamente; isRunning es @Volatile y se refleja en mode.
    // El permiso se revalida cada 500 ms: si se concede en los Ajustes del
    // sistema y se vuelve a la app, la nota desaparece sola.
    val lowriderMode by LowriderBeatEngine.mode.collectAsState()
    val lowriderLastError by LowriderBeatEngine.lastError.collectAsState()
    val lowriderRunning = lowriderMode != LowriderBeatEngine.Mode.STOPPED
    var lowriderAudioGrantedNow by remember { mutableStateOf(lowriderAudioGranted) }
    LaunchedEffect(Unit) {
        while (true) {
            lowriderAudioGrantedNow = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            delay(500)
        }
    }

    // Estado del modelo de voz sin internet (Vosk): se revalida contra el
    // disco al entrar en Ajustes, por si ya se descargó en otra sesión.
    val voskModelState by VoskModelManager.state.collectAsState()
    LaunchedEffect(Unit) {
        VoskModelManager.refreshState(context.applicationContext)
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // COLUMNA 1: PERSONALIZACIÓN DE TEMAS Y COLOR DE ACENTO
        AutomotiveCard(
            modifier = Modifier
                .weight(1.45f)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "PERSONALIZACIÓN VISUAL Y TEMAS",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Selecciona la atmósfera visual del Cockpit. Se restaura automáticamente al iniciar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                    }
                }

                // Selector de los 10 temas (5 clásicos + 5 Panorámico: Minimalista, Rojo Sport, Espectro, Vaporwave, Ácido Lima)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemePreset.entries.forEach { preset ->
                        val isSelected = selectedTheme == preset
                        val previewPalette = buildPaletteForTheme(preset, customAccentHex)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) palette.elevatedSurface else palette.background.copy(alpha = 0.6f)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) palette.accent else palette.borderSubtle,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { settingsRepository.setSelectedTheme(preset) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Muestra triple de colores del tema (Fondo, Superficie, Acento)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy((-6).dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(previewPalette.background)
                                            .border(1.dp, previewPalette.border, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(previewPalette.surface)
                                            .border(1.dp, previewPalette.border, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(previewPalette.accent)
                                            .border(1.dp, Color.Black.copy(alpha = 0.4f), CircleShape)
                                    )
                                }

                                Column {
                                    Text(
                                        text = preset.displayName.uppercase(),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        ),
                                        color = if (isSelected) palette.accent else palette.textPrimary
                                    )
                                    Text(
                                        text = preset.description,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                        color = palette.textSecondary
                                    )
                                }
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(palette.accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Tema activo",
                                        tint = palette.onAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Apartado Distribución del Cockpit (Fase 06.11): Original vs Panorámica.
                // Solo cambia la distribución y el orden de los elementos; nunca elimina funciones.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dashboard,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "DISTRIBUCIÓN",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Elige cómo se ordenan el vehículo, el velocímetro y los widgets en la pantalla principal. Se aplica al instante y se guarda.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary
                            )
                        }
                    }

                    CockpitLayout.entries.forEach { layout ->
                        val isSelected = cockpitLayout == layout
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) palette.elevatedSurface else palette.background.copy(alpha = 0.6f)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) palette.accent else palette.borderSubtle,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { settingsRepository.setCockpitLayout(layout) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = layout.displayName.uppercase(),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = if (isSelected) palette.accent else palette.textPrimary
                                )
                                Text(
                                    text = layout.description,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                    color = palette.textSecondary
                                )
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(palette.accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Distribución activa",
                                        tint = palette.onAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Panel de Color de Acento Personalizado
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = palette.elevatedSurface.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "COLOR DE ACENTO PERSONALIZADO",
                                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.8.sp),
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Protección de contraste activa",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.statusPositive
                            )
                        }

                        // Muestras curadas de selección rápida
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CuratedCustomAccents.forEach { option ->
                                val isSelectedSwatch =
                                    selectedTheme == ThemePreset.CUSTOM && customAccentHex == option.hex
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(option.color)
                                        .border(
                                            width = if (isSelectedSwatch) 2.5.dp else 1.dp,
                                            color = if (isSelectedSwatch) palette.textPrimary else Color.Black.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            settingsRepository.setCustomAccentHex(option.hex)
                                            settingsRepository.setSelectedTheme(ThemePreset.CUSTOM)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelectedSwatch) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = option.name,
                                            tint = Color.Black,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Ajuste fino RGB con bloqueo de legibilidad
                        val currentCustomColor = ensureReadableAccent(Color(customAccentHex))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            RgbChannelSlider(
                                label = "R",
                                value = currentCustomColor.red,
                                activeColor = palette.accent,
                                onValueChange = { newRed ->
                                    val updated = ensureReadableAccent(
                                        Color(newRed, currentCustomColor.green, currentCustomColor.blue, 1f)
                                    )
                                    settingsRepository.setCustomAccentHex(updated.toArgb().toLong() and 0xFFFFFFFFL)
                                    settingsRepository.setSelectedTheme(ThemePreset.CUSTOM)
                                }
                            )
                            RgbChannelSlider(
                                label = "G",
                                value = currentCustomColor.green,
                                activeColor = palette.accent,
                                onValueChange = { newGreen ->
                                    val updated = ensureReadableAccent(
                                        Color(currentCustomColor.red, newGreen, currentCustomColor.blue, 1f)
                                    )
                                    settingsRepository.setCustomAccentHex(updated.toArgb().toLong() and 0xFFFFFFFFL)
                                    settingsRepository.setSelectedTheme(ThemePreset.CUSTOM)
                                }
                            )
                            RgbChannelSlider(
                                label = "B",
                                value = currentCustomColor.blue,
                                activeColor = palette.accent,
                                onValueChange = { newBlue ->
                                    val updated = ensureReadableAccent(
                                        Color(currentCustomColor.red, currentCustomColor.green, newBlue, 1f)
                                    )
                                    settingsRepository.setCustomAccentHex(updated.toArgb().toLong() and 0xFFFFFFFFL)
                                    settingsRepository.setSelectedTheme(ThemePreset.CUSTOM)
                                }
                            )
                        }
                    }
                }
            }
        }

        // COLUMNA 2: MODO DE OPERACIÓN Y ESPECIFICACIONES DEL SISTEMA
        AutomotiveCard(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column {
                    Text(
                        text = "SISTEMA Y VEHÍCULO",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = palette.textPrimary
                    )
                    Text(
                        text = "Preferencias de operación automotriz y hardware.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                }

                // Selector de Modo de Operación
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Modo de Operación",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AutomotivePrimaryButton(
                            onClick = { settingsRepository.setOperatingMode(OperatingMode.AUTOMOTIVE_COCKPIT) },
                            containerColor = if (operatingMode == OperatingMode.AUTOMOTIVE_COCKPIT) palette.accent else palette.elevatedSurface,
                            contentColor = if (operatingMode == OperatingMode.AUTOMOTIVE_COCKPIT) palette.onAccent else palette.textPrimary,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Automóvil")
                        }

                        AutomotiveSecondaryButton(
                            onClick = { settingsRepository.setOperatingMode(OperatingMode.PERSONAL_TABLET) },
                            borderColor = if (operatingMode == OperatingMode.PERSONAL_TABLET) palette.accent else palette.border,
                            contentColor = if (operatingMode == OperatingMode.PERSONAL_TABLET) palette.accent else palette.textPrimary,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Personal")
                        }
                    }
                    Text(
                        text = operatingMode.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                }

                // Tema oscuro permanente
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = palette.elevatedSurface,
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Atmósfera Nocturna Permanente",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Evita deslumbramientos en cabina",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary
                            )
                        }
                        Switch(
                            checked = autoNightMode,
                            onCheckedChange = { settingsRepository.setAutoNightMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = palette.onAccent,
                                checkedTrackColor = palette.accent
                            )
                        )
                    }
                }

                // Modo Lowrider: el Chevy brinca con los hidráulicos al ritmo de
                // la música. Necesita el permiso de audio para escuchar el beat
                // (Visualizer sobre la mezcla del sistema); no se graba ni se
                // guarda ningún audio.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DIVERSIÓN",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = palette.textPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = palette.elevatedSurface,
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Modo Lowrider",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = palette.textPrimary
                                    )
                                    Text(
                                        text = "El Chevy brinca con los hidráulicos al ritmo de la música",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.textSecondary
                                    )
                                }
                                Switch(
                                    checked = isLowriderEnabled,
                                    onCheckedChange = { wantOn ->
                                        if (!wantOn) {
                                            lowriderPermissionNote = false
                                            settingsRepository.setLowriderEnabled(false)
                                        } else if (lowriderAudioGrantedNow) {
                                            settingsRepository.setLowriderEnabled(true)
                                        } else {
                                            lowriderPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = palette.onAccent,
                                        checkedTrackColor = palette.accent
                                    )
                                )
                            }
                            if (lowriderPermissionNote || (isLowriderEnabled && !lowriderAudioGrantedNow)) {
                                Text(
                                    text = "Activa el permiso de micrófono/audio para usar el Lowrider",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textSecondary
                                )
                                AutomotiveSecondaryButton(
                                    onClick = {
                                        try {
                                            context.startActivity(
                                                Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.fromParts("package", context.packageName, null)
                                                )
                                            )
                                        } catch (_: Exception) {
                                        }
                                    },
                                    borderColor = palette.accent,
                                    contentColor = palette.accent,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Abrir ajustes de la app")
                                }
                            }
                            // Estado del motor (solo con permiso concedido).
                            if (isLowriderEnabled && lowriderAudioGrantedNow) {
                                // Copia local: lowriderLastError es una propiedad delegada
                                // (by collectAsState) y Kotlin no permite smart cast sobre ella.
                                val lastError = lowriderLastError
                                when (lowriderMode) {
                                    LowriderBeatEngine.Mode.BEAT -> {
                                        Text(
                                            text = "Escuchando el beat",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = palette.statusPositive
                                        )
                                    }
                                    LowriderBeatEngine.Mode.CLOCK -> {
                                        Text(
                                            text = "Modo ritmo: el carro brinca a ritmo fijo mientras suena música",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = palette.statusPositive
                                        )
                                        if (lastError != null) {
                                            Text(
                                                text = lastError,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = palette.textSecondary
                                            )
                                        }
                                    }
                                    LowriderBeatEngine.Mode.STOPPED -> {
                                        if (lastError != null) {
                                            Text(
                                                text = "No se pudo escuchar la música en este dispositivo",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = palette.textSecondary
                                            )
                                            Text(
                                                text = lastError,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = palette.textSecondary
                                            )
                                        } else {
                                            Text(
                                                text = "El motor está detenido",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = palette.textSecondary
                                            )
                                        }
                                    }
                                }
                                if (!lowriderRunning) {
                                    AutomotiveSecondaryButton(
                                        onClick = {
                                            LowriderBeatEngine.start(context.applicationContext)
                                        },
                                        borderColor = palette.accent,
                                        contentColor = palette.accent,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Reintentar")
                                    }
                                }
                            }
                        }
                    }

                    // Modelo de voz sin internet (Vosk): se descarga una sola
                    // vez (~40 MB) y el micrófono funciona aunque no haya
                    // internet. Sin modelo, el asistente solo escucha con
                    // internet.
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = palette.elevatedSurface,
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Modelo de voz sin internet",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary
                            )
                            val modelState = voskModelState
                            when (modelState) {
                                is VoskModelState.Ready -> {
                                    Text(
                                        text = "Modelo de voz sin internet: listo",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.statusPositive
                                    )
                                }
                                is VoskModelState.Downloading -> {
                                    Text(
                                        text = if (modelState.percent >= 0) {
                                            "Modelo de voz sin internet: descargando ${modelState.percent}%"
                                        } else {
                                            "Modelo de voz sin internet: descargando…"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.textSecondary
                                    )
                                }
                                is VoskModelState.Error -> {
                                    Text(
                                        text = "Modelo de voz sin internet: error",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.textSecondary
                                    )
                                    Text(
                                        text = modelState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textSecondary
                                    )
                                    AutomotiveSecondaryButton(
                                        onClick = {
                                            VoskModelManager.retry(context.applicationContext)
                                        },
                                        borderColor = palette.accent,
                                        contentColor = palette.accent,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Reintentar")
                                    }
                                }
                                is VoskModelState.NotDownloaded -> {
                                    Text(
                                        text = "Modelo de voz sin internet: no descargado",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.textSecondary
                                    )
                                    AutomotiveSecondaryButton(
                                        onClick = {
                                            VoskModelManager.ensureModel(context.applicationContext)
                                        },
                                        borderColor = palette.accent,
                                        contentColor = palette.accent,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Descargar modelo")
                                    }
                                }
                            }
                        }
                    }
                }

                // Ficha Vehículo y Dispositivo
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = palette.elevatedSurface,
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "CHEVROLET CHEVY C2 2009",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.accent
                        )
                        Text(
                            text = "Motor 1.6L MPFI SOHC 8V • 100 HP • 102 lb-ft • Tanque 46L",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "SAMSUNG GALAXY TAB A11 (SM-X133)",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.accentSecondary
                        )
                        Text(
                            text = "Pantalla 8.7\" • 1340 × 800 px Landscape • Mali-G57 MC2",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RgbChannelSlider(
    label: String,
    value: Float,
    activeColor: Color,
    onValueChange: (Float) -> Unit
) {
    val palette = DivineDeckTheme.palette
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = palette.textSecondary,
            modifier = Modifier.width(14.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier
                .weight(1f)
                .height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = palette.background
            )
        )
        Text(
            text = "${(value * 255).toInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
            modifier = Modifier.width(28.dp)
        )
    }
}
