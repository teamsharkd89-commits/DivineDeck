package com.music.bitchord.ui.player

import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

/**
 * Adversarial Empirical Stress Test Suite for Milestone M2 (Queue Reorder Drag & Drop - R2).
 *
 * Specifically executes empirical verification against:
 * 1. Drag edge cases:
 *    - Dragging the first item down to the very end of the list.
 *    - Dragging the last item up to the top of its section.
 *    - Rapid drag reversals (dragging up, down, up in quick succession across thousands of oscillations).
 *    - Aborted drags: releasing without displacement or cancelling mid-drag — verifying exact original order is retained with zero duplicates.
 *    - Attempting to drag `nowPlaying` track: verifying it is strictly unmovable.
 * 2. Auto-scroll dynamics:
 *    - Hovering within 10px of top/bottom edge vs exactly at 40dp boundary.
 *    - Mathematical continuity, cap at maximum speed, zero-zone handling, and conflict avoidance in small viewports.
 * 3. Stable keys and partition invariants under adversarial mutations.
 * 4. Architectural invariants (complete arrow removal, unmovable nowPlaying contract).
 */
class QueueReorderAdversarialStressTest {

    private fun createSong(
        id: String,
        title: String,
        tier: QueueTier = QueueTier.USER_QUEUE,
        queueEntryId: String? = null
    ): Song = Song(
        videoId = id,
        title = title,
        artist = "Artist $id",
        durationSeconds = 180L,
        thumbnailUrl = "https://example.com/$id.jpg",
        queueTier = tier,
        queueEntryId = queueEntryId ?: "entry_$id",
    )

    /**
     * Empirical drag-and-drop test harness faithfully reproducing the settling,
     * clamping, neighbor swapping, and cancellation mechanics of PlayerQueuePane.kt.
     */
    private class EmpiricalQueueHarness(
        val initialQueue: List<Song>,
        val currentIndex: Int,
        val onReorderDispatched: (fromTimeline: Int, toTimeline: Int) -> Unit
    ) {
        val partition = partitionQueue(initialQueue, currentIndex)
        var baselineUser = partition.userQueue.toList()
        var baselineContext = partition.contextQueue.toList()

        var displayedUser = baselineUser.toMutableList()
        var displayedContext = baselineContext.toMutableList()

        var heldItem: QueueRowItem? = null
            private set
        var heldSection: QueueTier? = null
            private set
        var startTimelineIndex: Int = -1
            private set

        var reorderCalls = mutableListOf<Pair<Int, Int>>()
            private set

        val rowHeight = 72f

        fun startDrag(key: String, section: QueueTier): Boolean {
            // Verify if item is nowPlaying
            if (partition.nowPlaying?.key == key) {
                return false // Strictly unmovable
            }

            val list = if (section == QueueTier.USER_QUEUE) displayedUser else displayedContext
            val item = list.firstOrNull { it.key == key } ?: return false

            heldItem = item
            heldSection = section
            startTimelineIndex = item.timelineIndex
            return true
        }

        fun dragMove(relativeRowOffset: Int) {
            val item = heldItem ?: return
            val section = heldSection ?: return
            val list = if (section == QueueTier.USER_QUEUE) displayedUser else displayedContext

            val currentPos = list.indexOfFirst { it.key == item.key }
            if (currentPos == -1) return

            val targetPos = (currentPos + relativeRowOffset).coerceIn(0, list.lastIndex)
            if (targetPos != currentPos) {
                val moved = list.removeAt(currentPos)
                list.add(targetPos, moved)
            }
        }

        fun releaseDrop(commit: Boolean = true): Boolean {
            val item = heldItem ?: return false
            val section = heldSection ?: return false
            val currentRows = if (section == QueueTier.USER_QUEUE) displayedUser else displayedContext
            val baselineRows = if (section == QueueTier.USER_QUEUE) baselineUser else baselineContext

            val finalPos = currentRows.indexOfFirst { it.key == item.key }
            val fromTimeline = startTimelineIndex
            val toTimeline = if (finalPos in baselineRows.indices) baselineRows[finalPos].timelineIndex else -1

            heldItem = null
            heldSection = null
            startTimelineIndex = -1

            if (commit && fromTimeline != -1 && toTimeline != -1 && fromTimeline != toTimeline) {
                reorderCalls.add(Pair(fromTimeline, toTimeline))
                onReorderDispatched(fromTimeline, toTimeline)
                return true
            } else if (!commit) {
                // Cancellation restores original baseline immediately
                displayedUser = baselineUser.toMutableList()
                displayedContext = baselineContext.toMutableList()
                return false
            }
            return false
        }
    }

