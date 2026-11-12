package com.music.bitchord.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Representa una pista en la cola con su índice real en el timeline y su clave estable.
 */
data class QueueRowItem(
    val timelineIndex: Int,
    val song: Song,
    val key: String,
)

/**
 * Secciones de la cola divididas para visualización.
 */
data class QueuePartition(
    val nowPlaying: QueueRowItem?,
    val userQueue: List<QueueRowItem>,
    val contextQueue: List<QueueRowItem>,
    val autoplayQueue: List<QueueRowItem>,
)

/**
 * Particiona la cola de reproducción en secciones y genera llaves estables e independientes
 * de la posición en el timeline (sin incrustar `$i`).
 * Sigue la paridad con BitChord 1.8.1 `splitQueue`.
 */
fun partitionQueue(queue: List<Song>, currentIndex: Int): QueuePartition {
    if (queue.isEmpty()) {
        return QueuePartition(null, emptyList(), emptyList(), emptyList())
    }
    val safeIndex = if (currentIndex in queue.indices) currentIndex else 0

    val seen = HashMap<String, Int>()
    val occurrence = IntArray(queue.size) { i ->
        val id = queue[i].videoId
        seen.getOrDefault(id, 0).also { seen[id] = it + 1 }
    }
    val usedKeys = HashSet<String>()
    fun generateKey(index: Int, prefix: String): String {
        val song = queue[index]
        val base = prefix + "_" + (song.queueEntryId ?: "${song.videoId}#${occurrence[index]}")
        var key = base
        var repeat = 1
        while (!usedKeys.add(key)) key = base + "~" + repeat++
        return key
    }

    val nowPlayingItem = QueueRowItem(
        timelineIndex = safeIndex,
        song = queue[safeIndex],
        key = generateKey(safeIndex, "np"),
    )

    val user = mutableListOf<QueueRowItem>()
    val context = mutableListOf<QueueRowItem>()
    val autoplay = mutableListOf<QueueRowItem>()

    for (i in (safeIndex + 1) until queue.size) {
        val song = queue[i]
        val item = QueueRowItem(
            timelineIndex = i,
            song = song,
            key = generateKey(i, song.queueTier.name),
        )
        when (song.queueTier) {
            QueueTier.USER_QUEUE -> user.add(item)
            QueueTier.CONTEXT -> context.add(item)
            QueueTier.AUTOPLAY -> autoplay.add(item)
        }
    }

    return QueuePartition(
        nowPlaying = nowPlayingItem,
        userQueue = user,
        contextQueue = context,
        autoplayQueue = autoplay,
    )
}

val QUEUE_EDGE_SCROLL_ZONE = 40.dp
val QUEUE_EDGE_SCROLL_SPEED = 340.dp
private val QUEUE_ROW_MOTION = tween<IntOffset>(durationMillis = 200, easing = FastOutSlowInEasing)

/**
 * Calcula la velocidad de auto-scroll en píxeles/segundo para un elemento sostenido en los extremos.
 * Paridad matemática exacta con BitChord 1.8.1 `edgeScrollSpeed`.
 */
fun edgeScrollSpeed(
    top: Float,
    bottom: Float,
    viewportStart: Int,
    viewportEnd: Int,
    zone: Float,
    speed: Float,
): Float {
    if (zone <= 0f) return 0f
    val intoStart = (viewportStart + zone) - top
    val intoEnd = bottom - (viewportEnd - zone)
    val reach = when {
        intoStart > 0f && intoEnd <= 0f -> -intoStart
        intoEnd > 0f && intoStart <= 0f -> intoEnd
        else -> return 0f
    }
    val ramp = speed * (0.2f + 0.8f * (abs(reach) / zone).coerceAtMost(1f))
    return if (reach < 0f) -ramp else ramp
}

/**
 * Gestor de estado de arrastre y reordenación de la cola.
 * Mantiene la posición del elemento sostenido, intercambia visualmente los elementos adyacentes
 * dentro de su propia sección, calcula auto-scroll y despacha la reordenación a Media3 al soltar.
 */
