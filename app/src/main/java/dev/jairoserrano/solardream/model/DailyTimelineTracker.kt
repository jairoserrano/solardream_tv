package dev.jairoserrano.solardream.model

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

data class TimelineBucket(
    val index: Int,
    val solarW: Int,
    val houseW: Int,
    val gridImportW: Int,
    val hasData: Boolean,
)

data class TimelineSeries(
    val buckets: List<TimelineBucket>,
    val currentBucketIndex: Int,
    val currentMinuteOfDay: Int,
    val maxPowerW: Int,
    val todaySolarKwh: Float,
    val todayHouseKwh: Float,
    val todayImportKwh: Float,
    val hasAnyData: Boolean,
)

/**
 * Registra y gestiona los puntos de energía a lo largo del día en intervalos de 15 minutos (96 puntos).
 * Mantiene promedios reales por cubeta y persiste el histórico en almacenamiento local.
 */
class DailyTimelineTracker(private val storageFile: File? = null) {

    private class RawBucket {
        var count: Int = 0
        var solarSum: Long = 0L
        var houseSum: Long = 0L
        var importSum: Long = 0L

        fun add(solar: Int, house: Int, gridImport: Int) {
            count++
            solarSum += solar
            houseSum += house
            importSum += gridImport
        }

        val hasData: Boolean get() = count > 0
        val solarW: Int get() = if (count > 0) (solarSum / count).toInt() else 0
        val houseW: Int get() = if (count > 0) (houseSum / count).toInt() else 0
        val gridImportW: Int get() = if (count > 0) (importSum / count).toInt() else 0

        fun reset() {
            count = 0
            solarSum = 0L
            houseSum = 0L
            importSum = 0L
        }
    }

    private val rawBuckets = Array(BUCKETS_PER_DAY) { RawBucket() }
    private var currentDate: String = ""
    private var latestSnapshot: EnergySnapshot? = null
    private var isDirty = false

    init {
        loadFromDisk()
    }

    @Synchronized
    fun record(snapshot: EnergySnapshot, nowMillis: Long) {
        val dateStr = formatDate(nowMillis)
        if (dateStr != currentDate) {
            resetForDate(dateStr)
        }

        latestSnapshot = snapshot
        val minuteOfDay = getMinuteOfDay(nowMillis)
        val bucketIdx = (minuteOfDay / INTERVAL_MINUTES).coerceIn(0, BUCKETS_PER_DAY - 1)

        val solar = snapshot.productionW.coerceAtLeast(0)
        val house = snapshot.houseW?.coerceAtLeast(0) ?: 0
        val gridImport = if (snapshot.hasMeter && snapshot.gridW < -ACTIVE_W) -snapshot.gridW else 0

        rawBuckets[bucketIdx].add(solar, house, gridImport)
        isDirty = true
        persist()
    }

    @Synchronized
    fun getTimelineSeries(nowMillis: Long = System.currentTimeMillis()): TimelineSeries {
        val dateStr = formatDate(nowMillis)
        if (dateStr != currentDate) {
            resetForDate(dateStr)
        }

        val minuteOfDay = getMinuteOfDay(nowMillis)
        val currentBucket = (minuteOfDay / INTERVAL_MINUTES).coerceIn(0, BUCKETS_PER_DAY - 1)

        var peakPower = MIN_MAX_POWER_W
        var hasAny = false
        var sumSolarWatts = 0L
        var sumHouseWatts = 0L
        var sumImportWatts = 0L
        var activeBucketCount = 0

        val bucketList = ArrayList<TimelineBucket>(BUCKETS_PER_DAY)
        for (i in 0 until BUCKETS_PER_DAY) {
            val b = rawBuckets[i]
            if (b.hasData) {
                hasAny = true
                activeBucketCount++
                val s = b.solarW
                val h = b.houseW
                val g = b.gridImportW
                peakPower = max(peakPower, max(s, max(h, g)))
                sumSolarWatts += s
                sumHouseWatts += h
                sumImportWatts += g
                bucketList.add(TimelineBucket(i, s, h, g, true))
            } else {
                bucketList.add(TimelineBucket(i, 0, 0, 0, false))
            }
        }

        // Redondeo elegante hacia arriba para el techo del gráfico (múltiplos de 500 W)
        val scaledMaxPower = if (peakPower > MIN_MAX_POWER_W) {
            ((peakPower + 499) / 500) * 500
        } else {
            MIN_MAX_POWER_W
        }

        // Cálculo de energía acumulada hoy en kWh (intervalo de 15 min = 0.25 h)
        val solarKwh = latestSnapshot?.takeIf { it.producedTodayWh > 0L }?.let {
            it.producedTodayWh / 1000f
        } ?: (sumSolarWatts * HOURS_PER_BUCKET / 1000f)

        val houseKwh = sumHouseWatts * HOURS_PER_BUCKET / 1000f
        val importKwh = sumImportWatts * HOURS_PER_BUCKET / 1000f

        return TimelineSeries(
            buckets = bucketList,
            currentBucketIndex = currentBucket,
            currentMinuteOfDay = minuteOfDay,
            maxPowerW = scaledMaxPower,
            todaySolarKwh = solarKwh,
            todayHouseKwh = houseKwh,
            todayImportKwh = importKwh,
            hasAnyData = hasAny,
        )
    }

