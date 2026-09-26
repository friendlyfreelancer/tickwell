package com.friendlyfreelancer.classicfaces

import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Opens the app when the phone companion asks. The phone sends a Data Layer
 * message rather than a remote intent, because remote intents need Google's
 * Wear OS phone app, which Galaxy Watch owners don't have.
 */
class PhoneMessageService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH_OPEN) return
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        /** Keep in sync with the phone app. */
        const val PATH_OPEN = "/classicfaces/open"
    }
}
