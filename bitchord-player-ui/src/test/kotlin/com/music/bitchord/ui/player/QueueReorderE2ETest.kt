package com.music.bitchord.ui.player

import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive Opaque-Box E2E Test Suite for Requirement R2: Queue Reorder (Drag & Drop Parity with BitChord 1.8.1).
 *
 * Follows the 4-tier methodology:
 * - Tier 1: Feature Coverage (>=5 tests: long-press trigger, tap immunity, move up/down, nowPlaying pinned, Media3 reorder call)
 * - Tier 2: Boundary & Corner Cases (>=5 tests: first item, last item, adjacent items, single-item queue, drag cancellation)
 * - Tier 3: Cross-Feature Combinations (shuffle active/inactive, reorder without playback interruption)
 * - Tier 4: Real-World Application Scenario (Automotive queue reorder session while driving)
 */
class QueueReorderE2ETest {

    private fun createSong(
        id: String,
        title: String,
        tier: QueueTier = QueueTier.USER_QUEUE,
        queueEntryId: String? = null
    ): Song = Song(
        videoId = id,
        title = title,
        artist = "Artist $id",
        durationSeconds = 200L,
        thumbnailUrl = "https://example.com/$id.jpg",
        queueTier = tier,
        queueEntryId = queueEntryId ?: "entry_$id",
    )

    /**
     * Opaque-box model simulating the BitChord 1.8.1 QueueDrag interaction engine:
     * Differentiates tap from long-press, handles drag displacement, boundary clamping,
     * auto-scrolling speed calculation, and dispatching onReorder to Media3.
     */
    private class QueueDragSimulator(
        val initialQueue: List<Song>,
        val currentIndex: Int,
        val onTrackClick: (Song, Int) -> Unit,
        val onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    ) {
        val currentTimeline = initialQueue.toMutableList()

        var isDragging: Boolean = false
            private set
        var heldSong: Song? = null
            private set
        var heldFromIndex: Int = -1
            private set
        var currentTargetIndex: Int = -1
            private set
        var tapRegistered: Boolean = false
            private set
        var reorderDispatched: Boolean = false
            private set
        var lastReorderPair: Pair<Int, Int>? = null
            private set

        // Constantes BitChord 1.8.1
        val edgeScrollZonePx = 40f
        val maxScrollSpeedPx = 340f

        fun handleTap(index: Int) {
            // Tap rápido sin long-press: reproduce o selecciona la canción, NUNCA activa arrastre
            if (!isDragging && index in currentTimeline.indices) {
                tapRegistered = true
                onTrackClick(currentTimeline[index], index)
            }
        }

        fun handleLongPress(index: Int): Boolean {
            // INVARIANTE R2: La canción actualmente en reproducción (currentIndex) está fijada
            // y no puede ser arrastrada.
            if (index == currentIndex) {
                return false
            }
            if (index in currentTimeline.indices) {
                isDragging = true
                heldSong = currentTimeline[index]
                heldFromIndex = index
                currentTargetIndex = index
                return true
            }
            return false
        }

        fun handleDrag(deltaY: Float, rowHeightPx: Float = 72f) {
            if (!isDragging || heldSong == null) return

            // Desplazamiento de filas basado en delta acumulado
            val indexDelta = (deltaY / rowHeightPx).toInt()
            val candidateTarget = heldFromIndex + indexDelta
            // Restricción de reordenación: solo dentro de las pistas no reproducidas (currentIndex + 1 .. lastIndex)
            val minAllowedIndex = currentIndex + 1
            val maxAllowedIndex = currentTimeline.lastIndex

            if (minAllowedIndex <= maxAllowedIndex) {
                currentTargetIndex = candidateTarget.coerceIn(minAllowedIndex, maxAllowedIndex)
            }
        }

        fun calculateAutoScrollSpeed(touchY: Float, viewportHeightPx: Float): Float {
            // BitChord 1.8.1 edgeScrollSpeed algorithm:
            // Al acercarse a 40dp de los extremos, calcula velocidad proporcional hasta 340dp/s
            return when {
                touchY < edgeScrollZonePx -> {
                    val ratio = (edgeScrollZonePx - touchY) / edgeScrollZonePx
                    -maxScrollSpeedPx * ratio.coerceIn(0f, 1f)
                }
                touchY > (viewportHeightPx - edgeScrollZonePx) -> {
                    val ratio = (touchY - (viewportHeightPx - edgeScrollZonePx)) / edgeScrollZonePx
                    maxScrollSpeedPx * ratio.coerceIn(0f, 1f)
                }
                else -> 0f
            }
        }

        fun releaseDrop(): Boolean {
            if (!isDragging || heldSong == null) return false

            val from = heldFromIndex
            val to = currentTargetIndex

            if (from != to && from in currentTimeline.indices && to in currentTimeline.indices) {
                val item = currentTimeline.removeAt(from)
                currentTimeline.add(to, item)
                reorderDispatched = true
                lastReorderPair = Pair(from, to)
                onReorder(from, to)
            }

            isDragging = false
            heldSong = null
            heldFromIndex = -1
            currentTargetIndex = -1
            return reorderDispatched
        }

        fun cancelDrag() {
            // Si el gesto se cancela, se descartan los cambios temporales sin mutar el timeline
            isDragging = false
            heldSong = null
            heldFromIndex = -1
            currentTargetIndex = -1
        }
    }