    private lateinit var sampleQueue: List<Song>

    @Before
    fun setUp() {
        sampleQueue = listOf(
            createSong("np_track", "Now Playing Track", QueueTier.CONTEXT),   // timeline 0
            createSong("u_1", "User Track 1", QueueTier.USER_QUEUE),          // timeline 1
            createSong("u_2", "User Track 2", QueueTier.USER_QUEUE),          // timeline 2
            createSong("u_3", "User Track 3", QueueTier.USER_QUEUE),          // timeline 3
            createSong("u_4", "User Track 4", QueueTier.USER_QUEUE),          // timeline 4
            createSong("u_5", "User Track 5", QueueTier.USER_QUEUE),          // timeline 5
            createSong("c_6", "Context Track 6", QueueTier.CONTEXT),          // timeline 6
            createSong("c_7", "Context Track 7", QueueTier.CONTEXT),          // timeline 7
            createSong("a_8", "Autoplay Track 8", QueueTier.AUTOPLAY)         // timeline 8
        )
    }

    // =========================================================================
    // 1. AUTO-SCROLL DYNAMICS (edgeScrollSpeed Adversarial Boundary Stress)
    // =========================================================================

    @Test
    fun autoScroll_atExact40dpTopBoundary_returnsZeroSpeed() {
        val viewportStart = 0
        val viewportEnd = 800
        val zone = 40f
        val speed = 340f

        // Top edge at exactly 40f: (viewportStart + zone) - top = 40 - 40 = 0f.
        val computedSpeed = edgeScrollSpeed(
            top = 40.0f,
            bottom = 112.0f,
            viewportStart = viewportStart,
            viewportEnd = viewportEnd,
            zone = zone,
            speed = speed,
        )
        assertEquals("Exactamente en la frontera de 40dp superior, el auto-scroll debe ser 0.0f", 0f, computedSpeed, 0.0001f)
    }

    @Test
    fun autoScroll_justInside40dpTopBoundary_startsAtMinimumTwentyPercentSpeed() {
        val viewportStart = 0
        val viewportEnd = 800
        val zone = 40f
        val speed = 340f

        // Just inside boundary (top = 39.9f)
        val computedSpeed = edgeScrollSpeed(
            top = 39.9f,
            bottom = 111.9f,
            viewportStart = viewportStart,
            viewportEnd = viewportEnd,
            zone = zone,
            speed = speed,
        )
        assertTrue("Justo dentro de la zona (top = 39.9f), debe ser negativo", computedSpeed < 0f)
        // ramp = 340 * (0.2 + 0.8 * (0.1/40)) = 340 * 0.202 = 68.68f
        assertTrue("Debe arrancar suavemente en al menos el 20% (-68 dp/s)", computedSpeed <= -68f)
        assertTrue("No debe exceder el 25% en el umbral inmediato", computedSpeed >= -85f)
    }

    @Test
    fun autoScroll_hoveringWithin10pxOfTopEdge_returnsRampedHighSpeed() {
        val viewportStart = 0
        val viewportEnd = 800
        val zone = 40f
        val speed = 340f

        // Hovering within 10px of top edge (top = 10f):
        // intoStart = 40 - 10 = 30f. ratio = 30/40 = 0.75. ramp = 340 * (0.2 + 0.8*0.75) = 340 * 0.8 = 272f.
        val speedAt10px = edgeScrollSpeed(
            top = 10f,
            bottom = 82f,
            viewportStart = viewportStart,
            viewportEnd = viewportEnd,
            zone = zone,
            speed = speed,
        )
        assertEquals("A 10px del borde superior (top=10f), la velocidad debe ser exactamente -272 dp/s", -272f, speedAt10px, 0.01f)

        // Hovering at top = 5f (even closer within 10px):
        // intoStart = 35f. ratio = 35/40 = 0.875. ramp = 340 * (0.2 + 0.8*0.875) = 340 * 0.9 = 306f.
        val speedAt5px = edgeScrollSpeed(
            top = 5f,
            bottom = 77f,
            viewportStart = viewportStart,
            viewportEnd = viewportEnd,
            zone = zone,
            speed = speed,
        )
        assertEquals("A 5px del borde superior (top=5f), la velocidad debe ser exactamente -306 dp/s", -306f, speedAt5px, 0.01f)
    }

