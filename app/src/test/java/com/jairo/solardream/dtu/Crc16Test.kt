package com.jairo.solardream.dtu

import org.junit.Assert.assertEquals
import org.junit.Test

class Crc16Test {
    @Test
    fun `vector estandar CRC-16 MODBUS`() {
        assertEquals(0x4B37, Crc16.compute("123456789".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `respeta offset y longitud`() {
        val data = "xx123456789yy".toByteArray(Charsets.US_ASCII)
        assertEquals(0x4B37, Crc16.compute(data, offset = 2, length = 9))
    }

    @Test
    fun `entrada vacia devuelve el valor inicial`() {
        assertEquals(0xFFFF, Crc16.compute(ByteArray(0)))
    }
}