private class QueueDrag(
    private val listState: LazyListState,
    private val onReorderDispatched: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    var baselineUserQueue: List<QueueRowItem> = emptyList()
    var baselineContextQueue: List<QueueRowItem> = emptyList()

    var displayedUserQueue by mutableStateOf<List<QueueRowItem>>(emptyList())
    var displayedContextQueue by mutableStateOf<List<QueueRowItem>>(emptyList())

    var edgeZone = 0f
    var edgeSpeed = 0f

    var held by mutableStateOf<QueueRowItem?>(null)
        private set

    var heldTop by mutableFloatStateOf(0f)
        private set

    var autoScrollDir by mutableIntStateOf(0)
        private set

    var autoScrollSpeed = 0f
        private set

    private var currentSection = QueueTier.USER_QUEUE
    private var heldCenter = 0f
    private var heldSize = 0
    private var awaiting: Int? = null

    private var startTimelineIndex = -1

    fun updateBaselines(user: List<QueueRowItem>, context: List<QueueRowItem>) {
        baselineUserQueue = user
        baselineContextQueue = context
        if (held == null) {
            displayedUserQueue = user
            displayedContextQueue = context
        }
    }

    fun start(item: QueueRowItem, section: QueueTier) {
        val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.key } ?: return
        this.currentSection = section
        heldSize = visibleItem.size
        heldCenter = visibleItem.offset + visibleItem.size / 2f
        heldTop = visibleItem.offset.toFloat()
        awaiting = null
        held = item
        startTimelineIndex = item.timelineIndex
    }

    fun drag(deltaY: Float) = settle(deltaY)

    fun scrolled() = settle(0f)

    fun end(key: String, commit: Boolean = true) {
        if (held?.key != key) return
        val currentRows = if (currentSection == QueueTier.USER_QUEUE) displayedUserQueue else displayedContextQueue
        val baselineRows = if (currentSection == QueueTier.USER_QUEUE) baselineUserQueue else baselineContextQueue

        val finalPos = currentRows.indexOfFirst { it.key == key }
        val fromTimeline = startTimelineIndex
        val toTimeline = if (finalPos in baselineRows.indices) baselineRows[finalPos].timelineIndex else -1

        held = null
        awaiting = null
        setAutoScroll(0f)
        startTimelineIndex = -1

        if (commit && fromTimeline != -1 && toTimeline != -1 && fromTimeline != toTimeline) {
            onReorderDispatched(fromTimeline, toTimeline)
        } else if (!commit) {
            displayedUserQueue = baselineUserQueue
            displayedContextQueue = baselineContextQueue
        }
    }

    private fun settle(deltaY: Float) {
        val key = held?.key ?: return
        val isUserSection = currentSection == QueueTier.USER_QUEUE
        val rows = if (isUserSection) displayedUserQueue else displayedContextQueue

        val info = listState.layoutInfo
        val items = info.visibleItemsInfo
        val half = heldSize / 2f

        var center = heldCenter + deltaY
        rows.firstOrNull()?.let { first ->
            items.firstOrNull { it.key == first.key }?.let { center = center.coerceAtLeast(it.offset + half) }
        }
        rows.lastOrNull()?.let { last ->
            items.firstOrNull { it.key == last.key }?.let { center = center.coerceAtMost(it.offset + it.size - half) }
        }
        heldCenter = center
        val top = center - half
        val minTop = info.viewportStartOffset.toFloat()
        heldTop = top.coerceIn(minTop, (info.viewportEndOffset - heldSize).toFloat().coerceAtLeast(minTop))

        val dragged = items.firstOrNull { it.key == key } ?: return setAutoScroll(0f)
        aimAutoScroll(top, key, rows)

        awaiting?.let {
            if (dragged.index != it) return
            awaiting = null
        }

        val target = items
            .filter { item -> item.key != key && rows.any { it.key == item.key } }
            .minByOrNull { abs(it.offset + it.size / 2f - center) }
            ?: return

        if (abs(center - (target.offset + target.size / 2f)) > target.size / 2f) return
        if (target.index == listState.firstVisibleItemIndex && listState.canScrollBackward) return

        val fromPos = rows.indexOfFirst { it.key == key }
        val toPos = rows.indexOfFirst { it.key == target.key }
        if (fromPos != -1 && toPos != -1 && fromPos != toPos) {
            val mutable = rows.toMutableList()
            val moved = mutable.removeAt(fromPos)
            mutable.add(toPos, moved)
            if (isUserSection) {
                displayedUserQueue = mutable
            } else {
                displayedContextQueue = mutable
            }
            awaiting = target.index
        }
    }

    private fun aimAutoScroll(top: Float, key: String, rows: List<QueueRowItem>) {
        val info = listState.layoutInfo
        val speed = edgeScrollSpeed(
            top = top,
            bottom = top + heldSize,
            viewportStart = info.viewportStartOffset,
            viewportEnd = info.viewportEndOffset,
            zone = edgeZone,
            speed = edgeSpeed,
        )
        val blocked = when {
            speed < 0f -> rows.firstOrNull()?.key == key || !listState.canScrollBackward
            speed > 0f -> rows.lastOrNull()?.key == key || !listState.canScrollForward
            else -> true
        }
        setAutoScroll(if (blocked) 0f else speed)
    }

    private fun setAutoScroll(speed: Float) {
        autoScrollSpeed = speed
        val direction = when {
            speed > 0f -> 1
            speed < 0f -> -1
            else -> 0
        }
        if (autoScrollDir != direction) autoScrollDir = direction
    }
}

