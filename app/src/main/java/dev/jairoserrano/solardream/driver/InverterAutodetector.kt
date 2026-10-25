package dev.jairoserrano.solardream.driver

import android.util.Log

/**
 * Autodetector inteligente que descubre el tipo de inversor en la IP dada.
 */
class InverterAutodetector(
    val drivers: List<InverterDriver> = listOf(
        HoymilesDriver(),
        DeyeSolarmanDriver(),
        SolaxModbusDriver(),
        SunSpecModbusDriver(),
    ),
) {

    /**
     * Prueba los drivers en secuencia hasta encontrar el que responda afirmativamente.
     */
    fun detect(host: String): InverterDriver {
        Log.i(TAG, "Iniciando autodetección de inversor en $host...")
        for (driver in drivers) {
            try {
                Log.d(TAG, "Probando driver ${driver.displayName} (puerto ${driver.defaultPort})...")
                if (driver.probe(host)) {
                    Log.i(TAG, "¡Inversor detectado con éxito!: ${driver.displayName} en $host")
                    return driver
                }
            } catch (e: Exception) {
                Log.d(TAG, "Fallo en sondeo de ${driver.displayName}: ${e.message}")
            }
        }
        Log.w(TAG, "No se detectó un protocolo específico; usando Hoymiles DTU por defecto.")
        return drivers.first()
    }

    fun getDriverById(id: String): InverterDriver {
        return drivers.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: drivers.first()
    }

    private companion object {
        const val TAG = "InverterAutodetector"
    }
}
