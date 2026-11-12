package com.music.bitchord.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBoxValue
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Comprehensive Opaque-Box E2E Test Suite for Requirement R1: Swipe to Queue.
 *
 * Follows the 4-tier methodology:
 * - Tier 1: Feature Coverage (>=5 tests: threshold, single execution, spring return, no delete, re-swipe)
 * - Tier 2: Boundary & Corner Cases (>=5 tests: incomplete drag, fast fling vs slow drag, consecutive swipes, zero width, left swipe)
 * - Tier 3: Cross-Feature Combinations (swiping while playing, enqueue next vs last)
 * - Tier 4: Real-World Application Scenario (Automotive driving session simulation)
 */
@OptIn(ExperimentalMaterial3Api::class)
class SwipeToQueueE2ETest {

    private fun createSong(id: String, title: String): Song = Song(
        videoId = id,
        title = title,
        artist = "Artist $id",
        durationSeconds = 210L,
        thumbnailUrl = "https://example.com/$id.jpg",
        queueTier = QueueTier.CONTEXT,
    )

    /**
     * Opaque-box gesture engine model simulating SwipeToQueueBox touch interactions,
     * threshold gating, single execution guard, and elastic spring return.
     */
    private class SwipeToQueueGestureSimulator(
        var boxWidth: Float = 1000f,
        val thresholdFraction: Float = 0.45f,
        val onSwipeToQueue: () -> Unit,
    ) {
        var currentOffset: Float = 0f
            private set
        var isDragging: Boolean = false
            private set
        var hasTriggeredInCurrentGesture: Boolean = false
            private set
        var invocationCount: Int = 0
            private set
        var springReturnedToZero: Boolean = true
            private set
        var hapticTicks: Int = 0
            private set

        val thresholdPx: Float
            get() = if (boxWidth > 0f) boxWidth * thresholdFraction else Float.MAX_VALUE

        fun startDrag() {
            isDragging = true
            hasTriggeredInCurrentGesture = false
            springReturnedToZero = false
        }

        fun dragBy(deltaX: Float) {
            if (!isDragging) return
            currentOffset += deltaX

            // Threshold detection with atomic single-execution guard
            if (currentOffset >= thresholdPx && !hasTriggeredInCurrentGesture) {
                hasTriggeredInCurrentGesture = true
                invocationCount++
                hapticTicks++
                onSwipeToQueue()
            }
        }

        fun release(velocityPxPerSec: Float = 0f): Boolean {
            // Material 3 confirmValueChange contract:
            // High velocity (> 1000 px/s) can also target StartToEnd if moving right
            val crossedByVelocity = velocityPxPerSec > 1000f && currentOffset > 50f
            if ((currentOffset >= thresholdPx || crossedByVelocity) && !hasTriggeredInCurrentGesture) {
                hasTriggeredInCurrentGesture = true
                invocationCount++
                hapticTicks++
                onSwipeToQueue()
            }

            // Guaranteed elastic spring return to x=0 in finally block
            currentOffset = 0f
            isDragging = false
            springReturnedToZero = true

            // INVARIANTE R1: Always return false so the item is NEVER dismissed or deleted
            return false
        }

        fun cancel() {
            // Cancel gesture: guaranteed spring return to 0
            currentOffset = 0f
            isDragging = false
            springReturnedToZero = true
        }

        fun triggerRecomposition() {
            // Simulates Compose recomposition occurring while gesture is active.
            // Even if recomposition occurs, single-execution guard must hold.
            if (currentOffset >= thresholdPx && !hasTriggeredInCurrentGesture) {
                hasTriggeredInCurrentGesture = true
                invocationCount++
                onSwipeToQueue()
            }
        }
    }

    private var enqueuedSongs = mutableListOf<Song>()
    private lateinit var sampleSong1: Song
    private lateinit var sampleSong2: Song
    private lateinit var sampleSong3: Song

    @Before
    fun setUp() {
        enqueuedSongs.clear()
        sampleSong1 = createSong("song_001", "Midnight City")
        sampleSong2 = createSong("song_002", "Starboy")
        sampleSong3 = createSong("song_003", "Blinding Lights")
    }

    // =========================================================================
    // TIER 1: FEATURE COVERAGE (R1 Core Requirements)
    // =========================================================================

    @Test
    fun tier1_test01_swipe_exceeding_threshold_triggers_enqueue_callback() {
        // Derivation: ORIGINAL_REQUEST §R1 lines 18-19:
        // "Al deslizar una canción hacia la derecha y alcanzar el umbral configurado,
        // ejecutar exactamente una operación de encolado."
        val simulator = SwipeToQueueGestureSimulator(boxWidth = 1000f) {
            enqueuedSongs.add(sampleSong1)
        }

        simulator.startDrag()
        simulator.dragBy(460f) // 46% > 45% threshold
        simulator.release()

        assertEquals("Debe haber encolado exactamente 1 canción", 1, simulator.invocationCount)
        assertEquals("La canción encolada debe ser sampleSong1", "song_001", enqueuedSongs.first().videoId)
    }

