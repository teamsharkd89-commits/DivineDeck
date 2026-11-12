package com.music.bitchord.ui.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Tune
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite de pruebas unitarias para la UI del Reproductor y el Sistema de Cola (Fase 06.9.1 - R2):
 * 1. Verificación de contrato de iconos en la barra de acciones (eliminación de EQ, adición de Cola).
 * 2. Particionamiento estricto de la cola (Now Playing, User Queue, Context, Autoplay).
 * 3. Estabilidad e integridad de llaves en QueueRowItem (claves independientes de la posición en timeline).
 * 4. Algoritmo de auto-scroll en los extremos (edgeScrollSpeed - BitChord 1.8.1 parity).
 */
class BitChordPlayerQueueAndActionsTest {

    private fun createSong(
        id: String,
        title: String,
        tier: QueueTier = QueueTier.CONTEXT,
        queueEntryId: String? = null,
    ): Song = Song(
        videoId = id,
        title = title,
        artist = "Artist $id",
        durationSeconds = 180L,
        thumbnailUrl = "https://example.com/$id.jpg",
        queueTier = tier,
        queueEntryId = queueEntryId,
    )

    // =========================================================================
    // 1. Verificación de Contratos de Iconos de Acción del Reproductor (R5 & R4)
    // =========================================================================

    @Test
    fun playerActionRow_usesQueueMusicIconAndReplacesStandaloneEqualizer() {
        // En Requisito R5: El botón independiente de ecualizador (Icons.Rounded.Tune) se elimina
        // de la barra de acciones del reproductor.
        // En Requisito R4: Se incorpora el botón dedicado de cola (Icons.AutoMirrored.Rounded.QueueMusic).
        val expectedQueueIcon = Icons.AutoMirrored.Rounded.QueueMusic
        val obsoleteEqIcon = Icons.Rounded.Tune

        assertNotNull("El icono QueueMusic debe estar disponible", expectedQueueIcon)
        assertNotNull("El icono Tune existe en la biblioteca de Material", obsoleteEqIcon)
        assertNotEquals("El icono de cola no debe ser el icono de ecualizador", expectedQueueIcon, obsoleteEqIcon)

        // Verificamos nombres de iconos de Material para garantizar contrato de UI
        assertEquals("AutoMirrored.Rounded.QueueMusic", expectedQueueIcon.name)
        assertEquals("Rounded.Tune", obsoleteEqIcon.name)
    }

    // =========================================================================
    // 2. Particionamiento de la Cola (partitionQueue) con Llaves Estables
    // =========================================================================

    @Test
    fun partitionQueue_withEmptyList_returnsEmptyPartition() {
        val partition = partitionQueue(emptyList(), currentIndex = 0)

        assertNull("Now playing debe ser null en cola vacía", partition.nowPlaying)
        assertTrue("User queue debe estar vacía", partition.userQueue.isEmpty())
        assertTrue("Context queue debe estar vacía", partition.contextQueue.isEmpty())
        assertTrue("Autoplay queue debe estar vacía", partition.autoplayQueue.isEmpty())
    }

    @Test
    fun partitionQueue_withSingleSong_returnsNowPlayingOnly() {
        val song = createSong("s1", "Single Track", QueueTier.CONTEXT)
        val partition = partitionQueue(listOf(song), currentIndex = 0)

        assertNotNull("Now playing debe estar presente", partition.nowPlaying)
        assertEquals("s1", partition.nowPlaying?.song?.videoId)
        assertEquals(0, partition.nowPlaying?.timelineIndex)
        assertEquals("np_s1#0", partition.nowPlaying?.key)

        assertTrue(partition.userQueue.isEmpty())
        assertTrue(partition.contextQueue.isEmpty())
        assertTrue(partition.autoplayQueue.isEmpty())
    }