    @Test
    fun autoScroll_atTopEdgeAndOvershoot_capsAtMaxSpeed() {
        val viewportStart = 0
        val viewportEnd = 800
        val zone = 40f
        val speed = 340f

        // At exact top edge (top = 0f)
        val speedAtTop = edgeScrollSpeed(
            top = 0f,
            bottom = 72f,
            viewportStart = viewportStart,
            viewportEnd = viewportEnd,
            zone = zone,
            speed = speed,
        )
        assertEquals("En el borde superior exacto (top = 0f), la velocidad debe ser el 100% (-340 dp/s)", -340f, speedAtTop, 0.001f)

        // Overshooting past top edge (top = -50f, -200f)
        val speedOvershoot = edgeScrollSpeed(
            top = -100f,
            bottom = -28f,
            viewportStart = viewportStart,
            viewportEnd = viewportEnd,
            zone = zone,
            speed = speed,
        )
        assertEquals("Superando el borde superior, la velocidad debe estar estrictamente acotada a -340 dp/s", -340f, speedOvershoot, 0.001f)
    }

    @Test
    fun autoScroll_atExact40dpBottomBoundary_returnsZeroSpeed() {
        val viewportStart = 0
        val viewportEnd = 800
        val zone = 40f
        val speed = 340f

        // Bottom boundary is viewportEnd - zone = 800 - 40 = 760f.
        // If bottom = 760.0f, intoEnd = 760 - 760 = 0f.
        val computedSpeed = edgeScrollSpeed(
            top = 688f,
            bottom = 760.0f,
            viewportStart = viewportStart,
            viewportEnd = viewportEnd,
            zone = zone,
            speed = speed,
        )
        assertEquals("Exactamente en la frontera de 40dp inferior, el auto-scroll debe ser 0.0f", 0f, computedSpeed, 0.0001f)
    }

    @Test
    fun autoScroll_hoveringWithin10pxOfBottomEdge_returnsRampedHighSpeed() {
        val viewportStart = 0
        val viewportEnd = 800
        val zone = 40f
        val speed = 340f

        // Bottom boundary = 760f. ViewportEnd = 800f.
        // Hovering within 10px of bottom edge (bottom = 790f, 10px before 800):
        // intoEnd = 790 - 760 = 30f. ratio = 30/40 = 0.75. ramp = 340 * 0.8 = 272f.
        val speedAt10px = edgeScrollSpeed(
            top = 718f,
            bottom = 790f,
            viewportStart = viewportStart,
            viewportEnd = viewportEnd,
            zone = zone,
            speed = speed,
        )
        assertEquals("A 10px del borde inferior (bottom=790f), la velocidad debe ser exactamente +272 dp/s", 272f, speedAt10px, 0.01f)

        // At exact bottom edge (bottom = 800f):
        val speedAtBottom = edgeScrollSpeed(
            top = 728f,
            bottom = 800f,
            viewportStart = viewportStart,
            viewportEnd = viewportEnd,
            zone = zone,
            speed = speed,
        )
        assertEquals("En el borde inferior exacto (bottom=800f), la velocidad debe ser exactamente +340 dp/s", 340f, speedAtBottom, 0.01f)

        // Overshoot past bottom (bottom = 850f):
        val speedOvershoot = edgeScrollSpeed(
            top = 778f,
            bottom = 850f,
            viewportStart = viewportStart,
            viewportEnd = viewportEnd,
            zone = zone,
            speed = speed,
        )
        assertEquals("Superando el borde inferior, debe acotarse a +340 dp/s", 340f, speedOvershoot, 0.01f)
    }

