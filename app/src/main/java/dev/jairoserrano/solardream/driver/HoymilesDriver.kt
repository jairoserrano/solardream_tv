package dev.jairoserrano.solardream.driver

import dev.jairoserrano.solardream.dtu.DtuClient
import dev.jairoserrano.solardream.model.EnergyModel
import dev.jairoserrano.solardream.model.EnergySnapshot
import dev.jairoserrano.solardream.model.MeterConvention
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Driver para DTU Hoymiles (DTU-Pro, DTU-WLite) mediante protocolo local TCP (puerto 10081).
 */
class HoymilesDriver : InverterDriver {
    override val id: String = "HOYMILES"
    override val displayName: String = "Hoymiles DTU"
    override val defaultPort: Int = 10081

    private val model = EnergyModel(MeterConvention.CALIBRATED)

    override fun probe(host: String): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, defaultPort), 1200)
            socket.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun readSnapshot(host: String): EnergySnapshot {
        val client = DtuClient(host, defaultPort)
        val raw = client.readRealData()
        return model.toSnapshot(raw)
    }
}
