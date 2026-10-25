package dev.jairoserrano.solardream.driver

import dev.jairoserrano.solardream.model.EnergySnapshot
import kotlin.math.max
import kotlin.math.pow

/**
 * Driver universal para inversores que cumplen el estándar industrial SunSpec Modbus TCP (puerto 502).
 * Compatible con Fronius, SolarEdge, SMA, Schneider, ABB, Sungrow, etc.
 */
class SunSpecModbusDriver(private val client: ModbusTcpClient = ModbusTcpClient()) : InverterDriver {
    override val id: String = "SUNSPEC"
    override val displayName: String = "SunSpec (Fronius/SMA/SolarEdge)"
    override val defaultPort: Int = 502

    private var baseAddress: Int = 40000

    override fun probe(host: String): Boolean {
        if (!client.ping(host, defaultPort)) return false

        // Las direcciones base estandar para buscar "SunS" son 40000, 40001 o 0
        for (candidate in listOf(40000, 40001, 0)) {
            try {
                val regs = client.readRegisters(host, port = defaultPort, unitId = 1, functionCode = 0x03, startAddress = candidate, quantity = 2)
                if (regs.size >= 2 && regs[0] == 0x5375 && regs[1] == 0x6E53) { // "SunS"
                    baseAddress = candidate
                    return true
                }
            } catch (_: Exception) {
                // Siguiente candidato
            }
        }
        return false
    }

    override fun readSnapshot(host: String): EnergySnapshot {
        // 1. Leer encabezado y longitud del modelo común (Modelo 1)
        // Regs: [0, 1] = "SunS", [2] = ModelID (1), [3] = ModelLength L
        val head = client.readRegisters(host, port = defaultPort, unitId = 1, functionCode = 0x03, startAddress = baseAddress, quantity = 4)
        val commonLen = head[3]

        // 2. El modelo de inversor (Model 101/102/103) comienza inmediatamente despues del Modelo 1:
        // offset = baseAddress + 4 + commonLen
        val inverterStart = baseAddress + 4 + commonLen
        val invData = client.readRegisters(host, port = defaultPort, unitId = 1, functionCode = 0x03, startAddress = inverterStart, quantity = 30)

        // Modelo de inversor SunSpec:
        // offset 0: Model ID (101, 102, 103)
        // offset 1: Model Length
        // offset 14: Watts (entero con signo de 16 bits)
        // offset 15: Watts Scale Factor (entero con signo de 16 bits)
        // offset 24, 25: Lifetime Energy Wh (uint32)
        // offset 26: Energy Scale Factor
        val rawW = if (invData.size > 14) invData[14].toShort().toInt() else 0
        val scaleW = if (invData.size > 15) invData[15].toShort().toInt() else 0
        val productionW = max(0, (rawW * 10.0.pow(scaleW.toDouble())).toInt())

        val rawWh = if (invData.size > 25) {
            ((invData[24].toLong() and 0xFFFF) shl 16) or (invData[25].toLong() and 0xFFFF)
        } else 0L
        val scaleWh = if (invData.size > 26) invData[26].toShort().toInt() else 0
        val lifetimeWh = (rawWh * 10.0.pow(scaleWh.toDouble())).toLong()

        // 3. Intentar buscar modelo de medidor (Model 201/202/203)
        val invLen = invData[1]
        val meterStart = inverterStart + 2 + invLen
        var gridW = 0
        var hasMeter = false

        try {
            val meterData = client.readRegisters(host, port = defaultPort, unitId = 1, functionCode = 0x03, startAddress = meterStart, quantity = 25)
            val meterId = meterData[0]
            if (meterId in 201..203) {
                // En medidor SunSpec: offset 19 es Real Power W, offset 20 es W_SF
                val rawMeterW = meterData[19].toShort().toInt()
                val scaleMeterW = meterData[20].toShort().toInt()
                gridW = (rawMeterW * 10.0.pow(scaleMeterW.toDouble())).toInt()
                hasMeter = true
            }
        } catch (_: Exception) {
            // Sin medidor SunSpec conectado
        }

        val houseW = max(0, productionW - gridW)

        return EnergySnapshot(
            productionW = productionW,
            gridW = gridW,
            houseW = houseW,
            producedTodayWh = lifetimeWh % 100_000_000L, // Estimacion o lifetime si no hay acumulado diario
            timestampSec = System.currentTimeMillis() / 1000L,
            hasMeter = hasMeter,
        )
    }
}