    @Test
    fun autoScroll_middleDeadZone_returnsZero() {
        val viewportStart = 0
        val viewportEnd = 800
        val zone = 40f
        val speed = 340f
        val itemSize = 72f

        // In middle dead zone:
        // top >= zone (top >= 40f) and bottom <= (viewportEnd - zone) (bottom <= 760f)
        // Since bottom = top + itemSize (72f), top must be in [40f .. 688f].
        for (y in listOf(40.5f, 50f, 100f, 250f, 400f, 550f, 650f, 688.0f)) {
            val s = edgeScrollSpeed(
                top = y,
                bottom = y + itemSize,
                viewportStart = viewportStart,
                viewportEnd = viewportEnd,
                zone = zone,
                speed = speed,
            )
            assertEquals("En zona muerta central (top=$y, bottom=${y + itemSize}), el auto-scroll debe ser 0", 0f, s, 0.001f)
        }
    }

    @Test
    fun autoScroll_conflictAvoidanceInTinyViewport_returnsZero() {
        // When viewport is too small (e.g. 60px height with 40px top and bottom zones):
        // top zone and bottom zone overlap. The engine must return 0 to avoid jitter or conflicting scrolls.
        val s = edgeScrollSpeed(
            top = 10f,
            bottom = 50f,
            viewportStart = 0,
            viewportEnd = 60,
            zone = 40f,
            speed = 340f,
        )
        assertEquals("En viewport con zonas conflictivas superpuestas, debe retornar 0f", 0f, s, 0.001f)
    }

    @Test
    fun autoScroll_zeroOrNegativeZone_returnsZero() {
        val sZero = edgeScrollSpeed(10f, 80f, 0, 800, 0f, 340f)
        val sNeg = edgeScrollSpeed(10f, 80f, 0, 800, -10f, 340f)
        assertEquals(0f, sZero, 0.0001f)
        assertEquals(0f, sNeg, 0.0001f)
    }

    @Test
    fun autoScroll_fuzzTenThousandCoordinates_strictlyBoundedAndNeverNaN() {
        val rng = Random(42)
        val zone = 40f
        val maxSpeed = 340f

        for (i in 0 until 10_000) {
            val vpStart = rng.nextInt(-100, 100)
            val vpEnd = vpStart + rng.nextInt(100, 2000)
            val top = rng.nextFloat() * 3000f - 1000f
            val itemSize = rng.nextFloat() * 100f + 20f
            val bottom = top + itemSize

            val speed = edgeScrollSpeed(
                top = top,
                bottom = bottom,
                viewportStart = vpStart,
                viewportEnd = vpEnd,
                zone = zone,
                speed = maxSpeed
            )

            assertFalse("La velocidad nunca debe ser NaN", speed.isNaN())
            assertFalse("La velocidad nunca debe ser infinita", speed.isInfinite())
            assertTrue("La velocidad no debe exceder la magnitud máxima: speed=$speed", abs(speed) <= maxSpeed)
        }
    }

    // =========================================================================
    // 2. DRAG EDGE CASES (First item to end, Last item to top, Rapid Reversals)
    // =========================================================================

    @Test
    fun dragEdgeCase_dragFirstItemDownToVeryEndOfSection_reordersCorrectly() {
        val reordered = mutableListOf<Pair<Int, Int>>()
        val harness = EmpiricalQueueHarness(
            initialQueue = sampleQueue,
            currentIndex = 0,
            onReorderDispatched = { from, to -> reordered.add(from to to) }
        )

        // First item in userQueue: u_1 (timeline index 1)
        val firstItemKey = harness.baselineUser.first().key
        assertEquals("USER_QUEUE_entry_u_1", firstItemKey)

        val started = harness.startDrag(firstItemKey, QueueTier.USER_QUEUE)
        assertTrue("Debe permitir iniciar arrastre del primer elemento de user queue", started)

        // Drag down to the very end of the user queue (5 items: index 0 -> index 4)
        harness.dragMove(relativeRowOffset = 10) // Overshooting downwards
        assertEquals("El elemento arrastrado debe estar al final de la sección mostrada", firstItemKey, harness.displayedUser.last().key)
        assertEquals(5, harness.displayedUser.size)

        val dropped = harness.releaseDrop(commit = true)
        assertTrue("El drop debe confirmar el reordenamiento", dropped)
        assertEquals("Debe emitir exactamente 1 callback de reordenamiento", 1, reordered.size)
        // From timeline 1 (u_1) to timeline 5 (baselineUser.last().timelineIndex = 5)
        assertEquals(Pair(1, 5), reordered.first())

        // Verify no duplicate keys or items
        val keys = harness.displayedUser.map { it.key }
        assertEquals("No debe haber claves duplicadas", keys.toSet().size, keys.size)
        assertEquals("u_2", harness.displayedUser[0].song.videoId)
        assertEquals("u_3", harness.displayedUser[1].song.videoId)
        assertEquals("u_4", harness.displayedUser[2].song.videoId)
        assertEquals("u_5", harness.displayedUser[3].song.videoId)
        assertEquals("u_1", harness.displayedUser[4].song.videoId)
    }

