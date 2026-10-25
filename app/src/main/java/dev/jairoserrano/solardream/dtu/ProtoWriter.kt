package dev.jairoserrano.solardream.dtu

import java.io.ByteArrayOutputStream

/** Escritor mínimo de protobuf. Omite los valores cero, como hace proto3. */
internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long) {
        if (value == 0L) return
        tag(field, 0)
        rawVarint(value)
    }

    fun bytes(field: Int, value: ByteArray) {
        if (value.isEmpty()) return
        tag(field, 2)
        rawVarint(value.size.toLong())
        out.write(value)
    }

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wireType: Int) = rawVarint(((field shl 3) or wireType).toLong())

    private fun rawVarint(value: Long) {
        var x = value
        while (true) {
            if ((x and 0x7FL.inv()) == 0L) {
                out.write(x.toInt())
                return
            }
            out.write(((x and 0x7F) or 0x80).toInt())
            x = x ushr 7
        }
    }
}

