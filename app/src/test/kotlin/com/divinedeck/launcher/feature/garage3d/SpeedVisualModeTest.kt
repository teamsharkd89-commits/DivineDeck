package com.divinedeck.launcher.feature.garage3d

import com.divinedeck.launcher.feature.garage3d.domain.model.SpeedVisualMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedVisualModeTest {

    @Test
    fun `normal mode below 60 kmh stays normal`() {
        val mode = SpeedVisualMode.fromSpeed(45f, SpeedVisualMode.NORMAL)
        assertEquals(SpeedVisualMode.NORMAL, mode)
    }

    @Test
    fun `speed at 60 kmh transitions to corredor`() {
        val mode = SpeedVisualMode.fromSpeed(60f, SpeedVisualMode.NORMAL)
        assertEquals(SpeedVisualMode.CORREDOR, mode)
    }

    @Test
    fun `corredor mode with hysteresis does not drop at 59 kmh`() {
        // Hysteresis is 2.0 km/h, so threshold to drop back to NORMAL is < 58.0 km/h
        val mode = SpeedVisualMode.fromSpeed(59f, SpeedVisualMode.CORREDOR, hysteresis = 2.0f)
        assertEquals(SpeedVisualMode.CORREDOR, mode)
    }

    @Test
    fun `corredor mode drops to normal when speed falls below 58 kmh`() {
        val mode = SpeedVisualMode.fromSpeed(57.5f, SpeedVisualMode.CORREDOR, hysteresis = 2.0f)
        assertEquals(SpeedVisualMode.NORMAL, mode)
    }

    @Test
    fun `speed at 80 kmh transitions from corredor to adrenalina`() {
        val mode = SpeedVisualMode.fromSpeed(80f, SpeedVisualMode.CORREDOR)
        assertEquals(SpeedVisualMode.ADRENALINA, mode)
    }

    @Test
    fun `adrenalina mode with hysteresis does not drop at 79 kmh`() {
        val mode = SpeedVisualMode.fromSpeed(79f, SpeedVisualMode.ADRENALINA, hysteresis = 2.0f)
        assertEquals(SpeedVisualMode.ADRENALINA, mode)
    }

    @Test
    fun `speed at 100 kmh transitions to cohete`() {
        val mode = SpeedVisualMode.fromSpeed(100f, SpeedVisualMode.ADRENALINA)
        assertEquals(SpeedVisualMode.COHETE, mode)
    }

    @Test
    fun `cohete mode with hysteresis does not drop at 99 kmh`() {
        val mode = SpeedVisualMode.fromSpeed(99f, SpeedVisualMode.COHETE, hysteresis = 2.0f)
        assertEquals(SpeedVisualMode.COHETE, mode)
    }

    @Test
    fun `cohete mode drops to adrenalina when speed falls below 98 kmh`() {
        val mode = SpeedVisualMode.fromSpeed(97.5f, SpeedVisualMode.COHETE, hysteresis = 2.0f)
        assertEquals(SpeedVisualMode.ADRENALINA, mode)
    }
}
