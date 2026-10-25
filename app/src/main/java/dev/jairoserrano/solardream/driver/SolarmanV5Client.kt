package dev.jairoserrano.solardream.driver

import dev.jairoserrano.solardream.dtu.Crc16
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Cliente Solarman V5 nativo sobre TCP (puerto 8899).
 * Utilizado por inversores Deye, SunSynk, Sol-Ark, Sofar con registradores WiFi de Solarman/IGEN.
 */
class SolarmanV5Client(private val timeoutMs: Int = 3000) {

    /**
     * Envía una petición Modbus RTU envuelta en Solarman V5 y devuelve los registros leídos.
     */
    fun readHoldingRegisters(
        host: String,
        port: Int = 8899,
        loggerSerial: Long = 0L,
        unitId: Int = 1,
        startAddress: Int,
        quantity: Int,
    ): IntArray {
        val socket = Socket()
        socket.soTimeout = timeoutMs
        socket.connect(InetSocketAddress(host, port), timeoutMs)

        socket.use { s ->
            val out = DataOutputStream(s.getOutputStream())
            val inStream = DataInputStream(s.getInputStream())

            val requestFrame = buildFrame(loggerSerial, unitId, startAddress, quantity)
            out.write(requestFrame)
            out.flush()

            val buffer = ByteArray(1024)
            val bytesRead = inStream.read(buffer)
            if (bytesRead < 29) {
                throw IllegalStateException("Solarman response too short: $bytesRead bytes")
            }

            if (buffer[0] != 0xA5.toByte() || buffer[bytesRead - 1] != 0x15.toByte()) {
                throw IllegalStateException("Invalid Solarman frame delimiters (start=0x${(buffer[0].toInt() and 0xFF).toString(16)}, end=0x${(buffer[bytesRead - 1].toInt() and 0xFF).toString(16)})")
            }

            return parseModbusFromSolarman(buffer, bytesRead, quantity)
        }
    }

    fun ping(host: String, port: Int = 8899): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 1200)
            socket.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        fun buildFrame(
            loggerSerial: Long,
            unitId: Int,
            startAddress: Int,
            quantity: Int,
        ): ByteArray {
            // Modbus RTU payload: Unit(1) + FC(1) + Start(2) + Count(2) + CRC(2) = 8 bytes
            val modbusPdu = ByteArray(6)
            modbusPdu[0] = (unitId and 0xFF).toByte()
            modbusPdu[1] = 0x03 // Read Holding Registers
            modbusPdu[2] = (startAddress ushr 8).toByte()
            modbusPdu[3] = (startAddress and 0xFF).toByte()
            modbusPdu[4] = (quantity ushr 8).toByte()
            modbusPdu[5] = (quantity and 0xFF).toByte()

            val modbusCrc = Crc16.compute(modbusPdu)
            val modbusRtu = ByteArray(8)
            System.arraycopy(modbusPdu, 0, modbusRtu, 0, 6)
            modbusRtu[6] = (modbusCrc and 0xFF).toByte() // CRC LSB first
            modbusRtu[7] = (modbusCrc ushr 8).toByte()

            // Solarman header (11 bytes) + payload control (14 bytes) + Modbus (8 bytes) + checksum(1) + tail(1)
            // Payload length is from offset 11 to end of modbus (14 + 8 = 22 bytes)
            val payloadLen = 14 + modbusRtu.size
            val frame = ByteArray(1 + 2 + 2 + 2 + 4 + 1 + 2 + 4 + 4 + modbusRtu.size + 1 + 1)

            frame[0] = 0xA5.toByte() // Header start
            frame[1] = (payloadLen and 0xFF).toByte() // Length LSB
            frame[2] = (payloadLen ushr 8).toByte() // Length MSB
            frame[3] = 0x45.toByte() // Control code / protocol
            frame[4] = 0x10.toByte()
            frame[5] = 0x01.toByte() // Sequence
            frame[6] = 0x00.toByte()

            // Logger serial (uint32 little endian)
            frame[7] = (loggerSerial and 0xFF).toByte()
            frame[8] = ((loggerSerial ushr 8) and 0xFF).toByte()
            frame[9] = ((loggerSerial ushr 16) and 0xFF).toByte()
            frame[10] = ((loggerSerial ushr 24) and 0xFF).toByte()

            // Frame Type & Sensor Type
            frame[11] = 0x02.toByte() // Request
            frame[12] = 0x02.toByte() // Inverter
            frame[13] = 0x00.toByte()

            // Timestamps / offsets (zeros)
            for (i in 14..21) frame[i] = 0

            // Copy Modbus RTU payload starting at index 22
            System.arraycopy(modbusRtu, 0, frame, 22, modbusRtu.size)

            // Compute Solarman checksum (sum of bytes from index 1 to end of modbus payload, mod 256)
            var checksum = 0
            for (i in 1 until 22 + modbusRtu.size) {
                checksum += (frame[i].toInt() and 0xFF)
            }
            frame[22 + modbusRtu.size] = (checksum and 0xFF).toByte()
            frame[22 + modbusRtu.size + 1] = 0x15.toByte() // Tail

            return frame
        }

        fun parseModbusFromSolarman(buffer: ByteArray, length: Int, expectedQuantity: Int): IntArray {
            // In response, Modbus payload starts at offset 25:
            // 25: Unit ID
            // 26: Function Code (0x03)
            // 27: Byte count (2 * quantity)
            // 28..: Register data
            val modbusOffset = 25
            if (length < modbusOffset + 3) {
                throw IllegalStateException("Response too short for Modbus payload")
            }

            val fc = buffer[modbusOffset + 1].toInt() and 0xFF
            if (fc >= 0x80) {
                val exCode = buffer[modbusOffset + 2].toInt() and 0xFF
                throw IllegalStateException("Modbus exception over Solarman: 0x${exCode.toString(16)}")
            }

            val byteCount = buffer[modbusOffset + 2].toInt() and 0xFF
            val regCount = byteCount / 2
            val registers = IntArray(regCount)

            val dataStart = modbusOffset + 3
            for (i in 0 until regCount) {
                val msb = buffer[dataStart + i * 2].toInt() and 0xFF
                val lsb = buffer[dataStart + i * 2 + 1].toInt() and 0xFF
                registers[i] = (msb shl 8) or lsb
            }
            return registers
        }
    }
}
