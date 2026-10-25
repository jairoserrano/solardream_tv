package dev.jairoserrano.solardream.driver

import dev.jairoserrano.solardream.model.EnergySnapshot
import kotlin.math.max

/**
 * Driver para inversores Deye, SunSynk y Sol-Ark mediante protocolo Solarman V5 (puerto 8899)
 * o Modbus TCP directo (puerto 502).
 */
class DeyeSolarmanDriver(
    private val solarmanClient: SolarmanV5Client = SolarmanV5Client(),
    private val modbusClient: ModbusTcpClient = ModbusTcpClient(),
) : InverterDriver {
    override val id: String = "DEYE"
    override val displayName: String = "Deye / SunSynk"
    override val defaultPort: Int = 8899

    override fun probe(host: String): Boolean {
        // 1. Probar puerto Solarman V5 (8899)
        if (solarmanClient.ping(host, 8899)) {
            return try {
                val regs = solarmanClient.readHoldingRegisters(host, port = 8899, startAddress = 60, quantity = 5)
                regs.isNotEmpty()
            } catch (_: Exception) {
                true // El puerto 8899 abierto es distintivo de registradores Solarman
            }
        }
        // 2. Probar si tiene Modbus TCP directo en 502
        if (modbusClient.ping(host, 502)) {
            return try {
                val regs = modbusClient.readRegisters(host, port = 502, unitId = 1, functionCode = 0x03, startAddress = 60, quantity = 5)
                regs.isNotEmpty()
            } catch (_: Exception) {
                false
            }
        }
        return false
    }

    override fun readSnapshot(host: String): EnergySnapshot {
        val useSolarman = solarmanClient.ping(host, 8899)

        // Lectura de bloque solar y energia (Registros 60 a 111)
        val block1 = if (useSolarman) {
            solarmanClient.readHoldingRegisters(host, port = 8899, startAddress = 60, quantity = 52)
        } else {
            modbusClient.readRegisters(host, port = 502, unitId = 1, functionCode = 0x03, startAddress = 60, quantity = 52)
        }

        // Reg 60: Energia del dia en 0.1 kWh -> Wh
        val todayYieldWh = if (block1.isNotEmpty()) block1[0].toLong() * 100L else 0L

        // Reg 108 (offset 48) y Reg 109 (offset 49): PV1 y PV2 en Watts
        val pv1 = if (block1.size > 48) block1[48] else 0
        val pv2 = if (block1.size > 49) block1[49] else 0
        var productionW = pv1 + pv2
        if (productionW == 0 && block1.size > 5) {
            // Fallback a potencia total AC en reg 65 (offset 5)
            productionW = block1[5]
        }

        // Lectura de bloque de red y carga (Registros 175 a 191)
        var gridW = 0
        var houseW: Int? = null
        var hasMeter = false

        try {
            val block2 = if (useSolarman) {
                solarmanClient.readHoldingRegisters(host, port = 8899, startAddress = 175, quantity = 16)
            } else {
                modbusClient.readRegisters(host, port = 502, unitId = 1, functionCode = 0x03, startAddress = 175, quantity = 16)
            }

            if (block2.isNotEmpty()) {
                // Reg 178 (offset 3): Consumo total del hogar en Watts
                if (block2.size > 3 && block2[3] > 0) {
                    houseW = block2[3]
                    hasMeter = true
                }
                // Reg 189 (offset 14): Potencia de red (CT) entero con signo (positivo exporta, negativo importa)
                if (block2.size > 14) {
                    gridW = block2[14].toShort().toInt()
                    hasMeter = true
                }
            }
        } catch (_: Exception) {
            // Sin medidor CT conectado
        }

        if (houseW == null) {
            houseW = max(0, productionW - gridW)
        }

        return EnergySnapshot(
            productionW = max(0, productionW),
            gridW = gridW,
            houseW = houseW,
            producedTodayWh = todayYieldWh,
            timestampSec = System.currentTimeMillis() / 1000L,
            hasMeter = hasMeter,
        )
    }
}
