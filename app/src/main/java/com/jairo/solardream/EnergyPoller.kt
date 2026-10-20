package com.jairo.solardream

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.jairo.solardream.dtu.DtuClient
import com.jairo.solardream.model.ConnectionTracker
import com.jairo.solardream.model.EnergyModel
import com.jairo.solardream.model.MeterConvention
import com.jairo.solardream.ui.FlowState
import com.jairo.solardream.ui.FlowView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Sondea el DTU en un hilo de fondo y entrega el estado a la vista en el hilo principal. */
class EnergyPoller(host: String, private val view: FlowView) {
    private val client = DtuClient(host)
    private val model = EnergyModel(MeterConvention.CALIBRATED)
    private val tracker = ConnectionTracker()
    private val main = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    private var executor: ScheduledExecutorService? = null

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
    }

    private fun pollOnce() {
        try {
            try {
                val snapshot = model.toSnapshot(client.readRealData())
                tracker.onSuccess(snapshot, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.w(TAG, "lectura fallida: ${e.message}")
                tracker.onFailure()
            }
            val flow = FlowState.from(tracker.state()) { timeFormat.format(Date(it)) }
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
