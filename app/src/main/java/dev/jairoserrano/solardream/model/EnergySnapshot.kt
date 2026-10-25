package dev.jairoserrano.solardream.model

/**
 * Estado de energía ya normalizado.
 * [gridW] > 0 exporta a la red, < 0 importa. [houseW] es nulo si no hay medidor.
 */
data class EnergySnapshot(
    val productionW: Int,
    val gridW: Int,
    val houseW: Int?,
    val producedTodayWh: Long,
    val timestampSec: Long,
    val hasMeter: Boolean,
)

