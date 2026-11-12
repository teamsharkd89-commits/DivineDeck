package com.music.bitchord.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite de pruebas unitarias para la lógica y especificación de gestos de SwipeToQueueBox
 * (FASE 06.9.1 — Requisito R1 / Milestone M1):
 * 1. Single Execution Protection: Exactamente una ejecución por gesto frente a múltiples eventos de arrastre o callbacks repetidos.
 * 2. Guaranteed Elastic Spring Return: Retorno garantizado al reposo (offset = 0f) tanto al soltar como al cancelar.
 * 3. Rapid Fling vs Slow Drag: Detección de fling rápido por velocidad (> 1000 dp/s) vs arrastre sostenido (> 45%).
 * 4. Immediate Re-Swipe: Capacidad de volver a encolar la misma canción inmediatamente en un gesto posterior.
 * 5. Aborted / Sub-threshold Gestures: Cero ejecuciones si se cancela o si se suelta antes del umbral o si se retrotrae el arrastre.
 * 6. Haptic Feedback State Machine: Armado al 45% e histéresis al 80% del umbral (36%).
 * 7. Threshold Calculations: Cálculos exactos para resoluciones objetivo (Samsung Galaxy Tab A11 SM-X133 1340x800).
 * 8. Unidirectional Clamping: Bloqueo de arrastre hacia la izquierda (offset >= 0f).
 */
class SwipeToQueueBoxLogicTest {

    // =========================================================================
    // 1. Single Execution Protection (M1)
    // =========================================================================