@Composable
private fun rememberQueueDrag(
    listState: LazyListState,
    baselineUserQueue: List<QueueRowItem>,
    baselineContextQueue: List<QueueRowItem>,
    onReorder: ((fromIndex: Int, toIndex: Int) -> Unit)?,
): QueueDrag {
    val drag = remember(listState) {
        QueueDrag(listState) { from, to ->
            onReorder?.invoke(from, to)
        }
    }
    drag.updateBaselines(baselineUserQueue, baselineContextQueue)
    with(LocalDensity.current) {
        drag.edgeZone = QUEUE_EDGE_SCROLL_ZONE.toPx()
        drag.edgeSpeed = QUEUE_EDGE_SCROLL_SPEED.toPx()
    }
    val direction = drag.autoScrollDir
    LaunchedEffect(drag, direction) {
        if (direction == 0) return@LaunchedEffect
        listState.scroll {
            var previous = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(1f / 30f)
                previous = now
                if (scrollBy(drag.autoScrollSpeed * seconds) == 0f) break
                drag.scrolled()
            }
        }
    }
    return drag
}

/**
 * Panel de gestión de cola musical para BitChord Player Screen.
 * Diseñado y optimizado para visualización en tablet landscape (1340x800).
 *
 * Cumple con Requisito R2:
 * - Eliminación completa de botones de flechas para subir y bajar.
 * - Reordenamiento mediante drag & drop con pulsación prolongada o arrastre del tirador.
 * - Visualización de tarjeta flotante elevada y ranura de destino con `alpha(0f)`.
 * - Desplazamiento fluido de elementos vecinos con `animateItem`.
 * - Auto-scroll en los bordes superior e inferior (zona de 40dp).
 * - Canción en reproducción anclada sin interrupción de reproducción.
 * - Despacho atómico e inmediato a Media3 al soltar.
 */
