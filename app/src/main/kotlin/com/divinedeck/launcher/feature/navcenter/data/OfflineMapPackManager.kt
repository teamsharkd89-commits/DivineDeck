package com.divinedeck.launcher.feature.navcenter.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.compose.offline.AndroidOfflineManager
import org.maplibre.compose.offline.DownloadProgress
import org.maplibre.compose.offline.DownloadStatus
import org.maplibre.compose.offline.OfflineManager
import org.maplibre.compose.offline.OfflinePack
import org.maplibre.compose.offline.OfflinePackDefinition
import org.maplibre.spatialk.geojson.BoundingBox

/**
 * Paquete de mapa offline de Tepic con MapLibre Compose 0.13.0
 * (OfflineManager / OfflinePack, verificado en el AAR: no existe una clase
 * `OfflineStorage` en esta versión; la API es AndroidOfflineManager).
 *
 * Descarga ~1.800 teselas del estilo Liberty de OpenFreeMap (z10–z15,
 * ~4 MB: dentro del límite de su ToS, ≤3.000 teselas / 50 MB, con el
 * User-Agent propio que ya usa la app). Una vez descargado, el mapa usa el
 * paquete automáticamente cuando no hay red: no hay que cambiar el estilo.
 */
sealed interface MapPackState {
    data object NotDownloaded : MapPackState
    data class Downloading(val fraction: Float) : MapPackState
    data object Ready : MapPackState
    data class Error(val message: String) : MapPackState
}

class OfflineMapPackManager(context: Context) {
    private val appContext = context.applicationContext
    private val manager: OfflineManager = AndroidOfflineManager(appContext)

    private val _state = MutableStateFlow<MapPackState>(MapPackState.NotDownloaded)
    val state: StateFlow<MapPackState> = _state.asStateFlow()

    /** Busca el paquete de Tepic entre los descargados (por su metadata). */
    fun findPack(): OfflinePack? {
        return try {
            manager.packs.firstOrNull { pack ->
                pack.metadata?.decodeToString() == OfflineNavConstants.MAP_PACK_METADATA
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron listar los paquetes offline: ${e.message}")
            null
        }
    }

    fun isReady(): Boolean = _state.value is MapPackState.Ready

    /** Crea el paquete si falta y reanuda su descarga. Llamar solo con internet. */
    suspend fun ensurePack() {
        try {
            val existing = findPack()
            val pack = existing ?: manager.create(
                OfflinePackDefinition.TilePyramid(
                    OfflineNavConstants.MAP_STYLE_URL,
                    BoundingBox(
                        OfflineNavConstants.MAP_WEST,
                        OfflineNavConstants.MAP_SOUTH,
                        OfflineNavConstants.MAP_EAST,
                        OfflineNavConstants.MAP_NORTH
                    ),
                    OfflineNavConstants.MAP_MIN_ZOOM,
                    OfflineNavConstants.MAP_MAX_ZOOM
                ),
                OfflineNavConstants.MAP_PACK_METADATA.toByteArray()
            )
            manager.resume(pack)
            pollProgress(pack)
        } catch (e: Exception) {
            Log.w(TAG, "Descarga del mapa offline falló: ${e.message}", e)
            _state.value = MapPackState.Error("No se pudo descargar el mapa offline")
        }
    }

    /** Refresca el estado sin descargar (al entrar a la pantalla). */
    fun refreshState() {
        val pack = findPack()
        if (pack == null) {
            _state.value = MapPackState.NotDownloaded
            return
        }
        _state.value = when (val progress = pack.downloadProgress) {
            is DownloadProgress.Healthy -> {
                if (progress.status == DownloadStatus.Complete) {
                    MapPackState.Ready
                } else {
                    MapPackState.Downloading(fractionOf(progress))
                }
            }
            else -> MapPackState.Downloading(0f)
        }
    }

    private suspend fun pollProgress(pack: OfflinePack) {
        while (true) {
            val progress = pack.downloadProgress
            if (progress is DownloadProgress.Healthy) {
                if (progress.status == DownloadStatus.Complete) {
                    _state.value = MapPackState.Ready
                    return
                }
                _state.value = MapPackState.Downloading(fractionOf(progress))
            } else if (progress is DownloadProgress.Error) {
                _state.value = MapPackState.Error(progress.message ?: "Error al descargar el mapa")
                return
            }
            delay(1_000)
        }
    }

    private fun fractionOf(progress: DownloadProgress.Healthy): Float {
        val required = progress.requiredResourceCount
        if (required <= 0) return 0f
        return (progress.completedResourceCount.toFloat() / required.toFloat())
            .coerceIn(0f, 1f)
    }

    companion object {
        private const val TAG = "OfflineMapPack"
    }
}