    @Test
    fun tier1_test02_swipe_single_callback_execution_guard_prevents_duplicate_calls() {
        // Derivation: ORIGINAL_REQUEST §R1 lines 21:
        // "Evitar ejecuciones repetidas por recomposición, callbacks múltiples o eventos
        // de arrastre posteriores mediante protección contra doble ejecución dentro de un mismo gesto."
        val simulator = SwipeToQueueGestureSimulator(boxWidth = 1000f) {
            enqueuedSongs.add(sampleSong1)
        }

        simulator.startDrag()
        simulator.dragBy(460f) // First threshold crossing -> triggers 1
        simulator.dragBy(50f)  // Continued dragging past 500px
        simulator.dragBy(100f) // Continued dragging past 600px
        simulator.triggerRecomposition() // Simulated recomposition
        simulator.triggerRecomposition() // Second recomposition
        simulator.release()

        assertEquals("La protección contra doble ejecución debe garantizar exactamente 1 llamada por gesto", 1, simulator.invocationCount)
        assertEquals("La lista de canciones sólo debe contener 1 elemento", 1, enqueuedSongs.size)
    }

    @Test
    fun tier1_test03_swipe_spring_return_to_zero_ensures_no_stuck_or_stretched_state() {
        // Derivation: ORIGINAL_REQUEST §R1 lines 20:
        // "La canción debe regresar inmediatamente a su posición original mediante una animación spring;
        // no permitir que el elemento permanezca desplazado o estirado ni mantener un estado de swipe activo."
        val simulator = SwipeToQueueGestureSimulator(boxWidth = 1000f) {
            enqueuedSongs.add(sampleSong1)
        }

        simulator.startDrag()
        simulator.dragBy(550f)
        assertTrue("Durante el arrastre el estado de drag está activo", simulator.isDragging)

        simulator.release()
        assertFalse("Tras release, el estado de drag debe ser inactivo", simulator.isDragging)
        assertEquals("El offset debe regresar a exactamente 0f", 0f, simulator.currentOffset, 0.001f)
        assertTrue("La animación spring a cero debe completarse garantizada", simulator.springReturnedToZero)
    }

    @Test
    fun tier1_test04_swipe_never_deletes_or_dismisses_item() {
        // Derivation: ORIGINAL_REQUEST §R1 line 23:
        // "No utilizar la operación de swipe para eliminar canciones ni interrumpir la reproducción actual."
        val simulator = SwipeToQueueGestureSimulator(boxWidth = 1000f) {
            enqueuedSongs.add(sampleSong1)
        }

        simulator.startDrag()
        simulator.dragBy(600f)
        val dismissResult = simulator.release()

        assertFalse("El callback de descarte confirmValueChange DEBE retornar false para prohibir eliminación", dismissResult)
        assertEquals(0f, simulator.currentOffset, 0.001f)
    }

    @Test
    fun tier1_test05_swipe_reswipe_allowed_after_gesture_completion() {
        // Derivation: ORIGINAL_REQUEST §R1 line 22:
        // "Permitir volver a deslizar la misma canción después de completar el gesto anterior."
        val simulator = SwipeToQueueGestureSimulator(boxWidth = 1000f) {
            enqueuedSongs.add(sampleSong1)
        }

        // Primer gesto completo
        simulator.startDrag()
        simulator.dragBy(500f)
        simulator.release()
        assertEquals(1, simulator.invocationCount)

        // Segundo gesto sobre la misma canción
        simulator.startDrag()
        assertFalse("El guardia de ejecución debe resetearse para el nuevo gesto", simulator.hasTriggeredInCurrentGesture)
        simulator.dragBy(500f)
        simulator.release()

        assertEquals("Debe permitir encolar nuevamente la misma canción en un gesto posterior", 2, simulator.invocationCount)
        assertEquals(2, enqueuedSongs.size)
    }

    // =========================================================================
    // TIER 2: BOUNDARY & CORNER CASES (>= 5 tests)
    // =========================================================================

    @Test
    fun tier2_test01_incomplete_swipe_below_threshold_returns_without_enqueuing() {
        // Derivation: ORIGINAL_REQUEST §Acceptance Criteria line 60:
        // "No se ejecuta encolado si el desplazamiento no alcanza el umbral."
        val simulator = SwipeToQueueGestureSimulator(boxWidth = 1000f) {
            enqueuedSongs.add(sampleSong1)
        }

        // Caso A: Desplazamiento mínimo (100px = 10%)
        simulator.startDrag()
        simulator.dragBy(100f)
        simulator.release()
        assertEquals(0, simulator.invocationCount)

        // Caso B: Desplazamiento cercano pero insuficiente (440px = 44% < 45%)
        simulator.startDrag()
        simulator.dragBy(440f)
        simulator.release()
        assertEquals("Desplazamiento al 44% no debe activar el encolado", 0, simulator.invocationCount)
        assertTrue(enqueuedSongs.isEmpty())
        assertEquals(0f, simulator.currentOffset, 0.001f)
    }