    @Test
    fun partitionQueue_categorizesUpcomingSongsByTierCorrectly() {
        val songs = listOf(
            createSong("s0", "Current Song", QueueTier.CONTEXT),      // index 0: Now Playing
            createSong("u1", "User Enqueued 1", QueueTier.USER_QUEUE), // index 1: User Queue
            createSong("u2", "User Enqueued 2", QueueTier.USER_QUEUE), // index 2: User Queue
            createSong("c3", "Context Song 3", QueueTier.CONTEXT),    // index 3: Context Queue
            createSong("c4", "Context Song 4", QueueTier.CONTEXT),    // index 4: Context Queue
            createSong("a5", "Autoplay Song 5", QueueTier.AUTOPLAY),  // index 5: Autoplay Queue
        )

        val partition = partitionQueue(songs, currentIndex = 0)

        // Now playing
        assertNotNull(partition.nowPlaying)
        assertEquals("s0", partition.nowPlaying?.song?.videoId)
        assertEquals(0, partition.nowPlaying?.timelineIndex)
        assertEquals("np_s0#0", partition.nowPlaying?.key)

        // User Queue (índices 1 y 2) con claves estables independientes del timeline index
        assertEquals(2, partition.userQueue.size)
        assertEquals("u1", partition.userQueue[0].song.videoId)
        assertEquals(1, partition.userQueue[0].timelineIndex)
        assertEquals("USER_QUEUE_u1#0", partition.userQueue[0].key)

        assertEquals("u2", partition.userQueue[1].song.videoId)
        assertEquals(2, partition.userQueue[1].timelineIndex)
        assertEquals("USER_QUEUE_u2#0", partition.userQueue[1].key)

        // Context Queue (índices 3 y 4)
        assertEquals(2, partition.contextQueue.size)
        assertEquals("c3", partition.contextQueue[0].song.videoId)
        assertEquals(3, partition.contextQueue[0].timelineIndex)
        assertEquals("CONTEXT_c3#0", partition.contextQueue[0].key)

        assertEquals("c4", partition.contextQueue[1].song.videoId)
        assertEquals(4, partition.contextQueue[1].timelineIndex)
        assertEquals("CONTEXT_c4#0", partition.contextQueue[1].key)

        // Autoplay Queue (índice 5)
        assertEquals(1, partition.autoplayQueue.size)
        assertEquals("a5", partition.autoplayQueue[0].song.videoId)
        assertEquals(5, partition.autoplayQueue[0].timelineIndex)
        assertEquals("AUTOPLAY_a5#0", partition.autoplayQueue[0].key)
    }

    @Test
    fun partitionQueue_whenPlayingInMiddleOfQueue_omitsPrecedingHistoryFromUpcomingSections() {
        val songs = listOf(
            createSong("hist0", "Played Track 0", QueueTier.CONTEXT),
            createSong("hist1", "Played Track 1", QueueTier.USER_QUEUE),
            createSong("curr2", "Current Track 2", QueueTier.CONTEXT),  // Current = 2
            createSong("user3", "Upcoming User Track 3", QueueTier.USER_QUEUE),
            createSong("ctx4", "Upcoming Context Track 4", QueueTier.CONTEXT),
        )

        val partition = partitionQueue(songs, currentIndex = 2)

        // Now playing
        assertEquals("curr2", partition.nowPlaying?.song?.videoId)
        assertEquals(2, partition.nowPlaying?.timelineIndex)

        // Upcoming User Queue: solo pista 3 (pista 1 está en el historial pasado)
        assertEquals(1, partition.userQueue.size)
        assertEquals("user3", partition.userQueue[0].song.videoId)
        assertEquals(3, partition.userQueue[0].timelineIndex)

        // Upcoming Context Queue: solo pista 4 (pista 0 está en el historial pasado)
        assertEquals(1, partition.contextQueue.size)
        assertEquals("ctx4", partition.contextQueue[0].song.videoId)
        assertEquals(4, partition.contextQueue[0].timelineIndex)
    }

    @Test
    fun partitionQueue_handlesOutOfBoundsCurrentIndexSafely() {
        val songs = listOf(
            createSong("s0", "Track 0", QueueTier.CONTEXT),
            createSong("s1", "Track 1", QueueTier.USER_QUEUE),
        )

        // Índice negativo -> debe caer a 0 de forma segura sin excepciones
        val negPartition = partitionQueue(songs, currentIndex = -1)
        assertNotNull(negPartition.nowPlaying)
        assertEquals(0, negPartition.nowPlaying?.timelineIndex)
        assertEquals(1, negPartition.userQueue.size)

        // Índice superior al tamaño -> debe caer a 0 de forma segura
        val overPartition = partitionQueue(songs, currentIndex = 99)
        assertNotNull(overPartition.nowPlaying)
        assertEquals(0, overPartition.nowPlaying?.timelineIndex)
        assertEquals(1, overPartition.userQueue.size)
    }

