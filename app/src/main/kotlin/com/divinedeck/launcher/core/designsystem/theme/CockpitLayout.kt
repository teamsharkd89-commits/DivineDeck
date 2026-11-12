package com.divinedeck.launcher.core.designsystem.theme

/**
 * Distribuciones del Cockpit principal de DIVINE DECK (Fase 06.11).
 *
 * - ORIGINAL: la distribución actual — vehículo a la izquierda con el
 *   velocímetro digital dentro de su tarjeta, y dos widgets apilados
 *   verticalmente a la derecha.
 * - PANORAMICA: el vehículo como pancarta ancha arriba ocupando todo el
 *   ancho; abajo, tres tarjetas en fila: velocímetro analógico, widget
 *   superior y widget inferior. Es la distribución del mockup Panorámico
 *   aprobado por Daniel el 2026-11-11.
 *
 * Solo cambia la distribución y el orden de los elementos; nunca se
 * elimina ni se duplica ninguna función.
 */
enum class CockpitLayout(
    val id: String,
    val displayName: String,
    val description: String
) {
    ORIGINAL(
        id = "ORIGINAL",
        displayName = "Original",
        description = "La distribución actual: vehículo a la izquierda con el velocímetro dentro de su tarjeta, y dos widgets apilados a la derecha."
    ),
    PANORAMICA(
        id = "PANORAMICA",
        displayName = "Panorámica",
        description = "El vehículo como pancarta ancha arriba; abajo, velocímetro analógico, widget superior y widget inferior en tres tarjetas."
    );

    companion object {
        /** Distribución por defecto al instalar / sin preferencia guardada. */
        val DEFAULT: CockpitLayout = ORIGINAL

        fun fromId(id: String?): CockpitLayout {
            return entries.find { it.id == id } ?: DEFAULT
        }
    }
}
