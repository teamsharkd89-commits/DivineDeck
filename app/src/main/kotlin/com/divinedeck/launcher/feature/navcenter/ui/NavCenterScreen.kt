package com.divinedeck.launcher.feature.navcenter.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.divinedeck.launcher.core.designsystem.components.AutomotiveCard
import com.divinedeck.launcher.core.designsystem.components.AutomotivePrimaryButton
import com.divinedeck.launcher.core.designsystem.components.AutomotiveSecondaryButton
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckPalette
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme
import com.divinedeck.launcher.feature.navcenter.domain.NavigationLauncherRepository
import com.divinedeck.launcher.feature.navcenter.service.ReturnHomeFloatingService

/**
 * Centro de Navegación: Google Maps externo (sin mapa embebido).
 *
 * La tarjeta de Google Maps lleva un fondo de mapa estilizado dibujado en
 * Canvas (calles, río, ruta animada y pin pulsante). Al tocarla se abre
 * Google Maps y, si el permiso de superposición está concedido, aparece
 * el botón flotante de DivineDeck sobre Google Maps para volver al inicio
 * con un toque. Sin el permiso, Google Maps se abre igual y se muestra
 * un aviso con el acceso directo a los ajustes del permiso.
 */
@Composable
fun NavCenterScreen(
    navRepository: NavigationLauncherRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = DivineDeckTheme.palette
    val lifecycleOwner = LocalLifecycleOwner.current

    // El permiso de superposición puede concederse fuera de esta pantalla:
    // se re-comprueba cada vez que la pantalla vuelve a primer plano para
    // que el aviso desaparezca solo al concederlo.
    var overlayGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // El servicio flotante se arranca ANTES de lanzar Google Maps: en
    // Android 12+ arrancar un servicio en primer plano una vez la app ya
    // está en segundo plano se rechaza (ForegroundServiceStartNotAllowed).
    val openGoogleMaps: () -> Unit = {
        var floatingAttempted = false
        var floatingOk = false
        if (Settings.canDrawOverlays(context)) {
            floatingAttempted = true
            floatingOk = ReturnHomeFloatingService.start(context)
        }
        navRepository.launchGoogleMaps(context)
        if (floatingAttempted) {
            Toast.makeText(
                context,
                if (floatingOk) "Botón flotante activado: toca la casita para volver"
                else "No se pudo mostrar el botón flotante",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Prueba del botón flotante sin abrir Google Maps: lo muestra sobre
    // la propia DivineDeck para verificar que el overlay se dibuja.
    val testFloatingButton: () -> Unit = {
        if (Settings.canDrawOverlays(context)) {
            val ok = ReturnHomeFloatingService.start(context)
            Toast.makeText(
                context,
                if (ok) "Botón flotante activado: toca la casita para volver"
                else "No se pudo mostrar el botón flotante",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                context,
                "Concede el permiso \"Mostrar sobre otras apps\" para probar el botón",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // LANZADOR PRINCIPAL DE GOOGLE MAPS
        AutomotiveCard(
            modifier = Modifier
                .weight(1.6f)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text(
                        text = "CENTRO DE NAVEGACIÓN",
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Abre Google Maps para navegar con tráfico en vivo. En Google Maps puedes descargar mapas sin conexión de Tepic.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                }

                // Tarjeta grande de Google Maps con fondo de mapa dibujado.
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = palette.elevatedSurface,
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openGoogleMaps() }
                ) {
                    Column {
                        // Fondo de mapa estilizado (calles, río, ruta y pin).
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        ) {
                            MapBackground(palette = palette)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = palette.background.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Navigation,
                                        contentDescription = null,
                                        tint = palette.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Google Maps",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = palette.textPrimary
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Navegación con Google Maps",
                                style = MaterialTheme.typography.titleLarge,
                                color = palette.textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Lanza la aplicación de navegación instalada, con tráfico en vivo y mapas sin conexión descargados desde Google Maps.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            AutomotivePrimaryButton(
                                onClick = { openGoogleMaps() }
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null, tint = palette.onAccent)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Abrir Google Maps", color = palette.onAccent)
                            }
                        }
                    }
                }

                // Prueba del botón flotante (sin abrir Google Maps).
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AutomotiveSecondaryButton(
                        onClick = { testFloatingButton() }
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = palette.accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Probar botón flotante", color = palette.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "(prueba) Muestra el botón sobre DivineDeck sin abrir Google Maps.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                }

                if (overlayGranted) {
                    Text(
                        text = "• Al abrir Google Maps aparece un botón flotante de DivineDeck para volver al inicio con un toque.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.statusPositive
                    )
                } else {
                    // Aviso de permiso de superposición (no bloquea Google Maps).
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.elevatedSurface,
                        border = BorderStroke(1.dp, palette.statusAlert),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Botón flotante desactivado",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Para volver a DivineDeck con un toque desde Google Maps, concede el permiso \"Mostrar sobre otras apps\". Google Maps se abre igual sin él.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            AutomotiveSecondaryButton(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            ) {
                                Text("Conceder permiso")
                            }
                        }
                    }
                }
            }
        }

        // DESTINOS RÁPIDOS AUTOMOTRICES
        AutomotiveCard(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "DESTINOS RÁPIDOS",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.textPrimary
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val destinations = listOf(
                        Triple("Gasolinera Cercana", "Buscar gasolineras en la ruta", Icons.Default.LocalGasStation),
                        Triple("Taller Mecánico", "Servicio automotriz y refaccionarias", Icons.Default.Build),
                        Triple("Casa", "Ruta directa al hogar", Icons.Default.Home),
                        Triple("Trabajo", "Ruta de traslado laboral", Icons.Default.Work)
                    )

                    destinations.forEach { (title, subtitle, icon) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = palette.elevatedSurface,
                            border = BorderStroke(1.dp, palette.borderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navRepository.launchNavigationTo(context, title) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(title, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fondo de mapa estilizado: retícula de calles, una avenida diagonal, un
 * río, una ruta punteada que se desplaza y un pin que pulsa. Todo dibujo
 * en Canvas, sin mapa embebido ni dependencias externas.
 */
@Composable
private fun MapBackground(
    palette: DivineDeckPalette,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "mapAnimation")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Restart
        ),
        label = "pinPulse"
    )
    val routePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 32f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "routePhase"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Base del mapa.
        drawRect(color = palette.surface)
        // Manzanas: retícula de calles.
        val street = palette.border.copy(alpha = 0.55f)
        val xs = listOf(0.18f, 0.42f, 0.66f, 0.86f)
        val ys = listOf(0.28f, 0.55f, 0.80f)
        for (fx in xs) {
            drawLine(street, Offset(w * fx, 0f), Offset(w * fx, h), strokeWidth = 7f)
        }
        for (fy in ys) {
            drawLine(street, Offset(0f, h * fy), Offset(w, h * fy), strokeWidth = 7f)
        }
        // Avenida diagonal principal.
        drawLine(
            palette.border.copy(alpha = 0.9f),
            Offset(0f, h * 0.92f),
            Offset(w, h * 0.12f),
            strokeWidth = 14f
        )
        // Río (curva azul).
        val river = Path().apply {
            moveTo(w * 0.08f, 0f)
            cubicTo(w * 0.30f, h * 0.35f, w * 0.02f, h * 0.55f, w * 0.28f, h)
        }
        drawPath(
            river,
            color = Color(0xFF4A90D9).copy(alpha = 0.75f),
            style = Stroke(width = 16f)
        )
        // Ruta punteada animada (de la esquina inferior izquierda al pin).
        val pin = Offset(w * 0.64f, h * 0.38f)
        val route = Path().apply {
            moveTo(w * 0.10f, h * 0.88f)
            cubicTo(w * 0.30f, h * 0.70f, w * 0.38f, h * 0.62f, pin.x, pin.y)
        }
        drawPath(
            route,
            color = palette.accent,
            style = Stroke(
                width = 6f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), routePhase)
            )
        )
        // Pin pulsante.
        val pulseRadius = 18f + pulse * 34f
        drawCircle(
            color = palette.accent.copy(alpha = (0.45f * (1f - pulse))),
            radius = pulseRadius,
            center = pin
        )
        drawCircle(color = palette.accent, radius = 20f, center = pin)
        drawCircle(color = Color.White, radius = 8f, center = pin)
    }
}
