package dev.jairoserrano.solardream.dtu

/** Lector mínimo de protobuf: solo lo necesario para las respuestas del DTU. */
internal class ProtoReader(
    private val buf: ByteArray,
    private var pos: Int = 0,
    private val end: Int = buf.size,
) {
    fun hasMore(): Boolean = pos < end

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (pos >= end) throw DtuProtocolException("varint truncado")
            if (shift >= 64) throw DtuProtocolException("varint demasiado largo")
            val b = buf[pos++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) return result
            shift += 7
        }
    }

    fun readBytes(): ByteArray {
        val len = readLength()
        val out = buf.copyOfRange(pos, pos + len)
        pos += len
        return out
    }

    fun readMessage(): ProtoReader {
        val len = readLength()
        val sub = ProtoReader(buf, pos, pos + len)
        pos += len
        return sub
    }

    fun skip(wireType: Int) {
        when (wireType) {
            0 -> readVarint()
            1 -> advance(8)
            2 -> advance(readLength())
            5 -> advance(4)
            else -> throw DtuProtocolException("tipo de cable no soportado: $wireType")
        }
    }

    private fun readLength(): Int {
        val len = readVarint()
        if (len < 0 || len > end - pos) throw DtuProtocolException("longitud fuera de rango: $len")
        return len.toInt()
    }

    private fun advance(n: Int) {
        if (n > end - pos) throw DtuProtocolException("campo fuera de rango")
        pos += n
    }
}

