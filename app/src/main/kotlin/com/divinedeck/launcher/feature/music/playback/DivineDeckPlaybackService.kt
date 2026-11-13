package com.divinedeck.launcher.feature.music.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.divinedeck.launcher.MainActivity
import com.divinedeck.launcher.feature.garage3d.domain.model.LowriderBeatEngine
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.music.bitchord.data.Http
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.innertube.AndroidStreamHooks
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.EqualizerMode
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.playback.ChunkedDataSource
import com.music.bitchord.playback.EqualizerProcessor
import com.music.bitchord.playback.PermanentAwareLoadErrorPolicy
import com.music.bitchord.playback.QueueCoordinator
import com.music.bitchord.playback.QueueShuffle
import com.music.bitchord.playback.SleepTimer
import com.music.bitchord.playback.SpatialAudioProcessor
import com.music.bitchord.playback.manualCurve
import com.music.bitchord.playback.toneCurve
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Servicio Multimedia Unificado de DIVINE DECK.
 *
 * Integra el motor de reproducción original de BitChord 1.8.1 (GPLv3) como motor de producción
 * exclusivo de la aplicación, satisfaciendo la regla de oro:
 * - Una única instancia de producción de ExoPlayer.
 * - Una única instancia de MediaLibrarySession / MediaSession.
 * - Una única cola autoritativa gestionada mediante QueueCoordinator.
 * - Un pipeline de resolución de streams en capas (OkHttp -> Chunked -> Resolving -> Default -> Cache).
 * - Control de errores con PermanentAwareLoadErrorPolicy y reintentos acotados.
 * - Sin reproducción automática de audio de diagnóstico al iniciar.
 */
@OptIn(UnstableApi::class)
class DivineDeckPlaybackService : MediaLibraryService() {

    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val recoveries = ConcurrentHashMap<String, Int>()

    companion object {
        const val TAG = "BitChordPlayback"
        const val CHANNEL_ID = "bitchord_playback"
        const val SESSION_ID = "BitChordPlayback"

        // Constantes de audio de demostración DIVINE DECK (preservadas para pruebas de regresión)
        const val SAMPLE_ASSET_URI = "asset:///chime_test.wav"
        const val SAMPLE_TRACK_ID = "divine_deck_sample_chime"
        const val SAMPLE_TRACK_TITLE = "Divine Deck Chime"
        const val SAMPLE_TRACK_ARTIST = "DIVINE DECK Studio"
        const val SAMPLE_TRACK_ALBUM = "Cockpit Soundscape"

        // Comandos de sesión
        const val ACTION_PLAY_SAMPLE = "com.divinedeck.ACTION_PLAY_SAMPLE"
        const val ACTION_CLEAR_QUEUE = "com.divinedeck.ACTION_CLEAR_QUEUE"
        const val ACTION_SHUFFLE_TOGGLE = "com.music.bitchord.ACTION_SHUFFLE_TOGGLE"

        // Constantes de buffer del motor original de BitChord
        const val FAR_BUFFER_MS = 15 * 60 * 1000 // 15 minutos
        const val FAR_BUFFER_BYTES = 8 * 1024 * 1024 // 8 MB
        const val BACK_BUFFER_MS = 30 * 1000 // 30 segundos
        const val START_PLAYBACK_MS = 500 // Inicia tras 500ms
        const val RESUME_PLAYBACK_MS = 2_000 // Reanuda tras 2000ms
        const val BACK_RESTARTS_AFTER_MS = 10_000L // 10 segundos
        const val STREAM_CHUNK_BYTES = 2L * 1024 * 1024 // 2 MB
        const val RESOLVE_TIMEOUT_MS = 25_000L
        const val MAX_RECOVERIES = 2

        @Volatile
        var activeAudioEnergyProcessor: AudioEnergyProcessor? = null
            internal set

        /**
         * Sesión de audio del ExoPlayer de BitChord (0 = reproductor aún no
         * creado o sin sesión asignada). La lee el motor Lowrider para
         * intentar el Visualizer sobre la sesión real del reproductor cuando
         * la mezcla del sistema (sesión 0) falla en el dispositivo. Se
         * actualiza al crear el reproductor y cada vez que cambia la sesión.
         */
        @Volatile
        var playbackAudioSessionId: Int = 0
            private set
    }

    private var serviceScope: CoroutineScope? = null
    private var sleepTimerJob: Job? = null
    private val spatialAudioProcessor = SpatialAudioProcessor()
    private val equalizerProcessor = EqualizerProcessor()
    val audioEnergyProcessor = AudioEnergyProcessor()
    private var dspSyncJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        activeAudioEnergyProcessor = audioEnergyProcessor
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        // 1. Inicialización de subsistemas del motor BitChord con contexto de la aplicación
        AndroidStreamHooks.installEarly()
        AppSettings.init(this)
        AudioCache.init(this)
        Downloads.init(this)
        AndroidStreamHooks.initInnerTubeX(this)
        createNotificationChannel()

