package com.friendlyfreelancer.classicfaces

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The system installs the bundled watch face only with the first install, so
 * app updates push the new face themselves.
 */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WatchFaceInstaller.ensureInstalled(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
