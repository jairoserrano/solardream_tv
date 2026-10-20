package com.jairo.solardream

import android.content.Context
import android.provider.Settings

/** La IP del DTU se cambia con: adb shell settings put global solardream_dtu_host <ip> */
object DtuSettings {
    const val DEFAULT_HOST = "192.168.88.16"
    private const val KEY = "solardream_dtu_host"

    fun host(context: Context): String =
        Settings.Global.getString(context.contentResolver, KEY)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_HOST
}
