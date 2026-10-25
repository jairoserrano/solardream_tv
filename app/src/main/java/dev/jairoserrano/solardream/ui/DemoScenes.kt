package dev.jairoserrano.solardream.ui

import dev.jairoserrano.solardream.model.TimelineBucket
import dev.jairoserrano.solardream.model.TimelineSeries
import java.util.Locale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Escenarios realistas para capturas de pantalla de la tienda Google Play Store.
 */
object DemoScenes {

    fun getScene(sceneIndex: Int, isEnglish: Boolean): FlowState {
        val locale = if (isEnglish) Locale.ENGLISH else Locale.forLanguageTag("es")
        return when (sceneIndex) {
            1 -> sceneMiddayExport(locale)
            2 -> sceneEnergyBalance(locale)
            3 -> sceneNightMode(locale)
            4 -> sceneCloudyDay(locale)
            5 -> sceneMiddayExport(locale) // Background behind settings dialog
            6 -> sceneOfflineWaiting(locale)
            7 -> sceneMiddayExport(locale) // English export
            8 -> sceneNightMode(locale)    // English night
            else -> sceneMiddayExport(locale)
        }
    }

    private fun generateTimeline(
        currentMinute: Int,
        peakSolarW: Int,
        baseHouseW: Int,
        noonHouseW: Int,
        eveningHouseW: Int,
        cloudyFactor: Double = 1.0,
        maxCeilingW: Int = 4000
    ): TimelineSeries {
        val currentBucket = (currentMinute / 15).coerceIn(0, 95)
        val buckets = ArrayList<TimelineBucket>(96)
        var totalSolarWh = 0.0
        var totalHouseWh = 0.0
        var totalImportWh = 0.0

        for (i in 0 until 96) {
            val bucketMinute = i * 15 + 7
            if (bucketMinute > currentMinute) {
                buckets.add(TimelineBucket(i, 0, 0, 0, false))
                continue
            }

            // 1. Solar curve (approx 6:30 to 18:30)
            val solarW = if (bucketMinute in 390..1110) {
                val progress = (bucketMinute - 390).toDouble() / (1110 - 390)
                val baseSin = sin(progress * PI)
                val noise = if (cloudyFactor < 0.9) {
                    // Simula nubes irregulares
                    0.5 + 0.5 * sin(progress * PI * 7)
                } else 1.0
                (peakSolarW * baseSin * noise * cloudyFactor).toInt().coerceAtLeast(0)
            } else 0

            // 2. House consumption curve
            val houseW = when {
                bucketMinute < 390 -> baseHouseW + (40 * sin(bucketMinute.toDouble())).toInt()
                bucketMinute in 390..540 -> baseHouseW + ((noonHouseW - baseHouseW) * ((bucketMinute - 390) / 150.0)).toInt() // Despertar
                bucketMinute in 540..1110 -> noonHouseW + (120 * sin(bucketMinute * 0.05)).toInt() // Mediodía
                bucketMinute in 1110..1320 -> eveningHouseW + (150 * sin(bucketMinute * 0.03)).toInt() // Noche / Cena
                else -> baseHouseW
            }.coerceAtLeast(200)

            // 3. Grid Import
            val gridImportW = max(0, houseW - solarW)

            totalSolarWh += solarW * 0.25
            totalHouseWh += houseW * 0.25
            totalImportWh += gridImportW * 0.25

            buckets.add(TimelineBucket(i, solarW, houseW, gridImportW, true))
        }

        return TimelineSeries(
            buckets = buckets,
            currentBucketIndex = currentBucket,
            currentMinuteOfDay = currentMinute,
            maxPowerW = maxCeilingW,
            todaySolarKwh = (totalSolarWh / 1000.0).toFloat(),
            todayHouseKwh = (totalHouseWh / 1000.0).toFloat(),
            todayImportKwh = (totalImportWh / 1000.0).toFloat(),
            hasAnyData = true
        )
    }

    // 1. Pico Solar Mediodía con Exportación a Red (13:45)
    private fun sceneMiddayExport(locale: Locale): FlowState {
        val isEs = locale.language.lowercase() == "es"
        val timeline = generateTimeline(
            currentMinute = 825, // 13:45
            peakSolarW = 3900,
            baseHouseW = 420,
            noonHouseW = 1200,
            eveningHouseW = 2100,
            maxCeilingW = 4000
        )
        return FlowState(
            panelsText = String.format(locale, "3.85 kW"),
            houseText = String.format(locale, "1.20 kW"),
            gridText = String.format(locale, "2.65 kW"),
            gridLabel = if (isEs) "Red (exporta)" else "Grid (export)",
            panelsToHouse = Arrow.FORWARD,
            houseGrid = Arrow.FORWARD,
            gridMode = GridMode.EXPORT,
            dimmed = false,
            footer = if (isEs) "Tu hogar comparte energía con la red" else "Your home is sharing energy with the grid",
            corner = "13:45",
            todayText = "18.4",
            timeline = timeline
        )
    }

