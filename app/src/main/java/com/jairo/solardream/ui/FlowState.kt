package com.jairo.solardream.ui

import com.jairo.solardream.model.ConnectionTracker
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
) {
    companion object {
        const val ACTIVE_W = 5
        private val ES: Locale = Locale.forLanguageTag("es")

        val CONNECTING: FlowState = from(ConnectionTracker.State(false, null, null)) { "" }

        fun from(state: ConnectionTracker.State, formatTime: (Long) -> String): FlowState {
            val s = state.last
                ?: return FlowState(
                    panelsText = "—",
                    houseText = "—",
                    gridText = "—",
                    gridLabel = "Red",
                    panelsToHouse = Arrow.OFF,
                    houseGrid = Arrow.OFF,
                    gridMode = GridMode.UNKNOWN,
                    dimmed = true,
                    footer = "Conectando…",
                    corner = "",
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
                GridMode.EXPORT -> "Red (exporta)"
                GridMode.IMPORT -> "Red (importa)"
                else -> "Red"
            }
            val houseGrid = when {
                !live -> Arrow.OFF
                mode == GridMode.EXPORT -> Arrow.FORWARD
                mode == GridMode.IMPORT -> Arrow.BACKWARD
                else -> Arrow.OFF
            }
            val panelsToHouse =
                if (live && s.productionW >= ACTIVE_W) Arrow.FORWARD else Arrow.OFF

            return FlowState(
                panelsText = kw(s.productionW),
                houseText = s.houseW?.let { kw(it) } ?: "n/d",
                gridText = if (s.hasMeter) kw(abs(s.gridW)) else "n/d",
                gridLabel = label,
                panelsToHouse = panelsToHouse,
                houseGrid = houseGrid,
                gridMode = mode,
                dimmed = !live,
                footer = if (live) {
                    "Hoy: ${String.format(ES, "%.1f", s.producedTodayWh / 1000.0)} kWh producidos"
                } else {
                    "Sin conexión · última lectura a las $time"
                },
                corner = time,
            )
        }

        private fun kw(watts: Int): String = String.format(ES, "%.2f kW", watts / 1000.0)
    }
}
