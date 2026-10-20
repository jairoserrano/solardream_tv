package com.jairo.solardream.model

import com.jairo.solardream.dtu.RawReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyModelTest {
    private val negativeIsImport = MeterConvention(
        importIsPositive = false,
        dtuPowerWattsPerUnit = 0.1,
        dailyEnergyWhPerUnit = 1.0,
        meterPowerWattsPerUnit = 10.0,
    )

    private fun raw(meter: Int?, dtuPower: Long, daily: Long = 8200L) =
        RawReading("400000000001", 1792463207L, meter, dtuPower, daily)

    @Test
    fun `exportando a la red`() {
        val s = EnergyModel(negativeIsImport).toSnapshot(raw(meter = 129, dtuPower = 24100))
        assertEquals(2410, s.productionW)
        assertEquals(1290, s.gridW)
        assertEquals(1120, s.houseW)
        assertEquals(8200L, s.producedTodayWh)
        assertEquals(1792463207L, s.timestampSec)
        assertTrue(s.hasMeter)
    }

    @Test
    fun `de noche importando de la red`() {
        val s = EnergyModel(negativeIsImport).toSnapshot(raw(meter = -15, dtuPower = 0))
        assertEquals(0, s.productionW)
        assertEquals(-150, s.gridW)
        assertEquals(150, s.houseW)
    }

    @Test
    fun `lectura real nocturna del medidor`() {
        val s = EnergyModel(negativeIsImport).toSnapshot(raw(meter = -155, dtuPower = 0))
        assertEquals(-1550, s.gridW)
        assertEquals(1550, s.houseW)
    }

    @Test
    fun `aplica la escala de potencia del medidor`() {
        val conv = negativeIsImport.copy(meterPowerWattsPerUnit = 1.0)
        val s = EnergyModel(conv).toSnapshot(raw(meter = -155, dtuPower = 0))
        assertEquals(-155, s.gridW)
        assertEquals(155, s.houseW)
    }

    @Test
    fun `convencion invertida cambia el signo de la red`() {
        val conv = negativeIsImport.copy(importIsPositive = true)
        val s = EnergyModel(conv).toSnapshot(raw(meter = 14, dtuPower = 0))
        assertEquals(-140, s.gridW)
        assertEquals(140, s.houseW)
    }

    @Test
    fun `sin medidor no hay casa ni red`() {
        val s = EnergyModel(negativeIsImport).toSnapshot(raw(meter = null, dtuPower = 24100))
        assertFalse(s.hasMeter)
        assertNull(s.houseW)
        assertEquals(0, s.gridW)
        assertEquals(2410, s.productionW)
    }

    @Test
    fun `la casa nunca es negativa`() {
        val s = EnergyModel(negativeIsImport).toSnapshot(raw(meter = 50, dtuPower = 1000))
        assertEquals(100, s.productionW)
        assertEquals(0, s.houseW)
    }

    @Test
    fun `aplica las escalas de la convencion`() {
        val conv = MeterConvention(false, dtuPowerWattsPerUnit = 1.0, dailyEnergyWhPerUnit = 10.0, meterPowerWattsPerUnit = 10.0)
        val s = EnergyModel(conv).toSnapshot(raw(meter = 0, dtuPower = 2410, daily = 820))
        assertEquals(2410, s.productionW)
        assertEquals(8200L, s.producedTodayWh)
    }
}