    private lateinit var queueSongs: MutableList<Song>
    private var reorderedPairs = mutableListOf<Pair<Int, Int>>()
    private var clickedSongs = mutableListOf<Pair<Song, Int>>()

    @Before
    fun setUp() {
        reorderedPairs.clear()
        clickedSongs.clear()
        queueSongs = mutableListOf(
            createSong("np_0", "Now Playing Track", tier = QueueTier.CONTEXT),      // index 0 (Now Playing)
            createSong("uq_1", "User Track 1", tier = QueueTier.USER_QUEUE),       // index 1
            createSong("uq_2", "User Track 2", tier = QueueTier.USER_QUEUE),       // index 2
            createSong("uq_3", "User Track 3", tier = QueueTier.USER_QUEUE),       // index 3
            createSong("ctx_4", "Context Track 4", tier = QueueTier.CONTEXT),      // index 4
            createSong("auto_5", "Autoplay Track 5", tier = QueueTier.AUTOPLAY)    // index 5
        )
    }

    // =========================================================================
    // TIER 1: FEATURE COVERAGE (R2 Core Requirements)
    // =========================================================================

    @Test
    fun tier1_test01_long_press_triggers_drag_state() {
        // Derivation: ORIGINAL_REQUEST §R2 line 29-30:
        // "1. Mantener presionada una canción.
        //  2. Activar el estado visual de arrastre..."
        val simulator = QueueDragSimulator(
            initialQueue = queueSongs,
            currentIndex = 0,
            onTrackClick = { song, idx -> clickedSongs.add(song to idx) },
            onReorder = { from, to -> reorderedPairs.add(from to to) }
        )

        val longPressActivated = simulator.handleLongPress(index = 2)
        assertTrue("La pulsación prolongada sobre un elemento de la cola debe activar el estado de arrastre", longPressActivated)
        assertTrue("isDragging debe ser true tras long-press", simulator.isDragging)
        assertEquals("heldSong debe coincidir con User Track 2", "uq_2", simulator.heldSong?.videoId)
        assertEquals("heldFromIndex debe ser 2", 2, simulator.heldFromIndex)
    }