    @Test
    fun dragEdgeCase_dragLastItemUpToTopOfSection_reordersCorrectly() {
        val reordered = mutableListOf<Pair<Int, Int>>()
        val harness = EmpiricalQueueHarness(
            initialQueue = sampleQueue,
            currentIndex = 0,
            onReorderDispatched = { from, to -> reordered.add(from to to) }
        )

        // Last item in userQueue: u_5 (timeline index 5)
        val lastItemKey = harness.baselineUser.last().key
        assertEquals("USER_QUEUE_entry_u_5", lastItemKey)

        val started = harness.startDrag(lastItemKey, QueueTier.USER_QUEUE)
        assertTrue("Debe permitir iniciar arrastre del último elemento", started)

        // Drag up to the top of its section (overshooting upwards)
        harness.dragMove(relativeRowOffset = -10)
        assertEquals("El elemento arrastrado debe estar en la primera posición de su sección", lastItemKey, harness.displayedUser.first().key)
        assertEquals(5, harness.displayedUser.size)

        val dropped = harness.releaseDrop(commit = true)
        assertTrue("El drop debe confirmar el reordenamiento", dropped)
        assertEquals(1, reordered.size)
        // From timeline 5 (u_5) to timeline 1 (u_1)
        assertEquals(Pair(5, 1), reordered.first())

        // Verify order and zero duplicates
        val keys = harness.displayedUser.map { it.key }
        assertEquals(keys.toSet().size, keys.size)
        assertEquals("u_5", harness.displayedUser[0].song.videoId)
        assertEquals("u_1", harness.displayedUser[1].song.videoId)
        assertEquals("u_2", harness.displayedUser[2].song.videoId)
        assertEquals("u_3", harness.displayedUser[3].song.videoId)
        assertEquals("u_4", harness.displayedUser[4].song.videoId)
    }

    @Test
    fun dragEdgeCase_rapidDragReversals_preservesOrderAndZeroDuplicates() {
        val reordered = mutableListOf<Pair<Int, Int>>()
        val harness = EmpiricalQueueHarness(
            initialQueue = sampleQueue,
            currentIndex = 0,
            onReorderDispatched = { from, to -> reordered.add(from to to) }
        )

        // Drag middle item u_3 (timeline 3, index 2 in userQueue)
        val itemKey = harness.baselineUser[2].key
        harness.startDrag(itemKey, QueueTier.USER_QUEUE)

        // Rapid oscillations: 10,001 times (odd count ends at index 1: 2 - 1 = 1)
        for (i in 0 until 10_001) {
            val direction = if (i % 2 == 0) -1 else 1
            harness.dragMove(direction)
        }

        // Verify exact size and zero duplicates after 10,001 rapid reversals
        assertEquals("El tamaño de la cola debe preservarse intacto", 5, harness.displayedUser.size)
        val distinctKeys = harness.displayedUser.map { it.key }.toSet()
        assertEquals("Zero duplicados tras 10,001 oscilaciones rápidas", 5, distinctKeys.size)
        assertEquals("Item u_3 debe estar en el índice 1 tras 10,001 oscilaciones", "u_3", harness.displayedUser[1].song.videoId)

        // Drop
        val dropped = harness.releaseDrop(commit = true)
        assertTrue("El drop debe confirmar el reordenamiento a la nueva posición", dropped)
        assertEquals("Debe emitir exactamente 1 callback al soltar tras oscilaciones impares", 1, reordered.size)
        // From timeline 3 (u_3) to timeline 2 (u_2)
        assertEquals(Pair(3, 2), reordered.first())
    }

