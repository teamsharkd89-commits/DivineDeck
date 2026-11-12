package com.divinedeck.launcher.feature.navcenter.data

/**
 * Constantes del modo offline de Navegación (Tepic, Nayarit).
 *
 * Cobertura:
 * - Mapa offline (paquete MapLibre): bbox amplio -105.05,21.3,-104.7,21.7.
 * - Rutas offline (Valhalla): bbox de enrutado -105.1,21.3,-104.65,21.7, un
 *   poco más ancho para que las rutas de las afueras también funcionen.
 * Las rutas fuera de ese bbox, sin internet, no se pueden calcular.
 */
object OfflineNavConstants {
    // Mapa offline (TilePyramid de MapLibre).
    const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
    const val MAP_MIN_ZOOM = 10
    const val MAP_MAX_ZOOM = 15
    const val MAP_WEST = -105.05
    const val MAP_SOUTH = 21.3
    const val MAP_EAST = -104.7
    const val MAP_NORTH = 21.7
    const val MAP_PACK_METADATA = "divinedeck-tepic-v1"

    // Enrutado offline (Valhalla).
    const val ROUTE_WEST = -105.1
    const val ROUTE_SOUTH = 21.3
    const val ROUTE_EAST = -104.65
    const val ROUTE_NORTH = 21.7

    /** Carpeta de DivineDeck donde viven los paquetes offline descargados. */
    const val OFFLINE_DIR = "offline"

    /** Grafo Valhalla de Tepic (release asset `offline-packs`). */
    const val VALHALLA_TAR_NAME = "valhalla_tiles.tar"

    /** Catálogo de paquetes offline (release `offline-packs`). */
    const val CATALOGUE_URL =
        "https://github.com/teamsharkd89-commits/DivineDeck/releases/download/offline-packs/catalogue.json"
    const val PACKS_BASE_URL =
        "https://github.com/teamsharkd89-commits/DivineDeck/releases/download/offline-packs/"

    /** Búsqueda offline empaquetada en assets (también descargable). */
    const val SEARCH_ASSET_PATH = "offline/tepic_places.json"
    const val SEARCH_FILE_NAME = "tepic_places.json"

    fun isInsideRouteBounds(lat: Double, lon: Double): Boolean =
        lat in ROUTE_SOUTH..ROUTE_NORTH && lon in ROUTE_WEST..ROUTE_EAST

    fun isInsideMapBounds(lat: Double, lon: Double): Boolean =
        lat in MAP_SOUTH..MAP_NORTH && lon in MAP_WEST..MAP_EAST
}
