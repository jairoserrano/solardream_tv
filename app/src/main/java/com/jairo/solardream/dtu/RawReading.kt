package com.jairo.solardream.dtu

/**
 * Lectura combinada de todas las páginas, en las unidades crudas del DTU.
 * [meterTotalPower] es nulo si ninguna página trae datos de medidor.
 */
data class RawReading(
    val deviceSerial: String,
    val timestamp: Long,
    val meterTotalPower: Int?,
    val dtuPower: Long,
    val dtuDailyEnergy: Long,
)
