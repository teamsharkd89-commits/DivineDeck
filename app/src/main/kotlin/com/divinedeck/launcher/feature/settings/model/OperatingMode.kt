package com.divinedeck.launcher.feature.settings.model

enum class OperatingMode(val title: String, val description: String) {
    AUTOMOTIVE_COCKPIT(
        title = "Modo Automóvil",
        description = "Interfaz Cockpit con velocímetro GPS, modelo 3D, multimedia y controles vehiculares de acceso rápido."
    ),
    PERSONAL_TABLET(
        title = "Modo Personal",
        description = "Entorno para uso de la tablet fuera del automóvil con acceso directo a utilidades y biblioteca de aplicaciones."
    )
}
