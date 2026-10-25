package dev.jairoserrano.solardream.driver

import android.util.Log
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class DiscoveredInverter(
    val host: String,
    val driverId: String,
    val displayName: String,
    val port: Int,
)

/**
 * Escanea la subred local (/24) buscando inversores y pasarelas solares (Hoymiles, SolaX, Deye, SunSpec).
 */
class SubnetScanner(
    private val autodetector: InverterAutodetector = InverterAutodetector(),
) {

    private val solarPorts = listOf(10081, 8899, 502)

    /**
     * Obtiene el prefijo de subred local (ej. "192.168.1.").
     */
    fun getLocalSubnetPrefix(fallbackHost: String): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddr = addr.hostAddress ?: continue
                        val parts = hostAddr.split(".")
                        if (parts.size == 4) {
                            return "${parts[0]}.${parts[1]}.${parts[2]}."
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error detectando IP local: ${e.message}")
        }

        // Fallback usando el host configurado
        val parts = fallbackHost.split(".")
        return if (parts.size == 4) {
            "${parts[0]}.${parts[1]}.${parts[2]}."
        } else {
            "192.168.1."
        }
    }

    /**
     * Escanea la subred local buscando equipos solares en puertos 10081, 8899 y 502.
     */
    fun scanSubnet(subnetPrefix: String, progressCallback: ((Int) -> Unit)? = null): List<DiscoveredInverter> {
        val discovered = ConcurrentLinkedQueue<DiscoveredInverter>()
        val executor = Executors.newFixedThreadPool(32)
        val total = 254
        var completed = 0

        Log.i(TAG, "Iniciando escaneo de red en ${subnetPrefix}0/24...")

        for (i in 1..254) {
            val targetIp = "$subnetPrefix$i"
            executor.execute {
                try {
                    for (port in solarPorts) {
                        if (isPortOpen(targetIp, port, 300)) {
                            Log.i(TAG, "Puerto solar $port abierto en $targetIp! Identificando protocolo...")
                            val driver = autodetector.detect(targetIp)
                            discovered.add(
                                DiscoveredInverter(
                                    host = targetIp,
                                    driverId = driver.id,
                                    displayName = driver.displayName,
                                    port = port,
                                )
                            )
                            break
                        }
                    }
                } finally {
                    synchronized(this) {
                        completed++
                        progressCallback?.invoke(completed * 100 / total)
                    }
                }
            }
        }

        executor.shutdown()
        executor.awaitTermination(6, TimeUnit.SECONDS)

        Log.i(TAG, "Escaneo finalizado. Inversores encontrados: ${discovered.size}")
        return discovered.toList()
    }

    private fun isPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val TAG = "SubnetScanner"
    }
}