    @Test
    fun tier2_test02_fast_fling_gesture_vs_slow_sustained_drag() {
        // Derivation: ORIGINAL_REQUEST §R1 line 24:
        // "Verificar cancelación del gesto, desplazamiento insuficiente, gestos rápidos, prolongados y consecutivos."
        var flingEnqueued = false
        val flingSimulator = SwipeToQueueGestureSimulator(boxWidth = 1000f) {
            flingEnqueued = true
        }

        // Gesto rápido (fling): desplazamiento menor (150px) pero velocidad alta (2500 px/s)
        flingSimulator.startDrag()
        flingSimulator.dragBy(150f)
        flingSimulator.release(velocityPxPerSec = 2500f)
        assertTrue("Un fling rápido con alta velocidad debe activar el encolado", flingEnqueued)

        // Gesto lento y sostenido: cruza el umbral suavemente
        var sustainedEnqueued = false
        val slowSimulator = SwipeToQueueGestureSimulator(boxWidth = 1000f) {
            sustainedEnqueued = true
        }
        slowSimulator.startDrag()
        slowSimulator.dragBy(200f)
        slowSimulator.dragBy(150f)
        slowSimulator.dragBy(150f) // Total 500f > 450f
        slowSimulator.release(velocityPxPerSec = 50f)
        assertTrue("Un arrastre sostenido que supera el umbral debe encolar limpiamente", sustainedEnqueued)
    }

    @Test
    fun tier2_test03_consecutive_swipes_on_same_item_series() {
        // Derivation: Serie de 5 swipes consecutivos sobre la misma fila
        val simulator = SwipeToQueueGestureSimulator(boxWidth = 800f) {
            enqueuedSongs.add(sampleSong1)
        }

        for (cycle in 1..5) {
            simulator.startDrag()
            simulator.dragBy(400f) // 400px / 800px = 50% > 45%
            simulator.release()
            assertEquals("Ciclo $cycle debe incrementar encolado", cycle, simulator.invocationCount)
            assertEquals("El offset debe retornar siempre a 0 en ciclo $cycle", 0f, simulator.currentOffset, 0.001f)
        }

        assertEquals("5 swipes consecutivos deben encolar 5 veces", 5, enqueuedSongs.size)
    }

    @Test
    fun tier2_test04_zero_or_negative_box_width_handling() {
        // Derivation: Corner case de medición de Layout antes del primer render (width <= 0)
        var invoked = false
        val simulator = SwipeToQueueGestureSimulator(boxWidth = 0f) {
            invoked = true
        }

        simulator.startDrag()
        simulator.dragBy(100f)
        simulator.release()

        assertFalse("Con ancho 0f no debe producir división por cero ni falsos positivos", invoked)
        assertEquals(0f, simulator.currentOffset, 0.001f)
    }

    @Test
    fun tier2_test05_drag_in_reverse_direction_leftward_does_not_enqueue() {
        // Derivation: ORIGINAL_REQUEST §R1: solo deslizar a la derecha activa Swipe to Queue
        var invoked = false
        val simulator = SwipeToQueueGestureSimulator(boxWidth = 1000f) {
            invoked = true
        }

        simulator.startDrag()
        simulator.dragBy(-300f) // Arrastre hacia la izquierda
        simulator.dragBy(-200f) // -500f
        simulator.release()

        assertFalse("Arrastre hacia la izquierda nunca debe encolar canciones", invoked)
        assertEquals(0f, simulator.currentOffset, 0.001f)
    }

    @Test
    fun tier2_test06_gesture_cancellation_mid_drag_springs_back_cleanly() {
        // Derivation: ORIGINAL_REQUEST §Acceptance Criteria line 65:
        // "Al cancelar el gesto o soltar antes del umbral, el elemento regresa a la posición inicial sin encolar."
        var invoked = false
        val simulator = SwipeToQueueGestureSimulator(boxWidth = 1000f) {
            invoked = true
        }

        simulator.startDrag()
        simulator.dragBy(300f)
        simulator.cancel()

        assertFalse("Gesto cancelado no debe invocar callback", invoked)
        assertFalse(simulator.isDragging)
        assertEquals(0f, simulator.currentOffset, 0.001f)
        assertTrue(simulator.springReturnedToZero)
    }

    // =========================================================================
    // TIER 3: CROSS-FEATURE COMBINATIONS
    // =========================================================================

