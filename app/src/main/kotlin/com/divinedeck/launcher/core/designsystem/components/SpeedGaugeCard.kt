package com.divinedeck.launcher.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme
import com.divinedeck.launcher.feature.gps.domain.model.GpsStatus
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Tarjeta de Velocímetro Analógico (Fase 06.11 — layout Panorámica).
 *
 * Replica la tarjeta VELOCIDAD del mockup Panorámico: dial analógico de
 * 240° con aguja, lectura digital en el centro y estado GPS abajo.
 * La aguja se anima suavemente al cambiar la velocidad.
 *
 * La lectura digital vive en una pastilla opaca en el centro del dial,
 * como el display central de un tablero real: la aguja pasa por detrás
 * de la pastilla y nunca se encima con el número ni con el "KM/H".
 *
 * Escala 0–180 km/h. El arco va de 150° a 390° (30°), abierto por abajo.
 * Los últimos 30 km/h (150–180) van en zona roja, como un tacómetro.
 */
@Composable
fun SpeedGaugeCard(
    speedKmh: Float,
    gpsStatus: GpsStatus = GpsStatus.NOT_AVAILABLE,
    onStatusClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = DivineDeckTheme.palette
    val textMeasurer = rememberTextMeasurer()
    val animatedSpeed by animateFloatAsState(
        targetValue = speedKmh.coerceIn(0f, MAX_GAUGE_SPEED_KMH),
        animationSpec = tween(durationMillis = 450),
        label = "gaugeSpeed"
    )
    val speedFraction = (animatedSpeed / MAX_GAUGE_SPEED_KMH).coerceIn(0f, 1f)

    val statusDotColor = when (gpsStatus) {
        GpsStatus.CONNECTED -> palette.statusPositive
        GpsStatus.WEAK_SIGNAL -> palette.accentSecondary
        GpsStatus.SEARCHING -> palette.accent
        GpsStatus.UNRELIABLE_SPEED -> palette.statusAlert
        GpsStatus.NOT_AVAILABLE -> palette.textMuted
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.surface.copy(alpha = 0.92f),
                        palette.background.copy(alpha = 0.95f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = palette.accent.copy(alpha = 0.45f),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "VELOCIDAD",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp
                ),
                color = palette.accent
            )

            // Dial analógico con lectura digital centrada
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = min(size.width, size.height) * 0.42f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val arcTopLeft = Offset(center.x - radius, center.y - radius)
                    val arcSize = Size(radius * 2f, radius * 2f)
                    val strokeWidth = 10.dp.toPx()

                    fun polar(angleDeg: Double, r: Float): Offset {
                        val rad = Math.toRadians(angleDeg)
                        return Offset(
                            center.x + r * cos(rad).toFloat(),
                            center.y + r * sin(rad).toFloat()
                        )
                    }

                    // Resplandor radial suave detrás del dial
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                palette.accent.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius * 1.25f
                        ),
                        radius = radius * 1.25f,
                        center = center
                    )

                    // Arco de fondo (240°) teñido con el acento del tema,
                    // como el dial rojo del mockup Rojo Sport
                    drawArc(
                        color = palette.accent.copy(alpha = 0.22f),
                        startAngle = GAUGE_START_ANGLE,
                        sweepAngle = GAUGE_SWEEP_ANGLE,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Zona roja: últimos 30 km/h (150–180)
                    drawArc(
                        color = palette.statusAlert.copy(alpha = 0.45f),
                        startAngle = GAUGE_START_ANGLE + GAUGE_SWEEP_ANGLE * (REDLINE_START_KMH / MAX_GAUGE_SPEED_KMH),
                        sweepAngle = GAUGE_SWEEP_ANGLE * ((MAX_GAUGE_SPEED_KMH - REDLINE_START_KMH) / MAX_GAUGE_SPEED_KMH),
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Arco de progreso según la velocidad, con halo
                    if (speedFraction > 0f) {
                        drawArc(
                            color = palette.accent.copy(alpha = 0.14f),
                            startAngle = GAUGE_START_ANGLE,
                            sweepAngle = GAUGE_SWEEP_ANGLE * speedFraction,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth * 1.8f, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = palette.accent,
                            startAngle = GAUGE_START_ANGLE,
                            sweepAngle = GAUGE_SWEEP_ANGLE * speedFraction,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // Marcas menores cada 10 km/h
                    for (j in 1..17 step 2) {
                        val angle = GAUGE_START_ANGLE + j * (GAUGE_SWEEP_ANGLE / 18f)
                        drawLine(
                            color = palette.accent.copy(alpha = 0.30f),
                            start = polar(angle.toDouble(), radius * 0.90f),
                            end = polar(angle.toDouble(), radius * 0.97f),
                            strokeWidth = 1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Marcas mayores cada 20 km/h (0–180) en acento del tema
                    for (i in 0..9) {
                        val angle = GAUGE_START_ANGLE + i * (GAUGE_SWEEP_ANGLE / 9f)
                        drawLine(
                            color = palette.accent.copy(alpha = 0.60f),
                            start = polar(angle.toDouble(), radius * 0.86f),
                            end = polar(angle.toDouble(), radius * 0.98f),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Números del dial (0, 60, 120, 180) fuera del arco
                    val labelStyle = TextStyle(
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    for ((value, angle) in DIAL_LABELS) {
                        val pos = polar(angle.toDouble(), radius * 1.10f)
                        val layout = textMeasurer.measure(value.toString(), labelStyle)
                        drawText(
                            textMeasurer = textMeasurer,
                            text = value.toString(),
                            topLeft = Offset(
                                pos.x - layout.size.width / 2f,
                                pos.y - layout.size.height / 2f
                            ),
                            style = labelStyle
                        )
                    }

                    // Aguja con halo: la pastilla central la tapa por dentro,
                    // así nunca se encima con el número
                    val needleAngle = GAUGE_START_ANGLE + GAUGE_SWEEP_ANGLE * speedFraction
                    drawLine(
                        color = palette.accent.copy(alpha = 0.20f),
                        start = center,
                        end = polar(needleAngle.toDouble(), radius * 0.78f),
                        strokeWidth = 8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = palette.accent,
                        start = center,
                        end = polar(needleAngle.toDouble(), radius * 0.78f),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Buje de la aguja (queda detrás de la pastilla central)
                    drawCircle(
                        color = palette.accent,
                        radius = 7.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = palette.background,
                        radius = 4.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = palette.accent,
                        radius = 2.dp.toPx(),
                        center = center
                    )
                }

                // Lectura digital en pastilla opaca: la aguja pasa por detrás
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .background(
                            color = palette.elevatedSurface.copy(alpha = 0.96f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = palette.accent.copy(alpha = 0.30f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "%.0f".format(animatedSpeed.coerceAtLeast(0f)),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 34.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-1).sp
                        ),
                        color = palette.accent
                    )
                    Text(
                        text = "KM/H",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.sp
                        ),
                        color = palette.textSecondary
                    )
                }
            }

            // Estado GPS (igual que en el indicador digital original)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = if (onStatusClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onStatusClick
                    )
                } else {
                    Modifier
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(statusDotColor, CircleShape)
                )
                Text(
                    text = gpsStatus.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.2.sp
                    ),
                    color = palette.accent
                )
            }
        }
    }
}

private const val MAX_GAUGE_SPEED_KMH = 180f
private const val REDLINE_START_KMH = 150f
private const val GAUGE_START_ANGLE = 150f
private const val GAUGE_SWEEP_ANGLE = 240f

/** Valor del dial → ángulo en el arco (0 → 150°, 60 → 230°, 120 → 310°, 180 → 30°). */
private val DIAL_LABELS = listOf(
    0 to 150f,
    60 to 230f,
    120 to 310f,
    180 to 30f
)

@Preview(showBackground = true, widthDp = 420, heightDp = 280)
@Composable
private fun SpeedGaugeCardPreview() {
    SpeedGaugeCard(
        speedKmh = 62f,
        gpsStatus = GpsStatus.SEARCHING
    )
}
