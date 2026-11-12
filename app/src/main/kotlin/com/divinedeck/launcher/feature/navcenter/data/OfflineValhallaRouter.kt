package com.divinedeck.launcher.feature.navcenter.data

import android.content.Context
import android.util.Log
import com.stadiamaps.ferrostar.core.CustomRouteProvider
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.config.ValhallaConfigFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteRequest
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.createOsrmResponseParser
import uniffi.ferrostar.createValhallaRequestGenerator
import java.time.Duration

/**
 * Motor Valhalla en el dispositivo (valhalla-mobile 0.6.4): calcula rutas
 * contra el grafo de Tepic descargado, sin internet y sin servidor.
 * La instancia es cara de construir (mapea el .tar en memoria): se crea
 * una sola vez y se reutiliza; [close] libera el actor nativo.
 */
class OfflineValhallaRouter(context: Context) {
    private val appContext = context.applicationContext
    private var engine: Valhalla? = null

    fun isPackInstalled(): Boolean {
        val file = java.io.File(
            java.io.File(appContext.filesDir, OfflineNavConstants.OFFLINE_DIR),
            OfflineNavConstants.VALHALLA_TAR_NAME
        )
        return file.exists() && file.length() > 0
    }

    /**
     * Construye el motor si el paquete está instalado. Devuelve null (sin
     * lanzar) si el paquete falta o está corrupto: el llamador muestra el
     * mensaje de "paquete offline" en ese caso.
     */
    @Synchronized
    fun ensureEngine(): Valhalla? {
        engine?.let { return it }
        if (!isPackInstalled()) return null
        return try {
            val tar = java.io.File(
                java.io.File(appContext.filesDir, OfflineNavConstants.OFFLINE_DIR),
                OfflineNavConstants.VALHALLA_TAR_NAME
            )
            val config = ValhallaConfigFactory.usingTileExtract(tar.absolutePath)
            Valhalla(appContext, config).also { engine = it }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo arrancar Valhalla offline: ${e.message}", e)
            null
        }
    }

    /** Petición Valhalla en crudo (JSON in, JSON OSRM out). Bloqueante: llamar en IO. */
    suspend fun routeRaw(requestJson: String): String = withContext(Dispatchers.IO) {
        val valhalla = ensureEngine()
            ?: throw IllegalStateException("paquete de rutas offline no instalado")
        valhalla.routeRaw(requestJson)
    }

    fun close() {
        try {
            engine?.close()
        } catch (_: Exception) {
        }
        engine = null
    }

    companion object {
        private const val TAG = "OfflineValhalla"
    }
}

/**
 * Proveedor de rutas único de DivineDeck: genera la petición con el mismo
 * generador Valhalla que usaba el proveedor online (mismo endpoint, perfil
 * "auto" y opciones: km, español), y cambia solo el transporte:
 * - Con internet: POST al servidor gratuito FOSSGIS (como antes).
 * - Sin internet: el mismo JSON al motor Valhalla del dispositivo.
 * La respuesta OSRM se parsea en ambos casos con el parser de Ferrostar,
 * así el recálculo por desvío también funciona sin internet.
 */
class DivineDeckRouteProvider(
    private val isOnline: () -> Boolean,
    private val offlineRouter: OfflineValhallaRouter,
    private val onRouteComputed: (offline: Boolean) -> Unit = {}
) : CustomRouteProvider {

    private val requestGenerator = createValhallaRequestGenerator(
        VALHALLA_ENDPOINT,
        "auto",
        """{"units":"kilometers","language":"es-ES"}"""
    )
    private val responseParser = createOsrmResponseParser(6u)

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(15))
        .build()

    override suspend fun getRoutes(
        userLocation: UserLocation,
        waypoints: List<Waypoint>
    ): List<Route> {
        val request = requestGenerator.generateRequest(userLocation, waypoints)
        val post = request as? RouteRequest.HttpPost
            ?: throw IllegalStateException("el generador Valhalla no devolvió un POST")
        val bodyJson = post.body.decodeToString()
        val responseJson = if (isOnline()) {
            withContext(Dispatchers.IO) { postOnline(post, bodyJson) }
                .also { onRouteComputed(false) }
        } else {
            offlineRouter.routeRaw(bodyJson)
                .also { onRouteComputed(true) }
        }
        return responseParser.parseResponse(responseJson.toByteArray(Charsets.UTF_8))
    }

    private fun postOnline(post: RouteRequest.HttpPost, bodyJson: String): String {
        val contentType = post.headers["Content-Type"] ?: "application/json"
        val request = Request.Builder()
            .url(post.url)
            .post(bodyJson.toRequestBody(contentType.toMediaType()))
            .header("User-Agent", "DivineDeck/1.0 (Android car launcher)")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Valhalla en línea devolvió HTTP ${response.code}")
            }
            return response.body?.string()
                ?: throw IllegalStateException("Valhalla en línea devolvió una respuesta vacía")
        }
    }

    companion object {
        private const val VALHALLA_ENDPOINT = "https://valhalla1.openstreetmap.de/route"
    }
}
