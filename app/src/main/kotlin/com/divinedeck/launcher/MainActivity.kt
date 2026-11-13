package com.divinedeck.launcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.divinedeck.launcher.feature.navcenter.service.ReturnHomeFloatingService
import kotlinx.coroutines.flow.MutableStateFlow
import com.divinedeck.launcher.core.designsystem.components.AutomotiveBottomDock
import com.divinedeck.launcher.core.designsystem.components.CockpitStatusBar
import com.divinedeck.launcher.core.designsystem.theme.CockpitLayout
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme
import com.divinedeck.launcher.core.navigation.DivineDeckRoute
import com.divinedeck.launcher.feature.appcenter.data.AppLauncherRepositoryImpl
import com.divinedeck.launcher.feature.appcenter.ui.AppCenterScreen
import com.divinedeck.launcher.feature.cockpit.ui.CockpitScreen
import com.divinedeck.launcher.feature.garage3d.data.Chevy3DContractImpl
import com.divinedeck.launcher.feature.garage3d.domain.model.LowriderBeatEngine
import com.divinedeck.launcher.feature.garage3d.domain.model.SpeedVisualMode
import com.divinedeck.launcher.feature.garage3d.ui.Garage3DScreen
import com.divinedeck.launcher.feature.gps.data.GpsSpeedometerRepositoryImpl
import com.divinedeck.launcher.feature.gps.domain.model.GpsStatus
import com.divinedeck.launcher.feature.gps.domain.model.MovementState
import com.divinedeck.launcher.feature.gps.ui.GpsPermissionRationaleDialog
import com.divinedeck.launcher.feature.launcher.domain.LauncherExitManager
import com.divinedeck.launcher.feature.launcher.ui.ExitConfirmationDialog
import com.divinedeck.launcher.feature.music.data.MediaControllerRepositoryImpl
import com.divinedeck.launcher.feature.music.ui.MusicCenterDestination
import com.divinedeck.launcher.feature.music.ui.MusicCenterScreen
import com.divinedeck.launcher.feature.navcenter.data.NavigationLauncherRepositoryImpl
import com.divinedeck.launcher.feature.navcenter.ui.NavCenterScreen
import com.divinedeck.launcher.feature.settings.data.SettingsRepositoryImpl
import com.divinedeck.launcher.feature.settings.model.OperatingMode
import com.divinedeck.launcher.feature.settings.ui.SettingsScreen
import com.divinedeck.launcher.feature.widgets.data.AndroidWidgetHostManagerImpl
import com.divinedeck.launcher.feature.widgets.ui.WidgetCenterScreen

/**
 * Actividad Principal de DIVINE DECK (Fase 4.0 — Real GPS Integration & Digital Speedometer).
 *
 * Estructura visual en cuatro zonas para 1340 × 800 Landscape:
 * 1. Barra superior de estado discreta con estado GPS honesto (`CockpitStatusBar`).
 * 2. Vehículo protagonista en el centro con velocidad digital real debajo (`VehicleShowcaseCard`).
 * 3. Dos espacios verticales configurables para widgets en el lateral derecho (`CockpitWidgetColumn`).
 * 4. Dock de navegación sólo con iconos (`AutomotiveBottomDock`): horizontal
 *    centrado en la parte inferior, salvo en Distribución Panorámica, donde va
 *    lateral a la derecha del modelo 3D.
 */
class MainActivity : ComponentActivity() {

    private val gpsRepository by lazy { GpsSpeedometerRepositoryImpl(applicationContext) }
    private val chevy3DRenderer by lazy { Chevy3DContractImpl() }
    private val mediaRepository by lazy { MediaControllerRepositoryImpl(applicationContext) }
    private val widgetManager by lazy { AndroidWidgetHostManagerImpl(applicationContext) }
    private val navRepository by lazy { NavigationLauncherRepositoryImpl() }
    private val appRepository by lazy { AppLauncherRepositoryImpl() }
    private val settingsRepository by lazy { SettingsRepositoryImpl(applicationContext) }
    private val launcherExitManager by lazy { LauncherExitManager(this) }

