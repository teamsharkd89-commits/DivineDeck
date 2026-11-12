package com.music.bitchord.playback

import androidx.media3.common.Player
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song

private fun Player.queueTierAt(index: Int): QueueTier = getMediaItemAt(index).queueTier

/**
 * Headless orchestrator for two-tier queue operations on Media3 Player.
 * Extracted from BitChord 1.8.1 (GPLv3).
 */
object QueueCoordinator {

    fun Song.asQueueEntry(tier: QueueTier): Song = with(QueueTimeline) { asQueueEntry(tier) }

    fun buildContextQueue(
        currentTimeline: List<Song>,
        currentIndex: Int,
        newContextSongs: List<Song>,
        selectedIndex: Int,
        contextSource: QueueSource,
    ): ContextQueueResult = QueueTimeline.buildContextQueue(
        currentTimeline, currentIndex, newContextSongs, selectedIndex, contextSource,
    )

    fun buildOneOffQueue(
        currentTimeline: List<Song>,
        currentIndex: Int,
        tappedSong: Song,
        source: QueueSource,
    ): List<Song> = QueueTimeline.buildOneOffQueue(currentTimeline, currentIndex, tappedSong, source)

    fun findUserQueueInsertionIndex(
        timeline: List<Song>,
        currentIndex: Int,
        isNext: Boolean,
    ): Int = QueueTimeline.findUserQueueInsertionIndex(timeline, currentIndex, isNext)

    fun clearUserQueue(player: Player, tierAt: (Int) -> QueueTier = player::queueTierAt) {
        val currentIndex = player.currentMediaItemIndex
        val count = player.mediaItemCount
        if (count == 0) return

        val userQueueIndices = mutableListOf<Int>()
        for (i in (currentIndex + 1) until count) {
            if (tierAt(i) == QueueTier.USER_QUEUE) {
                userQueueIndices.add(i)
            }
        }

        for (i in userQueueIndices.asReversed()) {
            player.removeMediaItem(i)
        }
    }

    fun consumePlayedUserQueue(player: Player, tierAt: (Int) -> QueueTier = player::queueTierAt) {
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex <= 0) return
        if (player.currentMediaItem == null) return
        if (tierAt(currentIndex) != QueueTier.CONTEXT) return

        val playedIndices = mutableListOf<Int>()
        for (i in 0 until currentIndex) {
            if (tierAt(i) == QueueTier.USER_QUEUE) {
                playedIndices.add(i)
            }
        }

        for (i in playedIndices.asReversed()) {
            player.removeMediaItem(i)
        }
    }

    fun jumpToQueueItem(
        player: Player,
        targetIndex: Int,
        cachedTimeline: List<Song>? = null,
        tierAt: (Int) -> QueueTier = player::queueTierAt,
        positionMs: Long = 0L,
        play: Boolean = true,
    ) {
        val currentIndex = player.currentMediaItemIndex
        val count = player.mediaItemCount
        if (targetIndex !in 0 until count) return

        val tiers = cachedTimeline?.takeIf { it.size == count }?.map { it.queueTier }
            ?: (0 until count).map { tierAt(it) }
        val plan = QueueTimeline.computeJumpOrder(tiers, currentIndex, targetIndex) ?: return

        if (plan.isPureSeek) {
            player.seekTo(plan.newTargetIndex, positionMs)
            if (play) player.play()
            return
        }

        val existingItems = (0 until count).map { player.getMediaItemAt(it) }
        val newItems = plan.newOrderIndices.map { idx ->
            val item = existingItems[idx]
            if (idx == targetIndex && plan.promoteTargetToContext) {
                QueueShuffle.withQueueMetadata(item, newTier = QueueTier.CONTEXT)
            } else {
                item
            }
        }

        val historyKept = plan.newOrderIndices.take(currentIndex + 1) == (0..currentIndex).toList()
        if (historyKept) {
            QueueShuffle.replaceChanged(player, currentIndex + 1, count, newItems.drop(currentIndex + 1))
            player.seekTo(plan.newTargetIndex, positionMs)
        } else {
            player.setMediaItems(newItems, plan.newTargetIndex, positionMs)
        }
        if (runCatching { player.playbackState }.getOrNull() == Player.STATE_IDLE) {
            player.prepare()
        }
        if (play) player.play()
    }
}