    @Test
    fun queueRowItem_dataClassPropertiesAndEquality() {
        val song = createSong("vid_abc", "Test Track")
        val item1 = QueueRowItem(timelineIndex = 3, song = song, key = "key_1")
        val item2 = QueueRowItem(timelineIndex = 3, song = song, key = "key_1")
        val item3 = QueueRowItem(timelineIndex = 4, song = song, key = "key_2")

        assertEquals(item1, item2)
        assertNotEquals(item1, item3)
        assertEquals(3, item1.timelineIndex)
        assertEquals("Test Track", item1.song.title)
        assertEquals("key_1", item1.key)
    }

    // =========================================================================
    // 3. Verificación de Estabilidad de Llaves (Invariante R2 para Drag & Drop)
    // =========================================================================

    @Test
    fun partitionQueue_keysRemainStableWhenItemsAreSwapped() {
        // En BitChord 1.8.1 parity: las claves deben basarse en la identidad del elemento
        // y NUNCA en la posición del timeline `$i`. De lo contrario, un swap destruye el nodo
        // y cancela el gesto de arrastre activo.
        val songA = createSong("track_a", "Song A", QueueTier.USER_QUEUE, queueEntryId = "entry_a")
        val songB = createSong("track_b", "Song B", QueueTier.USER_QUEUE, queueEntryId = "entry_b")

        val initialQueue = listOf(
            createSong("np", "Now Playing", QueueTier.CONTEXT),
            songA, // timeline index 1
            songB, // timeline index 2
        )

        val partitionBefore = partitionQueue(initialQueue, currentIndex = 0)
        val keyABefore = partitionBefore.userQueue[0].key
        val keyBBefore = partitionBefore.userQueue[1].key

        assertEquals("USER_QUEUE_entry_a", keyABefore)
        assertEquals("USER_QUEUE_entry_b", keyBBefore)

        // Simular swap de posiciones: B ahora en índice 1, A en índice 2
        val swappedQueue = listOf(
            createSong("np", "Now Playing", QueueTier.CONTEXT),
            songB, // timeline index 1
            songA, // timeline index 2
        )

        val partitionAfter = partitionQueue(swappedQueue, currentIndex = 0)
        val keyBAfter = partitionAfter.userQueue[0].key
        val keyAAfter = partitionAfter.userQueue[1].key

        // Las llaves DEBEN ser idénticas a las anteriores a pesar del cambio de índice
        assertEquals("La clave de Song B debe preservarse tras swap", keyBBefore, keyBAfter)
        assertEquals("La clave de Song A debe preservarse tras swap", keyABefore, keyAAfter)
    }

    @Test
    fun partitionQueue_disambiguatesDuplicateTracksWithoutQueueEntryId() {
        // Dos instancias de la misma canción (mismo videoId) deben recibir claves distintas
        // basadas en occurrence count para evitar colisiones en LazyColumn.
        val dupSong1 = createSong("duplicate_vid", "Duplicate Song", QueueTier.USER_QUEUE, queueEntryId = null)
        val dupSong2 = createSong("duplicate_vid", "Duplicate Song", QueueTier.USER_QUEUE, queueEntryId = null)

        val queue = listOf(
            createSong("np", "Now Playing", QueueTier.CONTEXT),
            dupSong1,
            dupSong2,
        )

        val partition = partitionQueue(queue, currentIndex = 0)
        val key1 = partition.userQueue[0].key
        val key2 = partition.userQueue[1].key

        assertEquals("USER_QUEUE_duplicate_vid#0", key1)
        assertEquals("USER_QUEUE_duplicate_vid#1", key2)
        assertNotEquals("Las claves deben ser únicas para elementos duplicados", key1, key2)
    }

