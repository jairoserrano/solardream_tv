package dev.jairoserrano.solardream.driver

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Cliente Modbus TCP nativo ultraligero sin dependencias externas.
 * Soporta Function Code 0x03 (Read Holding Registers) y 0x04 (Read Input Registers).
 */
class ModbusTcpClient(private val timeoutMs: Int = 3000) {

    class ModbusException(val code: Int) : Exception("Modbus Exception code: 0x${code.toString(16)}")

    /**
     * Lee registros (Function 0x03 o 0x04) y devuelve una lista de enteros de 16 bits sin signo (0..65535).
     */
    fun readRegisters(
        host: String,
        port: Int = 502,
        unitId: Int = 1,
        functionCode: Int = 0x03,
        startAddress: Int,
        quantity: Int,
    ): IntArray {
        require(quantity in 1..125) { "Quantity must be between 1 and 125, got $quantity" }

        val socket = Socket()
        socket.soTimeout = timeoutMs
        socket.connect(InetSocketAddress(host, port), timeoutMs)

        socket.use { s ->
            val out = DataOutputStream(s.getOutputStream())
            val inStream = DataInputStream(s.getInputStream())

            val transactionId = (System.currentTimeMillis() and 0xFFFF).toInt()

            // MBAP Header (7 bytes) + PDU (5 bytes) = 12 bytes
            val request = ByteArray(12)
            request[0] = (transactionId ushr 8).toByte()
            request[1] = (transactionId and 0xFF).toByte()
            request[2] = 0 // Protocol ID MSB (0 = Modbus)
            request[3] = 0 // Protocol ID LSB
            request[4] = 0 // Length MSB
            request[5] = 6 // Length LSB (Unit ID + Function + Start(2) + Count(2))
            request[6] = (unitId and 0xFF).toByte()
            request[7] = (functionCode and 0xFF).toByte()
            request[8] = (startAddress ushr 8).toByte()
            request[9] = (startAddress and 0xFF).toByte()
            request[10] = (quantity ushr 8).toByte()
            request[11] = (quantity and 0xFF).toByte()

            out.write(request)
            out.flush()

            // Leer respuesta MBAP (7 bytes)
            val header = ByteArray(7)
            inStream.readFully(header)

            val respTxId = ((header[0].toInt() and 0xFF) shl 8) or (header[1].toInt() and 0xFF)
            val respProto = ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
            val respLen = ((header[4].toInt() and 0xFF) shl 8) or (header[5].toInt() and 0xFF)
            val respUnit = header[6].toInt() and 0xFF

            if (respProto != 0 || respLen < 2) {
                throw IllegalStateException("Invalid Modbus TCP header: proto=$respProto, len=$respLen")
            }

            val respFc = inStream.readUnsignedByte()
            if (respFc >= 0x80) {
                val exCode = inStream.readUnsignedByte()
                throw ModbusException(exCode)
            }

            if (respFc != functionCode) {
                throw IllegalStateException("Unexpected function code: requested $functionCode, got $respFc")
            }

            val byteCount = inStream.readUnsignedByte()
            if (byteCount != quantity * 2) {
                throw IllegalStateException("Byte count mismatch: expected ${quantity * 2}, got $byteCount")
            }

            val rawData = ByteArray(byteCount)
            inStream.readFully(rawData)

            val registers = IntArray(quantity)
            for (i in 0 until quantity) {
                val msb = rawData[i * 2].toInt() and 0xFF
                val lsb = rawData[i * 2 + 1].toInt() and 0xFF
                registers[i] = (msb shl 8) or lsb
            }
            return registers
        }
    }

    /**
     * Comprueba si el host y puerto responden a Modbus.
     */
    fun ping(host: String, port: Int = 502): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 1200)
            socket.close()
            true
        } catch (_: Exception) {
            false
        }
    }
}