@Composable
fun PlayerQueuePane(
    queue: List<Song>,
    currentSong: Song?,
    currentIndex: Int = -1,
    onSongClick: (Song, Int) -> Unit,
    onReorder: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    onRemove: ((index: Int) -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val resolvedIndex = remember(queue, currentSong, currentIndex) {
        if (currentIndex in queue.indices) {
            currentIndex
        } else {
            val idx = queue.indexOfFirst { it.videoId == currentSong?.videoId }
            if (idx >= 0) idx else 0
        }
    }

    val partition = remember(queue, resolvedIndex) {
        partitionQueue(queue, resolvedIndex)
    }

    val listState = rememberLazyListState()
    val drag = rememberQueueDrag(
        listState = listState,
        baselineUserQueue = partition.userQueue,
        baselineContextQueue = partition.contextQueue,
        onReorder = onReorder,
    )

    var showClearConfirmDialog by remember { mutableStateOf(false) }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "Vaciar cola de usuario",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            },
            text = {
                Text(
                    text = "¿Deseas eliminar de la cola todas las canciones añadidas manualmente? La lista del álbum o contexto original permanecerá intacta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        onClear?.invoke()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444)),
                ) {
                    Text("Vaciar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.7f)),
                ) {
                    Text("Cancelar")
                }
            },
            containerColor = Color(0xFF141414),
            shape = RoundedCornerShape(16.dp),
        )
    }

    if (queue.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = "La cola de reproducción está vacía",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Cabecera de la cola
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "COLA DE REPRODUCCIÓN",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                        ),
                        color = Color(0xFF00E5FF),
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                    ) {
                        Text(
                            text = "${queue.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }

                if (partition.userQueue.isNotEmpty() && onClear != null) {
                    Surface(
                        onClick = { showClearConfirmDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.12f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Vaciar cola",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Borrar",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFEF4444),
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Sección: En Reproducción (fijada, sin tirador ni arrastre)
                    partition.nowPlaying?.let { np ->
                        item(key = "header_now_playing") {
                            QueueSectionHeader(title = "EN REPRODUCCIÓN", tint = Color.White.copy(alpha = 0.7f))
                        }
                        item(key = np.key) {
                            QueueTrackRow(
                                item = np,
                                isCurrent = true,
                                draggable = false,
                                onClick = { onSongClick(np.song, np.timelineIndex) },
                                onRemove = null,
                            )
                        }
                    }

                    // Sección: A continuación en la cola (USER_QUEUE)
                    if (drag.displayedUserQueue.isNotEmpty()) {
                        item(key = "header_user_queue") {
                            Spacer(Modifier.height(8.dp))
                            QueueSectionHeader(
                                title = "A CONTINUACIÓN EN LA COLA (${drag.displayedUserQueue.size})",
                                tint = Color(0xFF00E5FF),
                            )
                        }
                        items(drag.displayedUserQueue, key = { it.key }) { item ->
                            val isHeld = drag.held?.key == item.key
                            if (isHeld) {
                                DisposableEffect(item.key) {
                                    onDispose { drag.end(item.key, commit = true) }
                                }
                            }
                            QueueTrackRow(
                                item = item,
                                isCurrent = false,
                                draggable = onReorder != null,
                                onDragStart = { drag.start(item, QueueTier.USER_QUEUE) },
                                onDrag = drag::drag,
                                onDragEnd = { drag.end(item.key, commit = true) },
                                onDragCancel = { drag.end(item.key, commit = false) },
                                onClick = { onSongClick(item.song, item.timelineIndex) },
                                onRemove = if (onRemove != null) { { onRemove(item.timelineIndex) } } else null,
                                modifier = if (isHeld) Modifier.alpha(0f) else Modifier.animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null,
                                    placementSpec = QUEUE_ROW_MOTION,
                                ),
                            )
                        }
                    }

                    // Sección: Siguiente del álbum o contexto
                    if (drag.displayedContextQueue.isNotEmpty()) {
                        item(key = "header_context_queue") {
                            Spacer(Modifier.height(8.dp))
                            val contextTitle = drag.displayedContextQueue.firstOrNull()?.song?.albumName
                                ?.takeIf { it.isNotBlank() } ?: "ÁLBUM / LISTA"
                            QueueSectionHeader(
                                title = "SIGUIENTE DE $contextTitle".uppercase(),
                                tint = Color.White.copy(alpha = 0.5f),
                            )
                        }
                        items(drag.displayedContextQueue, key = { it.key }) { item ->
                            val isHeld = drag.held?.key == item.key
                            if (isHeld) {
                                DisposableEffect(item.key) {
                                    onDispose { drag.end(item.key, commit = true) }
                                }
                            }
                            QueueTrackRow(
                                item = item,
                                isCurrent = false,
                                draggable = onReorder != null,
                                onDragStart = { drag.start(item, QueueTier.CONTEXT) },
                                onDrag = drag::drag,
                                onDragEnd = { drag.end(item.key, commit = true) },
                                onDragCancel = { drag.end(item.key, commit = false) },
                                onClick = { onSongClick(item.song, item.timelineIndex) },
                                onRemove = if (onRemove != null) { { onRemove(item.timelineIndex) } } else null,
                                modifier = if (isHeld) Modifier.alpha(0f) else Modifier.animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null,
                                    placementSpec = QUEUE_ROW_MOTION,
                                ),
                            )
                        }
                    }

                    // Sección: AutoPlay
                    if (partition.autoplayQueue.isNotEmpty()) {
                        item(key = "header_autoplay_queue") {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AllInclusive,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(16.dp),
                                )
                                QueueSectionHeader(title = "AUTOPLAY INFINITO", tint = Color(0xFF00E5FF))
                            }
                        }
                        items(partition.autoplayQueue, key = { it.key }) { item ->
                            QueueTrackRow(
                                item = item,
                                isCurrent = false,
                                draggable = false,
                                onClick = { onSongClick(item.song, item.timelineIndex) },
                                onRemove = if (onRemove != null) { { onRemove(item.timelineIndex) } } else null,
                            )
                        }
                    }
                }

                // Tarjeta flotante superpuesta para el elemento arrastrado
                drag.held?.let { heldItem ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(0, drag.heldTop.roundToInt()) }
                            .graphicsLayer {
                                scaleX = 1.02f
                                scaleY = 1.02f
                                shadowElevation = 8.dp.toPx()
                                shape = RoundedCornerShape(10.dp)
                                clip = false
                            }
                    ) {
                        QueueTrackRow(
                            item = heldItem,
                            isCurrent = false,
                            lifted = true,
                            draggable = false,
                            onClick = {},
                            onRemove = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSectionHeader(
    title: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        ),
        color = tint,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun QueueTrackRow(
    item: QueueRowItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    draggable: Boolean = false,
    lifted: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
) {
    val song = item.song
    val rowShape = RoundedCornerShape(10.dp)
    val backgroundColor = when {
        lifted -> Color(0xFF262626)
        isCurrent -> Color(0xFF00E5FF).copy(alpha = 0.14f)
        else -> Color.White.copy(alpha = 0.04f)
    }

    val rowGestures = if (draggable && !lifted) {
        Modifier.pointerInput(item.key) {
            detectDragGesturesAfterLongPress(
                onDragStart = { onDragStart() },
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                onDrag = { change, amount ->
                    change.consume()
                    onDrag(amount.y)
                },
            )
        }
    } else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(backgroundColor)
            .clickable(enabled = !lifted, onClick = onClick)
            .then(rowGestures)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tirador de arrastre (cuando la pista es reordenable o está levantada)
        if (draggable || lifted) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = "Arrastrar para reordenar",
                tint = Color.White.copy(alpha = if (lifted) 0.85f else 0.45f),
                modifier = Modifier
                    .size(24.dp)
                    .padding(2.dp)
                    .then(
                        if (draggable && !lifted) {
                            Modifier.pointerInput(item.key) {
                                detectDragGestures(
                                    onDragStart = { onDragStart() },
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragCancel,
                                    onDrag = { change, amount ->
                                        change.consume()
                                        onDrag(amount.y)
                                    },
                                )
                            }
                        } else Modifier
                    ),
            )
            Spacer(Modifier.width(6.dp))
        }

        // Thumbnail
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!song.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Textos
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                ),
                color = if (isCurrent) Color(0xFF00E5FF) else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Indicador si es la pista activa
        if (isCurrent) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF00E5FF).copy(alpha = 0.18f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "En reproducción",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "SONANDO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                        ),
                        color = Color(0xFF00E5FF),
                    )
                }
            }
        } else {
            // Botón eliminar de la cola
            if (onRemove != null && !lifted) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Eliminar de la cola",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
