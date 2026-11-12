package com.divinedeck.launcher.feature.music.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divinedeck.launcher.feature.music.domain.MediaControllerRepository
import com.divinedeck.launcher.feature.music.domain.model.MediaServiceConnectionStatus
import com.divinedeck.launcher.feature.music.playback.AudioEnergyState
import com.divinedeck.launcher.feature.music.playback.DivineDeckPlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import com.music.bitchord.data.lyrics.LyricsUiState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.download.DownloadSession
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.components.DownloadManagerDialog
import com.music.bitchord.ui.components.PlaylistPickerSheet
import com.music.bitchord.ui.components.TopBarDownloadButton
import com.music.bitchord.ui.player.BitChordPlayerScreen

/**
 * Pantalla principal del Centro Multimedia de DIVINE DECK (Fase 06.5B).
 *
 * Incorpora navegación automotriz nativa en formato horizontal para Samsung Galaxy Tab A11 (1340x800):
 * - INICIO: Pantalla original `HomeScreen` de BitChord 1.8.1 con recomendaciones reales de YouTube Music.
 * - EXPLORAR: Pantalla original `ExploreScreen` y `MoodGenrePlaylistsScreen` de BitChord 1.8.1.
 * - BUSCAR: Buscador en tiempo real de YouTube Music mediante el motor original de BitChord.
 * - BIBLIOTECA: Pantalla original `LibraryScreen` de BitChord 1.8.1 con gestión real de playlists y favoritos.
 * - REPRODUCTOR: Interfaz original de reproducción de BitChord integrada con Media3.
 *
 * Permite seleccionar canciones o colecciones desde Inicio, Explorar, Buscar o Biblioteca, resolver sus streams
 * mediante StreamResolver y reproducirlas en segundo plano con persistencia total entre destinos
 * y retorno a Cockpit.
 */