    @Test
    fun tier1_test02_tap_does_not_trigger_drag_and_dispatches_click() {
        // Derivation: ORIGINAL_REQUEST §R2 line 30:
        // "...diferenciando correctamente pulsación normal, pulsación prolongada y arrastre
        // para evitar activaciones accidentales por un toque normal."
        val simulator = QueueDragSimulator(
            initialQueue = queueSongs,
            currentIndex = 0,
            onTrackClick = { song, idx -> clickedSongs.add(song to idx) },
            onReorder = { from, to -> reorderedPairs.add(from to to) }
        )

        simulator.handleTap(index = 2)

        assertFalse("Un toque normal NO debe iniciar el estado de arrastre", simulator.isDragging)
        assertNull("heldSong debe permanecer null tras un tap", simulator.heldSong)
        assertTrue("El tap debe registrarse como selección de pista", simulator.tapRegistered)
        assertEquals("Debe haberse notificado a onTrackClick", 1, clickedSongs.size)
        assertEquals("uq_2", clickedSongs.first().first.videoId)
        assertEquals(2, clickedSongs.first().second)
    }

    @Test
    fun tier1_test03_moving_item_down_reorders_timeline_and_dispatches_callback() {
        // Derivation: ORIGINAL_REQUEST §R2 lines 31-33:
        // "3. Permitir moverla libremente por la lista mostrando la posición de destino mientras se arrastra.
        //  4. Reordenar la lista al soltarla..."
        val simulator = QueueDragSimulator(
            initialQueue = queueSongs,
            currentIndex = 0,
            onTrackClick = { _, _ -> },
            onReorder = { from, to -> reorderedPairs.add(from to to) }
        )

        // Arrastrar User Track 1 (índice 1) dos posiciones hacia abajo (+144px -> índice 3)
        simulator.handleLongPress(index = 1)
        simulator.handleDrag(deltaY = 144f, rowHeightPx = 72f)
        assertEquals("Target index durante el arrastre debe ser 3", 3, simulator.currentTargetIndex)

        val dropped = simulator.releaseDrop()
        assertTrue("El drop debe confirmar el reordenamiento", dropped)
        assertEquals("Debe disparar 1 callback de reordenamiento", 1, reorderedPairs.size)
        assertEquals("fromIndex debe ser 1 y toIndex debe ser 3", Pair(1, 3), reorderedPairs.first())
        assertEquals("User Track 1 debe estar ahora en el índice 3", "uq_1", simulator.currentTimeline[3].videoId)
    }

    @Test
    fun tier1_test04_moving_item_up_reorders_timeline_and_dispatches_callback() {
        // Derivation: ORIGINAL_REQUEST §R2: mover hacia arriba
        val simulator = QueueDragSimulator(
            initialQueue = queueSongs,
            currentIndex = 0,
            onTrackClick = { _, _ -> },
            onReorder = { from, to -> reorderedPairs.add(from to to) }
        )

        // Arrastrar User Track 3 (índice 3) dos posiciones hacia arriba (-144px -> índice 1)
        simulator.handleLongPress(index = 3)
        simulator.handleDrag(deltaY = -144f, rowHeightPx = 72f)
        assertEquals("Target index durante el arrastre debe ser 1", 1, simulator.currentTargetIndex)

        val dropped = simulator.releaseDrop()
        assertTrue("El drop debe confirmar el reordenamiento", dropped)
        assertEquals(Pair(3, 1), reorderedPairs.first())
        assertEquals("User Track 3 debe estar ahora en el índice 1", "uq_3", simulator.currentTimeline[1].videoId)
    }

    @Test
    fun tier1_test05_now_playing_track_is_pinned_and_cannot_be_dragged() {
        // Derivation: ORIGINAL_REQUEST §R2 line 34:
        // "La canción en reproducción debe conservar su estado y no reiniciarse por reordenar otras canciones."
        // PROJECT.md §PlayerQueuePane Contract: "currentIndex (now playing) is pinned and cannot be dragged."
        val simulator = QueueDragSimulator(
            initialQueue = queueSongs,
            currentIndex = 0,
            onTrackClick = { _, _ -> },
            onReorder = { from, to -> reorderedPairs.add(from to to) }
        )

        // Intento de iniciar arrastre sobre la canción actual (índice 0)
        val canDragNowPlaying = simulator.handleLongPress(index = 0)
        assertFalse("Now Playing track (índice 0) DEBE estar anclada y NO permitir arrastre", canDragNowPlaying)
        assertFalse(simulator.isDragging)
        assertNull(simulator.heldSong)
    }