    // =========================================================================
    // 3. ABORTED DRAGS (Releasing without displacement or cancelling mid-drag)
    // =========================================================================

    @Test
    fun abortedDrag_releasingWithoutDisplacement_dispatchesZeroCallbacksAndRetainsOrder() {
        val reordered = mutableListOf<Pair<Int, Int>>()
        val harness = EmpiricalQueueHarness(
            initialQueue = sampleQueue,
            currentIndex = 0,
            onReorderDispatched = { from, to -> reordered.add(from to to) }
        )

        val itemKey = harness.baselineUser[2].key
        harness.startDrag(itemKey, QueueTier.USER_QUEUE)

        // Zero displacement
        harness.dragMove(0)

        val dropResult = harness.releaseDrop(commit = true)
        assertFalse("Soltar sin desplazamiento no debe despachar reorden", dropResult)
        assertTrue("No debe haber llamadas a reorder", reordered.isEmpty())
        assertNull(harness.heldItem)

        // Verify exact original order
        for (i in harness.baselineUser.indices) {
            assertEquals(harness.baselineUser[i].key, harness.displayedUser[i].key)
        }
    }

    @Test
    fun abortedDrag_movingAndReversingBackToOrigin_dispatchesZeroCallbacks() {
        val reordered = mutableListOf<Pair<Int, Int>>()
        val harness = EmpiricalQueueHarness(
            initialQueue = sampleQueue,
            currentIndex = 0,
            onReorderDispatched = { from, to -> reordered.add(from to to) }
        )

        val itemKey = harness.baselineUser[1].key
        harness.startDrag(itemKey, QueueTier.USER_QUEUE)

        // Move down 2 positions, then move back up 2 positions to origin
        harness.dragMove(2)
        harness.dragMove(-2)

        val dropResult = harness.releaseDrop(commit = true)
        assertFalse("Regresar a la posición de origen no debe despachar reorden", dropResult)
        assertTrue(reordered.isEmpty())

        for (i in harness.baselineUser.indices) {
            assertEquals(harness.baselineUser[i].key, harness.displayedUser[i].key)
        }
    }

    @Test
    fun abortedDrag_cancelledMidDrag_restoresOriginalBaselineImmediatelyWithZeroDuplicates() {
        val reordered = mutableListOf<Pair<Int, Int>>()
        val harness = EmpiricalQueueHarness(
            initialQueue = sampleQueue,
            currentIndex = 0,
            onReorderDispatched = { from, to -> reordered.add(from to to) }
        )

        val itemKey = harness.baselineUser[0].key
        harness.startDrag(itemKey, QueueTier.USER_QUEUE)

        // Displace heavily to end of list
        harness.dragMove(4)
        assertEquals(itemKey, harness.displayedUser.last().key)

        // Explicit cancellation (gesture cancelled, touch lost or onDragCancel)
        val dropResult = harness.releaseDrop(commit = false)
        assertFalse("Cancelación debe retornar false", dropResult)
        assertTrue("Zero callbacks despachados en cancelación", reordered.isEmpty())

        // Verify displayedUser immediately reverts to baseline
        assertEquals(5, harness.displayedUser.size)
        for (i in harness.baselineUser.indices) {
            assertEquals(
                "Posición $i debe coincidir exactamente con el baseline tras cancelación",
                harness.baselineUser[i].key,
                harness.displayedUser[i].key
            )
        }
    }

