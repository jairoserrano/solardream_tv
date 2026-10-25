package dev.jairoserrano.solardream.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTrackerTest {
    private val snap = EnergySnapshot(2410, 1290, 1120, 8200L, 1792463207L, true)

    @Test
    fun `al inicio no esta conectado y no hay lectura`() {
        val s = ConnectionTracker().state()
        assertFalse(s.connected)
        assertNull(s.last)
        assertNull(s.lastOkMillis)
    }

    @Test
    fun `una lectura correcta conecta`() {
        val t = ConnectionTracker()
        t.onSuccess(snap, 1000L)
        val s = t.state()
        assertTrue(s.connected)
        assertEquals(snap, s.last)
        assertEquals(1000L, s.lastOkMillis)
    }

    @Test
    fun `dos fallos siguen conectado, el tercero desconecta y conserva la ultima lectura`() {
        val t = ConnectionTracker(maxFailures = 3)
        t.onSuccess(snap, 1000L)
        t.onFailure()
        t.onFailure()
        assertTrue(t.state().connected)
        t.onFailure()
        val s = t.state()
        assertFalse(s.connected)
        assertEquals(snap, s.last)
        assertEquals(1000L, s.lastOkMillis)
    }

    @Test
    fun `un exito reinicia los fallos`() {
        val t = ConnectionTracker(maxFailures = 3)
        t.onSuccess(snap, 1000L)
        t.onFailure()
        t.onFailure()
        t.onSuccess(snap, 2000L)
        t.onFailure()
        t.onFailure()
        assertTrue(t.state().connected)
        assertEquals(2000L, t.state().lastOkMillis)
    }
}

