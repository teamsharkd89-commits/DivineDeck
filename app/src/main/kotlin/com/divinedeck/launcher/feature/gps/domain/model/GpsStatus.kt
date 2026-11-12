package com.divinedeck.launcher.feature.gps.domain.model

/**
 * Estados visuales del subsistema GPS en DIVINE DECK (Fase 4.0).
 *
 * 1. GPS NO DISPONIBLE: Permisos no concedidos, proveedor GPS desactivado o hardware ausente.
 * 2. BUSCANDO GPS: Permisos y proveedor activos, esperando fijación satelital inicial o reconexión.
 * 3. GPS CONECTADO: Señal satelital óptima con precisión adecuada y lectura de velocidad confiable.
 * 4. SEÑAL DÉBIL: Fijación GPS recibida pero con precisión horizontal degradada.
 * 5. VELOCIDAD NO FIABLE: Lecturas anómalas, saltos bruscos o datos desactualizados.
 */
enum class GpsStatus(val label: String) {
    NOT_AVAILABLE("GPS NO DISPONIBLE"),
    SEARCHING("BUSCANDO GPS"),
    CONNECTED("GPS CONECTADO"),
    WEAK_SIGNAL("SEÑAL DÉBIL"),
    UNRELIABLE_SPEED("VELOCIDAD NO FIABLE")
}
