package com.friendlyfreelancer.classicfaces

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationCompat
import kotlin.math.pow

/**
 * Plays a tick on every wall-clock second while the screen is on.
 *
 * The watch face moves its seconds hand on the system clock's second
 * boundaries, so scheduling against the same clock keeps the two in step
 * without any link between them. Each sound fires [TickSettings.leadMs]
 * early to absorb speaker latency.
 */
class TickService : Service() {

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var pool: SoundPool
    private lateinit var audio: AudioManager
    private lateinit var notifications: NotificationManager
    private var tickId = 0
    private var tockId = 0
    private var loaded = 0
    @Volatile private var ticking = false

    // Read on the tick thread, written from the main thread.
    @Volatile private var volume = 0f
    @Volatile private var leadMs = 0

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> handler.post { startTicking() }
                // Also sent on entering ambient, where the seconds hand is hidden.
                Intent.ACTION_SCREEN_OFF -> handler.post { stopTicking() }
            }
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        readSettings()
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!ticking) return
            val second = (System.currentTimeMillis() + leadMs) / 1000
            if (canMakeSound()) {
                val id = if (second % 2 == 0L) tickId else tockId
                pool.play(id, volume, volume, 1, 0, 1f)
            }
            scheduleNext()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        audio = getSystemService(AudioManager::class.java)
        notifications = getSystemService(NotificationManager::class.java)

        thread = HandlerThread("tick", Process.THREAD_PRIORITY_URGENT_AUDIO).apply { start() }
        handler = Handler(thread.looper)

        pool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        pool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0 && ++loaded == 2) handler.post { if (isScreenOn()) startTicking() }
        }
        tickId = pool.load(this, R.raw.tick, 1)
        tockId = pool.load(this, R.raw.tock, 1)

        readSettings()
        TickSettings.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // From the notification: persist "off" so the app and tile agree.
            TickSettings.setEnabled(this, false)
        }
        // Not sticky: a background restart of a media FGS is not allowed. The
        // app restarts the service next time it is opened if still enabled.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        TickSettings.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        stopTicking()
        thread.quitSafely()
        pool.release()
        super.onDestroy()
    }

    private fun readSettings() {
        val steps = TickSettings.volume(this)
        // Perceptual curve: equal slider steps sound like equal loudness steps.
        volume = (steps.toFloat() / TickSettings.VOLUME_STEPS).pow(2)
        leadMs = TickSettings.leadMs(this)
    }

    private fun startTicking() {
        if (ticking || loaded < 2) return
        ticking = true
        scheduleNext()
    }

    private fun stopTicking() {
        ticking = false
        handler.removeCallbacks(tickRunnable)
    }

    private fun scheduleNext() {
        val now = System.currentTimeMillis() + leadMs
        var wait = 1000 - now % 1000
        // Woke a hair early for this second: skip to the next, never double-tick.
        if (wait < 200) wait += 1000
        handler.postDelayed(tickRunnable, wait)
    }

    private fun isScreenOn() = getSystemService(PowerManager::class.java).isInteractive

    /** Silent in silent/vibrate mode and whenever Do Not Disturb filters anything. */
    private fun canMakeSound(): Boolean =
        audio.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
            notifications.currentInterruptionFilter.let {
                it == NotificationManager.INTERRUPTION_FILTER_ALL ||
                    it == NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TickService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tick)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.turn_off), stop)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ticking"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.friendlyfreelancer.classicfaces.STOP"
    }
}
