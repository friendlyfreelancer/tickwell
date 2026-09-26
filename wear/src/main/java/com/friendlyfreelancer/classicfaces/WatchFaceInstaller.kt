package com.friendlyfreelancer.classicfaces

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.edit
import androidx.wear.watchfacepush.WatchFacePushManager
import androidx.wear.watchfacepush.WatchFacePushManagerFactory
import java.io.File

/**
 * Manages the watch face bundled in assets/default_watchface.apk through
 * Watch Face Push. The system installs it once with the app; this keeps it
 * installed and up to date, and can make it the active face.
 */
object WatchFaceInstaller {
    private const val TAG = "WatchFaceInstaller"
    private const val ASSET = "default_watchface.apk"
    private const val PREFS = "watchface"
    private const val KEY_ACTIVATION_USED = "activation_used"

    /** Package of the bundled face ("<app package>.watchfacepush.<name>"). */
    fun facePackage(context: Context) = "${context.packageName}.watchfacepush.classic"

    enum class State { MISSING, INSTALLED, ACTIVE }

    private fun manager(context: Context): WatchFacePushManager =
        WatchFacePushManagerFactory.createWatchFacePushManager(context)

    private suspend fun installed(context: Context) =
        manager(context).listWatchFaces().installedWatchFaceDetails
            .firstOrNull { it.packageName == facePackage(context) }

    suspend fun state(context: Context): State {
        if (installed(context) == null) return State.MISSING
        return if (manager(context).isWatchFaceActive(facePackage(context))) State.ACTIVE
        else State.INSTALLED
    }

    /** Installs the bundled face, or updates it if the bundled one is newer. */
    suspend fun ensureInstalled(context: Context) {
        try {
            val apk = copyAsset(context)
            val bundledVersion = context.packageManager
                .getPackageArchiveInfo(apk.path, 0)?.longVersionCode ?: return
            val token = context.getString(R.string.default_wf_token)
            val current = installed(context)
            ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                when {
                    current == null -> manager(context).addWatchFace(fd, token)
                    current.versionCode < bundledVersion ->
                        manager(context).updateWatchFace(current.slotId, fd, token)
                }
            }
            apk.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't install the bundled watch face", e)
        }
    }

    /**
     * The system allows an app to set its face as active only once, ever.
     * After that, the user picks it by long-pressing their watch face.
     */
    fun canActivate(context: Context) =
        !prefs(context).getBoolean(KEY_ACTIVATION_USED, false)

    /** Needs SET_PUSHED_WATCH_FACE_AS_ACTIVE to have been granted. */
    suspend fun activate(context: Context): Boolean {
        val face = installed(context) ?: return false
        prefs(context).edit { putBoolean(KEY_ACTIVATION_USED, true) }
        return try {
            manager(context).setWatchFaceAsActive(face.slotId)
            true
        } catch (e: WatchFacePushManager.SetWatchFaceAsActiveException) {
            Log.w(TAG, "Couldn't set the watch face as active", e)
            false
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun copyAsset(context: Context): File {
        val file = File(context.cacheDir, ASSET)
        context.assets.open(ASSET).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return file
    }
}
