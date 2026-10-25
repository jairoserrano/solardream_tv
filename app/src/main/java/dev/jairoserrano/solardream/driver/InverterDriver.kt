package dev.jairoserrano.solardream.driver

import dev.jairoserrano.solardream.model.EnergySnapshot

/**
 * Interfaz unificada para todas las fuentes e inversores solares locales.
 */
interface InverterDriver {
    val id: String
    val displayName: String
    val defaultPort: Int

    /**
     * Envía una sonda ligera para determinar si el equipo en [host] corresponde a este inversor.
     */
    fun probe(host: String): Boolean

    /**
     * Realiza la lectura completa de telemetría y entrega un [EnergySnapshot] normalizado.
     */
    fun readSnapshot(host: String): EnergySnapshot
}