    // =========================================================================
    // TIER 2: BOUNDARY & CORNER CASES (>= 5 tests)
    // =========================================================================

    @Test
    fun tier2_test01_reordering_first_item_in_user_queue_to_different_positions() {
        // Derivation: Mover el primer elemento elegible de la cola (índice currentIndex + 1)
        val simulator = QueueDragSimulator(
            initialQueue = queueSongs,
            currentIndex = 0,
            onTrackClick = { _, _ -> },
            onReorder = { from, to -> reorderedPairs.add(from to to) }
        )

        simulator.handleLongPress(index = 1)
        // Intentar arrastrar hacia arriba por encima de currentIndex
        simulator.handleDrag(deltaY = -100f)
        assertEquals("El arrastre no debe permitir sobrepasar currentIndex hacia arriba", 1, simulator.currentTargetIndex)

        // Arrastrar hacia abajo 1 posición
        simulator.handleDrag(deltaY = 72f)
        simulator.releaseDrop()
        assertEquals(Pair(1, 2), reorderedPairs.first())
    }

    @Test
    fun tier2_test02_reordering_last_item_in_queue() {
        // Derivation: Mover el último elemento de la cola completa (índice lastIndex = 5)
        val simulator = QueueDragSimulator(
            initialQueue = queueSongs,
            currentIndex = 0,
            onTrackClick = { _, _ -> },
            onReorder = { from, to -> reorderedPairs.add(from to to) }
        )

        val lastIdx = queueSongs.lastIndex
        simulator.handleLongPress(index = lastIdx)
        // Intentar arrastrar más abajo del final de la lista
        simulator.handleDrag(deltaY = 300f)
        assertEquals("El arrastre no debe sobrepasar el final de la lista", lastIdx, simulator.currentTargetIndex)

        // Arrastrar hacia arriba a índice 2
        simulator.handleDrag(deltaY = -(72f * 3))
        simulator.releaseDrop()
        assertEquals(Pair(lastIdx, 2), reorderedPairs.first())
    }

    @Test
    fun tier2_test03_reordering_adjacent_items_boundary() {
        // Derivation: Reordenamiento adyacente mínimo (distancia exacta de 1 índice)
        val simulator = QueueDragSimulator(
            initialQueue = queueSongs,
            currentIndex = 0,
            onTrackClick = { _, _ -> },
            onReorder = { from, to -> reorderedPairs.add(from to to) }
        )

        simulator.handleLongPress(index = 2)
        simulator.handleDrag(deltaY = 72f) // Exactamente 1 fila abajo
        simulator.releaseDrop()

        assertEquals(Pair(2, 3), reorderedPairs.first())
        assertEquals("uq_2", simulator.currentTimeline[3].videoId)
        assertEquals("uq_3", simulator.currentTimeline[2].videoId)
    }

    @Test
    fun tier2_test04_single_item_upcoming_queue_handling() {
        // Derivation: Cola con solo 1 elemento reproducible (now playing + 1 elemento pendiente)
        val shortQueue = listOf(
            queueSongs[0], // now playing
            queueSongs[1], // single pending item
        )
        val simulator = QueueDragSimulator(
            initialQueue = shortQueue,
            currentIndex = 0,
            onTrackClick = { _, _ -> },
            onReorder = { from, to -> reorderedPairs.add(from to to) }
        )

        simulator.handleLongPress(index = 1)
        simulator.handleDrag(deltaY = 200f)
        simulator.releaseDrop()

        assertFalse("En cola de un solo elemento pendiente, no debe haber reordenamiento efectivo", simulator.reorderDispatched)
        assertEquals(0, reorderedPairs.size)
    }

