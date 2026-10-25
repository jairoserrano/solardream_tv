package dev.jairoserrano.solardream

import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.jairoserrano.solardream.driver.InverterAutodetector
import dev.jairoserrano.solardream.driver.InverterDriver
import dev.jairoserrano.solardream.model.ConnectionTracker
import dev.jairoserrano.solardream.model.DailyTimelineTracker
import dev.jairoserrano.solardream.ui.FlowState
import dev.jairoserrano.solardream.ui.FlowView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Sondea el inversor en un hilo de fondo y entrega el estado a la vista en el hilo principal. */
class EnergyPoller(
    private val host: String,
    private val view: FlowView,
    private val autodetector: InverterAutodetector = InverterAutodetector(),
    private val forcedDriverId: String = DtuSettings.TYPE_AUTO,
    private val timelineTracker: DailyTimelineTracker? = DailyTimelineTracker.getOrCreate(view.context),
) {
    private val tracker = ConnectionTracker()
    private val main = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var executor: ScheduledExecutorService? = null
    private var currentDriver: InverterDriver? =
        if (forcedDriverId != DtuSettings.TYPE_AUTO) autodetector.getDriverById(forcedDriverId) else null

    @Synchronized
    fun start() {
        if (executor != null) return
        executor = Executors.newSingleThreadScheduledExecutor().also {
            it.scheduleWithFixedDelay({ pollOnce() }, 0, POLL_DELAY_SECONDS, TimeUnit.SECONDS)
        }
    }

    @Synchronized
    fun stop() {
        executor?.shutdownNow()
        executor = null
        timelineTracker?.persist()
    }

    private fun pollOnce() {
        try {
            try {
                var driver = currentDriver
                if (driver == null) {
                    driver = autodetector.detect(host)
                    currentDriver = driver
                }
                val snapshot = driver.readSnapshot(host)
                val now = System.currentTimeMillis()
                tracker.onSuccess(snapshot, now)
                timelineTracker?.record(snapshot, now)
            } catch (e: Exception) {
                Log.w(TAG, "lectura fallida con ${currentDriver?.displayName ?: "desconocido"}: ${e.message}")
                tracker.onFailure()
                // Si falla consecutivamente en modo AUTO, permitimos re-detectar
                if (forcedDriverId == DtuSettings.TYPE_AUTO && tracker.state().consecutiveFailures >= 3) {
                    currentDriver = null
                }
            }
            val series = timelineTracker?.getTimelineSeries(System.currentTimeMillis())
            val flow = FlowState.from(tracker.state(), Locale.getDefault(), timeline = series) { timeFormat.format(Date(it)) }
            main.post { view.render(flow) }
        } catch (t: Throwable) {
            Log.w(TAG, "fallo inesperado en el sondeo: ${t.message}")
        }
    }

    private companion object {
        const val TAG = "SolarDream"
        const val POLL_DELAY_SECONDS = 4L
    }
}
