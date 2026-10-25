package dev.jairoserrano.solardream.dtu

import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cliente bloqueante del DTU. Una conexión por petición, como hace la app oficial.
 * El DTU necesita unos 2 s entre peticiones, por eso [pageGapMs].
 */
class DtuClient(
    private val host: String,
    private val port: Int = 10081,
    private val timeoutMs: Int = 5000,
    private val pageGapMs: Long = 2000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var seq = 0
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @Throws(IOException::class)
    fun readRealData(): RawReading {
        val first = exchange(0)
        val pages = mutableListOf(first)
        for (page in 1 until maxOf(1, first.totalPages)) {
            if (pageGapMs > 0) Thread.sleep(pageGapMs)
            pages += exchange(page)
        }
        return DtuProtocol.merge(pages)
    }

    private fun exchange(page: Int): DtuProtocol.Page {
        seq = (seq + 1) and 0xFFFF
        val nowMs = now()
        val request = DtuProtocol.buildRealDataRequest(
            seq = seq,
            timeText = timeFormat.format(Date(nowMs)),
            epochSec = nowMs / 1000,
            page = page,
        )
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            socket.getOutputStream().apply {
                write(request)
                flush()
            }
            return DtuProtocol.parseRealDataResponse(readFrame(socket.getInputStream()))
        }
    }

    private fun readFrame(input: InputStream): ByteArray {
        val head = readFully(input, HEADER_SIZE)
        val total = ((head[8].toInt() and 0xFF) shl 8) or (head[9].toInt() and 0xFF)
        if (total < HEADER_SIZE || total > MAX_FRAME) {
            throw DtuProtocolException("longitud de trama inválida: $total")
        }
        return head + readFully(input, total - HEADER_SIZE)
    }

    private fun readFully(input: InputStream, n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val read = input.read(out, off, n - off)
            if (read < 0) throw DtuProtocolException("conexión cerrada tras $off de $n bytes")
            off += read
        }
        return out
    }

    private companion object {
        const val HEADER_SIZE = 10
        const val MAX_FRAME = 4096
    }
}

