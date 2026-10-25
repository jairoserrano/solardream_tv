package dev.jairoserrano.solardream.ui

import dev.jairoserrano.solardream.model.ConnectionTracker
import dev.jairoserrano.solardream.model.TimelineSeries
import java.util.Locale
import kotlin.math.abs

enum class Arrow { OFF, FORWARD, BACKWARD }

enum class GridMode { EXPORT, IMPORT, IDLE, UNKNOWN }

/** Todo lo que [FlowView] necesita dibujar, ya decidido y probado sin Android. */
data class FlowState(
    val panelsText: String,
    val houseText: String,
    val gridText: String,
    val gridLabel: String,
    val panelsToHouse: Arrow,
    val houseGrid: Arrow,
    val gridMode: GridMode,
    val dimmed: Boolean,
    val footer: String,
    val corner: String,
    val todayText: String = "—",
    val timeline: TimelineSeries? = null,
) {
    companion object {
        const val ACTIVE_W = 5
        private val ES: Locale = Locale.forLanguageTag("es")

        val CONNECTING: FlowState = from(ConnectionTracker.State(false, null, null)) { "" }

        fun from(state: ConnectionTracker.State, formatTime: (Long) -> String): FlowState =
            from(state, ES, null, formatTime)

        fun from(
            state: ConnectionTracker.State,
            locale: Locale,
            formatTime: (Long) -> String,
        ): FlowState = from(state, locale, null, formatTime)

        fun from(
            state: ConnectionTracker.State,
            locale: Locale,
            timeline: TimelineSeries? = null,
            formatTime: (Long) -> String,
        ): FlowState {
            val isEs = locale.language.lowercase() == "es"
            val s = state.last
                ?: return FlowState(
                    panelsText = "—",
                    houseText = "—",
                    gridText = "—",
                    gridLabel = if (isEs) "Red" else "Grid",
                    panelsToHouse = Arrow.OFF,
                    houseGrid = Arrow.OFF,
                    gridMode = GridMode.UNKNOWN,
                    dimmed = true,
                    footer = if (isEs) "Conectando…" else "Connecting…",
                    corner = "",
                    timeline = timeline,
                )

            val live = state.connected
            val time = state.lastOkMillis?.let(formatTime) ?: ""

            val mode = when {
                !s.hasMeter -> GridMode.UNKNOWN
                s.gridW >= ACTIVE_W -> GridMode.EXPORT
                s.gridW <= -ACTIVE_W -> GridMode.IMPORT
                else -> GridMode.IDLE
            }
            val label = when (mode) {
                GridMode.EXPORT -> if (isEs) "Red (exporta)" else "Grid (export)"
                GridMode.IMPORT -> if (isEs) "Red (importa)" else "Grid (import)"
                else -> if (isEs) "Red" else "Grid"
            }
            val houseGrid = when {
                !live -> Arrow.OFF
                mode == GridMode.EXPORT -> Arrow.FORWARD
                mode == GridMode.IMPORT -> Arrow.BACKWARD
                else -> Arrow.OFF
            }
            val panelsToHouse =
                if (live && s.productionW >= ACTIVE_W) Arrow.FORWARD else Arrow.OFF

            val todayFormatted = String.format(locale, "%.1f", s.producedTodayWh / 1000.0)

            return FlowState(
                panelsText = kw(s.productionW, locale),
                houseText = s.houseW?.let { kw(it, locale) } ?: if (isEs) "n/d" else "n/a",
                gridText = if (s.hasMeter) kw(abs(s.gridW), locale) else if (isEs) "n/d" else "n/a",
                gridLabel = label,
                panelsToHouse = panelsToHouse,
                houseGrid = houseGrid,
                gridMode = mode,
                dimmed = !live,
                footer = if (live) {
                    if (isEs) {
                        "Hoy: $todayFormatted kWh producidos"
                    } else {
                        "Today: $todayFormatted kWh produced"
                    }
                } else {
                    if (isEs) {
                        "Sin conexión · última lectura a las $time"
                    } else {
                        "Offline · last reading at $time"
                    }
                },
                corner = time,
                todayText = "$todayFormatted kWh",
                timeline = timeline,
            )
        }

        private fun kw(watts: Int, locale: Locale = ES): String =
            String.format(locale, "%.2f kW", watts / 1000.0)
    }
}