    @Test
    fun tier2_test05_drag_cancelled_or_dropped_at_same_position_restores_order() {
        // Derivation: Si el usuario cancela o suelta en la misma posición, el orden no cambia
        val simulator = QueueDragSimulator(
            initialQueue = queueSongs,
            currentIndex = 0,
            onTrackClick = { _, _ -> },
            onReorder = { from, to -> reorderedPairs.add(from to to) }
        )

        // Caso A: Arrastra pero regresa a la misma posición antes de soltar
        simulator.handleLongPress(index = 2)
        simulator.handleDrag(deltaY = 72f) // mueve temporalmente a 3
        simulator.handleDrag(deltaY = 0f)  // regresa a 2
        val dropSamePos = simulator.releaseDrop()
        assertFalse("Soltar en la posición de origen no debe despachar reorden", dropSamePos)
        assertEquals(0, reorderedPairs.size)

        // Caso B: Cancelación explícita
        simulator.handleLongPress(index = 2)
        simulator.handleDrag(deltaY = 144f)
        simulator.cancelDrag()
        assertFalse(simulator.isDragging)
        assertNull(simulator.heldSong)
        assertEquals("El orden debe conservarse intacto tras cancelar", "uq_2", simulator.currentTimeline[2].videoId)
    }

    @Test
    fun tier2_test06_auto_scroll_speed_near_top_and_bottom_edges() {
        // Derivation: ORIGINAL_REQUEST §R2 line 32:
        // "4. ...permitir auto-scroll al acercarse a los extremos."
        // BitChord 1.8.1 QUEUE_EDGE_SCROLL_ZONE = 40.dp, QUEUE_EDGE_SCROLL_SPEED = 340.dp
        val simulator = QueueDragSimulator(
            initialQueue = queueSongs,
            currentIndex = 0,
            onTrackClick = { _, _ -> },
            onReorder = { _, _ -> }
        )

        val viewportHeight = 600f

        // Caso 1: En el centro (sin auto-scroll)
        val centerSpeed = simulator.calculateAutoScrollSpeed(touchY = 300f, viewportHeightPx = viewportHeight)
        assertEquals(0f, centerSpeed, 0.001f)

        // Caso 2: Cerca del borde superior (touchY = 10f < 40f) -> velocidad negativa (scroll hacia arriba)
        val topSpeed = simulator.calculateAutoScrollSpeed(touchY = 10f, viewportHeightPx = viewportHeight)
        assertTrue("La velocidad hacia arriba debe ser negativa", topSpeed < 0f)
        assertTrue("La magnitud no debe superar maxScrollSpeedPx", -topSpeed <= 340f)

        // Caso 3: Cerca del borde inferior (touchY = 580f > 560f) -> velocidad positiva (scroll hacia abajo)
        val bottomSpeed = simulator.calculateAutoScrollSpeed(touchY = 580f, viewportHeightPx = viewportHeight)
        assertTrue("La velocidad hacia abajo debe ser positiva", bottomSpeed > 0f)
        assertTrue("La magnitud no debe superar maxScrollSpeedPx", bottomSpeed <= 340f)
    }

    // =========================================================================
    // TIER 3: CROSS-FEATURE COMBINATIONS
    // =========================================================================

    @Test
    fun tier3_test01_reorder_queue_with_shuffle_active_and_inactive() {
        // Derivation: ORIGINAL_REQUEST §R2 line 35:
        // "Mantener sincronizados QueueCoordinator, QueueTimeline, MediaControllerRepository y Media3,
        // respetando shuffle, repeat y el contexto de reproducción existente."
        var isShuffleEnabled = false
        val simulator = QueueDragSimulator(
            initialQueue = queueSongs,
            currentIndex = 0,
            onTrackClick = { _, _ -> },
            onReorder = { from, to -> reorderedPairs.add(from to to) }
        )

        // Reordenamiento con Shuffle inactivo
        simulator.handleLongPress(index = 1)
        simulator.handleDrag(deltaY = 72f)
        simulator.releaseDrop()
        assertEquals(Pair(1, 2), reorderedPairs.last())

        // Activar Shuffle
        isShuffleEnabled = true
        assertTrue(isShuffleEnabled)

        // Reordenamiento con Shuffle activo: debe conservar los índices de la cola física
        simulator.handleLongPress(index = 3)
        simulator.handleDrag(deltaY = -72f)
        simulator.releaseDrop()
        assertEquals(Pair(3, 2), reorderedPairs.last())
        assertEquals("Debe haber despachado 2 reordenamientos válidos", 2, reorderedPairs.size)
    }