@Composable
fun MusicCenterScreen(
    mediaRepository: MediaControllerRepository,
    onBackToCockpit: (() -> Unit)? = null,
    homeViewModel: MusicHomeViewModel = viewModel(),
    exploreViewModel: MusicExploreViewModel = viewModel(),
    searchViewModel: MusicSearchViewModel = viewModel(),
    libraryViewModel: MusicLibraryViewModel = viewModel(),
    lyricsViewModel: MusicLyricsViewModel = viewModel(),
    initialDestination: MusicCenterDestination = MusicCenterDestination.DEFAULT,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    remember(context) {
        LocalPlaylistStore.init(context.applicationContext)
        Downloads.init(context.applicationContext)
        true
    }

    val currentTrack by mediaRepository.currentTrack.collectAsState()
    val playbackState by mediaRepository.playbackState.collectAsState()
    val currentQueue by mediaRepository.currentQueue.collectAsState()
    val likedSongs by libraryViewModel.likedSongs.collectAsState()
    val savedCollections by libraryViewModel.savedCollections.collectAsState()
    val userPlaylistsState by libraryViewModel.userPlaylistsState.collectAsState()
    val activeDownloads by libraryViewModel.activeDownloads.collectAsState()
    val savedDownloads by libraryViewModel.savedDownloads.collectAsState()
    val savedDownloadMetadata by libraryViewModel.savedDownloadMetadata.collectAsState()
    val downloadedSongs by libraryViewModel.downloadedSongs.collectAsState()
    val downloadBatchState by libraryViewModel.downloadBatchState.collectAsState()
    val lyricsState by lyricsViewModel.lyricsState.collectAsState()
    val lyricsEffectsEnabled by AppSettings.lyricsEffectsEnabled.collectAsState()
    val lyricsEffectStyle by AppSettings.lyricsEffectStyle.collectAsState()
    val lyricsEffectIntensity by AppSettings.lyricsEffectIntensity.collectAsState()
    val lyricsBeatReaction by AppSettings.lyricsBeatReaction.collectAsState()
    val lyricsLowPowerMode by AppSettings.lyricsLowPowerMode.collectAsState()

    var activeDestination by rememberSaveable { mutableStateOf(initialDestination) }
    var previousDestination by rememberSaveable { mutableStateOf(MusicCenterDestination.HOME) }
    var showUnifiedSettings by rememberSaveable { mutableStateOf(false) }
    var unifiedSettingsCategory by rememberSaveable { mutableStateOf(BitChordSettingsCategory.AUDIO_DSP) }
    var showAudioSettings by rememberSaveable { mutableStateOf(false) }
    var showLyricsSettings by rememberSaveable { mutableStateOf(false) }
    var globalSongForPlaylistPicker by remember { mutableStateOf<Song?>(null) }
    var showDownloadCenterDialog by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(activeDestination) {
        val unsubscribe = if (activeDestination == MusicCenterDestination.PLAYER) {
            DivineDeckPlaybackService.activeAudioEnergyProcessor?.subscribe()
        } else {
            null
        }
        onDispose {
            unsubscribe?.invoke()
        }
    }

    val audioEnergyFlow = remember {
        DivineDeckPlaybackService.activeAudioEnergyProcessor?.energyFlow ?: MutableStateFlow(AudioEnergyState())
    }
    val audioEnergyState by audioEnergyFlow.collectAsState()

    LaunchedEffect(initialDestination) {
        activeDestination = initialDestination
    }

    val homeListState = rememberLazyListState()
    val exploreListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()

    // Selector de archivos de audio locales del sistema Android (preservado para acceso local)
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            mediaRepository.playLocalUri(uri)
            activeDestination = MusicCenterDestination.PLAYER
        }
    }

    // Adaptador estricto de modelo
    val song = remember(currentTrack) {
        BitChordMediaModelAdapter.toSong(currentTrack)
    }

    LaunchedEffect(song?.videoId, context) {
        lyricsViewModel.updateSong(song, context)
    }

    // Playhead desacoplado de BitChord para evitar recomposiciones completas
    val position = remember { PlaybackPosition() }
    LaunchedEffect(currentTrack.currentPositionMs, playbackState.isPlaying) {
        position.report(
            position = currentTrack.currentPositionMs,
            timeNanos = System.nanoTime(),
            moving = playbackState.isPlaying
        )
    }

    val onSwipeToQueueSong: (Song) -> Unit = remember(mediaRepository) {
        { targetSong ->
            if (AppSettings.swipeToPlayNext.value) {
                mediaRepository.enqueueNext(targetSong)
            } else {
                mediaRepository.enqueueLast(targetSong)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // BARRA SUPERIOR AUTÉNTICA ESTILO BITCHORD (52 dp, oculta en modo Reproductor a pantalla completa)
        if (activeDestination != MusicCenterDestination.PLAYER) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                color = Color.Black,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Lado izquierdo: Logo BitChord + Botón Volver a Cockpit + Título de sección
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(com.divinedeck.launcher.R.drawable.ic_logo),
                            contentDescription = "BitChord Logo",
                            tint = Color.White,
                            modifier = Modifier.height(20.dp)
                        )

                        if (onBackToCockpit != null) {
                            Surface(
                                onClick = onBackToCockpit,
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Volver a Cockpit",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "COCKPIT",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            letterSpacing = 1.sp
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        val headerTitle = when (activeDestination) {
                            MusicCenterDestination.HOME -> "Escuchar ahora"
                            MusicCenterDestination.EXPLORE -> "Explorar"
                            MusicCenterDestination.LIBRARY -> "Biblioteca"
                            MusicCenterDestination.SEARCH -> "Buscar"
                            MusicCenterDestination.PLAYER -> "Reproductor"
                        }
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = Color.White
                        )
                    }

                    // Lado derecho: Descargas, Audio Local, Ajustes BitChord, Conectividad
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TopBarDownloadButton(
                            batchState = downloadBatchState,
                            downloadedCount = downloadedSongs.size,
                            onClick = {
                                DownloadSession.markSeen()
                                showDownloadCenterDialog = true
                            }
                        )

                        IconButton(
                            onClick = { audioPickerLauncher.launch("audio/*") },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = "Cargar audio local",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                unifiedSettingsCategory = BitChordSettingsCategory.AUDIO_DSP
                                showUnifiedSettings = true
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (showUnifiedSettings) Color(0xFFFFB547).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
                                .border(
                                    1.dp,
                                    if (showUnifiedSettings) Color(0xFFFFB547) else Color.White.copy(alpha = 0.12f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "BitChord Ajustes",
                                tint = if (showUnifiedSettings) Color(0xFFFFB547) else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Indicador de estado del servicio Media3
                        val isConnected = playbackState.connectionStatus == MediaServiceConnectionStatus.CONNECTED
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isConnected) Color(0xFF00E5FF).copy(alpha = 0.12f) else Color(0xFFFF5252).copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnected) Color(0xFF00E5FF) else Color(0xFFFF5252))
                            )
                            Text(
                                text = if (isConnected) "ONLINE" else "OFFLINE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnected) Color(0xFF00E5FF) else Color(0xFFFF5252)
                            )
                        }
                    }
                }
            }
        }

        // ÁREA PRINCIPAL SEGÚN ESTADO DE CONEXIÓN Y DESTINO
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AnimatedContent(
                targetState = playbackState.connectionStatus,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                },
                modifier = Modifier.fillMaxSize(),
                label = "musicCenterStateTransition"
            ) { status ->
                when (status) {
                    MediaServiceConnectionStatus.CONNECTING -> {
                        MusicConnectingState()
                    }

                    MediaServiceConnectionStatus.DISCONNECTED,
                    MediaServiceConnectionStatus.FAILED,
                    MediaServiceConnectionStatus.DISCONNECTED_BY_SERVICE -> {
                        MusicDisconnectedState(
                            errorMessage = playbackState.errorMessage,
                            onReconnect = { mediaRepository.connect() }
                        )
                    }

                    MediaServiceConnectionStatus.CONNECTED -> {
                        if (showAudioSettings) {
                            BitChordAudioSettingsScreen(
                                onBack = { showAudioSettings = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (showLyricsSettings) {
                            BitChordLyricsSettingsScreen(
                                onBack = { showLyricsSettings = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            when (activeDestination) {
                            MusicCenterDestination.HOME -> {
                                MusicHomePane(
                                    viewModel = homeViewModel,
                                    currentSong = song,
                                    isPlaying = playbackState.isPlaying,
                                    listState = homeListState,
                                    onSongSelect = { selectedSong ->
                                        previousDestination = activeDestination
                                        mediaRepository.playSong(selectedSong)
                                        activeDestination = MusicCenterDestination.PLAYER
                                    },
                                    onPlayQueue = { songs, startIndex ->
                                        previousDestination = activeDestination
                                        mediaRepository.playQueue(songs, startIndex)
                                        activeDestination = MusicCenterDestination.PLAYER
                                    },
                                    isCollectionSaved = { browseId ->
                                        savedCollections.any { it.browseId == browseId }
                                    },
                                    onToggleSaveCollection = { browseId, title, subtitle, thumbnailUrl ->
                                        libraryViewModel.toggleSaveCollection(browseId, title, subtitle, thumbnailUrl)
                                    },
                                    isSongLiked = { targetSong ->
                                        likedSongs.any { it.videoId == targetSong.videoId }
                                    },
                                    onToggleLikeSong = { targetSong ->
                                        libraryViewModel.toggleFavoriteSong(targetSong)
                                    },
                                    onAddSongToPlaylist = { targetSong ->
                                        globalSongForPlaylistPicker = targetSong
                                    },
                                    downloadStates = activeDownloads,
                                    downloadedIds = savedDownloads.keys,
                                    onDownloadSong = { targetSong ->
                                        libraryViewModel.downloadSong(context, targetSong)
                                    },
                                    onDownloadAll = { songs ->
                                        libraryViewModel.downloadAll(context, songs)
                                    },
                                    onCancelDownload = { videoId ->
                                        libraryViewModel.cancelDownload(videoId)
                                    },
                                    onRetryDownload = { targetSong ->
                                        libraryViewModel.retryDownload(context, targetSong.videoId, targetSong)
                                    },
                                    onDeleteDownload = { videoId ->
                                        libraryViewModel.deleteDownload(context, videoId)
                                    },
                                    onSwipeToQueue = onSwipeToQueueSong,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            MusicCenterDestination.EXPLORE -> {
                                MusicExplorePane(
                                    viewModel = exploreViewModel,
                                    currentSong = song,
                                    isPlaying = playbackState.isPlaying,
                                    listState = exploreListState,
                                    onSongSelect = { selectedSong ->
                                        previousDestination = activeDestination
                                        mediaRepository.playSong(selectedSong)
                                        activeDestination = MusicCenterDestination.PLAYER
                                    },
                                    onPlayQueue = { songs, startIndex ->
                                        previousDestination = activeDestination
                                        mediaRepository.playQueue(songs, startIndex)
                                        activeDestination = MusicCenterDestination.PLAYER
                                    },
                                    isCollectionSaved = { browseId ->
                                        savedCollections.any { it.browseId == browseId }
                                    },
                                    onToggleSaveCollection = { browseId, title, subtitle, thumbnailUrl ->
                                        libraryViewModel.toggleSaveCollection(browseId, title, subtitle, thumbnailUrl)
                                    },
                                    isSongLiked = { targetSong ->
                                        likedSongs.any { it.videoId == targetSong.videoId }
                                    },
                                    onToggleLikeSong = { targetSong ->
                                        libraryViewModel.toggleFavoriteSong(targetSong)
                                    },
                                    onAddSongToPlaylist = { targetSong ->
                                        globalSongForPlaylistPicker = targetSong
                                    },
                                    downloadStates = activeDownloads,
                                    downloadedIds = savedDownloads.keys,
                                    onDownloadSong = { targetSong ->
                                        libraryViewModel.downloadSong(context, targetSong)
                                    },
                                    onDownloadAll = { songs ->
                                        libraryViewModel.downloadAll(context, songs)
                                    },
                                    onCancelDownload = { videoId ->
                                        libraryViewModel.cancelDownload(videoId)
                                    },
                                    onRetryDownload = { targetSong ->
                                        libraryViewModel.retryDownload(context, targetSong.videoId, targetSong)
                                    },
                                    onDeleteDownload = { videoId ->
                                        libraryViewModel.deleteDownload(context, videoId)
                                    },
                                    onSwipeToQueue = onSwipeToQueueSong,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            MusicCenterDestination.SEARCH -> {
                                MusicSearchPane(
                                    viewModel = searchViewModel,
                                    onSongSelect = { selectedSong ->
                                        previousDestination = activeDestination
                                        mediaRepository.playSong(selectedSong)
                                        activeDestination = MusicCenterDestination.PLAYER
                                    },
                                    isSongLiked = { targetSong ->
                                        likedSongs.any { it.videoId == targetSong.videoId }
                                    },
                                    onToggleLikeSong = { targetSong ->
                                        libraryViewModel.toggleFavoriteSong(targetSong)
                                    },
                                    onAddSongToPlaylist = { targetSong ->
                                        globalSongForPlaylistPicker = targetSong
                                    },
                                    isCollectionSaved = { browseId ->
                                        savedCollections.any { it.browseId == browseId }
                                    },
                                    onToggleSaveCollection = { item ->
                                        libraryViewModel.toggleSaveCollection(
                                            browseId = item.browseId,
                                            title = item.title,
                                            subtitle = item.subtitle,
                                            thumbnailUrl = item.thumbnailUrl,
                                        )
                                    },
                                    onBrowseItemClick = { item ->
                                        libraryViewModel.openCollection(
                                            browseId = item.browseId,
                                            title = item.title,
                                            subtitle = item.subtitle,
                                            thumbnailUrl = item.thumbnailUrl,
                                        )
                                        activeDestination = MusicCenterDestination.LIBRARY
                                    },
                                    downloadStates = activeDownloads,
                                    downloadedIds = savedDownloads.keys,
                                    onDownloadSong = { targetSong ->
                                        libraryViewModel.downloadSong(context, targetSong)
                                    },
                                    onCancelDownload = { videoId ->
                                        libraryViewModel.cancelDownload(videoId)
                                    },
                                    onRetryDownload = { targetSong ->
                                        libraryViewModel.retryDownload(context, targetSong.videoId, targetSong)
                                    },
                                    onDeleteDownload = { videoId ->
                                        libraryViewModel.deleteDownload(context, videoId)
                                    },
                                    onSwipeToQueue = onSwipeToQueueSong,
                                )
                            }

                            MusicCenterDestination.LIBRARY -> {
                                MusicLibraryPane(
                                    viewModel = libraryViewModel,
                                    currentSong = song,
                                    isPlaying = playbackState.isPlaying,
                                    listState = libraryListState,
                                    onSongSelect = { selectedSong ->
                                        previousDestination = activeDestination
                                        mediaRepository.playSong(selectedSong)
                                        activeDestination = MusicCenterDestination.PLAYER
                                    },
                                    onPlayQueue = { songs, startIndex ->
                                        previousDestination = activeDestination
                                        mediaRepository.playQueue(songs, startIndex)
                                        activeDestination = MusicCenterDestination.PLAYER
                                    },
                                    onOpenDownloadsCenter = {
                                        DownloadSession.markSeen()
                                        showDownloadCenterDialog = true
                                    },
                                    onSwipeToQueue = onSwipeToQueueSong,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            MusicCenterDestination.PLAYER -> {
                                val currentLikeStatus = remember(song, likedSongs) {
                                    val s = song
                                    if (s != null && likedSongs.any { it.videoId == s.videoId }) {
                                        LikeStatus.LIKE
                                    } else {
                                        LikeStatus.INDIFFERENT
                                    }
                                }
                                val currentDownloadState = song?.videoId?.let { activeDownloads[it] }
                                val currentIsDownloaded = song?.videoId?.let { it in savedDownloads } == true
                                Box(modifier = Modifier.fillMaxSize()) {
                                    BitChordPlayerScreen(
                                        song = song,
                                        position = position,
                                        isPlaying = playbackState.isPlaying,
                                        isLoading = !playbackState.isPlaying && playbackState.isBuffering,
                                        durationMs = currentTrack.durationMs ?: 0L,
                                        lyrics = (lyricsState as? LyricsUiState.Synced)?.lines
                                            ?: (lyricsState as? LyricsUiState.Plain)?.lines
                                            ?: (lyricsState as? LyricsUiState.EmbeddedLocal)?.lines,
                                        lyricsState = lyricsState,
                                        queue = currentQueue,
                                        currentIndex = currentQueue.indexOfFirst { it.videoId == song?.videoId }.coerceAtLeast(0),
                                        onSongSelect = { _, index ->
                                            mediaRepository.skipToQueueItem(index)
                                        },
                                        onReorderQueue = { from, to ->
                                            mediaRepository.reorderQueue(from, to)
                                        },
                                        onRemoveQueueItem = { idx ->
                                            mediaRepository.removeFromQueue(idx)
                                        },
                                        onClearQueue = {
                                            mediaRepository.clearUserQueue()
                                        },
                                        onRetryLyrics = {
                                            lyricsViewModel.retry(context, song)
                                        },
                                        likeStatus = currentLikeStatus,
                                        downloadState = currentDownloadState,
                                        isDownloaded = currentIsDownloaded,
                                        canPlayPause = playbackState.canPlayPause,
                                        canPrevious = playbackState.canSkipPrevious,
                                        canNext = playbackState.canSkipNext,
                                        canSeek = playbackState.canSeek,
                                        onPlayPause = {
                                            mediaRepository.togglePlayback()
                                        },
                                        onNext = {
                                            if (playbackState.canSkipNext) mediaRepository.skipToNext()
                                        },
                                        onPrevious = {
                                            if (playbackState.canSkipPrevious) mediaRepository.skipToPrevious()
                                        },
                                        onSeek = { targetMs ->
                                            if (playbackState.canSeek) mediaRepository.seekTo(targetMs)
                                        },
                                        onSeekFraction = { fraction ->
                                            if (playbackState.canSeek) {
                                                val dur = currentTrack.durationMs
                                                if (dur != null && dur > 0L) {
                                                    mediaRepository.seekTo((fraction * dur).toLong())
                                                }
                                            }
                                        },
                                        onLyricClick = { targetMs ->
                                            if (playbackState.canSeek) mediaRepository.seekTo(targetMs)
                                        },
                                        onToggleLike = {
                                            val s = song
                                            if (s != null) {
                                                libraryViewModel.toggleFavoriteSong(s)
                                            }
                                        },
                                        onDownloadSong = { targetSong ->
                                            libraryViewModel.downloadSong(context, targetSong)
                                        },
                                        onCancelDownload = { videoId ->
                                            libraryViewModel.cancelDownload(videoId)
                                        },
                                        onRetryDownload = { targetSong ->
                                            libraryViewModel.retryDownload(context, targetSong.videoId, targetSong)
                                        },
                                        onDeleteDownload = { videoId ->
                                            libraryViewModel.deleteDownload(context, videoId)
                                        },
                                        onOpenEqualizer = {
                                            unifiedSettingsCategory = BitChordSettingsCategory.AUDIO_DSP
                                            showUnifiedSettings = true
                                        },
                                        onOpenLyricsSettings = {
                                            unifiedSettingsCategory = BitChordSettingsCategory.LYRICS_SOURCES
                                            showUnifiedSettings = true
                                        },
                                        effectsEnabled = lyricsEffectsEnabled,
                                        effectStyle = lyricsEffectStyle,
                                        effectIntensity = lyricsEffectIntensity,
                                        beatReaction = lyricsBeatReaction,
                                        lowPowerMode = lyricsLowPowerMode,
                                        audioEnergy = audioEnergyState.bassEnergy,
                                        beatPulse = audioEnergyState.beatPulse,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Botón circular de colapso a pestañas de navegación
                                    IconButton(
                                        onClick = { activeDestination = previousDestination },
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(start = 16.dp, top = 16.dp)
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.60f))
                                            .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = "Volver a navegación",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // DOCK DE NAVEGACIÓN FLOTANTE ANCLADO EN LA PARTE INFERIOR CENTRAL (BottomCenter)
            val isConnected = playbackState.connectionStatus == MediaServiceConnectionStatus.CONNECTED
            if (activeDestination != MusicCenterDestination.PLAYER && isConnected && !showAudioSettings && !showLyricsSettings) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (song != null) {
                        BitChordFloatingMiniPlayer(
                            song = song,
                            isPlaying = playbackState.isPlaying,
                            isBuffering = !playbackState.isPlaying && playbackState.isBuffering,
                            currentPosMs = currentTrack.currentPositionMs,
                            durationMs = currentTrack.durationMs ?: 0L,
                            onExpand = {
                                previousDestination = activeDestination
                                activeDestination = MusicCenterDestination.PLAYER
                            },
                            onPlayPause = { mediaRepository.togglePlayback() },
                            onNext = { mediaRepository.skipToNext() },
                            onPrevious = { mediaRepository.skipToPrevious() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    BitChordBottomFloatingBar(
                        selectedDestination = activeDestination,
                        onDestinationSelected = { dest ->
                            if (dest == activeDestination) {
                                when (dest) {
                                    MusicCenterDestination.HOME -> homeViewModel.closeCollection()
                                    MusicCenterDestination.EXPLORE -> {
                                        if (!exploreViewModel.closeCollection()) exploreViewModel.closeCategory()
                                    }
                                    MusicCenterDestination.LIBRARY -> {
                                        if (!libraryViewModel.closeCollection()) libraryViewModel.closeShelfGrid()
                                    }
                                    else -> {}
                                }
                            } else {
                                previousDestination = dest
                                activeDestination = dest
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    val pickerSong = globalSongForPlaylistPicker
    if (pickerSong != null) {
        val currentPlaylists = (userPlaylistsState as? UiState.Success)?.data.orEmpty()
        PlaylistPickerSheet(
            playlists = currentPlaylists,
            songToAdd = pickerSong,
            onAddToPlaylists = { selectedPlaylists ->
                libraryViewModel.addSongToUserPlaylists(pickerSong, selectedPlaylists)
                globalSongForPlaylistPicker = null
            },
            onCreatePlaylist = { title, privacy ->
                libraryViewModel.createPlaylist(title, privacy, initialSong = pickerSong)
                globalSongForPlaylistPicker = null
            },
            onDismiss = { globalSongForPlaylistPicker = null },
        )
    }

    if (showDownloadCenterDialog) {
        DownloadManagerDialog(
            batchState = downloadBatchState,
            activeStates = activeDownloads,
            downloadedSongs = downloadedSongs,
            savedMetadata = savedDownloadMetadata,
            onDismiss = { showDownloadCenterDialog = false },
            onPlaySong = { selectedSong ->
                showDownloadCenterDialog = false
                mediaRepository.playSong(selectedSong)
                activeDestination = MusicCenterDestination.PLAYER
            },
            onPlayAllOffline = { songs, shuffle ->
                if (songs.isNotEmpty()) {
                    val targetQueue = if (shuffle) songs.shuffled() else songs
                    showDownloadCenterDialog = false
                    mediaRepository.playQueue(targetQueue, 0)
                    activeDestination = MusicCenterDestination.PLAYER
                }
            },
            onCancelDownload = { videoId ->
                libraryViewModel.cancelDownload(videoId)
            },
            onCancelAll = {
                libraryViewModel.cancelAllDownloads()
            },
            onRetryDownload = { videoId ->
                libraryViewModel.retryDownload(context, videoId)
            },
            onDismissFailure = { failureKey ->
                DownloadSession.dismissFailure(failureKey)
            },
            onDeleteDownload = { videoId ->
                libraryViewModel.deleteDownload(context, videoId)
            },
        )
    }

    if (showUnifiedSettings) {
        BitChordUnifiedSettingsDialog(
            onDismiss = { showUnifiedSettings = false },
            initialCategory = unifiedSettingsCategory,
            onLaunchAudioPicker = { audioPickerLauncher.launch("audio/*") },
            downloadedCount = downloadedSongs.size
        )
    }
}

@Composable
private fun NavigationTabPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillShape = RoundedCornerShape(percent = 50)
    Surface(
        onClick = onClick,
        shape = pillShape,
        color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.08f)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.8.sp,
                color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun MusicConnectingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = Color(0xFF00E5FF),
            strokeWidth = 3.dp
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Conectando con el motor multimedia BitChord...",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f)
        )
    }
}

@Composable
private fun MusicDisconnectedState(
    errorMessage: String?,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicOff,
            contentDescription = null,
            tint = Color(0xFFFF5252),
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Servicio multimedia desconectado",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.60f),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onReconnect,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Reconectar Servicio",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
}
