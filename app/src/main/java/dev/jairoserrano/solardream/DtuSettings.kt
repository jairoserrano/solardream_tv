package dev.jairoserrano.solardream

import android.content.Context
import android.provider.Settings

/**
 * Gestiona la dirección IP y el tipo de inversor/pasarela solar.
 *
 * Prioridad de resolución:
 * 1. Preferencias locales de la aplicación (configurables desde la UI en la TV).
 * 2. Ajuste global del sistema (solardream_dtu_host vía ADB).
 * 3. Dirección predeterminada (192.168.88.16).
 */
object DtuSettings {
    const val DEFAULT_HOST = "192.168.88.16"
    const val TYPE_AUTO = "AUTO"
    private const val KEY = "solardream_dtu_host"
    private const val PREFS_NAME = "solardream_settings"
    private const val PREF_HOST_KEY = "dtu_host"
    private const val PREF_TYPE_KEY = "inverter_type"

    fun host(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(PREF_HOST_KEY, null)?.takeIf { it.isNotBlank() }?.let { return it }

        return runCatching {
            Settings.Global.getString(context.contentResolver, KEY)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST
    }

    fun setHost(context: Context, host: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_HOST_KEY, host.trim()).apply()
    }

    fun inverterType(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_TYPE_KEY, TYPE_AUTO) ?: TYPE_AUTO
    }

    fun setInverterType(context: Context, type: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_TYPE_KEY, type.trim().uppercase()).apply()
    }
}
