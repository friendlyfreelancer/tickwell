package com.friendlyfreelancer.classicfaces

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.wear.tiles.TileService

/** User settings, plus the one place that turns ticking on and off. */
object TickSettings {
    private const val PREFS = "tick"
    const val KEY_ENABLED = "enabled"
    const val KEY_VOLUME = "volume"
    const val KEY_LEAD_MS = "lead_ms"

    const val VOLUME_STEPS = 10
    const val DEFAULT_VOLUME = 6

    /**
     * How early each tick is fired, to cancel out speaker latency so the
     * sound lands with the seconds hand. Tuned per watch in the app.
     */
    const val MAX_LEAD_MS = 150
    const val LEAD_STEP_MS = 10
    const val DEFAULT_LEAD_MS = 40

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)
    fun volume(context: Context) = prefs(context).getInt(KEY_VOLUME, DEFAULT_VOLUME)
    fun leadMs(context: Context) = prefs(context).getInt(KEY_LEAD_MS, DEFAULT_LEAD_MS)

    fun setVolume(context: Context, steps: Int) =
        prefs(context).edit { putInt(KEY_VOLUME, steps.coerceIn(1, VOLUME_STEPS)) }

    fun setLeadMs(context: Context, ms: Int) =
        prefs(context).edit { putInt(KEY_LEAD_MS, ms.coerceIn(0, MAX_LEAD_MS)) }

    /** Must be called while the app is in the foreground (FGS start rules). */
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
        syncService(context)
        TileService.getUpdater(context).requestUpdate(TickTileService::class.java)
    }

    /** Starts or stops the service to match the saved setting. */
    fun syncService(context: Context) {
        val intent = Intent(context, TickService::class.java)
        if (isEnabled(context)) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.stopService(intent)
        }
    }
}