    // Señal de "volver al inicio" del botón flotante: el servicio lanza
    // MainActivity (singleTask) con el extra navigate_home; onCreate y
    // onNewIntent la elevan aquí y el contenido navega al Cockpit.
    private val navigateHomeSignal = MutableStateFlow(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNavigateHomeIntent(intent)

        // Bloqueo estricto de orientación HORIZONTAL (LANDSCAPE)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Mantiene la pantalla encendida para operación continua en el vehículo
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current

            val selectedTheme by settingsRepository.selectedTheme.collectAsState()
            val customAccentHex by settingsRepository.customAccentHex.collectAsState()
            val cockpitLayout by settingsRepository.cockpitLayout.collectAsState()
            val operatingMode by settingsRepository.operatingMode.collectAsState()
            val isLowriderEnabled by settingsRepository.isLowriderEnabled.collectAsState()

            val speedReading by gpsRepository.speedReading.collectAsState()
            val gpsStatus by gpsRepository.gpsStatus.collectAsState()
            val chevyState by chevy3DRenderer.modelState.collectAsState()
            val homeSignal by navigateHomeSignal.collectAsState()

            var currentRoute by remember { mutableStateOf(DivineDeckRoute.COCKPIT) }
            var musicDestination by remember { mutableStateOf(MusicCenterDestination.DEFAULT) }
            var showExitDialog by remember { mutableStateOf(false) }
            var showGpsRationaleDialog by remember { mutableStateOf(false) }

            // Vuelta al inicio desde el botón flotante (o la notificación):
            // aterriza siempre en el Cockpit, no en la última ruta abierta.
            LaunchedEffect(homeSignal) {
                if (homeSignal > 0) {
                    currentRoute = DivineDeckRoute.COCKPIT
                }
            }

            // Contrato de permisos en tiempo de ejecución (FINE y COARSE location)
            val locationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                val isGranted = fineGranted || coarseGranted
                gpsRepository.onPermissionStatusChanged(isGranted)
            }

