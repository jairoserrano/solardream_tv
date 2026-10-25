package dev.jairoserrano.solardream.model

/** Decide si seguimos "conectados": se pierde la conexión tras [maxFailures] fallos seguidos. */
class ConnectionTracker(private val maxFailures: Int = 3) {

    data class State(
        val connected: Boolean,
        val last: EnergySnapshot?,
        val lastOkMillis: Long?,
        val consecutiveFailures: Int = 0,
    )

    private var failures = 0
    private var last: EnergySnapshot? = null
    private var lastOkMillis: Long? = null

    @Synchronized
    fun onSuccess(snapshot: EnergySnapshot, nowMillis: Long) {
        failures = 0
        last = snapshot
        lastOkMillis = nowMillis
    }

    @Synchronized
    fun onFailure() {
        failures++
    }

    @Synchronized
    fun state(): State = State(
        connected = last != null && failures < maxFailures,
        last = last,
        lastOkMillis = lastOkMillis,
        consecutiveFailures = failures,
    )
}

