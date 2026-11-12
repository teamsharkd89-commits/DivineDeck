package com.music.bitchord.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Suite de pruebas unitarias para el motor headless de cola de BitChord (Fase 06.9 - R4):
 * 1. QueueCoordinator: cálculo de índices de inserción findUserQueueInsertionIndex (next vs last).
 * 2. QueueCoordinator: vaciado de cola clearUserQueue sin afectar el contexto actual ni pasado.
 * 3. QueueCoordinator: purga de pistas consumidas consumePlayedUserQueue al entrar en Context.
 * 4. QueueTimeline: orden de jump y planes deterministas computeJumpOrder.
 * 5. QueueShuffle: invariante de preservación de USER_QUEUE fija mientras CONTEXT se mezcla.
 */
class BitChordQueueCoordinatorTest {

    private fun createSong(
        id: String,
        title: String,
        tier: QueueTier = QueueTier.CONTEXT,
        source: String? = "Test Album",
    ): Song = Song(
        videoId = id,
        title = title,
        artist = "Artist $id",
        durationSeconds = 180L,
        thumbnailUrl = "https://example.com/$id.jpg",
        queueTier = tier,
        playbackSource = source,
        playbackSourceType = PlaybackSourceType.BROWSE,
    )

    private fun createFakePlayer(
        currentIndex: Int,
        mediaCount: Int,
        onRemoveMediaItem: (Int) -> Unit = {},
    ): Player {
        val dummyMediaItem = MediaItem.Builder().setMediaId("dummy").build()
        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getCurrentMediaItemIndex" -> currentIndex
                "getMediaItemCount" -> mediaCount
                "getCurrentMediaItem" -> dummyMediaItem
                "removeMediaItem" -> {
                    onRemoveMediaItem(args[0] as Int)
                    null
                }
                "toString" -> "FakePlayer(index=$currentIndex, count=$mediaCount)"
                "hashCode" -> 42
                "equals" -> false
                else -> null
            }
        } as Player
    }

    // =========================================================================
    // 1. Cálculo de Índices de Inserción (findUserQueueInsertionIndex)
    // =========================================================================

    @Test
    fun findUserQueueInsertionIndex_emptyTimeline_returnsZero() {
        val indexNext = QueueCoordinator.findUserQueueInsertionIndex(emptyList(), currentIndex = 0, isNext = true)
        val indexLast = QueueCoordinator.findUserQueueInsertionIndex(emptyList(), currentIndex = 0, isNext = false)
        assertEquals(0, indexNext)
        assertEquals(0, indexLast)
    }

    @Test
    fun findUserQueueInsertionIndex_enqueueNext_insertsImmediatelyAfterCurrent() {
        val timeline = listOf(
            createSong("s0", "Current Song", QueueTier.CONTEXT),
            createSong("u1", "Existing User 1", QueueTier.USER_QUEUE),
            createSong("c2", "Context Song 2", QueueTier.CONTEXT),
        )

        // isNext = true siempre inserta en currentIndex + 1
        val insertionIndex = QueueCoordinator.findUserQueueInsertionIndex(timeline, currentIndex = 0, isNext = true)
        assertEquals(1, insertionIndex)

        // Si currentIndex está en 1
        val insertionAt1 = QueueCoordinator.findUserQueueInsertionIndex(timeline, currentIndex = 1, isNext = true)
        assertEquals(2, insertionAt1)
    }

    @Test
    fun findUserQueueInsertionIndex_enqueueLast_insertsAtEndOfUpcomingUserQueueBlock() {
        val timeline = listOf(
            createSong("s0", "Current Song", QueueTier.CONTEXT),      // index 0
            createSong("u1", "Existing User 1", QueueTier.USER_QUEUE), // index 1
            createSong("u2", "Existing User 2", QueueTier.USER_QUEUE), // index 2
            createSong("c3", "Context Song 3", QueueTier.CONTEXT),    // index 3
            createSong("a4", "Autoplay Song 4", QueueTier.AUTOPLAY),  // index 4
        )

        // Con currentIndex = 0:
        // isNext = false debe buscar el final del bloque de USER_QUEUE (antes de c3 -> index 3)
        val indexLast = QueueCoordinator.findUserQueueInsertionIndex(timeline, currentIndex = 0, isNext = false)
        assertEquals(3, indexLast)

        // Con currentIndex = 1 (reproduciendo u1):
        // upcoming user queue es u2 en index 2 -> el final es index 3
        val indexLastFrom1 = QueueCoordinator.findUserQueueInsertionIndex(timeline, currentIndex = 1, isNext = false)
        assertEquals(3, indexLastFrom1)

        // Si no hay USER_QUEUE posterior (currentIndex = 3, reproduciendo c3):
        // el bloque USER_QUEUE posterior está vacío -> inserta en index 4
        val indexLastFrom3 = QueueCoordinator.findUserQueueInsertionIndex(timeline, currentIndex = 3, isNext = false)
        assertEquals(4, indexLastFrom3)
    }

    // =========================================================================
    // 2. Vaciado de Cola de Usuario (clearUserQueue)
    // =========================================================================

    @Test
    fun clearUserQueue_removesOnlyUpcomingUserQueueItemsInReverseOrder() {
        // Timeline simulado:
        // 0: History User (pasado)
        // 1: Current Context (actual)
        // 2: Upcoming User 1
        // 3: Upcoming User 2
        // 4: Upcoming Context
        // 5: Upcoming Autoplay
        val tiers = listOf(
            QueueTier.USER_QUEUE, // 0 (pasado, no debe removerse)
            QueueTier.CONTEXT,    // 1 (actual)
            QueueTier.USER_QUEUE, // 2 (debe removerse)
            QueueTier.USER_QUEUE, // 3 (debe removerse)
            QueueTier.CONTEXT,    // 4 (debe conservarse)
            QueueTier.AUTOPLAY,   // 5 (debe conservarse)
        )

        val removedIndices = mutableListOf<Int>()
        val fakePlayer = createFakePlayer(
            currentIndex = 1,
            mediaCount = tiers.size,
            onRemoveMediaItem = { removedIndices.add(it) }
        )

        QueueCoordinator.clearUserQueue(fakePlayer, tierAt = { tiers[it] })

        // Deben removerse en orden inverso (3, luego 2) para preservar la validez de índices en ExoPlayer
        assertEquals(listOf(3, 2), removedIndices)
    }

    @Test
    fun clearUserQueue_whenNoUpcomingUserQueue_removesNothing() {
        val tiers = listOf(
            QueueTier.CONTEXT,
            QueueTier.CONTEXT,
            QueueTier.AUTOPLAY,
        )

        val removedIndices = mutableListOf<Int>()
        val fakePlayer = createFakePlayer(
            currentIndex = 0,
            mediaCount = tiers.size,
            onRemoveMediaItem = { removedIndices.add(it) }
        )

        QueueCoordinator.clearUserQueue(fakePlayer, tierAt = { tiers[it] })
        assertTrue("No debe remover nada si no hay canciones añadidas por usuario", removedIndices.isEmpty())
    }

    // =========================================================================
    // 3. Purga de Cola de Usuario Consumida (consumePlayedUserQueue)
    // =========================================================================

    @Test
    fun consumePlayedUserQueue_whenInContext_purgesPrecedingPlayedUserQueue() {
        // Supongamos que el reproductor acaba de transicionar a la pista 3 (CONTEXT).
        // Las pistas 1 y 2 fueron pistas de usuario que ya fueron reproducidas.
        val tiers = listOf(
            QueueTier.CONTEXT,    // 0
            QueueTier.USER_QUEUE, // 1 (reproducida)
            QueueTier.USER_QUEUE, // 2 (reproducida)
            QueueTier.CONTEXT,    // 3 (actual)
            QueueTier.CONTEXT,    // 4 (futura)
        )

        val removedIndices = mutableListOf<Int>()
        val fakePlayer = createFakePlayer(
            currentIndex = 3,
            mediaCount = tiers.size,
            onRemoveMediaItem = { removedIndices.add(it) }
        )

        QueueCoordinator.consumePlayedUserQueue(fakePlayer, tierAt = { tiers[it] })

        // Se deben remover las pistas de usuario pasadas en orden inverso (2, 1)
        assertEquals(listOf(2, 1), removedIndices)
    }

    @Test
    fun consumePlayedUserQueue_whenStillPlayingUserQueue_doesNotPurge() {
        // Si el usuario aún está escuchando una pista encolada (currentIndex en USER_QUEUE)
        val tiers = listOf(
            QueueTier.CONTEXT,    // 0
            QueueTier.USER_QUEUE, // 1 (actual)
            QueueTier.USER_QUEUE, // 2
            QueueTier.CONTEXT,    // 3
        )

        val removedIndices = mutableListOf<Int>()
        val fakePlayer = createFakePlayer(
            currentIndex = 1,
            mediaCount = tiers.size,
            onRemoveMediaItem = { removedIndices.add(it) }
        )

        QueueCoordinator.consumePlayedUserQueue(fakePlayer, tierAt = { tiers[it] })
        assertTrue("No debe purgar mientras el usuario siga reproduciendo canciones de usuario", removedIndices.isEmpty())
    }

    // =========================================================================
    // 4. Construcción de Cola de Contexto y Jump (QueueTimeline)
    // =========================================================================

    @Test
    fun buildContextQueue_preservesUpcomingUserQueue() {
        val currentTimeline = listOf(
            createSong("curr0", "Current Track", QueueTier.CONTEXT),
            createSong("user1", "Pinned User Track 1", QueueTier.USER_QUEUE),
            createSong("user2", "Pinned User Track 2", QueueTier.USER_QUEUE),
        )

        val newAlbum = listOf(
            createSong("alb1", "Album Track 1"),
            createSong("alb2", "Album Track 2"),
            createSong("alb3", "Album Track 3"),
        )

        val source = QueueSource("Dark Side of the Moon", PlaybackSourceType.BROWSE)

        // Se selecciona alb1 (índice 0)
        val result = QueueCoordinator.buildContextQueue(
            currentTimeline = currentTimeline,
            currentIndex = 0,
            newContextSongs = newAlbum,
            selectedIndex = 0,
            contextSource = source,
        )

        // El timeline resultante debe colocar el alb1 seleccionado, luego las pistas de usuario pendientes,
        // y después el resto del álbum:
        // [alb1, user1, user2, alb2, alb3]
        assertEquals(5, result.timeline.size)
        assertEquals(0, result.startIndex)
        assertEquals("alb1", result.timeline[0].videoId)
        assertEquals("user1", result.timeline[1].videoId)
        assertEquals("user2", result.timeline[2].videoId)
        assertEquals("alb2", result.timeline[3].videoId)
        assertEquals("alb3", result.timeline[4].videoId)
    }

    @Test
    fun computeJumpOrder_toPastItem_isPureSeek() {
        val tiers = listOf(QueueTier.CONTEXT, QueueTier.USER_QUEUE, QueueTier.CONTEXT)
        val plan = QueueTimeline.computeJumpOrder(tiers, currentIndex = 2, targetIndex = 0)

        assertNotNull(plan)
        assertTrue("Saltar al pasado es un seek puro sin reordenar", plan!!.isPureSeek)
        assertEquals(0, plan.newTargetIndex)
    }

    @Test
    fun computeJumpOrder_jumpingOverUserQueueToContext_reordersAccurately() {
        val tiers = listOf(
            QueueTier.CONTEXT,    // 0: current
            QueueTier.USER_QUEUE, // 1: upcoming user
            QueueTier.USER_QUEUE, // 2: upcoming user
            QueueTier.CONTEXT,    // 3: target
        )

        val plan = QueueTimeline.computeJumpOrder(tiers, currentIndex = 0, targetIndex = 3)
        assertNotNull(plan)
        assertFalse(plan!!.isPureSeek)

        // Al saltar directamente a la pista de contexto 3, las pistas de usuario 1 y 2
        // deben seguir vivas después de la pista 3
        assertEquals(3, plan.newOrderIndices[1]) // El target queda inmediatamente activo
        assertTrue(plan.newOrderIndices.contains(1))
        assertTrue(plan.newOrderIndices.contains(2))
    }

    // =========================================================================
    // 5. Invariante de Shuffle (QueueShuffle / QueueTimeline)
    // =========================================================================

    @Test
    fun shuffledStartingOrder_pinsUserQueueAtFrontAndShufflesContextOnly() {
        val songs = listOf(
            createSong("start0", "Starting Song", QueueTier.CONTEXT),
            createSong("u1", "User Track 1", QueueTier.USER_QUEUE),
            createSong("u2", "User Track 2", QueueTier.USER_QUEUE),
            createSong("c3", "Context 3", QueueTier.CONTEXT),
            createSong("c4", "Context 4", QueueTier.CONTEXT),
            createSong("c5", "Context 5", QueueTier.CONTEXT),
            createSong("c6", "Context 6", QueueTier.CONTEXT),
        )

        val shuffled = QueueShuffle.startingOrder(songs, startIndex = 0)

        assertEquals("La primera pista siempre es la elegida", "start0", shuffled[0].videoId)
        assertEquals("Las canciones de usuario deben permanecer fijas al frente", "u1", shuffled[1].videoId)
        assertEquals("Las canciones de usuario deben permanecer fijas al frente", "u2", shuffled[2].videoId)

        // El bloque restante (índices 3..6) contiene exactamente las canciones de contexto
        val contextIds = shuffled.drop(3).map { it.videoId }.toSet()
        assertEquals(setOf("c3", "c4", "c5", "c6"), contextIds)
    }
}