    @Synchronized
    fun resetForDate(dateStr: String) {
        currentDate = dateStr
        for (b in rawBuckets) {
            b.reset()
        }
        latestSnapshot = null
        isDirty = true
    }

    @Synchronized
    fun persist() {
        if (!isDirty || storageFile == null) return
        try {
            val sb = java.lang.StringBuilder()
            sb.append(currentDate).append('\n')
            for (i in 0 until BUCKETS_PER_DAY) {
                val b = rawBuckets[i]
                if (b.hasData) {
                    sb.append(i).append(',')
                        .append(b.count).append(',')
                        .append(b.solarSum).append(',')
                        .append(b.houseSum).append(',')
                        .append(b.importSum).append('\n')
                }
            }
            storageFile.writeText(sb.toString())
            isDirty = false
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo guardar la línea de tiempo: ${e.message}")
        }
    }

    private fun loadFromDisk() {
        if (storageFile == null || !storageFile.exists()) {
            currentDate = formatDate(System.currentTimeMillis())
            return
        }
        try {
            val lines = storageFile.readLines()
            if (lines.isEmpty()) return
            val fileDate = lines[0].trim()
            val todayDate = formatDate(System.currentTimeMillis())
            if (fileDate != todayDate) {
                currentDate = todayDate
                return
            }

            currentDate = fileDate
            for (k in 1 until lines.size) {
                val line = lines[k].trim()
                if (line.isEmpty()) continue
                val parts = line.split(',')
                if (parts.size == 5) {
                    val idx = parts[0].toIntOrNull() ?: continue
                    if (idx in 0 until BUCKETS_PER_DAY) {
                        val b = rawBuckets[idx]
                        b.count = parts[1].toIntOrNull() ?: 1
                        b.solarSum = parts[2].toLongOrNull() ?: 0L
                        b.houseSum = parts[3].toLongOrNull() ?: 0L
                        b.importSum = parts[4].toLongOrNull() ?: 0L
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo cargar el archivo de línea de tiempo: ${e.message}")
            currentDate = formatDate(System.currentTimeMillis())
        }
    }

    companion object {
        const val INTERVAL_MINUTES = 15
        const val BUCKETS_PER_DAY = 24 * (60 / INTERVAL_MINUTES) // 96
        const val HOURS_PER_BUCKET = INTERVAL_MINUTES / 60f // 0.25f
        const val MIN_MAX_POWER_W = 2000
        const val ACTIVE_W = 5
        private const val TAG = "SolarDream"
        private const val FILE_NAME = "daily_timeline.json"

        private var instance: DailyTimelineTracker? = null

        @Synchronized
        fun getOrCreate(context: Context): DailyTimelineTracker {
            return instance ?: run {
                val file = File(context.filesDir, FILE_NAME)
                DailyTimelineTracker(file).also { instance = it }
            }
        }

        fun formatDate(millis: Long): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))
        }

        fun getMinuteOfDay(millis: Long): Int {
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        }
    }
}
