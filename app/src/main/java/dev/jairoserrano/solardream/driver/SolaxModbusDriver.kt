package dev.jairoserrano.solardream.driver

import dev.jairoserrano.solardream.model.EnergySnapshot
import kotlin.math.max

/**
 * Driver para inversores SolaX Power mediante Modbus TCP local en puerto 502.
 */
class SolaxModbusDriver(private val client: ModbusTcpClient = ModbusTcpClient()) : InverterDriver {
    override val id: String = "SOLAX"
    override val displayName: String = "SolaX Inverter"
    override val defaultPort: Int = 502

    override fun probe(host: String): Boolean {
        if (!client.ping(host, defaultPort)) return false
        return try {
            // Intenta leer los primeros registros de SolaX (FC 0x04 o 0x03)
            val regs = client.readRegisters(host, port = defaultPort, unitId = 1, functionCode = 0x04, startAddress = 0, quantity = 15)
            regs.isNotEmpty()
        } catch (_: Exception) {
            try {
                val regs = client.readRegisters(host, port = defaultPort, unitId = 1, functionCode = 0x03, startAddress = 0, quantity = 15)
                regs.isNotEmpty()
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun readSnapshot(host: String): EnergySnapshot {
        // Intenta primero con Input Registers (0x04), luego Holding Registers (0x03)
        val regs = try {
            client.readRegisters(host, port = defaultPort, unitId = 1, functionCode = 0x04, startAddress = 0, quantity = 25)
        } catch (_: Exception) {
            client.readRegisters(host, port = defaultPort, unitId = 1, functionCode = 0x03, startAddress = 0, quantity = 25)
        }

        val pv1 = if (regs.size > 8) regs[8] else 0
        val pv2 = if (regs.size > 9) regs[9] else 0
        val productionW = max(0, pv1 + pv2)

        // Grid Power con signo: en SolaX el entero de 16 bits con signo indica exportacion (>0) o importacion (<0)
        val rawGrid = if (regs.size > 3) regs[3].toShort().toInt() else 0
        val gridW = rawGrid

        // Rendimiento de hoy (Yield today) en 0.1 kWh -> Wh
        val todayYield = if (regs.size > 11) regs[11].toLong() * 100L else 0L

        // Consumo del hogar (si tiene CT/medidor en reg 20, de lo contrario calculado: produccion - exportacion)
        val houseMeter = if (regs.size > 20 && regs[20] in 1..50000) regs[20] else null
        val houseW = houseMeter ?: max(0, productionW - gridW)

        return EnergySnapshot(
            productionW = productionW,
            gridW = gridW,
            houseW = houseW,
            producedTodayWh = todayYield,
            timestampSec = System.currentTimeMillis() / 1000L,
            hasMeter = houseMeter != null || rawGrid != 0,
        )
    }
}
