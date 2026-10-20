package com.jairo.solardream.model

/**
 * Cómo interpretar los números crudos del DTU. Los valores por defecto son hipótesis
 * a partir de una lectura nocturna; la Tarea 8 los calibra con lecturas de día.
 *
 * - [importIsPositive]: si la potencia del medidor es positiva al importar de la red.
 * - [dtuPowerWattsPerUnit]: vatios por unidad del campo dtu_power.
 * - [dailyEnergyWhPerUnit]: vatios-hora por unidad del campo dtu_daily_energy.
 * - [meterPowerWattsPerUnit]: vatios por unidad del campo de potencia del medidor
 *   (phase_total_power); el DTU lo entrega en unidades de 10 W.
 */
data class MeterConvention(
    val importIsPositive: Boolean = false,
    val dtuPowerWattsPerUnit: Double = 0.1,
    val dailyEnergyWhPerUnit: Double = 1.0,
    val meterPowerWattsPerUnit: Double = 10.0,
) {
    companion object {
        val CALIBRATED = MeterConvention()
    }
}