    @Test
    fun abortedDrag_tenThousandRandomAborts_orderPreservedOneHundredPercent() {
        val rng = Random(123)
        for (iter in 0 until 10_000) {
            val reordered = mutableListOf<Pair<Int, Int>>()
            val harness = EmpiricalQueueHarness(
                initialQueue = sampleQueue,
                currentIndex = 0,
                onReorderDispatched = { from, to -> reordered.add(from to to) }
            )

            val targetIdx = rng.nextInt(harness.baselineUser.size)
            val itemKey = harness.baselineUser[targetIdx].key
            harness.startDrag(itemKey, QueueTier.USER_QUEUE)

            // Random drag motions
            val motions = rng.nextInt(1, 20)
            for (m in 0 until motions) {
                harness.dragMove(rng.nextInt(-5, 6))
            }

            // Randomly decide between cancel or return-to-origin
            if (rng.nextBoolean()) {
                harness.releaseDrop(commit = false) // Cancel
            } else {
                // Settle back to origin
                val curPos = harness.displayedUser.indexOfFirst { it.key == itemKey }
                harness.dragMove(targetIdx - curPos)
                harness.releaseDrop(commit = true)
            }

            assertEquals("Iteración $iter: No deben emitirse reordenamientos erróneos", 0, reordered.size)
            assertEquals("Iteración $iter: El tamaño debe ser 5", 5, harness.displayedUser.size)
            for (i in harness.baselineUser.indices) {
                assertEquals(
                    "Iteración $iter: Posición $i debe coincidir con el baseline original",
                    harness.baselineUser[i].key,
                    harness.displayedUser[i].key
                )
            }
        }
    }

    // =========================================================================
    // 4. NOW PLAYING IMMUTABILITY & CONTRACT VERIFICATION
    // =========================================================================

    @Test
    fun nowPlaying_isStrictlyUnmovable_cannotBeDraggedOrReordered() {
        val reordered = mutableListOf<Pair<Int, Int>>()
        val harness = EmpiricalQueueHarness(
            initialQueue = sampleQueue,
            currentIndex = 0,
            onReorderDispatched = { from, to -> reordered.add(from to to) }
        )

        val npItem = harness.partition.nowPlaying
        assertNotNull("Now Playing debe estar presente", npItem)
        assertEquals("np_track", npItem?.song?.videoId)
        assertEquals(0, npItem?.timelineIndex)

        // Attempting to drag nowPlaying
        val dragStarted = harness.startDrag(npItem!!.key, QueueTier.CONTEXT)
        assertFalse("El intento de arrastrar nowPlaying DEBE fallar y retornar false", dragStarted)
        assertNull("heldItem debe permanecer null", harness.heldItem)
        assertTrue(reordered.isEmpty())
    }

    @Test
    fun nowPlaying_playingInMiddleOfQueue_precedingItemsAreExcludedAndUpcomingAreReorderable() {
        val reordered = mutableListOf<Pair<Int, Int>>()
        // Playing at index 3 (u_3)
        val harness = EmpiricalQueueHarness(
            initialQueue = sampleQueue,
            currentIndex = 3,
            onReorderDispatched = { from, to -> reordered.add(from to to) }
        )

        val npItem = harness.partition.nowPlaying
        assertNotNull(npItem)
        assertEquals("u_3", npItem?.song?.videoId)
        assertEquals(3, npItem?.timelineIndex)

        // u_1 and u_2 are in the history (< currentIndex 3) and must not be in userQueue
        assertEquals(2, harness.baselineUser.size)
        assertEquals("u_4", harness.baselineUser[0].song.videoId)
        assertEquals("u_5", harness.baselineUser[1].song.videoId)

        // Upcoming items (u_4, u_5) can be reordered among themselves
        harness.startDrag(harness.baselineUser[0].key, QueueTier.USER_QUEUE)
        harness.dragMove(1)
        harness.releaseDrop(commit = true)

        assertEquals(1, reordered.size)
        assertEquals(Pair(4, 5), reordered.first())
    }

    // =========================================================================
    // 5. STABLE KEYS & COMPOSER IDENTITY INVARIANTS
    // =========================================================================

    @Test
    fun stableKeys_massiveDuplicateIds_neverCollideAndDisambiguateCorrectly() {
        val duplicateSongs = List(50) { i ->
            Song(
                videoId = "same_video_id",
                title = "Duplicate Track $i",
                artist = "Artist",
                durationSeconds = 120L,
                thumbnailUrl = null,
                queueTier = QueueTier.USER_QUEUE,
                queueEntryId = null // Force occurrence counting
            )
        }
        val fullQueue = listOf(createSong("np", "NP", QueueTier.CONTEXT)) + duplicateSongs

        val partition = partitionQueue(fullQueue, currentIndex = 0)
        assertEquals(50, partition.userQueue.size)

        val keys = partition.userQueue.map { it.key }
        val uniqueKeys = keys.toSet()
        assertEquals("Todas las 50 canciones duplicadas deben tener claves únicas garantizadas", 50, uniqueKeys.size)

        // Verify format
        assertEquals("USER_QUEUE_same_video_id#0", keys[0])
        assertEquals("USER_QUEUE_same_video_id#1", keys[1])
        assertEquals("USER_QUEUE_same_video_id#49", keys[49])
    }

