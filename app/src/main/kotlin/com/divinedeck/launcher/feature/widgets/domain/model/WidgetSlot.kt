package com.divinedeck.launcher.feature.widgets.domain.model

/**
 * Candidatos de widgets para los espacios del Cockpit.
 * Conserva identificadores para retrocompatibilidad y migración segura en SharedPreferences (Fase 05.2.1).
 */
enum class WidgetCandidateType(
    val id: String,
    val displayName: String,
    val categorySubtitle: String,
    val providerHint: String
) {
    NONE(
        id = "NONE",
        displayName = "Espacio Vacío",
        categorySubtitle = "Sin widget asignado",
        providerHint = "Toca para seleccionar un widget"
    ),
    GENERIC_WIDGET(
        id = "GENERIC_WIDGET",
        displayName = "Widget Android",
        categorySubtitle = "Widget personalizado",
        providerHint = "AppWidgetManager • Proveedor Universal"
    ),
    BITCHORD_MUSIC(
        id = "BITCHORD_MUSIC",
        displayName = "Bitchord Music",
        categorySubtitle = "Control Multimedia Automotriz",
        providerHint = "Widget Android • Bitchord Audio (4×2)"
    ),
    FUEL_SENSE(
        id = "FUEL_SENSE",
        displayName = "Fuel Sense",
        categorySubtitle = "Monitoreo de Combustible",
        providerHint = "Widget Android • Fuel Sense (4×2)"
    ),
    GOOGLE_MAPS(
        id = "GOOGLE_MAPS",
        displayName = "Google Maps",
        categorySubtitle = "Tráfico y Navegación",
        providerHint = "Widget Android • com.google.android.apps.maps"
    ),
    WEATHER(
        id = "WEATHER",
        displayName = "Clima",
        categorySubtitle = "Condiciones Meteorológicas",
        providerHint = "Widget Android • Proveedor de Clima"
    ),
    CLOCK(
        id = "CLOCK",
        displayName = "Reloj",
        categorySubtitle = "Hora y Cronógrafo",
        providerHint = "Widget Android • Reloj del Sistema"
    ),
    OTHER_ANDROID_WIDGET(
        id = "OTHER_ANDROID_WIDGET",
        displayName = "Otros Widgets Android",
        categorySubtitle = "Catálogo del Sistema",
        providerHint = "AppWidgetManager • Proveedor Instalado"
    );

    companion object {
        fun fromId(id: String?): WidgetCandidateType {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: GENERIC_WIDGET
        }

        val selectableCandidates: List<WidgetCandidateType>
            get() = entries.filter { it != NONE }
    }
}

/**
 * Modelo de datos universal de una ranura de widget en DIVINE DECK (Fase 05.2.1).
 * Ambas ranuras (Slot 1 Superior y Slot 2 Inferior) admiten cualquier widget Android compatible.
 */
data class WidgetSlot(
    val slotId: Int,
    val positionLabel: String = if (slotId == 1) "WIDGET SUPERIOR" else "WIDGET INFERIOR",
    val assignedCandidate: WidgetCandidateType = WidgetCandidateType.NONE,
    val appWidgetId: Int? = null,
    val providerPackage: String? = null,
    val providerClass: String? = null,
    val bindingStatus: WidgetBindingStatus = WidgetBindingStatus.AVAILABLE_TO_BIND,
    val discoveredProvider: DiscoveredWidgetProvider? = null,
    val title: String = if (slotId == 1) "WIDGET SUPERIOR" else "WIDGET INFERIOR",
    val appName: String? = null,
    val isFuelSenseCandidate: Boolean = slotId == 2 || assignedCandidate == WidgetCandidateType.FUEL_SENSE
) {
    val isEmpty: Boolean
        get() = appWidgetId == null || bindingStatus != WidgetBindingStatus.BOUND

    val isBound: Boolean
        get() = appWidgetId != null && bindingStatus == WidgetBindingStatus.BOUND
}
