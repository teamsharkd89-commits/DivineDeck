package com.divinedeck.launcher.feature.navcenter.domain

/**
 * Destino rápido configurable por Daniel ("Mis lugares").
 * Nombre que se dice por voz + dirección que se manda a Google Maps.
 */
data class QuickPlace(
    val id: String,
    val name: String,
    val address: String
)