    // 2. Equilibrio y Autoconsumo Total (16:15)
    private fun sceneEnergyBalance(locale: Locale): FlowState {
        val isEs = locale.language.lowercase() == "es"
        val timeline = generateTimeline(
            currentMinute = 975, // 16:15
            peakSolarW = 3200,
            baseHouseW = 450,
            noonHouseW = 1700,
            eveningHouseW = 2000,
            maxCeilingW = 3500
        )
        return FlowState(
            panelsText = String.format(locale, "1.75 kW"),
            houseText = String.format(locale, "1.70 kW"),
            gridText = String.format(locale, "0.05 kW"),
            gridLabel = if (isEs) "Red" else "Grid",
            panelsToHouse = Arrow.FORWARD,
            houseGrid = Arrow.OFF,
            gridMode = GridMode.IDLE,
            dimmed = false,
            footer = if (isEs) "100% autoconsumo solar" else "100% solar self-consumption",
            corner = "16:15",
            todayText = "14.2",
            timeline = timeline
        )
    }

    // 3. Modo Nocturno / Red Eléctrica (21:30)
    private fun sceneNightMode(locale: Locale): FlowState {
        val isEs = locale.language.lowercase() == "es"
        val timeline = generateTimeline(
            currentMinute = 1290, // 21:30
            peakSolarW = 4100,
            baseHouseW = 480,
            noonHouseW = 1350,
            eveningHouseW = 850,
            maxCeilingW = 4500
        )
        return FlowState(
            panelsText = String.format(locale, "0.00 kW"),
            houseText = String.format(locale, "0.85 kW"),
            gridText = String.format(locale, "0.85 kW"),
            gridLabel = if (isEs) "Red (importa)" else "Grid (import)",
            panelsToHouse = Arrow.OFF,
            houseGrid = Arrow.BACKWARD,
            gridMode = GridMode.IMPORT,
            dimmed = false,
            footer = if (isEs) "La red mantiene tu hogar en marcha" else "The grid is powering your home",
            corner = "21:30",
            todayText = "22.1",
            timeline = timeline
        )
    }

    // 4. Día Nublado con Consumo Mixto (10:20)
    private fun sceneCloudyDay(locale: Locale): FlowState {
        val isEs = locale.language.lowercase() == "es"
        val timeline = generateTimeline(
            currentMinute = 620, // 10:20
            peakSolarW = 2500,
            baseHouseW = 450,
            noonHouseW = 1900,
            eveningHouseW = 1800,
            cloudyFactor = 0.45,
            maxCeilingW = 3000
        )
        return FlowState(
            panelsText = String.format(locale, "0.65 kW"),
            houseText = String.format(locale, "1.90 kW"),
            gridText = String.format(locale, "1.25 kW"),
            gridLabel = if (isEs) "Red (importa)" else "Grid (import)",
            panelsToHouse = Arrow.FORWARD,
            houseGrid = Arrow.BACKWARD,
            gridMode = GridMode.IMPORT,
            dimmed = false,
            footer = if (isEs) "El sol y la red alimentan tu hogar" else "Sun and grid powering your home together",
            corner = "10:20",
            todayText = "4.8",
            timeline = timeline
        )
    }

    // 6. Sin Conexión / Esperando al DTU (18:45)
    private fun sceneOfflineWaiting(locale: Locale): FlowState {
        val isEs = locale.language.lowercase() == "es"
        val timeline = generateTimeline(
            currentMinute = 1125, // 18:45
            peakSolarW = 3600,
            baseHouseW = 400,
            noonHouseW = 1100,
            eveningHouseW = 1200,
            maxCeilingW = 4000
        )
        return FlowState(
            panelsText = String.format(locale, "—"),
            houseText = String.format(locale, "—"),
            gridText = String.format(locale, "—"),
            gridLabel = if (isEs) "Red" else "Grid",
            panelsToHouse = Arrow.OFF,
            houseGrid = Arrow.OFF,
            gridMode = GridMode.UNKNOWN,
            dimmed = true,
            footer = if (isEs) "Sin conexión con el DTU local" else "No connection to local DTU",
            corner = "18:45",
            todayText = "12.8",
            timeline = timeline
        )
    }
}