        // 2. Configuración de atributos de audio automotriz
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // 3. Cadena de fuentes de datos de BitChord: OkHttp -> Chunked -> Resolving -> Default -> Cache
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(Http.client)
        val chunkedFactory = ChunkedDataSource.Factory(okHttpDataSourceFactory, STREAM_CHUNK_BYTES)

        val resolvingFactory = ResolvingDataSource.Factory(chunkedFactory) { dataSpec ->
            resolveDataSpec(dataSpec)
        }

        val defaultDataSourceFactory = DefaultDataSource.Factory(this, resolvingFactory)
        val cachedDataSourceFactory = AudioCache.playbackFactory(defaultDataSourceFactory)

        val mediaSourceFactory = DefaultMediaSourceFactory(cachedDataSourceFactory)
            .setLoadErrorHandlingPolicy(PermanentAwareLoadErrorPolicy())

        // 4. LoadControl de buffer extendido (farBufferingLoadControl)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                FAR_BUFFER_MS,
                START_PLAYBACK_MS,
                RESUME_PLAYBACK_MS
            )
            .setTargetBufferBytes(FAR_BUFFER_BYTES)
            .setBackBuffer(BACK_BUFFER_MS, /* retainBackBufferFromKeyframe = */ true)
            .build()

        // 5. RenderersFactory con AudioSink y AudioProcessors (Spatial Audio -> Equalizer -> AudioEnergy)
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false) // Ruta PCM16 obligatoria para compatibilidad universal de efectos DSP
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(spatialAudioProcessor, equalizerProcessor, audioEnergyProcessor))
                    .build()
            }
        }

        // 6. Construcción de ExoPlayer único de producción
        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setMaxSeekToPreviousPositionMs(BACK_RESTARTS_AFTER_MS)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                handlePlayerError(error, exoPlayer)
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                // La sesión se asigna al crear el AudioTrack; publicarla aquí
                // cubre el caso en que aún era 0 al construir el reproductor.
                // Si el Lowrider está en modo CLOCK, intenta subir a BEAT.
                playbackAudioSessionId = audioSessionId
                LowriderBeatEngine.setPlaybackSessionId(audioSessionId)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    exoPlayer.currentMediaItem?.mediaId?.let { recoveries.remove(it) }
                } else if (state == Player.STATE_ENDED) {
                    SleepTimer.cancel()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.let { recoveries.remove(it) }
                val ended = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                if (ended && SleepTimer.afterTrack.value) {
                    exoPlayer.pause()
                    SleepTimer.cancel()
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                if (AppSettings.repeatMode.value != repeatMode) {
                    AppSettings.setRepeatMode(repeatMode)
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (AppSettings.shuffleEnabled.value != shuffleModeEnabled) {
                    AppSettings.setShuffleEnabled(shuffleModeEnabled)
                }
            }
        })

        player = exoPlayer
        // Publicar la sesión de audio del reproductor para el motor Lowrider
        // (puede ser 0 hasta que se cree el AudioTrack; el listener
        // onAudioSessionIdChanged la actualiza en cuanto se asigna).
        playbackAudioSessionId = exoPlayer.audioSessionId
        LowriderBeatEngine.setPlaybackSessionId(exoPlayer.audioSessionId)
        watchSleepTimer(exoPlayer)
        watchDspSettings()
        watchPlaybackSettings(exoPlayer)

        // 6. Configuración de MediaLibrarySession
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val callback = DivineDeckMediaLibraryCallback()

        mediaLibrarySession = MediaLibrarySession.Builder(this, exoPlayer, callback)
            .setSessionActivity(sessionActivityPendingIntent)
            .setId(SESSION_ID)
            .build()

        // 7. Notificación multimedia en primer plano
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .build()
        )

        // El reproductor arranca limpio y en reposo: sin reproducción automática ni siembra de chime
    }

    /**
     * Resolución controlada de URIs multimedia.
     * Se ejecuta en el hilo de carga en segundo plano de Media3.
     * Garantiza que NUNCA se devuelva una URI 'bitchord://' no resuelta a OkHttpDataSource.
     */
    private fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        val scheme = uri.scheme

        // 1. URI virtual de BitChord: bitchord://watch?v=...
        if (scheme == "bitchord") {
            val videoId = uri.getQueryParameter("v")
                ?: uri.schemeSpecificPart.substringAfter("v=").substringBefore('&')

            if (videoId.isBlank()) {
                throw IOException("Invalid bitchord URI: missing videoId in $uri")
            }

            // Prioridad offline: si existe archivo local descargado y verificado en disco, usarlo sin red
            val verifiedLocalUri = Downloads.verifiedSavedUriString(videoId)
            if (!verifiedLocalUri.isNullOrBlank()) {
                return dataSpec.buildUpon()
                    .setUri(Uri.parse(verifiedLocalUri))
                    .build()
            }

            val streamUrl = try {
                runBlocking {
                    withTimeout(RESOLVE_TIMEOUT_MS) {
                        StreamResolver.resolve(videoId)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                TrackLog.e(TAG, "Stream resolution timed out for $videoId after ${RESOLVE_TIMEOUT_MS}ms")
                throw IOException("Stream resolution timed out for $videoId", e)
            } catch (t: Throwable) {
                TrackLog.e(TAG, "Stream resolution error for $videoId: ${t.message}", t)
                throw IOException("Stream resolution failed for $videoId: ${t.message}", t)
            }

            if (streamUrl.isNullOrBlank() || (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://"))) {
                TrackLog.w(TAG, "Resolved stream URL is invalid for $videoId: $streamUrl")
                throw IOException("Resolved stream URL is invalid for $videoId: $streamUrl")
            }

            val headers = StreamResolver.mediaHeadersFor(streamUrl)
            return dataSpec.buildUpon()
                .setUri(Uri.parse(streamUrl))
                .setHttpRequestHeaders(headers)
                .build()
        }

        // 2. Enlaces directos HTTP / HTTPS
        if (scheme == "http" || scheme == "https") {
            val headers = StreamResolver.mediaHeadersFor(uri.toString())
            return if (headers.isNotEmpty()) {
                dataSpec.buildUpon()
                    .setHttpRequestHeaders(headers)
                    .build()
            } else {
                dataSpec
            }
        }

        // 3. URIs locales (content://, file://, asset://) pasan sin resolución HTTP
        if (scheme == "content" || scheme == "file" || scheme == "asset") {
            return dataSpec
        }

        // 4. Esquema no reconocido o no soportado
        throw IOException("Unsupported media URI scheme: $scheme in $uri")
    }

    private fun handlePlayerError(error: PlaybackException, player: ExoPlayer) {
        val item = player.currentMediaItem ?: return
        val mediaId = item.mediaId
        val attempts = (recoveries[mediaId] ?: 0) + 1
        recoveries[mediaId] = attempts
        TrackLog.w(TAG, "Playback error for $mediaId (attempt $attempts): ${error.message}", error)

        if (attempts <= MAX_RECOVERIES && error.errorCode != PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) {
            TrackLog.d(TAG, "Retrying $mediaId from position ${player.currentPosition}ms")
            player.prepare()
            player.play()
        } else {
            TrackLog.w(TAG, "Giving up on $mediaId after $attempts attempts")
            if (player.hasNextMediaItem()) {
                TrackLog.i(TAG, "Skipping to next media item in queue")
                player.seekToNextMediaItem()
                player.prepare()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BitChord Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Reproducción de audio en segundo plano de DIVINE DECK"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun createSampleMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(SAMPLE_TRACK_TITLE)
            .setArtist(SAMPLE_TRACK_ARTIST)
            .setAlbumTitle(SAMPLE_TRACK_ALBUM)
            .build()

        return MediaItem.Builder()
            .setMediaId(SAMPLE_TRACK_ID)
            .setUri(SAMPLE_ASSET_URI)
            .setMediaMetadata(metadata)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || AppSettings.stopOnTaskRemoved.value) {
            stopSelf()
        }
    }

    private fun watchSleepTimer(exoPlayer: Player) {
        val scope = serviceScope ?: return
        sleepTimerJob?.cancel()
        sleepTimerJob = scope.launch {
            SleepTimer.deadline.collectLatest { deadline ->
                if (deadline == null) return@collectLatest
                val remaining = deadline - SleepTimer.timeProvider()
                if (remaining > 0L) {
                    delay(remaining)
                }
                exoPlayer.pause()
                SleepTimer.cancel()
            }
        }
    }

    private fun watchDspSettings() {
        val scope = serviceScope ?: return
        dspSyncJob?.cancel()

        // Sincronización inmediata al inicio
        applyEqualizerSettings()
        applySpatialAudioSettings()

        dspSyncJob = scope.launch {
            launch {
                combine(
                    listOf(
                        AppSettings.equalizerEnabled,
                        AppSettings.equalizerMode,
                        AppSettings.equalizerToneX,
                        AppSettings.equalizerToneY,
                        AppSettings.equalizerFocused,
                        AppSettings.equalizerBalance,
                        AppSettings.equalizerBands,
                    )
                ) { _ -> Unit }.collectLatest {
                    applyEqualizerSettings()
                }
            }
            launch {
                AppSettings.spatialAudio.collectLatest {
                    applySpatialAudioSettings()
                }
            }
        }
    }

    private fun applyEqualizerSettings() {
        val enabled = AppSettings.equalizerEnabled.value
        val mode = AppSettings.equalizerMode.value
        val curve = when (mode) {
            EqualizerMode.TONE_PAD -> toneCurve(
                x = AppSettings.equalizerToneX.value,
                y = AppSettings.equalizerToneY.value,
                focused = AppSettings.equalizerFocused.value,
            )
            EqualizerMode.MANUAL -> manualCurve(AppSettings.equalizerBands.value)
        }
        val balance = AppSettings.equalizerBalance.value
        equalizerProcessor.setTuning(enabled, curve, balance)
    }

    private fun applySpatialAudioSettings() {
        spatialAudioProcessor.enabled = AppSettings.spatialAudio.value
    }

    private fun watchPlaybackSettings(exoPlayer: ExoPlayer) {
        val scope = serviceScope ?: return
        // Aplicar estado inicial
        exoPlayer.setPlaybackSpeed(AppSettings.playbackSpeed.value)
        exoPlayer.skipSilenceEnabled = AppSettings.skipSilence.value
        exoPlayer.repeatMode = AppSettings.repeatMode.value
        exoPlayer.shuffleModeEnabled = AppSettings.shuffleEnabled.value

        scope.launch {
            launch {
                AppSettings.playbackSpeed.collectLatest { speed ->
                    exoPlayer.setPlaybackSpeed(speed)
                }
            }
            launch {
                AppSettings.skipSilence.collectLatest { skip ->
                    exoPlayer.skipSilenceEnabled = skip
                }
            }
            launch {
                AppSettings.repeatMode.collectLatest { mode ->
                    if (exoPlayer.repeatMode != mode) {
                        exoPlayer.repeatMode = mode
                    }
                }
            }
            launch {
                AppSettings.shuffleEnabled.collectLatest { shuffle ->
                    if (exoPlayer.shuffleModeEnabled != shuffle) {
                        exoPlayer.shuffleModeEnabled = shuffle
                    }
                }
            }
        }
    }

    internal fun getEqualizerProcessorForTest(): EqualizerProcessor = equalizerProcessor
    internal fun getSpatialAudioProcessorForTest(): SpatialAudioProcessor = spatialAudioProcessor
    internal fun getAudioEnergyProcessorForTest(): AudioEnergyProcessor = audioEnergyProcessor

    override fun onDestroy() {
        if (activeAudioEnergyProcessor === audioEnergyProcessor) {
            activeAudioEnergyProcessor = null
        }
        dspSyncJob?.cancel()
        dspSyncJob = null
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        serviceScope?.cancel()
        serviceScope = null
        SleepTimer.cancel()
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        player = null
        playbackAudioSessionId = 0
        AudioCache.release()
        super.onDestroy()
    }

    private inner class DivineDeckMediaLibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val playSampleCmd = SessionCommand(ACTION_PLAY_SAMPLE, Bundle())
            val clearQueueCmd = SessionCommand(ACTION_CLEAR_QUEUE, Bundle())
            val shuffleToggleCmd = SessionCommand(ACTION_SHUFFLE_TOGGLE, Bundle())

            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(playSampleCmd)
                .add(clearQueueCmd)
                .add(shuffleToggleCmd)
                .build()

            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                .buildUpon()
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(playerCommands)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val p = player ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_SESSION_DISCONNECTED))
            return when (customCommand.customAction) {
                ACTION_PLAY_SAMPLE -> {
                    p.setMediaItem(createSampleMediaItem())
                    p.prepare()
                    p.play()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_CLEAR_QUEUE -> {
                    QueueCoordinator.clearUserQueue(p)
                    p.clearMediaItems()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_SHUFFLE_TOGGLE -> {
                    val current = QueueShuffle.enabled.value
                    val newState = !current
                    QueueShuffle.setEnabled(newState)
                    p.shuffleModeEnabled = newState
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                else -> super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            return Futures.immediateFuture(mediaItems)
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
            )
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId("divine_deck_root")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle("Divine Deck Library")
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items = if (parentId == "divine_deck_root") {
                ImmutableList.of(createSampleMediaItem())
            } else {
                ImmutableList.of()
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(items, params))
        }
    }
}