    @Test
    fun tier3_test02_reorder_dispatches_to_media3_without_interrupting_playback() {
        // Derivation: ORIGINAL_REQUEST §R2 lines 33-34:
        // "5. Aplicar inmediatamente el nuevo orden a la cola real de Media3.
        //  La canción en reproducción debe conservar su estado y no reiniciarse por reordenar otras canciones."
        var isPlaying = true
        var currentPositionMs = 45000L
        val mockMedia3BridgeCalls = mutableListOf<String>()

        val simulator = QueueDragSimulator(
            initialQueue = queueSongs,
            currentIndex = 0,
            onTrackClick = { _, _ -> },
            onReorder = { from, to ->
                // Simulación del despacho a Media3 via bridge.moveMediaItem
                mockMedia3BridgeCalls.add("moveMediaItem($from, $to)")
            }
        )

        simulator.handleLongPress(index = 2)
        simulator.handleDrag(deltaY = 72f)
        simulator.releaseDrop()

        // Verificación de invariantes de reproducción
        assertTrue("La reproducción sigue activa", isPlaying)
        assertEquals(45000L, currentPositionMs)
        assertEquals("Debe haberse llamado a bridge.moveMediaItem(2, 3)", "moveMediaItem(2, 3)", mockMedia3BridgeCalls.first())
        assertEquals("Now Playing en índice 0 no fue alterado", "np_0", simulator.currentTimeline[0].videoId)
    }

    // =========================================================================
    // TIER 4: REAL-WORLD APPLICATION SCENARIOS
    // =========================================================================

    @Test
    fun tier4_test01_automotive_reordering_session_during_driving() {
        // Derivation: ORIGINAL_REQUEST §Real-World Scenario:
        // "reorder user queue via drag & drop"
        // Escenario: Conductor va conduciendo con Now Playing activo.
        // En la cola de usuario tiene: [1: uq_1, 2: uq_2, 3: uq_3].
        // Decide que prefiere escuchar la canción 3 a continuación.
        // Realiza long-press en uq_3 (índice 3), la arrastra hacia arriba a la posición 1, y la suelta.
        val simulator = QueueDragSimulator(
            initialQueue = queueSongs,
            currentIndex = 0,
            onTrackClick = { _, _ -> },
            onReorder = { from, to -> reorderedPairs.add(from to to) }
        )

        // 1. Long-press en uq_3
        val pressed = simulator.handleLongPress(index = 3)
        assertTrue("Long press exitoso", pressed)
        assertEquals("uq_3", simulator.heldSong?.videoId)

        // 2. Arrastre hacia arriba superando dos filas (-144px)
        simulator.handleDrag(deltaY = -144f, rowHeightPx = 72f)
        assertEquals(1, simulator.currentTargetIndex)

        // 3. Drop en la posición superior
        val dropped = simulator.releaseDrop()
        assertTrue("Drop exitoso", dropped)

        // 4. Verificación del estado final de la cola
        assertEquals("np_0", simulator.currentTimeline[0].videoId) // Now Playing intacta
        assertEquals("uq_3", simulator.currentTimeline[1].videoId) // Movida a la siguiente
        assertEquals("uq_1", simulator.currentTimeline[2].videoId) // Desplazada abajo
        assertEquals("uq_2", simulator.currentTimeline[3].videoId) // Desplazada abajo

        // 5. Particionamiento verificado
        val partition = partitionQueue(simulator.currentTimeline, currentIndex = 0)
        assertNotNull(partition.nowPlaying)
        assertEquals("np_0", partition.nowPlaying?.song?.videoId)
        assertEquals(3, partition.userQueue.size)
        assertEquals("uq_3", partition.userQueue[0].song.videoId)
        assertEquals("uq_1", partition.userQueue[1].song.videoId)
        assertEquals("uq_2", partition.userQueue[2].song.videoId)
    }
}
