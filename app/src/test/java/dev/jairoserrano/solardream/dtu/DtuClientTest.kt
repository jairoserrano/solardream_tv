package dev.jairoserrano.solardream.dtu

import dev.jairoserrano.solardream.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/** DTU falso: atiende una conexión por respuesta, o acepta y calla si [silent]. */
private class FakeDtu(
    private val responses: List<ByteArray>,
    private val silent: Boolean = false,
) : AutoCloseable {
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = server.localPort
    val requestsSeen = CopyOnWriteArrayList<ByteArray>()

    init {
        Thread {
            try {
                if (silent) {
                    server.accept().use { Thread.sleep(3000) }
                } else {
                    for (response in responses) {
                        server.accept().use { socket ->
                            requestsSeen += readRequest(socket.getInputStream())
                            socket.getOutputStream().apply {
                                write(response)
                                flush()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun readRequest(input: InputStream): ByteArray {
        val data = DataInputStream(input)
        val head = ByteArray(10)
        data.readFully(head)
        val total = ((head[8].toInt() and 0xFF) shl 8) or (head[9].toInt() and 0xFF)
        val rest = ByteArray(total - 10)
        data.readFully(rest)
        return head + rest
    }

    override fun close() = server.close()
}

class DtuClientTest {

    @Test
    fun `lee una sola pagina`() {
        FakeDtu(listOf(Fixtures.load("golden_single.bin"))).use { dtu ->
            val client = DtuClient("127.0.0.1", dtu.port, timeoutMs = 2000, pageGapMs = 0)
            val reading = client.readRealData()
            assertEquals(-143, reading.meterTotalPower)
            assertEquals(24100L, reading.dtuPower)
            assertEquals(1, dtu.requestsSeen.size)
            assertEquals(0xA3, dtu.requestsSeen[0][2].toInt() and 0xFF)
            assertEquals(0x11, dtu.requestsSeen[0][3].toInt() and 0xFF)
        }
    }

    @Test
    fun `pide todas las paginas y las combina`() {
        val pages = listOf(Fixtures.load("golden_page0_of2.bin"), Fixtures.load("golden_page1_of2.bin"))
        FakeDtu(pages).use { dtu ->
            val client = DtuClient("127.0.0.1", dtu.port, timeoutMs = 2000, pageGapMs = 0)
            val reading = client.readRealData()
            assertEquals(2, dtu.requestsSeen.size)
            assertEquals(-143, reading.meterTotalPower)
            assertEquals(24100L, reading.dtuPower)
        }
    }

    @Test
    fun `si el DTU no responde lanza IOException dentro del timeout`() {
        FakeDtu(emptyList(), silent = true).use { dtu ->
            val client = DtuClient("127.0.0.1", dtu.port, timeoutMs = 300, pageGapMs = 0)
            assertThrows(IOException::class.java) { client.readRealData() }
        }
    }

    @Test
    fun `una respuesta corrupta lanza DtuProtocolException`() {
        val bad = Fixtures.load("golden_single.bin")
        bad[bad.size - 1] = (bad[bad.size - 1].toInt() xor 0x01).toByte()
        FakeDtu(listOf(bad)).use { dtu ->
            val client = DtuClient("127.0.0.1", dtu.port, timeoutMs = 2000, pageGapMs = 0)
            assertThrows(DtuProtocolException::class.java) { client.readRealData() }
        }
    }
}

