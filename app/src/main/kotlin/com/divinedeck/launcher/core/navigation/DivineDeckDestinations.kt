package com.divinedeck.launcher.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

enum class DivineDeckRoute(
    val title: String,
    val icon: ImageVector,
    val subtitle: String
) {
    COCKPIT("Cockpit", Icons.Default.Home, "Tablero Principal"),
    MUSIC("Música", Icons.Default.MusicNote, "Centro Multimedia"),
    NAVIGATION("Navegación", Icons.Default.Map, "Ruta y Mapas"),
    GARAGE_3D("Garaje 3D", Icons.Default.DirectionsCar, "Chevy C2 2009"),
    WIDGETS("Widgets", Icons.Default.Widgets, "Centro de Widgets"),
    APPS("Aplicaciones", Icons.Default.ViewAgenda, "App Center"),
    SETTINGS("Ajustes", Icons.Default.Settings, "Configuración del Sistema")
}
