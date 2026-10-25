package com.alauncher.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alauncher.app.service.NotificationListener

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i("PackageReplacedReceiver", "ALauncher updated. Requesting NotificationListener rebind.")
            rebindTrigger(context)
        }
    }

    companion object {
        @androidx.annotation.VisibleForTesting
        internal var rebindTrigger: (Context) -> Boolean = { NotificationListener.requestRebindIfDisconnected(it) }
    }
}
