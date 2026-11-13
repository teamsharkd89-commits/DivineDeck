package com.divinedeck.launcher.feature.navcenter.data

import android.content.Context
import android.content.SharedPreferences
import com.divinedeck.launcher.feature.navcenter.domain.QuickPlace
import com.divinedeck.launcher.feature.navcenter.domain.QuickPlacesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persistencia de "Mis lugares" en SharedPreferences, siguiendo la
 * convención de SettingsRepositoryImpl: StateFlow + apply().
 * Se guarda un JSONArray de {id, name, address} en una sola clave.
 */
class SharedPreferencesQuickPlacesRepository(
    context: Context? = null
) : QuickPlacesRepository {

    private val prefs: SharedPreferences? =
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _places = MutableStateFlow(loadPlaces())
    override val places: StateFlow<List<QuickPlace>> = _places.asStateFlow()

    private fun loadPlaces(): List<QuickPlace> {
        val raw = prefs?.getString(KEY_PLACES_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id").ifBlank { return@mapNotNull null }
                val name = obj.optString("name").ifBlank { return@mapNotNull null }
                val address = obj.optString("address").ifBlank { return@mapNotNull null }
                QuickPlace(id = id, name = name, address = address)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun savePlaces(list: List<QuickPlace>) {
        val arr = JSONArray()
        for (place in list) {
            arr.put(
                JSONObject().apply {
                    put("id", place.id)
                    put("name", place.name)
                    put("address", place.address)
                }
            )
        }
        prefs?.edit()?.putString(KEY_PLACES_JSON, arr.toString())?.apply()
    }

    override fun addPlace(name: String, address: String): QuickPlace {
        val place = QuickPlace(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            address = address.trim()
        )
        val updated = _places.value + place
        _places.value = updated
        savePlaces(updated)
        return place
    }

    override fun updatePlace(id: String, name: String, address: String) {
        val updated = _places.value.map {
            if (it.id == id) it.copy(name = name.trim(), address = address.trim()) else it
        }
        _places.value = updated
        savePlaces(updated)
    }

    override fun deletePlace(id: String) {
        val updated = _places.value.filterNot { it.id == id }
        _places.value = updated
        savePlaces(updated)
    }

    companion object {
        const val PREFS_NAME = "divinedeck_quick_places"
        const val KEY_PLACES_JSON = "key_quick_places_json"
    }
}
