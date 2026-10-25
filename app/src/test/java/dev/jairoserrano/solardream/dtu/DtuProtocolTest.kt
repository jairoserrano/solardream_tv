package dev.jairoserrano.solardream.dtu

import dev.jairoserrano.solardream.Fixtures
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DtuProtocolTest {

    @Test
    fun `la peticion es identica a la que genera la libreria oficial`() {
        val built = DtuProtocol.buildRealDataRequest(
            seq = 1,
            timeText = "2026-10-19 21:30:00",
            epochSec = 1792463400L,
            page = 2,
        )
        assertArrayEquals(Fixtures.load("golden_request_cp2.bin"), built)
    }

    @Test
    fun `parsea todos los campos de la respuesta`() {
        val page = DtuProtocol.parseRealDataResponse(Fixtures.load("golden_response_p1of4.bin"))
        assertEquals("400000000001", page.deviceSerial)
        assertEquals(1792463207L, page.timestamp)
        assertEquals(4, page.totalPages)
        assertEquals(1, page.page)
        assertEquals(-143, page.meterTotalPower)
        assertEquals(24100L, page.dtuPower)
        assertEquals(8200L, page.dtuDailyEnergy)
    }

    @Test
    fun `una pagina sin medidor devuelve potencia de medidor nula`() {
        val page = DtuProtocol.parseRealDataResponse(Fixtures.load("golden_page0_of2.bin"))
        assertNull(page.meterTotalPower)
        assertEquals(24100L, page.dtuPower)
    }

    @Test
    fun `un CRC alterado se rechaza`() {
        val frame = Fixtures.load("golden_single.bin")
        frame[frame.size - 1] = (frame[frame.size - 1].toInt() xor 0x01).toByte()
        assertThrows(DtuProtocolException::class.java) { DtuProtocol.parseRealDataResponse(frame) }
    }

    @Test
    fun `una trama truncada se rechaza`() {
        val frame = Fixtures.load("golden_single.bin").copyOf(40)
        assertThrows(DtuProtocolException::class.java) { DtuProtocol.parseRealDataResponse(frame) }
    }

    @Test
    fun `una trama sin cabecera HM se rechaza`() {
        val frame = Fixtures.load("golden_single.bin")
        frame[0] = 'X'.code.toByte()
        assertThrows(DtuProtocolException::class.java) { DtuProtocol.parseRealDataResponse(frame) }
    }

    @Test
    fun `combinar paginas toma el medidor de la pagina que lo trae`() {
        val p0 = DtuProtocol.parseRealDataResponse(Fixtures.load("golden_page0_of2.bin"))
        val p1 = DtuProtocol.parseRealDataResponse(Fixtures.load("golden_page1_of2.bin"))
        val merged = DtuProtocol.merge(listOf(p0, p1))
        assertEquals(-143, merged.meterTotalPower)
        assertEquals(24100L, merged.dtuPower)
        assertEquals(8200L, merged.dtuDailyEnergy)
        assertEquals("400000000001", merged.deviceSerial)
    }

    @Test
    fun `combinar sin ninguna pagina con medidor deja el medidor nulo`() {
        val p0 = DtuProtocol.parseRealDataResponse(Fixtures.load("golden_page0_of2.bin"))
        assertNull(DtuProtocol.merge(listOf(p0)).meterTotalPower)
    }
}

