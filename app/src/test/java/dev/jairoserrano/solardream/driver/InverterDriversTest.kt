package dev.jairoserrano.solardream.driver

import dev.jairoserrano.solardream.model.EnergySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InverterDriversTest {

    @Test
    fun `solarman v5 frame generation has valid structure and checksum`() {
        val frame = SolarmanV5Client.buildFrame(
            loggerSerial = 123456789L,
            unitId = 1,
            startAddress = 60,
            quantity = 10,
        )

        // Verificaciones de estructura
        assertEquals(0xA5.toByte(), frame[0]) // Start byte
        assertEquals(0x15.toByte(), frame[frame.size - 1]) // Tail byte
        assertEquals(0x45.toByte(), frame[3]) // Control code
        assertEquals(0x10.toByte(), frame[4])

        // Verificacion de checksum
        var sum = 0
        for (i in 1 until frame.size - 2) {
            sum += (frame[i].toInt() and 0xFF)
        }
        val expectedChecksum = (sum and 0xFF).toByte()
        assertEquals(expectedChecksum, frame[frame.size - 2])
    }

    @Test
    fun `solarman v5 parse modbus response extracts registers`() {
        // Simular respuesta Solarman con 3 registros Modbus en offset 25
        val response = ByteArray(40)
        response[0] = 0xA5.toByte()
        response[39] = 0x15.toByte()

        val offset = 25
        response[offset] = 0x01 // Unit ID
        response[offset + 1] = 0x03 // FC
        response[offset + 2] = 0x06 // Byte count (3 registers * 2)

        // Reg 0: 1000 (0x03E8)
        response[offset + 3] = 0x03
        response[offset + 4] = 0xE8.toByte()

        // Reg 1: 500 (0x01F4)
        response[offset + 5] = 0x01
        response[offset + 6] = 0xF4.toByte()

        // Reg 2: 250 (0x00FA)
        response[offset + 7] = 0x00
        response[offset + 8] = 0xFA.toByte()

        val registers = SolarmanV5Client.parseModbusFromSolarman(response, response.size, 3)
        assertEquals(3, registers.size)
        assertEquals(1000, registers[0])
        assertEquals(500, registers[1])
        assertEquals(250, registers[2])
    }

    @Test
    fun `autodetector falls back gracefully when offline`() {
        val autodetector = InverterAutodetector()
        // IP no enrutable
        val driver = autodetector.detect("192.0.2.1")
        assertNotNull(driver)
        assertEquals("HOYMILES", driver.id)
    }

    @Test
    fun `autodetector returns driver by id`() {
        val autodetector = InverterAutodetector()
        assertEquals("SOLAX", autodetector.getDriverById("SOLAX").id)
        assertEquals("DEYE", autodetector.getDriverById("DEYE").id)
        assertEquals("SUNSPEC", autodetector.getDriverById("SUNSPEC").id)
        assertEquals("HOYMILES", autodetector.getDriverById("HOYMILES").id)
        assertEquals("HOYMILES", autodetector.getDriverById("UNKNOWN").id)
    }

    @Test
    fun `subnet scanner extracts correct prefix`() {
        val scanner = SubnetScanner()
        val prefix = scanner.getLocalSubnetPrefix("192.168.1.100")
        assertTrue(prefix.endsWith("."))
    }
}
