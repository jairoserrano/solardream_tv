package com.jairo.solardream.dtu

/**
 * Protocolo local de Hoymiles (puerto 10081).
 * Trama: "HM" + comando(2) + secuencia(2) + CRC16(2) + longitud total(2) + protobuf.
 */
object DtuProtocol {
    private const val HEADER_SIZE = 10
    private const val UTC_OFFSET_SECONDS = 28800L
    private const val CMD_HI = 0xA3
    private const val CMD_LO = 0x11

    data class Page(
        val deviceSerial: String,
        val timestamp: Long,
        val totalPages: Int,
        val page: Int,
        val meterTotalPower: Int?,
        val dtuPower: Long,
        val dtuDailyEnergy: Long,
    )

    fun buildRealDataRequest(seq: Int, timeText: String, epochSec: Long, page: Int): ByteArray {
        val body = ProtoWriter()
        body.bytes(1, timeText.toByteArray(Charsets.US_ASCII))
        body.varint(2, page.toLong())
        body.varint(4, UTC_OFFSET_SECONDS)
        body.varint(5, epochSec)
        val payload = body.toByteArray()

        val out = ByteArray(HEADER_SIZE + payload.size)
        out[0] = 'H'.code.toByte()
        out[1] = 'M'.code.toByte()
        out[2] = CMD_HI.toByte()
        out[3] = CMD_LO.toByte()
        putU16(out, 4, seq and 0xFFFF)
        putU16(out, 6, Crc16.compute(payload))
        putU16(out, 8, payload.size + HEADER_SIZE)
        payload.copyInto(out, HEADER_SIZE)
        return out
    }

    fun parseRealDataResponse(frame: ByteArray): Page {
        if (frame.size < HEADER_SIZE) throw DtuProtocolException("trama demasiado corta: ${frame.size} bytes")
        if (frame[0] != 'H'.code.toByte() || frame[1] != 'M'.code.toByte()) {
            throw DtuProtocolException("cabecera inválida")
        }
        val declared = getU16(frame, 8)
        if (declared != frame.size) {
            throw DtuProtocolException("longitud declarada $declared distinta de la recibida ${frame.size}")
        }
        val expected = getU16(frame, 6)
        val actual = Crc16.compute(frame, HEADER_SIZE, frame.size - HEADER_SIZE)
        if (expected != actual) throw DtuProtocolException("CRC incorrecto")
        return decode(ProtoReader(frame, HEADER_SIZE))
    }

    fun merge(pages: List<Page>): RawReading {
        require(pages.isNotEmpty()) { "se necesita al menos una página" }
        return RawReading(
            deviceSerial = pages.first().deviceSerial,
            timestamp = pages.maxOf { it.timestamp },
            meterTotalPower = pages.firstNotNullOfOrNull { it.meterTotalPower },
            dtuPower = pages.maxOf { it.dtuPower },
            dtuDailyEnergy = pages.maxOf { it.dtuDailyEnergy },
        )
    }

    private fun decode(r: ProtoReader): Page {
        var serial = ""
        var timestamp = 0L
        var totalPages = 0
        var page = 0
        var meter: Int? = null
        var dtuPower = 0L
        var dailyEnergy = 0L
        while (r.hasMore()) {
            val tag = r.readVarint().toInt()
            val field = tag ushr 3
            val wire = tag and 7
            when {
                field == 1 && wire == 2 -> serial = String(r.readBytes(), Charsets.US_ASCII)
                field == 2 && wire == 0 -> timestamp = r.readVarint()
                field == 3 && wire == 0 -> totalPages = r.readVarint().toInt()
                field == 4 && wire == 0 -> page = r.readVarint().toInt()
                field == 6 && wire == 2 -> {
                    val power = decodeMeterPower(r.readMessage())
                    if (meter == null) meter = power
                }
                field == 12 && wire == 0 -> dtuPower = r.readVarint()
                field == 13 && wire == 0 -> dailyEnergy = r.readVarint()
                else -> r.skip(wire)
            }
        }
        return Page(serial, timestamp, totalPages, page, meter, dtuPower, dailyEnergy)
    }

    /** Campo 3 de MeterMO (phase_total_power, int32 con signo). Ausente significa 0. */
    private fun decodeMeterPower(r: ProtoReader): Int {
        var power = 0
        while (r.hasMore()) {
            val tag = r.readVarint().toInt()
            val field = tag ushr 3
            val wire = tag and 7
            if (field == 3 && wire == 0) power = r.readVarint().toInt() else r.skip(wire)
        }
        return power
    }

    private fun putU16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value ushr 8).toByte()
        buf[offset + 1] = value.toByte()
    }

    private fun getU16(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)
}