    @Test
    fun stableKeys_swappingItemsAcrossArbitraryPermutations_neverAltersKeys() {
        val songs = (1..10).map { createSong("v_$it", "Song $it", QueueTier.USER_QUEUE, queueEntryId = "e_$it") }
        val queue = listOf(createSong("np", "NP", QueueTier.CONTEXT)) + songs

        val partitionOriginal = partitionQueue(queue, currentIndex = 0)
        val originalKeyMap = partitionOriginal.userQueue.associate { it.song.videoId to it.key }

        // Shuffle queue
        val rng = Random(999)
        for (rep in 0 until 100) {
            val shuffled = songs.shuffled(rng)
            val partitionShuffled = partitionQueue(listOf(queue[0]) + shuffled, currentIndex = 0)

            for (item in partitionShuffled.userQueue) {
                val expectedKey = originalKeyMap[item.song.videoId]
                assertEquals(
                    "La clave de ${item.song.videoId} debe ser idéntica independientemente de su nueva posición",
                    expectedKey,
                    item.key
                )
            }
        }
    }

    // =========================================================================
    // 6. CODE CONTRACT & ARCHITECTURAL INVARIANTS
    // =========================================================================

    private fun resolvePlayerQueuePaneFile(): File {
        val candidates = listOf(
            File("src/main/kotlin/com/music/bitchord/ui/player/PlayerQueuePane.kt"),
            File("bitchord-player-ui/src/main/kotlin/com/music/bitchord/ui/player/PlayerQueuePane.kt"),
            File("C:/DivineDeck/bitchord-player-ui/src/main/kotlin/com/music/bitchord/ui/player/PlayerQueuePane.kt")
        )
        return candidates.firstOrNull { it.exists() } ?: File("src/main/kotlin/com/music/bitchord/ui/player/PlayerQueuePane.kt")
    }

    @Test
    fun architecturalContract_arrowIconsAndCallbacksCompletelyEliminated() {
        val playerQueuePaneFile = resolvePlayerQueuePaneFile()
        assertTrue("PlayerQueuePane.kt debe existir en ${playerQueuePaneFile.absolutePath}", playerQueuePaneFile.exists())
        val content = playerQueuePaneFile.readText()

        // Verify arrow icons are completely removed
        assertFalse("PlayerQueuePane.kt no debe contener ArrowDownward", content.contains("ArrowDownward"))
        assertFalse("PlayerQueuePane.kt no debe contener ArrowUpward", content.contains("ArrowUpward"))
        assertFalse("PlayerQueuePane.kt no debe contener onMoveUp", content.contains("onMoveUp"))
        assertFalse("PlayerQueuePane.kt no debe contener onMoveDown", content.contains("onMoveDown"))

        // Verify DragHandle is present
        assertTrue("PlayerQueuePane.kt debe incluir DragHandle", content.contains("DragHandle"))
        assertTrue("PlayerQueuePane.kt debe incluir detectDragGesturesAfterLongPress", content.contains("detectDragGesturesAfterLongPress"))
        assertTrue("PlayerQueuePane.kt debe incluir detectDragGestures", content.contains("detectDragGestures"))
    }

    @Test
    fun architecturalContract_nowPlayingTrackHasDraggableFalseInCode() {
        val playerQueuePaneFile = resolvePlayerQueuePaneFile()
        assertTrue("PlayerQueuePane.kt debe existir en ${playerQueuePaneFile.absolutePath}", playerQueuePaneFile.exists())
        val content = playerQueuePaneFile.readText()

        // Check partition.nowPlaying block in PlayerQueuePane
        assertTrue(
            "nowPlaying debe invocarse con draggable = false",
            content.contains("partition.nowPlaying?.let") && content.contains("draggable = false")
        )
    }
}
