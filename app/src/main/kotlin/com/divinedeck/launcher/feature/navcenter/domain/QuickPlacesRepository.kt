package com.divinedeck.launcher.feature.navcenter.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * Repositorio de "Mis lugares": destinos rápidos configurables
 * (nombre + dirección), persistidos en SharedPreferences.
 */
interface QuickPlacesRepository {
    val places: StateFlow<List<QuickPlace>>

    fun addPlace(name: String, address: String): QuickPlace
    fun updatePlace(id: String, name: String, address: String)
    fun deletePlace(id: String)
}