            val requestLocationPermissionOrSettings = {
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!fine && !coarse) {
                    showGpsRationaleDialog = true
                } else {
                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    if (lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) != true) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        } catch (_: Exception) { }
                    } else {
                        gpsRepository.refreshProviderStatus()
                    }
                }
            }

            // Comprobación inicial de permisos en primer arranque
            LaunchedEffect(Unit) {
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!fine && !coarse) {
                    showGpsRationaleDialog = true
                }
            }

            // Sincronización continua de velocidad GPS real y fiabilidad con el motor 3D (Fase 4.5A)
            LaunchedEffect(speedReading.currentSpeedKmh, speedReading.isReliable, speedReading.movementState) {
                val effectiveSpeed = if (speedReading.isReliable && speedReading.movementState == MovementState.MOVING) {
                    speedReading.currentSpeedKmh
                } else {
                    0f
                }
                chevy3DRenderer.onSpeedUpdated(effectiveSpeed, speedReading.isReliable)
            }

            // Modo Lowrider: el motor de ritmo (Visualizer sobre la mezcla del
            // sistema, sesión 0) solo vive mientras el ajuste está activo Y el
            // permiso RECORD_AUDIO está concedido. Sin permiso no se arranca y
            // el Chevy simplemente no brinca; nunca se graba audio.
            //
            // Reintento al volver a la app: el ajuste lee el valor ACTUAL del
            // flujo (settingsRepository.isLowriderEnabled.value) en vez del
            // estado coleccionado, así el observador de ciclo de vida — que se
            // registra una sola vez — nunca ve un valor obsoleto del interruptor.
            // Si Daniel concede el permiso en los Ajustes del sistema y vuelve,
            // ON_START / ON_RESUME reintentan el arranque sin tocar el interruptor.
            val tryStartLowrider: () -> Unit = {
                val audioGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (settingsRepository.isLowriderEnabled.value && audioGranted) {
                    LowriderBeatEngine.start(context.applicationContext)
                } else {
                    LowriderBeatEngine.stop()
                }
            }

            LaunchedEffect(isLowriderEnabled) {
                tryStartLowrider()
            }

            // Ciclo de vida: rastreo GPS activo únicamente mientras la pantalla y app estén activas
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> {
                            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            gpsRepository.onPermissionStatusChanged(fine || coarse)
                            gpsRepository.startTracking()
                            widgetManager.startListening()
                            // Lowrider: reintento al volver a la app (permiso
                            // concedido en Ajustes del sistema).
                            tryStartLowrider()
                        }
                        Lifecycle.Event.ON_RESUME -> {
                            gpsRepository.refreshProviderStatus()
                            // Lowrider: reintento al volver del sistema de
                            // permisos sin tocar el interruptor.
                            tryStartLowrider()
                        }
                        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                            gpsRepository.stopTracking()
                            widgetManager.stopListening()
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    gpsRepository.stopTracking()
                    widgetManager.stopListening()
                }
            }

            DivineDeckTheme(
                themePreset = selectedTheme,
                customAccentHex = customAccentHex
            ) {
                val palette = DivineDeckTheme.palette

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.background)
                        .safeDrawingPadding()
                ) {
                    AnimatedContent(
                        targetState = currentRoute == DivineDeckRoute.MUSIC,
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                        },
                        label = "immersiveMusicModeTransition"
                    ) { isMusicImmersive ->
                        if (isMusicImmersive) {
                            // MODO INMERSIVO REAL: Ocupa toda la pantalla de la tablet sin barras del Cockpit
                            MusicCenterScreen(
                                mediaRepository = mediaRepository,
                                initialDestination = musicDestination,
                                onBackToCockpit = { currentRoute = DivineDeckRoute.COCKPIT },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // ZONA 1: Barra Superior Discreta de Estado con Estado GPS Oficial
                                CockpitStatusBar(
                                    operatingModeTitle = operatingMode.title,
                                    onToggleMode = {
                                        val nextMode = if (operatingMode == OperatingMode.AUTOMOTIVE_COCKPIT) {
                                            OperatingMode.PERSONAL_TABLET
                                        } else {
                                            OperatingMode.AUTOMOTIVE_COCKPIT
                                        }
                                        settingsRepository.setOperatingMode(nextMode)
                                    },
                                    onRequestExit = { showExitDialog = true },
                                    isGpsAvailable = speedReading.isReliable,
                                    gpsStatus = gpsStatus,
                                    onGpsStatusClick = requestLocationPermissionOrSettings
                                )

                                // ZONAS 2 Y 3: Área Principal de Contenido (Sin scroll vertical en Cockpit)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    when (currentRoute) {
                                        DivineDeckRoute.COCKPIT -> CockpitScreen(
                                            gpsRepository = gpsRepository,
                                            chevyRenderer = chevy3DRenderer,
                                            mediaRepository = mediaRepository,
                                            navRepository = navRepository,
                                            widgetManager = widgetManager,
                                            onOpenMusicPlayer = {
                                                musicDestination = MusicCenterDestination.PLAYER
                                                currentRoute = DivineDeckRoute.MUSIC
                                            },
                                            onNavigate = { destination ->
                                                if (destination == DivineDeckRoute.MUSIC) {
                                                    musicDestination = MusicCenterDestination.DEFAULT
                                                }
                                                currentRoute = destination
                                            },
                                            onGpsStatusClick = requestLocationPermissionOrSettings,
                                            layout = cockpitLayout,
                                            currentRoute = currentRoute,
                                            lowriderEnabled = isLowriderEnabled
                                        )
                                        DivineDeckRoute.MUSIC -> {
                                            // Conmutado por AnimatedContent superior en modo inmersivo
                                        }
                                        DivineDeckRoute.NAVIGATION -> NavCenterScreen(
                                            navRepository = navRepository
                                        )
                                        DivineDeckRoute.GARAGE_3D -> Garage3DScreen(
                                            chevyRenderer = chevy3DRenderer,
                                            lowriderEnabled = isLowriderEnabled
                                        )
                                        DivineDeckRoute.WIDGETS -> WidgetCenterScreen(
                                            widgetManager = widgetManager
                                        )
                                        DivineDeckRoute.APPS -> AppCenterScreen(
                                            appRepository = appRepository
                                        )
                                        DivineDeckRoute.SETTINGS -> SettingsScreen(
                                            settingsRepository = settingsRepository
                                        )
                                    }
                                }

                                // ZONA 4: Dock Horizontal Inferior Minimalista (Sólo iconos).
                                // En Distribución Panorámica el dock es lateral (dentro del
                                // Cockpit, a la derecha del modelo 3D), así que aquí no se
                                // dibuja el inferior para no duplicarlo.
                                if (!(currentRoute == DivineDeckRoute.COCKPIT && cockpitLayout == CockpitLayout.PANORAMICA)) {
                                    AutomotiveBottomDock(
                                        currentRoute = currentRoute,
                                        onNavigate = { destination ->
                                            if (destination == DivineDeckRoute.MUSIC) {
                                                musicDestination = MusicCenterDestination.DEFAULT
                                            }
                                            currentRoute = destination
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Diálogo explicativo de permisos de ubicación
                    if (showGpsRationaleDialog) {
                        GpsPermissionRationaleDialog(
                            onDismissRequest = {
                                showGpsRationaleDialog = false
                                gpsRepository.onPermissionStatusChanged(false)
                            },
                            onConfirmGrant = {
                                showGpsRationaleDialog = false
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        )
                    }

                    // Diálogo de salida con doble confirmación hacia Samsung One UI Home
                    if (showExitDialog) {
                        ExitConfirmationDialog(
                            onDismissRequest = { showExitDialog = false },
                            onConfirmExit = {
                                showExitDialog = false
                                launcherExitManager.exitToSystemDesktop()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigateHomeIntent(intent)
    }

    private fun handleNavigateHomeIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(
                ReturnHomeFloatingService.EXTRA_NAVIGATE_HOME,
                false
            ) == true
        ) {
            navigateHomeSignal.value = System.currentTimeMillis()
        }
    }

    override fun onResume() {
        super.onResume()
        // El botón flotante nunca debe quedarse vivo si DivineDeck ya está
        // al frente (vuelta por Recientes, atrás o toque del botón): solo
        // se detiene si realmente está corriendo.
        if (ReturnHomeFloatingService.isRunning) {
            ReturnHomeFloatingService.stop(this)
        }
    }

    override fun onStart() {
        super.onStart()
        widgetManager.startListening()
    }

    override fun onStop() {
        widgetManager.stopListening()
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgetManager.handleActivityResult(this, requestCode, resultCode, data)
    }

    override fun onDestroy() {
        mediaRepository.disconnect()
        widgetManager.clearHostViews()
        super.onDestroy()
    }
}