    @Test
    fun singleExecution_triggersExactlyOncePerGesture_evenWithRepeatedDragUpdates() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }
        // Umbral = 450f

        state.onDragStart()
        // Simular arrastre progresivo superando el umbral y continuando el arrastre ("arrastre continuo")
        state.onDrag(300f)
        state.onDrag(160f) // total raw = 460f (> 450f)
        state.onDrag(20f)
        state.onDrag(30f)
        state.onDrag(50f)

        // Al soltar el dedo, debe dispararse exactamente una ejecución
        val triggered = state.onRelease(velocity = 0f)
        assertTrue("onRelease debe reportar disparo", triggered)
        assertEquals("Debe disparar exactamente una sola ejecución", 1, enqueueCount)
        assertEquals(1, state.enqueueCount)
    }

    @Test
    fun singleExecution_protectedAgainstRepeatedReleaseCallsInSameGesture() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }

        state.onDragStart()
        state.onDrag(500f) // Supera umbral de 450f

        // Primer evento release (touch up)
        val firstRelease = state.onRelease(velocity = 0f)
        assertTrue("Primer release debe procesar la acción", firstRelease)
        assertEquals(1, enqueueCount)

        // Supuestos eventos repetidos de release por recomposición concurrente o multi-pointer
        val secondRelease = state.onRelease(velocity = 0f)
        assertFalse("Segundo release en el mismo gesto debe ser bloqueado por atomic flag", secondRelease)
        assertEquals("No debe haber ejecuciones adicionales en el mismo gesto", 1, enqueueCount)

        val thirdRelease = state.onRelease(velocity = 0f)
        assertFalse("Tercer release debe ser bloqueado", thirdRelease)
        assertEquals("Permanece en exactamente 1 ejecución", 1, enqueueCount)
    }

    // =========================================================================
    // 2. Guaranteed Elastic Spring Return to Zero Offset (M1)
    // =========================================================================

    @Test
    fun springReturn_guaranteesZeroOffset_onReleaseAndOnCancel() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }

        // Caso A: Spring return tras soltar superando el umbral
        state.onDragStart()
        state.onDrag(500f)
        assertTrue(state.currentOffset > 0f)

        state.onRelease(velocity = 0f)
        assertEquals(1, enqueueCount)

        // Simulación del bloque try/finally de la animación spring
        val restingOffsetRelease = state.simulateSpringReturn()
        assertEquals(0f, restingOffsetRelease, 0.0001f)
        assertEquals("El offset actual debe quedar en exactamente 0f tras el spring", 0f, state.currentOffset, 0.0001f)
        assertFalse("Flag de ejecución debe restablecerse tras el reposo", state.hasTriggeredInCurrentGesture.get())
        assertFalse("Estado armado debe resetearse", state.isArmed)

        // Caso B: Spring return tras cancelación del gesto
        state.onDragStart()
        state.onDrag(480f)
        assertTrue(state.currentOffset > 0f)

        state.onCancel()
        val restingOffsetCancel = state.simulateSpringReturn()
        assertEquals(0f, restingOffsetCancel, 0.0001f)
        assertEquals(0f, state.currentOffset, 0.0001f)
        assertEquals("Cancelación no debe haber incrementado el conteo de encolado", 1, enqueueCount)
    }

    // =========================================================================
    // 3. Immediate Re-Swipe Support (M1)
    // =========================================================================

    @Test
    fun consecutiveGestures_allowReEnqueueingSameSong_afterPreviousGestureCompletes() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }

        // Gesto 1: Arrastre > 45% -> Release -> Spring return completo
        state.onDragStart()
        state.onDrag(500f)
        state.onRelease(velocity = 0f)
        state.simulateSpringReturn()
        assertEquals("Primer gesto debe encolar 1 vez", 1, enqueueCount)

        // Gesto 2: Inmediatamente sobre la misma canción en un segundo gesto
        state.onDragStart()
        state.onDrag(520f)
        val secondTriggered = state.onRelease(velocity = 0f)
        state.simulateSpringReturn()
        assertTrue("Segundo gesto debe ser permitido inmediatamente", secondTriggered)
        assertEquals("Total debe ser exactamente 2 ejecuciones", 2, enqueueCount)

        // Gesto 3: Tercer gesto consecutivo
        state.onDragStart()
        state.onDrag(490f)
        state.onRelease(velocity = 0f)
        state.simulateSpringReturn()
        assertEquals("Total acumulado debe ser exactamente 3", 3, enqueueCount)
    }

    // =========================================================================
    // 4. Rapid Fling vs Slow Drag (M1)
    // =========================================================================

    @Test
    fun rapidFling_withHighVelocity_triggersEnqueueEvenIfOffsetBelowThreshold() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }
        // Umbral normal = 450f (45%). Min fling offset = 150f (15%). Fling threshold = 1000 dp/s

        state.onDragStart()
        // Desplazamiento rápido pero corto: 250f (25% < 45%) con velocidad alta 1400 dp/s
        state.onDrag(250f)
        assertFalse("No debe estar armado por distancia aún", state.isArmed)

        val triggered = state.onRelease(velocity = 1400f)
        assertTrue("Fling rápido con suficiente desplazamiento debe activar encolado", triggered)
        assertEquals(1, enqueueCount)

        state.simulateSpringReturn()
        assertEquals(0f, state.currentOffset, 0.0001f)
    }

    @Test
    fun rapidFling_withInsufficientOffset_doesNotTriggerEnqueue() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }
        // Min fling offset = 150f (15%)

        state.onDragStart()
        // Toque casi accidental con alta velocidad: desplazamiento de apenas 80f (8% < 15%)
        state.onDrag(80f)

        val triggered = state.onRelease(velocity = 1800f)
        assertFalse("Toque accidental con desplazamiento menor al mínimo no debe encolar", triggered)
        assertEquals(0, enqueueCount)

        state.simulateSpringReturn()
        assertEquals(0f, state.currentOffset, 0.0001f)
    }

    @Test
    fun slowDrag_belowThreshold_doesNotTriggerEnqueue() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }

        state.onDragStart()
        // Arrastre lento a 300f (30% < 45%) con baja velocidad
        state.onDrag(300f)
        assertFalse(state.isArmed)

        val triggered = state.onRelease(velocity = 150f)
        assertFalse("Arrastre insuficiente sin velocidad no debe encolar", triggered)
        assertEquals(0, enqueueCount)

        state.simulateSpringReturn()
        assertEquals(0f, state.currentOffset, 0.0001f)
    }

    // =========================================================================
    // 5. Aborted Gestures and Cancellation (M1)
    // =========================================================================

    @Test
    fun abortedGesture_draggedPastThresholdThenReturnedBelow_doesNotEnqueue() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }
        // Umbral = 450f, Reset = 360f

        state.onDragStart()
        // 1. Arrastre inicial superando el umbral a 500f
        state.onOffsetUpdate(500f)
        assertTrue("Debe haberse armado al cruzar 450f", state.isArmed)

        // 2. El usuario cambia de opinión y retrocede hacia la izquierda a 250f (< 360f)
        state.onOffsetUpdate(250f)
        assertFalse("Debe desarmarse al caer por debajo del umbral de reset", state.isArmed)

        // 3. Suelta el dedo a 250f sin velocidad
        val triggered = state.onRelease(velocity = 0f)
        assertFalse("Gesto abortado no debe encolar", triggered)
        assertEquals(0, enqueueCount)

        state.simulateSpringReturn()
        assertEquals(0f, state.currentOffset, 0.0001f)
    }

    @Test
    fun cancelledGesture_doesNotEnqueue_andSpringsBackToOrigin() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }

        state.onDragStart()
        state.onDrag(480f) // Supera umbral
        assertTrue(state.isArmed)

        // El gesto es cancelado (e.g. scroll vertical en LazyColumn interrumpe el gesto)
        state.onCancel()
        assertEquals("Cancelación no debe invocar callback", 0, enqueueCount)

        state.simulateSpringReturn()
        assertEquals("El offset debe restaurarse al origen tras cancelación", 0f, state.currentOffset, 0.0001f)
        assertFalse(state.isArmed)
        assertFalse(state.hasTriggeredInCurrentGesture.get())
    }

    // =========================================================================
    // 6. Máquina de Estados Háptica (Armado 45% & Histéresis 36%)
    // =========================================================================

    @Test
    fun hapticFeedback_armsAt45Percent_andRequiresHysteresisToReset() {
        val state = SwipeToQueueGestureState(boxWidth = 1000f) {}
        // Arm = 450f, Reset = 360f

        assertFalse(state.isArmed)
        assertEquals(0, state.tickCount)

        // 1. Arrastre sub-umbral: 300f
        state.onOffsetUpdate(300f)
        assertFalse(state.isArmed)
        assertEquals(0, state.tickCount)

        // 2. Cruza umbral a 455f -> Debe armarse y emitir 1 tick háptico
        state.onOffsetUpdate(455f)
        assertTrue(state.isArmed)
        assertEquals(1, state.tickCount)

        // 3. Pequeño movimiento mientras sigue armado (480f) -> No emite ticks repetidos
        state.onOffsetUpdate(480f)
        assertTrue(state.isArmed)
        assertEquals("No debe re-emitir tick mientras permanezca armado", 1, state.tickCount)

        // 4. Retroceso leve por encima del reset (380f > 360f) -> Sigue armado por histéresis
        state.onOffsetUpdate(380f)
        assertTrue("Debe mantener estado armado para evitar oscilaciones en la frontera", state.isArmed)
        assertEquals(1, state.tickCount)

        // 5. Retroceso por debajo del reset (350f < 360f) -> Se desarma
        state.onOffsetUpdate(350f)
        assertFalse("Debe desarmarse al caer por debajo del 80% del umbral", state.isArmed)
        assertEquals(1, state.tickCount)

        // 6. Nuevo avance superando umbral (460f) -> Emite un segundo tick limpio
        state.onOffsetUpdate(460f)
        assertTrue(state.isArmed)
        assertEquals(2, state.tickCount)
    }

    // =========================================================================
    // 7. Umbral de Activación del 45% en Resoluciones Reales (SM-X133 1340x800)
    // =========================================================================

    @Test
    fun thresholdCalculations_forTargetHardwareResolution_1340x800() {
        // En Samsung Galaxy Tab A11 SM-X133 (1340 × 800 horizontal):
        // 1. Ancho completo horizontal: 1340px -> Umbral 45% = 603.0px
        val fullWidth = 1340f
        val stateFull = SwipeToQueueGestureState(boxWidth = fullWidth) {}
        assertEquals(603.0f, stateFull.threshold, 0.001f)
        assertEquals(603.0f, stateFull.armThreshold, 0.001f)
        assertEquals(482.4f, stateFull.resetThreshold, 0.001f)

        // 2. Panel de lista de canciones (MusicLibrary / Explore con Dock o Split): ~800px -> 360.0px
        val panelWidth = 800f
        val statePanel = SwipeToQueueGestureState(boxWidth = panelWidth) {}
        assertEquals(360.0f, statePanel.threshold, 0.001f)
        assertEquals(360.0f, statePanel.armThreshold, 0.001f)
        assertEquals(288.0f, statePanel.resetThreshold, 0.001f)

        // 3. Fila compacta de búsqueda / diálogo: 400px -> 180.0px
        val compactWidth = 400f
        val stateCompact = SwipeToQueueGestureState(boxWidth = compactWidth) {}
        assertEquals(180.0f, stateCompact.threshold, 0.001f)
        assertEquals(180.0f, stateCompact.armThreshold, 0.001f)
        assertEquals(144.0f, stateCompact.resetThreshold, 0.001f)
    }

    // =========================================================================
    // 8. Clamping Unidireccional & Invariante de Descarte
    // =========================================================================

    @Test
    fun negativeDrag_isClampedToZero_preventingLeftwardMovement() {
        val state = SwipeToQueueGestureState(boxWidth = 1000f) {}
        state.onDragStart()

        // Intento de arrastre hacia la izquierda (valor negativo)
        state.onDrag(-150f)
        assertEquals("Offset no debe volverse negativo", 0f, state.currentOffset, 0.0001f)

        state.onDrag(-50f)
        assertEquals(0f, state.currentOffset, 0.0001f)
    }

    @Test
    fun confirmValueChange_backwardCompatibility_alwaysReturnsFalse() {
        var enqueued = false
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueued = true }

        val resultDismissed = state.confirmValueChange(targetIsDismissed = true, offset = 500f)
        assertFalse("confirmValueChange debe retornar siempre false para evitar descarte", resultDismissed)
        assertTrue("onEnqueue debe haberse invocado con offset >= 450f", enqueued)

        enqueued = false
        val resultNotDismissed = state.confirmValueChange(targetIsDismissed = false, offset = 0f)
        assertFalse("Retorna false", resultNotDismissed)
        assertFalse("No debe encolar si targetIsDismissed es false", enqueued)
    }
}
