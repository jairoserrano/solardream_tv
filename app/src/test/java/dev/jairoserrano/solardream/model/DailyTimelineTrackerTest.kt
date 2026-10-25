package dev.jairoserrano.solardream.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar
import java.util.TimeZone

class DailyTimelineTrackerTest {

    private fun timeMillis(hour: Int, minute: Int, dayOffset: Int = 0): Long {
        return Calendar.getInstance(TimeZone.getDefault()).apply {
            if (dayOffset != 0) {
                add(Calendar.DAY_OF_MONTH, dayOffset)
            }
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `al inicio la linea de tiempo esta vacia con techo minimo de 2000W`() {
        val tracker = DailyTimelineTracker()
        val series = tracker.getTimelineSeries(timeMillis(12, 0))
        assertFalse(series.hasAnyData)
        assertEquals(96, series.buckets.size)
        assertEquals(DailyTimelineTracker.MIN_MAX_POWER_W, series.maxPowerW)
        assertEquals(0f, series.todayHouseKwh, 0.01f)
        assertEquals(0f, series.todayImportKwh, 0.01f)
    }

    @Test
    fun `registra cubeta correcta en intervalo de 15 minutos`() {
        val tracker = DailyTimelineTracker()
        // 10:14 -> minuto 614 -> cubeta 614 / 15 = 40
        val t1 = timeMillis(10, 14)
        tracker.record(EnergySnapshot(2400, -800, 3200, 5000L, 0L, true), t1)

        val series = tracker.getTimelineSeries(t1)
        assertTrue(series.hasAnyData)
        val b40 = series.buckets[40]
        assertTrue(b40.hasData)
        assertEquals(2400, b40.solarW)
        assertEquals(3200, b40.houseW)
        assertEquals(800, b40.gridImportW) // -800 gridW significa importacion de 800W

        // La cubeta 41 no debe tener datos todavia
        assertFalse(series.buckets[41].hasData)
    }

    @Test
    fun `promedia multiples lecturas dentro de la misma cubeta`() {
        val tracker = DailyTimelineTracker()
        val t1 = timeMillis(12, 2)
        val t2 = timeMillis(12, 8)

        tracker.record(EnergySnapshot(2000, 500, 1500, 4000L, 0L, true), t1)
        tracker.record(EnergySnapshot(3000, 500, 2500, 4000L, 0L, true), t2)

        val series = tracker.getTimelineSeries(t2)
        // 12:00 -> cubeta 12 * 4 = 48
        val b48 = series.buckets[48]
        assertTrue(b48.hasData)
        assertEquals(2500, b48.solarW) // (2000 + 3000) / 2
        assertEquals(2000, b48.houseW) // (1500 + 2500) / 2
        assertEquals(0, b48.gridImportW) // Exportando (+500), importacion es 0
    }

    @Test
    fun `cambio de fecha reinicia las cubetas para el nuevo dia`() {
        val tracker = DailyTimelineTracker()
        val day1 = timeMillis(23, 45, dayOffset = 0)
        tracker.record(EnergySnapshot(0, -500, 500, 10000L, 0L, true), day1)
        assertTrue(tracker.getTimelineSeries(day1).hasAnyData)

        // Siguiente dia a las 00:05
        val day2 = timeMillis(0, 5, dayOffset = 1)
        val seriesDay2 = tracker.getTimelineSeries(day2)
        assertFalse(seriesDay2.hasAnyData)
        assertFalse(seriesDay2.buckets[95].hasData) // Cubeta de las 23:45 debe estar limpia
    }

    @Test
    fun `persistencia en disco guarda y restaura el historico`() {
        val tempFile = File.createTempFile("solardream_test_", ".json")
        try {
            val tracker1 = DailyTimelineTracker(tempFile)
            val t = timeMillis(14, 30) // cubeta 14 * 4 + 2 = 58
            tracker1.record(EnergySnapshot(4200, 1200, 3000, 12000L, 0L, true), t)

            // Cargar en una nueva instancia desde el mismo archivo
            val tracker2 = DailyTimelineTracker(tempFile)
            val series = tracker2.getTimelineSeries(t)

            assertTrue(series.hasAnyData)
            val b58 = series.buckets[58]
            assertTrue(b58.hasData)
            assertEquals(4200, b58.solarW)
            assertEquals(3000, b58.houseW)
            assertEquals(0, b58.gridImportW)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `escala de potencia maxima redondea a multiplos de 500W con minimo 2000W`() {
        val tracker = DailyTimelineTracker()
        val t = timeMillis(13, 0)
        tracker.record(EnergySnapshot(3250, 0, 3250, 0L, 0L, true), t)

        val series = tracker.getTimelineSeries(t)
        // 3250W supera 2000W y debe redondearse al siguiente múltiplo de 500W -> 3500W
        assertEquals(3500, series.maxPowerW)
    }
}
