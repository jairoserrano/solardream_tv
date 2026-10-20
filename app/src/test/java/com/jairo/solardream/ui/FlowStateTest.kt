package com.jairo.solardream.ui

import com.jairo.solardream.model.ConnectionTracker
import com.jairo.solardream.model.EnergySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowStateTest {
    private fun connected(snapshot: EnergySnapshot) = ConnectionTracker.State(true, snapshot, 1000L)
    private val time: (Long) -> String = { "21:30" }

    @Test
    fun `exportando`() {
        val f = FlowState.from(connected(EnergySnapshot(2410, 1290, 1120, 8200L, 0L, true)), time)
        assertEquals("2,41 kW", f.panelsText)
        assertEquals("1,12 kW", f.houseText)
        assertEquals("1,29 kW", f.gridText)
        assertEquals("Red (exporta)", f.gridLabel)
        assertEquals(Arrow.FORWARD, f.panelsToHouse)
        assertEquals(Arrow.FORWARD, f.houseGrid)
        assertEquals(GridMode.EXPORT, f.gridMode)
        assertFalse(f.dimmed)
        assertEquals("Hoy: 8,2 kWh producidos", f.footer)
        assertEquals("21:30", f.corner)
    }

    @Test
    fun `de noche importando`() {
        val f = FlowState.from(connected(EnergySnapshot(0, -143, 143, 0L, 0L, true)), time)
        assertEquals(Arrow.OFF, f.panelsToHouse)
        assertEquals(Arrow.BACKWARD, f.houseGrid)
        assertEquals(GridMode.IMPORT, f.gridMode)
        assertEquals("Red (importa)", f.gridLabel)
        assertEquals("0,14 kW", f.gridText)
        assertEquals("0,14 kW", f.houseText)
    }

    @Test
    fun `flujo minimo se considera en reposo`() {
        val f = FlowState.from(connected(EnergySnapshot(2, 3, 0, 0L, 0L, true)), time)
        assertEquals(Arrow.OFF, f.panelsToHouse)
        assertEquals(Arrow.OFF, f.houseGrid)
        assertEquals(GridMode.IDLE, f.gridMode)
    }

    @Test
    fun `sin medidor casa y red son n-d`() {
        val f = FlowState.from(connected(EnergySnapshot(2410, 0, null, 8200L, 0L, false)), time)
        assertEquals("n/d", f.houseText)
        assertEquals("n/d", f.gridText)
        assertEquals(GridMode.UNKNOWN, f.gridMode)
        assertEquals(Arrow.OFF, f.houseGrid)
        assertEquals(Arrow.FORWARD, f.panelsToHouse)
    }

    @Test
    fun `desconectado atenua y muestra la hora de la ultima lectura`() {
        val state = ConnectionTracker.State(false, EnergySnapshot(2410, 1290, 1120, 8200L, 0L, true), 1000L)
        val f = FlowState.from(state, time)
        assertTrue(f.dimmed)
        assertEquals(Arrow.OFF, f.panelsToHouse)
        assertEquals(Arrow.OFF, f.houseGrid)
        assertEquals("Sin conexión · última lectura a las 21:30", f.footer)
    }

    @Test
    fun `antes de la primera lectura dice conectando`() {
        val f = FlowState.from(ConnectionTracker.State(false, null, null), time)
        assertTrue(f.dimmed)
        assertEquals("Conectando…", f.footer)
        assertEquals("—", f.panelsText)
    }
}