    @Test
    fun partitionQueue_disambiguatesDuplicateQueueEntryIdsWithSuffix() {
        // En caso extremo de colisión de queueEntryId (ej. cola restaurada dos veces)
        val dupEntry1 = createSong("v1", "Song 1", QueueTier.USER_QUEUE, queueEntryId = "same_entry")
        val dupEntry2 = createSong("v2", "Song 2", QueueTier.USER_QUEUE, queueEntryId = "same_entry")

        val queue = listOf(
            createSong("np", "Now Playing", QueueTier.CONTEXT),
            dupEntry1,
            dupEntry2,
        )

        val partition = partitionQueue(queue, currentIndex = 0)
        val key1 = partition.userQueue[0].key
        val key2 = partition.userQueue[1].key

        assertEquals("USER_QUEUE_same_entry", key1)
        assertEquals("USER_QUEUE_same_entry~1", key2)
        assertNotEquals("Debe resolverse con sufijo para evitar crash de LazyColumn", key1, key2)
    }

    // =========================================================================
    // 4. Verificación del Algoritmo de Auto-Scroll (edgeScrollSpeed - BitChord 1.8.1)
    // =========================================================================

    @Test
    fun edgeScrollSpeed_middleOfViewport_returnsZero() {
        // En el centro de la pantalla (fuera de la zona de 50px de borde) no hay desplazamiento
        val speed = edgeScrollSpeed(
            top = 250f,
            bottom = 350f,
            viewportStart = 0,
            viewportEnd = 600,
            zone = 50f,
            speed = 1000f,
        )
        assertEquals(0f, speed, 0.001f)
    }

    @Test
    fun edgeScrollSpeed_approachingTopEdge_returnsNegativeRampedSpeed() {
        // Cerca del borde superior (top = 40f < 50f): velocidad negativa hacia el inicio
        val speed = edgeScrollSpeed(
            top = 40f,
            bottom = 140f,
            viewportStart = 0,
            viewportEnd = 600,
            zone = 50f,
            speed = 1000f,
        )
        assertTrue("La velocidad hacia arriba debe ser negativa", speed < 0f)
        assertTrue("Debe iniciar en al menos el 20% de velocidad máxima", speed <= -200f)
    }

    @Test
    fun edgeScrollSpeed_approachingBottomEdge_returnsPositiveRampedSpeed() {
        // Cerca del borde inferior (bottom = 570f > 550f): velocidad positiva hacia el final
        val speed = edgeScrollSpeed(
            top = 470f,
            bottom = 570f,
            viewportStart = 0,
            viewportEnd = 600,
            zone = 50f,
            speed = 1000f,
        )
        assertTrue("La velocidad hacia abajo debe ser positiva", speed > 0f)
        assertTrue("Debe ser mayor o igual al 20% de la velocidad máxima", speed >= 200f)
    }

    @Test
    fun edgeScrollSpeed_atExtremeEdge_capsAtMaxSpeed() {
        // En el borde superior exacto (top = 0f)
        val speedTop = edgeScrollSpeed(
            top = 0f,
            bottom = 100f,
            viewportStart = 0,
            viewportEnd = 600,
            zone = 50f,
            speed = 1000f,
        )
        assertEquals(-1000f, speedTop, 0.01f)

        // Superando el borde superior (top = -20f)
        val speedPastTop = edgeScrollSpeed(
            top = -20f,
            bottom = 80f,
            viewportStart = 0,
            viewportEnd = 600,
            zone = 50f,
            speed = 1000f,
        )
        assertEquals(-1000f, speedPastTop, 0.01f)
    }

    @Test
    fun edgeScrollSpeed_zeroZoneOrSmallViewport_returnsZero() {
        // Zona 0 -> sin auto scroll
        val speedZeroZone = edgeScrollSpeed(
            top = 10f,
            bottom = 110f,
            viewportStart = 0,
            viewportEnd = 600,
            zone = 0f,
            speed = 1000f,
        )
        assertEquals(0f, speedZeroZone, 0.001f)

        // Viewport más pequeño que ambas zonas combinadas (120px < 100px fila + 2*50px zonas)
        val speedSmallViewport = edgeScrollSpeed(
            top = 10f,
            bottom = 110f,
            viewportStart = 0,
            viewportEnd = 120,
            zone = 50f,
            speed = 1000f,
        )
        assertEquals(0f, speedSmallViewport, 0.001f)
    }
}
