package dev.jairoserrano.solardream.model

import dev.jairoserrano.solardream.dtu.RawReading
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class EnergyModel(private val convention: MeterConvention = MeterConvention.CALIBRATED) {

    fun toSnapshot(raw: RawReading): EnergySnapshot {
        val productionW = (raw.dtuPower * convention.dtuPowerWattsPerUnit).roundToInt()
        val meter = raw.meterTotalPower?.let { (it * convention.meterPowerWattsPerUnit).roundToInt() }
        val hasMeter = meter != null
        val gridW = when {
            meter == null -> 0
            convention.importIsPositive -> -meter
            else -> meter
        }
        val houseW = if (hasMeter) (productionW - gridW).coerceAtLeast(0) else null
        return EnergySnapshot(
            productionW = productionW,
            gridW = gridW,
            houseW = houseW,
            producedTodayWh = (raw.dtuDailyEnergy * convention.dailyEnergyWhPerUnit).roundToLong(),
            timestampSec = raw.timestamp,
            hasMeter = hasMeter,
        )
    }
}