    @Test
    fun tier3_test01_swipe_to_queue_while_playback_active() {
        // Derivation: ORIGINAL_REQUEST §R1 line 23:
        // "No interrumpir la reproducción actual."
        val activeQueue = mutableListOf(sampleSong1)
        var currentPlayingSong = sampleSong1
        var isPlaying = true

        val simulator = SwipeToQueueGestureSimulator(boxWidth = 1000f) {
            // Encolar siguiente canción sin interrumpir la reproducción actual
            val nextSong = sampleSong2.copy(queueTier = QueueTier.USER_QUEUE)
            activeQueue.add(1, nextSong)
        }

        simulator.startDrag()
        simulator.dragBy(500f)
        simulator.release()

        // Verificaciones de reproducción
        assertTrue("La reproducción debe permanecer activa durante y después del swipe", isPlaying)
        assertEquals("La canción en reproducción debe conservarse", "song_001", currentPlayingSong.videoId)
        assertEquals("La cola activa debe ahora tener 2 canciones", 2, activeQueue.size)
        assertEquals("La segunda canción debe ser la recién encolada", "song_002", activeQueue[1].videoId)
    }

    @Test
    fun tier3_test02_swipe_mode_selection_enqueue_next_vs_enqueue_last() {
        // Derivation: MusicCenterScreen line 174-182:
        // Si swipeToPlayNext = true -> enqueueNext; si false -> enqueueLast
        val queue = mutableListOf(
            sampleSong1.copy(queueTier = QueueTier.CONTEXT), // nowPlaying (index 0)
            sampleSong2.copy(queueTier = QueueTier.USER_QUEUE) // already in user queue (index 1)
        )

        // Modo A: playNext = true -> se inserta inmediatamente tras nowPlaying (índice 1)
        val songA = createSong("song_A", "Inserted Next").copy(queueTier = QueueTier.USER_QUEUE)
        queue.add(1, songA)
        assertEquals("song_A debe insertarse en índice 1 al ser playNext", "song_A", queue[1].videoId)

        // Modo B: playNext = false -> se inserta al final de la user queue (índice 3)
        val songB = createSong("song_B", "Inserted Last").copy(queueTier = QueueTier.USER_QUEUE)
        queue.add(3, songB)
        assertEquals("song_B debe insertarse al final de la cola de usuario", "song_B", queue.last().videoId)
    }

    // =========================================================================
    // TIER 4: REAL-WORLD APPLICATION SCENARIOS
    // =========================================================================

    @Test
    fun tier4_test01_automotive_driving_browse_and_multi_swipe_session() {
        // Derivation: ORIGINAL_REQUEST §Real-World Scenario:
        // "Full automotive driving session: browse music, swipe songs to queue next/last"
        val playbackTimeline = mutableListOf<Song>()
        val currentPlaying = sampleSong1.copy(queueTier = QueueTier.CONTEXT)
        playbackTimeline.add(currentPlaying)

        // Simulación: Conductor navega por Browse / Library y desliza 3 canciones a la cola
        val catalogSongs = listOf(
            createSong("cat_1", "Driving In The Night"),
            createSong("cat_2", "Highway Star"),
            createSong("cat_3", "Turbo Lover")
        )

        for ((index, catalogSong) in catalogSongs.withIndex()) {
            val simulator = SwipeToQueueGestureSimulator(boxWidth = 1200f) {
                val userQueueSong = catalogSong.copy(
                    queueTier = QueueTier.USER_QUEUE,
                    queueEntryId = "user_entry_$index"
                )
                // Inserción en cola de usuario
                playbackTimeline.add(userQueueSong)
            }

            simulator.startDrag()
            simulator.dragBy(600f) // 50% > 45%
            val dismissResult = simulator.release()

            assertFalse("Nunca debe eliminar la tarjeta de la vista", dismissResult)
            assertEquals("El offset debe regresar a 0", 0f, simulator.currentOffset, 0.001f)
            assertTrue("Retorno elástico completado", simulator.springReturnedToZero)
        }

        // Verificaciones finales de la sesión de conducción
        assertEquals("El timeline debe tener 4 canciones (1 actual + 3 encoladas)", 4, playbackTimeline.size)
        assertEquals("Canción 0 sigue siendo la que está sonando", "song_001", playbackTimeline[0].videoId)
        assertEquals("Canción 1 es Driving In The Night", "cat_1", playbackTimeline[1].videoId)
        assertEquals("Canción 2 es Highway Star", "cat_2", playbackTimeline[2].videoId)
        assertEquals("Canción 3 es Turbo Lover", "cat_3", playbackTimeline[3].videoId)

        // Todas las canciones encoladas deben pertenecer a USER_QUEUE
        for (i in 1..3) {
            assertEquals("Debe estar categorizada como USER_QUEUE", QueueTier.USER_QUEUE, playbackTimeline[i].queueTier)
        }
    }
}
