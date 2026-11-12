package com.divinedeck.launcher.feature.navcenter.data

import android.content.Context
import com.divinedeck.launcher.feature.navcenter.domain.NavLatLng
import com.divinedeck.launcher.feature.navcenter.domain.NavPlace
import com.divinedeck.launcher.feature.navcenter.domain.PoiKind

/**
 * Casa y Trabajo guardados en SharedPreferences (mismo patrón que
 * SettingsRepositoryImpl): latitud, longitud, nombre y detalle en un string.
 * Sin cuentas ni nube; vive en la tablet.
 */
class NavPlacesPreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getHome(): NavPlace? = readPlace(KEY_HOME, PoiKind.HOME)

    fun setHome(place: NavPlace?) = writePlace(KEY_HOME, place)

    fun getWork(): NavPlace? = readPlace(KEY_WORK, PoiKind.WORK)

    fun setWork(place: NavPlace?) = writePlace(KEY_WORK, place)

    private fun readPlace(key: String, kind: PoiKind): NavPlace? {
        val raw = prefs.getString(key, null) ?: return null
        val parts = raw.split("|")
        if (parts.size < 4) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        return NavPlace(
            id = "saved:$key",
            name = parts[2].ifBlank { if (kind == PoiKind.HOME) "Casa" else "Trabajo" },
            detail = parts[3],
            latLng = NavLatLng(latitude = lat, longitude = lon),
            kind = kind
        )
    }

    private fun writePlace(key: String, place: NavPlace?) {
        val editor = prefs.edit()
        if (place == null) {
            editor.remove(key)
        } else {
            val safeName = place.name.replace("|", " ")
            val safeDetail = place.detail.replace("|", " ")
            editor.putString(
                key,
                "${place.latLng.latitude}|${place.latLng.longitude}|$safeName|$safeDetail"
            )
        }
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "nav_places"
        private const val KEY_HOME = "home"
        private const val KEY_WORK = "work"
    }
}
